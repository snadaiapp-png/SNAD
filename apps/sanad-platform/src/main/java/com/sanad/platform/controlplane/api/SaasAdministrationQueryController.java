package com.sanad.platform.controlplane.api;

import com.sanad.platform.admin.api.SaasAdminDtos.EntitlementResponse;
import com.sanad.platform.admin.api.SaasAdminDtos.InvoiceResponse;
import com.sanad.platform.admin.api.SaasAdminDtos.MembershipAdminResponse;
import com.sanad.platform.admin.api.SaasAdminDtos.OrganizationAdminResponse;
import com.sanad.platform.admin.api.SaasAdminDtos.PlanResponse;
import com.sanad.platform.admin.api.SaasAdminDtos.SubscriptionEventResponse;
import com.sanad.platform.admin.api.SaasAdminDtos.SubscriptionResponse;
import com.sanad.platform.admin.service.SaasAdministrationService;
import com.sanad.platform.admin.service.TenantDirectoryAdministrationService;
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

/** Read models for the complete SaaS administration control plane. */
@RestController
@RequestMapping("/api/v1/control-plane")
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
    @RequireCapability("ROLE.READ")
    public ResponseEntity<List<PlanResponse>> plans(Authentication authentication) {
        accessGuard.require(authentication);
        return ResponseEntity.ok(saasService.listPlans());
    }

    @GetMapping("/plans/{planId}")
    @RequireCapability("ROLE.READ")
    public ResponseEntity<PlanResponse> plan(Authentication authentication, @PathVariable UUID planId) {
        accessGuard.require(authentication);
        return ResponseEntity.ok(saasService.getPlan(planId));
    }

    @GetMapping("/subscriptions")
    @RequireCapability("ROLE.READ")
    public ResponseEntity<List<SubscriptionResponse>> subscriptions(
            Authentication authentication,
            @RequestParam(required = false) UUID tenantId
    ) {
        accessGuard.require(authentication);
        return ResponseEntity.ok(saasService.listSubscriptions(tenantId));
    }

    @GetMapping("/subscriptions/{subscriptionId}")
    @RequireCapability("ROLE.READ")
    public ResponseEntity<SubscriptionResponse> subscription(
            Authentication authentication,
            @PathVariable UUID subscriptionId
    ) {
        accessGuard.require(authentication);
        return ResponseEntity.ok(saasService.getSubscription(subscriptionId));
    }

    @GetMapping("/subscriptions/{subscriptionId}/entitlements")
    @RequireCapability("ROLE.READ")
    public ResponseEntity<List<EntitlementResponse>> subscriptionEntitlements(
            Authentication authentication,
            @PathVariable UUID subscriptionId
    ) {
        accessGuard.require(authentication);
        return ResponseEntity.ok(saasService.subscriptionEntitlements(subscriptionId));
    }

    @GetMapping("/subscriptions/{subscriptionId}/events")
    @RequireCapability("ROLE.READ")
    public ResponseEntity<List<SubscriptionEventResponse>> subscriptionEvents(
            Authentication authentication,
            @PathVariable UUID subscriptionId
    ) {
        accessGuard.require(authentication);
        return ResponseEntity.ok(saasService.subscriptionEvents(subscriptionId));
    }

    @GetMapping("/billing/invoices")
    @RequireCapability("ROLE.READ")
    public ResponseEntity<List<InvoiceResponse>> invoices(
            Authentication authentication,
            @RequestParam(required = false) UUID tenantId
    ) {
        accessGuard.require(authentication);
        return ResponseEntity.ok(saasService.listInvoices(tenantId));
    }

    @GetMapping("/tenants/{tenantId}/organizations")
    @RequireCapability("ROLE.READ")
    public ResponseEntity<List<OrganizationAdminResponse>> organizations(
            Authentication authentication,
            @PathVariable UUID tenantId
    ) {
        accessGuard.require(authentication);
        return ResponseEntity.ok(directoryService.listOrganizations(tenantId));
    }

    @GetMapping("/tenants/{tenantId}/organizations/{organizationId}/memberships")
    @RequireCapability("ROLE.READ")
    public ResponseEntity<List<MembershipAdminResponse>> memberships(
            Authentication authentication,
            @PathVariable UUID tenantId,
            @PathVariable UUID organizationId
    ) {
        accessGuard.require(authentication);
        return ResponseEntity.ok(directoryService.listMemberships(tenantId, organizationId));
    }
}