package com.sanad.platform.management.health;

import com.sanad.platform.management.application.WorkflowSystemHealthService;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Workflow health adapter (v20260816.1).
 *
 * <p>Adapts the existing {@link WorkflowSystemHealthService} into the System
 * Health contract. Maps the existing HEALTHY/DEGRADED/UNHEALTHY status into
 * the canonical {@link SystemHealthModel.SystemHealthStatus}.
 */
@Component
public class WorkflowSystemHealthContributor implements SystemHealthContributor {

    private final WorkflowSystemHealthService workflowService;

    public WorkflowSystemHealthContributor(@Lazy WorkflowSystemHealthService workflowService) {
        this.workflowService = workflowService;
    }

    @Override public String componentId() { return "workflow"; }
    @Override public String componentType() { return "MODULE"; }
    @Override public String displayName() { return "Workflow Engine"; }

    @Override
    @SuppressWarnings("unchecked")
    public SystemHealthModel.SystemHealthComponent checkHealth(UUID tenantId) {
        long start = System.currentTimeMillis();
        Map<String, Object> details = new LinkedHashMap<>();
        try {
            Map<String, Object> health = workflowService.getWorkflowHealth(tenantId);
            long latency = System.currentTimeMillis() - start;
            String statusStr = String.valueOf(health.getOrDefault("status", "HEALTHY"));
            var status = mapStatus(statusStr);
            details.put("totalDefinitions", health.getOrDefault("totalDefinitions", 0));
            details.put("activeDefinitions", health.getOrDefault("activeDefinitions", 0));
            details.put("runningInstances", health.getOrDefault("runningInstances", 0));
            details.put("failedInstances", health.getOrDefault("failedInstances", 0));
            details.put("pendingApprovals", health.getOrDefault("pendingApprovals", 0));
            details.put("overdueApprovals", health.getOrDefault("overdueApprovals", 0));
            details.put("slaBreaches", health.getOrDefault("slaBreaches", 0));

            String message;
            var severity = SystemHealthModel.SystemHealthComponent.Severity.INFO;
            if (status == SystemHealthModel.SystemHealthStatus.HEALTHY) {
                message = "Workflow engine healthy";
            } else if (status == SystemHealthModel.SystemHealthStatus.DEGRADED) {
                message = "Workflow engine degraded";
                severity = SystemHealthModel.SystemHealthComponent.Severity.WARN;
            } else {
                message = "Workflow engine unhealthy";
                severity = SystemHealthModel.SystemHealthComponent.Severity.ERROR;
            }
            return new SystemHealthModel.SystemHealthComponent(
                    componentId(), componentType(), displayName(), status, message,
                    Instant.now(), latency, details, Instant.now(), null, severity);
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - start;
            return new SystemHealthModel.SystemHealthComponent(
                    componentId(), componentType(), displayName(),
                    SystemHealthModel.SystemHealthStatus.UNHEALTHY,
                    "Workflow health check failed: " + e.getClass().getSimpleName(),
                    Instant.now(), latency, Map.of(), null, "WORKFLOW_CHECK_FAILED",
                    SystemHealthModel.SystemHealthComponent.Severity.ERROR);
        }
    }

    private SystemHealthModel.SystemHealthStatus mapStatus(String s) {
        return switch (s.toUpperCase()) {
            case "HEALTHY" -> SystemHealthModel.SystemHealthStatus.HEALTHY;
            case "DEGRADED" -> SystemHealthModel.SystemHealthStatus.DEGRADED;
            case "UNHEALTHY" -> SystemHealthModel.SystemHealthStatus.UNHEALTHY;
            default -> SystemHealthModel.SystemHealthStatus.UNKNOWN;
        };
    }
}
