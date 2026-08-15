package com.sanad.platform.management.application;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Adapter wrapping {@link ModuleGovernanceService} for the governance contract (GAP 24).
 *
 * <p>The Module Registry is itself a governed module — it tracks all modules'
 * enablement state, including itself. This adapter exposes the registry's
 * status as a governance contract so the command center sees it alongside
 * the other modules.
 */
@Service
public class ModuleRegistryGovernanceAdapter implements ManagementGovernanceModuleContract {

    private final ModuleGovernanceService moduleGovernanceService;

    public ModuleRegistryGovernanceAdapter(@Lazy ModuleGovernanceService moduleGovernanceService) {
        this.moduleGovernanceService = moduleGovernanceService;
    }

    @Override public String moduleCode() { return "MODULE_REGISTRY"; }
    @Override public String displayName() { return "Module Registry Governance"; }

    @Override
    public boolean isEnabled(UUID tenantId) {
        // The Module Registry is always enabled — it's the meta-module.
        return true;
    }

    @Override
    public ModuleHealthStatus healthStatus(UUID tenantId) {
        try {
            List<Map<String, Object>> statuses = moduleGovernanceService.getModuleStatuses();
            return statuses.isEmpty() ? ModuleHealthStatus.UNAVAILABLE : ModuleHealthStatus.HEALTHY;
        } catch (Exception e) {
            return ModuleHealthStatus.UNAVAILABLE;
        }
    }

    @Override
    public List<String> capabilities(UUID tenantId) {
        return List.of("EXECUTIVE_VIEW", "EXECUTIVE_MANAGE");
    }

    @Override
    public Map<String, Object> kpiSummary(UUID tenantId) {
        try {
            List<Map<String, Object>> statuses = moduleGovernanceService.getModuleStatuses();
            long enabledCount = statuses.stream().filter(s -> Boolean.TRUE.equals(s.get("enabled"))).count();
            return Map.of("totalModules", statuses.size(), "enabledModules", enabledCount);
        } catch (Exception e) {
            return Map.of("_error", e.getClass().getSimpleName());
        }
    }

    @Override
    public Map<String, Object> operationalSummary(UUID tenantId) {
        return kpiSummary(tenantId);
    }

    @Override public int openAlertsCount(UUID tenantId) { return 0; }
    @Override public int openRisksCount(UUID tenantId) { return 0; }
    @Override public int openIssuesCount(UUID tenantId) { return 0; }
    @Override public SlaState slaState(UUID tenantId) { return SlaState.NOT_APPLICABLE; }

    @Override
    public Map<String, Object> metadata() {
        return Map.of("version", "1.0", "maturity", "GA", "since", "V20260814_1");
    }
}
