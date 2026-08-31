package com.sanad.platform.subscription.entitlement;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Loads {@link ProductEntitlementRow}s for a subscription + module by joining
 * ACTIVE ADD_ON/METERED subscription items with {@code product_entitlements}.
 */
@Repository
public class ItemEntitlementRepository {

    private final JdbcTemplate jdbc;

    public ItemEntitlementRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<ProductEntitlementRow> findBySubscriptionIdAndModuleId(UUID subscriptionId, UUID moduleId) {
        return jdbc.query("""
                        SELECT pe.module_enabled, pe.capability_code, pe.boolean_value,
                               pe.limit_value, pe.quota_value, pe.quota_period
                        FROM subscription_items si
                        JOIN product_entitlements pe ON pe.product_id = si.product_id
                        WHERE si.subscription_id = ?
                          AND si.status = 'ACTIVE'
                          AND si.item_type IN ('ADD_ON', 'METERED')
                          AND pe.module_id = ?
                        ORDER BY pe.created_at, pe.id
                        """,
                (rs, rowNum) -> new ProductEntitlementRow(
                        rs.getBoolean("module_enabled"),
                        rs.getString("capability_code"),
                        (Boolean) rs.getObject("boolean_value"),
                        (Long) rs.getObject("limit_value"),
                        (Long) rs.getObject("quota_value"),
                        rs.getString("quota_period")),
                subscriptionId, moduleId);
    }
}
