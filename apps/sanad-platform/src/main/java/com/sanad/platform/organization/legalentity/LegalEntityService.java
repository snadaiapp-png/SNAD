package com.sanad.platform.organization.legalentity;

import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.UUID;

@Service
public class LegalEntityService {

    private final LegalEntityRepository legalEntityRepository;
    private final LegalEntityOrganizationEligibilityRepository eligibilityRepository;

    public LegalEntityService(
            LegalEntityRepository legalEntityRepository,
            LegalEntityOrganizationEligibilityRepository eligibilityRepository) {
        this.legalEntityRepository = legalEntityRepository;
        this.eligibilityRepository = eligibilityRepository;
    }

    /**
     * Requires the Legal Entity to exist and be ACTIVE for the given tenant.
     *
     * @throws IllegalArgumentException if not found or inactive
     */
    public LegalEntity requireActive(UUID tenantId, UUID legalEntityId) {
        LegalEntity le = legalEntityRepository.findByTenantIdAndId(tenantId, legalEntityId)
                .orElseThrow(() -> new IllegalArgumentException("Legal entity not found: " + legalEntityId));
        if (!le.isActive()) {
            throw new IllegalArgumentException("Legal entity is not active: " + legalEntityId);
        }
        return le;
    }

    /**
     * Requires the given Legal Entity to be eligible for the given Organization on the effective date.
     *
     * @throws IllegalArgumentException if not eligible
     */
    public void requireOrganizationEligibility(UUID tenantId, UUID legalEntityId, UUID organizationId, LocalDate effectiveDate) {
        if (!eligibilityRepository.isEligibleOn(tenantId, legalEntityId, organizationId, effectiveDate)) {
            throw new IllegalArgumentException(
                    "Legal entity " + legalEntityId + " is not eligible for organization " + organizationId + " on " + effectiveDate);
        }
    }

    public boolean isOrganizationEligible(UUID tenantId, UUID legalEntityId, UUID organizationId, LocalDate effectiveDate) {
        return eligibilityRepository.isEligibleOn(tenantId, legalEntityId, organizationId, effectiveDate);
    }
}
