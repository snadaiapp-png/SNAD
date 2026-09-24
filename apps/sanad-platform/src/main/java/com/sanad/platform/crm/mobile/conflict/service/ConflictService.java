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
import java.util.UUID;

/**
 * Service for conflict detection and classification.
 *
 * Requirements: ARCH-002 (12 Conflict Classes), SYNC-005 (Conflict Detection),
 *               SYNC-009 (Conflict Isolation), DATA-005 (Conflict Log)
 *
 * Conflict Classes (from ADR-G7-001):
 *   C1: Same Record / Same Field
 *   C2: Same Record / Different Fields
 *   C3: Delete vs Update
 *   C4: Update vs Delete
 *   C5: State Transition Conflict
 *   C6: Ownership Conflict
 *   C7: Same Record / Non-Overlapping Fields (auto-merge)
 *   C8: Concurrent Creates (same unique constraint)
 *   C9: Stale Read Conflict
 *   C10: Cross-Tenant Attempt
 *   C11: Batch Partial Failure
 *   C12: Append-Only Conflict
 */
@Service
public class ConflictService {

    private static final Logger log = LoggerFactory.getLogger(ConflictService.class);

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    // Default retention period: 1 year
    private static final long RETENTION_DAYS = 365;

    public ConflictService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Detect and classify a conflict using the default operation context
     * (client UPDATE against a live server row owned by the caller's tenant).
     */
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

    /**
     * Detect and classify a conflict with full operation context — DEF-006.
     *
     * <p>Coverage (ADR-G7-001): C1 same-field, C2 stale + non-overlapping,
     * C3 delete-vs-update, C4 update-vs-delete, C7 same-version non-overlapping,
     * C9 stale / impossible version, C10 cross-tenant attempt.
     * C5 (state-transition), C6 (ownership), C8 (concurrent create),
     * C11 (batch partial failure) and C12 (append-only) require entity-state
     * or batch context not available at this layer and remain documented for
     * downstream classification.</p>
     *
     * @param clientOperation CREATE | UPDATE | DELETE
     * @param serverDeleted   true if the server row was deleted
     * @param tenantOwned     true if the entity belongs to the caller's tenant
     */
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
            // C10: caller attempted to mutate an entity owned by another tenant
            conflictType = "CROSS_TENANT_ATTEMPT";
            conflictClass = "C10";
            canAutoMerge = false;
        } else if (serverDeleted && !"DELETE".equals(clientOperation)) {
            // C3: server deleted the row, client is trying to update it
            conflictType = "DELETE_VS_UPDATE";
            conflictClass = "C3";
            canAutoMerge = false;
        } else if ("DELETE".equals(clientOperation) && clientVersion < serverVersion) {
            // C4: client wants to delete a stale copy the server has since updated
            conflictType = "UPDATE_VS_DELETE";
            conflictClass = "C4";
            canAutoMerge = false;
        } else if (clientVersion < serverVersion) {
            // Client has stale data
            if (hasFieldOverlap(clientPayload, serverPayload)) {
                // Same field modified on both sides
                conflictType = "SAME_FIELD_BOTH_SIDES";
                conflictClass = "C1";
                canAutoMerge = false;
            } else {
                // Different fields modified — can auto-merge for Account, Contact, Task, Activity
                conflictType = "NON_OVERLAPPING_FIELDS";
                conflictClass = "C2";
                canAutoMerge = canAutoMergeForEntity(entityType);
            }
        } else if (clientVersion == serverVersion) {
            // Same version — concurrent modification
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
            // Client version > server version — impossible in normal flow
            conflictType = "VERSION_MISMATCH";
            conflictClass = "C9";
            canAutoMerge = false;
        }

        // Log conflict to mobile_conflict_log
        UUID conflictId = logConflict(
            tenantId, deviceId, userId, entityType, entityId,
            clientVersion, clientPayload, serverVersion, serverPayload,
            conflictType, conflictClass
        );

        log.info("Conflict detected: id={}, entity={}/{}, class={}, type={}, autoMerge={}",
            conflictId, entityType, entityId, conflictClass, conflictType, canAutoMerge);

        return new ConflictDetection(
            conflictId.toString(),
            conflictType,
            conflictClass,
            canAutoMerge,
            serverVersion,
            clientVersion
        );
    }

    /**
     * Auto-merge non-conflicting fields.
     * Only permitted for: Account, Contact, Task, Activity (per ADR-G7-001).
     */
    @Transactional
    public JsonNode autoMerge(String entityType, JsonNode clientPayload, JsonNode serverPayload) {
        if (!canAutoMergeForEntity(entityType)) {
            throw new IllegalStateException("Auto-merge not permitted for entity type: " + entityType);
        }

        ObjectNode merged = serverPayload.deepCopy();

        if (clientPayload != null && clientPayload.isObject()) {
            clientPayload.fields().forEachRemaining(entry -> {
                String field = entry.getKey();
                // Only merge fields that are NOT in the server payload
                // or where client has a value and server doesn't
                if (!merged.has(field) || merged.get(field).isNull()) {
                    merged.set(field, entry.getValue());
                }
            });
        }

        return merged;
    }

    /**
     * Get all open conflicts for a tenant/device.
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getOpenConflicts(UUID tenantId, UUID deviceId) {
        return jdbcTemplate.queryForList("""
            SELECT conflict_id, entity_type, entity_id, conflict_type, conflict_class,
                   status, client_mutation, server_state, created_at
            FROM mobile_conflict_log
            WHERE tenant_id = ? AND device_id = ? AND status = 'OPEN'
            ORDER BY created_at ASC
        """, tenantId, deviceId);
    }

    /**
     * Resolve a conflict (user choice or server wins).
     */
    @Transactional
    public void resolveConflict(UUID tenantId, UUID conflictId, UUID userId,
                                 String resolution, JsonNode resolutionData) {
        jdbcTemplate.update("""
            UPDATE mobile_conflict_log
            SET status = 'RESOLVED',
                resolution = ?,
                resolved_by = ?,
                resolved_at = NOW(),
                updated_at = NOW()
            WHERE tenant_id = ? AND conflict_id = ? AND status = 'OPEN'
        """, resolution, userId, tenantId, conflictId);

        log.info("Conflict resolved: id={}, resolution={}", conflictId, resolution);
    }

    /**
     * Expire old conflicts (called by scheduled job).
     * Auto-resolves with SERVER_WINS after retention period.
     */
    @Transactional
    public int expireOldConflicts() {
        int expired = jdbcTemplate.update("""
            UPDATE mobile_conflict_log
            SET status = 'EXPIRED',
                resolution = 'SERVER_WINS',
                resolved_at = NOW(),
                updated_at = NOW()
            WHERE status = 'OPEN'
              AND retention_expires_at < NOW()
        """);

        if (expired > 0) {
            log.info("Expired {} old conflicts (SERVER_WINS)", expired);
        }
        return expired;
    }

    /**
     * Check if entity type allows auto-merge (per ADR-G7-001).
     */
    private boolean canAutoMergeForEntity(String entityType) {
        return switch (entityType.toLowerCase()) {
            case "account", "contact", "task", "activity" -> true;
            case "lead", "opportunity", "pipeline", "tags", "custom_fields" -> false;
            case "note" -> false; // push-only, no merge needed
            default -> false;
        };
    }

    /**
     * Check if client and server payloads overlap on any fields.
     */
    private boolean hasFieldOverlap(JsonNode client, JsonNode server) {
        if (client == null || server == null) return false;

        var clientFields = client.fieldNames();
        while (clientFields.hasNext()) {
            String field = clientFields.next();
            if (server.has(field) && !server.get(field).equals(client.get(field))) {
                return true; // Same field, different values
            }
        }
        return false;
    }

    /**
     * Log conflict to mobile_conflict_log table.
     */
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
            VALUES (?, ?, ?, ?, ?, ?, ?, ?::JSONB, ?, ?::JSONB, ?, ?, 'OPEN', ?, NOW(), NOW())
        """,
            conflictId, tenantId, deviceId, userId,
            entityType, entityId, clientVersion, clientPayload.toString(),
            serverVersion, serverPayload.toString(),
            conflictType, conflictClass, retentionExpiry
        );

        return conflictId;
    }

    /**
     * Detection result record.
     */
    public record ConflictDetection(
        String conflictId,
        String conflictType,
        String conflictClass,
        boolean canAutoMerge,
        long serverVersion,
        long clientVersion
    ) {}
}
