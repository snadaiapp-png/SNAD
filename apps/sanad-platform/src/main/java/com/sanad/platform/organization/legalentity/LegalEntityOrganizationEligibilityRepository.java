package com.sanad.platform.organization.legalentity;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface LegalEntityOrganizationEligibilityRepository {

    boolean isEligibleOn(UUID tenantId, UUID legalEntityId, UUID organizationId, LocalDate effectiveDate);

    Optional<LegalEntityOrganizationEligibility> findActiveOn(UUID tenantId, UUID legalEntityId, UUID organizationId, LocalDate effectiveDate);
}
