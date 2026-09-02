package com.sanad.platform.workflow;

import com.sanad.platform.security.SecurityPermitAllTestConfig;
import com.sanad.platform.workflow.application.WorkflowOperationalQueryService;
import com.sanad.platform.workflow.application.WorkflowOperationalQueryService.MonitoringSnapshot;
import com.sanad.platform.workflow.application.WorkflowOperationalQueryService.OperationalTaskRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Wave 3 / Task 19 — operational read models (AL3).
 *
 * <p>Every read model query starts from {@code tenant_id} and filters by the
 * concrete employee — a read model never grants authorization, it only
 * mirrors committed authoritative state. Proves tenant isolation across My
 * Tasks, pools, approvals, incidents, and instance search, plus the
 * monitoring snapshot contract.</p>
 */
@SpringBootTest
@ActiveProfiles("local")
@Import(SecurityPermitAllTestConfig.class)
@Transactional
class WorkflowOperationalReadModelTest {

    @Autowired
    private WorkflowOperationalQueryService operationalQueries;

    @Autowired
    private JdbcTemplate jdbc;

    private UUID tenantA;
    private UUID tenantB;
    private UUID employeeA;
    private UUID employeeB;
    private UUID userA;
    private UUID instanceA;
    private UUID stepInstanceA;

    @BeforeEach
    void setUp() {
        tenantA = UUID.randomUUID();
        tenantB = UUID.randomUUID();
        userA = createUser(tenantA, "op-user-a");
        UUID userB = createUser(tenantB, "op-user-b");
        employeeA = createEmployee(tenantA, "OP-A", userA);
        employeeB = createEmployee(tenantB, "OP-B", userB);

        instanceA = createInstance(tenantA, userA);
        stepInstanceA = createStepInstance(tenantA, instanceA);

        UUID instanceB = createInstance(tenantB, userB);
        UUID stepInstanceB = createStepInstance(tenantB, instanceB);

        // Tenant A work: direct item for A, pool item where A is candidate,
        // a claimed pool item, and a completed item that must be excluded.
        createWorkItem(tenantA, stepInstanceA, "DIRECT", "CLAIMED", employeeA, null, "Direct task A");
        createWorkItem(tenantA, stepInstanceA, "WORK_POOL", "AVAILABLE", null, null, "Pool task A");
        addCandidate(tenantA, null, employeeA);
        UUID claimedA = createWorkItem(tenantA, stepInstanceA, "WORK_POOL", "CLAIMED", null, employeeA, "Claimed pool A");
        markCandidateClaimant(claimedA, employeeA);
        UUID done = createWorkItem(tenantA, stepInstanceA, "DIRECT", "COMPLETED", employeeA, null, "Completed A");

        // Tenant B work that must never leak into tenant A queries.
        createWorkItem(tenantB, stepInstanceB, "DIRECT", "CLAIMED", employeeB, null, "Direct task B");
        createWorkItem(tenantB, stepInstanceB, "WORK_POOL", "AVAILABLE", null, null, "Pool task B");

        // Open incident on tenant A only.
        createIncident(tenantA, instanceA, "OPEN", "TEST_FAILURE");
        createIncident(tenantB, instanceB, "OPEN", "OTHER_FAILURE");
    }

    @Test
    void myTasksQueryReturnsOnlyCurrentEmployeesTenantWork() {
        List<OperationalTaskRow> rows = operationalQueries.findMyTasks(tenantA, employeeA, 50);

        assertThat(rows).isNotEmpty();
        assertThat(rows).allSatisfy(row -> assertThat(row.title()).doesNotContain("B"));
        // Completed work is excluded from the active task list.
        assertThat(rows).noneMatch(row -> row.title().equals("Completed A"));
        assertThat(rows).extracting(OperationalTaskRow::title)
                .contains("Direct task A", "Claimed pool A");

        UUID otherTenant = UUID.randomUUID();
        assertThat(operationalQueries.findMyTasks(otherTenant, employeeA, 50)).isEmpty();
        assertThat(operationalQueries.findMyTasks(tenantA, employeeB, 50)).isEmpty();
    }

    @Test
    void poolQueryReturnsOnlyCandidateWorkInTenant() {
        List<OperationalTaskRow> rows = operationalQueries.findPoolTasks(tenantA, employeeA, 50);
        assertThat(rows).extracting(OperationalTaskRow::title).containsExactly("Pool task A");
        assertThat(operationalQueries.findPoolTasks(tenantA, employeeB, 50)).isEmpty();
    }

    @Test
    void limitIsEnforced() {
        assertThat(operationalQueries.findMyTasks(tenantA, employeeA, 1)).hasSize(1);
        assertThat(operationalQueries.findPoolTasks(tenantA, employeeA, 1)).hasSize(1);
    }

    @Test
    void openIncidentsAreTenantScoped() {
        List<java.util.Map<String, Object>> incidentsA =
                operationalQueries.openIncidents(tenantA, 50);
        assertThat(incidentsA).hasSize(1);
        assertThat(incidentsA.get(0).get("source")).isEqualTo("TEST_FAILURE");

        List<java.util.Map<String, Object>> incidentsB =
                operationalQueries.openIncidents(tenantB, 50);
        assertThat(incidentsB).hasSize(1);
        assertThat(incidentsB.get(0).get("source")).isEqualTo("OTHER_FAILURE");
    }

    @Test
    void instanceSearchFiltersByStatusAndTenant() {
        List<java.util.Map<String, Object>> running =
                operationalQueries.searchInstances(tenantA, "RUNNING", 50);
        assertThat(running).hasSize(1);
        List<java.util.Map<String, Object>> completed =
                operationalQueries.searchInstances(tenantA, "COMPLETED", 50);
        assertThat(completed).isEmpty();
        List<java.util.Map<String, Object>> other =
                operationalQueries.searchInstances(UUID.randomUUID(), "RUNNING", 50);
        assertThat(other).isEmpty();
    }

    @Test
    void monitoringSnapshotMirrorsCommittedState() {
        MonitoringSnapshot snapshot = operationalQueries.monitoringSnapshot(tenantA);

        assertThat(snapshot.availableWorkItems()).isEqualTo(1);
        assertThat(snapshot.openIncidents()).isEqualTo(1);
        assertThat(snapshot.openIncidentAgeMinutes()).isGreaterThanOrEqualTo(0);
        // No overdue items in this fixture.
        assertThat(snapshot.overdueSteps()).isZero();
        assertThat(snapshot.overdueApprovals()).isZero();

        MonitoringSnapshot otherTenant = operationalQueries.monitoringSnapshot(tenantB);
        assertThat(otherTenant.availableWorkItems()).isEqualTo(1);
        assertThat(otherTenant.openIncidents()).isEqualTo(1);
    }

    // ===== fixture helpers =====

    private UUID createUser(UUID tenantId, String prefix) {
        UUID id = UUID.randomUUID();
        var now = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO tenants (id,name,subdomain,status,created_at,updated_at) "
                        + "VALUES (?, ?, ?, 'ACTIVE', ?, ?)",
                tenantId, "Operational " + prefix,
                "wf-op-" + tenantId.toString().substring(0, 8), now, now);
        jdbc.update("INSERT INTO users (id,tenant_id,email,display_name,status,password_hash,created_at,updated_at) "
                        + "VALUES (?, ?, ?, ?, 'ACTIVE', 'dummy', ?, ?)",
                id, tenantId, prefix + "-" + id.toString().substring(0, 8) + "@test",
                "Operational User", now, now);
        return id;
    }

    private UUID createEmployee(UUID tenantId, String number, UUID linkedUserId) {
        UUID id = UUID.randomUUID();
        var now = Timestamp.from(Instant.now());
        jdbc.update("""
                INSERT INTO hr_employees (
                    id, tenant_id, user_id, employee_number, first_name, last_name, display_name,
                    employment_type, status, created_at, updated_at
                ) VALUES (?, ?, ?, ?, 'Operational', 'Employee', 'Operational Employee',
                          'FULL_TIME', 'ACTIVE', ?, ?)
                """, id, tenantId, linkedUserId, number + "-" + id.toString().substring(0, 8), now, now);
        return id;
    }

    private UUID createInstance(UUID tenantId, UUID startedBy) {
        UUID instanceId = UUID.randomUUID();
        UUID definitionId = UUID.randomUUID();
        var now = Timestamp.from(Instant.now());
        jdbc.update("""
                INSERT INTO workflow_definitions (
                    id, tenant_id, definition_family_id, code, name, module, version, status,
                    trigger_type, created_by, version_lock, engine_generation, publication_state,
                    schema_version, created_at, updated_at
                ) VALUES (?, ?, ?, 'WF-OP', 'Operational Fixture', 'GENERAL', 1, 'ACTIVE',
                          'MANUAL', ?, 0, 'LEGACY', 'DRAFT', 1, ?, ?)
                """, definitionId, tenantId, definitionId, startedBy, now, now);
        jdbc.update("""
                INSERT INTO workflow_instances (
                    id, tenant_id, workflow_definition_id, workflow_version, business_entity_type,
                    business_entity_id, status, started_by, started_at, version, created_at, updated_at
                ) VALUES (?, ?, ?, 1, 'TEST', gen_random_uuid(), 'RUNNING', ?, ?, 0, ?, ?)
                """, instanceId, tenantId, definitionId, startedBy, now, now, now);
        return instanceId;
    }

    private UUID createStepInstance(UUID tenantId, UUID instanceId) {
        UUID stepInstanceId = UUID.randomUUID();
        UUID stepDefId = UUID.randomUUID();
        var now = Timestamp.from(Instant.now());
        UUID definitionId = jdbc.queryForObject(
                "SELECT workflow_definition_id FROM workflow_instances WHERE tenant_id = ? AND id = ?",
                UUID.class, tenantId, instanceId);
        jdbc.update("""
                INSERT INTO workflow_steps (
                    id, tenant_id, workflow_definition_id, step_key, name, step_type,
                    sequence_order, configuration, version, created_at, updated_at
                ) VALUES (?, ?, ?, 'task', 'Task', 'HUMAN_TASK', 1, CAST('{}' AS jsonb), 0, ?, ?)
                """, stepDefId, tenantId, definitionId, now, now);
        jdbc.update("""
                INSERT INTO workflow_step_instances (
                    id, tenant_id, workflow_instance_id, workflow_step_id, step_key,
                    status, version, created_at, updated_at
                ) VALUES (?, ?, ?, ?, 'task', 'PENDING', 0, ?, ?)
                """, stepInstanceId, tenantId, instanceId, stepDefId, now, now);
        return stepInstanceId;
    }

    private UUID createWorkItem(UUID tenantId, UUID stepInstanceId, String mode,
                                String status, UUID assignee, UUID claimant, String title) {
        UUID workItemId = UUID.randomUUID();
        var now = Timestamp.from(Instant.now());
        jdbc.update("""
                INSERT INTO workflow_work_items (
                    id, tenant_id, workflow_instance_id, workflow_step_instance_id,
                    type, status, assignee_employee_id, claimed_by_employee_id, assignment_mode,
                    source_module, source_entity_type, source_entity_id, title,
                    priority, version, created_at, updated_at
                ) VALUES (?, ?, (SELECT workflow_instance_id FROM workflow_step_instances WHERE tenant_id = ? AND id = ?),
                          ?, 'HUMAN_TASK', ?, ?, ?, ?, 'TEST', 'CASE', gen_random_uuid(), ?, 0, 0, ?, ?)
                """, workItemId, tenantId, tenantId, stepInstanceId, stepInstanceId,
                status, assignee, claimant, mode, title, now, now);
        return workItemId;
    }

    private UUID poolWorkItemTenantA;

    private void addCandidate(UUID tenantId, UUID workItemId, UUID employeeId) {
        // Attach the candidate to the most recent AVAILABLE pool item of the tenant.
        UUID target = jdbc.queryForObject("""
                SELECT id FROM workflow_work_items
                WHERE tenant_id = ? AND status = 'AVAILABLE' AND assignment_mode = 'WORK_POOL'
                ORDER BY created_at DESC LIMIT 1
                """, UUID.class, tenantId);
        jdbc.update("""
                INSERT INTO workflow_work_item_candidates (
                    tenant_id, work_item_id, employee_id, resolution_source, resolved_at, snapshot_metadata
                ) VALUES (?, ?, ?, 'TEST_RULE', NOW(), '{}')
                """, tenantId, target, employeeId);
    }

    private void markCandidateClaimant(UUID workItemId, UUID employeeId) {
        jdbc.update("""
                INSERT INTO workflow_work_item_candidates (
                    tenant_id, work_item_id, employee_id, resolution_source, resolved_at, snapshot_metadata
                ) VALUES (?, ?, ?, 'TEST_RULE', NOW(), '{}')
                """, tenantA, workItemId, employeeId);
    }

    private void createIncident(UUID tenantId, UUID instanceId, String status, String source) {
        jdbc.update("""
                INSERT INTO workflow_incidents (
                    id, tenant_id, workflow_instance_id, source, severity, failure_category,
                    status, created_at, updated_at
                ) VALUES (?, ?, ?, ?, 'HIGH', 'TEST', ?, NOW(), NOW())
                """, UUID.randomUUID(), tenantId, instanceId, source, status);
    }
}
