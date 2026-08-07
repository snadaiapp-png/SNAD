package com.sanad.platform.executive.api;

import com.sanad.platform.admin.api.SaasAdminDtos;
import com.sanad.platform.admin.service.SaasAdministrationService;
import com.sanad.platform.admin.service.TenantDirectoryAdministrationService;
import com.sanad.platform.security.authorization.ControlPlaneAccessGuard;
import com.sanad.platform.security.authorization.RequireCapability;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

/** Executive Management — SaaS plan, subscription, and billing commands. */
@RestController
@RequestMapping("/api/v1/executive")
public class SaasAdministrationCommandController {

    private final ControlPlaneAccessGuard accessGuard;
    private final SaasAdministrationService saasService;
    private final TenantDirectoryAdministrationService directoryService;

    public SaasAdministrationCommandController(
            ControlPlaneAccessGuard accessGuard,
            SaasAdministrationService saasService,
            TenantDirectoryAdministrationService directoryService
    ) {
        this.accessGuard = accessGuard;
        this.saasService = saasService;
        this.directoryService = directoryService;
    }

    @PostMapping("/plans")
    @RequireCapability("EXECUTIVE_BILLING")
    public ResponseEntity<SaasAdminDtos.PlanResponse> createPlan(
            Authentication authentication,
            @Valid @RequestBody SaasAdminDtos.CreatePlanRequest request
    ) {
        accessGuard.require(authentication);
        return ResponseEntity.ok(saasService.createPlan(request, authentication));
    }

    @PutMapping("/plans/{planId}")
    @RequireCapability("EXECUTIVE_MANAGE")
    public ResponseEntity<SaasAdminDtos.PlanResponse> updatePlan(
            Authentication authentication,
            @PathVariable String planId,
            @Valid @RequestBody SaasAdminDtos.UpdatePlanRequest request
    ) {
        accessGuard.require(authentication);
        return ResponseEntity.ok(saasService.updatePlan(UUID.fromString(planId), request, authentication));
    }

    @PostMapping("/subscriptions")
    @RequireCapability("EXECUTIVE_MANAGE")
    public ResponseEntity<SaasAdminDtos.SubscriptionResponse> createSubscription(
            Authentication authentication,
            @Valid @RequestBody SaasAdminDtos.CreateSubscriptionRequest request
    ) {
        accessGuard.require(authentication);
        return ResponseEntity.ok(saasService.createSubscription(request, authentication));
    }

    @PatchMapping("/subscriptions/{subscriptionId}/change-plan")
    @RequireCapability("EXECUTIVE_MANAGE")
    public ResponseEntity<SaasAdminDtos.SubscriptionResponse> changeSubscriptionPlan(
            Authentication authentication,
            @PathVariable String subscriptionId,
            @Valid @RequestBody SaasAdminDtos.ChangeSubscriptionPlanRequest request
    ) {
        accessGuard.require(authentication);
        return ResponseEntity.ok(saasService.changePlan(UUID.fromString(subscriptionId), request, authentication));
    }

    @PatchMapping("/subscriptions/{subscriptionId}/seats")
    @RequireCapability("EXECUTIVE_MANAGE")
    public ResponseEntity<SaasAdminDtos.SubscriptionResponse> changeSubscriptionSeats(
            Authentication authentication,
            @PathVariable String subscriptionId,
            @Valid @RequestBody SaasAdminDtos.ChangeSeatsRequest request
    ) {
        accessGuard.require(authentication);
        return ResponseEntity.ok(saasService.changeSeats(UUID.fromString(subscriptionId), request, authentication));
    }

    @PatchMapping("/subscriptions/{subscriptionId}/cancel")
    @RequireCapability("EXECUTIVE_MANAGE")
    public ResponseEntity<SaasAdminDtos.SubscriptionResponse> cancelSubscription(
            Authentication authentication,
            @PathVariable String subscriptionId,
            @Valid @RequestBody SaasAdminDtos.CancelSubscriptionRequest request
    ) {
        accessGuard.require(authentication);
        return ResponseEntity.ok(saasService.cancelSubscription(UUID.fromString(subscriptionId), request, authentication));
    }

    @PatchMapping("/subscriptions/{subscriptionId}/resume")
    @RequireCapability("EXECUTIVE_MANAGE")
    public ResponseEntity<SaasAdminDtos.SubscriptionResponse> resumeSubscription(
            Authentication authentication,
            @PathVariable String subscriptionId
    ) {
        accessGuard.require(authentication);
        return ResponseEntity.ok(saasService.resumeSubscription(UUID.fromString(subscriptionId), authentication));
    }

    @PostMapping("/subscriptions/{subscriptionId}/renew")
    @RequireCapability("EXECUTIVE_MANAGE")
    public ResponseEntity<SaasAdminDtos.SubscriptionResponse> renewSubscription(
            Authentication authentication,
            @PathVariable String subscriptionId
    ) {
        accessGuard.require(authentication);
        return ResponseEntity.ok(saasService.renewSubscription(UUID.fromString(subscriptionId), authentication));
    }

    @PostMapping("/billing/invoices/{invoiceId}/mark-paid")
    @RequireCapability("EXECUTIVE_MANAGE")
    public ResponseEntity<SaasAdminDtos.InvoiceResponse> markInvoicePaid(
            Authentication authentication,
            @PathVariable String invoiceId,
            @Valid @RequestBody SaasAdminDtos.MarkInvoicePaidRequest request
    ) {
        accessGuard.require(authentication);
        return ResponseEntity.ok(saasService.markInvoicePaid(UUID.fromString(invoiceId), request, authentication));
    }

    @PostMapping("/tenants/{tenantId}/organizations")
    @RequireCapability("EXECUTIVE_MANAGE")
    public ResponseEntity<SaasAdminDtos.OrganizationAdminResponse> createOrganization(
            Authentication authentication,
            @PathVariable String tenantId,
            @Valid @RequestBody SaasAdminDtos.CreateOrganizationAdminRequest request
    ) {
        accessGuard.require(authentication);
        return ResponseEntity.ok(directoryService.createOrganization(UUID.fromString(tenantId), request, authentication));
    }

    @PutMapping("/tenants/{tenantId}/organizations/{organizationId}")
    @RequireCapability("EXECUTIVE_MANAGE")
    public ResponseEntity<SaasAdminDtos.OrganizationAdminResponse> updateOrganization(
            Authentication authentication,
            @PathVariable String tenantId,
            @PathVariable String organizationId,
            @Valid @RequestBody SaasAdminDtos.UpdateOrganizationAdminRequest request
    ) {
        accessGuard.require(authentication);
        return ResponseEntity.ok(directoryService.updateOrganization(UUID.fromString(tenantId), UUID.fromString(organizationId), request, authentication));
    }

    @PatchMapping("/tenants/{tenantId}/organizations/{organizationId}/status")
    @RequireCapability("EXECUTIVE_MANAGE")
    public ResponseEntity<SaasAdminDtos.OrganizationAdminResponse> changeOrganizationStatus(
            Authentication authentication,
            @PathVariable String tenantId,
            @PathVariable String organizationId,
            @Valid @RequestBody SaasAdminDtos.ChangeStatusRequest request
    ) {
        accessGuard.require(authentication);
        return ResponseEntity.ok(directoryService.changeOrganizationStatus(UUID.fromString(tenantId), UUID.fromString(organizationId), request.status(), request.reason(), authentication));
    }

    @PostMapping("/tenants/{tenantId}/organizations/{organizationId}/memberships")
    @RequireCapability("EXECUTIVE_MANAGE")
    public ResponseEntity<SaasAdminDtos.MembershipAdminResponse> createMembership(
            Authentication authentication,
            @PathVariable String tenantId,
            @PathVariable String organizationId,
            @Valid @RequestBody SaasAdminDtos.CreateMembershipAdminRequest request
    ) {
        accessGuard.require(authentication);
        return ResponseEntity.ok(directoryService.createMembership(UUID.fromString(tenantId), UUID.fromString(organizationId), request, authentication));
    }

    @PatchMapping("/tenants/{tenantId}/organizations/{organizationId}/memberships/{membershipId}")
    @RequireCapability("EXECUTIVE_MANAGE")
    public ResponseEntity<SaasAdminDtos.MembershipAdminResponse> updateMembership(
            Authentication authentication,
            @PathVariable String tenantId,
            @PathVariable String organizationId,
            @PathVariable String membershipId,
            @Valid @RequestBody SaasAdminDtos.UpdateMembershipAdminRequest request
    ) {
        accessGuard.require(authentication);
        return ResponseEntity.ok(directoryService.updateMembership(UUID.fromString(tenantId), UUID.fromString(organizationId), UUID.fromString(membershipId), request, authentication));
    }
}
