package com.sanad.platform.executive.api;

import com.sanad.platform.admin.api.AdminDtos;
import com.sanad.platform.admin.service.AdminPlatformService;
import com.sanad.platform.security.authorization.ControlPlaneAccessGuard;
import com.sanad.platform.security.authorization.RequireCapability;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Executive Management — tenant management, directory, billing, and platform operations. */
@RestController
@RequestMapping("/api/v1/executive")
public class PlatformOperationsQueryController {

    private final ControlPlaneAccessGuard accessGuard;
    private final AdminPlatformService adminService;

    public PlatformOperationsQueryController(
            ControlPlaneAccessGuard accessGuard,
            AdminPlatformService adminService
    ) {
        this.accessGuard = accessGuard;
        this.adminService = adminService;
    }

    @GetMapping("/dashboard")
    @RequireCapability("ROLE.READ")
    public ResponseEntity<AdminDtos.DashboardResponse> dashboard(Authentication authentication) {
        accessGuard.require(authentication);
        return ResponseEntity.ok(adminService.dashboard());
    }

    @GetMapping("/tenants")
    @RequireCapability("ROLE.READ")
    public ResponseEntity<java.util.List<AdminDtos.TenantResponse>> tenants(Authentication authentication) {
        accessGuard.require(authentication);
        return ResponseEntity.ok(adminService.listTenants());
    }

    @GetMapping("/tenants/{tenantId}")
    @RequireCapability("ROLE.READ")
    public ResponseEntity<AdminDtos.TenantResponse> tenant(
            Authentication authentication,
            @PathVariable String tenantId
    ) {
        accessGuard.require(authentication);
        return ResponseEntity.ok(adminService.getTenant(tenantId));
    }

    @GetMapping("/systems")
    @RequireCapability("ROLE.READ")
    public ResponseEntity<java.util.List<AdminDtos.SystemServiceResponse>> systems(Authentication authentication) {
        accessGuard.require(authentication);
        return ResponseEntity.ok(adminService.listSystems());
    }

    @GetMapping("/audit")
    @RequireCapability("ROLE.READ")
    public ResponseEntity<java.util.List<com.sanad.platform.admin.service.PlatformAuditService.AuditEntry>> audit(
            Authentication authentication
    ) {
        accessGuard.require(authentication);
        return ResponseEntity.ok(adminService.listAuditEntries());
    }

    @GetMapping("/access-check")
    public ResponseEntity<AdminPlatformService.AccessCheck> accessCheck(Authentication authentication) {
        return ResponseEntity.ok(adminService.accessCheck(authentication));
    }
}
