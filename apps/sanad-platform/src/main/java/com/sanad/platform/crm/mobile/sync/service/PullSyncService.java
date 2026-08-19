package com.sanad.platform.crm.mobile.sync.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sanad.platform.crm.mobile.sync.model.DeltaSyncRequest;
import com.sanad.platform.crm.mobile.sync.model.DeltaSyncResponse;
import com.sanad.platform.crm.mobile.sync.model.DeltaSyncResponse.EntityDelta;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Lossless delta-sync pull service.
 *
 * <p>{@code sync_version} is a row-local optimistic-concurrency token and is
 * never used as a global cursor. The opaque client cursor is backed by the
 * monotonic {@code mobile_change_log.change_id} sequence.</p>
 */
@Service
public class PullSyncService {

    private static final Logger log = LoggerFactory.getLogger(PullSyncService.class);
    private static final String CURSOR_VERSION = "g7c1:";

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final Map<String, String> ENTITY_TABLES = Map.of(
        "account", "crm_accounts",
        "contact", "crm_contacts",
        "lead", "crm_leads",
        "opportunity", "crm_opportunities",
        "task", "crm_tasks",
        "note", "crm_notes",
        "activity", "crm_activities"
    );

    public PullSyncService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public DeltaSyncResponse pull(UUID tenantId, UUID deviceId, DeltaSyncRequest request) {
        String entityType = request.entityType().toLowerCase();
        String tableName = ENTITY_TABLES.get(entityType);
        if (tableName == null) {
            throw new IllegalArgumentException("Unknown entity type: " + entityType);
        }

        long afterChangeId = decodeCursor(request.cursor());
        int pageSize = request.limit();

        // Fetch one extra row so hasMore is exact. The cursor advances only to
        // the last row actually returned, therefore no change can be skipped.
        String sql = String.format("""
            SELECT c.change_id,
                   c.entity_id,
                   c.entity_version,
                   c.change_operation,
                   c.changed_at,
                   e.sync_version,
                   e.updated_at,
                   %s
            FROM mobile_change_log c
            LEFT JOIN %s e
              ON e.tenant_id = c.tenant_id
             AND e.id = c.entity_id
            WHERE c.tenant_id = ?
              AND c.entity_type = ?
              AND c.change_id > ?
            ORDER BY c.change_id ASC
            LIMIT ?
        """, getSelectColumns(entityType), tableName);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            sql, tenantId, entityType, afterChangeId, pageSize + 1
        );

        boolean hasMore = rows.size() > pageSize;
        int resultCount = Math.min(rows.size(), pageSize);
        List<EntityDelta> entities = new ArrayList<>(resultCount);
        long lastChangeId = afterChangeId;

        for (int i = 0; i < resultCount; i++) {
            Map<String, Object> row = rows.get(i);
            long changeId = ((Number) row.get("change_id")).longValue();
            UUID entityId = (UUID) row.get("entity_id");
            String capturedOperation = String.valueOf(row.get("change_operation"));
            boolean rowExists = row.get("sync_version") != null;
            boolean tombstone = "DELETE".equals(capturedOperation) || !rowExists;

            long version = rowExists
                ? ((Number) row.get("sync_version")).longValue()
                : ((Number) row.get("entity_version")).longValue();

            Instant changedAt = toInstant(row.get("changed_at"));
            Instant updatedAt = rowExists && row.get("updated_at") != null
                ? toInstant(row.get("updated_at"))
                : changedAt;

            ObjectNode data = objectMapper.createObjectNode();
            if (!tombstone) {
                for (Map.Entry<String, Object> entry : row.entrySet()) {
                    String key = entry.getKey();
                    if (isEnvelopeColumn(key) || entry.getValue() == null) {
                        continue;
                    }
                    data.put(key, entry.getValue().toString());
                }
            }

            String operation = tombstone
                ? "DELETE"
                : ("INSERT".equals(capturedOperation) ? "CREATE" : "UPDATE");

            entities.add(new EntityDelta(
                entityId.toString(), operation, version, data, updatedAt
            ));
            lastChangeId = changeId;
        }

        String nextCursor = resultCount == 0 ? request.cursor() : encodeCursor(lastChangeId);

        log.info("Delta pull: tenant={}, device={}, entity={}, afterChangeId={}, returned={}, nextChangeId={}, hasMore={}",
            tenantId, deviceId, entityType, afterChangeId, resultCount, lastChangeId, hasMore);

        return new DeltaSyncResponse(
            entityType,
            nextCursor,
            resultCount,
            entities,
            Instant.now(),
            hasMore
        );
    }

    private boolean isEnvelopeColumn(String key) {
        return switch (key) {
            case "change_id", "entity_id", "entity_version", "change_operation",
                 "changed_at", "sync_version", "updated_at" -> true;
            default -> false;
        };
    }

    private Instant toInstant(Object value) {
        if (value instanceof Timestamp timestamp) {
            return timestamp.toInstant();
        }
        if (value instanceof java.time.OffsetDateTime offsetDateTime) {
            return offsetDateTime.toInstant();
        }
        if (value instanceof Instant instant) {
            return instant;
        }
        throw new IllegalStateException("Unsupported timestamp value: " + value);
    }

    /**
     * Legacy cursors were Base64(row sync_version) and are unsafe as a global
     * cursor. Treat them as invalid and bootstrap from change_id 0. New cursors
     * are version-tagged inside the existing opaque Base64 contract.
     */
    private long decodeCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return 0L;
        }
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(cursor));
            if (!decoded.startsWith(CURSOR_VERSION)) {
                log.info("Legacy G7 row-version cursor detected; forcing lossless bootstrap");
                return 0L;
            }
            return Long.parseLong(decoded.substring(CURSOR_VERSION.length()));
        } catch (Exception e) {
            log.warn("Invalid mobile sync cursor; forcing lossless bootstrap");
            return 0L;
        }
    }

    private String encodeCursor(long changeId) {
        String value = CURSOR_VERSION + changeId;
        return Base64.getUrlEncoder().encodeToString(value.getBytes());
    }

    /** Columns are aligned to the canonical unified CRM schema. */
    private String getSelectColumns(String entityType) {
        return switch (entityType) {
            case "account" -> "e.display_name, e.account_type, e.lifecycle_status, e.parent_account_id, e.owner_user_id, e.primary_currency_code, e.preferred_locale, e.time_zone, e.source, e.archived_at, e.created_at";
            case "contact" -> "e.account_id, e.given_name, e.family_name, e.display_name, e.primary_email, e.primary_phone, e.preferred_locale, e.time_zone, e.lifecycle_status, e.owner_user_id, e.consent_summary, e.archived_at, e.created_at";
            case "lead" -> "e.display_name, e.company_name, e.email, e.phone, e.source, e.status, e.owner_user_id, e.queue_id, e.score, e.converted_account_id, e.converted_contact_id, e.converted_opportunity_id, e.created_at";
            case "opportunity" -> "e.account_id, e.contact_id, e.pipeline_id, e.stage_id, e.name, e.amount, e.currency_code, e.probability, e.forecast_category, e.expected_close_date, e.owner_user_id, e.status, e.win_loss_reason, e.created_at";
            case "task" -> "e.title, e.description, e.related_type, e.related_id, e.assignee_user_id, e.owner_user_id, e.status, e.priority, e.start_at, e.due_at, e.completed_at, e.result, e.created_at";
            case "note" -> "e.subject_type, e.subject_id, e.body, e.author_user_id, e.archived, e.created_at";
            case "activity" -> "e.activity_type, e.subject, e.body, e.related_type, e.related_id, e.owner_user_id, e.status, e.priority, e.start_at, e.due_at, e.completed_at, e.created_at";
            default -> throw new IllegalArgumentException("Unknown entity type: " + entityType);
        };
    }
}
