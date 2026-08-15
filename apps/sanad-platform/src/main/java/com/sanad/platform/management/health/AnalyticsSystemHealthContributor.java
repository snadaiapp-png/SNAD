package com.sanad.platform.management.health;

import com.sanad.platform.management.application.AnalyticsManagementIntegrationService;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Analytics health contributor (v20260816.1).
 */
@Component
public class AnalyticsSystemHealthContributor implements SystemHealthContributor {

    private final AnalyticsManagementIntegrationService analyticsService;

    public AnalyticsSystemHealthContributor(@Lazy AnalyticsManagementIntegrationService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @Override public String componentId() { return "analytics"; }
    @Override public String componentType() { return "MODULE"; }
    @Override public String displayName() { return "Analytics Module"; }

    @Override
    public SystemHealthModel.SystemHealthComponent checkHealth(UUID tenantId) {
        long start = System.currentTimeMillis();
        Map<String, Object> details = new LinkedHashMap<>();
        try {
            var overview = analyticsService.getAnalyticsOverview(tenantId);
            long latency = System.currentTimeMillis() - start;
            details.put("available", true);
            return new SystemHealthModel.SystemHealthComponent(
                    componentId(), componentType(), displayName(),
                    SystemHealthModel.SystemHealthStatus.HEALTHY,
                    "Analytics queries successful",
                    Instant.now(), latency, details, Instant.now(), null,
                    SystemHealthModel.SystemHealthComponent.Severity.INFO);
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - start;
            return new SystemHealthModel.SystemHealthComponent(
                    componentId(), componentType(), displayName(),
                    SystemHealthModel.SystemHealthStatus.UNHEALTHY,
                    "Analytics health check failed: " + e.getClass().getSimpleName(),
                    Instant.now(), latency, Map.of(), null, "ANALYTICS_CHECK_FAILED",
                    SystemHealthModel.SystemHealthComponent.Severity.ERROR);
        }
    }
}
