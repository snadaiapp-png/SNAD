package com.sanad.platform.admin.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Typed contracts for plans, subscriptions, billing, organizations, and memberships. */
public final class SaasAdminDtos {

    private SaasAdminDtos() {
    }

    public record EntitlementRequest(
            @NotBlank @Size(max = 100) @Pattern(regexp = "^[A-Za-z0-9_.-]+$") String featureCode,
            boolean enabled,
            @Min(0) Long limitValue
    ) {
    }

    public record EntitlementResponse(
            UUID id,
            String featureCode,
            boolean enabled,
            Long limitValue
    ) {
    }

    public record PlanResponse(
            UUID id,
            String code,
            String name,
            String description,
            String status,
            String currencyCode,
            long monthlyPriceMinor,
            long annualPriceMinor,
            int trialDays,
            int maxUsers,
            int maxOrganizations,
            long storageMb,
            List<EntitlementResponse> entitlements,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record CreatePlanRequest(
            @NotBlank @Size(max = 50) @Pattern(regexp = "^[A-Za-z0-9_-]+$") String code,
            @NotBlank @Size(max = 160) String name,
            @Size(max = 1000) String description,
            @NotBlank @Pattern(regexp = "^[A-Z]{3}$") String currencyCode,
            @Min(0) long monthlyPriceMinor,
            @Min(0) long annualPriceMinor,
            @Min(0) @Max(365) int trialDays,
            @Min(1) int maxUsers,
            @Min(1) int maxOrganizations,
            @Min(0) long storageMb,
            @Valid List<EntitlementRequest> entitlements
    ) {
    }

    public record UpdatePlanRequest(
            @NotBlank @Size(max = 160) String name,
            @Size(max = 1000) String description,
            @NotBlank @Pattern(regexp = "^[A-Z]{3}$") String currencyCode,
            @Min(0) long monthlyPriceMinor,
            @Min(0) long annualPriceMinor,
            @Min(0) @Max(365) int trialDays,
            @Min(1) int maxUsers,
            @Min(1) int maxOrganizations,
            @Min(0) long storageMb,
            @Valid List<EntitlementRequest> entitlements
    ) {
    }

    public record ChangeStatusRequest(
            @NotBlank @Size(max = 30) String status,
            @NotBlank @Size(max = 500) String reason
    ) {
    }

    public record SubscriptionResponse(
            UUID id,
            UUID tenantId,
            String tenantName,
            UUID planId,
            String planCode,
            String planName,
            UUID pendingPlanId,
            String pendingPlanCode,
            String status,
            String billingCycle,
            String pendingBillingCycle,
            int seatQuantity,
            long creditBalanceMinor,
            String currencyCode,
            Instant startedAt,
            Instant trialEndsAt,
            Instant currentPeriodStart,
            Instant currentPeriodEnd,
            boolean cancelAtPeriodEnd,
            Instant cancelledAt,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record CreateSubscriptionRequest(
            @NotNull UUID tenantId,
            @NotNull UUID planId,
            @NotBlank @Pattern(regexp = "^(MONTHLY|ANNUAL)$") String billingCycle,
            @Min(1) int seatQuantity,
            @Min(0) @Max(365) Integer trialDays
    ) {
    }

    public record ChangeSubscriptionPlanRequest(
            @NotNull UUID planId,
            @NotBlank @Pattern(regexp = "^(MONTHLY|ANNUAL)$") String billingCycle,
            @NotBlank @Pattern(regexp = "^(IMMEDIATE|NEXT_CYCLE)$") String effectiveMode,
            @NotBlank @Size(max = 500) String reason
    ) {
    }

    public record ChangeSeatsRequest(
            @Min(1) int seatQuantity,
            @NotBlank @Size(max = 500) String reason
    ) {
    }

    public record CancelSubscriptionRequest(
            boolean immediate,
            @NotBlank @Size(max = 500) String reason
    ) {
    }

    public record InvoiceResponse(
            UUID id,
            UUID tenantId,
            String tenantName,
            UUID subscriptionId,
            String invoiceNumber,
            String status,
            String currencyCode,
            long subtotalMinor,
            long creditAppliedMinor,
            long taxMinor,
            long totalMinor,
            long amountPaidMinor,
            String description,
            Instant periodStart,
            Instant periodEnd,
            Instant dueAt,
            Instant paidAt,
            String paymentReference,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record MarkInvoicePaidRequest(
            @NotBlank @Size(max = 200) String paymentReference,
            @NotBlank @Size(max = 500) String reason
    ) {
    }

    public record OrganizationAdminResponse(
            UUID id,
            UUID tenantId,
            String name,
            String description,
            String status,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record CreateOrganizationAdminRequest(
            @NotBlank @Size(max = 200) String name,
            @Size(max = 1000) String description
    ) {
    }

    public record UpdateOrganizationAdminRequest(
            @NotBlank @Size(max = 200) String name,
            @Size(max = 1000) String description
    ) {
    }

    public record MembershipAdminResponse(
            UUID id,
            UUID tenantId,
            UUID organizationId,
            UUID userId,
            String email,
            String displayName,
            String roleCode,
            String status,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record CreateMembershipAdminRequest(
            @NotBlank @Email @Size(max = 255) String email,
            @Size(max = 200) String displayName,
            @NotBlank @Size(max = 100) @Pattern(regexp = "^[A-Za-z0-9_.-]+$") String roleCode
    ) {
    }

    public record UpdateMembershipAdminRequest(
            @NotBlank @Pattern(regexp = "^(INVITED|ACTIVE|INACTIVE|REMOVED)$") String status,
            @NotBlank @Size(max = 100) @Pattern(regexp = "^[A-Za-z0-9_.-]+$") String roleCode,
            @NotBlank @Size(max = 500) String reason
    ) {
    }

    public record SubscriptionEventResponse(
            UUID id,
            UUID subscriptionId,
            String action,
            UUID oldPlanId,
            UUID newPlanId,
            String effectiveMode,
            long adjustmentMinor,
            String reason,
            Instant effectiveAt,
            Instant createdAt
    ) {
    }
}