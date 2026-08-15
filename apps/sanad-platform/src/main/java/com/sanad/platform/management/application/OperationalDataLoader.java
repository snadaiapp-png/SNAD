package com.sanad.platform.management.application;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Loads cross-module operational data in a REQUIRES_NEW transaction so
 * PSQLException aborts do not pollute the caller's transaction.
 *
 * <p>Used by {@link CrossModuleOperationalOverviewService}.
 */
@Service
public class OperationalDataLoader {

    private final ManagementGovernanceModuleRegistry registry;

    public OperationalDataLoader(@Lazy ManagementGovernanceModuleRegistry registry) {
        this.registry = registry;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public CrossModuleOperationalOverviewService.OperationalData loadInNewTransaction(UUID tenantId) {
        // Force fresh discovery
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

        return new CrossModuleOperationalOverviewService.OperationalData(
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
        if (o instanceof Number) return ((Number) o).intValue();
        return 0;
    }
}
