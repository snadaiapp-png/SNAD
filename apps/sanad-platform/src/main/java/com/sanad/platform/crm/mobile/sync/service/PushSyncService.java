package com.sanad.platform.crm.mobile.sync.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.sanad.platform.crm.mobile.sync.model.PushSyncRequest;
import com.sanad.platform.crm.mobile.sync.model.PushSyncRequest.MutationEnvelope;
import com.sanad.platform.crm.mobile.sync.model.PushSyncResponse;
import com.sanad.platform.crm.mobile.sync.model.PushSyncResponse.MutationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * G7 batch push service.
 *
 * <p>Each mutation is committed in an independent transaction. This is
 * intentional: PostgreSQL marks a transaction aborted after a statement error,
 * so catching the Java exception inside one batch transaction cannot provide
 * the per-mutation isolation required by SYNC-009/ISO-004.</p>
 */
@Service
public class PushSyncService {

    private static final Logger log = LoggerFactory.getLogger(PushSyncService.class);

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate mutationTransaction;

    private static final Map<String, String> ENTITY_TABLES = Map.of(
        "account", "crm_accounts",
        "contact", "crm_contacts",
        "lead", "crm_leads",
        "opportunity", "crm_opportunities",
        "task", "crm_tasks",
        "note", "crm_notes",
        "activity", "crm_activities"
    );

    /** Mobile/legacy field names map onto the canonical unified CRM schema. */
    private static final Map<String, Map<String, String>> WRITABLE_FIELDS = Map.of(
        "crm_accounts", fields(
            "display_name", "display_name", "name", "display_name",
            "account_type", "account_type", "lifecycle_status", "lifecycle_status", "status", "lifecycle_status",
            "parent_account_id", "parent_account_id", "owner_user_id", "owner_user_id", "owner_id", "owner_user_id",
            "primary_currency_code", "primary_currency_code", "preferred_locale", "preferred_locale",
            "time_zone", "time_zone", "source", "source"),
        "crm_contacts", fields(
            "account_id", "account_id", "given_name", "given_name", "first_name", "given_name",
            "family_name", "family_name", "last_name", "family_name", "display_name", "display_name",
            "primary_email", "primary_email", "email", "primary_email", "primary_phone", "primary_phone", "phone", "primary_phone",
            "preferred_locale", "preferred_locale", "time_zone", "time_zone",
            "lifecycle_status", "lifecycle_status", "status", "lifecycle_status",
            "owner_user_id", "owner_user_id", "owner_id", "owner_user_id", "consent_summary", "consent_summary"),
        "crm_leads", fields(
            "display_name", "display_name", "name", "display_name", "company_name", "company_name",
            "email", "email", "phone", "phone", "source", "source", "status", "status",
            "owner_user_id", "owner_user_id", "owner_id", "owner_user_id", "queue_id", "queue_id", "score", "score"),
        "crm_opportunities", fields(
            "account_id", "account_id", "contact_id", "contact_id", "pipeline_id", "pipeline_id", "stage_id", "stage_id",
            "name", "name", "title", "name", "amount", "amount", "currency_code", "currency_code",
            "probability", "probability", "forecast_category", "forecast_category",
            "expected_close_date", "expected_close_date", "close_date", "expected_close_date",
            "owner_user_id", "owner_user_id", "owner_id", "owner_user_id", "status", "status", "win_loss_reason", "win_loss_reason"),
        "crm_tasks", fields(
            "title", "title", "description", "description", "related_type", "related_type", "related_id", "related_id",
            "assignee_user_id", "assignee_user_id", "assigned_to", "assignee_user_id", "owner_user_id", "owner_user_id",
            "status", "status", "priority", "priority", "start_at", "start_at", "due_at", "due_at", "due_date", "due_at",
            "completed_at", "completed_at", "result", "result"),
        "crm_notes", fields(
            "subject_type", "subject_type", "entity_type", "subject_type", "subject_id", "subject_id", "entity_id", "subject_id",
            "body", "body", "content", "body", "author_user_id", "author_user_id", "archived", "archived"),
        "crm_activities", fields(
            "activity_type", "activity_type", "subject", "subject", "body", "body", "description", "body",
            "related_type", "related_type", "entity_type", "related_type", "related_id", "related_id", "entity_id", "related_id",
            "owner_user_id", "owner_user_id", "status", "status", "priority", "priority",
            "start_at", "start_at", "due_at", "due_at", "completed_at", "completed_at")
    );

    private static final Set<String> UUID_COLUMNS = Set.of(
        "parent_account_id", "owner_user_id", "account_id", "queue_id", "contact_id", "pipeline_id", "stage_id",
        "related_id", "assignee_user_id", "subject_id", "author_user_id"
    );
    private static final Set<String> DATE_COLUMNS = Set.of("expected_close_date");
    private static final Set<String> TIMESTAMP_COLUMNS = Set.of("start_at", "due_at", "completed_at");
    private static final Set<String> NUMERIC_COLUMNS = Set.of("amount", "probability", "score");
    private static final Set<String> INTEGER_COLUMNS = Set.of("priority");
    private static final Set<String> BOOLEAN_COLUMNS = Set.of("archived");

    @Autowired
    public PushSyncService(JdbcTemplate jdbcTemplate,
                           ObjectMapper objectMapper,
                           PlatformTransactionManager transactionManager) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.mutationTransaction = new TransactionTemplate(transactionManager);
        this.mutationTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** Constructor retained for focused unit tests without a Spring context. */
    public PushSyncService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.mutationTransaction = null;
    }

    public PushSyncResponse push(UUID tenantId, UUID deviceId, UUID userId, PushSyncRequest request) {
        List<MutationResult> results = new ArrayList<>();
        int applied = 0, rejected = 0, duplicates = 0;

        for (MutationEnvelope mutation : request.mutations()) {
            MutationResult result = executeMutation(tenantId, deviceId, userId, mutation);
            results.add(result);
            switch (result.status()) {
                case "APPLIED" -> applied++;
                case "REJECTED", "CONFLICT" -> rejected++;
                case "DUPLICATE" -> duplicates++;
                default -> log.warn("Unexpected mutation status: {}", result.status());
            }
        }

        log.info("Push sync: tenant={}, device={}, total={}, applied={}, rejected={}, duplicates={}",
            tenantId, deviceId, request.mutations().size(), applied, rejected, duplicates);
        return new PushSyncResponse(request.mutations().size(), applied, rejected, duplicates, results);
    }

    private MutationResult executeMutation(UUID tenantId, UUID deviceId, UUID userId, MutationEnvelope mutation) {
        try {
            if (mutationTransaction == null) {
                return processMutation(tenantId, deviceId, userId, mutation);
            }
            MutationResult result = mutationTransaction.execute(
                status -> processMutation(tenantId, deviceId, userId, mutation));
            if (result == null) {
                return errorResult(mutation, "500", "Mutation transaction returned no result");
            }
            return result;
        } catch (Exception e) {
            log.error("Isolated mutation failed: key={}", mutation.idempotencyKey(), e);
            return errorResult(mutation, "500", "Internal error: " + e.getMessage());
        }
    }

    private MutationResult processMutation(UUID tenantId, UUID deviceId, UUID userId, MutationEnvelope mutation) {
        if (isDuplicate(tenantId, mutation.idempotencyKey())) {
            return new MutationResult(mutation.idempotencyKey(), mutation.entityId(),
                "DUPLICATE", "200", null, null, null, "Duplicate mutation — already processed");
        }

        String tableName = ENTITY_TABLES.get(mutation.entityType().toLowerCase(Locale.ROOT));
        if (tableName == null) {
            return errorResult(mutation, "400", "Unknown entity type: " + mutation.entityType());
        }

        long currentVersion = getCurrentVersion(tableName, tenantId, mutation.entityId());
        if (currentVersion < 0) {
            if ("CREATE".equals(mutation.operation())) {
                return createEntity(tenantId, userId, tableName, mutation);
            }
            return errorResult(mutation, "404", "Entity not found: " + mutation.entityId());
        }

        if (!"CREATE".equals(mutation.operation()) && mutation.expectedVersion() != null
                && currentVersion != mutation.expectedVersion()) {
            return conflictResult(mutation, currentVersion);
        }

        return switch (mutation.operation()) {
            case "CREATE" -> errorResult(mutation, "409", "Entity already exists: " + mutation.entityId());
            case "UPDATE" -> updateEntity(tenantId, tableName, mutation, currentVersion);
            case "DELETE" -> deleteEntity(tenantId, userId, tableName, mutation, currentVersion);
            default -> errorResult(mutation, "400", "Unknown operation: " + mutation.operation());
        };
    }

    private boolean isDuplicate(UUID tenantId, String idempotencyKey) {
        if (idempotencyKey == null) return false;
        String sha256 = computeSha256(idempotencyKey);
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM platform_audit_logs WHERE tenant_id = ? AND idempotency_key = ? AND created_at > NOW() - INTERVAL '24 hours'",
            Integer.class, tenantId, sha256);
        return count != null && count > 0;
    }

    private long getCurrentVersion(String tableName, UUID tenantId, String entityId) {
        if (entityId == null) return -1;
        try {
            Long version = jdbcTemplate.queryForObject(
                "SELECT sync_version FROM " + tableName + " WHERE tenant_id = ? AND id = ?::UUID",
                Long.class, tenantId, entityId);
            return version != null ? version : -1;
        } catch (Exception e) {
            return -1;
        }
    }

    private MutationResult createEntity(UUID tenantId, UUID userId, String tableName, MutationEnvelope mutation) {
        String entityId = mutation.entityId() != null ? mutation.entityId() : UUID.randomUUID().toString();
        LinkedHashMap<String, JsonNode> values = canonicalPayload(tableName, mutation.payload());
        String validationError = prepareAndValidateCreate(tableName, values);
        if (validationError != null) {
            return errorResult(mutation, "400", validationError);
        }

        StringBuilder columns = new StringBuilder("tenant_id, id, created_by, updated_by, sync_version");
        StringBuilder placeholders = new StringBuilder("?, ?::UUID, ?, ?, 1");
        List<Object> params = new ArrayList<>();
        params.add(tenantId);
        params.add(entityId);
        params.add(userId);
        params.add(userId);

        for (Map.Entry<String, JsonNode> entry : values.entrySet()) {
            columns.append(", ").append(entry.getKey());
            placeholders.append(", ").append(bindExpression(entry.getKey()));
            params.add(bindValue(entry.getValue()));
        }

        String sql = String.format(
            "INSERT INTO %s (%s, created_at, updated_at) VALUES (%s, NOW(), NOW())",
            tableName, columns, placeholders);
        jdbcTemplate.update(sql, params.toArray());
        recordIdempotency(tenantId, mutation.idempotencyKey(), mutation.operation(), entityId);

        return new MutationResult(mutation.idempotencyKey(), entityId,
            "APPLIED", "201", 1L, "\"1\"", null, null);
    }

    private MutationResult updateEntity(UUID tenantId, String tableName,
                                        MutationEnvelope mutation, long currentVersion) {
        LinkedHashMap<String, JsonNode> values = canonicalPayload(tableName, mutation.payload());
        if (values.isEmpty()) {
            return errorResult(mutation, "400", "No writable fields supplied");
        }

        StringBuilder setClauses = new StringBuilder();
        List<Object> params = new ArrayList<>();
        for (Map.Entry<String, JsonNode> entry : values.entrySet()) {
            if (!setClauses.isEmpty()) setClauses.append(", ");
            setClauses.append(entry.getKey()).append(" = ").append(bindExpression(entry.getKey()));
            params.add(bindValue(entry.getValue()));
        }

        String sql = String.format(
            "UPDATE %s SET %s WHERE tenant_id = ? AND id = ?::UUID AND sync_version = ?",
            tableName, setClauses);
        params.add(tenantId);
        params.add(mutation.entityId());
        params.add(currentVersion);

        int updated = jdbcTemplate.update(sql, params.toArray());
        if (updated == 0) {
            return conflictResult(mutation, currentVersion);
        }

        long newVersion = currentVersion + 1;
        recordIdempotency(tenantId, mutation.idempotencyKey(), mutation.operation(), mutation.entityId());
        return new MutationResult(mutation.idempotencyKey(), mutation.entityId(),
            "APPLIED", "200", newVersion, "\"" + newVersion + "\"", null, null);
    }

    private MutationResult deleteEntity(UUID tenantId, UUID userId, String tableName,
                                        MutationEnvelope mutation, long currentVersion) {
        String assignment = switch (tableName) {
            case "crm_accounts", "crm_contacts" -> "archived_at = NOW(), lifecycle_status = 'ARCHIVED', updated_by = ?";
            case "crm_leads", "crm_opportunities" -> "status = 'ARCHIVED', updated_by = ?";
            case "crm_tasks" -> "status = 'CANCELLED', updated_by = ?";
            case "crm_notes" -> "archived = TRUE, updated_by = ?";
            case "crm_activities" -> "status = 'ARCHIVED', updated_by = ?";
            default -> throw new IllegalArgumentException("Unsupported delete entity table: " + tableName);
        };
        String sql = String.format(
            "UPDATE %s SET %s, updated_at = NOW() WHERE tenant_id = ? AND id = ?::UUID AND sync_version = ?",
            tableName, assignment);
        int deleted = jdbcTemplate.update(sql, userId, tenantId, mutation.entityId(), currentVersion);
        if (deleted == 0) {
            return conflictResult(mutation, currentVersion);
        }
        recordIdempotency(tenantId, mutation.idempotencyKey(), mutation.operation(), mutation.entityId());
        return new MutationResult(mutation.idempotencyKey(), mutation.entityId(),
            "APPLIED", "200", currentVersion + 1, "\"" + (currentVersion + 1) + "\"", null, null);
    }

    private LinkedHashMap<String, JsonNode> canonicalPayload(String tableName, JsonNode payload) {
        LinkedHashMap<String, JsonNode> values = new LinkedHashMap<>();
        if (payload == null || !payload.isObject()) return values;
        Map<String, String> mapping = WRITABLE_FIELDS.getOrDefault(tableName, Map.of());
        payload.fields().forEachRemaining(entry -> {
            String column = mapping.get(entry.getKey());
            if (column != null) values.put(column, entry.getValue());
        });
        return values;
    }

    private String prepareAndValidateCreate(String tableName, LinkedHashMap<String, JsonNode> values) {
        switch (tableName) {
            case "crm_accounts" -> {
                if (blank(values.get("display_name"))) return "display_name (or name) is required";
                values.putIfAbsent("account_type", TextNode.valueOf("OTHER"));
                values.putIfAbsent("lifecycle_status", TextNode.valueOf("ACTIVE"));
                values.put("normalized_name", TextNode.valueOf(normalize(values.get("display_name").asText())));
            }
            case "crm_contacts" -> {
                if (blank(values.get("given_name"))) return "given_name (or first_name) is required";
                String display = blank(values.get("display_name"))
                    ? values.get("given_name").asText() + (blank(values.get("family_name")) ? "" : " " + values.get("family_name").asText())
                    : values.get("display_name").asText();
                values.put("display_name", TextNode.valueOf(display.trim()));
                values.put("normalized_name", TextNode.valueOf(normalize(display)));
                values.putIfAbsent("lifecycle_status", TextNode.valueOf("ACTIVE"));
                values.putIfAbsent("consent_summary", TextNode.valueOf("UNKNOWN"));
            }
            case "crm_leads" -> {
                if (blank(values.get("display_name"))) return "display_name (or name) is required";
                values.put("normalized_name", TextNode.valueOf(normalize(values.get("display_name").asText())));
                values.putIfAbsent("status", TextNode.valueOf("NEW"));
            }
            case "crm_opportunities" -> {
                if (blank(values.get("name"))) return "name (or title) is required";
                if (blank(values.get("pipeline_id"))) return "pipeline_id is required";
                if (blank(values.get("stage_id"))) return "stage_id is required";
                if (blank(values.get("currency_code"))) return "currency_code is required";
            }
            case "crm_tasks" -> {
                if (blank(values.get("title"))) return "title is required";
            }
            case "crm_notes" -> {
                if (blank(values.get("subject_type"))) return "subject_type (or entity_type) is required";
                if (blank(values.get("subject_id"))) return "subject_id (or entity_id) is required";
                if (blank(values.get("body"))) return "body (or content) is required";
            }
            case "crm_activities" -> {
                if (blank(values.get("activity_type"))) return "activity_type is required";
                if (blank(values.get("subject"))) {
                    String subject = blank(values.get("body")) ? "Mobile activity" : values.get("body").asText();
                    values.put("subject", TextNode.valueOf(subject.length() > 240 ? subject.substring(0, 240) : subject));
                }
            }
            default -> { return "Unsupported entity table: " + tableName; }
        }
        return null;
    }

    private boolean blank(JsonNode node) {
        return node == null || node.isNull() || node.asText().isBlank();
    }

    private String normalize(String input) {
        return input == null ? "" : input.trim().toLowerCase(Locale.ROOT);
    }

    private String bindExpression(String column) {
        if (UUID_COLUMNS.contains(column)) return "?::UUID";
        if (DATE_COLUMNS.contains(column)) return "?::DATE";
        if (TIMESTAMP_COLUMNS.contains(column)) return "?::TIMESTAMPTZ";
        if (NUMERIC_COLUMNS.contains(column)) return "?::NUMERIC";
        if (INTEGER_COLUMNS.contains(column)) return "?::INTEGER";
        if (BOOLEAN_COLUMNS.contains(column)) return "?::BOOLEAN";
        return "?";
    }

    private Object bindValue(JsonNode value) {
        return value == null || value.isNull() ? null : value.asText();
    }

    private MutationResult conflictResult(MutationEnvelope mutation, long serverVersion) {
        ObjectNode conflictInfo = objectMapper.createObjectNode();
        conflictInfo.put("serverVersion", serverVersion);
        conflictInfo.put("entityType", mutation.entityType());
        conflictInfo.put("entityId", mutation.entityId());
        conflictInfo.put("conflictType", "VERSION_MISMATCH");
        return new MutationResult(mutation.idempotencyKey(), mutation.entityId(),
            "CONFLICT", "412", serverVersion, null, conflictInfo, null);
    }

    private MutationResult errorResult(MutationEnvelope mutation, String httpStatus, String message) {
        return new MutationResult(mutation.idempotencyKey(), mutation.entityId(),
            "REJECTED", httpStatus, null, null, null, message);
    }

    private void recordIdempotency(UUID tenantId, String idempotencyKey, String operation, String entityId) {
        if (idempotencyKey == null) return;
        String sha256 = computeSha256(idempotencyKey);
        jdbcTemplate.update(
            "INSERT INTO platform_audit_logs (id, tenant_id, entity_type, entity_id, action, idempotency_key, created_at) " +
            "VALUES (gen_random_uuid(), ?, 'SYNC_MUTATION', ?::UUID, ?, ?, NOW())",
            tenantId, entityId, operation, sha256);
    }

    private String computeSha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(input.getBytes()));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static Map<String, String> fields(String... entries) {
        if (entries.length % 2 != 0) throw new IllegalArgumentException("field mapping must be key/value pairs");
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < entries.length; i += 2) map.put(entries[i], entries[i + 1]);
        return Map.copyOf(map);
    }
}
