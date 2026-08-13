package com.sanad.platform.module.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO for a plan-module entitlement.
 */
public record PlanModuleEntitlementResponse(
        UUID id,
        UUID planId,
        UUID moduleId,
        String moduleCode,
        boolean moduleEnabled,
        String capabilityCode,
        String capabilityValue,
        Long limitValue,
        Long quotaValue,
        String quotaPeriod,
        Instant effectiveAt,
        Instant createdAt,
        Instant updatedAt
) {}
