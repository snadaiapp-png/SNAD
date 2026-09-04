package com.sanad.platform.workflow;

import com.sanad.platform.security.SecurityPermitAllTestConfig;
import com.sanad.platform.workflow.application.WorkflowBreakGlassService;
import com.sanad.platform.workflow.domain.WorkflowInstance;
import com.sanad.platform.workflow.domain.WorkflowTransitionAudit;
import com.sanad.platform.workflow.domain.WorkflowTransitionAuditRepository;
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
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Wave 3 / Task 20 — break-glass gate (AH3).
 *
 * <p>Break-glass requires a non-blank reason, appends an OVERRIDE audit row
 * recording the real actor/target/timestamp, never forges approvals or
 * mutates published definitions, never crosses tenants, never erases
 * evidence, and never silently defeats B1 unavailable-assignee semantics.</p>
 */
@SpringBootTest
@ActiveProfiles("local")
@Import(SecurityPermitAllTestConfig.class)
@Transactional
class WorkflowBreakGlassTest {

    @Autowired
    private WorkflowBreakGlassService breakGlass;

    @Autowired
    private WorkflowTransitionAuditRepository auditRepo;

    @Autowired
    private JdbcTemplate jdbc;

    private UUID tenantId;
    private UUID userId;
    private UUID instanceId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        userId = createUser(tenantId, "bg-actor");
        instanceId = createInstance(tenantId, userId, "FAILED");
    }

    @Test
    void breakGlassWithoutReason_fails() {
        assertThatThrownBy(() -> breakGlass.emergencyResume(tenantId, instanceId, userId, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> breakGlass.emergencyCancel(tenantId, instanceId, userId, "  "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void breakGlassResumeUnblocksAndAuditsOverride() {
        var resumed = breakGlass.emergencyResume(tenantId, instanceId, userId, "stuck by incident 42");

        assertThat(resumed.status()).isEqualTo(WorkflowInstance.Status.RUNNING);

        List<Map<String, Object>> overrides = jdbc.queryForList(
                "SELECT actor_user_id, action, metadata FROM workflow_transition_audit "
                        + "WHERE tenant_id = ? AND workflow_instance_id = ? AND action = 'OVERRIDE'",
                tenantId, instanceId);
        assertThat(overrides).hasSize(1);
        assertThat(overrides.get(0).get("actor_user_id")).isEqualTo(userId);
        String metadata = String.valueOf(overrides.get(0).get("metadata"));
        assertThat(metadata).contains("\"breakGlass\"").contains("true").contains("RESUME")
                .contains("stuck by incident 42");
    }

    @Test
    void breakGlassCancelOnTerminalInstance_failsClosed() {
        jdbc.update("UPDATE workflow_instances SET status = 'COMPLETED' WHERE tenant_id = ? AND id = ?",
                tenantId, instanceId);
        assertThatThrownBy(() -> breakGlass.emergencyCancel(tenantId, instanceId, userId, "too late"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Break-glass cancel rejected");
    }

    @Test
    void breakGlassCannotCrossTenant() {
        UUID otherTenant = UUID.randomUUID();
        assertThatThrownBy(() -> breakGlass.emergencyResume(otherTenant, instanceId, userId, "cross"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found in tenant");
    }

    @Test
    void breakGlassDoesNotForgeApprovalsOrDefeatB1() {
        // B1: an unavailable-assignee work item is untouched by instance-level
        // break-glass — reassignment stays an explicit authorized command.
        UUID employeeId = UUID.randomUUID();
        var now = Timestamp.from(Instant.now());
        jdbc.update("""
                INSERT INTO hr_employees (
                    id, tenant_id, employee_number, first_name, last_name, display_name,
                    employment_type, status, created_at, updated_at
                ) VALUES (?, ?, 'BG-A', 'Break', 'Glass', 'Break Glass', 'FULL_TIME', 'ACTIVE', ?, ?)
                """, employeeId, tenantId, now, now);
        UUID workItemId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO workflow_work_items (
                    id, tenant_id, workflow_instance_id, workflow_step_instance_id,
                    type, status, assignee_employee_id, assignment_mode,
                    source_module, source_entity_type, source_entity_id, title,
                    priority, version, created_at, updated_at
                ) VALUES (?, ?, ?, ?, 'APPROVAL', 'ASSIGNEE_UNAVAILABLE', ?, 'DIRECT',
                          'TEST', 'CASE', gen_random_uuid(), 'B1 item', 0, 0, ?, ?)
                """, workItemId, tenantId, instanceId,
                jdbc.queryForObject(
                        "SELECT id FROM workflow_step_instances WHERE tenant_id = ? AND workflow_instance_id = ? LIMIT 1",
                        UUID.class, tenantId, instanceId),
                employeeId, now, now);

        breakGlass.emergencyResume(tenantId, instanceId, userId, "resume instance only");

        String itemStatus = jdbc.queryForObject(
                "SELECT status FROM workflow_work_items WHERE tenant_id = ? AND id = ?",
                String.class, tenantId, workItemId);
        assertThat(itemStatus).isEqualTo("ASSIGNEE_UNAVAILABLE");
    }

    @Test
    void breakGlassAuditIsAppendOnly() {
        breakGlass.emergencyCancel(tenantId, instanceId, userId, "stuck forever");
        long countBefore = jdbc.queryForObject(
                "SELECT COUNT(*) FROM workflow_transition_audit WHERE action = 'OVERRIDE'",
                Long.class);
        // A second emergency on a fresh stuck instance appends another row —
        // OVERRIDE evidence is append-only, never rewritten.
        UUID secondInstance = createInstance(tenantId, userId, "FAILED");
        breakGlass.emergencyCancel(tenantId, secondInstance, userId, "second emergency");
        long countAfter = jdbc.queryForObject(
                "SELECT COUNT(*) FROM workflow_transition_audit WHERE action = 'OVERRIDE'",
                Long.class);
        assertThat(countAfter).isEqualTo(countBefore + 1);
    }

    // ===== fixture helpers =====

    private UUID createUser(UUID tenant, String prefix) {
        UUID id = UUID.randomUUID();
        var now = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO tenants (id,name,subdomain,status,created_at,updated_at) "
                        + "VALUES (?, ?, ?, 'ACTIVE', ?, ?)",
                tenant, "Break Glass " + prefix, "wf-bg-" + tenant.toString().substring(0, 8), now, now);
        jdbc.update("INSERT INTO users (id,tenant_id,email,display_name,status,password_hash,created_at,updated_at) "
                        + "VALUES (?, ?, ?, ?, 'ACTIVE', 'dummy', ?, ?)",
                id, tenant, prefix + "-" + id.toString().substring(0, 8) + "@test",
                "Break Glass User", now, now);
        return id;
    }

    private UUID createInstance(UUID tenant, UUID startedBy, String status) {
        UUID instanceId = UUID.randomUUID();
        UUID definitionId = UUID.randomUUID();
        var now = Timestamp.from(Instant.now());
        jdbc.update("""
                INSERT INTO workflow_definitions (
                    id, tenant_id, definition_family_id, code, name, module, version, status,
                    trigger_type, created_by, version_lock, engine_generation, publication_state,
                    schema_version, created_at, updated_at
                ) VALUES (?, ?, ?, ?, 'Break Glass', 'GENERAL', 1, 'ACTIVE',
                          'MANUAL', ?, 0, 'LEGACY', 'DRAFT', 1, ?, ?)
                """, definitionId, tenant, definitionId,
                "WF-BG-" + definitionId.toString().substring(0, 8), startedBy, now, now);
        jdbc.update("""
                INSERT INTO workflow_instances (
                    id, tenant_id, workflow_definition_id, workflow_version, business_entity_type,
                    business_entity_id, status, started_by, started_at, version, created_at, updated_at
                ) VALUES (?, ?, ?, 1, 'TEST', gen_random_uuid(), ?, ?, NOW(), 0, NOW(), NOW())
                """, instanceId, tenant, definitionId, status, startedBy);
        UUID stepInstanceId = UUID.randomUUID();
        UUID stepDefId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO workflow_steps (
                    id, tenant_id, workflow_definition_id, step_key, name, step_type,
                    sequence_order, configuration, version, created_at, updated_at
                ) VALUES (?, ?, ?, 'task', 'Task', 'APPROVAL', 1, CAST('{}' AS jsonb), 0, NOW(), NOW())
                """, stepDefId, tenant, definitionId);
        jdbc.update("""
                INSERT INTO workflow_step_instances (
                    id, tenant_id, workflow_instance_id, workflow_step_id, step_key,
                    status, version, created_at, updated_at
                ) VALUES (?, ?, ?, ?, 'task', 'PENDING', 0, NOW(), NOW())
                """, stepInstanceId, tenant, instanceId, stepDefId);
        return instanceId;
    }
}
