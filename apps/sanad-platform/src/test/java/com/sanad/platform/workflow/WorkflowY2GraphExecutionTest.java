package com.sanad.platform.workflow;

import com.sanad.platform.security.SecurityPermitAllTestConfig;
import com.sanad.platform.workflow.application.WorkflowExecutionService;
import com.sanad.platform.workflow.application.WorkflowGraphExecutionService;
import com.sanad.platform.workflow.domain.WorkflowInstance;
import com.sanad.platform.workflow.domain.WorkflowWorkItem;
import com.sanad.platform.workflow.application.WorkflowWorkItemService;
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
 * Wave 2 / Task 10 — engine-generation routing and Y2 graph execution.
 *
 * <p>Proves the no-dual-engine invariant in both directions (AA3): a Y2
 * instance cannot advance through the legacy linear command, a LEGACY
 * instance cannot advance through the Y2 graph command, and a Y2 graph
 * advance resolves transitions deterministically, activates central
 * WorkItems for human steps, and completes at END.</p>
 */
@SpringBootTest
@ActiveProfiles("local")
@Import(SecurityPermitAllTestConfig.class)
@Transactional
class WorkflowY2GraphExecutionTest {

    @Autowired
    private WorkflowExecutionService executionService;

    @Autowired
    private WorkflowGraphExecutionService graphExecutionService;

    @Autowired
    private WorkflowWorkItemService workItemService;

    @Autowired
    private JdbcTemplate jdbc;

    private UUID tenantId;
    private UUID userId;
    private UUID employeeId;
    private UUID definitionId;
    private UUID startStepId;
    private UUID taskStepId;
    private UUID endStepId;
    private UUID instanceId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        userId = UUID.randomUUID();
        var now = Timestamp.from(Instant.now());

        jdbc.update("INSERT INTO tenants (id,name,subdomain,status,created_at,updated_at) "
                        + "VALUES (?, 'Y2 Graph Test', ?, 'ACTIVE', ?, ?)",
                tenantId, "y2-graph-" + tenantId.toString().substring(0, 8), now, now);
        jdbc.update("INSERT INTO users (id,tenant_id,email,display_name,status,password_hash,created_at,updated_at) "
                        + "VALUES (?, ?, ?, 'Y2 Graph User', 'ACTIVE', 'dummy', ?, ?)",
                userId, tenantId, "y2-graph-" + userId.toString().substring(0, 8) + "@test", now, now);

        // G0 fail-closed RLS (V20260905_5): production applies the JWT tenant via
        // TenantRlsConnectionHandler (SET LOCAL per transaction); the fixture mirrors that contract.
        jdbc.execute("SELECT set_config('app.tenant_id', '" + tenantId + "', true)");

        // Employee with an ACTIVE linked user holding ADMIN (grants WORKFLOW.TASK_EXECUTE)
        UUID linkedUserId = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id,tenant_id,email,display_name,status,password_hash,created_at,updated_at) "
                        + "VALUES (?, ?, ?, 'Pool Candidate', 'ACTIVE', 'dummy', ?, ?)",
                linkedUserId, tenantId, "y2-cand-" + linkedUserId.toString().substring(0, 8) + "@test", now, now);
        employeeId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO hr_employees (
                    id, tenant_id, user_id, employee_number, first_name, last_name, display_name,
                    employment_type, status, created_at, updated_at
                ) VALUES (?, ?, ?, 'E-Y2', 'Graph', 'Candidate', 'Graph Candidate',
                          'FULL_TIME', 'ACTIVE', ?, ?)
                """, employeeId, tenantId, linkedUserId, now, now);
        UUID adminRole = UUID.randomUUID();
        jdbc.update("INSERT INTO roles (id, tenant_id, code, name, status, created_at, updated_at) "
                        + "VALUES (?, ?, 'ADMIN', 'Administrator', 'ACTIVE', ?, ?)",
                adminRole, tenantId, now, now);
        jdbc.update("""
                INSERT INTO role_capabilities (id, tenant_id, role_id, capability_id, created_at)
                SELECT ?, ?, ?, id, NOW() FROM access_capabilities WHERE code = 'WORKFLOW.TASK_EXECUTE'
                """, UUID.randomUUID(), tenantId, adminRole);
        jdbc.update("""
                INSERT INTO user_role_assignments (id, tenant_id, user_id, role_id, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'ACTIVE', ?, ?)
                """, UUID.randomUUID(), tenantId, linkedUserId, adminRole, now, now);

        // Y2 definition: START -> HUMAN_TASK -> END
        definitionId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO workflow_definitions (
                    id, tenant_id, definition_family_id, code, name, module, version, status,
                    trigger_type, created_by, version_lock, engine_generation, publication_state,
                    schema_version, created_at, updated_at
                ) VALUES (?, ?, ?, 'WF-Y2-GRAPH', 'Y2 Graph', 'GENERAL', 1, 'ACTIVE',
                          'MANUAL', ?, 0, 'Y2', 'PUBLISHED', 1, ?, ?)
                """, definitionId, tenantId, definitionId, userId, now, now);
        startStepId = createStep("start", "START", null);
        taskStepId = createStep("review", "HUMAN_TASK", "WORKFLOW.TASK_EXECUTE");
        endStepId = createStep("end", "END", null);
        createTransition(startStepId, taskStepId, "begin", "SUCCESS");
        createTransition(taskStepId, endStepId, "done", "SUCCESS");
    }

    private void createY2Instance(String currentStepKey) {
        instanceId = UUID.randomUUID();
        var now = Timestamp.from(Instant.now());
        jdbc.update("""
                INSERT INTO workflow_instances (
                    id, tenant_id, workflow_definition_id, workflow_version, business_entity_type,
                    business_entity_id, status, current_step_key, started_by, started_at,
                    engine_generation, definition_family_id, definition_version_id,
                    context_json, context_schema_version, version, created_at, updated_at
                ) VALUES (?, ?, ?, 1, 'TEST', gen_random_uuid(), 'RUNNING', ?, ?,
                          ?, 'Y2', ?, ?, CAST('{}' AS jsonb), 1, 0, ?, ?)
                """, instanceId, tenantId, definitionId, currentStepKey, userId, now,
                definitionId, definitionId, now, now);
        UUID stepInstanceId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO workflow_step_instances (
                    id, tenant_id, workflow_instance_id, workflow_step_id, step_key,
                    status, version, created_at, updated_at
                ) VALUES (?, ?, ?, ?, 'start', 'PENDING', 0, ?, ?)
                """, stepInstanceId, tenantId, instanceId, startStepId, now, now);
    }

    @Test
    void y2InstanceNeverUsesLegacyNextStepCommand() {
        createY2Instance("start");
        assertThatThrownBy(() -> executionService.advanceToNextStep(tenantId, instanceId, "review", userId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Y2 graph");
    }

    @Test
    void legacyInstanceCannotAdvanceThroughY2Graph() {
        // LEGACY instance (default generation)
        UUID legacyInstance = UUID.randomUUID();
        var now = Timestamp.from(Instant.now());
        jdbc.update("""
                INSERT INTO workflow_instances (
                    id, tenant_id, workflow_definition_id, workflow_version, business_entity_type,
                    business_entity_id, status, current_step_key, started_by, started_at,
                    engine_generation, context_json, context_schema_version, version, created_at, updated_at
                ) VALUES (?, ?, ?, 1, 'TEST', gen_random_uuid(), 'RUNNING', 'start', ?, ?,
                          'LEGACY', CAST('{}' AS jsonb), 1, 0, ?, ?)
                """, legacyInstance, tenantId, definitionId, userId, now, now, now);

        assertThatThrownBy(() -> graphExecutionService.advance(tenantId, legacyInstance, "SUCCESS", userId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("LEGACY");
    }

    @Test
    void y2GraphAdvanceActivatesPoolWorkItemThenCompletesAtEnd() {
        createY2Instance("start");

        var afterBegin = graphExecutionService.advance(tenantId, instanceId, "SUCCESS", userId);
        assertThat(afterBegin.currentStepKey()).isEqualTo("review");

        // Pool WorkItem activated with the resolved candidate
        var pool = workItemService.findPoolWork(tenantId, employeeId, 10);
        assertThat(pool).hasSize(1);
        assertThat(pool.get(0).type()).isEqualTo(WorkflowWorkItem.Type.HUMAN_TASK);
        assertThat(pool.get(0).status()).isEqualTo(WorkflowWorkItem.Status.AVAILABLE);

        // An ambiguous second transition for the same outcome is an incident, not a guess
        createTransition(taskStepId, endStepId, "done-alias", "SUCCESS");
        assertThatThrownBy(() -> graphExecutionService.advance(tenantId, instanceId, "SUCCESS", userId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Graph resolution incident");
        jdbc.update("DELETE FROM workflow_step_transitions WHERE transition_key = 'done-alias'");

        var completed = graphExecutionService.advance(tenantId, instanceId, "SUCCESS", userId);
        assertThat(completed.status()).isEqualTo(WorkflowInstance.Status.COMPLETED);
        assertThat(completed.currentStepKey()).isNull();
    }

    // ===== fixture helpers =====

    private UUID createStep(String stepKey, String stepType, String requiredCapability) {
        UUID stepId = UUID.randomUUID();
        var now = Timestamp.from(Instant.now());
        jdbc.update("""
                INSERT INTO workflow_steps (
                    id, tenant_id, workflow_definition_id, step_key, name, step_type,
                    sequence_order, configuration, required_capability, version, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, 1, CAST('{}' AS jsonb), ?, 0, ?, ?)
                """, stepId, tenantId, definitionId, stepKey, stepKey, stepType,
                requiredCapability, now, now);
        return stepId;
    }

    private void createTransition(UUID fromStep, UUID toStep, String key, String outcome) {
        var now = Timestamp.from(Instant.now());
        jdbc.update("""
                INSERT INTO workflow_step_transitions (
                    id, tenant_id, workflow_definition_id, from_step_id, to_step_id,
                    transition_key, outcome, priority, metadata, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, 10, CAST('{}' AS jsonb), ?, ?)
                """, UUID.randomUUID(), tenantId, definitionId, fromStep, toStep, key, outcome, now, now);
    }
}
