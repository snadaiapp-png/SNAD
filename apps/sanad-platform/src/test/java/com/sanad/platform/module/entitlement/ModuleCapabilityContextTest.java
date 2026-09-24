package com.sanad.platform.module.entitlement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ModuleCapabilityContext}.
 */
@DisplayName("ModuleCapabilityContext — unit tests")
class ModuleCapabilityContextTest {

    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID SUBSCRIPTION_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID PLAN_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");

    @Test
    @DisplayName("denied: creates context with moduleEnabled=false")
    void denied_createsDisabledContext() {
        ModuleCapabilityContext ctx = ModuleCapabilityContext.denied(TENANT_ID, SUBSCRIPTION_ID, "CRM");

        assertThat(ctx.tenantId()).isEqualTo(TENANT_ID);
        assertThat(ctx.subscriptionId()).isEqualTo(SUBSCRIPTION_ID);
        assertThat(ctx.moduleCode()).isEqualTo("CRM");
        assertThat(ctx.isModuleEnabled()).isFalse();
        assertThat(ctx.capabilities()).isEmpty();
        assertThat(ctx.limits()).isEmpty();
        assertThat(ctx.quotas()).isEmpty();
    }

    @Test
    @DisplayName("allowed: creates context with moduleEnabled=true and entitlements")
    void allowed_createsEnabledContext() {
        Map<String, Boolean> caps = Map.of("CRM.ADVANCED_PIPELINE", true);
        Map<String, Long> limits = Map.of("CRM.MAX_CONTACTS", 10000L);
        Map<String, ModuleCapabilityContext.QuotaValue> quotas = Map.of(
                "CRM.MONTHLY_API_CALLS",
                new ModuleCapabilityContext.QuotaValue(50000L, "MONTHLY"));

        ModuleCapabilityContext ctx = ModuleCapabilityContext.allowed(
                TENANT_ID, SUBSCRIPTION_ID, PLAN_ID, "CRM", caps, limits, quotas);

        assertThat(ctx.tenantId()).isEqualTo(TENANT_ID);
        assertThat(ctx.planId()).isEqualTo(PLAN_ID);
        assertThat(ctx.isModuleEnabled()).isTrue();
        assertThat(ctx.capabilities()).hasSize(1);
        assertThat(ctx.limits()).hasSize(1);
        assertThat(ctx.quotas()).hasSize(1);
    }

    @Test
    @DisplayName("hasCapability: returns true for enabled capability")
    void hasCapability_returnsTrueForEnabled() {
        ModuleCapabilityContext ctx = ModuleCapabilityContext.allowed(
                TENANT_ID, null, PLAN_ID, "CRM",
                Map.of("CRM.ADVANCED_PIPELINE", true),
                Map.of(), Map.of());

        assertThat(ctx.hasCapability("CRM.ADVANCED_PIPELINE")).isTrue();
    }

    @Test
    @DisplayName("hasCapability: returns false for disabled capability")
    void hasCapability_returnsFalseForDisabled() {
        ModuleCapabilityContext ctx = ModuleCapabilityContext.allowed(
                TENANT_ID, null, PLAN_ID, "CRM",
                Map.of("CRM.ADVANCED_PIPELINE", false),
                Map.of(), Map.of());

        assertThat(ctx.hasCapability("CRM.ADVANCED_PIPELINE")).isFalse();
    }

    @Test
    @DisplayName("hasCapability: returns false for missing capability")
    void hasCapability_returnsFalseForMissing() {
        ModuleCapabilityContext ctx = ModuleCapabilityContext.allowed(
                TENANT_ID, null, PLAN_ID, "CRM",
                Map.of(), Map.of(), Map.of());

        assertThat(ctx.hasCapability("CRM.ADVANCED_PIPELINE")).isFalse();
    }

    @Test
    @DisplayName("getLimit: returns limit value when set")
    void getLimit_returnsValueWhenSet() {
        ModuleCapabilityContext ctx = ModuleCapabilityContext.allowed(
                TENANT_ID, null, PLAN_ID, "CRM",
                Map.of(), Map.of("CRM.MAX_CONTACTS", 25000L), Map.of());

        assertThat(ctx.getLimit("CRM.MAX_CONTACTS")).isEqualTo(25000L);
    }

    @Test
    @DisplayName("getLimit: returns MAX_VALUE when limit is null")
    void getLimit_returnsMaxValueWhenNull() {
        ModuleCapabilityContext ctx = ModuleCapabilityContext.allowed(
                TENANT_ID, null, PLAN_ID, "CRM",
                Map.of(), Map.of(), Map.of());

        assertThat(ctx.getLimit("CRM.MAX_CONTACTS")).isEqualTo(Long.MAX_VALUE);
    }

    @Test
    @DisplayName("getLimit: returns 0 when module is disabled")
    void getLimit_returnsZeroWhenModuleDisabled() {
        ModuleCapabilityContext ctx = ModuleCapabilityContext.denied(TENANT_ID, null, "CRM");

        assertThat(ctx.getLimit("CRM.MAX_CONTACTS")).isZero();
    }

    @Test
    @DisplayName("getQuota: returns quota when set")
    void getQuota_returnsQuotaWhenSet() {
        ModuleCapabilityContext.QuotaValue quota = new ModuleCapabilityContext.QuotaValue(50000L, "MONTHLY");
        ModuleCapabilityContext ctx = ModuleCapabilityContext.allowed(
                TENANT_ID, null, PLAN_ID, "CRM",
                Map.of(), Map.of(), Map.of("CRM.MONTHLY_API_CALLS", quota));

        ModuleCapabilityContext.QuotaValue result = ctx.getQuota("CRM.MONTHLY_API_CALLS");

        assertThat(result).isNotNull();
        assertThat(result.value()).isEqualTo(50000L);
        assertThat(result.period()).isEqualTo("MONTHLY");
    }

    @Test
    @DisplayName("getQuota: returns null when module is disabled")
    void getQuota_returnsNullWhenModuleDisabled() {
        ModuleCapabilityContext ctx = ModuleCapabilityContext.denied(TENANT_ID, null, "CRM");

        assertThat(ctx.getQuota("CRM.MONTHLY_API_CALLS")).isNull();
    }

    @Test
    @DisplayName("QuotaValue: factory method creates instance")
    void quotaValue_factoryCreatesInstance() {
        ModuleCapabilityContext.QuotaValue q = ModuleCapabilityContext.QuotaValue.of(1000L, "DAILY");

        assertThat(q.value()).isEqualTo(1000L);
        assertThat(q.period()).isEqualTo("DAILY");
    }

    @Test
    @DisplayName("QuotaValue: null period throws NPE")
    void quotaValue_nullPeriodThrowsNpe() {
        try {
            new ModuleCapabilityContext.QuotaValue(100L, null);
            assertThat(false).as("Expected NullPointerException").isTrue();
        } catch (NullPointerException e) {
            // expected
        }
    }

    @Test
    @DisplayName("maps are immutable after construction")
    void maps_areImmutable() {
        ModuleCapabilityContext ctx = ModuleCapabilityContext.allowed(
                TENANT_ID, null, PLAN_ID, "CRM",
                Map.of("CAP", true), Map.of("LIM", 1L), Map.of());

        try {
            ctx.capabilities().put("NEW", true);
            assertThat(false).as("Expected UnsupportedOperationException").isTrue();
        } catch (UnsupportedOperationException e) {
            // expected — map is unmodifiable
        }
    }
}
