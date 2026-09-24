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
import static org.mockito.Mockito.*;

/**
 * Tests for downgrade safety — ensures that downgrading a plan does NOT
 * delete operational data. Instead:
 * <ul>
 *   <li>Existing data is preserved</li>
 *   <li>New usage is restricted to the new (lower) limit</li>
 *   <li>Entitlements are recalculated</li>
 * </ul>
 *
 * <p>These tests verify the EntitlementResolver behavior when a plan change
 * results in a lower limit. The resolver itself does NOT delete data — it
 * only returns the new (lower) effective limit. The caller (module) is
 * responsible for enforcing the limit without deleting existing data.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Downgrade Safety — unit tests")
class DowngradeSafetyTest {

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
    private static final UUID OLD_PLAN_ID = UUID.fromString("c3000000-0000-0000-0000-000000000002"); // GROWTH
    private static final UUID NEW_PLAN_ID = UUID.fromString("c3000000-0000-0000-0000-000000000001"); // STARTER (lower)
    private static final UUID CRM_MODULE_ID = UUID.fromString("11111111-0000-0000-0000-000000000001");

    @BeforeEach
    void setUp() {
        resolver = new EntitlementResolver(jdbc, moduleRepository, moduleCapabilityRepository, planModuleEntitlementRepository);
    }

    @Test
    @DisplayName("Downgrade: new limit is lower than old limit (10000 → 1000)")
    void downgrade_newLimitIsLower() {
        // Setup: module exists, subscription active with STARTER plan (lower)
        setupCrmModule();
        setupActiveSubscription(NEW_PLAN_ID);

        // STARTER plan: CRM.MAX_CONTACTS = 1000
        PlanModuleEntitlementEntity pme = new PlanModuleEntitlementEntity();
        pme.setPlanId(NEW_PLAN_ID);
        pme.setModuleId(CRM_MODULE_ID);
        pme.setModuleEnabled(true);
        pme.setCapabilityCode("CRM.MAX_CONTACTS");
        pme.setLimitValue(1000L); // Lower than GROWTH's 25000
        when(planModuleEntitlementRepository.findByPlanIdAndModuleId(NEW_PLAN_ID, CRM_MODULE_ID))
                .thenReturn(List.of(pme));

        // Module capability definition
        ModuleCapabilityEntity cap = new ModuleCapabilityEntity();
        cap.setCode("CRM.MAX_CONTACTS");
        cap.setCapabilityType("NUMERIC_LIMIT");
        cap.setStatus("ACTIVE");
        cap.setDefaultValue("10000");
        when(moduleCapabilityRepository.findByModuleId(CRM_MODULE_ID))
                .thenReturn(List.of(cap));

        long newLimit = resolver.getLimit(TENANT_ID, "CRM", "CRM.MAX_CONTACTS");

        // The new limit should be 1000 (from STARTER plan override)
        assertThat(newLimit).isEqualTo(1000L);
        // It should be LOWER than the old GROWTH plan's 25000
        assertThat(newLimit).isLessThan(25000L);
    }

    @Test
    @DisplayName("Downgrade: module remains enabled (data preserved)")
    void downgrade_moduleRemainsEnabled() {
        setupCrmModule();
        setupActiveSubscription(NEW_PLAN_ID);

        // STARTER plan: CRM module enabled with CRM.ENABLED capability
        PlanModuleEntitlementEntity pme = new PlanModuleEntitlementEntity();
        pme.setPlanId(NEW_PLAN_ID);
        pme.setModuleId(CRM_MODULE_ID);
        pme.setModuleEnabled(true);
        pme.setCapabilityCode("CRM.ENABLED");
        pme.setCapabilityValue("true");
        when(planModuleEntitlementRepository.findByPlanIdAndModuleId(NEW_PLAN_ID, CRM_MODULE_ID))
                .thenReturn(List.of(pme));

        ModuleCapabilityEntity cap = new ModuleCapabilityEntity();
        cap.setCode("CRM.ENABLED");
        cap.setCapabilityType("MODULE_ENABLED");
        cap.setStatus("ACTIVE");
        cap.setDefaultValue("true");
        when(moduleCapabilityRepository.findByModuleId(CRM_MODULE_ID))
                .thenReturn(List.of(cap));

        boolean enabled = resolver.isModuleEnabled(TENANT_ID, "CRM");

        // Module should still be enabled after downgrade
        assertThat(enabled).isTrue();
    }

    @Test
    @DisplayName("Downgrade: boolean capability disabled in lower plan")
    void downgrade_booleanCapabilityDisabled() {
        setupCrmModule();
        setupActiveSubscription(NEW_PLAN_ID);

        // STARTER plan: CRM.ADVANCED_PIPELINE = false (was true in GROWTH)
        PlanModuleEntitlementEntity pme = new PlanModuleEntitlementEntity();
        pme.setPlanId(NEW_PLAN_ID);
        pme.setModuleId(CRM_MODULE_ID);
        pme.setModuleEnabled(true);
        pme.setCapabilityCode("CRM.ADVANCED_PIPELINE");
        pme.setCapabilityValue("false"); // Disabled in STARTER
        when(planModuleEntitlementRepository.findByPlanIdAndModuleId(NEW_PLAN_ID, CRM_MODULE_ID))
                .thenReturn(List.of(pme));

        ModuleCapabilityEntity cap = new ModuleCapabilityEntity();
        cap.setCode("CRM.ADVANCED_PIPELINE");
        cap.setCapabilityType("BOOLEAN_CAPABILITY");
        cap.setStatus("ACTIVE");
        cap.setDefaultValue("true"); // Default is true
        when(moduleCapabilityRepository.findByModuleId(CRM_MODULE_ID))
                .thenReturn(List.of(cap));

        boolean hasCap = resolver.hasCapability(TENANT_ID, "CRM", "CRM.ADVANCED_PIPELINE");

        // Plan override should take precedence — capability should be false
        assertThat(hasCap).isFalse();
    }

    @Test
    @DisplayName("Downgrade: quota reduced (50000 → 5000)")
    void downgrade_quotaReduced() {
        setupCrmModule();
        setupActiveSubscription(NEW_PLAN_ID);

        // STARTER plan: CRM.MONTHLY_API_CALLS = 5000
        PlanModuleEntitlementEntity pme = new PlanModuleEntitlementEntity();
        pme.setPlanId(NEW_PLAN_ID);
        pme.setModuleId(CRM_MODULE_ID);
        pme.setModuleEnabled(true);
        pme.setCapabilityCode("CRM.MONTHLY_API_CALLS");
        pme.setQuotaValue(5000L); // Lower than GROWTH's 50000
        pme.setQuotaPeriod("MONTHLY");
        when(planModuleEntitlementRepository.findByPlanIdAndModuleId(NEW_PLAN_ID, CRM_MODULE_ID))
                .thenReturn(List.of(pme));

        ModuleCapabilityEntity cap = new ModuleCapabilityEntity();
        cap.setCode("CRM.MONTHLY_API_CALLS");
        cap.setCapabilityType("QUOTA");
        cap.setStatus("ACTIVE");
        cap.setDefaultValue("50000");
        when(moduleCapabilityRepository.findByModuleId(CRM_MODULE_ID))
                .thenReturn(List.of(cap));

        ModuleCapabilityContext.QuotaValue quota = resolver.getQuota(TENANT_ID, "CRM", "CRM.MONTHLY_API_CALLS");

        assertThat(quota).isNotNull();
        assertThat(quota.value()).isEqualTo(5000L); // Reduced
        assertThat(quota.value()).isLessThan(50000L); // Lower than old plan
    }

    @Test
    @DisplayName("Downgrade: recalculateEntitlements does NOT delete data (no DELETE statements)")
    void downgrade_recalculateDoesNotDeleteData() {
        setupCrmModule();
        setupActiveSubscription(NEW_PLAN_ID);

        ModuleCapabilityEntity cap = new ModuleCapabilityEntity();
        cap.setCode("CRM.ENABLED");
        cap.setCapabilityType("MODULE_ENABLED");
        cap.setStatus("ACTIVE");
        cap.setDefaultValue("true");
        lenient().when(moduleCapabilityRepository.findByModuleId(CRM_MODULE_ID))
                .thenReturn(List.of(cap));

        when(moduleRepository.findAllEnabled()).thenReturn(List.of(createCrmModule()));

        // recalculateEntitlements is now read-only (OPTION A)
        resolver.recalculateEntitlements(TENANT_ID);

        // Verify NO DELETE statements were executed (data preservation)
        verify(jdbc, never()).update(contains("DELETE"), (Object) any());
        verify(jdbc, never()).update(contains("DELETE"), any(), any());
    }

    @Test
    @DisplayName("Downgrade: existing data not affected by limit reduction")
    void downgrade_existingDataPreserved() {
        setupCrmModule();
        setupActiveSubscription(NEW_PLAN_ID);

        // STARTER plan: CRM.MAX_CONTACTS = 1000 (was 25000 in GROWTH)
        PlanModuleEntitlementEntity pme = new PlanModuleEntitlementEntity();
        pme.setPlanId(NEW_PLAN_ID);
        pme.setModuleId(CRM_MODULE_ID);
        pme.setModuleEnabled(true);
        pme.setCapabilityCode("CRM.MAX_CONTACTS");
        pme.setLimitValue(1000L);
        when(planModuleEntitlementRepository.findByPlanIdAndModuleId(NEW_PLAN_ID, CRM_MODULE_ID))
                .thenReturn(List.of(pme));

        ModuleCapabilityEntity cap = new ModuleCapabilityEntity();
        cap.setCode("CRM.MAX_CONTACTS");
        cap.setCapabilityType("NUMERIC_LIMIT");
        cap.setStatus("ACTIVE");
        cap.setDefaultValue("10000");
        when(moduleCapabilityRepository.findByModuleId(CRM_MODULE_ID))
                .thenReturn(List.of(cap));

        // The resolver returns the new limit (1000) — it does NOT delete contacts
        long newLimit = resolver.getLimit(TENANT_ID, "CRM", "CRM.MAX_CONTACTS");
        assertThat(newLimit).isEqualTo(1000L);

        // The resolver does NOT issue any DELETE or UPDATE on operational tables
        // It only reads from plan_module_entitlements and module_capabilities
        // Existing contacts remain in the database — the module is responsible
        // for enforcing the new limit on NEW operations only
        verify(jdbc, never()).update(contains("DELETE FROM crm_contacts"), (Object) any());
        verify(jdbc, never()).update(contains("DELETE FROM crm_accounts"), (Object) any());
    }

    // === Helper methods ===

    private void setupCrmModule() {
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

    private void setupActiveSubscription(UUID planId) {
        Map<String, Object> subInfo = new HashMap<>();
        subInfo.put("subscriptionId", SUBSCRIPTION_ID);
        subInfo.put("planId", planId);
        when(jdbc.<Map<String, Object>>queryForStream(any(String.class), any(), eq(TENANT_ID)))
                .thenReturn(List.of(subInfo).stream());
    }
}
