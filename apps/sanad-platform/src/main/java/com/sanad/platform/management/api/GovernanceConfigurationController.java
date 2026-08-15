package com.sanad.platform.management.api;

import com.sanad.platform.management.application.GovernanceConfigurationDefaults;
import com.sanad.platform.management.application.GovernanceConfigurationService;
import com.sanad.platform.management.api.GovernanceConfigDtos.ConfigurationResponse;
import com.sanad.platform.management.api.GovernanceConfigDtos.CreateConfigurationRequest;
import com.sanad.platform.management.api.GovernanceConfigDtos.UpdateConfigurationRequest;
import com.sanad.platform.security.authorization.RequireCapability;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.sanad.platform.security.SecurityContextUtils.tenantId;

/**
 * Senior Management — Governance Configuration API (GAP 26).
 *
 * <p>Endpoints mounted under {@code /api/v1/management/governance/configurations}.
 * Backed by {@link GovernanceConfigurationService}. Tenant-scoped; every
 * mutation requires {@code GOVERNANCE_CONFIG.ADMIN} capability.
 */
@RestController
@RequestMapping("/api/v1/management/governance/configurations")
public class GovernanceConfigurationController {

    private final GovernanceConfigurationService configService;
    private final GovernanceConfigurationDefaults defaults;

    public GovernanceConfigurationController(
            GovernanceConfigurationService configService,
            GovernanceConfigurationDefaults defaults) {
        this.configService = configService;
        this.defaults = defaults;
    }

    @GetMapping
    @RequireCapability("GOVERNANCE_CONFIG.VIEW")
    public ResponseEntity<List<ConfigurationResponse>> listConfigurations(Authentication auth) {
        UUID tenantId = tenantId(auth);
        return ResponseEntity.ok(configService.list(tenantId));
    }

    @GetMapping("/defaults")
    @RequireCapability("GOVERNANCE_CONFIG.VIEW")
    public ResponseEntity<Map<String, String>> listDefaults() {
        return ResponseEntity.ok(defaults.allDefaults());
    }

    @PostMapping
    @RequireCapability("GOVERNANCE_CONFIG.WRITE")
    public ResponseEntity<ConfigurationResponse> createConfiguration(
            Authentication auth,
            @Valid @RequestBody CreateConfigurationRequest request
    ) {
        UUID tenantId = tenantId(auth);
        return ResponseEntity.ok(configService.create(tenantId, request, auth));
    }

    @GetMapping("/{configId}")
    @RequireCapability("GOVERNANCE_CONFIG.VIEW")
    public ResponseEntity<ConfigurationResponse> getConfiguration(
            Authentication auth,
            @PathVariable UUID configId
    ) {
        UUID tenantId = tenantId(auth);
        return ResponseEntity.ok(configService.get(tenantId, configId));
    }

    @PutMapping("/{configId}")
    @RequireCapability("GOVERNANCE_CONFIG.WRITE")
    public ResponseEntity<ConfigurationResponse> updateConfiguration(
            Authentication auth,
            @PathVariable UUID configId,
            @Valid @RequestBody UpdateConfigurationRequest request
    ) {
        UUID tenantId = tenantId(auth);
        return ResponseEntity.ok(configService.update(tenantId, configId, request, auth));
    }

    @PostMapping("/{configId}/disable")
    @RequireCapability("GOVERNANCE_CONFIG.ADMIN")
    public ResponseEntity<ConfigurationResponse> disableConfiguration(
            Authentication auth,
            @PathVariable UUID configId
    ) {
        UUID tenantId = tenantId(auth);
        return ResponseEntity.ok(configService.update(tenantId, configId,
                new UpdateConfigurationRequest(null, false), auth));
    }

    @PostMapping("/{configId}/enable")
    @RequireCapability("GOVERNANCE_CONFIG.ADMIN")
    public ResponseEntity<ConfigurationResponse> enableConfiguration(
            Authentication auth,
            @PathVariable UUID configId
    ) {
        UUID tenantId = tenantId(auth);
        return ResponseEntity.ok(configService.update(tenantId, configId,
                new UpdateConfigurationRequest(null, true), auth));
    }

    @DeleteMapping("/{configId}")
    @RequireCapability("GOVERNANCE_CONFIG.ADMIN")
    public ResponseEntity<Void> deleteConfiguration(
            Authentication auth,
            @PathVariable UUID configId
    ) {
        UUID tenantId = tenantId(auth);
        configService.delete(tenantId, configId, auth);
        return ResponseEntity.noContent().build();
    }
}
