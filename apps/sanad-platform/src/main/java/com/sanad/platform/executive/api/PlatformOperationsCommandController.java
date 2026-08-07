package com.sanad.platform.executive.api;

import com.sanad.platform.admin.api.AdminDtos;
import com.sanad.platform.executive.service.ExecutivePlatformService;
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

/** Executive Management — command operations (create/update). */
@RestController
@RequestMapping("/api/v1/executive")
public class PlatformOperationsCommandController {

    private final ControlPlaneAccessGuard accessGuard;
    private final ExecutivePlatformService adminService;

    public PlatformOperationsCommandController(
            ControlPlaneAccessGuard accessGuard,
            ExecutivePlatformService adminService
    ) {
        this.accessGuard = accessGuard;
        this.adminService = adminService;
    }

    @PostMapping("/tenants")
    @RequireCapability("EXECUTIVE_MANAGE")
    public ResponseEntity<AdminDtos.TenantResponse> createTenant(
            Authentication authentication,
            @Valid @RequestBody AdminDtos.CreateTenantRequest request
    ) {
        accessGuard.require(authentication);
        return ResponseEntity.ok(adminService.createTenant(request, authentication));
    }

    @PatchMapping("/tenants/{tenantId}/status")
    @RequireCapability("EXECUTIVE_MANAGE")
    public ResponseEntity<AdminDtos.TenantResponse> changeTenantStatus(
            Authentication authentication,
            @PathVariable String tenantId,
            @Valid @RequestBody AdminDtos.ChangeTenantStatusRequest request
    ) {
        accessGuard.require(authentication);
        return ResponseEntity.ok(adminService.changeTenantStatus(java.util.UUID.fromString(tenantId), request, authentication));
    }

}
