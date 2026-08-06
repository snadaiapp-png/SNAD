package com.sanad.platform.executive.api;

import com.sanad.platform.admin.api.SaasAdminDtos;
import com.sanad.platform.admin.service.SaasAdministrationService;
import com.sanad.platform.security.authorization.ControlPlaneAccessGuard;
import com.sanad.platform.security.authorization.RequireCapability;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/** Executive Management — SaaS plan, subscription, and billing commands. */
@RestController
@RequestMapping("/api/v1/executive")
public class SaasAdministrationCommandController {

    private final ControlPlaneAccessGuard accessGuard;
    private final SaasAdministrationService saasService;

    public SaasAdministrationCommandController(
            ControlPlaneAccessGuard accessGuard,
            SaasAdministrationService saasService
    ) {
        this.accessGuard = accessGuard;
        this.saasService = saasService;
    }

    @PostMapping("/plans")
    @RequireCapability("ROLE.WRITE")
    public ResponseEntity<SaasAdminDtos.PlanResponse> createPlan(
            Authentication authentication,
            @Valid @RequestBody SaasAdminDtos.CreatePlanRequest request
    ) {
        accessGuard.require(authentication);
        return ResponseEntity.ok(saasService.createPlan(request));
    }

    @PutMapping("/plans/{planId}")
    @RequireCapability("ROLE.WRITE")
    public ResponseEntity<SaasAdminDtos.PlanResponse> updatePlan(
            Authentication authentication,
            @PathVariable String planId,
            @Valid @RequestBody SaasAdminDtos.UpdatePlanRequest request
    ) {
        accessGuard.require(authentication);
        return ResponseEntity.ok(saasService.updatePlan(planId, request));
    }

    @PatchMapping("/plans/{planId}/status")
    @RequireCapability("ROLE.WRITE")
    public ResponseEntity<SaasAdminDtos.PlanResponse> changePlanStatus(
            Authentication authentication,
            @PathVariable String planId,
            @Valid @RequestBody SaasAdminDtos.ChangePlanStatusRequest request
    ) {
        accessGuard.require(authentication);
        return ResponseEntity.ok(saasService.changePlanStatus(planId, request));
    }

    @PostMapping("/subscriptions")
    @RequireCapability("ROLE.WRITE")
    public ResponseEntity<SaasAdminDtos.SubscriptionResponse> createSubscription(
            Authentication authentication,
            @Valid @RequestBody SaasAdminDtos.CreateSubscriptionRequest request
    ) {
        accessGuard.require(authentication);
        return ResponseEntity.ok(saasService.createSubscription(request));
    }

    @PatchMapping("/subscriptions/{subscriptionId}/change-plan")
    @RequireCapability("ROLE.WRITE")
    public ResponseEntity<SaasAdminDtos.SubscriptionResponse> changeSubscriptionPlan(
            Authentication authentication,
            @PathVariable String subscriptionId,
            @Valid @RequestBody SaasAdminDtos.ChangeSubscriptionPlanRequest request
    ) {
        accessGuard.require(authentication);
        return ResponseEntity.ok(saasService.changeSubscriptionPlan(subscriptionId, request));
    }

    @PatchMapping("/subscriptions/{subscriptionId}/seats")
    @RequireCapability("ROLE.WRITE")
    public ResponseEntity<SaasAdminDtos.SubscriptionResponse> changeSubscriptionSeats(
            Authentication authentication,
            @PathVariable String subscriptionId,
            @Valid @RequestBody SaasAdminDtos.ChangeSeatsRequest request
    ) {
        accessGuard.require(authentication);
        return ResponseEntity.ok(saasService.changeSubscriptionSeats(subscriptionId, request));
    }

    @PatchMapping("/subscriptions/{subscriptionId}/cancel")
    @RequireCapability("ROLE.WRITE")
    public ResponseEntity<SaasAdminDtos.SubscriptionResponse> cancelSubscription(
            Authentication authentication,
            @PathVariable String subscriptionId,
            @Valid @RequestBody SaasAdminDtos.CancelSubscriptionRequest request
    ) {
        accessGuard.require(authentication);
        return ResponseEntity.ok(saasService.cancelSubscription(subscriptionId, request));
    }

    @PatchMapping("/subscriptions/{subscriptionId}/resume")
    @RequireCapability("ROLE.WRITE")
    public ResponseEntity<SaasAdminDtos.SubscriptionResponse> resumeSubscription(
            Authentication authentication,
            @PathVariable String subscriptionId
    ) {
        accessGuard.require(authentication);
        return ResponseEntity.ok(saasService.resumeSubscription(subscriptionId));
    }

    @PostMapping("/subscriptions/{subscriptionId}/renew")
    @RequireCapability("ROLE.WRITE")
    public ResponseEntity<SaasAdminDtos.SubscriptionResponse> renewSubscription(
            Authentication authentication,
            @PathVariable String subscriptionId
    ) {
        accessGuard.require(authentication);
        return ResponseEntity.ok(saasService.renewSubscription(subscriptionId));
    }

    @PostMapping("/billing/invoices/{invoiceId}/mark-paid")
    @RequireCapability("ROLE.WRITE")
    public ResponseEntity<SaasAdminDtos.InvoiceResponse> markInvoicePaid(
            Authentication authentication,
            @PathVariable String invoiceId,
            @Valid @RequestBody SaasAdminDtos.MarkInvoicePaidRequest request
    ) {
        accessGuard.require(authentication);
        return ResponseEntity.ok(saasService.markInvoicePaid(invoiceId, request));
    }

    @PostMapping("/tenants/{tenantId}/organizations")
    @RequireCapability("ROLE.WRITE")
    public ResponseEntity<SaasAdminDtos.OrganizationResponse> createOrganization(
            Authentication authentication,
            @PathVariable String tenantId,
            @Valid @RequestBody SaasAdminDtos.CreateOrganizationRequest request
    ) {
        accessGuard.require(authentication);
        return ResponseEntity.ok(saasService.createOrganization(tenantId, request));
    }

    @PutMapping("/tenants/{tenantId}/organizations/{organizationId}")
    @RequireCapability("ROLE.WRITE")
    public ResponseEntity<SaasAdminDtos.OrganizationResponse> updateOrganization(
            Authentication authentication,
            @PathVariable String tenantId,
            @PathVariable String organizationId,
            @Valid @RequestBody SaasAdminDtos.UpdateOrganizationRequest request
    ) {
        accessGuard.require(authentication);
        return ResponseEntity.ok(saasService.updateOrganization(tenantId, organizationId, request));
    }

    @PatchMapping("/tenants/{tenantId}/organizations/{organizationId}/status")
    @RequireCapability("ROLE.WRITE")
    public ResponseEntity<SaasAdminDtos.OrganizationResponse> changeOrganizationStatus(
            Authentication authentication,
            @PathVariable String tenantId,
            @PathVariable String organizationId,
            @Valid @RequestBody SaasAdminDtos.ChangeStatusRequest request
    ) {
        accessGuard.require(authentication);
        return ResponseEntity.ok(saasService.changeOrganizationStatus(tenantId, organizationId, request));
    }

    @PostMapping("/tenants/{tenantId}/organizations/{organizationId}/memberships")
    @RequireCapability("ROLE.WRITE")
    public ResponseEntity<SaasAdminDtos.MembershipResponse> createMembership(
            Authentication authentication,
            @PathVariable String tenantId,
            @PathVariable String organizationId,
            @Valid @RequestBody SaasAdminDtos.CreateMembershipRequest request
    ) {
        accessGuard.require(authentication);
        return ResponseEntity.ok(saasService.createMembership(tenantId, organizationId, request));
    }

    @PatchMapping("/tenants/{tenantId}/organizations/{organizationId}/memberships/{membershipId}")
    @RequireCapability("ROLE.WRITE")
    public ResponseEntity<SaasAdminDtos.MembershipResponse> updateMembership(
            Authentication authentication,
            @PathVariable String tenantId,
            @PathVariable String organizationId,
            @PathVariable String membershipId,
            @Valid @RequestBody SaasAdminDtos.UpdateMembershipRequest request
    ) {
        accessGuard.require(authentication);
        return ResponseEntity.ok(saasService.updateMembership(tenantId, organizationId, membershipId, request));
    }
}
