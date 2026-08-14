package com.sanad.platform.management.api;

import com.sanad.platform.management.application.ExecutiveAlertService;
import com.sanad.platform.management.application.ExecutiveCommandCenterService;
import com.sanad.platform.management.application.ExecutiveIntelligenceService;
import com.sanad.platform.management.domain.ExecutiveAlert;
import com.sanad.platform.management.domain.ExecutiveInsight;
import com.sanad.platform.security.authorization.RequireCapability;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.sanad.platform.security.SecurityContextUtils.tenantId;
import static com.sanad.platform.security.SecurityContextUtils.userId;

/**
 * Executive Command Center API — the unified dashboard, alerts, and AI intelligence endpoints.
 */
@RestController
@RequestMapping("/api/v1/management")
public class CommandCenterController {

    private final ExecutiveCommandCenterService commandCenterService;
    private final ExecutiveAlertService alertService;
    private final ExecutiveIntelligenceService intelligenceService;

    public CommandCenterController(
            ExecutiveCommandCenterService commandCenterService,
            ExecutiveAlertService alertService,
            ExecutiveIntelligenceService intelligenceService) {
        this.commandCenterService = commandCenterService;
        this.alertService = alertService;
        this.intelligenceService = intelligenceService;
    }

    // ===== Command Center Dashboard =====

    @GetMapping("/command-center")
    @RequireCapability("EXECUTIVE_COMMAND_CENTER.VIEW")
    public ResponseEntity<ExecutiveCommandCenterService.CommandCenterDashboard> dashboard(
            Authentication auth) {
        return ResponseEntity.ok(commandCenterService.getDashboard(tenantId(auth)));
    }

    @PostMapping("/command-center/snapshot")
    @RequireCapability("EXECUTIVE_COMMAND_CENTER.VIEW")
    public ResponseEntity<Map<String, Object>> snapshotHealth(Authentication auth) {
        var snapshot = commandCenterService.snapshotHealth(tenantId(auth));
        return ResponseEntity.ok(Map.of(
                "healthScore", snapshot.healthScore(),
                "strategyScore", snapshot.strategyScore(),
                "kpiScore", snapshot.kpiScore(),
                "riskScore", snapshot.riskScore(),
                "snapshotAt", snapshot.snapshotAt().toString()
        ));
    }

    // ===== Alerts =====

    @GetMapping("/alerts")
    @RequireCapability("EXECUTIVE_ALERTS.VIEW")
    public ResponseEntity<List<Map<String, Object>>> listAlerts(
            Authentication auth, @RequestParam(defaultValue = "50") int limit) {
        var alerts = alertService.findByTenant(tenantId(auth), limit);
        return ResponseEntity.ok(alerts.stream().map(this::toAlertMap).toList());
    }

    @GetMapping("/alerts/open")
    @RequireCapability("EXECUTIVE_ALERTS.VIEW")
    public ResponseEntity<List<Map<String, Object>>> listOpenAlerts(
            Authentication auth, @RequestParam(defaultValue = "50") int limit) {
        var alerts = alertService.findOpenAlerts(tenantId(auth), limit);
        return ResponseEntity.ok(alerts.stream().map(this::toAlertMap).toList());
    }

    @PostMapping("/alerts/{id}/acknowledge")
    @RequireCapability("EXECUTIVE_ALERTS.WRITE")
    public ResponseEntity<Map<String, Object>> acknowledgeAlert(
            Authentication auth, @PathVariable UUID id) {
        return ResponseEntity.ok(toAlertMap(
                alertService.acknowledge(tenantId(auth), id, userId(auth))));
    }

    @PostMapping("/alerts/{id}/resolve")
    @RequireCapability("EXECUTIVE_ALERTS.WRITE")
    public ResponseEntity<Map<String, Object>> resolveAlert(
            Authentication auth, @PathVariable UUID id,
            @RequestBody Map<String, String> body) {
        var resolution = body.getOrDefault("resolution", "");
        return ResponseEntity.ok(toAlertMap(
                alertService.resolve(tenantId(auth), id, resolution, userId(auth))));
    }

    @PostMapping("/alerts/{id}/dismiss")
    @RequireCapability("EXECUTIVE_ALERTS.ADMIN")
    public ResponseEntity<Map<String, Object>> dismissAlert(
            Authentication auth, @PathVariable UUID id,
            @RequestBody Map<String, String> body) {
        var reason = body.getOrDefault("reason", "");
        return ResponseEntity.ok(toAlertMap(
                alertService.dismiss(tenantId(auth), id, reason, userId(auth))));
    }

    // ===== Executive Intelligence (AI) =====

    @PostMapping("/intelligence/summary")
    @RequireCapability("EXECUTIVE_INTELLIGENCE.VIEW")
    public ResponseEntity<Map<String, Object>> generateSummary(Authentication auth) {
        var insight = intelligenceService.generateExecutiveSummary(tenantId(auth), userId(auth));
        return ResponseEntity.ok(toInsightMap(insight));
    }

    @PostMapping("/intelligence/anomalies")
    @RequireCapability("EXECUTIVE_INTELLIGENCE.VIEW")
    public ResponseEntity<List<Map<String, Object>>> detectAnomalies(Authentication auth) {
        var insights = intelligenceService.detectKpiAnomalies(tenantId(auth), userId(auth));
        return ResponseEntity.ok(insights.stream().map(this::toInsightMap).toList());
    }

    @PostMapping("/intelligence/recommend")
    @RequireCapability("EXECUTIVE_INTELLIGENCE.VIEW")
    public ResponseEntity<Map<String, Object>> recommendAction(Authentication auth) {
        var insight = intelligenceService.recommendExecutiveAction(tenantId(auth), userId(auth));
        return ResponseEntity.ok(toInsightMap(insight));
    }

    @GetMapping("/intelligence")
    @RequireCapability("EXECUTIVE_INTELLIGENCE.VIEW")
    public ResponseEntity<List<Map<String, Object>>> listInsights(
            Authentication auth, @RequestParam(defaultValue = "20") int limit) {
        var insights = intelligenceService.findActiveInsights(tenantId(auth), limit);
        return ResponseEntity.ok(insights.stream().map(this::toInsightMap).toList());
    }

    @PostMapping("/intelligence/{id}/dismiss")
    @RequireCapability("EXECUTIVE_INTELLIGENCE.ADMIN")
    public ResponseEntity<Map<String, Object>> dismissInsight(
            Authentication auth, @PathVariable UUID id) {
        return ResponseEntity.ok(toInsightMap(
                intelligenceService.dismissInsight(tenantId(auth), id)));
    }

    // ===== Response helpers =====

    private Map<String, Object> toAlertMap(ExecutiveAlert a) {
        return Map.of(
                "id", a.id(),
                "type", a.type().name(),
                "severity", a.severity().name(),
                "sourceEntityType", a.sourceEntityType().name(),
                "sourceEntityId", a.sourceEntityId(),
                "title", a.title(),
                "status", a.status().name()
        );
    }

    private Map<String, Object> toInsightMap(ExecutiveInsight i) {
        return Map.of(
                "id", i.id(),
                "type", i.type().name(),
                "title", i.title(),
                "description", i.description(),
                "confidence", i.confidence().toString(),
                "modelName", i.modelName(),
                "advisory", i.advisory(),
                "status", i.status().name()
        );
    }
}
