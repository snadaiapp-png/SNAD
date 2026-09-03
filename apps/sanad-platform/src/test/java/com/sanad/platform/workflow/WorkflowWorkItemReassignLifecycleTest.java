package com.sanad.platform.workflow;

import com.sanad.platform.security.SecurityPermitAllTestConfig;
import com.sanad.platform.workflow.application.WorkflowWorkItemService;
import com.sanad.platform.workflow.domain.WorkflowWorkItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Regression: an explicit DIRECT reassignment transfers actionability. */
@SpringBootTest
@ActiveProfiles("local")
@Import(SecurityPermitAllTestConfig.class)
class WorkflowWorkItemReassignLifecycleTest {

    @Autowired
    WorkflowWorkItemService workItemService;

    @Autowired
    JdbcTemplate jdbc;

    private UUID tenantId;
    private UUID userId;
    private UUID instanceId;
    private UUID stepInstanceId;
    private UUID employeeA;
    private UUID employeeB;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        userId = UUID.randomUUID();
        var now = Timestamp.from(Instant.now());

        jdbc.update("INSERT INTO tenants (id,name,subdomain,status,created_at,updated_at) VALUES (?, 'Reassign Test', ?, 'ACTIVE', ?, ?)",
                tenantId, "wi-reassign-" + tenantId.toString().substring(0, 8), now, now);
        jdbc.update("INSERT INTO users (id,tenant_id,email,display_name,status,password_hash,created_at,updated_at) VALUES (?, ?, ?, 'Reassign User', 'ACTIVE', 'dummy', ?, ?)",
                userId, tenantId, "wi-reassign-" + userId.toString().substring(0, 8) + "@test", now, now);

        employeeA = createEmployee("RA");
        employeeB = createEmployee("RB");

        UUID definitionId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO workflow_definitions (
                    id, tenant_id, definition_family_id, code, name, module, version, status,
                    trigger_type, created_by, version_lock, engine_generation, publication_state,
                    schema_version, created_at, updated_at
                ) VALUES (?, ?, ?, ?, 'Reassign Fixture', 'GENERAL', 1, 'ACTIVE',
                          'MANUAL', ?, 0, 'LEGACY', 'DRAFT', 1, ?, ?)
                """, definitionId, tenantId, definitionId,
                "WF-REASSIGN-" + definitionId.toString().substring(0, 8), userId, now, now);

        instanceId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO workflow_instances (
                    id, tenant_id, workflow_definition_id, workflow_version, business_entity_type,
                    business_entity_id, status, started_by, started_at, version, created_at, updated_at
                ) VALUES (?, ?, ?, 1, 'TEST', gen_random_uuid(), 'RUNNING', ?, ?, 0, ?, ?)
                """, instanceId, tenantId, definitionId, userId, now, now, now);

        UUID stepDefId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO workflow_steps (
                    id, tenant_id, workflow_definition_id, step_key, name, step_type,
                    sequence_order, configuration, version, created_at, updated_at
                ) VALUES (?, ?, ?, 'review', 'Review', 'HUMAN_TASK', 1, CAST('{}' AS jsonb), 0, ?, ?)
                """, stepDefId, tenantId, definitionId, now, now);

        stepInstanceId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO workflow_step_instances (
                    id, tenant_id, workflow_instance_id, workflow_step_id, step_key,
                    status, version, created_at, updated_at
                ) VALUES (?, ?, ?, ?, 'review', 'PENDING', 0, ?, ?)
                """, stepInstanceId, tenantId, instanceId, stepDefId, now, now);
    }

    @Test
    void reassignedDirectItemCanBeCompletedByNewAssignee() {
        WorkflowWorkItem item = WorkflowWorkItem.create(
                tenantId, instanceId, stepInstanceId,
                WorkflowWorkItem.Type.HUMAN_TASK,
                WorkflowWorkItem.AssignmentMode.DIRECT,
                employeeA,
                "WORKFLOW", "INSTANCE", instanceId,
                "Review", null, 0, null, null);
        item = workItemService.create(item, List.of());

        WorkflowWorkItem reassigned = workItemService.reassign(
                tenantId, item.id(), employeeB, employeeA, item.version(), "coverage handoff");

        assertThat(reassigned.status()).isEqualTo(WorkflowWorkItem.Status.CLAIMED);
        assertThat(reassigned.assigneeEmployeeId()).isEqualTo(employeeB);
        assertThat(reassigned.claimedByEmployeeId()).isEqualTo(employeeB);
        assertThat(reassigned.claimedAt()).isNotNull();

        WorkflowWorkItem completed = workItemService.complete(
                tenantId, reassigned.id(), employeeB, reassigned.version());
        assertThat(completed.status()).isEqualTo(WorkflowWorkItem.Status.COMPLETED);
    }

    private UUID createEmployee(String prefix) {
        UUID employeeId = UUID.randomUUID();
        var now = Timestamp.from(Instant.now());
        jdbc.update("""
                INSERT INTO hr_employees (
                    id, tenant_id, employee_number, first_name, last_name, display_name,
                    employment_type, status, created_at, updated_at
                ) VALUES (?, ?, ?, 'Reassign', 'Employee', 'Reassign Employee',
                          'FULL_TIME', 'ACTIVE', ?, ?)
                """, employeeId, tenantId,
                prefix + "-" + employeeId.toString().substring(0, 8), now, now);
        return employeeId;
    }
}
