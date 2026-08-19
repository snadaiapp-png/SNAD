package com.sanad.platform.crm.mobile.sync.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import com.sanad.platform.crm.mobile.sync.model.DeltaSyncRequest;
import com.sanad.platform.crm.mobile.sync.model.DeltaSyncResponse;
import com.sanad.platform.crm.mobile.sync.model.DeltaSyncResponse.EntityDelta;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Lossless delta pull backed by a global monotonic change feed.
 *
 * Entity sync_version remains the optimistic-concurrency version only. The pull
 * cursor is mobile_change_log.change_id, so rows sharing the same entity version
 * cannot be skipped at page boundaries.
 */
@Service
public class PullSyncService {

    private static final Logger log = LoggerFactory.getLogger(PullSyncService.class);
    private static final Set<String> ENTITY_TYPES = Set.of(
        "account", "contact", "lead", "opportunity", "task", "note", "activity"
    );

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public PullSyncService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public DeltaSyncResponse pull(UUID tenantId, UUID deviceId, DeltaSyncRequest request) {
        String entityType = request.entityType().toLowerCase();
        if (!ENTITY_TYPES.contains(entityType)) {
            throw new IllegalArgumentException("Unknown entity type: " + entityType);
        }

        long sinceChangeId = decodeCursor(request.cursor());
        int fetchLimit = request.limit() + 1;

        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
            SELECT change_id, entity_id, operation, entity_version, payload, changed_at
            FROM mobile_change_log
            WHERE tenant_id = ?
              AND entity_type = ?
              AND change_id > ?
            ORDER BY change_id ASC
            LIMIT ?
        """, tenantId, entityType, sinceChangeId, fetchLimit);

        boolean hasMore = rows.size() > request.limit();
        int resultSize = Math.min(rows.size(), request.limit());
        List<EntityDelta> entities = new ArrayList<>(resultSize);
        long nextChangeId = sinceChangeId;

        for (int i = 0; i < resultSize; i++) {
            Map<String, Object> row = rows.get(i);
            long changeId = ((Number) row.get("change_id")).longValue();
            UUID entityId = (UUID) row.get("entity_id");
            String operation = row.get("operation").toString();
            long entityVersion = ((Number) row.get("entity_version")).longValue();
            Instant changedAt = toInstant(row.get("changed_at"));
            JsonNode payload = parsePayload(row.get("payload"));

            // DELETE is a tombstone: payload is retained for reconciliation/audit,
            // while operation explicitly tells the client to remove the local row.
            entities.add(new EntityDelta(
                entityId.toString(), operation, entityVersion, payload, changedAt
            ));
            nextChangeId = changeId;
        }

        String nextCursor = resultSize == 0 ? request.cursor() : encodeCursor(nextChangeId);
        if (nextCursor == null) {
            nextCursor = encodeCursor(sinceChangeId);
        }

        persistDeviceCursor(tenantId, deviceId, entityType, nextCursor, resultSize);

        log.info("Delta pull: tenant={}, device={}, entity={}, sinceChangeId={}, returned={}, nextChangeId={}, hasMore={}",
            tenantId, deviceId, entityType, sinceChangeId, resultSize, nextChangeId, hasMore);

        return new DeltaSyncResponse(
            entityType,
            nextCursor,
            resultSize,
            entities,
            Instant.now(),
            hasMore
        );
    }

    private JsonNode parsePayload(Object payload) {
        if (payload == null) {
            return NullNode.getInstance();
        }
        try {
            return objectMapper.readTree(payload.toString());
        } catch (Exception e) {
            throw new IllegalStateException("Invalid JSON payload in mobile_change_log", e);
        }
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
        return Instant.parse(value.toString());
    }

    /**
     * Persist cursor only for a registered, tenant-owned device. INSERT .. SELECT
     * makes an unregistered device a no-op rather than violating the FK; device
     * registration remains an explicit API contract.
     */
    private void persistDeviceCursor(UUID tenantId, UUID deviceId, String entityType,
                                     String cursor, int entityCount) {
        String cursorHash = sha256(cursor);
        jdbcTemplate.update("""
            INSERT INTO mobile_sync_cursor
                (tenant_id, device_id, entity_type, cursor_value, cursor_hash, entity_count,
                 last_sync_at, created_at, updated_at)
            SELECT ?, ?, ?, ?, ?, ?, NOW(), NOW(), NOW()
            WHERE EXISTS (
                SELECT 1 FROM mobile_device_registry
                WHERE tenant_id = ? AND device_id = ? AND is_active = TRUE
            )
            ON CONFLICT (tenant_id, device_id, entity_type)
            DO UPDATE SET cursor_value = EXCLUDED.cursor_value,
                          cursor_hash = EXCLUDED.cursor_hash,
                          entity_count = mobile_sync_cursor.entity_count + EXCLUDED.entity_count,
                          last_sync_at = NOW(),
                          updated_at = NOW()
        """, tenantId, deviceId, entityType, cursor, cursorHash, entityCount,
            tenantId, deviceId);

        jdbcTemplate.update("""
            UPDATE mobile_device_registry
            SET last_sync_at = NOW(), updated_at = NOW()
            WHERE tenant_id = ? AND device_id = ? AND is_active = TRUE
        """, tenantId, deviceId);
    }

    private long decodeCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return 0L;
        }
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            long value = Long.parseLong(decoded);
            if (value < 0) {
                throw new IllegalArgumentException("Negative cursor");
            }
            return value;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid sync cursor", e);
        }
    }

    private String encodeCursor(long changeId) {
        return Base64.getUrlEncoder().withoutPadding()
            .encodeToString(Long.toString(changeId).getBytes(StandardCharsets.UTF_8));
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
