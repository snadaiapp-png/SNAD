package com.sanad.platform.management.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link KpiMeasurement} persistence.
 *
 * <p>Measurements are append-only — there is no update() or delete() method.
 * This is intentional: historical measurements are immutable audit records.
 */
public interface KpiMeasurementRepository {

    /** Insert a new measurement (append-only). */
    KpiMeasurement save(KpiMeasurement measurement);

    Optional<KpiMeasurement> findById(UUID tenantId, UUID id);

    /** Find the most recent measurement for a KPI definition. */
    Optional<KpiMeasurement> findLatest(UUID kpiDefinitionId);

    /** Find the measurement for a specific period (one per period per KPI). */
    Optional<KpiMeasurement> findByPeriod(UUID kpiDefinitionId, java.time.LocalDate period);

    /** Find all measurements for a KPI definition, ordered by period descending. */
    List<KpiMeasurement> findByKpiDefinition(UUID kpiDefinitionId, int limit);

    /** Find measurements for multiple KPI definitions — used by dashboard aggregation. */
    List<KpiMeasurement> findLatestForDefinitions(List<UUID> kpiDefinitionIds);

    /** Count measurements for a KPI definition — used for trend analysis. */
    long countByKpiDefinition(UUID kpiDefinitionId);
}
