package com.sanad.platform.workflow;

import com.sanad.platform.security.SecurityPermitAllTestConfig;
import com.sanad.platform.workflow.application.WorkflowSlaEscalationService;
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

/**
 * Wave 2 / Task 12 — SLA escalation command worker (design decisions
 * V3 / G3 / AF3 / K3).
 *
 * <p>Proves that an overdue Y2 work item produces exactly one governed
 * SLA_BREACH incident and one IN_APP SLA_BREACHED notification intent to
 * the responsible user, that repeated scans are idempotent (no incident or
 * intent storms), that the escalation never reassigns or mutates the work
 * item (B1 dominance: explicit human reassignment only), and that healthy
 * work items are never escalated.</p>
 */
@SpringBootTest
@ActiveProfiles("local")
@Import(SecurityPermitAllTestConfig.class)
@Transactional
class WorkflowSlaEscalationTest {

    @Autowired
    private WorkflowSlaEscalationService escalationService;

    @Autowired
    private JdbcTemplate jdbc;

    private UUID tenantId;
    private UUID userId;
    private UUID employeeId;
    private UUID definitionId;
    private UUID instanceId;
    private UUID stepInstanceId;
    private UUID workItemId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        userId = UUID.randomUUID();
        var now = Timestamp.from(Instant.now());

        jdbc.update("INSERT INTO tenants (id,name,subdomain,status,created_at,updated_at) "
                        + "VALUES (?, 'Y2 SLA Escalation', ?, 'ACTIVE', ?, ?)",
                tenantId, "y2-esc-" + tenantId.toString().substring(0, 8), now, now);
        jdbc.update("INSERT INTO users (id,tenant_id,email,display_name,status,password_hash,created_at,updated_at) "
                        + "VALUES (?, ?, ?, 'Escalation User', 'ACTIVE', 'dummy', ?, ?)",
                userId, tenantId, "y2-esc-" + userId.toString().substring(0, 8) + "@test", now, now);
        jdbc.execute("SELECT set_config('app.tenant_id', '" + tenantId + "', true)");

        employeeId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO hr_employees (
                    id, tenant_id, user_id, employee_number, first_name, last_name, display_name,
                    employment_type, status, created_at, updated_at
                ) VALUES (?, ?, ?, 'E-ESC', 'Escalation', 'Owner', 'Escalation Owner',
                          'FULL_TIME', 'ACTIVE', ?, ?)
                """, employeeId, tenantId, userId, now, now);

        definitionId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO workflow_definitions (
                    id, tenant_id, definition_family_id, code, name, module, version, status,
                    trigger_type, created_by, version_lock, engine_generation, publication_state,
                    schema_version, created_at, updated_at
                ) VALUES (?, ?, ?, 'WF-Y2-ESC', 'Y2 Escalation', 'GENERAL', 1, 'ACTIVE',
                          'MANUAL', ?, 0, 'Y2', 'PUBLISHED', 1, ?, ?)
                """, definitionId, tenantId, definitionId, userId, now, now);
    }

    private void fixtureOverdueWorkItem(String workItemStatus, Instant slaDueAt) {
        var now = Timestamp.from(Instant.now());
        instanceId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO workflow_instances (
                    id, tenant_id, workflow_definition_id, workflow_version, business_entity_type,
                    business_entity_id, status, current_step_key, started_by, started_at,
                    engine_generation, definition_family_id, definition_version_id,
                    context_json, context_schema_version, version, created_at, updated_at
                ) VALUES (?, ?, ?, 1, 'TEST', gen_random_uuid(), 'RUNNING', 'review', ?,
                          ?, 'Y2', ?, ?, CAST('{}' AS jsonb), 1, 0, ?, ?)
                """, instanceId, tenantId, definitionId, userId, now,
                definitionId, definitionId, now, now);
        UUID stepDefId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO workflow_steps (
                    id, tenant_id, workflow_definition_id, step_key, name, step_type,
                    sequence_order, version, created_at, updated_at
                ) VALUES (?, ?, ?, 'review', 'Review', 'HUMAN_TASK', 1, 0, ?, ?)
                """, stepDefId, tenantId, definitionId, now, now);
        stepInstanceId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO workflow_step_instances (
                    id, tenant_id, workflow_instance_id, workflow_step_id, step_key,
                    status, version, created_at, updated_at
                ) VALUES (?, ?, ?, ?, 'review', 'IN_PROGRESS', 1, ?, ?)
                """, stepInstanceId, tenantId, instanceId, stepDefId, now, now);
        workItemId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO workflow_work_items (
                    id, tenant_id, workflow_instance_id, workflow_step_instance_id,
                    type, status, assignee_employee_id, claimed_by_employee_id, assignment_mode,
                    source_module, source_entity_type, source_entity_id, title,
                    sla_due_at, version, created_at, updated_at
                ) VALUES (?, ?, ?, ?, 'HUMAN_TASK', ?, NULL, ?, 'DIRECT',
                          'GENERAL', 'TEST', gen_random_uuid(), 'Overdue review',
                          ?, 3, ?, ?)
                """, workItemId, tenantId, instanceId, stepInstanceId,
                workItemStatus, employeeId, Timestamp.from(slaDueAt), now, now);
    }

    @Test
    void overdueWorkItemCreatesExactlyOneIncidentAndIntentOnRepeatedScans() {
        fixtureOverdueWorkItem("CLAIMED", Instant.now().minusSeconds(3600));

        int first = escalationService.escalateTenant(tenantId);
        assertThat(first).isEqualTo(1);

        Integer incidents = jdbc.queryForObject("""
                SELECT COUNT(*) FROM workflow_incidents
                WHERE workflow_instance_id = ? AND failure_category = 'SLA_BREACH'
                  AND status = 'OPEN'
                """, Integer.class, instanceId);
        assertThat(incidents).isEqualTo(1);

        Integer intents = jdbc.queryForObject("""
                SELECT COUNT(*) FROM workflow_notification_intents
                WHERE work_item_id = ? AND event_type = 'SLA_BREACHED' AND channel = 'IN_APP'
                """, Integer.class, workItemId);
        assertThat(intents).isEqualTo(1);

        String recipient = jdbc.queryForObject("""
                SELECT recipient_user_id::text FROM workflow_notification_intents
                WHERE work_item_id = ? AND event_type = 'SLA_BREACHED'
                """, String.class, workItemId);
        assertThat(recipient).isEqualTo(userId.toString());

        // Repeated scans are idempotent: the open incident is reused and the
        // notification intent deduplication key prevents a second intent.
        int second = escalationService.escalateTenant(tenantId);
        assertThat(second).isZero();
        incidents = jdbc.queryForObject("""
                SELECT COUNT(*) FROM workflow_incidents
                WHERE workflow_instance_id = ? AND failure_category = 'SLA_BREACH'
                """, Integer.class, instanceId);
        assertThat(incidents).isEqualTo(1);
        intents = jdbc.queryForObject("""
                SELECT COUNT(*) FROM workflow_notification_intents
                WHERE work_item_id = ? AND event_type = 'SLA_BREACHED'
                """, Integer.class, workItemId);
        assertThat(intents).isEqualTo(1);
    }

    @Test
    void escalationNeverReassignsOrMutatesTheWorkItem() {
        fixtureOverdueWorkItem("CLAIMED", Instant.now().minusSeconds(3600));

        escalationService.escalateTenant(tenantId);

        var item = jdbc.queryForMap("""
                SELECT status, claimed_by_employee_id::text AS claimant, priority
                FROM workflow_work_items WHERE id = ?
                """, workItemId);
        assertThat(item.get("status")).isEqualTo("CLAIMED");
        assertThat(item.get("claimant")).isEqualTo(employeeId.toString());
    }

    @Test
    void healthyWorkItemIsNeverEscalated() {
        fixtureOverdueWorkItem("CLAIMED", Instant.now().plusSeconds(3600));

        int escalations = escalationService.escalateTenant(tenantId);

        assertThat(escalations).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM workflow_incidents WHERE tenant_id = ?", Integer.class, tenantId))
                .isZero();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM workflow_notification_intents WHERE tenant_id = ?",
                Integer.class, tenantId)).isZero();
    }

    @Test
    void legacyEngineBreachesAreNotY2Escalated() {
        var now = Timestamp.from(Instant.now());
        UUID legacyDefinition = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO workflow_definitions (
                    id, tenant_id, definition_family_id, code, name, module, version, status,
                    trigger_type, created_by, version_lock, engine_generation, publication_state,
                    schema_version, created_at, updated_at
                ) VALUES (?, ?, ?, 'WF-LEG-ESC', 'Legacy Esc', 'GENERAL', 1, 'ACTIVE',
                          'MANUAL', ?, 0, 'LEGACY', 'DRAFT', 1, ?, ?)
                """, legacyDefinition, tenantId, legacyDefinition, userId, now, now);
        // Reuse the fixture shape but with a LEGACY instance generation.
        fixtureOverdueWorkItem("CLAIMED", Instant.now().minusSeconds(3600));
        jdbc.update("UPDATE workflow_instances SET engine_generation = 'LEGACY' WHERE id = ?", instanceId);

        int escalations = escalationService.escalateTenant(tenantId);
        assertThat(escalations).isZero();
    }
}
