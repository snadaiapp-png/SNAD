package com.sanad.platform.controlplane.api;

import com.sanad.platform.admin.api.AdminDtos.AuditEntryResponse;
import com.sanad.platform.admin.api.AdminDtos.DashboardResponse;
import com.sanad.platform.admin.api.AdminDtos.SystemServiceResponse;
import com.sanad.platform.admin.api.AdminDtos.TenantResponse;
import com.sanad.platform.admin.service.AdminPlatformService;
import com.sanad.platform.admin.service.PlatformAuditService;
import com.sanad.platform.security.authorization.ControlPlaneAccessGuard;
import com.sanad.platform.security.authorization.RequireCapability;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Read-only endpoints for executive and operational control-plane views. */
@RestController
@RequestMapping("/api/v1/control-plane")
public class PlatformOperationsQueryController {

    private final ControlPlaneAccessGuard accessGuard;
    private final AdminPlatformService platformService;
    private final PlatformAuditService auditService;

    public PlatformOperationsQueryController(
            ControlPlaneAccessGuard accessGuard,
            AdminPlatformService platformService,
            PlatformAuditService auditService
    ) {
        this.accessGuard = accessGuard;
        this.platformService = platformService;
        this.auditService = auditService;
    }

    @GetMapping("/dashboard")
    @RequireCapability("ROLE.READ")
    public ResponseEntity<DashboardResponse> dashboard(Authentication authentication) {
        accessGuard.require(authentication);
        return ResponseEntity.ok(platformService.dashboard());
    }

    @GetMapping("/tenants")
    @RequireCapability("ROLE.READ")
    public ResponseEntity<List<TenantResponse>> listTenants(
            Authentication authentication,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset
    ) {
        accessGuard.require(authentication);
        return ResponseEntity.ok(platformService.listTenants(search, status, limit, offset));
    }

    @GetMapping("/tenants/{tenantId}")
    @RequireCapability("ROLE.READ")
    public ResponseEntity<TenantResponse> getTenant(
            Authentication authentication,
            @PathVariable UUID tenantId
    ) {
        accessGuard.require(authentication);
        return ResponseEntity.ok(platformService.getTenant(tenantId));
    }

    @GetMapping("/systems")
    @RequireCapability("ROLE.READ")
    public ResponseEntity<List<SystemServiceResponse>> systems(Authentication authentication) {
        accessGuard.require(authentication);
        return ResponseEntity.ok(platformService.listSystemServices());
    }

    @GetMapping("/audit")
    @RequireCapability("ROLE.READ")
    public ResponseEntity<List<AuditEntryResponse>> audit(
            Authentication authentication,
            @RequestParam(defaultValue = "100") int limit
    ) {
        accessGuard.require(authentication);
        return ResponseEntity.ok(auditService.recent(limit));
    }
}
