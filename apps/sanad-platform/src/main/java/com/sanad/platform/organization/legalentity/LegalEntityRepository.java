package com.sanad.platform.organization.legalentity;

import java.util.Optional;
import java.util.UUID;

public interface LegalEntityRepository {

    Optional<LegalEntity> findByTenantIdAndId(UUID tenantId, UUID id);

    Optional<LegalEntity> findByTenantIdAndCode(UUID tenantId, String code);

    LegalEntity save(LegalEntity entity);
}
