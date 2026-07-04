package com.sanad.platform.controlplane.api;

import com.sanad.platform.health.api.HealthDtos.HealthActionRequest;
import com.sanad.platform.health.api.HealthDtos.HealthActionResult;
import com.sanad.platform.health.api.HealthDtos.PlatformHealthResponse;
import com.sanad.platform.health.service.HealthIntelligenceService;
import com.sanad.platform.security.authorization.ControlPlaneAccessGuard;
import com.sanad.platform.security.authorization.RequireCapability;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Executive health, risk prediction and controlled self-healing endpoints. */
@RestController
@RequestMapping("/api/v1/control-plane/health")
public class HealthIntelligenceController {

    private final ControlPlaneAccessGuard accessGuard;
    private final HealthIntelligenceService healthService;

    public HealthIntelligenceController(
            ControlPlaneAccessGuard accessGuard,
            HealthIntelligenceService healthService
    ) {
        this.accessGuard = accessGuard;
        this.healthService = healthService;
    }

    @GetMapping
    @RequireCapability("ROLE.READ")
    public ResponseEntity<PlatformHealthResponse> health(Authentication authentication) {
        accessGuard.require(authentication);
        return ResponseEntity.ok(healthService.snapshot());
    }

    @PostMapping("/actions")
    @RequireCapability("ROLE.WRITE")
    public ResponseEntity<HealthActionResult> execute(
            Authentication authentication,
            @Valid @RequestBody HealthActionRequest request
    ) {
        accessGuard.require(authentication);
        return ResponseEntity.ok(healthService.execute(request, authentication));
    }
}
