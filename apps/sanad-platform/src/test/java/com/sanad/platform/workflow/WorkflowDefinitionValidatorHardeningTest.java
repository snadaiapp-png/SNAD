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
 * R0.G7 — validator hardening.
 *
 * <p>Adds the missing deterministic structural publish checks while keeping
 * the existing validator behavior untouched: START fan-in, END fan-out,
 * terminal-path reachability, duplicate/ambiguous transitions, CONDITION
 * branch completeness and ambiguity, FORK/JOIN consistency, SYSTEM_ACTION
 * configuration, and assignment-rule configuration validity. Validation
 * stays side-effect free and publication stays blocked on any error.</p>
 */
@SpringBootTest
@ActiveProfiles("local")
@Import(SecurityPermitAllTestConfig.class)
@Transactional
class WorkflowDefinitionValidatorHardeningTest {

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
                        + "VALUES (?, 'R0 Validator Hardening', ?, 'ACTIVE', ?, ?)",
                tenantId, "r0-val-" + tenantId.toString().substring(0, 8), now, now);
        jdbc.update("INSERT INTO users (id,tenant_id,email,display_name,status,password_hash,created_at,updated_at) "
                        + "VALUES (?, ?, ?, 'Hardening User', 'ACTIVE', 'dummy', ?, ?)",
                userId, tenantId, "r0-val-" + userId.toString().substring(0, 8) + "@test", now, now);
    }

    @Test
    void startMustNotHaveIncomingTransitions() {
        UUID defId = createDefinition();
        UUID start = createStep(defId, "start", "START", null);
        UUID task = createStep(defId, "task", "HUMAN_TASK",
                "{\"assignment\":{\"type\":\"PERMISSION\",\"capability\":\"WORKFLOW.TASK_EXECUTE\"}}");
        UUID end = createStep(defId, "end", "END", null);
        createTransition(defId, start, task, "begin", "SUCCESS", null);
        createTransition(defId, task, end, "done", "SUCCESS", null);
        // Invalid: a second edge back into START.
        createTransition(defId, task, start, "back", "SUCCESS", null);

        var result = validator.validate(tenantId, defId);
        assertThat(result.errors()).extracting(WorkflowDefinitionValidation.Error::code)
                .contains("START_INCOMING_INVALID");
    }

    @Test
    void endMustNotHaveOutgoingTransitions() {
        UUID defId = createDefinition();
        UUID start = createStep(defId, "start", "START", null);
        UUID end = createStep(defId, "end", "END", null);
        UUID after = createStep(defId, "after", "HUMAN_TASK",
                "{\"assignment\":{\"type\":\"PERMISSION\",\"capability\":\"WORKFLOW.TASK_EXECUTE\"}}");
        createTransition(defId, start, end, "go", "SUCCESS", null);
        createTransition(defId, end, after, "beyond", "SUCCESS", null);

        var result = validator.validate(tenantId, defId);
        assertThat(result.errors()).extracting(WorkflowDefinitionValidation.Error::code)
                .contains("END_OUTGOING_INVALID");
    }

    @Test
    void everyNonTerminalPathMustBeAbleToReachEnd() {
        UUID defId = createDefinition();
        UUID start = createStep(defId, "start", "START", null);
        UUID a = createStep(defId, "task_a", "HUMAN_TASK",
                "{\"assignment\":{\"type\":\"PERMISSION\",\"capability\":\"WORKFLOW.TASK_EXECUTE\"}}");
        UUID b = createStep(defId, "task_b", "HUMAN_TASK",
                "{\"assignment\":{\"type\":\"PERMISSION\",\"capability\":\"WORKFLOW.TASK_EXECUTE\"}}");
        UUID end = createStep(defId, "end", "END", null);
        // A cycle between A and B that never reaches END (END itself is
        // unreachable too, which is independently reported).
        createTransition(defId, start, a, "s", "SUCCESS", null);
        createTransition(defId, a, b, "ab", "SUCCESS", null);
        createTransition(defId, b, a, "ba", "SUCCESS", null);

        var result = validator.validate(tenantId, defId);
        assertThat(result.errors()).extracting(WorkflowDefinitionValidation.Error::code)
                .contains("NO_PATH_TO_END");
    }

    @Test
    void duplicateTransitionsAreRejected() {
        UUID defId = createDefinition();
        UUID start = createStep(defId, "start", "START", null);
        UUID task = createStep(defId, "task", "HUMAN_TASK",
                "{\"assignment\":{\"type\":\"PERMISSION\",\"capability\":\"WORKFLOW.TASK_EXECUTE\"}}");
        UUID end = createStep(defId, "end", "END", null);
        createTransition(defId, start, task, "begin", "SUCCESS", null);
        // Two structurally identical edges (different keys — keys are unique)
        // with the same from/outcome/target.
        createTransition(defId, task, end, "done1", "SUCCESS", null);
        createTransition(defId, task, end, "done2", "SUCCESS", null);

        var result = validator.validate(tenantId, defId);
        assertThat(result.errors()).extracting(WorkflowDefinitionValidation.Error::code)
                .contains("TRANSITION_DUPLICATE");
    }

    @Test
    void ambiguousTransitionsAreRejected() {
        UUID defId = createDefinition();
        UUID start = createStep(defId, "start", "START", null);
        UUID task = createStep(defId, "task", "HUMAN_TASK",
                "{\"assignment\":{\"type\":\"PERMISSION\",\"capability\":\"WORKFLOW.TASK_EXECUTE\"}}");
        UUID endA = createStep(defId, "end_a", "END", null);
        UUID endB = createStep(defId, "end_b", "END", null);
        createTransition(defId, start, task, "begin", "SUCCESS", null);
        // Same outcome SUCCESS from the same source to two different targets:
        // graph resolution could not choose deterministically.
        createTransition(defId, task, endA, "done_a", "SUCCESS", null);
        createTransition(defId, task, endB, "done_b", "SUCCESS", null);

        var result = validator.validate(tenantId, defId);
        assertThat(result.errors()).extracting(WorkflowDefinitionValidation.Error::code)
                .contains("TRANSITION_AMBIGUOUS");
    }

    @Test
    void conditionBranchesMustBeCompleteAndUnambiguous() {
        // Missing FALSE branch.
        UUID defId = createDefinition();
        UUID start = createStep(defId, "start", "START", null);
        UUID condition = createStep(defId, "cond", "CONDITION", null);
        UUID task = createStep(defId, "task", "HUMAN_TASK",
                "{\"assignment\":{\"type\":\"PERMISSION\",\"capability\":\"WORKFLOW.TASK_EXECUTE\"}}");
        UUID end = createStep(defId, "end", "END", null);
        createTransition(defId, start, condition, "check", "SUCCESS", null);
        createTransition(defId, condition, task, "true-path", "TRUE", null);
        createTransition(defId, task, end, "done", "SUCCESS", null);

        var result = validator.validate(tenantId, defId);
        assertThat(result.errors()).extracting(WorkflowDefinitionValidation.Error::code)
                .contains("CONDITION_OUTCOME_MISSING");
    }

    @Test
    void conditionAmbiguityRejected() {
        UUID defId = createDefinition();
        UUID start = createStep(defId, "start", "START", null);
        UUID condition = createStep(defId, "cond", "CONDITION", null);
        UUID endTrue = createStep(defId, "end_true", "END", null);
        UUID endFalse = createStep(defId, "end_false", "END", null);
        createTransition(defId, start, condition, "check", "SUCCESS", null);
        // Two TRUE edges to different targets.
        createTransition(defId, condition, endTrue, "t1", "TRUE", null);
        createTransition(defId, condition, endFalse, "t2", "TRUE", null);

        var result = validator.validate(tenantId, defId);
        assertThat(result.errors()).extracting(WorkflowDefinitionValidation.Error::code)
                .contains("CONDITION_OUTCOME_AMBIGUOUS");
    }

    @Test
    void conditionBranchesCompleteGraphIsValid() {
        UUID defId = createDefinition();
        UUID start = createStep(defId, "start", "START", null);
        UUID condition = createStep(defId, "cond", "CONDITION", null);
        UUID task = createStep(defId, "task", "HUMAN_TASK",
                "{\"assignment\":{\"type\":\"PERMISSION\",\"capability\":\"WORKFLOW.TASK_EXECUTE\"}}");
        UUID end = createStep(defId, "end", "END", null);
        createTransition(defId, start, condition, "check", "SUCCESS", null);
        createTransition(defId, condition, task, "true-path", "TRUE", null);
        createTransition(defId, condition, end, "false-path", "FALSE", null);
        createTransition(defId, task, end, "done", "SUCCESS", null);

        var result = validator.validate(tenantId, defId);
        assertThat(result.valid()).isTrue();
    }

    @Test
    void forkJoinMismatchRejected() {
        // JOIN with no FORK anywhere in the graph.
        UUID defId = createDefinition();
        UUID start = createStep(defId, "start", "START", null);
        UUID join = createStep(defId, "join", "PARALLEL_JOIN", null);
        UUID end = createStep(defId, "end", "END", null);
        UUID a = createStep(defId, "a", "HUMAN_TASK",
                "{\"assignment\":{\"type\":\"PERMISSION\",\"capability\":\"WORKFLOW.TASK_EXECUTE\"}}");
        UUID b = createStep(defId, "b", "HUMAN_TASK",
                "{\"assignment\":{\"type\":\"PERMISSION\",\"capability\":\"WORKFLOW.TASK_EXECUTE\"}}");
        createTransition(defId, start, a, "s1", "SUCCESS", null);
        createTransition(defId, start, b, "s2", "SUCCESS", null);
        createTransition(defId, a, join, "j1", "SUCCESS", null);
        createTransition(defId, b, join, "j2", "SUCCESS", null);
        createTransition(defId, join, end, "done", "SUCCESS", null);

        var result = validator.validate(tenantId, defId);
        assertThat(result.errors()).extracting(WorkflowDefinitionValidation.Error::code)
                .contains("FORK_JOIN_MISMATCH");
    }

    @Test
    void systemActionMustDeclareAdapterToken() {
        UUID defId = createDefinition();
        UUID start = createStep(defId, "start", "START", null);
        // SYSTEM_ACTION without configuration.adapter.
        UUID action = createStepWithConfig(defId, "sys", "SYSTEM_ACTION", "{}");
        UUID end = createStep(defId, "end", "END", null);
        createTransition(defId, start, action, "go", "SUCCESS", null);
        createTransition(defId, action, end, "done", "SUCCESS", null);

        var result = validator.validate(tenantId, defId);
        assertThat(result.errors()).extracting(WorkflowDefinitionValidation.Error::code)
                .contains("SYSTEM_ACTION_CONFIG_MISSING");
    }

    @Test
    void unknownAssignmentTypeRejected() {
        UUID defId = createDefinition();
        UUID start = createStep(defId, "start", "START", null);
        UUID task = createStep(defId, "task", "HUMAN_TASK",
                "{\"assignment\":{\"type\":\"MAGIC\",\"target\":\"nobody\"}}");
        UUID end = createStep(defId, "end", "END", null);
        createTransition(defId, start, task, "begin", "SUCCESS", null);
        createTransition(defId, task, end, "done", "SUCCESS", null);

        var result = validator.validate(tenantId, defId);
        assertThat(result.errors()).extracting(WorkflowDefinitionValidation.Error::code)
                .contains("ASSIGNMENT_CONFIG_INVALID");
    }

    @Test
    void employeeAssignmentRequiresEmployeeId() {
        UUID defId = createDefinition();
        UUID start = createStep(defId, "start", "START", null);
        UUID task = createStep(defId, "task", "HUMAN_TASK",
                "{\"assignment\":{\"type\":\"EMPLOYEE\"}}");
        UUID end = createStep(defId, "end", "END", null);
        createTransition(defId, start, task, "begin", "SUCCESS", null);
        createTransition(defId, task, end, "done", "SUCCESS", null);

        var result = validator.validate(tenantId, defId);
        assertThat(result.errors()).extracting(WorkflowDefinitionValidation.Error::code)
                .contains("ASSIGNMENT_CONFIG_INVALID");
    }

    @Test
    void wellFormedParallelGraphIsValid() {
        UUID defId = createDefinition();
        UUID start = createStep(defId, "start", "START", null);
        UUID fork = createStep(defId, "fork", "PARALLEL_FORK", null);
        UUID a = createStep(defId, "a", "HUMAN_TASK",
                "{\"assignment\":{\"type\":\"PERMISSION\",\"capability\":\"WORKFLOW.TASK_EXECUTE\"}}");
        UUID b = createStep(defId, "b", "HUMAN_TASK",
                "{\"assignment\":{\"type\":\"PERMISSION\",\"capability\":\"WORKFLOW.TASK_EXECUTE\"}}");
        UUID join = createStep(defId, "join", "PARALLEL_JOIN", null);
        UUID end = createStep(defId, "end", "END", null);
        createTransition(defId, start, fork, "fan-out", "SUCCESS", null);
        createTransition(defId, fork, a, "b1", "SUCCESS", null);
        createTransition(defId, fork, b, "b2", "SUCCESS", null);
        createTransition(defId, a, join, "j1", "SUCCESS", null);
        createTransition(defId, b, join, "j2", "SUCCESS", null);
        createTransition(defId, join, end, "done", "SUCCESS", null);

        var result = validator.validate(tenantId, defId);
        assertThat(result.valid())
                .as("parallel graph errors: %s", result.errors())
                .isTrue();
    }

    // ===== fixture helpers (same conventions as WorkflowDefinitionValidatorTest) =====

    private UUID createDefinition() {
        UUID defId = UUID.randomUUID();
        var now = Timestamp.from(Instant.now());
        jdbc.update("""
                INSERT INTO workflow_definitions (
                    id, tenant_id, definition_family_id, code, name, module, version, status,
                    trigger_type, created_by, version_lock, engine_generation, publication_state,
                    schema_version, created_at, updated_at
                ) VALUES (?, ?, ?, 'WF-R0-VAL', 'Hardening Fixture', 'GENERAL', 1, 'DRAFT',
                          'MANUAL', ?, 0, 'Y2', 'DRAFT', 1, ?, ?)
                """, defId, tenantId, defId, userId, now, now);
        return defId;
    }

    private UUID createStep(UUID defId, String stepKey, String stepType, String configuration) {
        return createStepWithConfig(defId, stepKey, stepType, configuration);
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
