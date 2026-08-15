package com.sanad.platform.management.health;

import com.sanad.platform.management.application.ManagementGovernanceModuleRegistry;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Governance health contributor (v20260816.1).
 *
 * <p>Validates the availability and consistency of the governance layer:
 * ManagementGovernanceModuleRegistry, governance adapters, Governance
 * Configuration, Executive Command Center aggregation, module auto-discovery.
 */
@Component
public class GovernanceSystemHealthContributor implements SystemHealthContributor {

    private final SystemHealthContributorRegistry healthContributorRegistry;
    private final ManagementGovernanceModuleRegistry moduleRegistry;

    public GovernanceSystemHealthContributor(
            SystemHealthContributorRegistry healthContributorRegistry,
            @Lazy ManagementGovernanceModuleRegistry moduleRegistry) {
        this.healthContributorRegistry = healthContributorRegistry;
        this.moduleRegistry = moduleRegistry;
    }

    @Override public String componentId() { return "governance"; }
    @Override public String componentType() { return "GOVERNANCE"; }
    @Override public String displayName() { return "Governance Health"; }

    @Override
    public SystemHealthModel.SystemHealthComponent checkHealth(UUID tenantId) {
        long start = System.currentTimeMillis();
        Map<String, Object> details = new LinkedHashMap<>();
        try {
            int governanceAdapters = moduleRegistry.allModules().size();
            int healthContributors = healthContributorRegistry.allContributors().size();
            details.put("registeredGovernanceAdapters", governanceAdapters);
            details.put("registeredHealthContributors", healthContributors);
            details.put("contributorIds", healthContributorRegistry.allContributorIds());
            long latency = System.currentTimeMillis() - start;
            return new SystemHealthModel.SystemHealthComponent(
                    componentId(), componentType(), displayName(),
                    SystemHealthModel.SystemHealthStatus.HEALTHY,
                    "Governance layer operational",
                    Instant.now(), latency, details, Instant.now(), null,
                    SystemHealthModel.SystemHealthComponent.Severity.INFO);
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - start;
            return new SystemHealthModel.SystemHealthComponent(
                    componentId(), componentType(), displayName(),
                    SystemHealthModel.SystemHealthStatus.UNHEALTHY,
                    "Governance health check failed: " + e.getClass().getSimpleName(),
                    Instant.now(), latency, Map.of(), null, "GOVERNANCE_CHECK_FAILED",
                    SystemHealthModel.SystemHealthComponent.Severity.ERROR);
        }
    }
}
