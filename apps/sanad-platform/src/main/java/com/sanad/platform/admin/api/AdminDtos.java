package com.sanad.platform.admin.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Typed request and response contracts for the SNAD control plane. */
public final class AdminDtos {

    private AdminDtos() {
    }

    public record DashboardResponse(
            long totalTenants,
            long activeTenants,
            long trialTenants,
            long suspendedTenants,
            long totalUsers,
            long activeUsers,
            long operationalServices,
            long degradedServices,
            List<AuditEntryResponse> recentActivity
    ) {
    }

    public record TenantResponse(
            UUID id,
            String name,
            String legalName,
            String subdomain,
            String status,
            String billingEmail,
            String countryCode,
            String locale,
            String timezone,
            String currencyCode,
            Instant trialEndsAt,
            String suspensionReason,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record CreateTenantRequest(
            @NotBlank @Size(max = 200) String name,
            @Size(max = 250) String legalName,
            @NotBlank @Size(max = 63)
            @Pattern(regexp = "^[a-z0-9](?:[a-z0-9-]{1,61}[a-z0-9])?$") String subdomain,
            @Email @Size(max = 255) String billingEmail,
            @NotBlank @Email @Size(max = 255) String adminEmail,
            @NotBlank @Size(max = 200) String adminDisplayName,
            @Pattern(regexp = "^[A-Z]{2}$") String countryCode,
            @Size(max = 10) String locale,
            @Size(max = 64) String timezone,
            @Pattern(regexp = "^[A-Z]{3}$") String currencyCode,
            Integer trialDays
    ) {
    }

    public record ChangeTenantStatusRequest(
            @NotNull String status,
            @NotBlank @Size(max = 500) String reason
    ) {
    }

    public record SystemServiceResponse(
            UUID id,
            String code,
            String name,
            String description,
            String version,
            String environment,
            String status,
            String healthUrl,
            String ownerName,
            String criticality,
            String dependencies,
            Instant lastCheckedAt,
            Long lastLatencyMs,
            String lastMessage,
            Instant updatedAt
    ) {
    }

    public record UpdateSystemStatusRequest(
            @NotBlank String status,
            @NotBlank @Size(max = 500) String reason,
            Long latencyMs,
            @Size(max = 500) String message
    ) {
    }

    public record AuditEntryResponse(
            UUID id,
            UUID actorTenantId,
            UUID actorUserId,
            UUID targetTenantId,
            String action,
            String resourceType,
            String resourceId,
            String reason,
            String result,
            String correlationId,
            Instant createdAt
    ) {
    }
}
