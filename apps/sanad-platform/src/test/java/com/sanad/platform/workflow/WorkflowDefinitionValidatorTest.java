package com.sanad.platform.workflow;

import com.sanad.platform.security.SecurityPermitAllTestConfig;
import com.sanad.platform.workflow.application.WorkflowDefinitionValidator;
import com.sanad.platform.workflow.domain.WorkflowDefinitionValidation;
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
 * Wave 1 / Task 6 — publish validator (AN3).
 *
 * <p>Verifies the deterministic structural publish gate: exactly one START,
 * reachable steps, explicit APPROVE/REJECT edges for approvals, bounded
 * condition ASTs, and forbidden executable/secret fields fail closed.</p>
 */
@SpringBootTest
@ActiveProfiles("local")
@Import(SecurityPermitAllTestConfig.class)
@Transactional
class WorkflowDefinitionValidatorTest {

    @Autowired
    private WorkflowDefinitionValidator validator;

    @Autowired
    private JdbcTemplate jdbc;

    private UUID tenantId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        userId = UUID.randomUUID();
        var now = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO tenants (id,name,subdomain,status,created_at,updated_at) "
                        + "VALUES (?, 'Workflow Validator Test', ?, 'ACTIVE', ?, ?)",
                tenantId, "wf-valid-" + tenantId.toString().substring(0, 8), now, now);
        jdbc.update("INSERT INTO users (id,tenant_id,email,display_name,status,password_hash,created_at,updated_at) "
                        + "VALUES (?, ?, ?, 'Validator User', 'ACTIVE', 'dummy', ?, ?)",
                userId, tenantId, "wf-valid-" + userId.toString().substring(0, 8) + "@test", now, now);
    }

    @Test
    void publishRejectsGraphWithoutExactlyOneStart() {
        UUID defId = createDefinition();
        UUID a = createStep(defId, "task_a", "HUMAN_TASK", null);
        UUID b = createStep(defId, "task_b", "HUMAN_TASK", null);
        createTransition(defId, a, b, "next", "SUCCESS", null);

        var result = validator.validate(tenantId, defId);
        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).extracting(WorkflowDefinitionValidation.Error::code)
                .contains("START_COUNT_INVALID");
    }

    @Test
    void publishRejectsApprovalWithoutApproveAndRejectTransitions() {
        UUID defId = createDefinition();
        UUID start = createStep(defId, "start", "START", null);
        UUID approval = createStep(defId, "approval", "APPROVAL", "WORKFLOW.APPROVE");
        UUID end = createStep(defId, "end", "END", null);
        createTransition(defId, start, approval, "begin", "SUCCESS", null);
        createTransition(defId, approval, end, "approved", "APPROVE", null);

        var result = validator.validate(tenantId, defId);
        assertThat(result.errors()).extracting(WorkflowDefinitionValidation.Error::code)
                .contains("APPROVAL_OUTCOME_MISSING");
    }

    @Test
    void wellFormedGraphValidates() {
        UUID defId = createDefinition();
        UUID start = createStep(defId, "start", "START", null);
        UUID task = createStep(defId, "task", "HUMAN_TASK", "WORKFLOW.TASK_EXECUTE");
        UUID end = createStep(defId, "end", "END", null);
        createTransition(defId, start, task, "begin", "SUCCESS", null);
        createTransition(defId, task, end, "done", "SUCCESS", null);

        var result = validator.validate(tenantId, defId);
        assertThat(result.valid()).isTrue();
    }

    @Test
    void unreachableStepFailsValidation() {
        UUID defId = createDefinition();
        UUID start = createStep(defId, "start", "START", null);
        UUID end = createStep(defId, "end", "END", null);
        UUID orphan = createStep(defId, "orphan", "HUMAN_TASK", "WORKFLOW.TASK_EXECUTE");
        createTransition(defId, start, end, "done", "SUCCESS", null);

        var result = validator.validate(tenantId, defId);
        assertThat(result.errors()).extracting(WorkflowDefinitionValidation.Error::code)
                .contains("STEP_UNREACHABLE");
        assertThat(result.errors())
                .extracting(WorkflowDefinitionValidation.Error::stepId)
                .contains(orphan);
    }

    @Test
    void executableCodeAndSecretFieldsFailValidation() {
        UUID defId = createDefinition();
        UUID start = createStep(defId, "start", "START", null);
        UUID scripted = createStep(defId, "task", "HUMAN_TASK",
                "{\"script\": \"return secrets.apiKey;\"}");
        UUID end = createStep(defId, "end", "END", null);
        createTransition(defId, start, scripted, "begin", "SUCCESS", null);
        createTransition(defId, scripted, end, "done", "SUCCESS", null);

        var result = validator.validate(tenantId, defId);
        List<String> codes = result.errors().stream()
                .map(WorkflowDefinitionValidation.Error::code).toList();
        assertThat(codes).contains("FORBIDDEN_FIELD_DETECTED");
    }

    @Test
    void missingAssignmentConfigurationFailsValidation() {
        UUID defId = createDefinition();
        UUID start = createStep(defId, "start", "START", null);
        UUID task = createStep(defId, "task", "HUMAN_TASK", null);
        UUID end = createStep(defId, "end", "END", null);
        createTransition(defId, start, task, "begin", "SUCCESS", null);
        createTransition(defId, task, end, "done", "SUCCESS", null);

        var result = validator.validate(tenantId, defId);
        assertThat(result.errors()).extracting(WorkflowDefinitionValidation.Error::code)
                .contains("ASSIGNMENT_CONFIG_MISSING");
    }

    @Test
    void missingDefinitionFailsClosed() {
        var result = validator.validate(tenantId, UUID.randomUUID());
        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).extracting(WorkflowDefinitionValidation.Error::code)
                .containsExactly("DEFINITION_NOT_FOUND");
    }

    // ===== fixture helpers =====

    private UUID createDefinition() {
        UUID defId = UUID.randomUUID();
        var now = Timestamp.from(Instant.now());
        jdbc.update("""
                INSERT INTO workflow_definitions (
                    id, tenant_id, definition_family_id, code, name, module, version, status,
                    trigger_type, created_by, version_lock, engine_generation, publication_state,
                    schema_version, created_at, updated_at
                ) VALUES (?, ?, ?, 'WF-VALID', 'Validator Fixture', 'GENERAL', 1, 'DRAFT',
                          'MANUAL', ?, 0, 'Y2', 'DRAFT', 1, ?, ?)
                """, defId, tenantId, defId, userId, now, now);
        return defId;
    }

    private UUID createStep(UUID defId, String stepKey, String stepType, String requiredCapability) {
        return createStepWithConfig(defId, stepKey, stepType,
                requiredCapability != null
                        ? "{\"assignment\": {\"type\": \"PERMISSION\", \"capability\": \"" + requiredCapability + "\"}}"
                        : null);
    }

    private UUID createStepWithConfig(UUID defId, String stepKey, String stepType, String configuration) {
        UUID stepId = UUID.randomUUID();
        var now = Timestamp.from(Instant.now());
        jdbc.update("""
                INSERT INTO workflow_steps (
                    id, tenant_id, workflow_definition_id, step_key, name, step_type,
                    sequence_order, configuration, version, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, 1, CAST(? AS jsonb), 0, ?, ?)
                """, stepId, tenantId, defId, stepKey, stepKey, stepType, configuration, now, now);
        return stepId;
    }

    private void createTransition(UUID defId, UUID fromStep, UUID toStep,
                                  String key, String outcome, String conditionAst) {
        var transition = com.sanad.platform.workflow.domain.WorkflowTransition.create(
                tenantId, defId, fromStep, toStep, key, outcome, conditionAst, 10, "{}");
        jdbc.update("""
                INSERT INTO workflow_step_transitions (
                    id, tenant_id, workflow_definition_id, from_step_id, to_step_id,
                    transition_key, outcome, condition_ast, priority, metadata, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), ?, CAST('{}' AS jsonb), ?, ?)
                """, transition.id(), transition.tenantId(), transition.workflowDefinitionId(),
                transition.fromStepId(), transition.toStepId(), transition.transitionKey(),
                transition.outcome(), transition.conditionAst(), transition.priority(),
                Timestamp.from(transition.createdAt()), Timestamp.from(transition.updatedAt()));
    }
}
