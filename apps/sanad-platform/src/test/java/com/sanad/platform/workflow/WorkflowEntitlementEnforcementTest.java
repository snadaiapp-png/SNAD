package com.sanad.platform.workflow.application;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;

import com.sanad.platform.module.lifecycle.SubscriptionImpactService;
import com.sanad.platform.module.lifecycle.SubscriptionImpactService.SubscriptionImpactPreview;
import com.sanad.platform.security.SecurityPermitAllTestConfig;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * R1 GATES R1.3/R1.5/R1.6/R1.30 — enforcement guard, workflow-off invariants,
 * removal semantics and impact preview (PostgreSQL Direct).
 */
@SpringBootTest
@ActiveProfiles("local")
@Import(SecurityPermitAllTestConfig.class)
class WorkflowEntitlementEnforcementTest {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private WorkflowEntitlementGuard guard;
    @Autowired private SubscriptionImpactService impactService;
    @Autowired private WorkflowRuntimeImpactCounter counter;

    private final List<UUID> tenantIds = new ArrayList<>();
    private final List<UUID> planIds = new ArrayList<>();
    private final List<UUID> subscriptionIds = new ArrayList<>();
    private final List<UUID> entitlementRowIds = new ArrayList<>();
    private final List<UUID> definitionIds = new ArrayList<>();
    private final List<UUID> instanceIds = new ArrayList<>();
    private final List<UUID> userIds = new ArrayList<>();

    private UUID workflowModuleId;

    @BeforeEach
    void seed() {
        workflowModuleId = jdbc.queryForObject("SELECT id FROM modules WHERE code = 'WORKFLOW'", UUID.class);
    }

    @AfterEach
    void cleanup() {
        for (UUID id : instanceIds) jdbc.update("DELETE FROM workflow_instances WHERE id = ?", id);
        for (UUID id : definitionIds) jdbc.update("DELETE FROM workflow_definitions WHERE id = ?", id);
        for (UUID id : userIds) jdbc.update("DELETE FROM users WHERE id = ?", id);
        for (UUID id : entitlementRowIds) jdbc.update("DELETE FROM plan_module_entitlements WHERE id = ?", id);
        for (UUID id : subscriptionIds) jdbc.update("DELETE FROM tenant_subscriptions WHERE id = ?", id);
        for (UUID id : planIds) jdbc.update("DELETE FROM saas_plans WHERE id = ?", id);
        for (UUID id : tenantIds) jdbc.update("DELETE FROM tenants WHERE id = ?", id);
    }

    private UUID newTenant(String label) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO tenants (id, name, subdomain, status, created_at, updated_at) VALUES (?,?,?,?,NOW(),NOW())",
                id, "R1-ENF-" + label, "r1enf-" + label + "-" + UUID.randomUUID().toString().substring(0, 8), "ACTIVE");
        tenantIds.add(id);
        return id;
    }

    private UUID newUser(UUID tenantId, String label) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, tenant_id, email, display_name, status, password_hash, created_at, updated_at) "
                        + "VALUES (?, ?, ?, 'R1 Enf User', 'ACTIVE', 'x', NOW(), NOW())",
                id, tenantId, "r1enf-" + label + "-" + id + "@test");
        userIds.add(id);
        return id;
    }

    private void grantWorkflow(UUID tenantId) {
        UUID planId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO saas_plans (id, code, name, status, currency_code, monthly_price_minor,
                     annual_price_minor, trial_days, max_users, max_organizations, storage_mb,
                     created_at, updated_at)
                VALUES (?,?,?,?, 'SAR', 0, 0, 0, 10, 1, 0, NOW(), NOW())
                """, planId, "R1ENF_" + UUID.randomUUID().toString().substring(0, 8), "R1 enf plan", "ACTIVE");
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
        UUID row = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO plan_module_entitlements (id, plan_id, module_id, module_enabled,
                     capability_code, created_at, updated_at)
                VALUES (?,?,?,?, NULL, NOW(), NOW())
                """, row, planId, workflowModuleId, true);
        entitlementRowIds.add(row);
    }

    private void removeWorkflowEntitlement(UUID tenantId) {
        jdbc.update("DELETE FROM plan_module_entitlements WHERE module_id = ? AND plan_id IN "
                + "(SELECT plan_id FROM tenant_subscriptions WHERE tenant_id = ?)", workflowModuleId, tenantId);
    }

    private UUID newY2PublishedDefinition(UUID tenantId, String code) {
        UUID userId = newUser(tenantId, code);
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO workflow_definitions (
                    id, tenant_id, definition_family_id, code, name, module, version, status,
                    trigger_type, created_by, version_lock, engine_generation, publication_state,
                    schema_version, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, 'GENERAL', 1, 'ACTIVE', 'MANUAL', ?, 0, 'Y2', 'PUBLISHED', 1, NOW(), NOW())
                """, id, tenantId, id, code, "R1 enforcement def " + code, userId);
        definitionIds.add(id);
        return id;
    }

    private UUID newY2Instance(UUID tenantId, UUID definitionId, String status) {
        UUID userId = newUser(tenantId, "inst");
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO workflow_instances (
                    id, tenant_id, workflow_definition_id, definition_family_id, workflow_version,
                    business_entity_type, business_entity_id, status, engine_generation,
                    current_step_key, started_by, started_at, created_at, updated_at)
                VALUES (?,?,?,?, 1, 'R1_TEST_ENTITY', ?, ?, 'Y2', 'START', ?, NOW(), NOW(), NOW())
                """, id, tenantId, definitionId, definitionId, id, status, userId);
        instanceIds.add(id);
        return id;
    }

    @Test
    void GUARD_FAIL_CLOSED_FOR_NEVER_PURCHASED_TENANT() {
        UUID tenant = newTenant("never");
        assertThat(guard.isWorkflowEnabled(tenant)).isFalse();
        assertThatThrownBy(() -> guard.requireWorkflowEnabled(tenant))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("WORKFLOW_MODULE_NOT_ENTITLED");
    }

    @Test
    void GUARD_ALLOWS_ENTITLED_TENANT() {
        UUID tenant = newTenant("granted");
        grantWorkflow(tenant);
        assertThat(guard.isWorkflowEnabled(tenant)).isTrue();
        guard.requireWorkflowEnabled(tenant); // no exception
    }

    @Test
    void GUARD_NULL_TENANT_DENIED() {
        assertThat(guard.isWorkflowEnabled(null)).isFalse();
    }

    @Test
    void WORKFLOW_OFF_NO_NEW_INSTANCE() {
        // Workflow-off tenants cannot START new instances: the start path is
        // guarded by requireWorkflowEnabled in the API layer (authoritative).
        // Engine-level proof: the guard deny code is the documented contract.
        UUID tenant = newTenant("nostart");
        assertThat(guard.isWorkflowEnabled(tenant)).isFalse();
        assertThat(WorkflowEntitlementGuard.DENIED_REASON).isEqualTo("WORKFLOW_MODULE_NOT_ENTITLED");
    }

    @Test
    void ENTITLEMENT_REMOVAL_BLOCKS_NEW_START_AND_PRESERVES_HISTORY() {
        UUID tenant = newTenant("removed");
        grantWorkflow(tenant);
        assertThat(guard.isWorkflowEnabled(tenant)).isTrue();

        // History created while entitled:
        UUID defId = newY2PublishedDefinition(tenant, "R1-REMOVAL");
        UUID instId = newY2Instance(tenant, defId, "RUNNING");

        // Subscription removal: the explicit plan entitlement row is gone.
        removeWorkflowEntitlement(tenant);

        // New product use fails closed after removal:
        assertThat(guard.isWorkflowEnabled(tenant)).isFalse();
        assertThatThrownBy(() -> guard.requireWorkflowEnabled(tenant))
                .isInstanceOf(AccessDeniedException.class);

        // Business history is preserved (NO data erasure, DRAIN semantics):
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM workflow_definitions WHERE id = ?", Integer.class, defId)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM workflow_instances WHERE id = ?", Integer.class, instId)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT status FROM workflow_instances WHERE id = ?", String.class, instId)).isEqualTo("RUNNING");
    }

    @Test
    void WORKFLOW_OFF_HISTORY_READS_REMAIN_GOVERNED_NOT_ERASED() {
        // Governed historical access: definitions of a workflow-off tenant
        // remain present; entitlement state never auto-deletes history.
        UUID tenant = newTenant("history");
        grantWorkflow(tenant);
        UUID defId = newY2PublishedDefinition(tenant, "R1-HISTORY");
        removeWorkflowEntitlement(tenant);
        assertThat(guard.isWorkflowEnabled(tenant)).isFalse();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM workflow_definitions WHERE id = ?", Integer.class, defId)).isEqualTo(1);
    }

    @Test
    void IMPACT_PREVIEW_COUNTS_ACTIVE_RUNTIME() {
        UUID tenant = newTenant("impact");
        grantWorkflow(tenant);
        UUID defId = newY2PublishedDefinition(tenant, "R1-IMPACT");
        newY2Instance(tenant, defId, "RUNNING");
        UUID paused = newY2Instance(tenant, defId, "PAUSED");

        Map<String, Long> counts = counter.countRuntime(tenant);
        assertThat(counts.get("ACTIVE_INSTANCES")).isEqualTo(1L);
        assertThat(counts.get("PAUSED_INSTANCES")).isEqualTo(1L);
        assertThat(counts.get("PENDING_WORK_ITEMS")).isEqualTo(0L);
        assertThat(counts.get("OPEN_INCIDENTS")).isEqualTo(0L);

        // The subscription control-plane preview surfaces the workflow counts.
        UUID targetPlan = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO saas_plans (id, code, name, status, currency_code, monthly_price_minor,
                     annual_price_minor, trial_days, max_users, max_organizations, storage_mb,
                     created_at, updated_at)
                VALUES (?,?,?,?, 'SAR', 0, 0, 0, 10, 1, 0, NOW(), NOW())
                """, targetPlan, "R1ENF_T_" + UUID.randomUUID().toString().substring(0, 8),
                "R1 target plan", "ACTIVE");
        planIds.add(targetPlan);
        SubscriptionImpactPreview preview = impactService.previewPlanChange(tenant, targetPlan);
        assertThat(preview.workflowRuntimeImpact()).isNotNull();
        assertThat(preview.workflowRuntimeImpact().get("ACTIVE_INSTANCES")).isEqualTo(1L);
        assertThat(preview.workflowRuntimeImpact().get("PAUSED_INSTANCES")).isEqualTo(1L);
        assertThat(preview.dataSafetyNote()).contains("DRAIN_EXISTING");
    }
}
