package com.sanad.platform.crm.mobile.sync.web;

import com.sanad.platform.crm.mobile.sync.model.SyncStatusResponse;
import com.sanad.platform.crm.mobile.sync.service.SyncStatusService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controller for sync status operations.
 *
 * <p>Delegates all persistence to {@link SyncStatusService} so this {@code web} package
 * does not depend on {@code org.springframework.jdbc..} (CrmArchitectureTest rule).
 *
 * <p>Requirements: API-005 (Sync Status API), CRM-022 architecture rule.
 *
 * <p>Endpoints:
 *   GET /api/v2/mobile/sync/status
 */
@RestController
@RequestMapping("/api/v2/mobile/sync")
public class SyncStatusController {

    private static final Logger log = LoggerFactory.getLogger(SyncStatusController.class);

    private final SyncStatusService syncStatusService;

    public SyncStatusController(SyncStatusService syncStatusService) {
        this.syncStatusService = syncStatusService;
    }

    @GetMapping("/status")
    public ResponseEntity<SyncStatusResponse> getStatus(
            @RequestHeader("X-Device-Id") String deviceId) {
        log.debug("GET sync status for device {}", deviceId);
        return ResponseEntity.ok(syncStatusService.buildStatus(deviceId));
    }
}
