package com.sanad.platform.management.application;

import com.sanad.platform.management.domain.ExecutiveAlert;
import com.sanad.platform.management.domain.KpiDefinition;
import com.sanad.platform.management.domain.KpiDefinitionRepository;
import com.sanad.platform.management.domain.KpiMeasurement;
import com.sanad.platform.management.domain.KpiMeasurementRepository;
import com.sanad.platform.management.domain.KpiTarget;
import com.sanad.platform.management.domain.KpiTargetRepository;
import com.sanad.platform.management.domain.KeyResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Application service for KPI management — definitions, targets, and measurements.
 *
 * <p>This is the heart of the Senior Management KPI engine. It coordinates:
 * <ul>
 *   <li>KPI Definition lifecycle (create, activate, deactivate, deprecate)</li>
 *   <li>KPI Target setting per period</li>
 *   <li>KPI Measurement recording (append-only, with status computation)</li>
 * </ul>
 */
@Service
public class KpiService {

    private final KpiDefinitionRepository definitionRepo;
    private final KpiTargetRepository targetRepo;
    private final KpiMeasurementRepository measurementRepo;
    private final ExecutiveAlertService alertService;

    public KpiService(
            KpiDefinitionRepository definitionRepo,
            KpiTargetRepository targetRepo,
            KpiMeasurementRepository measurementRepo,
            ExecutiveAlertService alertService) {
        this.definitionRepo = definitionRepo;
        this.targetRepo = targetRepo;
        this.measurementRepo = measurementRepo;
        this.alertService = alertService;
    }

    // ===== KPI Definitions =====

    @Transactional
    public KpiDefinition createDefinition(KpiDefinition definition) {
        return definitionRepo.save(definition);
    }

    @Transactional(readOnly = true)
    public Optional<KpiDefinition> findDefinitionById(UUID tenantId, UUID id) {
        return definitionRepo.findById(tenantId, id);
    }

    @Transactional(readOnly = true)
    public List<KpiDefinition> findDefinitionsByTenant(UUID tenantId, int limit) {
        return definitionRepo.findByTenant(tenantId, limit);
    }

    @Transactional(readOnly = true)
    public List<KpiDefinition> findActiveDefinitions(UUID tenantId, int limit) {
        return definitionRepo.findByTenantAndStatus(tenantId, KpiDefinition.Status.ACTIVE, limit);
    }

    @Transactional
    public KpiDefinition deactivateDefinition(UUID tenantId, UUID id) {
        var def = definitionRepo.findById(tenantId, id)
                .orElseThrow(() -> new IllegalArgumentException("KPI Definition not found: " + id));
        return definitionRepo.save(def.deactivate());
    }

    @Transactional
    public KpiDefinition deprecateDefinition(UUID tenantId, UUID id) {
        var def = definitionRepo.findById(tenantId, id)
                .orElseThrow(() -> new IllegalArgumentException("KPI Definition not found: " + id));
        return definitionRepo.save(def.deprecate());
    }

    // ===== KPI Targets =====

    @Transactional
    public KpiTarget createTarget(KpiTarget target) {
        return targetRepo.save(target);
    }

    @Transactional(readOnly = true)
    public List<KpiTarget> findTargetsByDefinition(UUID tenantId, UUID definitionId) {
        return targetRepo.findByKpiDefinition(tenantId, definitionId);
    }

    @Transactional(readOnly = true)
    public Optional<KpiTarget> findActiveTargetForDate(UUID definitionId, LocalDate asOf) {
        return targetRepo.findActiveForDate(definitionId, asOf);
    }

    @Transactional
    public KpiTarget closeTarget(UUID tenantId, UUID targetId) {
        // Note: KpiTarget doesn't have findById in the repo interface, so we query by definition
        // In practice, the controller would pass the definition_id too. For now, we iterate.
        // This is a known limitation — a real implementation would add findById to the repo.
        throw new UnsupportedOperationException("Use the domain object directly — see controller");
    }

    // ===== KPI Measurements =====

    /**
     * Record a new KPI measurement.
     *
     * <p>This is the core measurement-recording method. It:
     * <ol>
     *   <li>Loads the KPI definition (to get direction)</li>
     *   <li>Loads the active target for the measurement period (if any)</li>
     *   <li>Loads the previous measurement (for delta calculation)</li>
     *   <li>Computes the status using {@link KpiMeasurement#computeStatus}</li>
     *   <li>Computes the variance percentage</li>
     *   <li>Persists the immutable measurement record</li>
     * </ol>
     *
     * @param tenantId the tenant scope
     * @param kpiDefinitionId the KPI definition ID
     * @param period the measurement period
     * @param measuredValue the actual measured value
     * @param evidence human-readable source reference (e.g., "CRM export 2026-08-14")
     * @param measuredBy the user ID who recorded the measurement
     * @return the persisted measurement
     */
    @Transactional
    public KpiMeasurement recordMeasurement(
            UUID tenantId, UUID kpiDefinitionId, LocalDate period,
            BigDecimal measuredValue, String evidence, UUID measuredBy) {
        var definition = definitionRepo.findById(tenantId, kpiDefinitionId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "KPI Definition not found: " + kpiDefinitionId));

        var activeTarget = targetRepo.findActiveForDate(kpiDefinitionId, period);
        var previous = measurementRepo.findLatest(kpiDefinitionId);

        var targetValue = activeTarget.map(KpiTarget::targetValue).orElse(null);
        var minimumValue = activeTarget.map(KpiTarget::minimumValue).orElse(null);
        var stretchValue = activeTarget.map(KpiTarget::stretchValue).orElse(null);
        var previousValue = previous.map(KpiMeasurement::measuredValue).orElse(null);

        var status = KpiMeasurement.computeStatus(
                measuredValue, targetValue, minimumValue, stretchValue, definition.direction());
        var variancePct = KpiMeasurement.computeVariancePct(measuredValue, targetValue);

        var measurement = new KpiMeasurement(
                UUID.randomUUID(), tenantId, kpiDefinitionId,
                activeTarget.map(KpiTarget::id).orElse(null),
                period, measuredValue, previousValue, variancePct,
                status, evidence, measuredBy, Instant.now()
        );
        var saved = measurementRepo.save(measurement);

        // Cross-domain workflow: KPI OFF_TRACK → Executive Alert
        if (status == KpiMeasurement.Status.OFF_TRACK) {
            alertService.createOrGetExisting(
                    tenantId,
                    ExecutiveAlert.AlertType.KPI_OFF_TRACK,
                    ExecutiveAlert.Severity.HIGH,
                    ExecutiveAlert.SourceEntityType.KPI,
                    kpiDefinitionId,
                    "KPI Off Track: " + definition.name(),
                    "KPI '" + definition.name() + "' is OFF_TRACK. "
                            + "Measured: " + measuredValue + ", Target: " + targetValue
                            + ", Variance: " + (variancePct != null ? variancePct + "%" : "N/A"),
                    measuredBy
            );
        }

        return saved;
    }

    @Transactional(readOnly = true)
    public Optional<KpiMeasurement> findLatestMeasurement(UUID kpiDefinitionId) {
        return measurementRepo.findLatest(kpiDefinitionId);
    }

    @Transactional(readOnly = true)
    public List<KpiMeasurement> findMeasurementHistory(UUID kpiDefinitionId, int limit) {
        return measurementRepo.findByKpiDefinition(kpiDefinitionId, limit);
    }

    /**
     * Find the latest measurements for a list of KPI definitions.
     * Used by the executive dashboard to aggregate current KPI health.
     */
    @Transactional(readOnly = true)
    public List<KpiMeasurement> findLatestMeasurementsForDashboard(List<UUID> kpiDefinitionIds) {
        return measurementRepo.findLatestForDefinitions(kpiDefinitionIds);
    }
}
