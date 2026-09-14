package com.sanad.platform.module.entitlement;

import com.sanad.platform.security.SecurityPermitAllTestConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * R1 GATE R1.30 — explicit paid Workflow entitlement policy (PostgreSQL Direct).
 *
 * <p>Commercial invariant under test: WORKFLOW is OPTIONAL_PAID_MODULE +
 * EXPLICIT_OPT_IN. An ACTIVE subscription alone never grants Workflow; only
 * an explicit plan_module_entitlements row does. Existing
 * DEFAULT_COMPATIBILITY modules keep their historical fallback semantics.</p>
 */
@SpringBootTest
@ActiveProfiles("local")
@Import(SecurityPermitAllTestConfig.class)
class WorkflowEntitlementPolicyTest {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntitlementResolver resolver;

    private final List<UUID> tenantIds = new ArrayList<>();
    private final List<UUID> planIds = new ArrayList<>();
    private final List<UUID> subscriptionIds = new ArrayList<>();
    private final List<UUID> entitlementRowIds = new ArrayList<>();

    private UUID workflowModuleId;
    private UUID erpModuleId;
    private UUID crmModuleId;

    @BeforeEach
    void seed() {
        workflowModuleId = moduleId("WORKFLOW");
        erpModuleId = moduleId("ERP");
        crmModuleId = moduleId("CRM");
    }

    @AfterEach
    void cleanup() {
        for (UUID id : entitlementRowIds) jdbc.update("DELETE FROM plan_module_entitlements WHERE id = ?", id);
        for (UUID id : subscriptionIds) jdbc.update("DELETE FROM tenant_subscriptions WHERE id = ?", id);
        for (UUID id : planIds) jdbc.update("DELETE FROM saas_plans WHERE id = ?", id);
        for (UUID id : tenantIds) jdbc.update("DELETE FROM tenants WHERE id = ?", id);
    }

    private UUID moduleId(String code) {
        return jdbc.queryForObject("SELECT id FROM modules WHERE code = ?", UUID.class, code);
    }

    private UUID newTenant(String label) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO tenants (id, name, subdomain, status, created_at, updated_at) VALUES (?,?,?,?,NOW(),NOW())",
                id, "R1-ENT-" + label, "r1ent-" + label + "-" + UUID.randomUUID().toString().substring(0, 8), "ACTIVE");
        tenantIds.add(id);
        return id;
    }

    private UUID newPlan(String label) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO saas_plans (id, code, name, status, currency_code, monthly_price_minor,
                     annual_price_minor, trial_days, max_users, max_organizations, storage_mb,
                     created_at, updated_at)
                VALUES (?,?,?,?, 'SAR', 0, 0, 0, 10, 1, 0, NOW(), NOW())
                """, id, "R1ENT_" + label + "_" + UUID.randomUUID().toString().substring(0, 8),
                "R1 entitlement test plan " + label, "ACTIVE");
        planIds.add(id);
        return id;
    }

    private void newActiveSubscription(UUID tenantId, UUID planId) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update("""
                INSERT INTO tenant_subscriptions (id, tenant_id, plan_id, status, billing_cycle,
                     seat_quantity, credit_balance_minor, started_at, current_period_start,
                     current_period_end, cancel_at_period_end, created_at, updated_at)
                VALUES (?,?,?, 'ACTIVE', 'MONTHLY', 5, 0, ?, ?, ?, false, NOW(), NOW())
                """, id, tenantId, planId, Timestamp.from(now), Timestamp.from(now),
                Timestamp.from(now.plusSeconds(30 * 24 * 3600)));
        subscriptionIds.add(id);
    }

    private UUID newWorkflowEntitlementRow(UUID planId, boolean enabled) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO plan_module_entitlements (id, plan_id, module_id, module_enabled,
                     capability_code, created_at, updated_at)
                VALUES (?,?,?,?, NULL, NOW(), NOW())
                """, id, planId, workflowModuleId, enabled);
        entitlementRowIds.add(id);
        return id;
    }

    @Test
    void NO_SUBSCRIPTION_WORKFLOW_DENIED() {
        UUID tenant = newTenant("nosub");
        assertThat(resolver.isModuleEnabled(tenant, "WORKFLOW")).isFalse();
    }

    @Test
    void ACTIVE_PLAN_WITHOUT_WORKFLOW_ROW_DENIED() {
        // The core R1 change: module catalog enabled=true + EXPLICIT_OPT_IN policy
        // + zero plan rows => FAIL CLOSED (previously the catalog fallback enabled it).
        UUID tenant = newTenant("norow");
        UUID plan = newPlan("norow");
        newActiveSubscription(tenant, plan);
        Boolean catalogEnabled = jdbc.queryForObject(
                "SELECT enabled FROM modules WHERE id = ?", Boolean.class, workflowModuleId);
        assertThat(catalogEnabled).isTrue(); // precondition: catalog flag does not grant paid use
        assertThat(resolver.isModuleEnabled(tenant, "WORKFLOW")).isFalse();
    }

    @Test
    void WORKFLOW_EXPLICIT_DISABLED_DENIED() {
        UUID tenant = newTenant("dis");
        UUID plan = newPlan("dis");
        newActiveSubscription(tenant, plan);
        newWorkflowEntitlementRow(plan, false);
        assertThat(resolver.isModuleEnabled(tenant, "WORKFLOW")).isFalse();
    }

    @Test
    void WORKFLOW_EXPLICIT_ENABLED_ALLOWED() {
        UUID tenant = newTenant("en");
        UUID plan = newPlan("en");
        newActiveSubscription(tenant, plan);
        newWorkflowEntitlementRow(plan, true);
        assertThat(resolver.isModuleEnabled(tenant, "WORKFLOW")).isTrue();
    }

    @Test
    void WORKFLOW_MODULE_DEFAULT_ENABLED_DOES_NOT_OVERRIDE_EXPLICIT_OPT_IN() {
        // Identical in spirit to ACTIVE_PLAN_WITHOUT_WORKFLOW_ROW_DENIED; pinned
        // explicitly: flipping the catalog flag cannot purchase the module.
        UUID tenant = newTenant("catflag");
        UUID plan = newPlan("catflag");
        newActiveSubscription(tenant, plan);
        assertThat(resolver.isModuleEnabled(tenant, "WORKFLOW")).isFalse();
        jdbc.update("UPDATE modules SET enabled = NOT enabled WHERE id = ?", workflowModuleId);
        try {
            assertThat(resolver.isModuleEnabled(tenant, "WORKFLOW")).isFalse();
        } finally {
            jdbc.update("UPDATE modules SET enabled = NOT enabled WHERE id = ?", workflowModuleId);
        }
    }

    @Test
    void UNKNOWN_MODULE_DENIED() {
        UUID tenant = newTenant("unknown");
        UUID plan = newPlan("unknown");
        newActiveSubscription(tenant, plan);
        assertThat(resolver.isModuleEnabled(tenant, "NOT_A_MODULE")).isFalse();
    }

    @Test
    void NULL_AND_BLANK_MODULE_DENIED() {
        UUID tenant = newTenant("blank");
        assertThat(resolver.isModuleEnabled(tenant, null)).isFalse();
        assertThat(resolver.isModuleEnabled(tenant, "  ")).isFalse();
    }

    @Test
    void WORKFLOW_OFF_ERP_NATIVE_OPERATION_ALLOWED() {
        // DEFAULT_COMPATIBILITY modules are untouched by the R1 policy change:
        // ERP has no plan rows for this fresh plan and remains enabled through
        // the historical catalog fallback -> source modules stay functional.
        UUID tenant = newTenant("erp");
        UUID plan = newPlan("erp");
        newActiveSubscription(tenant, plan);
        assertThat(resolver.isModuleEnabled(tenant, "ERP")).isTrue();
        assertThat(resolver.isModuleEnabled(tenant, "WORKFLOW")).isFalse();
    }

    @Test
    void WORKFLOW_OFF_CRM_NATIVE_OPERATION_ALLOWED() {
        // CRM keeps its explicit STARTER-style row semantics: a row granting the
        // module keeps CRM on. Workflow stays off for the same tenant.
        UUID tenant = newTenant("crm");
        UUID plan = newPlan("crm");
        newActiveSubscription(tenant, plan);
        UUID row = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO plan_module_entitlements (id, plan_id, module_id, module_enabled,
                     capability_code, created_at, updated_at)
                VALUES (?,?,?,?, NULL, NOW(), NOW())
                """, row, plan, crmModuleId, true);
        entitlementRowIds.add(row);
        assertThat(resolver.isModuleEnabled(tenant, "CRM")).isTrue();
        assertThat(resolver.isModuleEnabled(tenant, "WORKFLOW")).isFalse();
    }
}
