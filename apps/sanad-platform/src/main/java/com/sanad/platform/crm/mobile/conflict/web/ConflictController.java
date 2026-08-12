package com.sanad.platform.crm.mobile.conflict.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanad.platform.crm.integration.domain.TenantContextPort;
import com.sanad.platform.crm.mobile.conflict.service.ConflictService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Controller for conflict management operations.
 *
 * Requirements: API-007 (Conflict List), API-008 (Conflict Resolve),
 *               API-009 (Conflict Skip), ARCH-002 (12 Conflict Classes)
 *
 * Tenant/user identity from authenticated JWT via {@link TenantContextPort} (DEF-005).
 */
@RestController
@RequestMapping("/api/v2/mobile/conflicts")
public class ConflictController {

    private static final Logger log = LoggerFactory.getLogger(ConflictController.class);

    private final ConflictService conflictService;
    private final ObjectMapper objectMapper;
    private final TenantContextPort tenantContext;

    public ConflictController(ConflictService conflictService,
                              ObjectMapper objectMapper,
                              TenantContextPort tenantContext) {
        this.conflictService = conflictService;
        this.objectMapper = objectMapper;
        this.tenantContext = tenantContext;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> listConflicts(
            @RequestHeader("X-Device-Id") String deviceId) {

        UUID tenantId = tenantContext.getTenantId();
        UUID deviceUuid = UUID.fromString(deviceId);

        List<Map<String, Object>> conflicts = conflictService.getOpenConflicts(tenantId, deviceUuid);

        return ResponseEntity.ok(Map.of(
            "totalConflicts", conflicts.size(),
            "conflicts", conflicts
        ));
    }

    @PostMapping("/{conflictId}/resolve")
    public ResponseEntity<Map<String, String>> resolveConflict(
            @PathVariable UUID conflictId,
            @RequestBody Map<String, Object> body,
            @RequestHeader("X-Device-Id") String deviceId) {

        UUID tenantId = tenantContext.getTenantId();
        UUID userId = tenantContext.getPrincipalId();

        String resolution = (String) body.get("resolution");
        JsonNode resolutionData = objectMapper.valueToTree(body.get("resolutionData"));

        conflictService.resolveConflict(tenantId, conflictId, userId, resolution, resolutionData);

        log.info("Conflict resolved via API: id={}, resolution={}", conflictId, resolution);

        return ResponseEntity.ok(Map.of(
            "status", "RESOLVED",
            "conflictId", conflictId.toString(),
            "resolution", resolution
        ));
    }

    @PostMapping("/{conflictId}/skip")
    public ResponseEntity<Map<String, String>> skipConflict(
            @PathVariable UUID conflictId,
            @RequestHeader("X-Device-Id") String deviceId) {

        UUID tenantId = tenantContext.getTenantId();
        UUID userId = tenantContext.getPrincipalId();

        conflictService.resolveConflict(tenantId, conflictId, userId, "DEFERRED", null);

        log.info("Conflict skipped/deferred: id={}", conflictId);

        return ResponseEntity.ok(Map.of(
            "status", "DEFERRED",
            "conflictId", conflictId.toString()
        ));
    }
}
