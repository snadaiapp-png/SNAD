package com.sanad.platform.management.health;

import com.sanad.platform.management.application.CrmManagementIntegrationService;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * CRM health contributor (v20260816.1).
 *
 * <p>Adapts the existing {@link CrmManagementIntegrationService} into the
 * System Health contract. An empty tenant is HEALTHY. A SQL/schema failure
 * is UNHEALTHY.
 */
@Component
public class CrmSystemHealthContributor implements SystemHealthContributor {

    private final CrmManagementIntegrationService crmService;

    public CrmSystemHealthContributor(@Lazy CrmManagementIntegrationService crmService) {
        this.crmService = crmService;
    }

    @Override
    public String componentId() { return "crm"; }

    @Override
    public String componentType() { return "MODULE"; }

    @Override
    public String displayName() { return "CRM Module"; }

    @Override
    public SystemHealthModel.SystemHealthComponent checkHealth(UUID tenantId) {
        long start = System.currentTimeMillis();
        Map<String, Object> details = new LinkedHashMap<>();
        try {
            var overview = crmService.getCrmOverview(tenantId);
            long latency = System.currentTimeMillis() - start;
            details.put("totalAccounts", overview.getOrDefault("totalAccounts", 0));
            details.put("totalOpportunities", overview.getOrDefault("totalOpportunities", 0));
            details.put("activeAccounts", overview.getOrDefault("activeAccounts", 0));
            return new SystemHealthModel.SystemHealthComponent(
                    componentId(), componentType(), displayName(),
                    SystemHealthModel.SystemHealthStatus.HEALTHY,
                    "CRM queries successful",
                    Instant.now(), latency, details, Instant.now(), null,
                    SystemHealthModel.SystemHealthComponent.Severity.INFO);
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - start;
            return new SystemHealthModel.SystemHealthComponent(
                    componentId(), componentType(), displayName(),
                    SystemHealthModel.SystemHealthStatus.UNHEALTHY,
                    "CRM health check failed: " + e.getClass().getSimpleName(),
                    Instant.now(), latency, Map.of(), null, "CRM_CHECK_FAILED",
                    SystemHealthModel.SystemHealthComponent.Severity.ERROR);
        }
    }
}
