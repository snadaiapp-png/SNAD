package com.sanad.platform.crm.mobile.sync.service;

import com.sanad.platform.crm.integration.domain.TenantContextPort;
import com.sanad.platform.crm.mobile.sync.model.SyncStatusResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Reads G7 mobile sync status from tenant-isolated PostgreSQL state. */
@Service
public class SyncStatusService {

    private static final String CURSOR_VERSION = "g7c1:";

    private final JdbcTemplate jdbcTemplate;
    private final TenantContextPort tenantContext;

    public SyncStatusService(JdbcTemplate jdbcTemplate, TenantContextPort tenantContext) {
        this.jdbcTemplate = jdbcTemplate;
        this.tenantContext = tenantContext;
    }

    /**
     * FORCE RLS on the mobile metadata tables requires an explicit transaction:
     * TenantRlsConnectionHandler applies SET LOCAL app.tenant_id only when the
     * connection is participating in a transaction.
     */
    @Transactional(readOnly = true)
    public SyncStatusResponse buildStatus(String deviceId) {
        UUID tenantId = tenantContext.getTenantId();
        UUID deviceUuid = UUID.fromString(deviceId);

        Instant lastSyncAt = jdbcTemplate.queryForObject(
            "SELECT COALESCE(GREATEST(MAX(completed_at), MAX(started_at)), 'epoch'::timestamptz) " +
            "FROM mobile_sync_log WHERE tenant_id = ? AND device_id = ?",
            Instant.class, tenantId, deviceUuid
        );

        Map<String, SyncStatusResponse.EntitySyncStatus> entityStatuses = new HashMap<>();
        var cursorRows = jdbcTemplate.queryForList(
            "SELECT entity_type, cursor_value, last_sync_at FROM mobile_sync_cursor " +
            "WHERE tenant_id = ? AND device_id = ?",
            tenantId, deviceUuid
        );

        for (var row : cursorRows) {
            String entityType = (String) row.get("entity_type");
            Instant lastPullAt = toInstant(row.get("last_sync_at"));
            long changeCursor = decodeChangeCursor((String) row.get("cursor_value"));
            entityStatuses.put(entityType, new SyncStatusResponse.EntitySyncStatus(
                entityType, lastPullAt, changeCursor, 0
            ));
        }

        Integer pendingMutations = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM mobile_sync_log WHERE tenant_id = ? AND device_id = ? AND status = 'STARTED'",
            Integer.class, tenantId, deviceUuid
        );

        Integer unresolvedConflicts = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM mobile_conflict_log WHERE tenant_id = ? AND device_id = ? " +
            "AND status IN ('OPEN', 'RESOLUTION_PENDING')",
            Integer.class, tenantId, deviceUuid
        );

        int pending = pendingMutations == null ? 0 : pendingMutations;
        int conflicts = unresolvedConflicts == null ? 0 : unresolvedConflicts;
        String overallStatus = conflicts > 0 ? "CONFLICTS_PENDING" : pending > 0 ? "SYNCING" : "OK";

        return new SyncStatusResponse(
            deviceId,
            lastSyncAt,
            entityStatuses,
            pending,
            conflicts,
            overallStatus
        );
    }

    /**
     * New cursors contain Base64("g7c1:<change_id>"). Legacy Base64 numeric
     * cursors represented row-local sync_version and are not comparable to the
     * new change feed, so status reports them as zero rather than mislabeling
     * them as a valid continuation position.
     */
    private long decodeChangeCursor(String cursorValue) {
        if (cursorValue == null || cursorValue.isBlank()) return 0L;
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(cursorValue));
            if (!decoded.startsWith(CURSOR_VERSION)) return 0L;
            return Long.parseLong(decoded.substring(CURSOR_VERSION.length()));
        } catch (RuntimeException ignored) {
            return 0L;
        }
    }

    private Instant toInstant(Object value) {
        if (value == null) return null;
        if (value instanceof Instant instant) return instant;
        if (value instanceof java.sql.Timestamp timestamp) return timestamp.toInstant();
        if (value instanceof java.time.OffsetDateTime offsetDateTime) return offsetDateTime.toInstant();
        return null;
    }
}
