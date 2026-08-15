package com.sanad.platform.management.application;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Isolated loader for cross-module operational data (v20260815.9).
 *
 * <p>Runs in its own {@link Propagation#REQUIRES_NEW} transaction so any
 * SQL error in a module adapter does not poison the caller's transaction.
 *
 * <p>CRITICAL: Does NOT catch exceptions internally — lets them propagate
 * so Spring can properly roll back the REQUIRES_NEW transaction. The caller
 * ({@link CrossModuleOperationalOverviewService}) catches the exception
 * and substitutes a degraded response.
 */
@Service
public class OperationalDataLoader {

    private final ManagementGovernanceModuleRegistry registry;

    public OperationalDataLoader(@Lazy ManagementGovernanceModuleRegistry registry) {
        this.registry = registry;
    }

    /** Composite record loaded in a separate transaction. */
    public record OperationalData(
            String overallHealthStatus,
            int moduleCount,
            List<Map<String, Object>> modules,
            int totalOpenAlerts,
            int totalOpenRisks,
            int totalOpenIssues,
            String slaOverallState
    ) {}

    /**
     * Load operational data in an isolated REQUIRES_NEW transaction.
     * If any adapter throws, the exception propagates (NOT caught here).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public OperationalData load(UUID tenantId) {
        // Force fresh discovery
        registry.invalidate(tenantId);

        List<Map<String, Object>> modules = registry.compositeSummary(tenantId);
        var overall = registry.compositeHealthStatus(tenantId);

        int totalOpenAlerts = 0;
        int totalOpenRisks = 0;
        int totalOpenIssues = 0;
        var worstSla = ManagementGovernanceModuleContract.SlaState.OK;

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

        return new OperationalData(
                overall.name(),
                modules.size(),
                modules,
                totalOpenAlerts,
                totalOpenRisks,
                totalOpenIssues,
                worstSla.name()
        );
    }

    private static int toInt(Object o) {
        if (o instanceof Number n) return n.intValue();
        return 0;
    }
}
