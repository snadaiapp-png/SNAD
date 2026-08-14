package com.sanad.platform.management.api;

import com.sanad.platform.management.application.KeyResultService;
import com.sanad.platform.management.application.KpiService;
import com.sanad.platform.management.application.StrategicInitiativeService;
import com.sanad.platform.management.application.StrategicObjectiveService;
import com.sanad.platform.management.domain.KeyResult;
import com.sanad.platform.management.domain.KpiDefinition;
import com.sanad.platform.management.domain.KpiMeasurement;
import com.sanad.platform.management.domain.KpiTarget;
import com.sanad.platform.management.domain.StrategicInitiative;
import com.sanad.platform.management.domain.StrategicObjective;
import com.sanad.platform.management.dto.ManagementRequests;
import com.sanad.platform.management.dto.ManagementResponses;
import com.sanad.platform.security.authorization.RequireCapability;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.sanad.platform.security.SecurityContextUtils.tenantId;
import static com.sanad.platform.security.SecurityContextUtils.userId;

/**
 * Senior Management API — Strategic Objectives, Key Results, KPIs, and Initiatives.
 *
 * <p>All endpoints require EXECUTIVE_MANAGEMENT.* capabilities and are tenant-scoped.
 * The API follows SNAD REST conventions: /api/v1/management/{resource}.
 */
@RestController
@RequestMapping("/api/v1/management")
public class ManagementController {

    private final StrategicObjectiveService objectiveService;
    private final KeyResultService keyResultService;
    private final KpiService kpiService;
    private final StrategicInitiativeService initiativeService;

    public ManagementController(
            StrategicObjectiveService objectiveService,
            KeyResultService keyResultService,
            KpiService kpiService,
            StrategicInitiativeService initiativeService) {
        this.objectiveService = objectiveService;
        this.keyResultService = keyResultService;
        this.kpiService = kpiService;
        this.initiativeService = initiativeService;
    }

    // ===== Strategic Objectives =====

    @PostMapping("/objectives")
    @RequireCapability("EXECUTIVE_MANAGEMENT.WRITE")
    public ResponseEntity<ManagementResponses.ObjectiveResponse> createObjective(
            Authentication auth, @Valid @RequestBody ManagementRequests.CreateObjectiveRequest req) {
        var objective = StrategicObjective.create(
                tenantId(auth), req.code(), req.title(), req.description(),
                StrategicObjective.Priority.valueOf(req.priority()),
                req.ownerUserId(), req.periodStart(), req.periodEnd()
        );
        var saved = objectiveService.createObjective(objective);
        return ResponseEntity.ok(ManagementResponses.ObjectiveResponse.from(saved, List.of()));
    }

    @GetMapping("/objectives")
    @RequireCapability("EXECUTIVE_MANAGEMENT.VIEW")
    public ResponseEntity<List<ManagementResponses.ObjectiveResponse>> listObjectives(
            Authentication auth,
            @RequestParam(defaultValue = "50") int limit) {
        var objectives = objectiveService.findByTenant(tenantId(auth), limit);
        var responses = objectives.stream()
                .map(o -> ManagementResponses.ObjectiveResponse.from(o, List.of()))
                .toList();
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/objectives/{id}")
    @RequireCapability("EXECUTIVE_MANAGEMENT.VIEW")
    public ResponseEntity<ManagementResponses.ObjectiveResponse> getObjective(
            Authentication auth, @PathVariable UUID id) {
        return objectiveService.findById(tenantId(auth), id)
                .map(o -> {
                    var krs = keyResultService.findByObjective(tenantId(auth), id).stream()
                            .map(ManagementResponses.KeyResultResponse::from)
                            .toList();
                    return ResponseEntity.ok(ManagementResponses.ObjectiveResponse.from(o, krs));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/objectives/{id}/activate")
    @RequireCapability("EXECUTIVE_MANAGEMENT.WRITE")
    public ResponseEntity<ManagementResponses.ObjectiveResponse> activateObjective(
            Authentication auth, @PathVariable UUID id) {
        var o = objectiveService.activate(tenantId(auth), id);
        return ResponseEntity.ok(ManagementResponses.ObjectiveResponse.from(o, List.of()));
    }

    @PostMapping("/objectives/{id}/mark-at-risk")
    @RequireCapability("EXECUTIVE_MANAGEMENT.WRITE")
    public ResponseEntity<ManagementResponses.ObjectiveResponse> markAtRisk(
            Authentication auth, @PathVariable UUID id) {
        var o = objectiveService.markAtRisk(tenantId(auth), id);
        return ResponseEntity.ok(ManagementResponses.ObjectiveResponse.from(o, List.of()));
    }

    @PostMapping("/objectives/{id}/mark-off-track")
    @RequireCapability("EXECUTIVE_MANAGEMENT.WRITE")
    public ResponseEntity<ManagementResponses.ObjectiveResponse> markOffTrack(
            Authentication auth, @PathVariable UUID id) {
        var o = objectiveService.markOffTrack(tenantId(auth), id);
        return ResponseEntity.ok(ManagementResponses.ObjectiveResponse.from(o, List.of()));
    }

    @PostMapping("/objectives/{id}/achieve")
    @RequireCapability("EXECUTIVE_MANAGEMENT.WRITE")
    public ResponseEntity<ManagementResponses.ObjectiveResponse> achieveObjective(
            Authentication auth, @PathVariable UUID id) {
        var o = objectiveService.achieve(tenantId(auth), id);
        return ResponseEntity.ok(ManagementResponses.ObjectiveResponse.from(o, List.of()));
    }

    @PostMapping("/objectives/{id}/close")
    @RequireCapability("EXECUTIVE_MANAGEMENT.WRITE")
    public ResponseEntity<ManagementResponses.ObjectiveResponse> closeObjective(
            Authentication auth, @PathVariable UUID id) {
        var o = objectiveService.close(tenantId(auth), id);
        return ResponseEntity.ok(ManagementResponses.ObjectiveResponse.from(o, List.of()));
    }

    @PostMapping("/objectives/{id}/cancel")
    @RequireCapability("EXECUTIVE_MANAGEMENT.WRITE")
    public ResponseEntity<ManagementResponses.ObjectiveResponse> cancelObjective(
            Authentication auth, @PathVariable UUID id) {
        var o = objectiveService.cancel(tenantId(auth), id);
        return ResponseEntity.ok(ManagementResponses.ObjectiveResponse.from(o, List.of()));
    }

    @PostMapping("/objectives/{id}/recompute-progress")
    @RequireCapability("EXECUTIVE_MANAGEMENT.WRITE")
    public ResponseEntity<ManagementResponses.ObjectiveResponse> recomputeProgress(
            Authentication auth, @PathVariable UUID id) {
        var o = objectiveService.recomputeProgress(tenantId(auth), id);
        return ResponseEntity.ok(ManagementResponses.ObjectiveResponse.from(o, List.of()));
    }

    @DeleteMapping("/objectives/{id}")
    @RequireCapability("EXECUTIVE_MANAGEMENT.ADMIN")
    public ResponseEntity<Void> deleteObjective(Authentication auth, @PathVariable UUID id) {
        objectiveService.delete(tenantId(auth), id);
        return ResponseEntity.noContent().build();
    }

    // ===== Key Results =====

    @PostMapping("/objectives/{objectiveId}/key-results")
    @RequireCapability("EXECUTIVE_MANAGEMENT.WRITE")
    public ResponseEntity<ManagementResponses.KeyResultResponse> createKeyResult(
            Authentication auth, @PathVariable UUID objectiveId,
            @Valid @RequestBody ManagementRequests.CreateKeyResultRequest req) {
        var kr = KeyResult.create(
                tenantId(auth), objectiveId, req.title(), req.description(),
                KeyResult.MetricUnit.valueOf(req.metricUnit()),
                req.baselineValue() != null ? new BigDecimal(req.baselineValue()) : null,
                new BigDecimal(req.targetValue()),
                KeyResult.Direction.valueOf(req.direction()),
                req.weightPct(), req.ownerUserId(), req.dueDate()
        );
        var saved = keyResultService.create(kr);
        return ResponseEntity.ok(ManagementResponses.KeyResultResponse.from(saved));
    }

    @PostMapping("/key-results/{id}/measurement")
    @RequireCapability("EXECUTIVE_MANAGEMENT.WRITE")
    public ResponseEntity<ManagementResponses.KeyResultResponse> recordKeyResultMeasurement(
            Authentication auth, @PathVariable UUID id,
            @Valid @RequestBody ManagementRequests.RecordKeyResultMeasurementRequest req) {
        var kr = keyResultService.recordMeasurement(tenantId(auth), id, new BigDecimal(req.value()));
        return ResponseEntity.ok(ManagementResponses.KeyResultResponse.from(kr));
    }

    @PostMapping("/key-results/{id}/mark-missed")
    @RequireCapability("EXECUTIVE_MANAGEMENT.WRITE")
    public ResponseEntity<ManagementResponses.KeyResultResponse> markKeyResultMissed(
            Authentication auth, @PathVariable UUID id) {
        var kr = keyResultService.markMissed(tenantId(auth), id);
        return ResponseEntity.ok(ManagementResponses.KeyResultResponse.from(kr));
    }

    // ===== KPI Definitions =====

    @PostMapping("/kpis")
    @RequireCapability("EXECUTIVE_MANAGEMENT.WRITE")
    public ResponseEntity<ManagementResponses.KpiDefinitionResponse> createKpi(
            Authentication auth, @Valid @RequestBody ManagementRequests.CreateKpiDefinitionRequest req) {
        var def = KpiDefinition.create(
                tenantId(auth), req.code(), req.name(), req.description(),
                req.category(),
                KeyResult.MetricUnit.valueOf(req.metricUnit()),
                KeyResult.Direction.valueOf(req.direction()),
                req.formula(), req.sourceSystem(), req.ownerUserId()
        );
        var saved = kpiService.createDefinition(def);
        return ResponseEntity.ok(ManagementResponses.KpiDefinitionResponse.from(saved));
    }

    @GetMapping("/kpis")
    @RequireCapability("EXECUTIVE_MANAGEMENT.VIEW")
    public ResponseEntity<List<ManagementResponses.KpiDefinitionResponse>> listKpis(
            Authentication auth,
            @RequestParam(defaultValue = "50") int limit) {
        var defs = kpiService.findActiveDefinitions(tenantId(auth), limit);
        return ResponseEntity.ok(defs.stream()
                .map(ManagementResponses.KpiDefinitionResponse::from)
                .toList());
    }

    @PostMapping("/kpis/{id}/deactivate")
    @RequireCapability("EXECUTIVE_MANAGEMENT.WRITE")
    public ResponseEntity<ManagementResponses.KpiDefinitionResponse> deactivateKpi(
            Authentication auth, @PathVariable UUID id) {
        var def = kpiService.deactivateDefinition(tenantId(auth), id);
        return ResponseEntity.ok(ManagementResponses.KpiDefinitionResponse.from(def));
    }

    // ===== KPI Targets =====

    @PostMapping("/kpi-targets")
    @RequireCapability("EXECUTIVE_MANAGEMENT.WRITE")
    public ResponseEntity<ManagementResponses.KpiTargetResponse> createKpiTarget(
            Authentication auth, @Valid @RequestBody ManagementRequests.CreateKpiTargetRequest req) {
        var target = KpiTarget.create(
                tenantId(auth), req.kpiDefinitionId(),
                req.periodStart(), req.periodEnd(),
                new BigDecimal(req.targetValue()),
                req.minimumValue() != null ? new BigDecimal(req.minimumValue()) : null,
                req.stretchValue() != null ? new BigDecimal(req.stretchValue()) : null,
                req.ownerUserId()
        );
        var saved = kpiService.createTarget(target);
        return ResponseEntity.ok(ManagementResponses.KpiTargetResponse.from(saved));
    }

    // ===== KPI Measurements =====

    @PostMapping("/kpis/{kpiDefinitionId}/measurements")
    @RequireCapability("EXECUTIVE_MANAGEMENT.WRITE")
    public ResponseEntity<ManagementResponses.KpiMeasurementResponse> recordKpiMeasurement(
            Authentication auth, @PathVariable UUID kpiDefinitionId,
            @Valid @RequestBody ManagementRequests.RecordKpiMeasurementRequest req) {
        var m = kpiService.recordMeasurement(
                tenantId(auth), kpiDefinitionId, req.period(),
                new BigDecimal(req.value()), req.evidence(), userId(auth)
        );
        return ResponseEntity.ok(ManagementResponses.KpiMeasurementResponse.from(m));
    }

    @GetMapping("/kpis/{kpiDefinitionId}/measurements")
    @RequireCapability("EXECUTIVE_MANAGEMENT.VIEW")
    public ResponseEntity<List<ManagementResponses.KpiMeasurementResponse>> listKpiMeasurements(
            Authentication auth, @PathVariable UUID kpiDefinitionId,
            @RequestParam(defaultValue = "12") int limit) {
        var measurements = kpiService.findMeasurementHistory(kpiDefinitionId, limit);
        return ResponseEntity.ok(measurements.stream()
                .map(ManagementResponses.KpiMeasurementResponse::from)
                .toList());
    }

    // ===== Strategic Initiatives =====

    @PostMapping("/objectives/{objectiveId}/initiatives")
    @RequireCapability("EXECUTIVE_MANAGEMENT.WRITE")
    public ResponseEntity<ManagementResponses.InitiativeResponse> createInitiative(
            Authentication auth, @PathVariable UUID objectiveId,
            @Valid @RequestBody ManagementRequests.CreateInitiativeRequest req) {
        var initiative = StrategicInitiative.create(
                tenantId(auth), objectiveId, req.code(), req.name(), req.description(),
                req.ownerUserId(), req.startDate(), req.targetEndDate(), req.budgetMinor()
        );
        var saved = initiativeService.create(initiative);
        return ResponseEntity.ok(ManagementResponses.InitiativeResponse.from(saved));
    }

    @PostMapping("/initiatives/{id}/start")
    @RequireCapability("EXECUTIVE_MANAGEMENT.WRITE")
    public ResponseEntity<ManagementResponses.InitiativeResponse> startInitiative(
            Authentication auth, @PathVariable UUID id) {
        return ResponseEntity.ok(ManagementResponses.InitiativeResponse.from(
                initiativeService.start(tenantId(auth), id)));
    }

    @PostMapping("/initiatives/{id}/complete")
    @RequireCapability("EXECUTIVE_MANAGEMENT.WRITE")
    public ResponseEntity<ManagementResponses.InitiativeResponse> completeInitiative(
            Authentication auth, @PathVariable UUID id) {
        return ResponseEntity.ok(ManagementResponses.InitiativeResponse.from(
                initiativeService.complete(tenantId(auth), id)));
    }

    @PostMapping("/initiatives/{id}/progress")
    @RequireCapability("EXECUTIVE_MANAGEMENT.WRITE")
    public ResponseEntity<ManagementResponses.InitiativeResponse> updateInitiativeProgress(
            Authentication auth, @PathVariable UUID id,
            @RequestBody Map<String, Integer> body) {
        int progress = body.getOrDefault("progressPct", 0);
        return ResponseEntity.ok(ManagementResponses.InitiativeResponse.from(
                initiativeService.updateProgress(tenantId(auth), id, progress)));
    }

    // ===== Executive Dashboard =====

    @GetMapping("/dashboard")
    @RequireCapability("EXECUTIVE_MANAGEMENT.VIEW")
    public ResponseEntity<ManagementResponses.ExecutiveDashboardResponse> dashboard(
            Authentication auth,
            @RequestParam(defaultValue = "5") int topObjectivesLimit,
            @RequestParam(defaultValue = "10") int kpiLimit) {
        var tid = tenantId(auth);
        var objectives = objectiveService.findActiveForPeriod(tid, LocalDate.now());

        // Count objectives by status
        int active = (int) objectives.stream().filter(o -> o.status() == StrategicObjective.Status.ACTIVE).count();
        int atRisk = (int) objectives.stream().filter(o -> o.status() == StrategicObjective.Status.AT_RISK).count();
        int offTrack = (int) objectives.stream().filter(o -> o.status() == StrategicObjective.Status.OFF_TRACK).count();
        int achieved = (int) objectives.stream().filter(o -> o.status() == StrategicObjective.Status.ACHIEVED).count();

        // Aggregate Key Results from all objectives
        var allKrs = objectives.stream()
                .flatMap(o -> keyResultService.findByObjective(tid, o.id()).stream())
                .toList();
        int krsAchieved = (int) allKrs.stream().filter(kr -> kr.status() == KeyResult.Status.ACHIEVED).count();
        int krsAtRisk = (int) allKrs.stream().filter(kr -> kr.status() == KeyResult.Status.AT_RISK).count();
        int krsOffTrack = (int) allKrs.stream().filter(kr -> kr.status() == KeyResult.Status.OFF_TRACK).count();

        // KPI health
        var kpiDefs = kpiService.findActiveDefinitions(tid, kpiLimit);
        var kpiDefIds = kpiDefs.stream().map(KpiDefinition::id).toList();
        var latestMeasurements = kpiService.findLatestMeasurementsForDashboard(kpiDefIds);

        int kpisOnTrack = (int) latestMeasurements.stream().filter(m -> m.status() == KpiMeasurement.Status.ON_TRACK).count();
        int kpisAtRisk = (int) latestMeasurements.stream().filter(m -> m.status() == KpiMeasurement.Status.AT_RISK).count();
        int kpisOffTrack = (int) latestMeasurements.stream().filter(m -> m.status() == KpiMeasurement.Status.OFF_TRACK).count();
        int kpisNoData = kpiDefs.size() - latestMeasurements.size();

        // Top objectives (by priority, then by progress asc = most behind first)
        var topObjectives = objectives.stream()
                .sorted((a, b) -> {
                    int p = b.priority().compareTo(a.priority());
                    if (p != 0) return p;
                    return Integer.compare(a.progressPct(), b.progressPct());
                })
                .limit(topObjectivesLimit)
                .map(o -> new ManagementResponses.ExecutiveDashboardResponse.ObjectiveSummary(
                        o.id(), o.code(), o.title(), o.status().name(),
                        o.priority().name(), o.progressPct(), o.ownerUserId()))
                .toList();

        // KPI health summaries
        var kpiHealth = kpiDefs.stream()
                .map(def -> {
                    var m = latestMeasurements.stream()
                            .filter(meas -> meas.kpiDefinitionId().equals(def.id()))
                            .findFirst();
                    return new ManagementResponses.ExecutiveDashboardResponse.KpiHealthSummary(
                            def.id(), def.code(), def.name(), def.category(),
                            m.map(meas -> meas.status().name()).orElse("NO_DATA"),
                            m.map(KpiMeasurement::measuredValue).orElse(null),
                            m.flatMap(meas -> kpiService.findActiveTargetForDate(def.id(), LocalDate.now())
                                    .map(KpiTarget::targetValue)).orElse(null),
                            m.map(KpiMeasurement::variancePct).orElse(null),
                            m.map(KpiMeasurement::period).orElse(null)
                    );
                })
                .toList();

        // Initiatives count
        var allInitiatives = objectives.stream()
                .flatMap(o -> initiativeService.findByObjective(tid, o.id()).stream())
                .toList();
        int initiativesInProgress = (int) allInitiatives.stream()
                .filter(i -> i.status() == StrategicInitiative.Status.IN_PROGRESS).count();
        int initiativesOnHold = (int) allInitiatives.stream()
                .filter(i -> i.status() == StrategicInitiative.Status.ON_HOLD).count();
        int initiativesCompleted = (int) allInitiatives.stream()
                .filter(i -> i.status() == StrategicInitiative.Status.COMPLETED).count();

        var response = new ManagementResponses.ExecutiveDashboardResponse(
                objectives.size(), active, atRisk, offTrack, achieved,
                allKrs.size(), krsAchieved, krsAtRisk, krsOffTrack,
                kpiDefs.size(), kpisOnTrack, kpisAtRisk, kpisOffTrack, kpisNoData,
                allInitiatives.size(), initiativesInProgress, initiativesOnHold, initiativesCompleted,
                topObjectives, kpiHealth, Instant.now()
        );
        return ResponseEntity.ok(response);
    }
}
