package com.sanad.platform.organization.worklocation;

import java.util.Optional;
import java.util.UUID;

public interface WorkLocationRepository {

    Optional<WorkLocation> findByTenantIdAndId(UUID tenantId, UUID id);

    Optional<WorkLocation> findByTenantIdAndCode(UUID tenantId, String code);

    WorkLocation save(WorkLocation entity);
}
