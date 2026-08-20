package com.sanad.platform.crm.mobile.sync.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sanad.platform.crm.mobile.conflict.service.ConflictService;
import com.sanad.platform.crm.mobile.sync.model.PushSyncRequest;
import com.sanad.platform.crm.mobile.sync.model.PushSyncRequest.MutationEnvelope;
import com.sanad.platform.crm.mobile.sync.model.PushSyncResponse;
import com.sanad.platform.crm.mobile.sync.model.PushSyncResponse.MutationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Service for batch sync push operations.
 * Processes array of mutation envelopes with per-mutation ACK.
 *
 * <p>Correctness invariants:
 * <ul>
 *   <li>idempotency is claimed before the side effect and is scoped by tenant,
 *       principal, endpoint and key;</li>
 *   <li>the idempotency fingerprint is derived from the mutation payload, not
 *       from the key itself;</li>
 *   <li>UPDATE/DELETE require an expected version so blind offline writes are
 *       impossible;</li>
 *   <li>version conflicts are persisted through {@link ConflictService}.</li>
 * </ul>
 *
 * Requirements: API-004 (Batch Sync Push API), SYNC-017 (Per-Mutation ACK),
 *               SYNC-008 (Idempotency), SYNC-009 (Conflict Isolation)
 */
@Service
public class PushSyncService {

    private static final Logger log = LoggerFactory.getLogger(PushSyncService.class);
    private static final String PUSH_ENDPOINT = "/api/v2/mobile/sync/push";

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final ConflictService conflictService;

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

    public PushSyncService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper,
                           ConflictService conflictService) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.conflictService = conflictService;
    }

    /**
     * Process batch push mutations.
     */
    @Transactional
    public PushSyncResponse push(UUID tenantId, UUID deviceId, UUID userId, PushSyncRequest request) {
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

        return new PushSyncResponse(request.mutations().size(), applied, rejected, duplicates, results);
    }

    /** Process a single mutation. */
    private MutationResult processMutation(UUID tenantId, UUID deviceId, UUID userId, MutationEnvelope mutation) {
        IdempotencyDecision idempotency = null;
        try {
            if (mutation.idempotencyKey() == null || mutation.idempotencyKey().isBlank()) {
                return errorResult(mutation, "400", "idempotencyKey is required for sync mutations");
            }

            idempotency = claimIdempotency(tenantId, userId, mutation);
            if (idempotency.state() == IdempotencyState.DUPLICATE) {
                log.debug("Duplicate mutation detected: key={}", mutation.idempotencyKey());
                return new MutationResult(
                    mutation.idempotencyKey(), mutation.entityId(), "DUPLICATE", "200",
                    null, null, null, "Duplicate mutation — already processed");
            }
            if (idempotency.state() == IdempotencyState.MISMATCH) {
                return errorResult(mutation, "409",
                        "IDEMPOTENCY_KEY_REUSE_MISMATCH: key was reused with a different mutation payload");
            }
            if (idempotency.state() == IdempotencyState.ERROR) {
                return errorResult(mutation, "409", "Unable to establish idempotency ownership");
            }

            String entityType = mutation.entityType() == null ? "" : mutation.entityType().toLowerCase();
            String tableName = ENTITY_TABLES.get(entityType);
            if (tableName == null) {
                MutationResult result = errorResult(mutation, "400", "Unknown entity type: " + mutation.entityType());
                completeIdempotency(tenantId, userId, mutation, 400);
                return result;
            }

            String operation = mutation.operation() == null ? "" : mutation.operation().toUpperCase();
            if (("UPDATE".equals(operation) || "DELETE".equals(operation))
                    && mutation.expectedVersion() == null) {
                MutationResult result = errorResult(mutation, "428",
                        "expectedVersion is required for " + operation + " mutations");
                completeIdempotency(tenantId, userId, mutation, 428);
                return result;
            }

            long currentVersion = getCurrentVersion(tableName, tenantId, mutation.entityId());
            if (currentVersion < 0) {
                if ("CREATE".equals(operation)) {
                    MutationResult result = createEntity(tenantId, userId, tableName, mutation);
                    completeIdempotency(tenantId, userId, mutation, 201);
                    return result;
                }
                MutationResult result = errorResult(mutation, "404", "Entity not found: " + mutation.entityId());
                completeIdempotency(tenantId, userId, mutation, 404);
                return result;
            }

            if (!"CREATE".equals(operation) && currentVersion != mutation.expectedVersion()) {
                log.warn("Version mismatch: entity={}, expected={}, actual={}",
                    mutation.entityId(), mutation.expectedVersion(), currentVersion);
                MutationResult result = persistConflict(
                        tenantId, deviceId, userId, tableName, mutation, currentVersion, true);
                completeIdempotency(tenantId, userId, mutation, 412);
                return result;
            }

            MutationResult result = switch (operation) {
                case "CREATE" -> createEntity(tenantId, userId, tableName, mutation);
                case "UPDATE" -> updateEntity(tenantId, userId, tableName, mutation, currentVersion,
                        deviceId);
                case "DELETE" -> deleteEntity(tenantId, userId, tableName, mutation, currentVersion,
                        deviceId);
                default -> errorResult(mutation, "400", "Unknown operation: " + mutation.operation());
            };
            completeIdempotency(tenantId, userId, mutation, parseStatus(result.httpStatus()));
            return result;

        } catch (Exception e) {
            log.error("Error processing mutation: key={}", mutation.idempotencyKey(), e);
            try {
                if (idempotency != null && idempotency.state() == IdempotencyState.CLAIMED) {
                    completeIdempotency(tenantId, userId, mutation, 500);
                }
            } catch (Exception completionError) {
                log.warn("Failed to finalize idempotency record after mutation error: key={}",
                        mutation.idempotencyKey(), completionError);
            }
            return errorResult(mutation, "500", "Internal error: " + e.getMessage());
        }
    }

    /**
     * Atomically claim an idempotency key before applying its mutation. The
     * unique constraint blocks a concurrent claimant until the winner commits;
     * ON CONFLICT avoids a PostgreSQL transaction-aborting duplicate-key error.
     */
    private IdempotencyDecision claimIdempotency(UUID tenantId, UUID userId, MutationEnvelope mutation) {
        String keyHash = computeSha256(mutation.idempotencyKey());
        String fingerprint = computeMutationFingerprint(mutation);

        // Expired rows must be removed before reuse because the physical unique
        // constraint does not include expires_at.
        jdbcTemplate.update(
                "DELETE FROM crm_idempotency_records WHERE tenant_id=? AND principal_id=? "
                        + "AND endpoint=? AND idempotency_key=? AND expires_at <= NOW()",
                tenantId, userId, PUSH_ENDPOINT, keyHash);

        try {
            UUID claimId = jdbcTemplate.queryForObject(
                    "INSERT INTO crm_idempotency_records "
                            + "(id, tenant_id, principal_id, endpoint, idempotency_key, request_fingerprint_sha256, "
                            + "response_status, created_at, expires_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?, 0, NOW(), NOW() + INTERVAL '24 hours') "
                            + "ON CONFLICT (tenant_id, principal_id, endpoint, idempotency_key) DO NOTHING RETURNING id",
                    UUID.class,
                    UUID.randomUUID(), tenantId, userId, PUSH_ENDPOINT, keyHash, fingerprint);
            if (claimId != null) {
                return new IdempotencyDecision(IdempotencyState.CLAIMED, fingerprint);
            }
        } catch (EmptyResultDataAccessException ignored) {
            // Existing winner: compare request identity below.
        }

        try {
            String stored = jdbcTemplate.queryForObject(
                    "SELECT request_fingerprint_sha256 FROM crm_idempotency_records "
                            + "WHERE tenant_id=? AND principal_id=? AND endpoint=? AND idempotency_key=? "
                            + "AND expires_at > NOW()",
                    String.class, tenantId, userId, PUSH_ENDPOINT, keyHash);
            if (stored == null) return new IdempotencyDecision(IdempotencyState.ERROR, fingerprint);
            return new IdempotencyDecision(
                    stored.equals(fingerprint) ? IdempotencyState.DUPLICATE : IdempotencyState.MISMATCH,
                    fingerprint);
        } catch (EmptyResultDataAccessException missing) {
            return new IdempotencyDecision(IdempotencyState.ERROR, fingerprint);
        }
    }

    private void completeIdempotency(UUID tenantId, UUID userId,
                                     MutationEnvelope mutation, int responseStatus) {
        String keyHash = computeSha256(mutation.idempotencyKey());
        jdbcTemplate.update(
                "UPDATE crm_idempotency_records SET response_status=? "
                        + "WHERE tenant_id=? AND principal_id=? AND endpoint=? AND idempotency_key=?",
                responseStatus, tenantId, userId, PUSH_ENDPOINT, keyHash);
    }

    /** Get current version of entity. Returns -1 if entity not found. */
    private long getCurrentVersion(String tableName, UUID tenantId, String entityId) {
        if (entityId == null || entityId.isBlank()) return -1;
        try {
            Long version = jdbcTemplate.queryForObject(
                "SELECT sync_version FROM " + tableName + " WHERE tenant_id = ? AND id = ?::UUID",
                Long.class, tenantId, entityId
            );
            return version != null ? version : -1;
        } catch (EmptyResultDataAccessException e) {
            return -1;
        }
    }

    /** Create a new entity. */
    private MutationResult createEntity(UUID tenantId, UUID userId, String tableName, MutationEnvelope mutation) {
        String entityId = mutation.entityId() != null ? mutation.entityId() : UUID.randomUUID().toString();

        JsonNode payload = mutation.payload();
        List<String> columns = new ArrayList<>(List.of("tenant_id", "id", "created_by", "updated_by", "sync_version"));
        List<Object> params = new ArrayList<>(List.of(tenantId, entityId, userId, userId, 1));
        String displayName = null;

        if (payload != null && payload.isObject()) {
            Set<String> allowed = allowedColumnsFor(tableName);
            payload.fields().forEachRemaining(entry -> {
                String key = entry.getKey();
                if (allowed.contains(key)) {
                    columns.add(key);
                    params.add(entry.getValue().asText());
                }
            });
            JsonNode dn = payload.get("display_name");
            if (dn != null && !dn.isNull()) displayName = dn.asText();
        }

        if (REQUIRES_NORMALIZED_NAME.contains(tableName)
                && !columns.contains("normalized_name") && displayName != null) {
            columns.add("normalized_name");
            params.add(displayName.toLowerCase());
        }

        columns.add("created_at");
        params.add(Timestamp.from(Instant.now()));
        columns.add("updated_at");
        params.add(Timestamp.from(Instant.now()));

        StringBuilder placeholders = new StringBuilder();
        for (int i = 0; i < columns.size(); i++) {
            placeholders.append(i == 0 ? "?" : ", ?");
        }

        String sql = String.format("INSERT INTO %s (%s) VALUES (%s)",
            tableName, String.join(", ", columns), placeholders);
        jdbcTemplate.update(sql, params.toArray());

        return new MutationResult(
            mutation.idempotencyKey(), entityId, "APPLIED", "201",
            1L, "\"1\"", null, null
        );
    }

    /** Update an existing entity with version check. */
    private MutationResult updateEntity(UUID tenantId, UUID userId, String tableName,
                                         MutationEnvelope mutation, long currentVersion,
                                         UUID deviceId) {
        JsonNode payload = mutation.payload();
        StringBuilder setClauses = new StringBuilder();
        List<Object> params = new ArrayList<>();

        if (payload != null && payload.isObject()) {
            Set<String> allowed = allowedColumnsFor(tableName);
            payload.fields().forEachRemaining(entry -> {
                String key = entry.getKey();
                if (allowed.contains(key)) {
                    if (!setClauses.isEmpty()) setClauses.append(", ");
                    setClauses.append(key).append(" = ?");
                    params.add(entry.getValue().asText());
                }
            });
        }

        if (setClauses.isEmpty()) {
            return new MutationResult(
                mutation.idempotencyKey(), mutation.entityId(),
                "APPLIED", "200", currentVersion, "\"" + currentVersion + "\"", null, null
            );
        }

        String sql = String.format("UPDATE %s SET %s WHERE tenant_id = ? AND id = ?::UUID AND sync_version = ?",
            tableName, setClauses);

        List<Object> allParams = new ArrayList<>(params);
        allParams.add(tenantId);
        allParams.add(mutation.entityId());
        allParams.add(currentVersion);
        int updated = jdbcTemplate.update(sql, allParams.toArray());

        if (updated == 0) {
            long actualVersion = getCurrentVersion(tableName, tenantId, mutation.entityId());
            return persistConflict(tenantId, deviceId, userId, tableName, mutation,
                    actualVersion >= 0 ? actualVersion : currentVersion, true);
        }

        long newVersion = currentVersion + 1;
        String etag = "\"" + newVersion + "\"";
        return new MutationResult(
            mutation.idempotencyKey(), mutation.entityId(), "APPLIED", "200",
            newVersion, etag, null, null
        );
    }

    /** Delete an entity using the expected/current version guard. */
    private MutationResult deleteEntity(UUID tenantId, UUID userId, String tableName,
                                         MutationEnvelope mutation, long currentVersion,
                                         UUID deviceId) {
        String sql = String.format(
            "DELETE FROM %s WHERE tenant_id = ? AND id = ?::UUID AND sync_version = ?",
            tableName);
        int deleted = jdbcTemplate.update(sql, tenantId, mutation.entityId(), currentVersion);

        if (deleted == 0) {
            long actualVersion = getCurrentVersion(tableName, tenantId, mutation.entityId());
            return persistConflict(tenantId, deviceId, userId, tableName, mutation,
                    actualVersion >= 0 ? actualVersion : currentVersion, true);
        }

        return new MutationResult(
            mutation.idempotencyKey(), mutation.entityId(), "APPLIED", "200",
            currentVersion + 1, null, null, null
        );
    }

    /** Persist and return a conflict result. */
    private MutationResult persistConflict(UUID tenantId, UUID deviceId, UUID userId,
                                            String tableName, MutationEnvelope mutation,
                                            long serverVersion, boolean clientModified) {
        JsonNode serverState = loadServerState(tableName, tenantId, mutation.entityId());
        long safeServerVersion = Math.max(serverVersion, 0L);
        long baseVersion = mutation.expectedVersion() != null ? mutation.expectedVersion() : 0L;
        ConflictService.ConflictDetection detection = conflictService.detectConflict(
                tenantId, deviceId, userId,
                mutation.entityType(), mutation.entityId(), baseVersion,
                mutation.payload(), safeServerVersion, serverState,
                mutation.operation(), false, clientModified);

        ObjectNode conflictInfo = objectMapper.createObjectNode();
        conflictInfo.put("serverVersion", safeServerVersion);
        conflictInfo.put("entityType", mutation.entityType());
        conflictInfo.put("entityId", mutation.entityId());
        conflictInfo.put("conflictType", detection.conflictType());
        conflictInfo.put("conflictClass", detection.conflictClass());
        conflictInfo.put("conflictId", detection.conflictId());
        conflictInfo.put("autoResolvable", detection.autoResolvable());

        return new MutationResult(
            mutation.idempotencyKey(), mutation.entityId(), "CONFLICT", "412",
            safeServerVersion, null, conflictInfo, null
        );
    }

    private JsonNode loadServerState(String tableName, UUID tenantId, String entityId) {
        try {
            String json = jdbcTemplate.queryForObject(
                    "SELECT to_jsonb(t)::text FROM " + tableName + " t WHERE tenant_id = ? AND id = ?::UUID",
                    String.class, tenantId, entityId);
            return json == null ? objectMapper.createObjectNode() : objectMapper.readTree(json);
        } catch (Exception e) {
            log.debug("Unable to load server state for conflict classification: table={} entity={}",
                    tableName, entityId, e);
            return objectMapper.createObjectNode();
        }
    }

    private MutationResult errorResult(MutationEnvelope mutation, String httpStatus, String message) {
        return new MutationResult(
            mutation.idempotencyKey(), mutation.entityId(), "REJECTED", httpStatus,
            null, null, null, message
        );
    }

    /**
     * Canonical SHA-256 fingerprint for a mutation business payload. The
     * idempotency key and client timestamp are excluded from identity.
     */
    static String computeMutationFingerprint(MutationEnvelope mutation) {
        StringBuilder canonical = new StringBuilder(512);
        appendCanonical(canonical, "entityType", normalize(mutation.entityType()));
        appendCanonical(canonical, "entityId", normalize(mutation.entityId()));
        appendCanonical(canonical, "operation", normalize(mutation.operation()));
        appendCanonical(canonical, "expectedVersion", mutation.expectedVersion());
        appendCanonical(canonical, "payload", canonicalJson(mutation.payload()));
        return computeSha256(canonical.toString());
    }

    private static String canonicalJson(JsonNode node) {
        if (node == null || node.isNull()) return "null";
        if (node.isObject()) {
            TreeMap<String, String> fields = new TreeMap<>();
            Iterator<Map.Entry<String, JsonNode>> iterator = node.fields();
            while (iterator.hasNext()) {
                Map.Entry<String, JsonNode> entry = iterator.next();
                fields.put(entry.getKey(), canonicalJson(entry.getValue()));
            }
            StringBuilder out = new StringBuilder("{");
            for (Map.Entry<String, String> entry : fields.entrySet()) {
                appendCanonical(out, entry.getKey(), entry.getValue());
            }
            return out.append('}').toString();
        }
        if (node.isArray()) {
            StringBuilder out = new StringBuilder("[");
            for (int i = 0; i < node.size(); i++) {
                appendCanonical(out, Integer.toString(i), canonicalJson(node.get(i)));
            }
            return out.append(']').toString();
        }
        return node.toString();
    }

    private static void appendCanonical(StringBuilder out, String key, Object value) {
        String text = value == null ? "<null>" : String.valueOf(value);
        out.append(key.length()).append(':').append(key).append('=')
                .append(text.length()).append(':').append(text).append(';');
    }

    private static String normalize(String value) {
        return value == null ? null : value.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private static String computeSha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is not available", impossible);
        }
    }

    private int parseStatus(String status) {
        try { return Integer.parseInt(status); }
        catch (Exception ignored) { return 500; }
    }

    private enum IdempotencyState { CLAIMED, DUPLICATE, MISMATCH, ERROR }
    private record IdempotencyDecision(IdempotencyState state, String fingerprint) {}
}
