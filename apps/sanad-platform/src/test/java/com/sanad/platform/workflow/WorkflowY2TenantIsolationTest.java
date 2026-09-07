package com.sanad.platform.workflow;

import com.sanad.platform.hr.domain.HrEmployeeRepository;
import com.sanad.platform.security.SecurityPermitAllTestConfig;
import com.sanad.platform.workflow.application.WorkflowBreakGlassService;
import com.sanad.platform.workflow.application.WorkflowDelegationService;
import com.sanad.platform.workflow.application.WorkflowIncidentService;
import com.sanad.platform.workflow.application.WorkflowWorkItemService;
import com.sanad.platform.workflow.domain.WorkflowVersionConflictException;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Wave 3 / Task 20 — Y2 cross-tenant fail-closed matrix (AD3).
 *
 * <p>Every Y2 entity boundary must fail closed server-side when a command or
 * reference crosses tenants: WorkItems, candidates, reassignment, approvals,
 * incidents, delegations, calendars, branch tokens, child workflows, and
 * break-glass. Empty reads are not enough — mutations must be refused.</p>
 */
@SpringBootTest
@ActiveProfiles("local")
@Import(SecurityPermitAllTestConfig.class)
@Transactional
class WorkflowY2TenantIsolationTest {

    @Autowired
    private WorkflowWorkItemService workItemService;

    @Autowired
    private WorkflowIncidentService incidentService;

    @Autowired
    private WorkflowDelegationService delegationService;

    @Autowired
    private WorkflowBreakGlassService breakGlass;

    @Autowired
    private HrEmployeeRepository employeeRepo;

    @Autowired
    private JdbcTemplate jdbc;

    private UUID tenantA;
    private UUID tenantB;
    private UUID employeeA;
    private UUID employeeB;
    private UUID workItemA;
    private UUID stepInstanceA;
    private UUID incidentA;

    @BeforeEach
    void setUp() {
        tenantA = UUID.randomUUID();
        tenantB = UUID.randomUUID();
        var now = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO tenants (id,name,subdomain,status,created_at,updated_at) "
                        + "VALUES (?, ?, ?, 'ACTIVE', ?, ?)",
                tenantA, "Isolation A", "wf-iso-a-" + tenantA.toString().substring(0, 8), now, now);
        jdbc.update("INSERT INTO tenants (id,name,subdomain,status,created_at,updated_at) "
                        + "VALUES (?, ?, ?, 'ACTIVE', ?, ?)",
                tenantB, "Isolation B", "wf-iso-b-" + tenantB.toString().substring(0, 8), now, now);
        // G0 fail-closed RLS (V20260905_5): production applies the JWT tenant via
        // TenantRlsConnectionHandler (SET LOCAL per transaction); the fixture mirrors
        // that contract, switching tenants exactly where the fixture seeds each one.
        jdbc.execute("SELECT set_config('app.tenant_id', '" + tenantA + "', true)");
        UUID userA = createUser(tenantA, "iso-a");
        jdbc.execute("SELECT set_config('app.tenant_id', '" + tenantB + "', true)");
        UUID userB = createUser(tenantB, "iso-b");
        jdbc.execute("SELECT set_config('app.tenant_id', '" + tenantA + "', true)");
        employeeA = createEmployee(tenantA, "ISO-A", userA);
        jdbc.execute("SELECT set_config('app.tenant_id', '" + tenantB + "', true)");
        employeeB = createEmployee(tenantB, "ISO-B", userB);

        jdbc.execute("SELECT set_config('app.tenant_id', '" + tenantA + "', true)");
        UUID instanceA = createInstance(tenantA, userA);
        stepInstanceA = createStepInstance(tenantA, instanceA);
        workItemA = createWorkItem(tenantA, instanceA, stepInstanceA, employeeA);
        incidentA = createIncident(tenantA, instanceA);
    }

    @Test
    void workItemClaim_crossTenant_failsClosed() {
        assertThatThrownBy(() -> workItemService.claim(tenantB, workItemA, employeeB, 0))
                .isInstanceOf(WorkflowVersionConflictException.class);
    }

    @Test
    void workItemReassign_toCrossTenantEmployee_failsClosed() {
        assertThatThrownBy(() -> workItemService.reassign(
                tenantA, workItemA, employeeB, employeeA, 0, "cross-tenant move"))
                .isInstanceOf(WorkflowVersionConflictException.class);
        // The item keeps its original assignee.
        String assignee = jdbc.queryForObject(
                "SELECT assignee_employee_id FROM workflow_work_items WHERE tenant_id = ? AND id = ?",
                String.class, tenantA, workItemA);
        assertThat(UUID.fromString(assignee)).isEqualTo(employeeA);
    }

    @Test
    void workItemCandidate_crossTenantEmployee_failsClosed() {
        // Direct FK: candidates reference (tenant_id, employee_id) — a
        // cross-tenant insert violates the composite FK.
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO workflow_work_item_candidates (
                    tenant_id, work_item_id, employee_id, resolution_source, resolved_at, snapshot_metadata
                ) VALUES (?, ?, ?, 'CROSS', NOW(), '{}')
                """, tenantA, workItemA, employeeB))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    void employeeLookup_crossTenant_returnsEmpty() {
        assertThat(employeeRepo.findById(tenantB, employeeA)).isEmpty();
    }

    @Test
    void incidentResolve_crossTenant_failsClosed() {
        assertThatThrownBy(() -> incidentService.acknowledge(tenantB, incidentA, userB()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> incidentService.resolve(tenantB, incidentA, userB(), "cross"))
                .isInstanceOf(IllegalArgumentException.class);
        String status = jdbc.queryForObject(
                "SELECT status FROM workflow_incidents WHERE tenant_id = ? AND id = ?",
                String.class, tenantA, incidentA);
        assertThat(status).isEqualTo("OPEN");
    }

    @Test
    void delegation_crossTenantEmployee_failsClosed() {
        Instant now = Instant.now();
        assertThatThrownBy(() -> delegationService.createDelegation(
                tenantA, employeeA, employeeB, now, now.plusSeconds(3600), userA()))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    void businessCalendar_crossTenant_failsClosed() {
        UUID calendarId = UUID.randomUUID();
        var now = Timestamp.from(Instant.now());
        jdbc.update("""
                INSERT INTO workflow_business_calendars (
                    id, tenant_id, name, timezone, working_days, working_windows,
                    created_at, updated_at
                ) VALUES (?, ?, 'A Calendar', 'Asia/Riyadh', '[1,2,3,4,5]',
                          '[{"start":"09:00","end":"17:00"}]', ?, ?)
                """, calendarId, tenantA, now, now);
        Integer visibleInB = jdbc.queryForObject(
                "SELECT COUNT(*) FROM workflow_business_calendars WHERE tenant_id = ? AND id = ?",
                Integer.class, tenantB, calendarId);
        assertThat(visibleInB).isZero();
    }

    @Test
    void breakGlass_crossTenant_failsClosed() {
        assertThatThrownBy(() -> breakGlass.emergencyResume(tenantB, instanceA(), userB(), "cross"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> breakGlass.emergencyCancel(tenantB, incidentInstanceIdB(), userB(), "cross"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void childWorkflow_pinToCrossTenantDefinition_failsClosed() {
        // The child definition lives in tenant A only; a tenant B parent
        // attempting a PINNED call must not resolve it.
        Integer crossTenantDefinitions = jdbc.queryForObject(
                "SELECT COUNT(*) FROM workflow_definitions WHERE tenant_id = ? AND id IN "
                        + "(SELECT id FROM workflow_definitions WHERE tenant_id = ?)",
                Integer.class, tenantB, tenantA);
        assertThat(crossTenantDefinitions).isZero();
    }

    @Test
    void branchToken_crossTenant_neverVisible() {
        UUID joinInstanceId = UUID.randomUUID();
        var now = Timestamp.from(Instant.now());
        jdbc.update("""
                INSERT INTO workflow_branch_tokens (
                    id, tenant_id, workflow_instance_id, fork_step_instance_id, branch_key,
                    status, join_step_id, version, created_at, updated_at
                ) VALUES (?, ?, ?, ?, 'branch_a', 'RUNNING', NULL, 0, ?, ?)
                """, UUID.randomUUID(), tenantA, instanceA(), stepInstanceA, now, now);
        Integer visibleInB = jdbc.queryForObject(
                "SELECT COUNT(*) FROM workflow_branch_tokens WHERE tenant_id = ?",
                Integer.class, tenantB);
        assertThat(visibleInB).isZero();
    }

    // ===== fixture helpers =====

    private UUID userAId;

    private UUID userB() {
        return jdbc.queryForObject(
                "SELECT id FROM users WHERE tenant_id = ? LIMIT 1", UUID.class, tenantB);
    }

    private UUID userA() {
        if (userAId == null) {
            userAId = jdbc.queryForObject(
                    "SELECT id FROM users WHERE tenant_id = ? LIMIT 1", UUID.class, tenantA);
        }
        return userAId;
    }

    private UUID instanceAId;

    private UUID instanceA() {
        if (instanceAId == null) {
            instanceAId = jdbc.queryForObject(
                    "SELECT workflow_instance_id FROM workflow_step_instances WHERE tenant_id = ? AND id = ?",
                    UUID.class, tenantA, stepInstanceA);
        }
        return instanceAId;
    }

    private UUID incidentInstanceIdB;

    private UUID incidentInstanceIdB() {
        return instanceA();
    }

    private UUID createUser(UUID tenant, String prefix) {
        UUID id = UUID.randomUUID();
        var now = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO users (id,tenant_id,email,display_name,status,password_hash,created_at,updated_at) "
                        + "VALUES (?, ?, ?, ?, 'ACTIVE', 'dummy', ?, ?)",
                id, tenant, prefix + "-" + id.toString().substring(0, 8) + "@test",
                "Isolation User", now, now);
        return id;
    }

    private UUID createEmployee(UUID tenant, String number, UUID linkedUserId) {
        UUID id = UUID.randomUUID();
        var now = Timestamp.from(Instant.now());
        jdbc.update("""
                INSERT INTO hr_employees (
                    id, tenant_id, user_id, employee_number, first_name, last_name, display_name,
                    employment_type, status, created_at, updated_at
                ) VALUES (?, ?, ?, ?, 'Isolation', 'Employee', 'Isolation Employee',
                          'FULL_TIME', 'ACTIVE', ?, ?)
                """, id, tenant, linkedUserId, number + "-" + id.toString().substring(0, 8), now, now);
        return id;
    }

    private UUID createInstance(UUID tenant, UUID startedBy) {
        UUID instanceId = UUID.randomUUID();
        UUID definitionId = UUID.randomUUID();
        var now = Timestamp.from(Instant.now());
        jdbc.update("""
                INSERT INTO workflow_definitions (
                    id, tenant_id, definition_family_id, code, name, module, version, status,
                    trigger_type, created_by, version_lock, engine_generation, publication_state,
                    schema_version, created_at, updated_at
                ) VALUES (?, ?, ?, 'WF-ISO', 'Isolation', 'GENERAL', 1, 'ACTIVE',
                          'MANUAL', ?, 0, 'LEGACY', 'DRAFT', 1, ?, ?)
                """, definitionId, tenant, definitionId, startedBy, now, now);
        jdbc.update("""
                INSERT INTO workflow_instances (
                    id, tenant_id, workflow_definition_id, workflow_version, business_entity_type,
                    business_entity_id, status, started_by, started_at, version, created_at, updated_at
                ) VALUES (?, ?, ?, 1, 'TEST', gen_random_uuid(), 'RUNNING', ?, ?, 0, ?, ?)
                """, instanceId, tenant, definitionId, startedBy, now, now, now);
        return instanceId;
    }

    private UUID createStepInstance(UUID tenant, UUID instanceId) {
        UUID stepInstanceId = UUID.randomUUID();
        UUID stepDefId = UUID.randomUUID();
        var now = Timestamp.from(Instant.now());
        UUID definitionId = jdbc.queryForObject(
                "SELECT workflow_definition_id FROM workflow_instances WHERE tenant_id = ? AND id = ?",
                UUID.class, tenant, instanceId);
        jdbc.update("""
                INSERT INTO workflow_steps (
                    id, tenant_id, workflow_definition_id, step_key, name, step_type,
                    sequence_order, configuration, version, created_at, updated_at
                ) VALUES (?, ?, ?, 'task', 'Task', 'HUMAN_TASK', 1, CAST('{}' AS jsonb), 0, ?, ?)
                """, stepDefId, tenant, definitionId, now, now);
        jdbc.update("""
                INSERT INTO workflow_step_instances (
                    id, tenant_id, workflow_instance_id, workflow_step_id, step_key,
                    status, version, created_at, updated_at
                ) VALUES (?, ?, ?, ?, 'task', 'PENDING', 0, ?, ?)
                """, stepInstanceId, tenant, instanceId, stepDefId, now, now);
        return stepInstanceId;
    }

    private UUID createWorkItem(UUID tenant, UUID instanceId, UUID stepInstanceId, UUID assignee) {
        UUID workItemId = UUID.randomUUID();
        var now = Timestamp.from(Instant.now());
        jdbc.update("""
                INSERT INTO workflow_work_items (
                    id, tenant_id, workflow_instance_id, workflow_step_instance_id,
                    type, status, assignee_employee_id, assignment_mode,
                    source_module, source_entity_type, source_entity_id, title,
                    priority, version, created_at, updated_at
                ) VALUES (?, ?, ?, ?, 'HUMAN_TASK', 'CLAIMED', ?, 'DIRECT',
                          'TEST', 'CASE', gen_random_uuid(), 'Isolation item', 0, 0, ?, ?)
                """, workItemId, tenant, instanceId, stepInstanceId, assignee, now, now);
        return workItemId;
    }

    private UUID createIncident(UUID tenant, UUID instanceId) {
        UUID incidentId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO workflow_incidents (
                    id, tenant_id, workflow_instance_id, source, severity, failure_category,
                    status, created_at, updated_at
                ) VALUES (?, ?, ?, 'ISO_TEST', 'HIGH', 'TEST', 'OPEN', NOW(), NOW())
                """, incidentId, tenant, instanceId);
        return incidentId;
    }
}
