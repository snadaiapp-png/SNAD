package com.sanad.platform.organization.legalentity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record LegalEntityOrganizationEligibility(
        UUID id,
        UUID tenantId,
        UUID organizationId,
        UUID legalEntityId,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        String status,
        Instant createdAt
) {
    public boolean isActive() {
        return "ACTIVE".equals(status);
    }

    public boolean isEffectiveOn(LocalDate date) {
        return effectiveFrom.compareTo(date) <= 0
                && (effectiveTo == null || effectiveTo.compareTo(date) >= 0);
    }
}
