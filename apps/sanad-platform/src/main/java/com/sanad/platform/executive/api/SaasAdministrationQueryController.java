package com.sanad.platform.executive.api;

import com.sanad.platform.admin.api.SaasAdminDtos;
import com.sanad.platform.admin.service.SaasAdministrationService;
import com.sanad.platform.security.authorization.ControlPlaneAccessGuard;
import com.sanad.platform.security.authorization.RequireCapability;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

/** Executive Management — SaaS plan, subscription, and billing queries. */
@RestController
@RequestMapping("/api/v1/executive")
public class SaasAdministrationQueryController {

    private final ControlPlaneAccessGuard accessGuard;
    private final SaasAdministrationService saasService;

    public SaasAdministrationQueryController(
            ControlPlaneAccessGuard accessGuard,
            SaasAdministrationService saasService
    ) {
        this.accessGuard = accessGuard;
        this.saasService = saasService;
    }

    @GetMapping("/plans")
    @RequireCapability("ROLE.READ")
    public ResponseEntity<List<SaasAdminDtos.PlanResponse>> plans(Authentication authentication) {
        accessGuard.require(authentication);
        return ResponseEntity.ok(saasService.listPlans());
    }

    @GetMapping("/subscriptions")
    @RequireCapability("ROLE.READ")
    public ResponseEntity<List<SaasAdminDtos.SubscriptionResponse>> subscriptions(Authentication authentication) {
        accessGuard.require(authentication);
        return ResponseEntity.ok(saasService.listSubscriptions());
    }

    @GetMapping("/billing/invoices")
    @RequireCapability("ROLE.READ")
    public ResponseEntity<List<SaasAdminDtos.InvoiceResponse>> invoices(Authentication authentication) {
        accessGuard.require(authentication);
        return ResponseEntity.ok(saasService.listInvoices());
    }

    @GetMapping("/tenants/{tenantId}/organizations")
    @RequireCapability("ROLE.READ")
    public ResponseEntity<List<SaasAdminDtos.OrganizationResponse>> organizations(
            Authentication authentication,
            @org.springframework.web.bind.annotation.PathVariable String tenantId
    ) {
        accessGuard.require(authentication);
        return ResponseEntity.ok(saasService.listOrganizations(tenantId));
    }

    @GetMapping("/tenants/{tenantId}/organizations/{organizationId}/memberships")
    @RequireCapability("ROLE.READ")
    public ResponseEntity<List<SaasAdminDtos.MembershipResponse>> memberships(
            Authentication authentication,
            @org.springframework.web.bind.annotation.PathVariable String tenantId,
            @org.springframework.web.bind.annotation.PathVariable String organizationId
    ) {
        accessGuard.require(authentication);
        return ResponseEntity.ok(saasService.listMemberships(tenantId, organizationId));
    }
}
