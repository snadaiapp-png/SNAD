package com.sanad.platform.module.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * DTO for updating a plan-module entitlement.
 */
public record PlanModuleEntitlementRequest(
        @NotNull UUID moduleId,
        boolean moduleEnabled,
        String capabilityCode,        // nullable: if null, only toggles moduleEnabled
        String capabilityValue,       // string value for boolean/string types
        Long limitValue,              // for NUMERIC_LIMIT
        Long quotaValue,              // for QUOTA
        String quotaPeriod            // DAILY | MONTHLY | YEARLY | TOTAL
) {}
