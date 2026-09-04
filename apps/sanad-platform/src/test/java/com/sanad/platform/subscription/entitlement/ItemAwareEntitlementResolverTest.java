package com.sanad.platform.subscription.entitlement;

import com.sanad.platform.module.entitlement.EntitlementResolver;
import com.sanad.platform.module.entitlement.ModuleCapabilityContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ItemAwareEntitlementResolver}.
 *
 * <p>Proves two critical properties:
 * <ul>
 *   <li>ADD_ON / METERED subscription items merge additively into the
 *       plan-derived entitlement context (booleans OR, limits/quotas max)</li>
 *   <li>The plan-only path is byte-identical to the legacy resolver when no
 *       item entitlements exist (no regression)</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ItemAwareEntitlementResolver — item-derived capability merge")
class ItemAwareEntitlementResolverTest {

    @Mock
    private EntitlementResolver baseResolver;
    @Mock
    private ItemEntitlementRepository itemEntitlementRepository;

    private ItemAwareEntitlementResolver resolver;

    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID SUBSCRIPTION_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID PLAN_ID = UUID.fromString("c3000000-0000-0000-0000-000000000001");
    private static final UUID MODULE_ID = UUID.fromString("11111111-0000-0000-0000-000000000001");

    @BeforeEach
    void setUp() {
        resolver = new ItemAwareEntitlementResolver(baseResolver, itemEntitlementRepository);
    }

    private ModuleCapabilityContext planContext() {
        return ModuleCapabilityContext.allowed(
                TENANT_ID, SUBSCRIPTION_ID, PLAN_ID, "CRM",
                Map.of("CRM.ADVANCED_PIPELINE", false),
                Map.of("CRM.MAX_CONTACTS", 1000L),
                Map.of("CRM.MONTHLY_API_CALLS", new ModuleCapabilityContext.QuotaValue(50_000L, "MONTHLY")));
    }

    @Test
    @DisplayName("merges add-on limits by taking the maximum")
    void mergesLimitsByMax() {
        when(baseResolver.getEffectiveEntitlements(TENANT_ID, "CRM")).thenReturn(planContext());
        when(itemEntitlementRepository.findBySubscriptionIdAndModuleId(SUBSCRIPTION_ID, MODULE_ID))
                .thenReturn(List.of(row("CRM.MAX_CONTACTS", null, 5000L, null)));

        ModuleCapabilityContext merged = resolver.getEffectiveEntitlements(TENANT_ID, "CRM", MODULE_ID);

        assertThat(merged.getLimit("CRM.MAX_CONTACTS")).isEqualTo(5000L);
        assertThat(merged.getQuota("CRM.MONTHLY_API_CALLS")).isEqualTo(
                new ModuleCapabilityContext.QuotaValue(50_000L, "MONTHLY"));
    }

    @Test
    @DisplayName("merges add-on boolean capabilities with OR semantics")
    void mergesBooleansWithOr() {
        when(baseResolver.getEffectiveEntitlements(TENANT_ID, "CRM")).thenReturn(planContext());
        when(itemEntitlementRepository.findBySubscriptionIdAndModuleId(SUBSCRIPTION_ID, MODULE_ID))
                .thenReturn(List.of(row("CRM.ADVANCED_PIPELINE", true, null, null)));

        ModuleCapabilityContext merged = resolver.getEffectiveEntitlements(TENANT_ID, "CRM", MODULE_ID);

        assertThat(merged.hasCapability("CRM.ADVANCED_PIPELINE")).isTrue();
    }

    @Test
    @DisplayName("an add-on can enable a module the plan does not include")
    void addOnEnablesModule() {
        when(baseResolver.getEffectiveEntitlements(TENANT_ID, "CRM"))
                .thenReturn(ModuleCapabilityContext.denied(TENANT_ID, SUBSCRIPTION_ID, "CRM"));
        when(itemEntitlementRepository.findBySubscriptionIdAndModuleId(SUBSCRIPTION_ID, MODULE_ID))
                .thenReturn(List.of(rowWithModuleEnabled("CRM.MAX_CONTACTS", 250L)));

        ModuleCapabilityContext merged = resolver.getEffectiveEntitlements(TENANT_ID, "CRM", MODULE_ID);

        assertThat(merged.isModuleEnabled()).isTrue();
        assertThat(merged.getLimit("CRM.MAX_CONTACTS")).isEqualTo(250L);
    }

    @Test
    @DisplayName("no item entitlements: result is identical to the plan-only resolver (no regression)")
    void planOnlyPathUnchanged() {
        ModuleCapabilityContext planCtx = planContext();
        when(baseResolver.getEffectiveEntitlements(TENANT_ID, "CRM")).thenReturn(planCtx);
        when(itemEntitlementRepository.findBySubscriptionIdAndModuleId(SUBSCRIPTION_ID, MODULE_ID))
                .thenReturn(List.of());

        ModuleCapabilityContext merged = resolver.getEffectiveEntitlements(TENANT_ID, "CRM", MODULE_ID);

        assertThat(merged.capabilities()).isEqualTo(planCtx.capabilities());
        assertThat(merged.limits()).isEqualTo(planCtx.limits());
        assertThat(merged.quotas()).isEqualTo(planCtx.quotas());
        assertThat(merged.isModuleEnabled()).isTrue();
    }

    @Test
    @DisplayName("denied plan context with no enabling items stays denied")
    void deniedStaysDenied() {
        ModuleCapabilityContext denied = ModuleCapabilityContext.denied(TENANT_ID, SUBSCRIPTION_ID, "CRM");
        when(baseResolver.getEffectiveEntitlements(TENANT_ID, "HRM")).thenReturn(denied);
        when(itemEntitlementRepository.findBySubscriptionIdAndModuleId(SUBSCRIPTION_ID, MODULE_ID))
                .thenReturn(List.of());

        ModuleCapabilityContext merged = resolver.getEffectiveEntitlements(TENANT_ID, "HRM", MODULE_ID);

        assertThat(merged.isModuleEnabled()).isFalse();
        assertThat(merged.getLimit("CRM.MAX_CONTACTS")).isEqualTo(0L);
    }

    private ProductEntitlementRow row(String capabilityCode, Boolean booleanValue,
                                      Long limitValue, Long quotaValue) {
        return new ProductEntitlementRow(false, capabilityCode, booleanValue, limitValue,
                quotaValue, quotaValue != null ? "MONTHLY" : null);
    }

    private ProductEntitlementRow rowWithModuleEnabled(String capabilityCode, Long limitValue) {
        return new ProductEntitlementRow(true, capabilityCode, null, limitValue, null, null);
    }
}
