package com.sanad.platform.workflow;

import com.sanad.platform.security.SecurityPermitAllTestConfig;
import com.sanad.platform.workflow.application.WorkflowDelegationService;
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
 * Wave 2 / Task 12 — delegation policy with B1 dominance (G3/B1).
 *
 * <p>Proves a hard-disabled linked user makes assigned work
 * ASSIGNEE_UNAVAILABLE with NO automatic reassignment, that delegations
 * resolve only inside their validity window, and that malformed windows
 * fail closed.</p>
 */
@SpringBootTest
@ActiveProfiles("local")
@Import(SecurityPermitAllTestConfig.class)
@Transactional
class WorkflowDelegationPolicyTest {

    @Autowired
    private WorkflowDelegationService delegationService;

    @Autowired
    private JdbcTemplate jdbc;

    private UUID tenantId;
    private UUID instanceId;
    private UUID stepInstanceId;
    private UUID definitionId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        var now = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO tenants (id,name,subdomain,status,created_at,updated_at) "
                        + "VALUES (?, 'Delegation Policy', ?, 'ACTIVE', ?, ?)",
                tenantId, "wf-del-" + tenantId.toString().substring(0, 8), now, now);

        // G0 fail-closed RLS (V20260905_5): production applies the JWT tenant via
        // TenantRlsConnectionHandler (SET LOCAL per transaction); the fixture mirrors that contract.
        jdbc.execute("SELECT set_config('app.tenant_id', '" + tenantId + "', true)");

        UUID startUserId = createUser("del-starter");
        definitionId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO workflow_definitions (
                    id, tenant_id, definition_family_id, code, name, module, version, status,
                    trigger_type, created_by, version_lock, engine_generation, publication_state,
                    schema_version, created_at, updated_at
                ) VALUES (?, ?, ?, 'WF-DEL', 'Delegation Fixture', 'GENERAL', 1, 'ACTIVE',
                          'MANUAL', ?, 0, 'LEGACY', 'DRAFT', 1, ?, ?)
                """, definitionId, tenantId, definitionId, startUserId, now, now);
        jdbc.update("""
                INSERT INTO workflow_instances (
                    id, tenant_id, workflow_definition_id, workflow_version, business_entity_type,
                    business_entity_id, status, started_by, started_at, version, created_at, updated_at
                ) VALUES (?, ?, ?, 1, 'TEST', gen_random_uuid(), 'RUNNING', ?, ?, 0, ?, ?)
                """, instanceId = UUID.randomUUID(), tenantId, definitionId, startUserId, now, now, now);
        UUID stepDefId = UUID.randomUUID();
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
                """, stepInstanceId = UUID.randomUUID(), tenantId, instanceId, stepDefId, now, now);
    }

    @Test
    void disabledUserDoesNotTriggerManagerAutoReassignment() {
        // Assignee with NO linked user at all (worst-case B1 scenario).
        UUID assignee = createEmployee("B1-A", null, "ACTIVE");
        UUID manager = createEmployee("B1-MGR", createUser("del-mgr"), "ACTIVE");
        setManager(assignee, manager);
        UUID workItemId = createWorkItem(assignee);

        var result = delegationService.resolveExistingUnavailableAssignment(tenantId, workItemId);

        assertThat(result.status()).isEqualTo("ASSIGNEE_UNAVAILABLE");
        assertThat(result.reassignedEmployeeId()).isNull();
        String liveStatus = jdbc.queryForObject(
                "SELECT status FROM workflow_work_items WHERE tenant_id = ? AND id = ?",
                String.class, tenantId, workItemId);
        assertThat(liveStatus).isEqualTo("ASSIGNEE_UNAVAILABLE");
    }

    @Test
    void disabledLinkedUserMarksWorkUnavailable() {
        UUID disabledUserId = createUser("del-disabled");
        jdbc.update("UPDATE users SET status = 'SUSPENDED' WHERE tenant_id = ? AND id = ?",
                tenantId, disabledUserId);
        UUID assignee = createEmployee("B1-B", disabledUserId, "ACTIVE");
        UUID workItemId = createWorkItem(assignee);

        var result = delegationService.resolveExistingUnavailableAssignment(tenantId, workItemId);
        assertThat(result.status()).isEqualTo("ASSIGNEE_UNAVAILABLE");
        assertThat(result.reassignedEmployeeId()).isNull();
    }

    @Test
    void activeLinkedUserKeepsWorkActionable() {
        UUID assignee = createEmployee("B1-C", createUser("del-ok"), "ACTIVE");
        UUID workItemId = createWorkItem(assignee);

        var result = delegationService.resolveExistingUnavailableAssignment(tenantId, workItemId);
        assertThat(result.status()).isEqualTo("CLAIMED");
        assertThat(result.reassignedEmployeeId()).isNull();
    }

    @Test
    void delegationResolvesOnlyInsideWindow() {
        UUID delegator = createEmployee("DEL-D", createUser("del-d"), "ACTIVE");
        UUID delegate = createEmployee("DEL-E", createUser("del-e"), "ACTIVE");
        Instant now = Instant.now();
        delegationService.createDelegation(tenantId, delegator, delegate,
                now.minusSeconds(3600), now.plusSeconds(3600), null);

        assertThat(delegationService.activeDelegateFor(tenantId, delegator, now, null, null, null))
                .contains(delegate);
        assertThat(delegationService.activeDelegateFor(tenantId, delegator,
                now.plusSeconds(7200), null, null, null)).isEmpty();
    }

    @Test
    void malformedDelegationWindowFailsClosed() {
        UUID delegator = createEmployee("DEL-F", createUser("del-f"), "ACTIVE");
        UUID delegate = createEmployee("DEL-G", createUser("del-g"), "ACTIVE");
        Instant now = Instant.now();
        assertThatThrownBy(() -> delegationService.createDelegation(
                tenantId, delegator, delegate, now, now, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ===== fixture helpers =====

    private UUID createUser(String prefix) {
        UUID id = UUID.randomUUID();
        var now = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO users (id,tenant_id,email,display_name,status,password_hash,created_at,updated_at) "
                        + "VALUES (?, ?, ?, ?, 'ACTIVE', 'dummy', ?, ?)",
                id, tenantId, prefix + "-" + id.toString().substring(0, 8) + "@test",
                "Delegation User", now, now);
        return id;
    }

    private UUID createEmployee(String number, UUID linkedUserId, String status) {
        UUID id = UUID.randomUUID();
        var now = Timestamp.from(Instant.now());
        jdbc.update("""
                INSERT INTO hr_employees (
                    id, tenant_id, user_id, employee_number, first_name, last_name, display_name,
                    employment_type, status, created_at, updated_at
                ) VALUES (?, ?, ?, ?, 'Delegation', 'Employee', 'Delegation Employee',
                          'FULL_TIME', ?, ?, ?)
                """, id, tenantId, linkedUserId, number + "-" + id.toString().substring(0, 8),
                status, now, now);
        return id;
    }

    private void setManager(UUID employeeId, UUID managerId) {
        jdbc.update("UPDATE hr_employees SET manager_id = ? WHERE tenant_id = ? AND id = ?",
                managerId, tenantId, employeeId);
    }

    private UUID createWorkItem(UUID assigneeEmployeeId) {
        UUID workItemId = UUID.randomUUID();
        var now = Timestamp.from(Instant.now());
        jdbc.update("""
                INSERT INTO workflow_work_items (
                    id, tenant_id, workflow_instance_id, workflow_step_instance_id,
                    type, status, assignee_employee_id, assignment_mode,
                    source_module, source_entity_type, source_entity_id, title,
                    priority, version, created_at, updated_at
                ) VALUES (?, ?, ?, ?, 'HUMAN_TASK', 'CLAIMED', ?, 'DIRECT',
                          'TEST', 'CASE', gen_random_uuid(), 'B1 fixture', 0, 0, ?, ?)
                """, workItemId, tenantId, instanceId, stepInstanceId, assigneeEmployeeId, now, now);
        return workItemId;
    }
}
