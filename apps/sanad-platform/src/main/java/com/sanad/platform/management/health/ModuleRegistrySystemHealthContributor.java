package com.sanad.platform.management.health;

import com.sanad.platform.management.application.ModuleGovernanceService;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Module Registry health contributor (v20260816.1).
 *
 * <p>Reports the health of the module registry itself. A disabled module
 * (DISABLED_BY_PLAN) does NOT mean unhealthy — it's intentionally disabled.
 */
@Component
public class ModuleRegistrySystemHealthContributor implements SystemHealthContributor {

    private final ModuleGovernanceService moduleGovernanceService;

    public ModuleRegistrySystemHealthContributor(@Lazy ModuleGovernanceService moduleGovernanceService) {
        this.moduleGovernanceService = moduleGovernanceService;
    }

    @Override public String componentId() { return "module-registry"; }
    @Override public String componentType() { return "GOVERNANCE"; }
    @Override public String displayName() { return "Module Registry"; }

    @Override
    public SystemHealthModel.SystemHealthComponent checkHealth(UUID tenantId) {
        long start = System.currentTimeMillis();
        Map<String, Object> details = new LinkedHashMap<>();
        try {
            List<Map<String, Object>> statuses = moduleGovernanceService.getModuleStatuses();
            long latency = System.currentTimeMillis() - start;
            long enabledCount = statuses.stream().filter(s -> Boolean.TRUE.equals(s.get("enabled"))).count();
            long disabledCount = statuses.size() - enabledCount;
            details.put("totalModules", statuses.size());
            details.put("enabledModules", enabledCount);
            details.put("disabledModules", disabledCount);
            return new SystemHealthModel.SystemHealthComponent(
                    componentId(), componentType(), displayName(),
                    SystemHealthModel.SystemHealthStatus.HEALTHY,
                    "Module registry operational",
                    Instant.now(), latency, details, Instant.now(), null,
                    SystemHealthModel.SystemHealthComponent.Severity.INFO);
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - start;
            return new SystemHealthModel.SystemHealthComponent(
                    componentId(), componentType(), displayName(),
                    SystemHealthModel.SystemHealthStatus.UNHEALTHY,
                    "Module registry check failed: " + e.getClass().getSimpleName(),
                    Instant.now(), latency, Map.of(), null, "REGISTRY_CHECK_FAILED",
                    SystemHealthModel.SystemHealthComponent.Severity.ERROR);
        }
    }
}
