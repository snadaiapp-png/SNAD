package com.sanad.platform.crm.mobile.conflict.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** G7 mobile conflict detection, persistence, and resolution service. */
@Service
public class ConflictService {

    private static final Logger log = LoggerFactory.getLogger(ConflictService.class);
    private static final long RETENTION_DAYS = 365;
    private static final Set<String> ALLOWED_RESOLUTIONS = Set.of(
        "CLIENT_WINS", "SERVER_WINS", "MERGED", "USER_CHOICE"
    );

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public ConflictService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ConflictDetection detectConflict(
            UUID tenantId, UUID deviceId, UUID userId,
            String entityType, String entityId,
            long clientVersion, JsonNode clientPayload,
            long serverVersion, JsonNode serverPayload) {
        return detectConflict(tenantId, deviceId, userId, entityType, entityId,
                clientVersion, clientPayload, serverVersion, serverPayload,
                "UPDATE", false, true);
    }

    @Transactional
    public ConflictDetection detectConflict(
            UUID tenantId, UUID deviceId, UUID userId,
            String entityType, String entityId,
            long clientVersion, JsonNode clientPayload,
            long serverVersion, JsonNode serverPayload,
            String clientOperation, boolean serverDeleted, boolean tenantOwned) {

        String conflictType;
        String conflictClass;
        boolean canAutoMerge;

        if (!tenantOwned) {
            conflictType = "CROSS_TENANT_ATTEMPT";
            conflictClass = "C10";
            canAutoMerge = false;
        } else if (serverDeleted && !"DELETE".equals(clientOperation)) {
            conflictType = "DELETE_VS_UPDATE";
            conflictClass = "C3";
            canAutoMerge = false;
        } else if ("DELETE".equals(clientOperation) && clientVersion < serverVersion) {
            conflictType = "UPDATE_VS_DELETE";
            conflictClass = "C4";
            canAutoMerge = false;
        } else if (clientVersion < serverVersion) {
            if (hasFieldOverlap(clientPayload, serverPayload)) {
                conflictType = "SAME_FIELD_BOTH_SIDES";
                conflictClass = "C1";
                canAutoMerge = false;
            } else {
                conflictType = "NON_OVERLAPPING_FIELDS";
                conflictClass = "C2";
                canAutoMerge = canAutoMergeForEntity(entityType);
            }
        } else if (clientVersion == serverVersion) {
            if (hasFieldOverlap(clientPayload, serverPayload)) {
                conflictType = "FIELD_CONFLICT";
                conflictClass = "C1";
                canAutoMerge = false;
            } else {
                conflictType = "NON_OVERLAPPING_FIELDS";
                conflictClass = "C7";
                canAutoMerge = canAutoMergeForEntity(entityType);
            }
        } else {
            conflictType = "VERSION_MISMATCH";
            conflictClass = "C9";
            canAutoMerge = false;
        }

        UUID conflictId = logConflict(
            tenantId, deviceId, userId, entityType, entityId,
            clientVersion, clientPayload, serverVersion, serverPayload,
            conflictType, conflictClass
        );

        log.info("Conflict detected: id={}, entity={}/{}, class={}, type={}, autoMerge={}",
            conflictId, entityType, entityId, conflictClass, conflictType, canAutoMerge);

        return new ConflictDetection(
            conflictId.toString(), conflictType, conflictClass, canAutoMerge,
            serverVersion, clientVersion
        );
    }

    @Transactional
    public JsonNode autoMerge(String entityType, JsonNode clientPayload, JsonNode serverPayload) {
        if (!canAutoMergeForEntity(entityType)) {
            throw new IllegalStateException("Auto-merge not permitted for entity type: " + entityType);
        }
        ObjectNode merged = serverPayload.deepCopy();
        if (clientPayload != null && clientPayload.isObject()) {
            clientPayload.fields().forEachRemaining(entry -> {
                String field = entry.getKey();
                if (!merged.has(field) || merged.get(field).isNull()) {
                    merged.set(field, entry.getValue());
                }
            });
        }
        return merged;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getOpenConflicts(UUID tenantId, UUID deviceId) {
        return jdbcTemplate.queryForList("""
            SELECT conflict_id, entity_type, entity_id, conflict_type, conflict_class,
                   status, client_mutation, server_state, created_at
            FROM mobile_conflict_log
            WHERE tenant_id = ? AND device_id = ? AND status IN ('OPEN', 'RESOLUTION_PENDING')
            ORDER BY created_at ASC
        """, tenantId, deviceId);
    }

    @Transactional
    public void resolveConflict(UUID tenantId, UUID conflictId, UUID userId,
                                String resolution, JsonNode resolutionData) {
        if (resolution == null || !ALLOWED_RESOLUTIONS.contains(resolution)) {
            throw new IllegalArgumentException("Unsupported conflict resolution: " + resolution);
        }
        int updated = jdbcTemplate.update("""
            UPDATE mobile_conflict_log
            SET status = 'RESOLVED',
                resolution = ?,
                resolved_by = ?,
                resolved_at = NOW(),
                resolution_notes = COALESCE(?, resolution_notes),
                updated_at = NOW()
            WHERE tenant_id = ?
              AND conflict_id = ?
              AND status IN ('OPEN', 'RESOLUTION_PENDING')
        """, resolution, userId,
            resolutionData == null || resolutionData.isNull() ? null : resolutionData.toString(),
            tenantId, conflictId);
        if (updated != 1) {
            throw new IllegalStateException("Open conflict not found: " + conflictId);
        }
        log.info("Conflict resolved: id={}, resolution={}", conflictId, resolution);
    }

    /**
     * Defer a conflict without writing a fake resolution. The database schema
     * models deferral as workflow status RESOLUTION_PENDING; resolution stays
     * NULL until an actual resolution is chosen.
     */
    @Transactional
    public boolean skipConflict(UUID tenantId, UUID conflictId, UUID userId) {
        int updated = jdbcTemplate.update("""
            UPDATE mobile_conflict_log
            SET status = 'RESOLUTION_PENDING',
                resolution = NULL,
                resolved_by = NULL,
                resolved_at = NULL,
                updated_at = NOW()
            WHERE tenant_id = ?
              AND conflict_id = ?
              AND status = 'OPEN'
        """, tenantId, conflictId);
        if (updated == 1) {
            log.info("Conflict deferred: id={}, user={}", conflictId, userId);
            return true;
        }
        return false;
    }

    @Transactional
    public int expireOldConflicts() {
        int expired = jdbcTemplate.update("""
            UPDATE mobile_conflict_log
            SET status = 'EXPIRED',
                resolution = 'SERVER_WINS',
                resolved_at = NOW(),
                updated_at = NOW()
            WHERE status IN ('OPEN', 'RESOLUTION_PENDING')
              AND retention_expires_at < NOW()
        """);
        if (expired > 0) {
            log.info("Expired {} old conflicts (SERVER_WINS)", expired);
        }
        return expired;
    }

    private boolean canAutoMergeForEntity(String entityType) {
        return switch (entityType.toLowerCase()) {
            case "account", "contact", "task", "activity" -> true;
            default -> false;
        };
    }

    private boolean hasFieldOverlap(JsonNode client, JsonNode server) {
        if (client == null || server == null) return false;
        var clientFields = client.fieldNames();
        while (clientFields.hasNext()) {
            String field = clientFields.next();
            if (server.has(field) && !server.get(field).equals(client.get(field))) {
                return true;
            }
        }
        return false;
    }

    private UUID logConflict(
            UUID tenantId, UUID deviceId, UUID userId,
            String entityType, String entityId,
            long clientVersion, JsonNode clientPayload,
            long serverVersion, JsonNode serverPayload,
            String conflictType, String conflictClass) {
        UUID conflictId = UUID.randomUUID();
        Instant retentionExpiry = Instant.now().plus(RETENTION_DAYS, ChronoUnit.DAYS);
        jdbcTemplate.update("""
            INSERT INTO mobile_conflict_log
                (conflict_id, tenant_id, device_id, user_id,
                 entity_type, entity_id, base_version, client_mutation,
                 server_version, server_state, conflict_type, conflict_class,
                 status, retention_expires_at, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?::UUID, ?, ?::JSONB, ?, ?::JSONB, ?, ?, 'OPEN', ?, NOW(), NOW())
        """,
            conflictId, tenantId, deviceId, userId,
            entityType, entityId, clientVersion,
            clientPayload == null ? "{}" : clientPayload.toString(),
            serverVersion, serverPayload == null ? "{}" : serverPayload.toString(),
            conflictType, conflictClass, retentionExpiry
        );
        return conflictId;
    }

    public record ConflictDetection(
        String conflictId,
        String conflictType,
        String conflictClass,
        boolean canAutoMerge,
        long serverVersion,
        long clientVersion
    ) {}
}
