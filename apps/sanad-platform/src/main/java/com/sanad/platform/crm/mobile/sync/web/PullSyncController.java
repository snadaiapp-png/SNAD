package com.sanad.platform.crm.mobile.sync.web;

import com.sanad.platform.crm.integration.domain.TenantContextPort;
import com.sanad.platform.crm.mobile.sync.model.DeltaSyncRequest;
import com.sanad.platform.crm.mobile.sync.model.DeltaSyncResponse;
import com.sanad.platform.crm.mobile.sync.service.PullSyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Controller for delta sync pull operations.
 *
 * Requirements: API-003 (Delta Sync Pull API), SYNC-002 (Delta Pull)
 *
 * Endpoints:
 *   GET /api/v2/mobile/sync/pull
 *
 * Tenant identity is resolved from the authenticated JWT security context via
 * {@link TenantContextPort} (set by {@code JwtAuthenticationFilter}). It is
 * NEVER read from request body, query parameters, or client headers (DEF-005).
 */
@RestController
@RequestMapping("/api/v2/mobile/sync")
public class PullSyncController {

    private static final Logger log = LoggerFactory.getLogger(PullSyncController.class);

    private final PullSyncService pullSyncService;
    private final TenantContextPort tenantContext;

    public PullSyncController(PullSyncService pullSyncService, TenantContextPort tenantContext) {
        this.pullSyncService = pullSyncService;
        this.tenantContext = tenantContext;
    }

    /**
     * Delta sync pull — returns only entities changed since client's last cursor.
     *
     * Headers:
     *   Authorization: Bearer {access_token}   (validated by JwtAuthenticationFilter)
     *   X-Device-Id: {device_uuid}
     *
     * Query params:
     *   entityType: account|contact|lead|opportunity|task|note|activity
     *   cursor: Base64-encoded sync version (optional)
     *   limit: max entities to return (default 100, max 500)
     */
    @GetMapping("/pull")
    public ResponseEntity<DeltaSyncResponse> pull(
            @RequestParam String entityType,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false, defaultValue = "100") Integer limit,
            @RequestHeader("X-Device-Id") String deviceId) {

        UUID tenantId = tenantContext.getTenantId();
        UUID deviceUuid = UUID.fromString(deviceId);

        log.info("Delta pull request: tenant={}, device={}, entity={}, cursor={}",
            tenantId, deviceUuid, entityType, cursor != null ? cursor.substring(0, Math.min(20, cursor.length())) : "null");

        DeltaSyncRequest syncRequest = new DeltaSyncRequest(entityType, cursor, limit);
        DeltaSyncResponse response = pullSyncService.pull(tenantId, deviceUuid, syncRequest);

        // ETag header for client caching
        String etag = "\"" + response.serverTimestamp().toEpochMilli() + "\"";

        return ResponseEntity.ok()
            .header("ETag", etag)
            .header("X-Entity-Count", String.valueOf(response.entityCount()))
            .header("X-Has-More", String.valueOf(response.hasMore()))
            .body(response);
    }
}
