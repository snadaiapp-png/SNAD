package com.sanad.platform.organization.legalentity;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LegalEntityOrganizationEligibilityRepository {

    boolean isEligibleOn(UUID tenantId, UUID legalEntityId, UUID organizationId, LocalDate effectiveDate);

    Optional<LegalEntityOrganizationEligibility> findActiveOn(UUID tenantId, UUID legalEntityId, UUID organizationId, LocalDate effectiveDate);

    List<LegalEntityOrganizationEligibility> findActiveForOrganizationOn(
            UUID tenantId, UUID organizationId, LocalDate effectiveDate);

    LegalEntityOrganizationEligibility ensureActive(
            UUID tenantId, UUID organizationId, UUID legalEntityId, LocalDate effectiveFrom);
}
