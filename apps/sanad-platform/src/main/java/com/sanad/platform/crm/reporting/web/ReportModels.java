package com.sanad.platform.crm.reporting.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.Map;

/**
 * Request/Response DTOs for CRM reporting endpoints.
 */
public final class ReportModels {

    private ReportModels() {}

    /**
     * Request body for generating a CRM report.
     */
    public record GenerateReportRequest(
            @NotBlank String reportType,
            @NotNull Instant dateFrom,
            @NotNull Instant dateTo,
            Map<String, String> filters
    ) {}
}
