package com.sanad.platform.management.health;

import com.sanad.platform.management.application.FinanceManagementIntegrationService;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Finance health contributor (v20260816.1).
 */
@Component
public class FinanceSystemHealthContributor implements SystemHealthContributor {

    private final FinanceManagementIntegrationService financeService;

    public FinanceSystemHealthContributor(@Lazy FinanceManagementIntegrationService financeService) {
        this.financeService = financeService;
    }

    @Override public String componentId() { return "finance"; }
    @Override public String componentType() { return "MODULE"; }
    @Override public String displayName() { return "Finance Module"; }

    @Override
    public SystemHealthModel.SystemHealthComponent checkHealth(UUID tenantId) {
        long start = System.currentTimeMillis();
        Map<String, Object> details = new LinkedHashMap<>();
        try {
            var overview = financeService.getOverview(tenantId);
            long latency = System.currentTimeMillis() - start;
            details.put("totalInvoices", overview.getOrDefault("totalInvoices", 0));
            details.put("totalPayments", overview.getOrDefault("totalPayments", 0));
            return new SystemHealthModel.SystemHealthComponent(
                    componentId(), componentType(), displayName(),
                    SystemHealthModel.SystemHealthStatus.HEALTHY,
                    "Finance queries successful",
                    Instant.now(), latency, details, Instant.now(), null,
                    SystemHealthModel.SystemHealthComponent.Severity.INFO);
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - start;
            return new SystemHealthModel.SystemHealthComponent(
                    componentId(), componentType(), displayName(),
                    SystemHealthModel.SystemHealthStatus.UNHEALTHY,
                    "Finance health check failed: " + e.getClass().getSimpleName(),
                    Instant.now(), latency, Map.of(), null, "FINANCE_CHECK_FAILED",
                    SystemHealthModel.SystemHealthComponent.Severity.ERROR);
        }
    }
}
