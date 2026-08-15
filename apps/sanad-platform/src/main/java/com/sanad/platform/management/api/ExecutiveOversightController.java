package com.sanad.platform.management.api;

import com.sanad.platform.management.application.CrossModuleOperationalOverviewService;
import com.sanad.platform.management.application.ExecutiveReportService;
import com.sanad.platform.management.application.RevenueOversightService;
import com.sanad.platform.security.authorization.RequireCapability;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static com.sanad.platform.security.SecurityContextUtils.tenantId;

/**
 * Senior Management — Executive Oversight API (GAP 19 + 18 + 25).
 *
 * <p>Three endpoints exposed under {@code /api/v1/management/oversight}:
 * <ul>
 *   <li>{@code GET /revenue/overview} — unified Executive Revenue Overview (GAP 19)</li>
 *   <li>{@code GET /operations/overview} — Cross-Module Operational Overview (GAP 18)</li>
 *   <li>{@code GET /reports/executive} — Executive Report JSON download (GAP 25)</li>
 * </ul>
 *
 * <p>All endpoints require {@code EXECUTIVE_COMMAND_CENTER.VIEW} capability
 * (or {@code EXECUTIVE_REPORT.VIEW} for the report). Tenant-scoped.
 */
@RestController
@RequestMapping("/api/v1/management/oversight")
public class ExecutiveOversightController {

    private final RevenueOversightService revenueService;
    private final CrossModuleOperationalOverviewService operationalService;
    private final ExecutiveReportService reportService;

    public ExecutiveOversightController(
            RevenueOversightService revenueService,
            CrossModuleOperationalOverviewService operationalService,
            ExecutiveReportService reportService) {
        this.revenueService = revenueService;
        this.operationalService = operationalService;
        this.reportService = reportService;
    }

    @GetMapping("/revenue/overview")
    @RequireCapability("EXECUTIVE_COMMAND_CENTER.VIEW")
    public ResponseEntity<Map<String, Object>> getRevenueOverview(Authentication auth) {
        UUID tenantId = tenantId(auth);
        return ResponseEntity.ok(revenueService.getExecutiveRevenueOverview(tenantId));
    }

    @GetMapping("/operations/overview")
    @RequireCapability("EXECUTIVE_COMMAND_CENTER.VIEW")
    public ResponseEntity<Map<String, Object>> getOperationalOverview(Authentication auth) {
        UUID tenantId = tenantId(auth);
        return ResponseEntity.ok(operationalService.getOperationalOverview(tenantId));
    }

    @GetMapping("/reports/executive")
    @RequireCapability("EXECUTIVE_REPORT.VIEW")
    public ResponseEntity<Map<String, Object>> getExecutiveReport(Authentication auth) {
        UUID tenantId = tenantId(auth);
        Map<String, Object> report = reportService.generateReport(tenantId);
        String filename = "executive-report-" + tenantId.toString().substring(0, 8)
                + "-" + Instant.now().toEpochMilli() + ".json";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_JSON)
                .body(report);
    }
}
