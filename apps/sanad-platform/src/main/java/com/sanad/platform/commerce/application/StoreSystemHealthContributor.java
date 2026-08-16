package com.sanad.platform.commerce.application;

import com.sanad.platform.management.health.SystemHealthContributor;
import com.sanad.platform.management.health.SystemHealthModel;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Stores System Health Contributor (v20260816.5).
 *
 * <p>Auto-discovers via {@code SystemHealthContributorRegistry} (Spring List
 * injection). Returns a healthy component as long as the commerce tables are
 * reachable. The {@code details} map carries the same KPI counts surfaced by
 * the governance adapter.
 */
@Component
public class StoreSystemHealthContributor implements SystemHealthContributor {

    private final JdbcTemplate jdbc;

    public StoreSystemHealthContributor(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override public String componentId() { return "stores"; }
    @Override public String componentType() { return "MODULE"; }
    @Override public String displayName() { return "Stores / E-Commerce"; }

    @Override
    public SystemHealthModel.SystemHealthComponent checkHealth(UUID tenantId) {
        long start = System.currentTimeMillis();
        Map<String, Object> details = new LinkedHashMap<>();
        try {
            details.put("totalStores", countFor(tenantId, "SELECT COUNT(*) FROM commerce_stores WHERE tenant_id = ?"));
            details.put("activeStores", countFor(tenantId, "SELECT COUNT(*) FROM commerce_stores WHERE tenant_id = ? AND status = 'ACTIVE'"));
            details.put("publishedProducts", countFor(tenantId, "SELECT COUNT(*) FROM commerce_products WHERE tenant_id = ? AND status = 'PUBLISHED'"));
            details.put("activeCarts", countFor(tenantId, "SELECT COUNT(*) FROM commerce_carts WHERE tenant_id = ? AND status = 'ACTIVE'"));
            details.put("totalOrders", countFor(tenantId, "SELECT COUNT(*) FROM commerce_orders WHERE tenant_id = ?"));
            details.put("paidOrders", countFor(tenantId, "SELECT COUNT(*) FROM commerce_orders WHERE tenant_id = ? AND payment_status = 'PAID'"));
            long latency = System.currentTimeMillis() - start;
            return new SystemHealthModel.SystemHealthComponent(
                    componentId(), componentType(), displayName(),
                    SystemHealthModel.SystemHealthStatus.HEALTHY,
                    "Stores / E-Commerce platform operational",
                    Instant.now(), latency, details, Instant.now(), null,
                    SystemHealthModel.SystemHealthComponent.Severity.INFO);
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - start;
            return new SystemHealthModel.SystemHealthComponent(
                    componentId(), componentType(), displayName(),
                    SystemHealthModel.SystemHealthStatus.UNHEALTHY,
                    "Stores health check failed: " + e.getClass().getSimpleName(),
                    Instant.now(), latency, Map.of(), null, "STORE_CHECK_FAILED",
                    SystemHealthModel.SystemHealthComponent.Severity.ERROR);
        }
    }

    private int countFor(UUID tenantId, String sql) {
        if (tenantId == null) return 0;
        Integer v = jdbc.queryForObject(sql, Integer.class, tenantId);
        return v != null ? v : 0;
    }
}
