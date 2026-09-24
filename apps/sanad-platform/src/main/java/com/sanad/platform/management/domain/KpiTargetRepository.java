package com.sanad.platform.management.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Repository for {@link KpiTarget} persistence. */
public interface KpiTargetRepository {

    KpiTarget save(KpiTarget target);

    Optional<KpiTarget> findById(UUID tenantId, UUID id);

    /** Find the active target for a KPI definition that covers the given date. */
    Optional<KpiTarget> findActiveForDate(UUID kpiDefinitionId, java.time.LocalDate asOf);

    List<KpiTarget> findByKpiDefinition(UUID tenantId, UUID kpiDefinitionId);

    List<KpiTarget> findByTenant(UUID tenantId, int limit);

    List<KpiTarget> findActiveByTenant(UUID tenantId, int limit);
}
