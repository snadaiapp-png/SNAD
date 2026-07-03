package com.sanad.platform.controlplane.api;

import com.sanad.platform.admin.api.AdminDtos.ChangeTenantStatusRequest;
import com.sanad.platform.admin.api.AdminDtos.CreateTenantRequest;
import com.sanad.platform.admin.api.AdminDtos.SystemServiceResponse;
import com.sanad.platform.admin.api.AdminDtos.TenantResponse;
import com.sanad.platform.admin.api.AdminDtos.UpdateSystemStatusRequest;
import com.sanad.platform.admin.service.AdminPlatformService;
import com.sanad.platform.security.authorization.ControlPlaneAccessGuard;
import com.sanad.platform.security.authorization.RequireCapability;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

/** Mutating endpoints for tenant and system lifecycle administration. */
@RestController
@RequestMapping("/api/v1/control-plane")
public class PlatformOperationsCommandController {

    private final ControlPlaneAccessGuard accessGuard;
    private final AdminPlatformService platformService;

    public PlatformOperationsCommandController(
            ControlPlaneAccessGuard accessGuard,
            AdminPlatformService platformService
    ) {
        this.accessGuard = accessGuard;
        this.platformService = platformService;
    }

    @PostMapping("/tenants")
    @RequireCapability("ROLE.WRITE")
    public ResponseEntity<TenantResponse> createTenant(
            Authentication authentication,
            @Valid @RequestBody CreateTenantRequest request
    ) {
        accessGuard.require(authentication);
        TenantResponse created = platformService.createTenant(request, authentication);
        return ResponseEntity.created(URI.create("/api/v1/control-plane/tenants/" + created.id()))
                .body(created);
    }

    @PatchMapping("/tenants/{tenantId}/status")
    @RequireCapability("ROLE.WRITE")
    public ResponseEntity<TenantResponse> changeTenantStatus(
            Authentication authentication,
            @PathVariable UUID tenantId,
            @Valid @RequestBody ChangeTenantStatusRequest request
    ) {
        accessGuard.require(authentication);
        return ResponseEntity.ok(platformService.changeTenantStatus(tenantId, request, authentication));
    }

    @PatchMapping("/systems/{serviceId}/status")
    @RequireCapability("ROLE.WRITE")
    public ResponseEntity<SystemServiceResponse> updateSystemStatus(
            Authentication authentication,
            @PathVariable UUID serviceId,
            @Valid @RequestBody UpdateSystemStatusRequest request
    ) {
        accessGuard.require(authentication);
        return ResponseEntity.ok(platformService.updateSystemStatus(serviceId, request, authentication));
    }
}
