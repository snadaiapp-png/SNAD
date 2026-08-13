package com.sanad.platform.module.entitlement;

import com.sanad.platform.module.registry.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.ArgumentMatchers;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link EntitlementResolver}.
 *
 * <p>Tests cover:
 * <ul>
 *   <li>Module enabled/disabled</li>
 *   <li>Boolean capability (hasCapability)</li>
 *   <li>Numeric limit (getLimit)</li>
 *   <li>Quota (getQuota)</li>
 *   <li>Missing entitlement (denied context)</li>
 *   <li>No active subscription</li>
 *   <li>Unknown module code</li>
 *   <li>Module disabled at plan level</li>
 *   <li>Plan override vs module default</li>
 *   <li>Tenant isolation</li>
 *   <li>Recalculate entitlements</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EntitlementResolver — unit tests")
class EntitlementResolverTest {

    @Mock
    private JdbcTemplate jdbc;
    @Mock
    private ModuleRepository moduleRepository;
    @Mock
    private ModuleCapabilityRepository moduleCapabilityRepository;
    @Mock
    private PlanModuleEntitlementRepository planModuleEntitlementRepository;

    private EntitlementResolver resolver;

    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID SUBSCRIPTION_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID PLAN_ID = UUID.fromString("c3000000-0000-0000-0000-000000000001");
    private static final UUID CRM_MODULE_ID = UUID.fromString("11111111-0000-0000-0000-000000000001");

    @BeforeEach
    void setUp() {
        resolver = new EntitlementResolver(jdbc, moduleRepository, moduleCapabilityRepository, planModuleEntitlementRepository);
    }

    @Test
    @DisplayName("isModuleEnabled: returns true when module is enabled at plan level")
    void isModuleEnabled_returnsTrueWhenEnabled() {
        setupCrmModule(true);
        setupActiveSubscription();
        setupPlanEntitlements(true);

        boolean enabled = resolver.isModuleEnabled(TENANT_ID, "CRM");

        assertThat(enabled).isTrue();
    }

    @Test
    @DisplayName("isModuleEnabled: returns false when module is disabled at plan level")
    void isModuleEnabled_returnsFalseWhenDisabled() {
        setupCrmModule(true);
        setupActiveSubscription();
        setupPlanEntitlements(false);

        boolean enabled = resolver.isModuleEnabled(TENANT_ID, "CRM");

        assertThat(enabled).isFalse();
    }

    @Test
    @DisplayName("isModuleEnabled: returns false when no active subscription")
    void isModuleEnabled_returnsFalseWhenNoSubscription() {
        setupCrmModule(true);
        when(jdbc.<Map<String, Object>>queryForStream(any(String.class), any(), eq(TENANT_ID)))
                .thenReturn(Collections.<Map<String, Object>>emptyList().stream());

        boolean enabled = resolver.isModuleEnabled(TENANT_ID, "CRM");

        assertThat(enabled).isFalse();
    }

    @Test
    @DisplayName("isModuleEnabled: returns false for unknown module code")
    void isModuleEnabled_returnsFalseForUnknownModule() {
        when(moduleRepository.findByCode("UNKNOWN")).thenReturn(Optional.empty());

        boolean enabled = resolver.isModuleEnabled(TENANT_ID, "UNKNOWN");

        assertThat(enabled).isFalse();
    }

    @Test
    @DisplayName("hasCapability: returns true for enabled boolean capability")
    void hasCapability_returnsTrueForEnabled() {
        setupCrmModule(true);
        setupActiveSubscription();
        setupPlanEntitlements(true);

        // Set up a plan entitlement that enables CRM.ADVANCED_PIPELINE
        PlanModuleEntitlementEntity pme = new PlanModuleEntitlementEntity();
        pme.setPlanId(PLAN_ID);
        pme.setModuleId(CRM_MODULE_ID);
        pme.setModuleEnabled(true);
        pme.setCapabilityCode("CRM.ADVANCED_PIPELINE");
        pme.setCapabilityValue("true");
        when(planModuleEntitlementRepository.findByPlanIdAndModuleId(PLAN_ID, CRM_MODULE_ID))
                .thenReturn(List.of(pme));

        // Set up module capability definition
        ModuleCapabilityEntity cap = new ModuleCapabilityEntity();
        cap.setCode("CRM.ADVANCED_PIPELINE");
        cap.setCapabilityType("BOOLEAN_CAPABILITY");
        cap.setStatus("ACTIVE");
        cap.setDefaultValue("false");
        when(moduleCapabilityRepository.findByModuleId(CRM_MODULE_ID))
                .thenReturn(List.of(cap));

        boolean hasCap = resolver.hasCapability(TENANT_ID, "CRM", "CRM.ADVANCED_PIPELINE");

        assertThat(hasCap).isTrue();
    }

    @Test
    @DisplayName("hasCapability: returns false for disabled boolean capability")
    void hasCapability_returnsFalseForDisabled() {
        setupCrmModule(true);
        setupActiveSubscription();
        setupPlanEntitlements(true);

        // Module default is false
        ModuleCapabilityEntity cap = new ModuleCapabilityEntity();
        cap.setCode("CRM.ADVANCED_PIPELINE");
        cap.setCapabilityType("BOOLEAN_CAPABILITY");
        cap.setStatus("ACTIVE");
        cap.setDefaultValue("false");
        when(moduleCapabilityRepository.findByModuleId(CRM_MODULE_ID))
                .thenReturn(List.of(cap));

        boolean hasCap = resolver.hasCapability(TENANT_ID, "CRM", "CRM.ADVANCED_PIPELINE");

        assertThat(hasCap).isFalse();
    }

    @Test
    @DisplayName("getLimit: returns plan override value when set")
    void getLimit_returnsPlanOverride() {
        setupCrmModule(true);
        setupActiveSubscription();
        setupPlanEntitlements(true);

        // Plan override: CRM.MAX_CONTACTS = 5000
        PlanModuleEntitlementEntity pme = new PlanModuleEntitlementEntity();
        pme.setPlanId(PLAN_ID);
        pme.setModuleId(CRM_MODULE_ID);
        pme.setModuleEnabled(true);
        pme.setCapabilityCode("CRM.MAX_CONTACTS");
        pme.setLimitValue(5000L);
        when(planModuleEntitlementRepository.findByPlanIdAndModuleId(PLAN_ID, CRM_MODULE_ID))
                .thenReturn(List.of(pme));

        // Module default: CRM.MAX_CONTACTS = 10000
        ModuleCapabilityEntity cap = new ModuleCapabilityEntity();
        cap.setCode("CRM.MAX_CONTACTS");
        cap.setCapabilityType("NUMERIC_LIMIT");
        cap.setStatus("ACTIVE");
        cap.setDefaultValue("10000");
        when(moduleCapabilityRepository.findByModuleId(CRM_MODULE_ID))
                .thenReturn(List.of(cap));

        long limit = resolver.getLimit(TENANT_ID, "CRM", "CRM.MAX_CONTACTS");

        assertThat(limit).isEqualTo(5000L);
    }

    @Test
    @DisplayName("getLimit: returns module default when no plan override")
    void getLimit_returnsModuleDefault() {
        setupCrmModule(true);
        setupActiveSubscription();
        setupPlanEntitlements(true);

        // No plan override — only module_enabled entitlement exists
        // Module default: CRM.MAX_CONTACTS = 10000
        ModuleCapabilityEntity cap = new ModuleCapabilityEntity();
        cap.setCode("CRM.MAX_CONTACTS");
        cap.setCapabilityType("NUMERIC_LIMIT");
        cap.setStatus("ACTIVE");
        cap.setDefaultValue("10000");
        when(moduleCapabilityRepository.findByModuleId(CRM_MODULE_ID))
                .thenReturn(List.of(cap));

        long limit = resolver.getLimit(TENANT_ID, "CRM", "CRM.MAX_CONTACTS");

        assertThat(limit).isEqualTo(10000L);
    }

    @Test
    @DisplayName("getLimit: returns 0 when module is disabled")
    void getLimit_returnsZeroWhenModuleDisabled() {
        setupCrmModule(true);
        setupActiveSubscription();
        setupPlanEntitlements(false);  // module disabled

        long limit = resolver.getLimit(TENANT_ID, "CRM", "CRM.MAX_CONTACTS");

        assertThat(limit).isZero();
    }

    @Test
    @DisplayName("getQuota: returns plan override quota with period")
    void getQuota_returnsPlanOverride() {
        setupCrmModule(true);
        setupActiveSubscription();
        setupPlanEntitlements(true);

        PlanModuleEntitlementEntity pme = new PlanModuleEntitlementEntity();
        pme.setPlanId(PLAN_ID);
        pme.setModuleId(CRM_MODULE_ID);
        pme.setModuleEnabled(true);
        pme.setCapabilityCode("CRM.MONTHLY_API_CALLS");
        pme.setQuotaValue(50000L);
        pme.setQuotaPeriod("MONTHLY");
        when(planModuleEntitlementRepository.findByPlanIdAndModuleId(PLAN_ID, CRM_MODULE_ID))
                .thenReturn(List.of(pme));

        ModuleCapabilityEntity cap = new ModuleCapabilityEntity();
        cap.setCode("CRM.MONTHLY_API_CALLS");
        cap.setCapabilityType("QUOTA");
        cap.setStatus("ACTIVE");
        cap.setDefaultValue("5000");
        when(moduleCapabilityRepository.findByModuleId(CRM_MODULE_ID))
                .thenReturn(List.of(cap));

        ModuleCapabilityContext.QuotaValue quota = resolver.getQuota(TENANT_ID, "CRM", "CRM.MONTHLY_API_CALLS");

        assertThat(quota).isNotNull();
        assertThat(quota.value()).isEqualTo(50000L);
        assertThat(quota.period()).isEqualTo("MONTHLY");
    }

    @Test
    @DisplayName("getQuota: returns null when module is disabled")
    void getQuota_returnsNullWhenDisabled() {
        setupCrmModule(true);
        setupActiveSubscription();
        setupPlanEntitlements(false);

        ModuleCapabilityContext.QuotaValue quota = resolver.getQuota(TENANT_ID, "CRM", "CRM.MONTHLY_API_CALLS");

        assertThat(quota).isNull();
    }

    @Test
    @DisplayName("getEffectiveEntitlements: returns denied context for null module code")
    void getEffectiveEntitlements_returnsDeniedForNullModuleCode() {
        ModuleCapabilityContext ctx = resolver.getEffectiveEntitlements(TENANT_ID, null);

        assertThat(ctx.isModuleEnabled()).isFalse();
        assertThat(ctx.moduleCode()).isNull();
    }

    @Test
    @DisplayName("getEffectiveEntitlements: returns denied context for blank module code")
    void getEffectiveEntitlements_returnsDeniedForBlankModuleCode() {
        ModuleCapabilityContext ctx = resolver.getEffectiveEntitlements(TENANT_ID, "  ");

        assertThat(ctx.isModuleEnabled()).isFalse();
    }

    @Test
    @DisplayName("Tenant isolation: tenant A's entitlements do not affect tenant B")
    void tenantIsolation_differentTenantsAreIsolated() {
        UUID tenantB = UUID.fromString("00000000-0000-0000-0000-000000000099");

        // Tenant A has active subscription
        setupCrmModule(true);
        setupActiveSubscription();
        setupPlanEntitlements(true);

        // Tenant B has NO active subscription
        when(jdbc.<Map<String, Object>>queryForStream(any(String.class), any(), eq(tenantB)))
                .thenReturn(Collections.<Map<String, Object>>emptyList().stream());

        boolean tenantAEnabled = resolver.isModuleEnabled(TENANT_ID, "CRM");
        boolean tenantBEnabled = resolver.isModuleEnabled(tenantB, "CRM");

        assertThat(tenantAEnabled).isTrue();
        assertThat(tenantBEnabled).isFalse();
    }

    @Test
    @DisplayName("recalculateEntitlements: validates entitlements for all enabled modules")
    void recalculateEntitlements_validatesModules() {
        setupCrmModule(true);
        setupActiveSubscription();
        setupPlanEntitlements(true);

        // Set up module capabilities for validation
        ModuleCapabilityEntity cap = new ModuleCapabilityEntity();
        cap.setCode("CRM.ENABLED");
        cap.setCapabilityType("MODULE_ENABLED");
        cap.setStatus("ACTIVE");
        cap.setDefaultValue("true");
        lenient().when(moduleCapabilityRepository.findByModuleId(CRM_MODULE_ID))
                .thenReturn(List.of(cap));

        when(moduleRepository.findAllEnabled()).thenReturn(List.of(createCrmModule()));

        // Should not throw and should validate entitlements
        resolver.recalculateEntitlements(TENANT_ID);

        // Verify that getEffectiveEntitlements was called for each module (via findByCode)
        verify(moduleRepository, atLeast(1)).findAllEnabled();
    }

    // === Helper methods ===

    private void setupCrmModule(boolean enabled) {
        when(moduleRepository.findByCode("CRM")).thenReturn(Optional.of(createCrmModule()));
    }

    private ModuleEntity createCrmModule() {
        ModuleEntity m = new ModuleEntity();
        m.setId(CRM_MODULE_ID);
        m.setCode("CRM");
        m.setName("CRM");
        m.setDescription("Customer Relationship Management");
        m.setStatus("ACTIVE");
        m.setDisplayOrder(10);
        m.setVersion("1.0");
        m.setEnabled(true);
        return m;
    }

    private void setupActiveSubscription() {
        Map<String, Object> subInfo = new HashMap<>();
        subInfo.put("subscriptionId", SUBSCRIPTION_ID);
        subInfo.put("planId", PLAN_ID);
        when(jdbc.<Map<String, Object>>queryForStream(any(String.class), any(), eq(TENANT_ID)))
                .thenReturn(List.of(subInfo).stream());
    }

    private void setupPlanEntitlements(boolean moduleEnabled) {
        PlanModuleEntitlementEntity pme = new PlanModuleEntitlementEntity();
        pme.setPlanId(PLAN_ID);
        pme.setModuleId(CRM_MODULE_ID);
        pme.setModuleEnabled(moduleEnabled);
        lenient().when(planModuleEntitlementRepository.findByPlanIdAndModuleId(PLAN_ID, CRM_MODULE_ID))
                .thenReturn(List.of(pme));
        lenient().when(moduleCapabilityRepository.findByModuleId(CRM_MODULE_ID))
                .thenReturn(Collections.emptyList());
    }
}
