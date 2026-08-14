package com.sanad.platform.management.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Repository for {@link KpiDefinition} persistence. */
public interface KpiDefinitionRepository {

    KpiDefinition save(KpiDefinition definition);

    Optional<KpiDefinition> findById(UUID tenantId, UUID id);

    Optional<KpiDefinition> findByCode(UUID tenantId, String code);

    List<KpiDefinition> findByTenant(UUID tenantId, int limit);

    List<KpiDefinition> findByTenantAndStatus(UUID tenantId, KpiDefinition.Status status, int limit);

    List<KpiDefinition> findByTenantAndCategory(UUID tenantId, String category, int limit);

    void deleteById(UUID tenantId, UUID id);
}
