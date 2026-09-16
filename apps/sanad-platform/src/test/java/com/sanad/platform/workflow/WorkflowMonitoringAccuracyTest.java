package com.sanad.platform.workflow;

import com.sanad.platform.workflow.application.WorkflowDefinitionService;
import com.sanad.platform.workflow.application.WorkflowExecutionService;
import com.sanad.platform.workflow.application.WorkflowMonitoringService;
import com.sanad.platform.workflow.domain.WorkflowDefinition;
import com.sanad.platform.workflow.domain.WorkflowInstance;
import com.sanad.platform.workflow.domain.WorkflowStep;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RED regression for WF-AUD-06: monitoring totals must be authoritative
 * counts, not the first 200 matching rows.
 */
@SpringBootTest
@ActiveProfiles("local")
@Transactional
class WorkflowMonitoringAccuracyTest {

    @Autowired private WorkflowDefinitionService definitionService;
    @Autowired private WorkflowExecutionService executionService;
    @Autowired private WorkflowMonitoringService monitoringService;
    @Autowired private JdbcTemplate jdbc;

    private UUID tenantId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        userId = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO tenants (id,name,subdomain,status,created_at,updated_at) "
                        + "VALUES (?, 'Workflow monitoring tenant', ?, 'ACTIVE', ?, ?)",
                tenantId, "wf-mon-" + tenantId.toString().substring(0, 8), now, now);
        jdbc.update("INSERT INTO users (id,tenant_id,email,display_name,status,password_hash,created_at,updated_at) "
                        + "VALUES (?, ?, ?, 'Workflow monitoring user', 'ACTIVE', 'dummy', ?, ?)",
                userId, tenantId, "wf-mon-" + userId.toString().substring(0, 8) + "@test", now, now);
    }

    @Test
    void overdueStepCountIsNotTruncatedAtTwoHundred() {
        WorkflowDefinition definition = definitionService.create(
                WorkflowDefinition.create(
                        tenantId, "WF-MON-205", "Monitoring 205", "fixture", "GENERAL",
                        WorkflowDefinition.TriggerType.MANUAL, userId),
                userId);

        WorkflowStep step = definitionService.addStep(WorkflowStep.create(
                tenantId, definition.id(), "task", "Task",
                WorkflowStep.StepType.ACTION, 1, "{}", 1, null, null), userId);
        WorkflowDefinition active = definitionService.activate(tenantId, definition.id(), userId);

        WorkflowInstance instance = WorkflowInstance.start(
                tenantId, active.id(), active.version(), "TEST", UUID.randomUUID(),
                step.stepKey(), userId, null);
        WorkflowInstance saved = executionService.startWorkflow(instance, userId);

        jdbc.update("""
                UPDATE workflow_step_instances
                SET status = 'IN_PROGRESS', due_at = NOW() - INTERVAL '1 hour',
                    started_at = NOW() - INTERVAL '2 hours', updated_at = NOW()
                WHERE workflow_instance_id = ?
                """, saved.id());

        for (int i = 0; i < 204; i++) {
            jdbc.update("""
                    INSERT INTO workflow_step_instances (
                        id, tenant_id, workflow_instance_id, workflow_step_id, step_key,
                        status, started_at, due_at, attempt_count, version, created_at, updated_at
                    ) VALUES (?, ?, ?, ?, ?, 'IN_PROGRESS',
                              NOW() - INTERVAL '2 hours', NOW() - INTERVAL '1 hour',
                              1, 0, NOW(), NOW())
                    """,
                    UUID.randomUUID(), tenantId, saved.id(), step.id(), step.stepKey());
        }

        assertThat(monitoringService.checkOverdueSteps(tenantId)).isEqualTo(205);
    }
}
