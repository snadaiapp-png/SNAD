package com.sanad.platform.crm.mobile.sync.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sanad.platform.crm.mobile.sync.model.PushSyncRequest;
import com.sanad.platform.crm.mobile.sync.model.PushSyncRequest.MutationEnvelope;
import com.sanad.platform.crm.mobile.sync.model.PushSyncResponse;
import com.sanad.platform.crm.mobile.sync.model.PushSyncResponse.MutationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Service for batch sync push operations.
 * Processes array of mutation envelopes with per-mutation ACK.
 *
 * Requirements: API-004 (Batch Sync Push API), SYNC-017 (Per-Mutation ACK),
 *               SYNC-008 (Idempotency), SYNC-009 (Conflict Isolation)
 */
@Service
public class PushSyncService {

    private static final Logger log = LoggerFactory.getLogger(PushSyncService.class);

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    // Entity type to table mapping
    private static final Map<String, String> ENTITY_TABLES = Map.of(
        "account", "crm_accounts",
        "contact", "crm_contacts",
        "lead", "crm_leads",
        "opportunity", "crm_opportunities",
        "task", "crm_tasks",
        "note", "crm_notes",
        "activity", "crm_activities"
    );

    /**
     * Per-entity column allowlist — DEF-004 remediation.
     * A mutation payload may ONLY write these columns; every other JSON key is
     * silently ignored. Column identifiers are therefore NEVER spliced from
     * untrusted input — only values are bound, and only for allowlisted columns.
     *
     * Columns verified against the physical CRM tables (V20260702_1/V20260717_*
     * + V20260812_2 sync columns) on the staging information_schema.
     */
    private static final Map<String, Set<String>> ALLOWED_COLUMNS = Map.of(
        "crm_accounts", Set.of("display_name", "normalized_name", "account_type", "industry_code", "website", "primary_phone", "primary_email"),
        "crm_contacts", Set.of("account_id", "given_name", "family_name", "display_name", "normalized_name", "primary_email", "primary_phone", "lifecycle_status"),
        "crm_leads", Set.of("display_name", "normalized_name", "company_name", "email", "phone", "status", "source"),
        "crm_opportunities", Set.of("account_id", "contact_id", "pipeline_id", "name", "amount", "currency_code", "stage_id", "expected_close_date", "status"),
        "crm_tasks", Set.of("title", "description", "status", "priority", "due_at", "assignee_user_id", "related_type", "related_id"),
        "crm_notes", Set.of("subject_type", "subject_id", "body"),
        "crm_activities", Set.of("activity_type", "subject", "body", "related_type", "related_id", "result", "status")
    );

    /** Tables whose NOT NULL constraints require normalized_name on INSERT. */
    private static final Set<String> REQUIRES_NORMALIZED_NAME = Set.of("crm_accounts", "crm_contacts", "crm_leads");

    private Set<String> allowedColumnsFor(String tableName) {
        return ALLOWED_COLUMNS.getOrDefault(tableName, Set.of());
    }

    public PushSyncService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Process batch push mutations.
     * Each mutation is processed independently — one failure does not block others.
     *
     * Requirements: SYNC-009 (Conflict Isolation), ISO-004 (Failure Isolation)
     */
    @Transactional
    public PushSyncResponse push(UUID tenantId, UUID deviceId, UUID userId, PushSyncRequest request) {
        // RLS tenant context (SET LOCAL app.tenant_id) is applied automatically by
        // TenantRlsConnectionHandler within this @Transactional boundary. The tenant
        // id is the validated JWT principal from the security context (DEF-005/DEF-007),
        // never client-supplied, so no manual SET / RESET is performed here.

        List<MutationResult> results = new ArrayList<>();
        int applied = 0, rejected = 0, duplicates = 0;

        for (MutationEnvelope mutation : request.mutations()) {
            MutationResult result = processMutation(tenantId, deviceId, userId, mutation);
            results.add(result);

            switch (result.status()) {
                case "APPLIED" -> applied++;
                case "REJECTED", "CONFLICT" -> rejected++;
                case "DUPLICATE" -> duplicates++;
            }
        }

        log.info("Push sync: tenant={}, device={}, total={}, applied={}, rejected={}, duplicates={}",
            tenantId, deviceId, request.mutations().size(), applied, rejected, duplicates);

        return new PushSyncResponse(
            request.mutations().size(),
            applied,
            rejected,
            duplicates,
            results
        );
    }

    /**
     * Process a single mutation.
     * Handles idempotency check, version validation, and application.
     */
    private MutationResult processMutation(UUID tenantId, UUID deviceId, UUID userId, MutationEnvelope mutation) {
        try {
            // 1. Idempotency check (crm_idempotency_records, 24h window)
            if (isDuplicate(tenantId, mutation.idempotencyKey())) {
                log.debug("Duplicate mutation detected: key={}", mutation.idempotencyKey());
                return new MutationResult(
                    mutation.idempotencyKey(),
                    mutation.entityId(),
                    "DUPLICATE",
                    "200",
                    null, null, null,
                    "Duplicate mutation — already processed"
                );
            }

            String tableName = ENTITY_TABLES.get(mutation.entityType().toLowerCase());
            if (tableName == null) {
                return errorResult(mutation, "400", "Unknown entity type: " + mutation.entityType());
            }

            // 2. Version validation (ETag/If-Match)
            long currentVersion = getCurrentVersion(tableName, tenantId, mutation.entityId());
            if (currentVersion < 0) {
                // Entity not found — for CREATE, this is fine; for UPDATE/DELETE, reject
                if ("CREATE".equals(mutation.operation())) {
                    return createEntity(tenantId, userId, tableName, mutation);
                }
                return errorResult(mutation, "404", "Entity not found: " + mutation.entityId());
            }

            if (!"CREATE".equals(mutation.operation()) && mutation.expectedVersion() != null) {
                if (currentVersion != mutation.expectedVersion()) {
                    // Version mismatch — conflict detected
                    log.warn("Version mismatch: entity={}, expected={}, actual={}",
                        mutation.entityId(), mutation.expectedVersion(), currentVersion);
                    return conflictResult(mutation, currentVersion);
                }
            }

            // 3. Apply mutation based on operation
            return switch (mutation.operation()) {
                case "CREATE" -> createEntity(tenantId, userId, tableName, mutation);
                case "UPDATE" -> updateEntity(tenantId, userId, tableName, mutation, currentVersion);
                case "DELETE" -> deleteEntity(tenantId, userId, tableName, mutation, currentVersion);
                default -> errorResult(mutation, "400", "Unknown operation: " + mutation.operation());
            };

        } catch (Exception e) {
            log.error("Error processing mutation: key={}", mutation.idempotencyKey(), e);
            return errorResult(mutation, "500", "Internal error: " + e.getMessage());
        }
    }

    /**
     * Check if idempotency key has already been processed.
     * Uses crm_idempotency_records (V20260713_1); platform_audit_logs has no
     * idempotency_key column.
     */
    private boolean isDuplicate(UUID tenantId, String idempotencyKey) {
        if (idempotencyKey == null) return false;
        String sha256 = computeSha256(idempotencyKey);
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM crm_idempotency_records WHERE tenant_id = ? AND idempotency_key = ? AND expires_at > NOW()",
            Integer.class, tenantId, sha256
        );
        return count != null && count > 0;
    }

    /**
     * Get current version of entity.
     * Returns -1 if entity not found.
     */
    private long getCurrentVersion(String tableName, UUID tenantId, String entityId) {
        try {
            Long version = jdbcTemplate.queryForObject(
                "SELECT sync_version FROM " + tableName + " WHERE tenant_id = ? AND id = ?::UUID",
                Long.class, tenantId, entityId
            );
            return version != null ? version : -1;
        } catch (Exception e) {
            return -1;
        }
    }

    /**
     * Create a new entity.
     */
    private MutationResult createEntity(UUID tenantId, UUID userId, String tableName, MutationEnvelope mutation) {
        // For CREATE, generate new ID if not provided
        String entityId = mutation.entityId() != null ? mutation.entityId() : UUID.randomUUID().toString();

        // Build INSERT from the allowlisted payload columns. created_by/updated_by
        // and sync_version are always bound; created_at/updated_at default to NOW().
        JsonNode payload = mutation.payload();
        List<String> columns = new ArrayList<>(List.of("tenant_id", "id", "created_by", "updated_by", "sync_version"));
        List<Object> params = new ArrayList<>(List.of(tenantId, entityId, userId, userId, 1));
        String displayName = null;

        if (payload != null && payload.isObject()) {
            Set<String> allowed = allowedColumnsFor(tableName);
            payload.fields().forEachRemaining(entry -> {
                String key = entry.getKey();
                if (allowed.contains(key)) {            // DEF-004: identifier allowlist
                    columns.add(key);
                    params.add(entry.getValue().asText());
                }
            });
            JsonNode dn = payload.get("display_name");
            if (dn != null && !dn.isNull()) displayName = dn.asText();
        }

        // NOT NULL fallback: these tables require normalized_name alongside display_name.
        if (REQUIRES_NORMALIZED_NAME.contains(tableName)
                && !columns.contains("normalized_name") && displayName != null) {
            columns.add("normalized_name");
            params.add(displayName.toLowerCase());
        }

        // created_at/updated_at are NOT NULL WITHOUT DEFAULT on the CRM tables
        // (V20260702_1) — they must be bound explicitly on INSERT.
        columns.add("created_at");
        params.add(Timestamp.from(Instant.now()));
        columns.add("updated_at");
        params.add(Timestamp.from(Instant.now()));

        StringBuilder placeholders = new StringBuilder();
        for (int i = 0; i < columns.size(); i++) {
            placeholders.append(i == 0 ? "?" : ", ?");
        }
        // Text-bound values rely on stringtype=unspecified (JDBC URL) so
        // PostgreSQL infers uuid/timestamp/numeric types per target column.

        String sql = String.format("INSERT INTO %s (%s) VALUES (%s)",
            tableName, String.join(", ", columns), placeholders);

        jdbcTemplate.update(sql, params.toArray());

        recordIdempotency(tenantId, userId, mutation.idempotencyKey(), mutation.operation(), entityId);

        return new MutationResult(
            mutation.idempotencyKey(),
            entityId,
            "APPLIED",
            "201",
            1L, null, null, null
        );
    }

    /**
     * Update an existing entity with version check.
     */
    private MutationResult updateEntity(UUID tenantId, UUID userId, String tableName,
                                         MutationEnvelope mutation, long currentVersion) {
        JsonNode payload = mutation.payload();
        StringBuilder setClauses = new StringBuilder();
        List<Object> params = new ArrayList<>();

        if (payload != null && payload.isObject()) {
            Set<String> allowed = allowedColumnsFor(tableName);
            payload.fields().forEachRemaining(entry -> {
                String key = entry.getKey();
                if (allowed.contains(key)) {            // DEF-004: identifier allowlist
                    if (!setClauses.isEmpty()) setClauses.append(", ");
                    setClauses.append(key).append(" = ?");
                    params.add(entry.getValue().asText());
                }
            });
        }

        if (setClauses.isEmpty()) {
            return new MutationResult(
                mutation.idempotencyKey(), mutation.entityId(),
                "APPLIED", "200", currentVersion, null, null, null
            );
        }

        // The sync_version trigger will auto-increment
        String sql = String.format("UPDATE %s SET %s WHERE tenant_id = ? AND id = ?::UUID AND sync_version = ?",
            tableName, setClauses);

        List<Object> allParams = new ArrayList<>(params);
        allParams.add(tenantId);
        allParams.add(mutation.entityId());
        allParams.add(currentVersion);
        int updated = jdbcTemplate.update(sql, allParams.toArray());

        if (updated == 0) {
            return conflictResult(mutation, currentVersion);
        }

        long newVersion = currentVersion + 1;
        String etag = "\"" + newVersion + "\"";

        recordIdempotency(tenantId, userId, mutation.idempotencyKey(), mutation.operation(), mutation.entityId());

        return new MutationResult(
            mutation.idempotencyKey(),
            mutation.entityId(),
            "APPLIED",
            "200",
            newVersion, etag, null, null
        );
    }

    /**
     * Delete an entity (soft delete or hard delete per entity policy).
     * The CRM tables have no universal deleted_at column (only crm_accounts/
     * crm_contacts have archived_at), so sync DELETE removes the row —
     * tenant- and version-scoped, RLS-enforced.
     */
    private MutationResult deleteEntity(UUID tenantId, UUID userId, String tableName,
                                         MutationEnvelope mutation, long currentVersion) {
        String sql = String.format(
            "DELETE FROM %s WHERE tenant_id = ? AND id = ?::UUID AND sync_version = ?",
            tableName);

        int deleted = jdbcTemplate.update(sql, tenantId, mutation.entityId(), currentVersion);

        if (deleted == 0) {
            return conflictResult(mutation, currentVersion);
        }

        recordIdempotency(tenantId, userId, mutation.idempotencyKey(), mutation.operation(), mutation.entityId());

        return new MutationResult(
            mutation.idempotencyKey(),
            mutation.entityId(),
            "APPLIED",
            "200",
            currentVersion + 1, null, null, null
        );
    }

    /**
     * Create conflict result for version mismatch.
     */
    private MutationResult conflictResult(MutationEnvelope mutation, long serverVersion) {
        ObjectNode conflictInfo = objectMapper.createObjectNode();
        conflictInfo.put("serverVersion", serverVersion);
        conflictInfo.put("entityType", mutation.entityType());
        conflictInfo.put("entityId", mutation.entityId());
        conflictInfo.put("conflictType", "VERSION_MISMATCH");

        return new MutationResult(
            mutation.idempotencyKey(),
            mutation.entityId(),
            "CONFLICT",
            "412",
            serverVersion, null, conflictInfo, null
        );
    }

    /**
     * Create error result.
     */
    private MutationResult errorResult(MutationEnvelope mutation, String httpStatus, String message) {
        return new MutationResult(
            mutation.idempotencyKey(),
            mutation.entityId(),
            "REJECTED",
            httpStatus,
            null, null, null, message
        );
    }

    /**
     * Record idempotency key. Uses crm_idempotency_records (V20260713_1) —
     * platform_audit_logs has neither idempotency_key nor entity columns.
     */
    private void recordIdempotency(UUID tenantId, UUID userId, String idempotencyKey, String operation, String entityId) {
        if (idempotencyKey == null) return;
        String sha256 = computeSha256(idempotencyKey);
        jdbcTemplate.update(
            "INSERT INTO crm_idempotency_records "
                + "(id, tenant_id, principal_id, endpoint, idempotency_key, request_fingerprint_sha256, response_status, created_at, expires_at) "
                + "VALUES (gen_random_uuid(), ?, ?, '/api/v2/mobile/sync/push', ?, ?, 200, NOW(), NOW() + INTERVAL '24 hours')",
            tenantId, userId, sha256, sha256
        );
    }

    /**
     * Compute SHA-256 hash.
     */
    private String computeSha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes());
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            return input; // fallback
        }
    }
}
