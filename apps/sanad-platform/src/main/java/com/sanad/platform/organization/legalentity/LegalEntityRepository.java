package com.sanad.platform.organization.legalentity;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LegalEntityRepository {

    Optional<LegalEntity> findByTenantIdAndId(UUID tenantId, UUID id);

    Optional<LegalEntity> findByTenantIdAndCode(UUID tenantId, String code);

    List<LegalEntity> findActiveEligibleForOrganization(
            UUID tenantId,
            UUID organizationId,
            LocalDate effectiveDate);

    LegalEntity save(LegalEntity entity);
}
