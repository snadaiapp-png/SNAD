package com.sanad.platform.executive.api;

import com.sanad.platform.admin.api.AdminDtos;
import com.sanad.platform.admin.service.PlatformAuditService;
import com.sanad.platform.executive.service.ExecutivePlatformService;
import com.sanad.platform.health.service.SystemHealthService;
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
    private final ExecutivePlatformService adminService;
    private final SystemHealthService systemHealthService;
    private final PlatformAuditService auditService;

    public PlatformOperationsQueryController(
            ControlPlaneAccessGuard accessGuard,
            ExecutivePlatformService adminService,
            SystemHealthService systemHealthService,
            PlatformAuditService auditService
    ) {
        this.accessGuard = accessGuard;
        this.adminService = adminService;
        this.systemHealthService = systemHealthService;
        this.auditService = auditService;
    }

    @GetMapping("/dashboard")
    @RequireCapability("EXECUTIVE_VIEW")
    public ResponseEntity<AdminDtos.DashboardResponse> dashboard(Authentication authentication) {
        accessGuard.require(authentication);
        return ResponseEntity.ok(adminService.dashboard());
    }

    @GetMapping("/tenants")
    @RequireCapability("EXECUTIVE_VIEW")
    public ResponseEntity<java.util.List<AdminDtos.TenantResponse>> tenants(Authentication authentication) {
        accessGuard.require(authentication);
        return ResponseEntity.ok(adminService.listTenants(null, null, 200, 0));
    }

    @GetMapping("/tenants/{tenantId}")
    @RequireCapability("EXECUTIVE_VIEW")
    public ResponseEntity<AdminDtos.TenantResponse> tenant(
            Authentication authentication,
            @PathVariable String tenantId
    ) {
        accessGuard.require(authentication);
        return ResponseEntity.ok(adminService.getTenant(java.util.UUID.fromString(tenantId)));
    }

    @GetMapping("/systems")
    @RequireCapability("EXECUTIVE_VIEW")
    public ResponseEntity<java.util.List<AdminDtos.SystemServiceResponse>> systems(Authentication authentication) {
        accessGuard.require(authentication);
        return ResponseEntity.ok(systemHealthService.listSystemServices());
    }

    @GetMapping("/audit")
    @RequireCapability("EXECUTIVE_VIEW")
    public ResponseEntity<java.util.List<AdminDtos.AuditEntryResponse>> audit(
            Authentication authentication
    ) {
        accessGuard.require(authentication);
        return ResponseEntity.ok(auditService.recent(100));
    }

    @GetMapping("/access-check")
    public ResponseEntity<ExecutivePlatformService.AccessCheck> accessCheck(Authentication authentication) {
        return ResponseEntity.ok(adminService.accessCheck(authentication));
    }
}
