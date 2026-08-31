package com.sanad.platform.subscription.entitlement;

import com.sanad.platform.module.entitlement.EntitlementResolver;
import com.sanad.platform.module.entitlement.ModuleCapabilityContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Item-aware entitlement resolution — the generalization of the existing
 * plan-derived {@link EntitlementResolver}, NOT a competing engine.
 *
 * <p>Resolution chain (extends the documented base chain):
 * <pre>
 *   Subscription Items (ADD_ON / METERED, ACTIVE)
 *         ↓ product_entitlements
 *   merge over plan-derived context
 *         ↓
 *   Tenant Effective Entitlements
 * </pre>
 *
 * <p>Merge semantics: module enablement ORs, boolean capabilities OR,
 * numeric limits and quotas take the maximum. When a subscription carries no
 * item entitlements the result is exactly the base plan-derived context
 * (byte-identical, no regression — proven by tests).
 */
@Service
public class ItemAwareEntitlementResolver {

    private final EntitlementResolver baseResolver;
    private final ItemEntitlementRepository itemEntitlementRepository;

    public ItemAwareEntitlementResolver(EntitlementResolver baseResolver,
                                        ItemEntitlementRepository itemEntitlementRepository) {
        this.baseResolver = baseResolver;
        this.itemEntitlementRepository = itemEntitlementRepository;
    }

    /**
     * @param moduleId registry id of the module (needed to locate item rows;
     *                 the base context does not expose it)
     */
    @Transactional(readOnly = true)
    public ModuleCapabilityContext getEffectiveEntitlements(UUID tenantId, String moduleCode, UUID moduleId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        ModuleCapabilityContext base = baseResolver.getEffectiveEntitlements(tenantId, moduleCode);
        if (base.subscriptionId() == null || moduleId == null) {
            return base;
        }
        var rows = itemEntitlementRepository.findBySubscriptionIdAndModuleId(
                base.subscriptionId(), moduleId);
        if (rows.isEmpty()) {
            return base;
        }

        boolean moduleEnabled = base.isModuleEnabled()
                || rows.stream().anyMatch(ProductEntitlementRow::moduleEnabled);
        if (!moduleEnabled) {
            return ModuleCapabilityContext.denied(tenantId, base.subscriptionId(), base.moduleCode());
        }

        Map<String, Boolean> capabilities = new HashMap<>(base.capabilities());
        Map<String, Long> limits = new HashMap<>(base.limits());
        Map<String, ModuleCapabilityContext.QuotaValue> quotas = new HashMap<>(base.quotas());

        for (ProductEntitlementRow row : rows) {
            if (row.capabilityCode() == null) {
                continue;
            }
            if (row.booleanValue() != null) {
                capabilities.merge(row.capabilityCode(), row.booleanValue(), Boolean::logicalOr);
            }
            if (row.limitValue() != null) {
                limits.merge(row.capabilityCode(), row.limitValue(), Long::max);
            }
            if (row.quotaValue() != null) {
                ModuleCapabilityContext.QuotaValue incoming =
                        new ModuleCapabilityContext.QuotaValue(
                                row.quotaValue(),
                                row.quotaPeriod() != null ? row.quotaPeriod() : "MONTHLY");
                quotas.merge(row.capabilityCode(), incoming,
                        (a, b) -> b.value() >= a.value() ? b : a);
            }
        }

        return ModuleCapabilityContext.allowed(
                tenantId, base.subscriptionId(), base.planId(), base.moduleCode(),
                capabilities, limits, quotas);
    }
}
