package com.sanad.platform.crm.reporting.web;

import com.sanad.platform.crm.pagination.CrmEnvelopes;
import com.sanad.platform.crm.reporting.application.ReportUseCases;
import com.sanad.platform.crm.reporting.domain.ReportData;
import com.sanad.platform.crm.reporting.domain.ReportRequest;
import com.sanad.platform.crm.reporting.domain.ReportType;
import com.sanad.platform.security.authorization.RequireCapability;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * REST controller for CRM reporting endpoints.
 * Mounted at /api/v2/crm/reports.
 */
@RestController
@RequestMapping("/api/v2/crm/reports")
public class ReportController {

    private final ReportUseCases reportUseCases;

    public ReportController(ReportUseCases reportUseCases) {
        this.reportUseCases = reportUseCases;
    }

    /**
     * Generate a CRM report based on type and date range.
     */
    @PostMapping("/generate")
    @RequireCapability("CRM.REPORTS.READ")
    public ResponseEntity<CrmEnvelopes.SingleResponse<ReportData>> generateReport(
            @RequestBody ReportModels.GenerateReportRequest request,
            Authentication authentication) {
        UUID tenantId = tenantId(authentication);
        UUID userId = userId(authentication);

        ReportType reportType = ReportType.valueOf(request.reportType());
        ReportRequest reportRequest = ReportRequest.of(
                tenantId, userId, reportType,
                request.dateFrom(), request.dateTo(),
                request.filters());

        ReportData reportData = reportUseCases.generateReport(reportRequest);
        return ResponseEntity.ok(CrmEnvelopes.SingleResponse.of(reportData, UUID.randomUUID()));
    }

    /**
     * Get summary statistics for the dashboard.
     */
    @GetMapping("/summary")
    @RequireCapability("CRM.REPORTS.READ")
    public ResponseEntity<CrmEnvelopes.SingleResponse<Map<String, Object>>> getSummary(
            @RequestParam Instant dateFrom,
            @RequestParam Instant dateTo,
            Authentication authentication) {
        UUID tenantId = tenantId(authentication);
        UUID userId = userId(authentication);

        ReportRequest request = ReportRequest.of(tenantId, userId, ReportType.LEAD_PIPELINE, dateFrom, dateTo, null);
        Map<String, Object> summary = reportUseCases.generateReport(request).summary();
        return ResponseEntity.ok(CrmEnvelopes.SingleResponse.of(summary, UUID.randomUUID()));
    }

    /**
     * List available report types.
     */
    @GetMapping("/types")
    @RequireCapability("CRM.REPORTS.READ")
    public ResponseEntity<CrmEnvelopes.SingleResponse<String[]>> listReportTypes() {
        String[] types = java.util.Arrays.stream(ReportType.values())
                .map(Enum::name)
                .toArray(String[]::new);
        return ResponseEntity.ok(CrmEnvelopes.SingleResponse.of(types, UUID.randomUUID()));
    }

    private static UUID tenantId(Authentication authentication) {
        return context(authentication, "tenant_id");
    }

    private static UUID userId(Authentication authentication) {
        return context(authentication, "user_id");
    }

    private static UUID context(Authentication authentication, String key) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getDetails() instanceof Map<?, ?> details)
                || details.get(key) == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Authenticated CRM context is required");
        }
        try {
            return UUID.fromString(details.get(key).toString());
        } catch (IllegalArgumentException exception) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Invalid authenticated CRM context", exception);
        }
    }
}
