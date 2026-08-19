package com.sanad.platform.crm.mobile.sync.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sanad.platform.crm.idempotency.IdempotencyService;
import com.sanad.platform.crm.mobile.sync.model.PushSyncRequest;
import com.sanad.platform.crm.mobile.sync.model.PushSyncRequest.MutationEnvelope;
import com.sanad.platform.crm.mobile.sync.model.PushSyncResponse;
import com.sanad.platform.crm.mobile.sync.model.PushSyncResponse.MutationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Savepoint;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Loss-safe batch push service.
 *
 * Each mutation gets an explicit JDBC savepoint inside the request transaction.
 * A statement failure is rolled back to that savepoint before processing the
 * next mutation, which is required by PostgreSQL once a statement aborts a
 * transaction. Mutation-level idempotency uses the platform canonical
 * crm_idempotency_records service rather than a second mobile-only store.
 */
@Service
public class PushSyncService {

    private static final Logger log = LoggerFactory.getLogger(PushSyncService.class);
    private static final String IDEMPOTENCY_ENDPOINT = "POST:/api/v2/mobile/sync/push:mutation";

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final IdempotencyService idempotencyService;

    private static final Map<String, String> ENTITY_TABLES = Map.of(
        "account", "crm_accounts",
        "contact", "crm_contacts",
        "lead", "crm_leads",
        "opportunity", "crm_opportunities",
        "task", "crm_tasks",
        "note", "crm_notes",
        "activity", "crm_activities"
    );

    private static final Map<String, Set<String>> ALLOWED_COLUMNS = Map.of(
        "crm_accounts", Set.of("name", "industry", "phone", "website", "owner_id", "status"),
        "crm_contacts", Set.of("account_id", "first_name", "last_name", "email", "phone", "title", "status"),
        "crm_leads", Set.of("first_name", "last_name", "email", "phone", "status", "source", "owner_id"),
        "crm_opportunities", Set.of("account_id", "contact_id", "pipeline_id", "title", "amount", "stage", "close_date"),
        "crm_tasks", Set.of("title", "description", "status", "due_date", "assigned_to", "priority"),
        "crm_notes", Set.of("entity_type", "entity_id", "content"),
        "crm_activities", Set.of("entity_type", "entity_id", "activity_type", "description", "result")
    );

    private static final Set<String> UUID_COLUMNS = Set.of(
        "owner_id", "account_id", "contact_id", "pipeline_id", "assigned_to", "entity_id"
    );

    @Autowired
    public PushSyncService(JdbcTemplate jdbcTemplate,
                           ObjectMapper objectMapper,
                           IdempotencyService idempotencyService) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.idempotencyService = idempotencyService;
    }

    /** Convenience constructor retained for focused unit tests. */
    public PushSyncService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this(jdbcTemplate, objectMapper, new IdempotencyService.InMemoryIdempotencyService());
    }

    @Transactional
    public PushSyncResponse push(UUID tenantId, UUID deviceId, UUID userId, PushSyncRequest request) {
        List<MutationResult> results = new ArrayList<>();
        int applied = 0;
        int rejected = 0;
        int duplicates = 0;

        DataSource dataSource = jdbcTemplate.getDataSource();
        Connection connection = dataSource == null ? null : DataSourceUtils.getConnection(dataSource);

        for (MutationEnvelope mutation : request.mutations()) {
            Savepoint savepoint = null;
            MutationResult result;
            try {
                if (connection != null) {
                    savepoint = connection.setSavepoint();
                }
                result = processMutation(tenantId, deviceId, userId, mutation);
                if (connection != null && savepoint != null) {
                    connection.releaseSavepoint(savepoint);
                }
            } catch (Exception e) {
                rollbackMutation(connection, savepoint, mutation, e);
                result = errorResult(mutation, "500", "Mutation failed and was rolled back");
            }

            results.add(result);
            switch (result.status()) {
                case "APPLIED" -> applied++;
                case "REJECTED", "CONFLICT" -> rejected++;
                case "DUPLICATE" -> duplicates++;
                default -> rejected++;
            }
        }

        log.info("Push sync: tenant={}, device={}, total={}, applied={}, rejected={}, duplicates={}",
            tenantId, deviceId, request.mutations().size(), applied, rejected, duplicates);

        return new PushSyncResponse(
            request.mutations().size(), applied, rejected, duplicates, results
        );
    }

    private MutationResult processMutation(UUID tenantId, UUID deviceId, UUID userId,
                                           MutationEnvelope mutation) throws Exception {
        UUID idempotencyOperation = null;
        if (mutation.idempotencyKey() != null && !mutation.idempotencyKey().isBlank()) {
            String fingerprint = IdempotencyService.fingerprint(
                "SYNC_MUTATION",
                IDEMPOTENCY_ENDPOINT,
                mutationFingerprintMaterial(mutation)
            );
            IdempotencyService.Replay replay = idempotencyService.begin(
                tenantId, userId, IDEMPOTENCY_ENDPOINT, mutation.idempotencyKey(), fingerprint
            );
            if (replay instanceof IdempotencyService.Replay.ReplayHit) {
                return new MutationResult(
                    mutation.idempotencyKey(), mutation.entityId(), "DUPLICATE", "200",
                    null, null, null, "Duplicate mutation — canonical idempotency replay"
                );
            }
            idempotencyOperation = ((IdempotencyService.Replay.ReplayMiss) replay).operationId();
        }

        MutationResult result = applyMutation(tenantId, userId, mutation);

        if (idempotencyOperation != null) {
            idempotencyService.complete(
                idempotencyOperation,
                Integer.parseInt(result.httpStatus()),
                objectMapper.writeValueAsString(result),
                "{}",
                "application/json"
            );
        }
        return result;
    }

    private MutationResult applyMutation(UUID tenantId, UUID userId, MutationEnvelope mutation) {
        String entityType = mutation.entityType() == null ? "" : mutation.entityType().toLowerCase();
        String tableName = ENTITY_TABLES.get(entityType);
        if (tableName == null) {
            return errorResult(mutation, "400", "Unknown entity type: " + mutation.entityType());
        }

        String operation = mutation.operation() == null ? "" : mutation.operation().toUpperCase();
        long currentVersion = getCurrentVersion(tableName, tenantId, mutation.entityId());

        if ("CREATE".equals(operation)) {
            if (currentVersion >= 0) {
                return errorResult(mutation, "409", "Entity already exists: " + mutation.entityId());
            }
            return createEntity(tenantId, userId, tableName, mutation);
        }

        if (currentVersion < 0) {
            return errorResult(mutation, "404", "Entity not found: " + mutation.entityId());
        }

        if (mutation.expectedVersion() == null) {
            return errorResult(mutation, "428", "expectedVersion is required for UPDATE/DELETE");
        }
        if (currentVersion != mutation.expectedVersion()) {
            return conflictResult(mutation, currentVersion);
        }

        return switch (operation) {
            case "UPDATE" -> updateEntity(tenantId, tableName, mutation, currentVersion);
            case "DELETE" -> deleteEntity(tenantId, tableName, mutation, currentVersion);
            default -> errorResult(mutation, "400", "Unknown operation: " + mutation.operation());
        };
    }

    private long getCurrentVersion(String tableName, UUID tenantId, String entityId) {
        if (entityId == null || entityId.isBlank()) {
            return -1L;
        }
        try {
            Long version = jdbcTemplate.queryForObject(
                "SELECT sync_version FROM " + tableName + " WHERE tenant_id = ? AND id = ?::UUID",
                Long.class, tenantId, entityId
            );
            return version == null ? -1L : version;
        } catch (EmptyResultDataAccessException notFound) {
            return -1L;
        }
    }

    private MutationResult createEntity(UUID tenantId, UUID userId, String tableName,
                                        MutationEnvelope mutation) {
        String entityId = mutation.entityId() != null ? mutation.entityId() : UUID.randomUUID().toString();
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
                if (allowed.contains(key)) {
                    columns.append(", ").append(key);
                    placeholders.append(", ").append(bindExpression(key));
                    params.add(jdbcValue(key, entry.getValue()));
                }
            });
        }

        String sql = String.format(
            "INSERT INTO %s (%s, created_at, updated_at) VALUES (%s, NOW(), NOW())",
            tableName, columns, placeholders
        );
        jdbcTemplate.update(sql, params.toArray());

        return new MutationResult(
            mutation.idempotencyKey(), entityId, "APPLIED", "201",
            1L, "\"1\"", null, null
        );
    }

    private MutationResult updateEntity(UUID tenantId, String tableName,
                                        MutationEnvelope mutation, long currentVersion) {
        JsonNode payload = mutation.payload();
        StringBuilder setClauses = new StringBuilder();
        List<Object> params = new ArrayList<>();

        if (payload != null && payload.isObject()) {
            Set<String> allowed = allowedColumnsFor(tableName);
            payload.fields().forEachRemaining(entry -> {
                String key = entry.getKey();
                if (allowed.contains(key)) {
                    if (!setClauses.isEmpty()) {
                        setClauses.append(", ");
                    }
                    setClauses.append(key).append(" = ").append(bindExpression(key));
                    params.add(jdbcValue(key, entry.getValue()));
                }
            });
        }

        if (setClauses.isEmpty()) {
            return new MutationResult(
                mutation.idempotencyKey(), mutation.entityId(), "APPLIED", "200",
                currentVersion, "\"" + currentVersion + "\"", null, null
            );
        }

        String sql = String.format(
            "UPDATE %s SET %s WHERE tenant_id = ? AND id = ?::UUID AND sync_version = ?",
            tableName, setClauses
        );

        // Critical fix: append guard parameters to the same flat parameter list.
        // The previous implementation passed params.toArray() as parameter #1.
        params.add(tenantId);
        params.add(mutation.entityId());
        params.add(currentVersion);
        int updated = jdbcTemplate.update(sql, params.toArray());

        if (updated == 0) {
            long serverVersion = getCurrentVersion(tableName, tenantId, mutation.entityId());
            return conflictResult(mutation, serverVersion < 0 ? currentVersion : serverVersion);
        }

        long newVersion = currentVersion + 1;
        return new MutationResult(
            mutation.idempotencyKey(), mutation.entityId(), "APPLIED", "200",
            newVersion, "\"" + newVersion + "\"", null, null
        );
    }

    private MutationResult deleteEntity(UUID tenantId, String tableName,
                                        MutationEnvelope mutation, long currentVersion) {
        String sql = String.format(
            "UPDATE %s SET deleted_at = NOW(), updated_at = NOW() " +
            "WHERE tenant_id = ? AND id = ?::UUID AND sync_version = ?",
            tableName
        );
        int deleted = jdbcTemplate.update(sql, tenantId, mutation.entityId(), currentVersion);
        if (deleted == 0) {
            long serverVersion = getCurrentVersion(tableName, tenantId, mutation.entityId());
            return conflictResult(mutation, serverVersion < 0 ? currentVersion : serverVersion);
        }

        long newVersion = currentVersion + 1;
        return new MutationResult(
            mutation.idempotencyKey(), mutation.entityId(), "APPLIED", "200",
            newVersion, "\"" + newVersion + "\"", null, null
        );
    }

    private String bindExpression(String column) {
        return UUID_COLUMNS.contains(column) ? "?::UUID" : "?";
    }

    private Object jdbcValue(String column, JsonNode value) {
        if (value == null || value.isNull()) {
            return null;
        }
        if (UUID_COLUMNS.contains(column)) {
            return UUID.fromString(value.asText());
        }
        if (value.isBoolean()) {
            return value.booleanValue();
        }
        if (value.isIntegralNumber()) {
            return value.longValue();
        }
        if (value.isFloatingPointNumber() || value.isBigDecimal()) {
            return value.decimalValue();
        }
        return value.asText();
    }

    private MutationResult conflictResult(MutationEnvelope mutation, long serverVersion) {
        ObjectNode conflictInfo = objectMapper.createObjectNode();
        conflictInfo.put("serverVersion", serverVersion);
        conflictInfo.put("entityType", mutation.entityType());
        conflictInfo.put("entityId", mutation.entityId());
        conflictInfo.put("conflictType", "VERSION_MISMATCH");

        return new MutationResult(
            mutation.idempotencyKey(), mutation.entityId(), "CONFLICT", "412",
            serverVersion, null, conflictInfo, null
        );
    }

    private MutationResult errorResult(MutationEnvelope mutation, String httpStatus, String message) {
        return new MutationResult(
            mutation.idempotencyKey(), mutation.entityId(), "REJECTED", httpStatus,
            null, null, null, message
        );
    }

    private Set<String> allowedColumnsFor(String tableName) {
        return ALLOWED_COLUMNS.getOrDefault(tableName, Set.of());
    }

    private String mutationFingerprintMaterial(MutationEnvelope mutation) {
        return String.join("|",
            String.valueOf(mutation.entityType()),
            String.valueOf(mutation.entityId()),
            String.valueOf(mutation.operation()),
            String.valueOf(mutation.expectedVersion()),
            mutation.payload() == null ? "" : mutation.payload().toString()
        );
    }

    private void rollbackMutation(Connection connection, Savepoint savepoint,
                                  MutationEnvelope mutation, Exception e) {
        try {
            if (connection != null && savepoint != null) {
                connection.rollback(savepoint);
                connection.releaseSavepoint(savepoint);
            }
        } catch (Exception rollbackError) {
            e.addSuppressed(rollbackError);
        }
        log.error("Mutation rolled back: key={}, entity={}/{}, operation={}",
            mutation.idempotencyKey(), mutation.entityType(), mutation.entityId(), mutation.operation(), e);
    }
}
