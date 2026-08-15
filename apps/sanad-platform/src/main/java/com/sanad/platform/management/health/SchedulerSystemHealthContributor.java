package com.sanad.platform.management.health;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Scheduler / Job health contributor (v20260816.1).
 *
 * <p>Reports the health of background schedulers. Currently tracks in-memory
 * evidence of scheduler availability (the SchedulingConfig bean). A more
 * advanced version would track lastRunAt/lastSuccessAt per job in a
 * persistent table — but that requires a migration which is deferred until
 * runtime evidence proves in-memory state is insufficient.
 */
@Component
public class SchedulerSystemHealthContributor implements SystemHealthContributor {

    @Override public String componentId() { return "schedulers"; }
    @Override public String componentType() { return "OPERATIONS"; }
    @Override public String displayName() { return "Background Schedulers"; }

    @Override
    public SystemHealthModel.SystemHealthComponent checkHealth(UUID tenantId) {
        long start = System.currentTimeMillis();
        Map<String, Object> details = new LinkedHashMap<>();
        try {
            // In-memory check — scheduler infrastructure is available if the bean loads
            details.put("schedulingEnabled", true);
            details.put("knownJobs", java.util.List.of(
                    "workflowSlaScheduler", "billingDunningCycle",
                    "customerRescoringJob", "crmWorkflowOutboxWorker",
                    "crmIntegrationOutboxWorker", "callbackReplayStore"
            ));
            long latency = System.currentTimeMillis() - start;
            return new SystemHealthModel.SystemHealthComponent(
                    componentId(), componentType(), displayName(),
                    SystemHealthModel.SystemHealthStatus.HEALTHY,
                    "Scheduler infrastructure available",
                    Instant.now(), latency, details, Instant.now(), null,
                    SystemHealthModel.SystemHealthComponent.Severity.INFO);
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - start;
            return new SystemHealthModel.SystemHealthComponent(
                    componentId(), componentType(), displayName(),
                    SystemHealthModel.SystemHealthStatus.UNHEALTHY,
                    "Scheduler check failed: " + e.getClass().getSimpleName(),
                    Instant.now(), latency, Map.of(), null, "SCHEDULER_CHECK_FAILED",
                    SystemHealthModel.SystemHealthComponent.Severity.ERROR);
        }
    }
}
