package com.sanad.platform.workflow;

import com.sanad.platform.security.SecurityPermitAllTestConfig;
import com.sanad.platform.workflow.application.WorkflowExecutionService;
import com.sanad.platform.workflow.application.WorkflowGraphExecutionService;
import com.sanad.platform.workflow.application.WorkflowSystemActionAdapter;
import com.sanad.platform.workflow.domain.WorkflowInstance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Wave 2 / Task 13 — Y2 two-phase cancellation (design decision P3).
 *
 * <p>Proves the durable cancellation lifecycle ACTIVE -> CANCELLING ->
 * CANCELLED: compensatable committed side effects run during CANCELLING,
 * compensation failures open governed incidents and hold the instance in
 * CANCELLING, the legacy facade routes Y2 instances by their persisted
 * engine generation, and LEGACY instances keep their direct
 * RUNNING -> CANCELLED backward-compatible cancellation.</p>
 */
@SpringBootTest
@ActiveProfiles("local")
@Import({SecurityPermitAllTestConfig.class, WorkflowY2CancellationTest.CancellationAdapters.class})
@Transactional
class WorkflowY2CancellationTest {

    @Autowired
    private WorkflowExecutionService executionService;

    @Autowired
    private WorkflowGraphExecutionService graphExecutionService;

    @Autowired
    private JdbcTemplate jdbc;

    private UUID tenantId;
    private UUID userId;
    private UUID definitionId;
    private UUID startStepId;
    private UUID actionStepId;
    private UUID endStepId;
    private UUID instanceId;

    /** Compensation succeeds and records the reconciliation reference. */
    static final class CompensatableOkAdapter implements WorkflowSystemActionAdapter {
        final AtomicInteger compensateCalls = new AtomicInteger();

        @Override
        public String type() {
            return "Y2CANCEL_OK";
        }

        @Override
        public ActionResult execute(ActionRequest request) {
            return ActionResult.ok("ok-ref", Map.of());
        }

        @Override
        public ActionResult compensate(ActionRequest request) {
            compensateCalls.incrementAndGet();
            return ActionResult.ok("compensated-ref", Map.of());
        }
    }

    /** Compensation fails permanently: incident, never silent success. */
    static final class CompensatableFailingAdapter implements WorkflowSystemActionAdapter {
        final AtomicInteger compensateCalls = new AtomicInteger();

        @Override
        public String type() {
            return "Y2CANCEL_FAIL";
        }

        @Override
        public ActionResult execute(ActionRequest request) {
            return ActionResult.ok("ok-ref", Map.of());
        }

        @Override
        public ActionResult compensate(ActionRequest request) {
            compensateCalls.incrementAndGet();
            return ActionResult.permanentFailure("COMPENSATION_BUSINESS_REJECT");
        }
    }

    @TestConfiguration
    static class CancellationAdapters {
        @Bean
        CompensatableOkAdapter compensatableOkAdapter() {
            return new CompensatableOkAdapter();
        }

        @Bean
        CompensatableFailingAdapter compensatableFailingAdapter() {
            return new CompensatableFailingAdapter();
        }
    }

    @Autowired
    CompensatableOkAdapter okAdapter;

    @Autowired
    CompensatableFailingAdapter failingAdapter;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        userId = UUID.randomUUID();
        var now = Timestamp.from(Instant.now());

        jdbc.update("INSERT INTO tenants (id,name,subdomain,status,created_at,updated_at) "
                        + "VALUES (?, 'Y2 Cancellation', ?, 'ACTIVE', ?, ?)",
                tenantId, "y2-cancel-" + tenantId.toString().substring(0, 8), now, now);
        jdbc.update("INSERT INTO users (id,tenant_id,email,display_name,status,password_hash,created_at,updated_at) "
                        + "VALUES (?, ?, ?, 'Y2 Cancel User', 'ACTIVE', 'dummy', ?, ?)",
                userId, tenantId, "y2-cancel-" + userId.toString().substring(0, 8) + "@test", now, now);
        jdbc.execute("SELECT set_config('app.tenant_id', '" + tenantId + "', true)");

        // Adapter beans are shared across the Spring context: reset the
        // per-test invocation counters so assertions measure only this test.
        okAdapter.compensateCalls.set(0);
        failingAdapter.compensateCalls.set(0);
    }

    private void createY2Definition(String adapterType) {
        definitionId = UUID.randomUUID();
        var now = Timestamp.from(Instant.now());
        jdbc.update("""
                INSERT INTO workflow_definitions (
                    id, tenant_id, definition_family_id, code, name, module, version, status,
                    trigger_type, created_by, version_lock, engine_generation, publication_state,
                    schema_version, created_at, updated_at
                ) VALUES (?, ?, ?, 'WF-Y2-CANCEL', 'Y2 Cancel', 'GENERAL', 1, 'ACTIVE',
                          'MANUAL', ?, 0, 'Y2', 'PUBLISHED', 1, ?, ?)
                """, definitionId, tenantId, definitionId, userId, now, now);
        startStepId = createStep("start", "START", null, null, null);
        actionStepId = createStep("act", "SYSTEM_ACTION", null,
                "{\"adapter\":\"" + adapterType + "\",\"input\":{}}", null);
        endStepId = createStep("end", "END", null, null, null);
        createTransition(startStepId, actionStepId, "begin", "SUCCESS");
        createTransition(actionStepId, endStepId, "done", "SUCCESS");
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
    }

    private UUID createCompletedActionStepInstance() {
        var now = Timestamp.from(Instant.now());
        UUID stepInstanceId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO workflow_step_instances (
                    id, tenant_id, workflow_instance_id, workflow_step_id, step_key,
                    status, started_at, completed_at, result, version, created_at, updated_at
                ) VALUES (?, ?, ?, ?, 'act', 'COMPLETED', ?, ?, 'executed', 2, ?, ?)
                """, stepInstanceId, tenantId, instanceId, actionStepId, now, now, now, now);
        return stepInstanceId;
    }

    @Test
    void y2CancelFinalizesThroughCancellingAfterSuccessfulCompensation() {
        createY2Definition(okAdapter.type());
        createY2Instance("end");
        UUID actionStepInstance = createCompletedActionStepInstance();

        var cancelled = graphExecutionService.cancel(tenantId, instanceId, userId, "no longer needed");

        assertThat(cancelled.status()).isEqualTo(WorkflowInstance.Status.CANCELLED);
        assertThat(okAdapter.compensateCalls.get()).isEqualTo(1);
        // Compensation was durably recorded against the system-action step
        Integer attempts = jdbc.queryForObject(
                "SELECT COUNT(*) FROM workflow_execution_attempts WHERE step_instance_id = ?",
                Integer.class, actionStepInstance);
        assertThat(attempts).isEqualTo(1);
        // Cancellation lifecycle is audited through both phases
        List<String> auditRows = jdbc.queryForList("""
                SELECT action || ':' || from_state || '->' || to_state
                FROM workflow_transition_audit
                WHERE workflow_instance_id = ? AND action = 'CANCEL'
                ORDER BY created_at ASC
                """, String.class, instanceId);
        assertThat(auditRows).containsExactly("CANCEL:RUNNING->CANCELLING", "CANCEL:CANCELLING->CANCELLED");
    }

    @Test
    void y2CancelWithFailingCompensationStaysCancellingAndOpensIncident() {
        createY2Definition(failingAdapter.type());
        createY2Instance("end");
        UUID actionStepInstance = createCompletedActionStepInstance();

        var held = graphExecutionService.cancel(tenantId, instanceId, userId, "cancel with side effects");

        // The instance must NOT pretend the cancellation succeeded.
        assertThat(held.status()).isEqualTo(WorkflowInstance.Status.CANCELLING);
        assertThat(failingAdapter.compensateCalls.get()).isEqualTo(1);
        Integer incidents = jdbc.queryForObject("""
                SELECT COUNT(*) FROM workflow_incidents
                WHERE workflow_instance_id = ? AND failure_category = 'COMPENSATION_FAILED'
                  AND status = 'OPEN'
                """, Integer.class, instanceId);
        assertThat(incidents).isEqualTo(1);
        // CANCELLING is durable and terminal-blocked: no further graph advance.
        assertThatThrownBy(() -> graphExecutionService.advance(tenantId, instanceId, "SUCCESS", userId))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void y2CancelIsIdempotentOnReentryWhileCancelling() {
        createY2Definition(okAdapter.type());
        createY2Instance("end");
        createCompletedActionStepInstance();

        graphExecutionService.cancel(tenantId, instanceId, userId, "first request");
        var again = graphExecutionService.cancel(tenantId, instanceId, userId, "first request");

        assertThat(again.status()).isEqualTo(WorkflowInstance.Status.CANCELLED);
        // Compensation stays idempotent: the replay does not re-run the adapter.
        assertThat(okAdapter.compensateCalls.get()).isEqualTo(1);
    }

    @Test
    void legacyFacadeCancelRoutesY2InstanceByPersistedGeneration() {
        createY2Definition(okAdapter.type());
        createY2Instance("end");
        createCompletedActionStepInstance();

        // The legacy /api/v1/workflows facade serves the same cancel command;
        // routing must follow the persisted engine generation (Z3/AA3).
        var cancelled = executionService.cancel(tenantId, instanceId, userId, "routed");

        assertThat(cancelled.status()).isEqualTo(WorkflowInstance.Status.CANCELLED);
        assertThat(okAdapter.compensateCalls.get()).isEqualTo(1);
    }

    @Test
    void graphCancelRejectsLegacyInstance() {
        var now = Timestamp.from(Instant.now());
        UUID legacyDefinition = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO workflow_definitions (
                    id, tenant_id, definition_family_id, code, name, module, version, status,
                    trigger_type, created_by, version_lock, engine_generation, publication_state,
                    schema_version, created_at, updated_at
                ) VALUES (?, ?, ?, 'WF-LEGACY-CANCEL', 'Legacy Cancel', 'GENERAL', 1, 'ACTIVE',
                          'MANUAL', ?, 0, 'LEGACY', 'DRAFT', 1, ?, ?)
                """, legacyDefinition, tenantId, legacyDefinition, userId, now, now);
        UUID legacyInstance = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO workflow_instances (
                    id, tenant_id, workflow_definition_id, workflow_version, business_entity_type,
                    business_entity_id, status, current_step_key, started_by, started_at,
                    engine_generation, context_json, context_schema_version, version, created_at, updated_at
                ) VALUES (?, ?, ?, 1, 'TEST', gen_random_uuid(), 'RUNNING', 'start', ?, ?,
                          'LEGACY', CAST('{}' AS jsonb), 1, 0, ?, ?)
                """, legacyInstance, tenantId, legacyDefinition, userId, now, now, now);

        assertThatThrownBy(() -> graphExecutionService.cancel(tenantId, legacyInstance, userId, "wrong engine"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("LEGACY");
        assertThat(jdbc.queryForObject(
                "SELECT status FROM workflow_instances WHERE id = ?", String.class, legacyInstance))
                .isEqualTo("RUNNING");
    }

    @Test
    void legacyInstancesKeepDirectBackwardCompatibleCancellation() {
        var now = Timestamp.from(Instant.now());
        UUID legacyDefinition = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO workflow_definitions (
                    id, tenant_id, definition_family_id, code, name, module, version, status,
                    trigger_type, created_by, version_lock, engine_generation, publication_state,
                    schema_version, created_at, updated_at
                ) VALUES (?, ?, ?, 'WF-LEGACY-CANCEL2', 'Legacy Cancel 2', 'GENERAL', 1, 'ACTIVE',
                          'MANUAL', ?, 0, 'LEGACY', 'DRAFT', 1, ?, ?)
                """, legacyDefinition, tenantId, legacyDefinition, userId, now, now);
        UUID legacyInstance = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO workflow_instances (
                    id, tenant_id, workflow_definition_id, workflow_version, business_entity_type,
                    business_entity_id, status, current_step_key, started_by, started_at,
                    engine_generation, context_json, context_schema_version, version, created_at, updated_at
                ) VALUES (?, ?, ?, 1, 'TEST', gen_random_uuid(), 'RUNNING', 'start', ?, ?,
                          'LEGACY', CAST('{}' AS jsonb), 1, 0, ?, ?)
                """, legacyInstance, tenantId, legacyDefinition, userId, now, now, now);

        var cancelled = executionService.cancel(tenantId, legacyInstance, userId, "legacy path");

        assertThat(cancelled.status()).isEqualTo(WorkflowInstance.Status.CANCELLED);
    }

    // ===== fixture helpers =====

    private UUID createStep(String stepKey, String stepType, String requiredCapability,
                            String configuration, String requiredRole) {
        UUID stepId = UUID.randomUUID();
        var now = Timestamp.from(Instant.now());
        jdbc.update("""
                INSERT INTO workflow_steps (
                    id, tenant_id, workflow_definition_id, step_key, name, step_type,
                    sequence_order, configuration, required_capability, required_role,
                    version, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, 1, CAST(? AS jsonb), ?, ?, 0, ?, ?)
                """, stepId, tenantId, definitionId, stepKey, stepKey, stepType,
                configuration, requiredCapability, requiredRole, now, now);
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
