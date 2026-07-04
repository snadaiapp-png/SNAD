package com.sanad.platform.health.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Typed contracts for executive health intelligence and controlled self-healing. */
public final class HealthDtos {

    private HealthDtos() {
    }

    public record PlatformHealthResponse(
            Instant generatedAt,
            String overallStatus,
            int healthScore,
            String riskLevel,
            String predictionSummary,
            RuntimeMetricsResponse runtime,
            DataPressureResponse dataPressure,
            List<ServiceHealthResponse> services,
            List<TenantHealthResponse> tenants,
            List<RiskForecastPoint> forecast,
            List<HealthActionDescriptor> availableActions
    ) {
    }

    public record RuntimeMetricsResponse(
            double cpuLoadPercent,
            double memoryUsagePercent,
            long memoryUsedMb,
            long memoryMaxMb,
            long uptimeSeconds,
            int availableProcessors
    ) {
    }

    public record DataPressureResponse(
            int pressureScore,
            String status,
            long trackedRows,
            long auditEventsLastHour,
            long failedAuditEventsLastHour,
            long openInvoices,
            long activeUsers,
            String message
    ) {
    }

    public record ServiceHealthResponse(
            UUID id,
            String code,
            String name,
            String environment,
            String status,
            String criticality,
            int healthScore,
            int pressureScore,
            String riskLevel,
            Long latencyMs,
            String lastMessage,
            Instant lastCheckedAt,
            String predictedStatus
    ) {
    }

    public record TenantHealthResponse(
            UUID tenantId,
            String tenantName,
            String tenantStatus,
            int healthScore,
            int pressureScore,
            String riskLevel,
            long users,
            long organizations,
            long memberships,
            long invoices,
            long openInvoices,
            long seatCapacity,
            int seatUtilizationPercent,
            long trackedRecords,
            String prediction
    ) {
    }

    public record RiskForecastPoint(
            int horizonMinutes,
            int riskScore,
            String riskLevel,
            String label
    ) {
    }

    public record HealthActionDescriptor(
            String code,
            String scope,
            String title,
            String description,
            boolean requiresTarget
    ) {
    }

    public record HealthActionRequest(
            @NotBlank @Size(max = 30) String scope,
            UUID targetId,
            @NotBlank @Size(max = 50) String action,
            @NotBlank @Size(max = 500) String reason
    ) {
    }

    public record HealthActionResult(
            String action,
            String scope,
            UUID targetId,
            String status,
            String message,
            Instant executedAt,
            PlatformHealthResponse snapshot
    ) {
    }
}
