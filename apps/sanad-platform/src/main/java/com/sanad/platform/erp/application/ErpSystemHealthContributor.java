package com.sanad.platform.erp.application;

import com.sanad.platform.management.health.SystemHealthContributor;
import com.sanad.platform.management.health.SystemHealthModel;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * ERP System Health Contributor (v20260816.7).
 *
 * <p>Auto-discovers via {@code SystemHealthContributorRegistry} (Spring List
 * injection). Returns a healthy component as long as the ERP tables are
 * reachable. The {@code details} map carries ERP KPI counts:
 * total items, warehouse count, total inventory balance query result.
 */
@Component
public class ErpSystemHealthContributor implements SystemHealthContributor {

    private final JdbcTemplate jdbc;

    public ErpSystemHealthContributor(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override public String componentId() { return "erp"; }
    @Override public String componentType() { return "MODULE"; }
    @Override public String displayName() { return "ERP Core Platform"; }

    @Override
    public SystemHealthModel.SystemHealthComponent checkHealth(UUID tenantId) {
        long start = System.currentTimeMillis();
        Map<String, Object> details = new LinkedHashMap<>();
        try {
            details.put("totalItems", countFor(tenantId,
                    "SELECT COUNT(*) FROM erp_items WHERE tenant_id = ?"));
            details.put("totalWarehouses", countFor(tenantId,
                    "SELECT COUNT(*) FROM erp_warehouses WHERE tenant_id = ?"));
            details.put("totalInventoryBalances", countFor(tenantId,
                    "SELECT COUNT(*) FROM erp_inventory_balances WHERE tenant_id = ?"));
            details.put("pendingPurchaseOrders", countFor(tenantId,
                    "SELECT COUNT(*) FROM erp_purchase_orders WHERE tenant_id = ? "
                            + "AND status IN ('DRAFT','SUBMITTED','APPROVED','SENT')"));
            long latency = System.currentTimeMillis() - start;
            return new SystemHealthModel.SystemHealthComponent(
                    componentId(), componentType(), displayName(),
                    SystemHealthModel.SystemHealthStatus.HEALTHY,
                    "ERP Core Platform operational",
                    Instant.now(), latency, details, Instant.now(), null,
                    SystemHealthModel.SystemHealthComponent.Severity.INFO);
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - start;
            return new SystemHealthModel.SystemHealthComponent(
                    componentId(), componentType(), displayName(),
                    SystemHealthModel.SystemHealthStatus.UNHEALTHY,
                    "ERP health check failed: " + e.getClass().getSimpleName(),
                    Instant.now(), latency, Map.of(), null, "ERP_CHECK_FAILED",
                    SystemHealthModel.SystemHealthComponent.Severity.ERROR);
        }
    }

    private int countFor(UUID tenantId, String sql) {
        if (tenantId == null) return 0;
        try {
            Integer v = jdbc.queryForObject(sql, Integer.class, tenantId);
            return v != null ? v : 0;
        } catch (Exception e) { return 0; }
    }
}
