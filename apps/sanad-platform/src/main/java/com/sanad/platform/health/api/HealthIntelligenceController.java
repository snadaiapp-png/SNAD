package com.sanad.platform.health.api;

import com.sanad.platform.admin.api.AdminDtos.SystemServiceResponse;
import com.sanad.platform.admin.api.AdminDtos.UpdateSystemStatusRequest;
import com.sanad.platform.health.api.HealthDtos.HealthActionRequest;
import com.sanad.platform.health.api.HealthDtos.HealthActionResult;
import com.sanad.platform.health.api.HealthDtos.PlatformHealthResponse;
import com.sanad.platform.health.service.HealthIntelligenceService;
import com.sanad.platform.health.service.SystemHealthService;
import com.sanad.platform.security.authorization.ControlPlaneAccessGuard;
import com.sanad.platform.security.authorization.RequireCapability;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** System Health — monitoring, diagnostics, system services, and controlled self-healing. */
@RestController
@RequestMapping("/api/v1/system-health")
public class HealthIntelligenceController {

    private final ControlPlaneAccessGuard accessGuard;
    private final HealthIntelligenceService healthService;
    private final SystemHealthService systemHealthService;

    public HealthIntelligenceController(
            ControlPlaneAccessGuard accessGuard,
            HealthIntelligenceService healthService,
            SystemHealthService systemHealthService
    ) {
        this.accessGuard = accessGuard;
        this.healthService = healthService;
        this.systemHealthService = systemHealthService;
    }

    @GetMapping
    @RequireCapability("SYSTEM_HEALTH_VIEW")
    public ResponseEntity<PlatformHealthResponse> health(Authentication authentication) {
        accessGuard.require(authentication);
        return ResponseEntity.ok(healthService.snapshot());
    }

    @PostMapping("/actions")
    @RequireCapability("SYSTEM_HEALTH_MONITOR")
    public ResponseEntity<HealthActionResult> execute(
            Authentication authentication,
            @Valid @RequestBody HealthActionRequest request
    ) {
        accessGuard.require(authentication);
        return ResponseEntity.ok(healthService.execute(request, authentication));
    }

    @GetMapping("/systems")
    @RequireCapability("SYSTEM_HEALTH_VIEW")
    public ResponseEntity<List<SystemServiceResponse>> systems(Authentication authentication) {
        accessGuard.require(authentication);
        return ResponseEntity.ok(systemHealthService.listSystemServices());
    }

    @PatchMapping("/systems/{serviceId}/status")
    @RequireCapability("SYSTEM_HEALTH_ALERTS")
    public ResponseEntity<SystemServiceResponse> updateSystemStatus(
            Authentication authentication,
            @PathVariable java.util.UUID serviceId,
            @Valid @RequestBody UpdateSystemStatusRequest request
    ) {
        accessGuard.require(authentication);
        return ResponseEntity.ok(systemHealthService.updateSystemStatus(serviceId, request, authentication));
    }
}
