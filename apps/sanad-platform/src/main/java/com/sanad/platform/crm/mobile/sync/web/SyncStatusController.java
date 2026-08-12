package com.sanad.platform.crm.mobile.sync.web;

import com.sanad.platform.crm.integration.domain.TenantContextPort;
import com.sanad.platform.crm.mobile.sync.model.SyncStatusResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Controller for sync status operations.
 *
 * Requirements: API-005 (Sync Status API)
 *
 * Endpoints:
 *   GET /api/v2/mobile/sync/status
 *
 * Tenant identity from authenticated JWT via {@link TenantContextPort} (DEF-005).
 */
@RestController
@RequestMapping("/api/v2/mobile/sync")
public class SyncStatusController {

    private static final Logger log = LoggerFactory.getLogger(SyncStatusController.class);

    private final JdbcTemplate jdbcTemplate;
    private final TenantContextPort tenantContext;

    public SyncStatusController(JdbcTemplate jdbcTemplate, TenantContextPort tenantContext) {
        this.jdbcTemplate = jdbcTemplate;
        this.tenantContext = tenantContext;
    }

    @GetMapping("/status")
    public ResponseEntity<SyncStatusResponse> getStatus(
            @RequestHeader("X-Device-Id") String deviceId) {

        UUID tenantId = tenantContext.getTenantId();
        UUID deviceUuid = UUID.fromString(deviceId);

        Instant lastSyncAt = jdbcTemplate.queryForObject(
            "SELECT COALESCE(MAX(last_sync_at), 'epoch'::timestamptz) FROM mobile_sync_log WHERE tenant_id = ? AND device_id = ?",
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

        String overallStatus = (unresolvedConflicts != null && unresolvedConflicts > 0)
            ? "CONFLICTS_PENDING" : "ONLINE";

        SyncStatusResponse response = new SyncStatusResponse(
            deviceId,
            lastSyncAt,
            entityStatuses,
            pendingMutations != null ? pendingMutations : 0,
            unresolvedConflicts != null ? unresolvedConflicts : 0,
            overallStatus
        );

        return ResponseEntity.ok(response);
    }
}
