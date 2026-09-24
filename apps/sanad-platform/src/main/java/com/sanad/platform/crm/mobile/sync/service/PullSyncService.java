package com.sanad.platform.crm.mobile.sync.service;

import com.sanad.platform.crm.mobile.sync.model.DeltaSyncRequest;
import com.sanad.platform.crm.mobile.sync.model.DeltaSyncResponse;
import com.sanad.platform.crm.mobile.sync.model.DeltaSyncResponse.EntityDelta;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service for delta sync pull operations.
 * Returns only entities changed since the client's last cursor.
 *
 * Requirements: SYNC-002 (Delta Pull), SYNC-004 (Cursor Invalidation)
 */
@Service
public class PullSyncService {

    private static final Logger log = LoggerFactory.getLogger(PullSyncService.class);

    private final JdbcTemplate jdbcTemplate;

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

    // Entity type to column mapping (which columns are sensitive/encrypted)
    private static final Map<String, List<String>> SENSITIVE_COLUMNS = Map.of(
        "account", List.of("phone", "email", "address"),
        "contact", List.of("phone", "email", "address", "notes"),
        "lead", List.of("phone", "email", "notes"),
        "opportunity", List.of("description"),
        "task", List.of("description"),
        "activity", List.of("description"),
        "note", List.of("content")
    );

    public PullSyncService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Execute delta pull for a given entity type and cursor.
     *
     * @param tenantId the tenant ID for RLS enforcement
     * @param deviceId the device ID for cursor management
     * @param request  the delta sync request
     * @return delta sync response with changed entities
     */
    @Transactional(readOnly = true)
    public DeltaSyncResponse pull(UUID tenantId, UUID deviceId, DeltaSyncRequest request) {
        String entityType = request.entityType().toLowerCase();
        String tableName = ENTITY_TABLES.get(entityType);

        if (tableName == null) {
            throw new IllegalArgumentException("Unknown entity type: " + entityType);
        }

        // RLS tenant context (SET LOCAL app.tenant_id) is applied automatically by
        // TenantRlsConnectionHandler within this @Transactional(readOnly=true) boundary.
        // tenantId is the validated JWT principal (DEF-005/DEF-007) — no manual SET here.

        // Decode cursor to get sync_version threshold
        long sinceVersion = decodeCursor(request.cursor());

        // Query changed entities
        String sql = String.format("""
            SELECT id, sync_version, updated_at, %s
            FROM %s
            WHERE tenant_id = ?
              AND sync_version > ?
            ORDER BY sync_version ASC
            LIMIT ?
        """, getSelectColumns(entityType), tableName);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            sql, tenantId, sinceVersion, request.limit()
        );

        List<EntityDelta> entities = new ArrayList<>();
        long maxVersion = sinceVersion;

        for (Map<String, Object> row : rows) {
            UUID entityId = (UUID) row.get("id");
            long version = ((Number) row.get("sync_version")).longValue();
            Instant updatedAt = ((java.sql.Timestamp) row.get("updated_at")).toInstant();

            // Build entity data (excluding sensitive columns if needed)
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.node.ObjectNode data = mapper.createObjectNode();

            for (String key : row.keySet()) {
                if (!key.equals("id") && !key.equals("sync_version") && !key.equals("updated_at")) {
                    Object value = row.get(key);
                    if (value != null) {
                        data.put(key, value.toString());
                    }
                }
            }

            // Determine operation type
            String operation = version > sinceVersion && sinceVersion > 0 ? "UPDATE" : "CREATE";

            entities.add(new EntityDelta(
                entityId.toString(),
                operation,
                version,
                data,
                updatedAt
            ));

            maxVersion = Math.max(maxVersion, version);
        }

        // Generate next cursor
        String nextCursor = entities.isEmpty() ? request.cursor() : encodeCursor(maxVersion);
        boolean hasMore = entities.size() == request.limit();

        log.info("Delta pull: tenant={}, device={}, entity={}, since={}, returned={}, maxVersion={}",
            tenantId, deviceId, entityType, sinceVersion, entities.size(), maxVersion);

        return new DeltaSyncResponse(
            entityType,
            nextCursor,
            entities.size(),
            entities,
            Instant.now(),
            hasMore
        );
    }

    /**
     * Decode cursor (Base64-encoded sync_version).
     */
    private long decodeCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return 0;
        }
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(cursor));
            return Long.parseLong(decoded);
        } catch (Exception e) {
            log.warn("Invalid cursor format, defaulting to 0: {}", cursor);
            return 0;
        }
    }

    /**
     * Encode cursor (Base64-encoded sync_version).
     */
    private String encodeCursor(long syncVersion) {
        return Base64.getUrlEncoder().encodeToString(Long.toString(syncVersion).getBytes());
    }

    /**
     * Get SELECT columns for entity type.
     */
    private String getSelectColumns(String entityType) {
        return switch (entityType) {
            case "account" -> "name, industry, phone, website, created_at, updated_at";
            case "contact" -> "account_id, first_name, last_name, email, phone, created_at, updated_at";
            case "lead" -> "first_name, last_name, email, phone, status, source, created_at, updated_at";
            case "opportunity" -> "account_id, contact_id, pipeline_id, title, amount, stage, close_date, created_at, updated_at";
            case "task" -> "title, description, status, due_date, assigned_to, created_at, updated_at";
            case "note" -> "entity_type, entity_id, content, created_at, updated_at";
            case "activity" -> "entity_type, entity_id, activity_type, description, result, created_at, updated_at";
            default -> "*";
        };
    }
}
