package com.sanad.platform.erp.application;

import com.sanad.platform.management.application.ManagementGovernanceModuleContract;
import com.sanad.platform.module.entitlement.EntitlementResolver;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * ERP Governance Module Adapter (v20260816.7).
 *
 * <p>Auto-discovers via {@code ManagementGovernanceModuleRegistry} (Spring
 * List injection). The {@link #moduleCode()} returns {@code "ERP"} —
 * matching the module already registered by {@code V20260814_1}.
 *
 * <p>Surfaces ERP KPIs (item count, warehouse count, low-stock count,
 * pending POs, pending requisitions, inventory summary) for inclusion
 * in the Senior Management command center dashboard.
 */
@Service
public class ErpGovernanceModuleAdapter implements ManagementGovernanceModuleContract {

    private final JdbcTemplate jdbc;
    private final EntitlementResolver entitlementResolver;

    public ErpGovernanceModuleAdapter(JdbcTemplate jdbc,
                                        @Lazy EntitlementResolver entitlementResolver) {
        this.jdbc = jdbc;
        this.entitlementResolver = entitlementResolver;
    }

    @Override public String moduleCode() { return "ERP"; }
    @Override public String displayName() { return "ERP Core Platform"; }

    @Override
    public boolean isEnabled(UUID tenantId) {
        try { return entitlementResolver.isModuleEnabled(tenantId, "ERP"); }
        catch (Exception e) { return false; }
    }

    @Override
    public ModuleHealthStatus healthStatus(UUID tenantId) {
        try {
            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM erp_items WHERE tenant_id = ?", Integer.class, tenantId);
            return count != null ? ModuleHealthStatus.HEALTHY : ModuleHealthStatus.UNAVAILABLE;
        } catch (Exception e) { return ModuleHealthStatus.UNAVAILABLE; }
    }

    @Override
    public List<String> capabilities(UUID tenantId) {
        return List.of(
                "ERP.VIEW", "ERP.WRITE", "ERP.ADMIN",
                "ERP.APPROVE", "ERP.INVENTORY", "ERP.PROCUREMENT");
    }

    @Override
    public Map<String, Object> kpiSummary(UUID tenantId) {
        Map<String, Object> m = new LinkedHashMap<>();
        try {
            m.put("totalItems", countFor(tenantId, "SELECT COUNT(*) FROM erp_items WHERE tenant_id = ?"));
            m.put("activeItems", countFor(tenantId, "SELECT COUNT(*) FROM erp_items WHERE tenant_id = ? AND status = 'ACTIVE'"));
            m.put("totalWarehouses", countFor(tenantId, "SELECT COUNT(*) FROM erp_warehouses WHERE tenant_id = ?"));
            m.put("totalSuppliers", countFor(tenantId, "SELECT COUNT(*) FROM erp_suppliers WHERE tenant_id = ?"));
            m.put("lowStockItems", countFor(tenantId,
                    "SELECT COUNT(DISTINCT i.id) FROM erp_items i "
                            + "JOIN erp_inventory_balances b ON b.tenant_id = i.tenant_id AND b.item_id = i.id "
                            + "WHERE i.tenant_id = ? AND i.track_inventory = TRUE AND i.reorder_level > 0 "
                            + "AND (b.on_hand - b.reserved) <= i.reorder_level"));
            m.put("pendingRequisitions", countFor(tenantId,
                    "SELECT COUNT(*) FROM erp_purchase_requisitions WHERE tenant_id = ? AND status IN ('DRAFT','SUBMITTED')"));
            m.put("pendingPurchaseOrders", countFor(tenantId,
                    "SELECT COUNT(*) FROM erp_purchase_orders WHERE tenant_id = ? AND status IN ('DRAFT','SUBMITTED','APPROVED','SENT')"));
            m.put("totalInventoryValue", inventoryValue(tenantId));
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
        return Map.of("version", "1.0", "maturity", "GA", "since", "V20260816.7");
    }

    // ===== Helpers =====
    private int countFor(UUID tenantId, String sql) {
        try {
            Integer v = jdbc.queryForObject(sql, Integer.class, tenantId);
            return v != null ? v : 0;
        } catch (Exception e) { return 0; }
    }

    private BigDecimal inventoryValue(UUID tenantId) {
        try {
            BigDecimal v = jdbc.queryForObject(
                    "SELECT COALESCE(SUM(b.on_hand * COALESCE(po.unit_cost, 0)), 0) "
                            + "FROM erp_inventory_balances b "
                            + "LEFT JOIN LATERAL ("
                            + "  SELECT poi.unit_cost FROM erp_purchase_order_items poi "
                            + "  JOIN erp_purchase_orders po ON po.tenant_id = poi.tenant_id AND po.id = poi.po_id "
                            + "  WHERE poi.tenant_id = b.tenant_id AND poi.item_id = b.item_id "
                            + "  AND po.status IN ('APPROVED','SENT','PARTIALLY_RECEIVED','RECEIVED','CLOSED') "
                            + "  ORDER BY poi.created_at DESC LIMIT 1"
                            + ") po ON TRUE "
                            + "WHERE b.tenant_id = ?",
                    BigDecimal.class, tenantId);
            return v != null ? v : BigDecimal.ZERO;
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }
}
