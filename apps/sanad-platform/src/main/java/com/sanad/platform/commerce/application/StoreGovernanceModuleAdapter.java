package com.sanad.platform.commerce.application;

import com.sanad.platform.management.application.ManagementGovernanceModuleContract;
import com.sanad.platform.module.entitlement.EntitlementResolver;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Store / Commerce Governance Module Adapter (v20260816.5).
 *
 * <p>Auto-discovers via {@code ManagementGovernanceModuleRegistry} (Spring
 * List injection). The {@link #moduleCode()} returns {@code "ECOMMERCE_CX"}
 * — matching the module already registered by {@code V20260814_1}.
 *
 * <p>Provides KPIs (totalStores, activeStores, totalProducts,
 * publishedProducts, totalCollections, activeCarts, totalOrders, paidOrders)
 * for inclusion in the Senior Management command center dashboard.
 */
@Service
public class StoreGovernanceModuleAdapter implements ManagementGovernanceModuleContract {

    private final JdbcTemplate jdbc;
    private final EntitlementResolver entitlementResolver;

    public StoreGovernanceModuleAdapter(JdbcTemplate jdbc,
                                          @Lazy EntitlementResolver entitlementResolver) {
        this.jdbc = jdbc;
        this.entitlementResolver = entitlementResolver;
    }

    @Override public String moduleCode() { return "ECOMMERCE_CX"; }
    @Override public String displayName() { return "Stores / E-Commerce"; }

    @Override
    public boolean isEnabled(UUID tenantId) {
        try { return entitlementResolver.isModuleEnabled(tenantId, "ECOMMERCE_CX"); }
        catch (Exception e) { return false; }
    }

    @Override
    public ModuleHealthStatus healthStatus(UUID tenantId) {
        try {
            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM commerce_stores WHERE tenant_id = ?", Integer.class, tenantId);
            return count != null ? ModuleHealthStatus.HEALTHY : ModuleHealthStatus.UNAVAILABLE;
        } catch (Exception e) { return ModuleHealthStatus.UNAVAILABLE; }
    }

    @Override
    public List<String> capabilities(UUID tenantId) {
        return List.of(
                "ECOMMERCE.VIEW", "ECOMMERCE.WRITE", "ECOMMERCE.PUBLISH",
                "ECOMMERCE.ADMIN", "ECOMMERCE.ORDER_MANAGE");
    }

    @Override
    public Map<String, Object> kpiSummary(UUID tenantId) {
        Map<String, Object> m = new LinkedHashMap<>();
        try {
            m.put("totalStores", countFor(tenantId, "SELECT COUNT(*) FROM commerce_stores WHERE tenant_id = ?"));
            m.put("activeStores", countFor(tenantId, "SELECT COUNT(*) FROM commerce_stores WHERE tenant_id = ? AND status = 'ACTIVE'"));
            m.put("totalProducts", countFor(tenantId, "SELECT COUNT(*) FROM commerce_products WHERE tenant_id = ?"));
            m.put("publishedProducts", countFor(tenantId, "SELECT COUNT(*) FROM commerce_products WHERE tenant_id = ? AND status = 'PUBLISHED'"));
            m.put("totalCollections", countFor(tenantId, "SELECT COUNT(*) FROM commerce_collections WHERE tenant_id = ?"));
            m.put("activeCarts", countFor(tenantId, "SELECT COUNT(*) FROM commerce_carts WHERE tenant_id = ? AND status = 'ACTIVE'"));
            m.put("totalOrders", countFor(tenantId, "SELECT COUNT(*) FROM commerce_orders WHERE tenant_id = ?"));
            m.put("paidOrders", countFor(tenantId, "SELECT COUNT(*) FROM commerce_orders WHERE tenant_id = ? AND payment_status = 'PAID'"));
            m.put("activeDomains", countFor(tenantId, "SELECT COUNT(*) FROM commerce_store_domains WHERE tenant_id = ? AND activation_status = 'ACTIVE'"));
        } catch (Exception e) { m.put("_error", e.getClass().getSimpleName()); }
        return m;
    }

    @Override
    public Map<String, Object> operationalSummary(UUID tenantId) { return kpiSummary(tenantId); }

    @Override public int openAlertsCount(UUID tenantId) { return 0; }
    @Override public int openRisksCount(UUID tenantId) { return 0; }
    @Override public int openIssuesCount(UUID tenantId) { return 0; }
    @Override public SlaState slaState(UUID tenantId) { return SlaState.NOT_APPLICABLE; }

    @Override
    public Map<String, Object> metadata() {
        return Map.of("version", "1.0", "maturity", "GA", "since", "V20260816.5");
    }

    private int countFor(UUID tenantId, String sql) {
        Integer v = jdbc.queryForObject(sql, Integer.class, tenantId);
        return v != null ? v : 0;
    }
}
