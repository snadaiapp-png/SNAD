package com.sanad.platform.subscription.branch;

import com.sanad.platform.security.authorization.ControlPlaneAccessGuard;
import com.sanad.platform.security.authorization.RequireCapability;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Executive API for branch / operating-unit subscription governance. */
@RestController
@RequestMapping("/api/v1/executive/subscriptions/{subscriptionId}")
public class SubscriptionOperatingUnitController {

    public record BindUnitRequest(String billingMode) {}

    public record UnitApplicationRequest(boolean enabled) {}

    public record BillingProfileRequest(
            UUID organizationId,
            @NotBlank String profileName,
            String billingEmail,
            @NotBlank String currencyCode,
            String billingMode
    ) {}

    public record ResourceBindingRequest(
            @NotNull UUID organizationId
    ) {}

    private final ControlPlaneAccessGuard accessGuard;
    private final SubscriptionOperatingUnitService service;

    public SubscriptionOperatingUnitController(
            ControlPlaneAccessGuard accessGuard,
            SubscriptionOperatingUnitService service
    ) {
        this.accessGuard = accessGuard;
        this.service = service;
    }

    @GetMapping("/operating-units")
    @RequireCapability("EXECUTIVE_VIEW")
    public ResponseEntity<List<SubscriptionOperatingUnitService.OperatingUnit>> list(
            @PathVariable UUID subscriptionId,
            Authentication authentication
    ) {
        accessGuard.require(authentication);
        return ResponseEntity.ok(service.list(subscriptionId));
    }

    @GetMapping("/operating-units/{organizationId}/applications")
    @RequireCapability("EXECUTIVE_VIEW")
    public ResponseEntity<List<SubscriptionOperatingUnitService.UnitApplication>> listApplications(
            @PathVariable UUID subscriptionId,
            @PathVariable UUID organizationId,
            Authentication authentication
    ) {
        accessGuard.require(authentication);
        return ResponseEntity.ok(service.listApplications(subscriptionId, organizationId));
    }

    @GetMapping("/billing-profiles")
    @RequireCapability("EXECUTIVE_VIEW")
    public ResponseEntity<List<SubscriptionOperatingUnitService.BillingProfile>> listBillingProfiles(
            @PathVariable UUID subscriptionId,
            Authentication authentication
    ) {
        accessGuard.require(authentication);
        return ResponseEntity.ok(service.listBillingProfiles(subscriptionId));
    }

    @GetMapping("/resource-bindings")
    @RequireCapability("EXECUTIVE_VIEW")
    public ResponseEntity<List<SubscriptionOperatingUnitService.ResourceBinding>> listResourceBindings(
            @PathVariable UUID subscriptionId,
            Authentication authentication
    ) {
        accessGuard.require(authentication);
        return ResponseEntity.ok(service.listResourceBindings(subscriptionId));
    }

    @GetMapping("/available-resources")
    @RequireCapability("EXECUTIVE_VIEW")
    public ResponseEntity<List<SubscriptionOperatingUnitService.AvailableResource>> listAvailableResources(
            @PathVariable UUID subscriptionId,
            Authentication authentication
    ) {
        accessGuard.require(authentication);
        return ResponseEntity.ok(service.listAvailableResources(subscriptionId));
    }

    @PutMapping("/operating-units/{organizationId}")
    @RequireCapability("EXECUTIVE_MANAGE")
    public ResponseEntity<SubscriptionOperatingUnitService.OperatingUnit> bind(
            @PathVariable UUID subscriptionId,
            @PathVariable UUID organizationId,
            @Valid @RequestBody BindUnitRequest request,
            Authentication authentication
    ) {
        accessGuard.require(authentication);
        return ResponseEntity.ok(service.bind(
                subscriptionId, organizationId, request.billingMode(), authentication));
    }

    @DeleteMapping("/operating-units/{organizationId}")
    @RequireCapability("EXECUTIVE_MANAGE")
    public ResponseEntity<Void> deactivate(
            @PathVariable UUID subscriptionId,
            @PathVariable UUID organizationId,
            Authentication authentication
    ) {
        accessGuard.require(authentication);
        service.deactivate(subscriptionId, organizationId, authentication);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/operating-units/{organizationId}/applications/{applicationId}")
    @RequireCapability("EXECUTIVE_MANAGE")
    public ResponseEntity<Void> setApplication(
            @PathVariable UUID subscriptionId,
            @PathVariable UUID organizationId,
            @PathVariable UUID applicationId,
            @Valid @RequestBody UnitApplicationRequest request,
            Authentication authentication
    ) {
        accessGuard.require(authentication);
        service.setApplication(
                subscriptionId, organizationId, applicationId, request.enabled(), authentication);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/billing-profile")
    @RequireCapability("EXECUTIVE_MANAGE")
    public ResponseEntity<SubscriptionOperatingUnitService.BillingProfile> billingProfile(
            @PathVariable UUID subscriptionId,
            @Valid @RequestBody BillingProfileRequest request,
            Authentication authentication
    ) {
        accessGuard.require(authentication);
        return ResponseEntity.ok(service.upsertBillingProfile(
                subscriptionId,
                request.organizationId(),
                request.profileName(),
                request.billingEmail(),
                request.currencyCode(),
                request.billingMode(),
                authentication));
    }

    @PutMapping("/resources/{resourceType}/{resourceId}")
    @RequireCapability("EXECUTIVE_MANAGE")
    public ResponseEntity<Void> bindResource(
            @PathVariable UUID subscriptionId,
            @PathVariable String resourceType,
            @PathVariable UUID resourceId,
            @Valid @RequestBody ResourceBindingRequest request,
            Authentication authentication
    ) {
        accessGuard.require(authentication);
        service.bindResource(
                subscriptionId,
                request.organizationId(),
                resourceType,
                resourceId,
                authentication);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/resources/{resourceType}/{resourceId}")
    @RequireCapability("EXECUTIVE_MANAGE")
    public ResponseEntity<Void> unbindResource(
            @PathVariable UUID subscriptionId,
            @PathVariable String resourceType,
            @PathVariable UUID resourceId,
            Authentication authentication
    ) {
        accessGuard.require(authentication);
        service.unbindResource(subscriptionId, resourceType, resourceId, authentication);
        return ResponseEntity.noContent().build();
    }
}
