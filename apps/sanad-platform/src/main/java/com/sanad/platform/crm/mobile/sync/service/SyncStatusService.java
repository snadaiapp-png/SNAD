package com.sanad.platform.crm.mobile.sync.service;

import com.sanad.platform.crm.integration.domain.TenantContextPort;
import com.sanad.platform.crm.mobile.sync.model.SyncStatusResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Reads mobile sync status from PostgreSQL.
 *
 * <p>Encapsulates all {@link JdbcTemplate} access so the controller
 * ({@code web} package) does not depend on {@code org.springframework.jdbc..}.
 * This is required by {@code CrmArchitectureTest.crmWebMustNotDependOnJdbc}.
 */
@Service
public class SyncStatusService {

    private final JdbcTemplate jdbcTemplate;
    private final TenantContextPort tenantContext;

    public SyncStatusService(JdbcTemplate jdbcTemplate, TenantContextPort tenantContext) {
        this.jdbcTemplate = jdbcTemplate;
        this.tenantContext = tenantContext;
    }

    public SyncStatusResponse buildStatus(String deviceId) {
        UUID tenantId = tenantContext.getTenantId();
        UUID deviceUuid = UUID.fromString(deviceId);

        Instant lastSyncAt = jdbcTemplate.queryForObject(
            // mobile_sync_log has no last_sync_at column (see V20260812_1);
            // derive the last sync time from the operation timestamps it does have.
            "SELECT COALESCE(GREATEST(MAX(completed_at), MAX(started_at)), 'epoch'::timestamptz) FROM mobile_sync_log WHERE tenant_id = ? AND device_id = ?",
            Instant.class, tenantId, deviceUuid
        );

        Map<String, SyncStatusResponse.EntitySyncStatus> entityStatuses = new HashMap<>();
        var cursorRows = jdbcTemplate.queryForList(
            "SELECT entity_type, cursor_value, last_sync_at FROM mobile_sync_cursor WHERE tenant_id = ? AND device_id = ?",
            tenantId, deviceUuid
        );

        for (var row : cursorRows) {
            String entityType = (String) row.get("entity_type");
            Instant lastPullAt = row.get("last_sync_at") instanceof Instant ts ? ts : null;
            String cursorValue = (String) row.get("cursor_value");
            long syncVersion = 0;
            if (cursorValue != null) {
                try {
                    syncVersion = Long.parseLong(new String(java.util.Base64.getUrlDecoder().decode(cursorValue)));
                } catch (IllegalArgumentException ignored) { }
            }
            entityStatuses.put(entityType, new SyncStatusResponse.EntitySyncStatus(
                entityType, lastPullAt, syncVersion, 0
            ));
        }

        Integer pendingMutations = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM mobile_sync_log WHERE tenant_id = ? AND device_id = ? AND status = 'STARTED'",
            Integer.class, tenantId, deviceUuid
        );

        Integer unresolvedConflicts = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM mobile_conflict_log WHERE tenant_id = ? AND device_id = ? AND status = 'OPEN'",
            Integer.class, tenantId, deviceUuid
        );

        String overallStatus;
        if ((unresolvedConflicts == null ? 0 : unresolvedConflicts) > 0) {
            overallStatus = "CONFLICTS_PENDING";
        } else if ((pendingMutations == null ? 0 : pendingMutations) > 0) {
            overallStatus = "SYNCING";
        } else {
            overallStatus = "OK";
        }

        return new SyncStatusResponse(
            deviceId,
            lastSyncAt,
            entityStatuses,
            pendingMutations == null ? 0 : pendingMutations,
            unresolvedConflicts == null ? 0 : unresolvedConflicts,
            overallStatus
        );
    }
}
