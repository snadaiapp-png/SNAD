package com.sanad.platform.management.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Cross-Module Operational Overview (GAP 18).
 *
 * <p>Produces a unified, typed operational picture across all enabled
 * governed modules for a tenant. Uses {@link ManagementGovernanceModuleRegistry}
 * to auto-discover modules — adding a new module (ERP/HRM/POS) requires
 * no modification to this service.
 *
 * <p>Result fields:
 * <ul>
 *   <li>{@code overallHealthStatus} — composite rollup (HEALTHY/DEGRADED/UNHEALTHY/UNAVAILABLE)</li>
 *   <li>{@code moduleCount} — number of enabled modules</li>
 *   <li>{@code modules} — per-module summary (see {@link ManagementGovernanceModuleRegistry#compositeSummary})</li>
 *   <li>{@code totalOpenAlerts} — sum of open alerts across modules</li>
 *   <li>{@code totalOpenRisks} — sum of open risks across modules</li>
 *   <li>{@code totalOpenIssues} — sum of open issues across modules</li>
 *   <li>{@code slaOverallState} — worst SLA state across modules</li>
 *   <li>{@code generatedAt} — timestamp</li>
 * </ul>
 *
 * <p>Read-only and tenant-scoped. No new tables. No duplicate business logic.
 */
@Service
public class CrossModuleOperationalOverviewService {

    private final ManagementGovernanceModuleRegistry registry;

    public CrossModuleOperationalOverviewService(ManagementGovernanceModuleRegistry registry) {
        this.registry = registry;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getOperationalOverview(UUID tenantId) {
        Map<String, Object> result = new LinkedHashMap<>();

        // Force fresh discovery (invalidate cache)
        registry.invalidate(tenantId);

        List<Map<String, Object>> modules = registry.compositeSummary(tenantId);
        ManagementGovernanceModuleContract.ModuleHealthStatus overall =
                registry.compositeHealthStatus(tenantId);

        int totalOpenAlerts = 0;
        int totalOpenRisks = 0;
        int totalOpenIssues = 0;
        ManagementGovernanceModuleContract.SlaState worstSla =
                ManagementGovernanceModuleContract.SlaState.OK;

        for (Map<String, Object> m : modules) {
            totalOpenAlerts += toInt(m.get("openAlertsCount"));
            totalOpenRisks += toInt(m.get("openRisksCount"));
            totalOpenIssues += toInt(m.get("openIssuesCount"));
            Object sla = m.get("slaState");
            if (sla instanceof ManagementGovernanceModuleContract.SlaState s) {
                if (s == ManagementGovernanceModuleContract.SlaState.BREACHED) {
                    worstSla = ManagementGovernanceModuleContract.SlaState.BREACHED;
                } else if (s == ManagementGovernanceModuleContract.SlaState.AT_RISK
                        && worstSla != ManagementGovernanceModuleContract.SlaState.BREACHED) {
                    worstSla = ManagementGovernanceModuleContract.SlaState.AT_RISK;
                }
            }
        }

        result.put("overallHealthStatus", overall.name());
        result.put("moduleCount", modules.size());
        result.put("modules", modules);
        result.put("totalOpenAlerts", totalOpenAlerts);
        result.put("totalOpenRisks", totalOpenRisks);
        result.put("totalOpenIssues", totalOpenIssues);
        result.put("slaOverallState", worstSla.name());
        result.put("generatedAt", Instant.now().toString());
        return result;
    }

    private static int toInt(Object o) {
        if (o instanceof Number) return ((Number) o).intValue();
        return 0;
    }
}
