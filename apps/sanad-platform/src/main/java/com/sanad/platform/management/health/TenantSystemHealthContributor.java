package com.sanad.platform.management.health;

import com.sanad.platform.management.application.GovernanceConfigurationService;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Tenant health contributor (v20260816.1).
 *
 * <p>Reports the health of the current tenant: tenant status, module
 * entitlement health, configuration availability. Does NOT expose
 * cross-tenant information.
 */
@Component
public class TenantSystemHealthContributor implements SystemHealthContributor {

    private final JdbcTemplate jdbc;
    private final GovernanceConfigurationService governanceConfigService;

    public TenantSystemHealthContributor(
            JdbcTemplate jdbc,
            @Lazy GovernanceConfigurationService governanceConfigService) {
        this.jdbc = jdbc;
        this.governanceConfigService = governanceConfigService;
    }

    @Override public String componentId() { return "tenant"; }
    @Override public String componentType() { return "GOVERNANCE"; }
    @Override public String displayName() { return "Tenant Health"; }

    @Override
    public SystemHealthModel.SystemHealthComponent checkHealth(UUID tenantId) {
        long start = System.currentTimeMillis();
        Map<String, Object> details = new LinkedHashMap<>();
        try {
            if (tenantId == null) {
                return new SystemHealthModel.SystemHealthComponent(
                        componentId(), componentType(), displayName(),
                        SystemHealthModel.SystemHealthStatus.UNKNOWN,
                        "No tenant context",
                        Instant.now(), System.currentTimeMillis() - start, Map.of(),
                        null, "NO_TENANT", SystemHealthModel.SystemHealthComponent.Severity.WARN);
            }
            String tenantStatus = jdbc.queryForObject(
                    "SELECT status FROM tenants WHERE id = ?",
                    String.class, tenantId);
            details.put("tenantStatus", tenantStatus != null ? tenantStatus : "UNKNOWN");
            int configCount = governanceConfigService.list(tenantId).size();
            details.put("governanceConfigCount", configCount);
            long latency = System.currentTimeMillis() - start;
            SystemHealthModel.SystemHealthStatus status = "ACTIVE".equals(tenantStatus)
                    ? SystemHealthModel.SystemHealthStatus.HEALTHY
                    : SystemHealthModel.SystemHealthStatus.DEGRADED;
            return new SystemHealthModel.SystemHealthComponent(
                    componentId(), componentType(), displayName(), status,
                    "Tenant " + tenantStatus,
                    Instant.now(), latency, details, Instant.now(), null,
                    SystemHealthModel.SystemHealthComponent.Severity.INFO);
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - start;
            return new SystemHealthModel.SystemHealthComponent(
                    componentId(), componentType(), displayName(),
                    SystemHealthModel.SystemHealthStatus.UNHEALTHY,
                    "Tenant health check failed: " + e.getClass().getSimpleName(),
                    Instant.now(), latency, Map.of(), null, "TENANT_CHECK_FAILED",
                    SystemHealthModel.SystemHealthComponent.Severity.ERROR);
        }
    }
}
