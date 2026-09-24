package com.sanad.platform.crm.mobile.sync.web;

import com.sanad.platform.crm.integration.domain.TenantContextPort;
import com.sanad.platform.crm.mobile.sync.model.PushSyncRequest;
import com.sanad.platform.crm.mobile.sync.model.PushSyncResponse;
import com.sanad.platform.crm.mobile.sync.service.PushSyncService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Controller for batch sync push operations.
 *
 * Requirements: API-004 (Batch Sync Push API), SYNC-017 (Per-Mutation ACK)
 *
 * Endpoints:
 *   POST /api/v2/mobile/sync/push
 *
 * Tenant/user identity is resolved from the authenticated JWT security context
 * via {@link TenantContextPort} (DEF-005). Never trusted from client input.
 */
@RestController
@RequestMapping("/api/v2/mobile/sync")
public class PushSyncController {

    private static final Logger log = LoggerFactory.getLogger(PushSyncController.class);

    private final PushSyncService pushSyncService;
    private final TenantContextPort tenantContext;

    public PushSyncController(PushSyncService pushSyncService, TenantContextPort tenantContext) {
        this.pushSyncService = pushSyncService;
        this.tenantContext = tenantContext;
    }

    /**
     * Batch sync push — accepts array of mutation envelopes, returns per-mutation results.
     *
     * Headers:
     *   Authorization: Bearer {access_token}
     *   X-Device-Id: {device_uuid}
     *   X-Idempotency-Key: {key} (optional, per-request idempotency)
     *
     * Response: Per-mutation ACK with APPLIED/REJECTED/CONFLICT/DUPLICATE status.
     */
    @PostMapping("/push")
    public ResponseEntity<PushSyncResponse> push(
            @Valid @RequestBody PushSyncRequest syncRequest,
            @RequestHeader("X-Device-Id") String deviceId,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey) {

        UUID tenantId = tenantContext.getTenantId();
        UUID userId = tenantContext.getPrincipalId();
        UUID deviceUuid = UUID.fromString(deviceId);

        log.info("Push sync request: tenant={}, device={}, mutations={}",
            tenantId, deviceUuid, syncRequest.mutations().size());

        PushSyncResponse response = pushSyncService.push(tenantId, deviceUuid, userId, syncRequest);

        // Overall HTTP status reflects per-mutation outcomes
        int statusCode = 200;
        if (response.rejected() > 0 && response.applied() == 0) {
            statusCode = 412; // All rejected — Precondition Failed
        } else if (response.rejected() > 0) {
            statusCode = 207; // Multi-Status — some succeeded, some failed
        }

        return ResponseEntity.status(statusCode)
            .header("X-Applied", String.valueOf(response.applied()))
            .header("X-Rejected", String.valueOf(response.rejected()))
            .header("X-Duplicates", String.valueOf(response.duplicates()))
            .body(response);
    }
}
