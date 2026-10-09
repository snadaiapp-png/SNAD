package com.sanad.platform.organization.legalentity;

import com.sanad.platform.organization.repository.OrganizationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class LegalEntityService {

    private final LegalEntityRepository legalEntityRepository;
    private final LegalEntityOrganizationEligibilityRepository eligibilityRepository;
    private final OrganizationRepository organizationRepository;

    public LegalEntityService(
            LegalEntityRepository legalEntityRepository,
            LegalEntityOrganizationEligibilityRepository eligibilityRepository,
            OrganizationRepository organizationRepository) {
        this.legalEntityRepository = legalEntityRepository;
        this.eligibilityRepository = eligibilityRepository;
        this.organizationRepository = organizationRepository;
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

    /**
     * Resolve exactly one ACTIVE Legal Entity eligible for the Organization on the effective date.
     * The lookup is tenant-scoped and fails closed when the employer context is missing or ambiguous.
     */
    @Transactional
    public LegalEntity bootstrapSingleActiveForOrganization(
            UUID tenantId,
            UUID organizationId,
            LocalDate effectiveDate,
            String code,
            String name,
            String registeredCountryCode,
            String statutoryCountryCode) {
        organizationRepository.findByTenantIdAndId(tenantId, organizationId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Organization not found in authenticated tenant: " + organizationId));

        List<LegalEntityOrganizationEligibility> activeLinks =
                eligibilityRepository.findActiveForOrganizationOn(tenantId, organizationId, effectiveDate);
        if (activeLinks.size() > 1) {
            throw new IllegalStateException(
                    "Employer context must resolve to exactly one active legal entity; found " + activeLinks.size());
        }
        if (activeLinks.size() == 1) {
            return requireActive(tenantId, activeLinks.get(0).legalEntityId());
        }

        LegalEntity legalEntity = legalEntityRepository.findByTenantIdAndCode(tenantId, code)
                .map(existing -> {
                    if (!existing.isActive()
                            || !existing.name().equals(name)
                            || !existing.registeredCountryCode().equals(registeredCountryCode)
                            || !existing.statutoryCountryCode().equals(statutoryCountryCode)) {
                        throw new IllegalStateException(
                                "Existing legal entity code conflicts with governed bootstrap definition: " + code);
                    }
                    return existing;
                })
                .orElseGet(() -> legalEntityRepository.save(new LegalEntity(
                        null,
                        tenantId,
                        code,
                        name,
                        registeredCountryCode,
                        statutoryCountryCode,
                        LegalEntityStatus.ACTIVE,
                        null,
                        null)));

        eligibilityRepository.ensureActive(
                tenantId, organizationId, legalEntity.id(), effectiveDate);

        return resolveSingleActiveForOrganization(tenantId, organizationId, effectiveDate);
    }

    public LegalEntity resolveSingleActiveForOrganization(
            UUID tenantId, UUID organizationId, LocalDate effectiveDate) {
        List<LegalEntityOrganizationEligibility> links =
                eligibilityRepository.findActiveForOrganizationOn(tenantId, organizationId, effectiveDate);
        if (links.size() != 1) {
            throw new IllegalStateException(
                    "Employer context must resolve to exactly one active legal entity; found " + links.size());
        }
        return requireActive(tenantId, links.get(0).legalEntityId());
    }
}
