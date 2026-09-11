package com.sanad.platform.workflow.application;

import com.sanad.platform.security.SecurityPermitAllTestConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * R1 GATE R1.31 — dynamic Designer catalog (PostgreSQL Direct): tenant scoping,
 * entitlement scoping, actionable classification and stable order.
 */
@SpringBootTest
@ActiveProfiles("local")
@Import(SecurityPermitAllTestConfig.class)
class WorkflowModuleCatalogServiceTest {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private WorkflowModuleCatalogService catalogService;

    private final List<UUID> tenantIds = new ArrayList<>();
    private final List<UUID> planIds = new ArrayList<>();
    private final List<UUID> subscriptionIds = new ArrayList<>();
    private final List<UUID> entitlementRowIds = new ArrayList<>();

    private UUID workflowModuleId;
    private UUID crmModuleId;

    @BeforeEach
    void seed() {
        workflowModuleId = jdbc.queryForObject("SELECT id FROM modules WHERE code = 'WORKFLOW'", UUID.class);
        crmModuleId = jdbc.queryForObject("SELECT id FROM modules WHERE code = 'CRM'", UUID.class);
    }

    @AfterEach
    void cleanup() {
        for (UUID id : entitlementRowIds) jdbc.update("DELETE FROM plan_module_entitlements WHERE id = ?", id);
        for (UUID id : subscriptionIds) jdbc.update("DELETE FROM tenant_subscriptions WHERE id = ?", id);
        for (UUID id : planIds) jdbc.update("DELETE FROM saas_plans WHERE id = ?", id);
        for (UUID id : tenantIds) jdbc.update("DELETE FROM tenants WHERE id = ?", id);
    }

    private UUID newTenant(String label) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO tenants (id, name, subdomain, status, created_at, updated_at) VALUES (?,?,?,?,NOW(),NOW())",
                id, "R1-CAT-" + label, "r1cat-" + label + "-" + UUID.randomUUID().toString().substring(0, 8), "ACTIVE");
        tenantIds.add(id);
        return id;
    }

    private void grant(UUID tenantId, UUID moduleId, boolean enabled) {
        if (subscriptionIds.size() < tenantIds.size()) {
            subscribe(tenantId);
        }
        entitle(tenantId, moduleId, enabled);
    }

    private void subscribe(UUID tenantId) {
        UUID planId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO saas_plans (id, code, name, status, currency_code, monthly_price_minor,
                     annual_price_minor, trial_days, max_users, max_organizations, storage_mb,
                     created_at, updated_at)
                VALUES (?,?,?,?, 'SAR', 0, 0, 0, 10, 1, 0, NOW(), NOW())
                """, planId, "R1CAT_" + UUID.randomUUID().toString().substring(0, 8), "R1 cat plan", "ACTIVE");
        planIds.add(planId);
        UUID subId = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update("""
                INSERT INTO tenant_subscriptions (id, tenant_id, plan_id, status, billing_cycle,
                     seat_quantity, credit_balance_minor, started_at, current_period_start,
                     current_period_end, cancel_at_period_end, created_at, updated_at)
                VALUES (?,?,?, 'ACTIVE', 'MONTHLY', 5, 0, ?, ?, ?, false, NOW(), NOW())
                """, subId, tenantId, planId, Timestamp.from(now), Timestamp.from(now),
                Timestamp.from(now.plusSeconds(30 * 24 * 3600)));
        subscriptionIds.add(subId);
    }

    private void entitle(UUID tenantId, UUID moduleId, boolean enabled) {
        UUID planId = jdbc.queryForObject(
                "SELECT plan_id FROM tenant_subscriptions WHERE tenant_id = ?", UUID.class, tenantId);
        UUID row = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO plan_module_entitlements (id, plan_id, module_id, module_enabled,
                     capability_code, created_at, updated_at)
                VALUES (?,?,?,?, NULL, NOW(), NOW())
                """, row, planId, moduleId, enabled);
        entitlementRowIds.add(row);
    }

    private Map<String, Object> entry(Map<String, Object> catalog, String moduleCode) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> modules = (List<Map<String, Object>>) catalog.get("modules");
        return modules.stream()
                .filter(m -> moduleCode.equals(m.get("moduleCode")))
                .findFirst()
                .orElse(null);
    }

    @Test
    void CATALOG_REQUIRES_WORKFLOW_ENTITLEMENT() {
        UUID tenant = newTenant("denied");
        assertThatThrownBy(() -> catalogService.effectiveCatalog(tenant))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("WORKFLOW_MODULE_NOT_ENTITLED");
    }

    @Test
    void CATALOG_TENANT_SCOPED_AND_ENTITLEMENT_SCOPED() {
        UUID tenantA = newTenant("a"); // CRM entitled
        grant(tenantA, workflowModuleId, true);
        grant(tenantA, crmModuleId, true);

        UUID tenantB = newTenant("b"); // CRM explicitly NOT entitled for tenant B
        grant(tenantB, workflowModuleId, true);
        grant(tenantB, crmModuleId, false);

        Map<String, Object> catalogA = catalogService.effectiveCatalog(tenantA);
        Map<String, Object> catalogB = catalogService.effectiveCatalog(tenantB);

        Map<String, Object> crmA = entry(catalogA, "CRM");
        Map<String, Object> crmB = entry(catalogB, "CRM");
        assertThat(crmA).isNotNull();
        assertThat(crmB).isNotNull();
        assertThat(crmA.get("status")).isEqualTo("REGISTERED_AND_WORKFLOW_READY");
        assertThat(crmA.get("actionable")).isEqualTo(Boolean.TRUE);
        assertThat(crmB.get("status")).isEqualTo("DISABLED_FOR_TENANT");
        assertThat(crmB.get("actionable")).isEqualTo(Boolean.FALSE);
        // DISABLED_TENANT_MODULE metadata still present (not actionable, not hidden data)
        assertThat(crmB.get("entities")).isNotNull();
    }

    @Test
    void CATALOG_ENTITIES_METADATA_PRESENT_FOR_ACTIONABLE_MODULE() {
        UUID tenant = newTenant("meta");
        grant(tenant, workflowModuleId, true);
        grant(tenant, crmModuleId, true);
        Map<String, Object> catalog = catalogService.effectiveCatalog(tenant);
        Map<String, Object> crm = entry(catalog, "CRM");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> entities = (List<Map<String, Object>>) crm.get("entities");
        assertThat(entities).extracting(e -> e.get("entityType"))
                .containsExactlyInAnyOrder("CUSTOMER", "ACCOUNT", "OPPORTUNITY");
        assertThat(entities.get(0)).containsKeys("entityType", "displayName", "sourceTable", "deepLinkTemplate");
    }

    @Test
    void CATALOG_STABLE_ORDER() {
        UUID tenant = newTenant("order");
        grant(tenant, workflowModuleId, true);
        grant(tenant, crmModuleId, true);
        Map<String, Object> c1 = catalogService.effectiveCatalog(tenant);
        Map<String, Object> c2 = catalogService.effectiveCatalog(tenant);
        @SuppressWarnings("unchecked")
        List<String> order1 = ((List<Map<String, Object>>) c1.get("modules")).stream()
                .map(m -> (String) m.get("moduleCode")).toList();
        @SuppressWarnings("unchecked")
        List<String> order2 = ((List<Map<String, Object>>) c2.get("modules")).stream()
                .map(m -> (String) m.get("moduleCode")).toList();
        assertThat(order1).isEqualTo(order2);
        assertThat(order1).isSorted();
    }

    @Test
    void UNAVAILABLE_MODULES_EXCLUDED_FROM_CATALOG() {
        UUID tenant = newTenant("ghost");
        grant(tenant, workflowModuleId, true);
        Map<String, Object> catalog = catalogService.effectiveCatalog(tenant);
        // TEST_FUTURE_MODULE is Spring-registered in the other test class only;
        // GHOST_MODULE has no contract at all — neither may appear.
        assertThat(entry(catalog, "GHOST_MODULE")).isNull();
        // Everything the catalog DOES expose has a contract (registry-driven).
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> modules = (List<Map<String, Object>>) catalog.get("modules");
        assertThat(modules).allSatisfy(m -> assertThat(m.get("status")).isNotEqualTo("UNAVAILABLE"));
    }
}
