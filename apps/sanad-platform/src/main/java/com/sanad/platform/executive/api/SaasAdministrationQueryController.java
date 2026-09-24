package com.sanad.platform.executive.api;

import com.sanad.platform.admin.api.SaasAdminDtos;
import com.sanad.platform.admin.service.SaasAdministrationService;
import com.sanad.platform.admin.service.TenantDirectoryAdministrationService;
import com.sanad.platform.security.authorization.ControlPlaneAccessGuard;
import com.sanad.platform.security.authorization.RequireCapability;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
import java.util.UUID;

/** Executive Management — SaaS plan, subscription, and billing queries. */
@RestController
@RequestMapping("/api/v1/executive")
public class SaasAdministrationQueryController {

    private final ControlPlaneAccessGuard accessGuard;
    private final SaasAdministrationService saasService;
    private final TenantDirectoryAdministrationService directoryService;

    public SaasAdministrationQueryController(
            ControlPlaneAccessGuard accessGuard,
            SaasAdministrationService saasService,
            TenantDirectoryAdministrationService directoryService
    ) {
        this.accessGuard = accessGuard;
        this.saasService = saasService;
        this.directoryService = directoryService;
    }

    @GetMapping("/plans")
    @RequireCapability("EXECUTIVE_VIEW")
    public ResponseEntity<List<SaasAdminDtos.PlanResponse>> plans(Authentication authentication) {
        accessGuard.require(authentication);
        return ResponseEntity.ok(saasService.listPlans());
    }

    @GetMapping("/subscriptions")
    @RequireCapability("EXECUTIVE_VIEW")
    public ResponseEntity<List<SaasAdminDtos.SubscriptionResponse>> subscriptions(
            Authentication authentication,
            @org.springframework.web.bind.annotation.RequestParam(required = false) UUID tenantId
    ) {
        accessGuard.require(authentication);
        if (tenantId != null) {
            return ResponseEntity.ok(saasService.listSubscriptions(tenantId));
        }
        return ResponseEntity.ok(List.of());
    }

    @GetMapping("/billing/invoices")
    @RequireCapability("EXECUTIVE_VIEW")
    public ResponseEntity<List<SaasAdminDtos.InvoiceResponse>> invoices(
            Authentication authentication,
            @org.springframework.web.bind.annotation.RequestParam(required = false) UUID tenantId
    ) {
        accessGuard.require(authentication);
        if (tenantId != null) {
            return ResponseEntity.ok(saasService.listInvoices(tenantId));
        }
        return ResponseEntity.ok(List.of());
    }

    @GetMapping("/tenants/{tenantId}/organizations")
    @RequireCapability("EXECUTIVE_VIEW")
    public ResponseEntity<List<SaasAdminDtos.OrganizationAdminResponse>> organizations(
            Authentication authentication,
            @PathVariable String tenantId
    ) {
        accessGuard.require(authentication);
        return ResponseEntity.ok(directoryService.listOrganizations(UUID.fromString(tenantId)));
    }

    @GetMapping("/tenants/{tenantId}/organizations/{organizationId}/memberships")
    @RequireCapability("EXECUTIVE_VIEW")
    public ResponseEntity<List<SaasAdminDtos.MembershipAdminResponse>> memberships(
            Authentication authentication,
            @PathVariable String tenantId,
            @PathVariable String organizationId
    ) {
        accessGuard.require(authentication);
        return ResponseEntity.ok(directoryService.listMemberships(UUID.fromString(tenantId), UUID.fromString(organizationId)));
    }
}
