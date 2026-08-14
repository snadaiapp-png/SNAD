package com.sanad.platform.management.dto;

import com.sanad.platform.management.domain.KeyResult;
import com.sanad.platform.management.domain.KpiDefinition;
import com.sanad.platform.management.domain.KpiMeasurement;
import com.sanad.platform.management.domain.KpiTarget;
import com.sanad.platform.management.domain.StrategicInitiative;
import com.sanad.platform.management.domain.StrategicObjective;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Response DTOs for the Senior Management API.
 *
 * <p>These are the wire-format records returned by controllers. They flatten
 * the domain model into API-friendly shapes (e.g., using String for status
 * instead of enum for forward compatibility).
 */
public final class ManagementResponses {

    private ManagementResponses() {}

    public record ObjectiveResponse(
            UUID id, UUID tenantId, UUID parentId, String code, String title,
            String description, String status, String priority, UUID ownerUserId,
            LocalDate periodStart, LocalDate periodEnd, int progressPct,
            long version, Instant createdAt, Instant updatedAt,
            List<KeyResultResponse> keyResults
    ) {
        public static ObjectiveResponse from(StrategicObjective o, List<KeyResultResponse> krs) {
            return new ObjectiveResponse(
                    o.id(), o.tenantId(), o.parentId(), o.code(), o.title(), o.description(),
                    o.status().name(), o.priority().name(), o.ownerUserId(),
                    o.periodStart(), o.periodEnd(), o.progressPct(),
                    o.version(), o.createdAt(), o.updatedAt(), krs
            );
        }
    }

    public record KeyResultResponse(
            UUID id, UUID objectiveId, String title, String description,
            String metricUnit, BigDecimal baselineValue, BigDecimal targetValue,
            BigDecimal currentValue, String direction, String status,
            int weightPct, UUID ownerUserId, LocalDate dueDate,
            long version, Instant createdAt, Instant updatedAt
    ) {
        public static KeyResultResponse from(KeyResult kr) {
            return new KeyResultResponse(
                    kr.id(), kr.objectiveId(), kr.title(), kr.description(),
                    kr.metricUnit().name(), kr.baselineValue(), kr.targetValue(),
                    kr.currentValue(), kr.direction().name(), kr.status().name(),
                    kr.weightPct(), kr.ownerUserId(), kr.dueDate(),
                    kr.version(), kr.createdAt(), kr.updatedAt()
            );
        }
    }

    public record KpiDefinitionResponse(
            UUID id, UUID tenantId, String code, String name, String description,
            String category, String metricUnit, String direction, String formula,
            String sourceSystem, String status, UUID ownerUserId,
            long version, Instant createdAt, Instant updatedAt
    ) {
        public static KpiDefinitionResponse from(KpiDefinition d) {
            return new KpiDefinitionResponse(
                    d.id(), d.tenantId(), d.code(), d.name(), d.description(),
                    d.category(), d.metricUnit().name(), d.direction().name(),
                    d.formula(), d.sourceSystem(), d.status().name(), d.ownerUserId(),
                    d.version(), d.createdAt(), d.updatedAt()
            );
        }
    }

    public record KpiTargetResponse(
            UUID id, UUID kpiDefinitionId, LocalDate periodStart, LocalDate periodEnd,
            BigDecimal targetValue, BigDecimal minimumValue, BigDecimal stretchValue,
            UUID ownerUserId, String status, long version, Instant createdAt, Instant updatedAt
    ) {
        public static KpiTargetResponse from(KpiTarget t) {
            return new KpiTargetResponse(
                    t.id(), t.kpiDefinitionId(), t.periodStart(), t.periodEnd(),
                    t.targetValue(), t.minimumValue(), t.stretchValue(),
                    t.ownerUserId(), t.status().name(), t.version(), t.createdAt(), t.updatedAt()
            );
        }
    }

    public record KpiMeasurementResponse(
            UUID id, UUID kpiDefinitionId, UUID kpiTargetId, LocalDate period,
            BigDecimal measuredValue, BigDecimal previousValue, BigDecimal variancePct,
            String status, String evidence, UUID measuredBy, Instant measuredAt
    ) {
        public static KpiMeasurementResponse from(KpiMeasurement m) {
            return new KpiMeasurementResponse(
                    m.id(), m.kpiDefinitionId(), m.kpiTargetId(), m.period(),
                    m.measuredValue(), m.previousValue(), m.variancePct(),
                    m.status().name(), m.evidence(), m.measuredBy(), m.measuredAt()
            );
        }
    }

    public record InitiativeResponse(
            UUID id, UUID objectiveId, String code, String name, String description,
            String status, UUID ownerUserId, LocalDate startDate, LocalDate targetEndDate,
            LocalDate actualEndDate, int progressPct, Long budgetMinor, long spentMinor,
            long version, Instant createdAt, Instant updatedAt
    ) {
        public static InitiativeResponse from(StrategicInitiative i) {
            return new InitiativeResponse(
                    i.id(), i.objectiveId(), i.code(), i.name(), i.description(),
                    i.status().name(), i.ownerUserId(), i.startDate(), i.targetEndDate(),
                    i.actualEndDate(), i.progressPct(), i.budgetMinor(), i.spentMinor(),
                    i.version(), i.createdAt(), i.updatedAt()
            );
        }
    }

    /**
     * Executive Dashboard response — aggregates KPI health, active objectives,
     * and critical items into a single response for the executive landing page.
     */
    public record ExecutiveDashboardResponse(
            int totalObjectives,
            int activeObjectives,
            int atRiskObjectives,
            int offTrackObjectives,
            int achievedObjectives,
            int totalKeyResults,
            int krsAchieved,
            int krsAtRisk,
            int krsOffTrack,
            int totalKpis,
            int kpisOnTrack,
            int kpisAtRisk,
            int kpisOffTrack,
            int kpisNoData,
            int totalInitiatives,
            int initiativesInProgress,
            int initiativesOnHold,
            int initiativesCompleted,
            List<ObjectiveSummary> topObjectives,
            List<KpiHealthSummary> kpiHealth,
            Instant generatedAt
    ) {
        public record ObjectiveSummary(
                UUID id, String code, String title, String status,
                String priority, int progressPct, UUID ownerUserId
        ) {}

        public record KpiHealthSummary(
                UUID kpiDefinitionId, String code, String name, String category,
                String status, BigDecimal measuredValue, BigDecimal targetValue,
                BigDecimal variancePct, LocalDate period
        ) {}
    }
}
