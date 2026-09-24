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
     */
    private static final Map<String, Set<String>> ALLOWED_COLUMNS = Map.of(
        "crm_accounts", Set.of("name", "industry", "phone", "website", "owner_id", "status"),
        "crm_contacts", Set.of("account_id", "first_name", "last_name", "email", "phone", "title", "status"),
        "crm_leads", Set.of("first_name", "last_name", "email", "phone", "status", "source", "owner_id"),
        "crm_opportunities", Set.of("account_id", "contact_id", "pipeline_id", "title", "amount", "stage", "close_date"),
        "crm_tasks", Set.of("title", "description", "status", "due_date", "assigned_to", "priority"),
        "crm_notes", Set.of("entity_type", "entity_id", "content"),
        "crm_activities", Set.of("entity_type", "entity_id", "activity_type", "description", "result")
    );

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
            // 1. Idempotency check
            if (isDuplicate(mutation.idempotencyKey())) {
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
     */
    private boolean isDuplicate(String idempotencyKey) {
        if (idempotencyKey == null) return false;
        String sha256 = computeSha256(idempotencyKey);
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM platform_audit_logs WHERE idempotency_key = ? AND created_at > NOW() - INTERVAL '24 hours'",
            Integer.class, sha256
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

        // Build INSERT from payload
        // This is simplified — production would use parameterized queries per entity type
        JsonNode payload = mutation.payload();
        StringBuilder columns = new StringBuilder("tenant_id, id, created_by, sync_version");
        StringBuilder placeholders = new StringBuilder("?, ?::UUID, ?, 1");
        List<Object> params = new ArrayList<>();
        params.add(tenantId);
        params.add(entityId);
        params.add(userId);

        if (payload != null && payload.isObject()) {
            Set<String> allowed = allowedColumnsFor(tableName);
            payload.fields().forEachRemaining(entry -> {
                String key = entry.getKey();
                if (allowed.contains(key)) {            // DEF-004: identifier allowlist
                    columns.append(", ").append(key);
                    placeholders.append(", ?");
                    params.add(entry.getValue().asText());
                }
            });
        }

        String sql = String.format("INSERT INTO %s (%s, created_at, updated_at) VALUES (%s, NOW(), NOW())",
            tableName, columns, placeholders);

        jdbcTemplate.update(sql, params.toArray());

        recordIdempotency(tenantId, mutation.idempotencyKey(), mutation.operation(), entityId);

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

        int updated = jdbcTemplate.update(sql,
            List.of(params.toArray(new Object[0]),
                tenantId, mutation.entityId(), currentVersion).toArray());

        if (updated == 0) {
            return conflictResult(mutation, currentVersion);
        }

        long newVersion = currentVersion + 1;
        String etag = "\"" + newVersion + "\"";

        recordIdempotency(tenantId, mutation.idempotencyKey(), mutation.operation(), mutation.entityId());

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
     */
    private MutationResult deleteEntity(UUID tenantId, UUID userId, String tableName,
                                         MutationEnvelope mutation, long currentVersion) {
        // Soft delete: set deleted_at timestamp
        String sql = String.format(
            "UPDATE %s SET deleted_at = NOW(), updated_at = NOW() WHERE tenant_id = ? AND id = ?::UUID AND sync_version = ?",
            tableName);

        int deleted = jdbcTemplate.update(sql, tenantId, mutation.entityId(), currentVersion);

        if (deleted == 0) {
            return conflictResult(mutation, currentVersion);
        }

        recordIdempotency(tenantId, mutation.idempotencyKey(), mutation.operation(), mutation.entityId());

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
     * Record idempotency key in audit log.
     */
    private void recordIdempotency(UUID tenantId, String idempotencyKey, String operation, String entityId) {
        if (idempotencyKey == null) return;
        String sha256 = computeSha256(idempotencyKey);
        jdbcTemplate.update(
            "INSERT INTO platform_audit_logs (id, tenant_id, entity_type, entity_id, action, idempotency_key, created_at) "
                + "VALUES (gen_random_uuid(), ?, 'SYNC_MUTATION', ?::UUID, ?, ?, NOW())",
            tenantId, entityId, operation, sha256
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
