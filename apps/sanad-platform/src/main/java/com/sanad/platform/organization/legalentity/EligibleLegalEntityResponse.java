package com.sanad.platform.organization.legalentity;

import java.util.UUID;

/**
 * Minimal read model for the Organization-scoped Legal Entity eligibility API.
 * Only non-sensitive identity and jurisdiction metadata required by governed
 * provisioning is exposed; lifecycle filtering remains server-side.
 */
public record EligibleLegalEntityResponse(
        UUID id,
        String code,
        String name,
        String registeredCountryCode,
        String statutoryCountryCode
) {
    public static EligibleLegalEntityResponse from(LegalEntity entity) {
        return new EligibleLegalEntityResponse(
                entity.id(),
                entity.code(),
                entity.name(),
                entity.registeredCountryCode(),
                entity.statutoryCountryCode());
    }
}
