package com.sanad.platform.crm.mobile.sync.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sanad.platform.crm.idempotency.IdempotencyService;
import com.sanad.platform.crm.mobile.conflict.service.ConflictService;
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
import java.sql.Connection;
import java.sql.Savepoint;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Loss-safe batch push with canonical schema translation and per-mutation savepoints. */
@Service
public class PushSyncService {
    private static final Logger log = LoggerFactory.getLogger(PushSyncService.class);
    private static final String IDEMPOTENCY_ENDPOINT = "POST:/api/v2/mobile/sync/push:mutation";

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final IdempotencyService idempotencyService;
    private final ConflictService conflictService;

    @Autowired
    public PushSyncService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper,
                           IdempotencyService idempotencyService, ConflictService conflictService) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.idempotencyService = idempotencyService;
        this.conflictService = conflictService;
    }

    /** Convenience constructor retained for focused unit tests. */
    public PushSyncService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this(jdbcTemplate, objectMapper, new IdempotencyService.InMemoryIdempotencyService(), null);
    }

    @Transactional
    public PushSyncResponse push(UUID tenantId, UUID deviceId, UUID userId, PushSyncRequest request) {
        List<MutationResult> results = new ArrayList<>();
        int applied = 0, rejected = 0, duplicates = 0;
        DataSource dataSource = jdbcTemplate.getDataSource();
        Connection connection = dataSource == null ? null : DataSourceUtils.getConnection(dataSource);

        for (MutationEnvelope mutation : request.mutations()) {
            Savepoint savepoint = null;
            MutationResult result;
            try {
                if (connection != null) savepoint = connection.setSavepoint();
                result = processMutation(tenantId, deviceId, userId, mutation);
                if (connection != null && savepoint != null) connection.releaseSavepoint(savepoint);
            } catch (Exception e) {
                rollbackMutation(connection, savepoint, mutation, e);
                result = errorResult(mutation, "500", "Mutation failed and was rolled back");
            }
            results.add(result);
            switch (result.status()) {
                case "APPLIED" -> applied++;
                case "DUPLICATE" -> duplicates++;
                default -> rejected++;
            }
        }

        log.info("Push sync: tenant={}, device={}, total={}, applied={}, rejected={}, duplicates={}",
            tenantId, deviceId, request.mutations().size(), applied, rejected, duplicates);
        return new PushSyncResponse(request.mutations().size(), applied, rejected, duplicates, results);
    }

    private MutationResult processMutation(UUID tenantId, UUID deviceId, UUID userId, MutationEnvelope mutation) throws Exception {
        UUID idempotencyOperation = null;
        if (mutation.idempotencyKey() != null && !mutation.idempotencyKey().isBlank()) {
            String fingerprint = IdempotencyService.fingerprint("SYNC_MUTATION", IDEMPOTENCY_ENDPOINT, mutationFingerprintMaterial(mutation));
            IdempotencyService.Replay replay = idempotencyService.begin(tenantId, userId, IDEMPOTENCY_ENDPOINT, mutation.idempotencyKey(), fingerprint);
            if (replay instanceof IdempotencyService.Replay.ReplayHit) {
                return new MutationResult(mutation.idempotencyKey(), mutation.entityId(), "DUPLICATE", "200",
                    null, null, null, "Duplicate mutation — canonical idempotency replay");
            }
            idempotencyOperation = ((IdempotencyService.Replay.ReplayMiss) replay).operationId();
        }

        MutationResult result = applyMutation(tenantId, deviceId, userId, mutation);
        if (idempotencyOperation != null) {
            idempotencyService.complete(idempotencyOperation, Integer.parseInt(result.httpStatus()),
                objectMapper.writeValueAsString(result), "{}", "application/json");
        }
        return result;
    }

    private MutationResult applyMutation(UUID tenantId, UUID deviceId, UUID userId, MutationEnvelope mutation) {
        String entityType = mutation.entityType() == null ? "" : mutation.entityType().toLowerCase();
        String tableName = MobileSyncSchema.tableFor(entityType);
        if (tableName == null) return errorResult(mutation, "400", "Unknown entity type: " + mutation.entityType());

        String operation = mutation.operation() == null ? "" : mutation.operation().toUpperCase();
        long currentVersion = getCurrentVersion(tableName, tenantId, mutation.entityId());

        if ("CREATE".equals(operation)) {
            if (currentVersion >= 0) return errorResult(mutation, "409", "Entity already exists: " + mutation.entityId());
            return createEntity(tenantId, userId, entityType, tableName, mutation);
        }
        if (currentVersion < 0) return errorResult(mutation, "404", "Entity not found: " + mutation.entityId());
        if (mutation.expectedVersion() == null) return errorResult(mutation, "428", "expectedVersion is required for UPDATE/DELETE");
        if (currentVersion != mutation.expectedVersion()) {
            return conflictResult(tenantId, deviceId, userId, entityType, tableName, mutation, currentVersion);
        }

        return switch (operation) {
            case "UPDATE" -> updateEntity(tenantId, userId, entityType, tableName, mutation, currentVersion);
            case "DELETE" -> deleteEntity(tenantId, entityType, tableName, mutation, currentVersion);
            default -> errorResult(mutation, "400", "Unknown operation: " + mutation.operation());
        };
    }

    private long getCurrentVersion(String tableName, UUID tenantId, String entityId) {
        if (entityId == null || entityId.isBlank()) return -1L;
        try {
            Long version = jdbcTemplate.queryForObject(
                "SELECT sync_version FROM " + tableName + " WHERE tenant_id = ? AND id = ?::UUID",
                Long.class, tenantId, entityId);
            return version == null ? -1L : version;
        } catch (EmptyResultDataAccessException notFound) { return -1L; }
    }

    private MutationResult createEntity(UUID tenantId, UUID userId, String entityType, String tableName, MutationEnvelope mutation) {
        String entityId = mutation.entityId() != null ? mutation.entityId() : UUID.randomUUID().toString();
        LinkedHashMap<String, Object> values = MobileSyncSchema.toDatabaseValues(entityType, mutation.payload());
        MobileSyncSchema.addCreateDefaults(entityType, values);
        String validationError = MobileSyncSchema.validateCreate(entityType, values);
        if (validationError != null) return errorResult(mutation, "400", validationError);

        StringBuilder columns = new StringBuilder("tenant_id, id, created_by, updated_by, sync_version");
        StringBuilder placeholders = new StringBuilder("?, ?::UUID, ?, ?, 1");
        List<Object> params = new ArrayList<>(List.of(tenantId, entityId, userId, userId));
        values.forEach((column, value) -> {
            columns.append(", ").append(column);
            placeholders.append(", ").append(MobileSyncSchema.bindExpression(column));
            params.add(value);
        });

        String sql = String.format("INSERT INTO %s (%s, created_at, updated_at) VALUES (%s, NOW(), NOW())", tableName, columns, placeholders);
        jdbcTemplate.update(sql, params.toArray());
        return new MutationResult(mutation.idempotencyKey(), entityId, "APPLIED", "201", 1L, "\"1\"", null, null);
    }

    private MutationResult updateEntity(UUID tenantId, UUID userId, String entityType, String tableName,
                                        MutationEnvelope mutation, long currentVersion) {
        LinkedHashMap<String, Object> values = MobileSyncSchema.toDatabaseValues(entityType, mutation.payload());
        if (values.isEmpty()) {
            return new MutationResult(mutation.idempotencyKey(), mutation.entityId(), "APPLIED", "200",
                currentVersion, "\"" + currentVersion + "\"", null, null);
        }

        StringBuilder setClauses = new StringBuilder();
        List<Object> params = new ArrayList<>();
        values.forEach((column, value) -> {
            if (!setClauses.isEmpty()) setClauses.append(", ");
            setClauses.append(column).append(" = ").append(MobileSyncSchema.bindExpression(column));
            params.add(value);
        });
        setClauses.append(", updated_by = ?");
        params.add(userId);

        String sql = String.format("UPDATE %s SET %s WHERE tenant_id = ? AND id = ?::UUID AND sync_version = ?", tableName, setClauses);
        params.add(tenantId); params.add(mutation.entityId()); params.add(currentVersion);
        int updated = jdbcTemplate.update(sql, params.toArray());
        if (updated == 0) {
            long serverVersion = getCurrentVersion(tableName, tenantId, mutation.entityId());
            return conflictResult(tenantId, null, userId, entityType, tableName, mutation, serverVersion < 0 ? currentVersion : serverVersion);
        }
        long newVersion = currentVersion + 1;
        return new MutationResult(mutation.idempotencyKey(), mutation.entityId(), "APPLIED", "200", newVersion, "\"" + newVersion + "\"", null, null);
    }

    private MutationResult deleteEntity(UUID tenantId, String entityType, String tableName,
                                        MutationEnvelope mutation, long currentVersion) {
        String sql = String.format("UPDATE %s SET %s WHERE tenant_id = ? AND id = ?::UUID AND sync_version = ?",
            tableName, MobileSyncSchema.softDeleteSetClause(entityType));
        int deleted = jdbcTemplate.update(sql, tenantId, mutation.entityId(), currentVersion);
        if (deleted == 0) {
            long serverVersion = getCurrentVersion(tableName, tenantId, mutation.entityId());
            return simpleConflictResult(mutation, serverVersion < 0 ? currentVersion : serverVersion);
        }
        long newVersion = currentVersion + 1;
        return new MutationResult(mutation.idempotencyKey(), mutation.entityId(), "APPLIED", "200", newVersion, "\"" + newVersion + "\"", null, null);
    }

    private MutationResult conflictResult(UUID tenantId, UUID deviceId, UUID userId, String entityType, String tableName,
                                          MutationEnvelope mutation, long serverVersion) {
        if (conflictService == null || deviceId == null) return simpleConflictResult(mutation, serverVersion);
        JsonNode serverPayload = readServerPayload(tableName, tenantId, mutation.entityId());
        ConflictService.ConflictDetection detection = conflictService.detectConflict(
            tenantId, deviceId, userId, entityType, mutation.entityId(),
            mutation.expectedVersion() == null ? 0 : mutation.expectedVersion(), mutation.payload(),
            serverVersion, serverPayload, mutation.operation(), false, true);
        ObjectNode info = objectMapper.createObjectNode();
        info.put("conflictId", detection.conflictId()); info.put("serverVersion", serverVersion);
        info.put("entityType", entityType); info.put("entityId", mutation.entityId());
        info.put("conflictType", detection.conflictType()); info.put("conflictClass", detection.conflictClass());
        info.put("canAutoMerge", detection.canAutoMerge());
        return new MutationResult(mutation.idempotencyKey(), mutation.entityId(), "CONFLICT", "412", serverVersion, null, info, null);
    }

    private JsonNode readServerPayload(String tableName, UUID tenantId, String entityId) {
        try {
            Object value = jdbcTemplate.queryForObject(
                "SELECT to_jsonb(t) FROM " + tableName + " t WHERE tenant_id = ? AND id = ?::UUID",
                Object.class, tenantId, entityId);
            return value == null ? objectMapper.createObjectNode() : objectMapper.readTree(value.toString());
        } catch (Exception e) { return objectMapper.createObjectNode(); }
    }

    private MutationResult simpleConflictResult(MutationEnvelope mutation, long serverVersion) {
        ObjectNode info = objectMapper.createObjectNode();
        info.put("serverVersion", serverVersion); info.put("entityType", mutation.entityType());
        info.put("entityId", mutation.entityId()); info.put("conflictType", "VERSION_MISMATCH");
        return new MutationResult(mutation.idempotencyKey(), mutation.entityId(), "CONFLICT", "412", serverVersion, null, info, null);
    }

    private MutationResult errorResult(MutationEnvelope mutation, String httpStatus, String message) {
        return new MutationResult(mutation.idempotencyKey(), mutation.entityId(), "REJECTED", httpStatus, null, null, null, message);
    }

    private String mutationFingerprintMaterial(MutationEnvelope mutation) {
        return String.join("|", String.valueOf(mutation.entityType()), String.valueOf(mutation.entityId()),
            String.valueOf(mutation.operation()), String.valueOf(mutation.expectedVersion()),
            mutation.payload() == null ? "" : mutation.payload().toString());
    }

    private void rollbackMutation(Connection connection, Savepoint savepoint, MutationEnvelope mutation, Exception e) {
        try {
            if (connection != null && savepoint != null) { connection.rollback(savepoint); connection.releaseSavepoint(savepoint); }
        } catch (Exception rollbackError) { e.addSuppressed(rollbackError); }
        log.error("Mutation rolled back: key={}, entity={}/{}, operation={}",
            mutation.idempotencyKey(), mutation.entityType(), mutation.entityId(), mutation.operation(), e);
    }
}
