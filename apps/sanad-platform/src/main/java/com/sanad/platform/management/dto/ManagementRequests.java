package com.sanad.platform.management.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Request DTOs for the Senior Management API.
 *
 * <p>Validation is via Jakarta Bean Validation (JSR-380).
 * Controllers validate these at the boundary before passing to services.
 */
public final class ManagementRequests {

    private ManagementRequests() {}

    public record CreateObjectiveRequest(
            @NotBlank String code,
            @NotBlank String title,
            String description,
            @NotBlank String priority,
            UUID ownerUserId,
            @NotNull LocalDate periodStart,
            @NotNull LocalDate periodEnd
    ) {}

    public record CreateKeyResultRequest(
            @NotBlank String title,
            String description,
            @NotBlank String metricUnit,
            String baselineValue,
            @NotNull String targetValue,
            @NotBlank String direction,
            int weightPct,
            UUID ownerUserId,
            LocalDate dueDate
    ) {}

    public record RecordKeyResultMeasurementRequest(
            @NotNull String value,
            String evidence
    ) {}

    public record CreateKpiDefinitionRequest(
            @NotBlank String code,
            @NotBlank String name,
            String description,
            String category,
            @NotBlank String metricUnit,
            @NotBlank String direction,
            String formula,
            String sourceSystem,
            UUID ownerUserId
    ) {}

    public record CreateKpiTargetRequest(
            @NotNull UUID kpiDefinitionId,
            @NotNull LocalDate periodStart,
            @NotNull LocalDate periodEnd,
            @NotNull String targetValue,
            String minimumValue,
            String stretchValue,
            UUID ownerUserId
    ) {}

    public record RecordKpiMeasurementRequest(
            @NotNull LocalDate period,
            @NotNull String value,
            String evidence
    ) {}

    public record CreateInitiativeRequest(
            @NotBlank String code,
            @NotBlank String name,
            String description,
            UUID ownerUserId,
            LocalDate startDate,
            LocalDate targetEndDate,
            Long budgetMinor
    ) {}
}
