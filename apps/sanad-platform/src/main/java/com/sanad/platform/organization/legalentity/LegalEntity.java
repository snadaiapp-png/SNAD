package com.sanad.platform.organization.legalentity;

import java.time.Instant;
import java.util.UUID;

public record LegalEntity(
        UUID id,
        UUID tenantId,
        String code,
        String name,
        String registeredCountryCode,
        String statutoryCountryCode,
        LegalEntityStatus status,
        Instant createdAt,
        Instant updatedAt
) {
    public boolean isActive() {
        return status == LegalEntityStatus.ACTIVE;
    }
}
