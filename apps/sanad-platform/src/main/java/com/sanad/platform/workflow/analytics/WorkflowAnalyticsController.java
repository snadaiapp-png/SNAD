package com.sanad.platform.workflow.analytics;

import com.sanad.platform.security.SecurityContextUtils;
import com.sanad.platform.security.authorization.RequireCapability;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Workflow analytics + dashboards API (GATES R2.14/R2.16/R2.17 read
 * surface). Every endpoint is capability-gated, tenant-scoped, and
 * derives EXCLUSIVELY from Journey-backed projections.
 */
@RestController
@RequestMapping("/api/v1/workflows/analytics")
public class WorkflowAnalyticsController {

    private final WorkflowAnalyticsQueryService queryService;
    private final WorkflowAnalyticsProjectionService projectionService;
    private final WorkflowPerformanceMetricsService metricsService;

    public WorkflowAnalyticsController(WorkflowAnalyticsQueryService queryService,
                                       WorkflowAnalyticsProjectionService projectionService,
                                       WorkflowPerformanceMetricsService metricsService) {
        this.queryService = queryService;
        this.projectionService = projectionService;
        this.metricsService = metricsService;
    }

    @GetMapping("/dashboards/service")
    @RequireCapability("WORKFLOW.MONITOR")
    public ResponseEntity<Map<String, Object>> service(
            Authentication authentication,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        UUID tenantId = SecurityContextUtils.tenantId(authentication);
        return ResponseEntity.ok(queryService.serviceDashboard(tenantId,
                parse(from), parse(to)));
    }

    @GetMapping("/dashboards/employee")
    @RequireCapability("WORKFLOW.MONITOR")
    public ResponseEntity<Map<String, Object>> employee(
            Authentication authentication,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) Integer limit) {
        UUID tenantId = SecurityContextUtils.tenantId(authentication);
        return ResponseEntity.ok(queryService.employeeDashboard(tenantId,
                parse(from), parse(to), limit));
    }

    @GetMapping("/dashboards/executive")
    @RequireCapability("WORKFLOW.MONITOR")
    public ResponseEntity<Map<String, Object>> executive(
            Authentication authentication,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        UUID tenantId = SecurityContextUtils.tenantId(authentication);
        return ResponseEntity.ok(queryService.executiveDashboard(tenantId,
                parse(from), parse(to)));
    }

    @GetMapping("/dashboards/bottleneck")
    @RequireCapability("WORKFLOW.MONITOR")
    public ResponseEntity<Map<String, Object>> bottleneck(
            Authentication authentication,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) Integer limit) {
        UUID tenantId = SecurityContextUtils.tenantId(authentication);
        return ResponseEntity.ok(queryService.bottleneckDashboard(tenantId,
                parse(from), parse(to), limit));
    }

    @GetMapping("/dashboards/sla")
    @RequireCapability("WORKFLOW.MONITOR")
    public ResponseEntity<Map<String, Object>> sla(
            Authentication authentication,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        UUID tenantId = SecurityContextUtils.tenantId(authentication);
        return ResponseEntity.ok(queryService.slaDashboard(tenantId,
                parse(from), parse(to)));
    }

    @GetMapping("/dashboards/customer")
    @RequireCapability("WORKFLOW.MONITOR")
    public ResponseEntity<Map<String, Object>> customer(
            Authentication authentication,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        UUID tenantId = SecurityContextUtils.tenantId(authentication);
        return ResponseEntity.ok(queryService.customerDashboard(tenantId,
                parse(from), parse(to)));
    }

    /**
     * Explicit deterministic rebuild (GATE R2.15). WORKFLOW.ADMIN only —
     * analytics is derived/rebuildable, journey stays authoritative.
     */
    @PostMapping("/rebuild")
    @RequireCapability("WORKFLOW.ADMIN")
    public ResponseEntity<Map<String, Object>> rebuild(
            Authentication authentication,
            @RequestParam(defaultValue = "200") int limit) {
        UUID tenantId = SecurityContextUtils.tenantId(authentication);
        int projected = projectionService.rebuildTenant(tenantId, limit);
        return ResponseEntity.ok(Map.of("projectedInstances", projected));
    }

    /** Explainable performance metrics (GATE R2.17 — read-only surface). */
    @GetMapping("/metrics/employee/{employeeId}")
    @RequireCapability("WORKFLOW.MONITOR")
    public ResponseEntity<WorkflowPerformanceMetricsService.MetricResult> employeeMetrics(
            Authentication authentication, @PathVariable UUID employeeId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        UUID tenantId = SecurityContextUtils.tenantId(authentication);
        return ResponseEntity.ok(metricsService.employeeMetrics(
                tenantId, employeeId, parse(from), parse(to)));
    }

    private static Instant parse(String value) {
        return value == null || value.isBlank() ? null : Instant.parse(value);
    }
}
