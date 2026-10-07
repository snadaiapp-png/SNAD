package com.sanad.platform.organization.legalentity;

import com.sanad.platform.organization.domain.Organization;
import com.sanad.platform.organization.domain.OrganizationStatus;
import com.sanad.platform.organization.repository.OrganizationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Governed bootstrap for legacy tenants that have a single ACTIVE Organization
 * and a single ACTIVE Legal Entity but no canonical organization-to-employer link yet.
 *
 * <p>The operation is intentionally fail-closed: it never guesses when more than
 * one ACTIVE Legal Entity is available, never crosses tenant boundaries, and is
 * idempotent when an effective link already exists.</p>
 */
@Service
public class EmployerContextBootstrapService {

    private final OrganizationRepository organizationRepository;
    private final LegalEntityRepository legalEntityRepository;
    private final LegalEntityOrganizationEligibilityRepository eligibilityRepository;

    public EmployerContextBootstrapService(
            OrganizationRepository organizationRepository,
            LegalEntityRepository legalEntityRepository,
            LegalEntityOrganizationEligibilityRepository eligibilityRepository) {
        this.organizationRepository = Objects.requireNonNull(organizationRepository, "organizationRepository");
        this.legalEntityRepository = Objects.requireNonNull(legalEntityRepository, "legalEntityRepository");
        this.eligibilityRepository = Objects.requireNonNull(eligibilityRepository, "eligibilityRepository");
    }

    @Transactional
    public BootstrapResult ensureSingleActiveEmployerContext(
            UUID tenantId,
            UUID organizationId,
            LocalDate effectiveDate) {

        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(organizationId, "organizationId");
        Objects.requireNonNull(effectiveDate, "effectiveDate");

        Organization organization = organizationRepository.findByTenantIdAndId(tenantId, organizationId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Organization not found in authenticated tenant: " + organizationId));
        if (organization.getStatus() != OrganizationStatus.ACTIVE) {
            throw new IllegalStateException("Organization is not ACTIVE: " + organizationId);
        }

        List<UUID> linkedLegalEntities = eligibilityRepository
                .findActiveForOrganizationOn(tenantId, organizationId, effectiveDate)
                .stream()
                .map(LegalEntityOrganizationEligibility::legalEntityId)
                .distinct()
                .toList();

        if (linkedLegalEntities.size() > 1) {
            throw new IllegalStateException(
                    "Employer context is ambiguous: organization has "
                            + linkedLegalEntities.size()
                            + " active legal entities on "
                            + effectiveDate);
        }

        if (linkedLegalEntities.size() == 1) {
            LegalEntity legalEntity = requireActive(tenantId, linkedLegalEntities.get(0));
            return new BootstrapResult(legalEntity, false);
        }

        List<LegalEntity> activeLegalEntities = legalEntityRepository.findActiveByTenantId(tenantId);
        if (activeLegalEntities.size() != 1) {
            throw new IllegalStateException(
                    "Employer context bootstrap requires exactly one ACTIVE legal entity in tenant; found "
                            + activeLegalEntities.size());
        }

        LegalEntity legalEntity = activeLegalEntities.get(0);
        eligibilityRepository.createActive(
                tenantId,
                organizationId,
                legalEntity.id(),
                effectiveDate);

        // Re-read through the canonical resolver after the write. This proves the
        // persisted link is effective and keeps the same fail-closed semantics used by HR.
        List<UUID> verified = eligibilityRepository
                .findActiveForOrganizationOn(tenantId, organizationId, effectiveDate)
                .stream()
                .map(LegalEntityOrganizationEligibility::legalEntityId)
                .distinct()
                .toList();

        if (verified.size() != 1 || !verified.get(0).equals(legalEntity.id())) {
            throw new IllegalStateException("Employer context bootstrap verification failed");
        }

        return new BootstrapResult(legalEntity, true);
    }

    private LegalEntity requireActive(UUID tenantId, UUID legalEntityId) {
        LegalEntity legalEntity = legalEntityRepository.findByTenantIdAndId(tenantId, legalEntityId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Legal entity not found in authenticated tenant: " + legalEntityId));
        if (!legalEntity.isActive()) {
            throw new IllegalStateException("Legal entity is not ACTIVE: " + legalEntityId);
        }
        return legalEntity;
    }

    public record BootstrapResult(LegalEntity legalEntity, boolean bootstrapped) {}
}
