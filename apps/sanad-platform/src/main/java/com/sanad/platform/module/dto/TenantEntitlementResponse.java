package com.sanad.platform.module.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO for the effective entitlement of a tenant for a specific module.
 */
public record TenantEntitlementResponse(
        UUID tenantId,
        String moduleCode,
        boolean moduleEnabled,
        String subscriptionId,
        String planId,
        java.util.Map<String, Boolean> capabilities,
        java.util.Map<String, Long> limits,
        java.util.Map<String, QuotaResponse> quotas,
        Instant effectiveAt
) {
    public record QuotaResponse(long value, String period) {}
}
