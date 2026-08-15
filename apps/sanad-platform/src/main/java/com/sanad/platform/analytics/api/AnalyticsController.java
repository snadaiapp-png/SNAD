package com.sanad.platform.analytics.api;

import com.sanad.platform.analytics.application.*;
import com.sanad.platform.analytics.domain.*;
import com.sanad.platform.security.authorization.RequireCapability;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.*;

import static com.sanad.platform.security.SecurityContextUtils.tenantId;
import static com.sanad.platform.security.SecurityContextUtils.userId;

@RestController
@RequestMapping("/api/v1/analytics")
public class AnalyticsController {

    private final AnalyticsDashboardService dashboardService;
    private final AnalyticsReportService reportService;
    private final AnalyticsDataSourceService dataSourceService;

    public AnalyticsController(AnalyticsDashboardService dashboardService,
                               AnalyticsReportService reportService,
                               AnalyticsDataSourceService dataSourceService) {
        this.dashboardService = dashboardService;
        this.reportService = reportService;
        this.dataSourceService = dataSourceService;
    }

    // ===== Dashboards =====
    @PostMapping("/dashboards") @RequireCapability("ANALYTICS.WRITE")
    public ResponseEntity<Map<String,Object>> createDashboard(Authentication auth, @RequestBody CreateDashboardRequest req) {
        var d = AnalyticsDashboard.create(tenantId(auth), req.code(), req.name(), req.description(),
                req.dashboardType()!=null?AnalyticsDashboard.DashboardType.valueOf(req.dashboardType()):null,
                req.configuration(), userId(auth));
        return ResponseEntity.ok(toDashboardMap(dashboardService.create(d)));
    }
    @GetMapping("/dashboards") @RequireCapability("ANALYTICS.VIEW")
    public ResponseEntity<List<Map<String,Object>>> listDashboards(Authentication auth, @RequestParam(defaultValue="50") int limit) {
        return ResponseEntity.ok(dashboardService.findByTenant(tenantId(auth), limit).stream().map(this::toDashboardMap).toList());
    }
    @GetMapping("/dashboards/{id}") @RequireCapability("ANALYTICS.VIEW")
    public ResponseEntity<Map<String,Object>> getDashboard(Authentication auth, @PathVariable UUID id) {
        return dashboardService.findById(tenantId(auth), id).map(d->ResponseEntity.ok(toDashboardMap(d))).orElse(ResponseEntity.notFound().build());
    }
    @PostMapping("/dashboards/{id}/activate") @RequireCapability("ANALYTICS.WRITE")
    public ResponseEntity<Map<String,Object>> activateDashboard(Authentication auth, @PathVariable UUID id) {
        return ResponseEntity.ok(toDashboardMap(dashboardService.activate(tenantId(auth), id)));
    }
    @PostMapping("/dashboards/{id}/deactivate") @RequireCapability("ANALYTICS.ADMIN")
    public ResponseEntity<Map<String,Object>> deactivateDashboard(Authentication auth, @PathVariable UUID id) {
        return ResponseEntity.ok(toDashboardMap(dashboardService.deactivate(tenantId(auth), id)));
    }
    @PostMapping("/dashboards/{id}/archive") @RequireCapability("ANALYTICS.ADMIN")
    public ResponseEntity<Map<String,Object>> archiveDashboard(Authentication auth, @PathVariable UUID id) {
        return ResponseEntity.ok(toDashboardMap(dashboardService.archive(tenantId(auth), id)));
    }

    // ===== Reports =====
    @PostMapping("/reports") @RequireCapability("ANALYTICS.WRITE")
    public ResponseEntity<Map<String,Object>> createReport(Authentication auth, @RequestBody CreateReportRequest req) {
        var r = AnalyticsReport.create(tenantId(auth), req.code(), req.name(), req.description(),
                req.reportType()!=null?AnalyticsReport.ReportType.valueOf(req.reportType()):null,
                req.dataSourceId(), req.queryText(), req.parameters(),
                req.outputFormat()!=null?AnalyticsReport.OutputFormat.valueOf(req.outputFormat()):null,
                userId(auth));
        return ResponseEntity.ok(toReportMap(reportService.create(r)));
    }
    @GetMapping("/reports") @RequireCapability("ANALYTICS.VIEW")
    public ResponseEntity<List<Map<String,Object>>> listReports(Authentication auth, @RequestParam(defaultValue="50") int limit) {
        return ResponseEntity.ok(reportService.findByTenant(tenantId(auth), limit).stream().map(this::toReportMap).toList());
    }
    @GetMapping("/reports/{id}") @RequireCapability("ANALYTICS.VIEW")
    public ResponseEntity<Map<String,Object>> getReport(Authentication auth, @PathVariable UUID id) {
        return reportService.findById(tenantId(auth), id).map(r->ResponseEntity.ok(toReportMap(r))).orElse(ResponseEntity.notFound().build());
    }
    @PostMapping("/reports/{id}/activate") @RequireCapability("ANALYTICS.WRITE")
    public ResponseEntity<Map<String,Object>> activateReport(Authentication auth, @PathVariable UUID id) {
        return ResponseEntity.ok(toReportMap(reportService.activate(tenantId(auth), id)));
    }
    @PostMapping("/reports/{id}/schedule") @RequireCapability("ANALYTICS.WRITE")
    public ResponseEntity<Map<String,Object>> scheduleReport(Authentication auth, @PathVariable UUID id, @RequestBody Map<String,String> body) {
        return ResponseEntity.ok(toReportMap(reportService.schedule(tenantId(auth), id, body.getOrDefault("cron",""))));
    }
    @PostMapping("/reports/{id}/archive") @RequireCapability("ANALYTICS.ADMIN")
    public ResponseEntity<Map<String,Object>> archiveReport(Authentication auth, @PathVariable UUID id) {
        return ResponseEntity.ok(toReportMap(reportService.archive(tenantId(auth), id)));
    }
    @PostMapping("/reports/{id}/execute") @RequireCapability("ANALYTICS.EXECUTE")
    public ResponseEntity<Map<String,Object>> executeReport(Authentication auth, @PathVariable UUID id) {
        return ResponseEntity.ok(toReportMap(reportService.execute(tenantId(auth), id)));
    }

    // ===== Data Sources =====
    @PostMapping("/data-sources") @RequireCapability("ANALYTICS.ADMIN")
    public ResponseEntity<Map<String,Object>> createDataSource(Authentication auth, @RequestBody CreateDataSourceRequest req) {
        var ds = AnalyticsDataSource.create(tenantId(auth), req.code(), req.name(), req.description(),
                AnalyticsDataSource.SourceType.valueOf(req.sourceType()), req.module(), req.configuration(), userId(auth));
        return ResponseEntity.ok(toDataSourceMap(dataSourceService.create(ds)));
    }
    @GetMapping("/data-sources") @RequireCapability("ANALYTICS.VIEW")
    public ResponseEntity<List<Map<String,Object>>> listDataSources(Authentication auth, @RequestParam(defaultValue="50") int limit) {
        return ResponseEntity.ok(dataSourceService.findByTenant(tenantId(auth), limit).stream().map(this::toDataSourceMap).toList());
    }
    @GetMapping("/data-sources/{id}") @RequireCapability("ANALYTICS.VIEW")
    public ResponseEntity<Map<String,Object>> getDataSource(Authentication auth, @PathVariable UUID id) {
        return dataSourceService.findById(tenantId(auth), id).map(d->ResponseEntity.ok(toDataSourceMap(d))).orElse(ResponseEntity.notFound().build());
    }
    @PostMapping("/data-sources/{id}/activate") @RequireCapability("ANALYTICS.ADMIN")
    public ResponseEntity<Map<String,Object>> activateDataSource(Authentication auth, @PathVariable UUID id) {
        return ResponseEntity.ok(toDataSourceMap(dataSourceService.activate(tenantId(auth), id)));
    }
    @PostMapping("/data-sources/{id}/deactivate") @RequireCapability("ANALYTICS.ADMIN")
    public ResponseEntity<Map<String,Object>> deactivateDataSource(Authentication auth, @PathVariable UUID id) {
        return ResponseEntity.ok(toDataSourceMap(dataSourceService.deactivate(tenantId(auth), id)));
    }

    // ===== DTOs =====
    public record CreateDashboardRequest(String code, String name, String description, String dashboardType, String configuration) {}
    public record CreateReportRequest(String code, String name, String description, String reportType, UUID dataSourceId, String queryText, String parameters, String outputFormat) {}
    public record CreateDataSourceRequest(String code, String name, String description, String sourceType, String module, String configuration) {}

    // ===== Response helpers =====
    private Map<String,Object> toDashboardMap(AnalyticsDashboard d) {
        var m = new HashMap<String,Object>();
        m.put("id",d.id()); m.put("code",d.code()); m.put("name",d.name());
        m.put("dashboardType",d.dashboardType().name()); m.put("status",d.status().name());
        m.put("version",d.version()); m.put("createdBy",d.createdBy());
        return m;
    }
    private Map<String,Object> toReportMap(AnalyticsReport r) {
        var m = new HashMap<String,Object>();
        m.put("id",r.id()); m.put("code",r.code()); m.put("name",r.name());
        m.put("reportType",r.reportType().name()); m.put("outputFormat",r.outputFormat().name());
        m.put("status",r.status().name()); m.put("version",r.version());
        m.put("lastExecutionStatus",r.lastExecutionStatus()!=null?r.lastExecutionStatus():"");
        return m;
    }
    private Map<String,Object> toDataSourceMap(AnalyticsDataSource ds) {
        var m = new HashMap<String,Object>();
        m.put("id",ds.id()); m.put("code",ds.code()); m.put("name",ds.name());
        m.put("sourceType",ds.sourceType().name()); m.put("status",ds.status().name());
        m.put("version",ds.version()); m.put("module",ds.module()!=null?ds.module():"");
        return m;
    }
}
