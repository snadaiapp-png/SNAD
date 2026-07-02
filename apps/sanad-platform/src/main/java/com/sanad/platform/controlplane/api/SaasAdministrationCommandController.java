package com.sanad.platform.controlplane.api;

import com.sanad.platform.admin.api.SaasAdminDtos.CancelSubscriptionRequest;
import com.sanad.platform.admin.api.SaasAdminDtos.ChangeSeatsRequest;
import com.sanad.platform.admin.api.SaasAdminDtos.ChangeStatusRequest;
import com.sanad.platform.admin.api.SaasAdminDtos.ChangeSubscriptionPlanRequest;
import com.sanad.platform.admin.api.SaasAdminDtos.CreateMembershipAdminRequest;
import com.sanad.platform.admin.api.SaasAdminDtos.CreateOrganizationAdminRequest;
import com.sanad.platform.admin.api.SaasAdminDtos.CreatePlanRequest;
import com.sanad.platform.admin.api.SaasAdminDtos.CreateSubscriptionRequest;
import com.sanad.platform.admin.api.SaasAdminDtos.InvoiceResponse;
import com.sanad.platform.admin.api.SaasAdminDtos.MarkInvoicePaidRequest;
import com.sanad.platform.admin.api.SaasAdminDtos.MembershipAdminResponse;
import com.sanad.platform.admin.api.SaasAdminDtos.OrganizationAdminResponse;
import com.sanad.platform.admin.api.SaasAdminDtos.PlanResponse;
import com.sanad.platform.admin.api.SaasAdminDtos.SubscriptionResponse;
import com.sanad.platform.admin.api.SaasAdminDtos.UpdateMembershipAdminRequest;
import com.sanad.platform.admin.api.SaasAdminDtos.UpdateOrganizationAdminRequest;
import com.sanad.platform.admin.api.SaasAdminDtos.UpdatePlanRequest;
import com.sanad.platform.admin.service.SaasAdministrationService;
import com.sanad.platform.admin.service.TenantDirectoryAdministrationService;
import com.sanad.platform.security.authorization.ControlPlaneAccessGuard;
import com.sanad.platform.security.authorization.RequireCapability;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

/** Mutating operations for the complete SaaS administration control plane. */
@RestController
@RequestMapping("/api/v1/control-plane")
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
    @RequireCapability("ROLE.WRITE")
    public ResponseEntity<PlanResponse> createPlan(
            Authentication authentication,
            @Valid @RequestBody CreatePlanRequest request
    ) {
        accessGuard.require(authentication);
        PlanResponse created = saasService.createPlan(request, authentication);
        return ResponseEntity.created(URI.create("/api/v1/control-plane/plans/" + created.id())).body(created);
    }

    @PutMapping("/plans/{planId}")
    @RequireCapability("ROLE.WRITE")
    public ResponseEntity<PlanResponse> updatePlan(
            Authentication authentication,
            @PathVariable UUID planId,
            @Valid @RequestBody UpdatePlanRequest request
    ) {
        accessGuard.require(authentication);
        return ResponseEntity.ok(saasService.updatePlan(planId, request, authentication));
    }

    @PatchMapping("/plans/{planId}/status")
    @RequireCapability("ROLE.WRITE")
    public ResponseEntity<PlanResponse> changePlanStatus(
            Authentication authentication,
            @PathVariable UUID planId,
            @Valid @RequestBody ChangeStatusRequest request
    ) {
        accessGuard.require(authentication);
        return ResponseEntity.ok(saasService.changePlanStatus(planId, request.status(), request.reason(), authentication));
    }

    @PostMapping("/subscriptions")
    @RequireCapability("ROLE.WRITE")
    public ResponseEntity<SubscriptionResponse> createSubscription(
            Authentication authentication,
            @Valid @RequestBody CreateSubscriptionRequest request
    ) {
        accessGuard.require(authentication);
        SubscriptionResponse created = saasService.createSubscription(request, authentication);
        return ResponseEntity.created(URI.create("/api/v1/control-plane/subscriptions/" + created.id())).body(created);
    }

    @PatchMapping("/subscriptions/{subscriptionId}/change-plan")
    @RequireCapability("ROLE.WRITE")
    public ResponseEntity<SubscriptionResponse> changeSubscriptionPlan(
            Authentication authentication,
            @PathVariable UUID subscriptionId,
            @Valid @RequestBody ChangeSubscriptionPlanRequest request
    ) {
        accessGuard.require(authentication);
        return ResponseEntity.ok(saasService.changePlan(subscriptionId, request, authentication));
    }

    @PatchMapping("/subscriptions/{subscriptionId}/seats")
    @RequireCapability("ROLE.WRITE")
    public ResponseEntity<SubscriptionResponse> changeSeats(
            Authentication authentication,
            @PathVariable UUID subscriptionId,
            @Valid @RequestBody ChangeSeatsRequest request
    ) {
        accessGuard.require(authentication);
        return ResponseEntity.ok(saasService.changeSeats(subscriptionId, request, authentication));
    }

    @PatchMapping("/subscriptions/{subscriptionId}/cancel")
    @RequireCapability("ROLE.WRITE")
    public ResponseEntity<SubscriptionResponse> cancelSubscription(
            Authentication authentication,
            @PathVariable UUID subscriptionId,
            @Valid @RequestBody CancelSubscriptionRequest request
    ) {
        accessGuard.require(authentication);
        return ResponseEntity.ok(saasService.cancelSubscription(subscriptionId, request, authentication));
    }

    @PatchMapping("/subscriptions/{subscriptionId}/resume")
    @RequireCapability("ROLE.WRITE")
    public ResponseEntity<SubscriptionResponse> resumeSubscription(
            Authentication authentication,
            @PathVariable UUID subscriptionId
    ) {
        accessGuard.require(authentication);
        return ResponseEntity.ok(saasService.resumeSubscription(subscriptionId, authentication));
    }

    @PostMapping("/subscriptions/{subscriptionId}/renew")
    @RequireCapability("ROLE.WRITE")
    public ResponseEntity<SubscriptionResponse> renewSubscription(
            Authentication authentication,
            @PathVariable UUID subscriptionId
    ) {
        accessGuard.require(authentication);
        return ResponseEntity.ok(saasService.renewSubscription(subscriptionId, authentication));
    }

    @PostMapping("/billing/invoices/{invoiceId}/mark-paid")
    @RequireCapability("ROLE.WRITE")
    public ResponseEntity<InvoiceResponse> markInvoicePaid(
            Authentication authentication,
            @PathVariable UUID invoiceId,
            @Valid @RequestBody MarkInvoicePaidRequest request
    ) {
        accessGuard.require(authentication);
        return ResponseEntity.ok(saasService.markInvoicePaid(invoiceId, request, authentication));
    }

    @PostMapping("/tenants/{tenantId}/organizations")
    @RequireCapability("ROLE.WRITE")
    public ResponseEntity<OrganizationAdminResponse> createOrganization(
            Authentication authentication,
            @PathVariable UUID tenantId,
            @Valid @RequestBody CreateOrganizationAdminRequest request
    ) {
        accessGuard.require(authentication);
        OrganizationAdminResponse created = directoryService.createOrganization(tenantId, request, authentication);
        return ResponseEntity.created(URI.create(
                "/api/v1/control-plane/tenants/" + tenantId + "/organizations/" + created.id())).body(created);
    }

    @PutMapping("/tenants/{tenantId}/organizations/{organizationId}")
    @RequireCapability("ROLE.WRITE")
    public ResponseEntity<OrganizationAdminResponse> updateOrganization(
            Authentication authentication,
            @PathVariable UUID tenantId,
            @PathVariable UUID organizationId,
            @Valid @RequestBody UpdateOrganizationAdminRequest request
    ) {
        accessGuard.require(authentication);
        return ResponseEntity.ok(directoryService.updateOrganization(tenantId, organizationId, request, authentication));
    }

    @PatchMapping("/tenants/{tenantId}/organizations/{organizationId}/status")
    @RequireCapability("ROLE.WRITE")
    public ResponseEntity<OrganizationAdminResponse> changeOrganizationStatus(
            Authentication authentication,
            @PathVariable UUID tenantId,
            @PathVariable UUID organizationId,
            @Valid @RequestBody ChangeStatusRequest request
    ) {
        accessGuard.require(authentication);
        return ResponseEntity.ok(directoryService.changeOrganizationStatus(
                tenantId, organizationId, request.status(), request.reason(), authentication));
    }

    @PostMapping("/tenants/{tenantId}/organizations/{organizationId}/memberships")
    @RequireCapability("ROLE.WRITE")
    public ResponseEntity<MembershipAdminResponse> createMembership(
            Authentication authentication,
            @PathVariable UUID tenantId,
            @PathVariable UUID organizationId,
            @Valid @RequestBody CreateMembershipAdminRequest request
    ) {
        accessGuard.require(authentication);
        MembershipAdminResponse created = directoryService.createMembership(
                tenantId, organizationId, request, authentication);
        return ResponseEntity.created(URI.create(
                "/api/v1/control-plane/tenants/" + tenantId + "/organizations/" + organizationId
                        + "/memberships/" + created.id())).body(created);
    }

    @PatchMapping("/tenants/{tenantId}/organizations/{organizationId}/memberships/{membershipId}")
    @RequireCapability("ROLE.WRITE")
    public ResponseEntity<MembershipAdminResponse> updateMembership(
            Authentication authentication,
            @PathVariable UUID tenantId,
            @PathVariable UUID organizationId,
            @PathVariable UUID membershipId,
            @Valid @RequestBody UpdateMembershipAdminRequest request
    ) {
        accessGuard.require(authentication);
        return ResponseEntity.ok(directoryService.updateMembership(
                tenantId, organizationId, membershipId, request, authentication));
    }
}