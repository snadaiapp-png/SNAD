package com.sanad.platform.workflow.application;

import com.sanad.platform.workflow.domain.WorkflowExecutionAttempt;
import com.sanad.platform.workflow.domain.WorkflowIncident;
import com.sanad.platform.workflow.domain.WorkflowSystemActionException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.Map;
import java.util.UUID;

/**
 * Resilient system-action execution (design decisions O3/P3/AF3).
 *
 * <p>Retry policy: transient infrastructure failures retry with bounded
 * exponential backoff; business validation failures never retry. After
 * retry exhaustion or a permanent failure an incident is opened — the
 * platform never silently converts exhaustion into success. Execution is
 * idempotent per idempotency key: a replayed key returns the prior outcome
 * without re-invoking the adapter.</p>
 */
@Service
public class WorkflowSystemActionService {

    private static final int DEFAULT_MAX_ATTEMPTS = 3;
    private static final long BASE_BACKOFF_MS = 10;

    private final JdbcTemplate jdbc;

    public WorkflowSystemActionService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private final WorkflowSystemActionAdapter.AttemptStore store = new JdbcAttemptStore();

    public record ExecutionResult(
            boolean success,
            int attemptCount,
            String failureCategory,
            UUID incidentId,
            String externalReference,
            Map<String, Object> output) {}

    @Transactional(propagation = Propagation.REQUIRED)
    public ExecutionResult execute(UUID tenantId, UUID workflowInstanceId, UUID workflowStepInstanceId,
                                   WorkflowSystemActionAdapter adapter, Map<String, Object> input,
                                   UUID correlationId, UUID causationId, String idempotencyKey,
                                   Integer maxAttemptsOverride) {
        int maxAttempts = maxAttemptsOverride != null ? maxAttemptsOverride : DEFAULT_MAX_ATTEMPTS;

        if (idempotencyKey != null && store.hasSucceeded(tenantId, idempotencyKey)) {
            var prior = jdbc.queryForMap("""
                    SELECT external_reference, outcome FROM workflow_execution_attempts
                    WHERE tenant_id = ? AND idempotency_key = ? AND outcome = 'SUCCEEDED'
                    ORDER BY attempt_number DESC LIMIT 1
                    """, tenantId, idempotencyKey);
            return new ExecutionResult(true, 0, null, null,
                    (String) prior.get("external_reference"), Map.of());
        }

        int attemptNumber = store.nextAttemptNumber(workflowStepInstanceId);
        int executed = 0;
        UUID incidentId = null;
        while (executed < maxAttempts) {
            executed++;
            attemptNumber++;
            WorkflowExecutionAttempt attempt = WorkflowExecutionAttempt.start(
                    tenantId, workflowInstanceId, workflowStepInstanceId, attemptNumber, idempotencyKey)
                    .finish(WorkflowExecutionAttempt.Outcome.IN_PROGRESS, null, null, "{}");
            store.insert(attempt);
            WorkflowSystemActionAdapter.ActionResult result;
            try {
                result = adapter.execute(new WorkflowSystemActionAdapter.ActionRequest(
                        tenantId, workflowInstanceId, workflowStepInstanceId, idempotencyKey,
                        input, correlationId, causationId, attemptNumber));
            } catch (RuntimeException e) {
                result = WorkflowSystemActionAdapter.ActionResult.transientFailure("ADAPTER_EXCEPTION");
            }

            WorkflowExecutionAttempt finished;
            if (result.success()) {
                finished = attempt.finish(WorkflowExecutionAttempt.Outcome.SUCCEEDED,
                        null, result.externalReference(), "{}");
                store.update(finished);
                return new ExecutionResult(true, attemptNumber, null, null,
                        result.externalReference(), result.output());
            }
            finished = attempt.finish(
                    result.transientFailure()
                            ? WorkflowExecutionAttempt.Outcome.FAILED_TRANSIENT
                            : WorkflowExecutionAttempt.Outcome.FAILED_PERMANENT,
                    result.failureCategory(), null, "{}");
            store.update(finished);

            if (!result.transientFailure()) {
                // Business validation failure: never blind-retried (O3).
                incidentId = openIncident(tenantId, workflowInstanceId, workflowStepInstanceId,
                        adapter.type(), result.failureCategory(), idempotencyKey);
                throw new WorkflowSystemActionException(
                        "System action " + adapter.type() + " failed permanently: "
                                + result.failureCategory(), incidentId);
            }
            try {
                Thread.sleep(BASE_BACKOFF_MS * (1L << (attemptNumber - 1)));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        incidentId = openIncident(tenantId, workflowInstanceId, workflowStepInstanceId,
                adapter.type(), "RETRY_EXHAUSTED", idempotencyKey);
        return new ExecutionResult(false, executed, "RETRY_EXHAUSTED", incidentId, null, Map.of());
    }

    /**
     * Business compensation (P3): explicit, idempotent per key, audited.
     * Compensation failure opens an incident and reports failure — it never
     * pretends the business side effect was undone.
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public ExecutionResult compensate(UUID tenantId, UUID workflowInstanceId, UUID workflowStepInstanceId,
                                      WorkflowSystemActionAdapter adapter, Map<String, Object> input,
                                      String idempotencyKey) {
        if (idempotencyKey != null && store.hasSucceeded(tenantId, idempotencyKey)) {
            return new ExecutionResult(true, 0, null, null, null, Map.of());
        }
        WorkflowExecutionAttempt attempt = WorkflowExecutionAttempt.start(
                tenantId, workflowInstanceId, workflowStepInstanceId,
                store.nextAttemptNumber(workflowStepInstanceId) + 1, idempotencyKey)
                .finish(WorkflowExecutionAttempt.Outcome.IN_PROGRESS, null, null, "{}");
        store.insert(attempt);
        try {
            var result = adapter.compensate(new WorkflowSystemActionAdapter.ActionRequest(
                    tenantId, workflowInstanceId, workflowStepInstanceId, idempotencyKey,
                    input, null, null, 1));
            if (result.success()) {
                store.update(attempt.finish(WorkflowExecutionAttempt.Outcome.SUCCEEDED,
                        null, result.externalReference(), "{}"));
                return new ExecutionResult(true, 1, null, null, result.externalReference(), result.output());
            }
            store.update(attempt.finish(WorkflowExecutionAttempt.Outcome.FAILED_PERMANENT,
                    result.failureCategory(), null, "{}"));
        } catch (UnsupportedOperationException e) {
            store.update(attempt.finish(WorkflowExecutionAttempt.Outcome.SKIPPED, "NOT_COMPENSATABLE", null, "{}"));
            return new ExecutionResult(true, 1, "NOT_COMPENSATABLE", null, null, Map.of());
        } catch (RuntimeException e) {
            store.update(attempt.finish(WorkflowExecutionAttempt.Outcome.FAILED_PERMANENT,
                    "COMPENSATION_EXCEPTION", null, "{}"));
        }
        UUID incidentId = openIncident(tenantId, workflowInstanceId, workflowStepInstanceId,
                adapter.type() + ":compensation", "COMPENSATION_FAILED", idempotencyKey);
        return new ExecutionResult(false, 1, "COMPENSATION_FAILED", incidentId, null, Map.of());
    }

    private UUID openIncident(UUID tenantId, UUID workflowInstanceId, UUID workflowStepInstanceId,
                              String source, String failureCategory, String idempotencyKey) {
        WorkflowIncident incident = WorkflowIncident.open(tenantId, workflowInstanceId,
                workflowStepInstanceId, source, WorkflowIncident.Severity.HIGH, failureCategory);
        store.insertIncident(incident);
        return incident.id();
    }

    /** JDBC persistence for attempts/incidents (tenant-scoped statements only). */
    private final class JdbcAttemptStore implements WorkflowSystemActionAdapter.AttemptStore {

        @Override
        public void insert(WorkflowExecutionAttempt attempt) {
            jdbc.update("""
                    INSERT INTO workflow_execution_attempts (
                        id, tenant_id, workflow_instance_id, step_instance_id, attempt_number,
                        idempotency_key, outcome, failure_category, external_reference,
                        diagnostics, started_at, finished_at, created_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), ?, ?, NOW())
                    """,
                    attempt.id(), attempt.tenantId(), attempt.workflowInstanceId(),
                    attempt.workflowStepInstanceId(), attempt.attemptNumber(),
                    attempt.idempotencyKey(),
                    attempt.outcome() != null ? attempt.outcome().name() : null,
                    attempt.failureCategory(), attempt.externalReference(),
                    attempt.diagnostics() != null ? attempt.diagnostics() : "{}",
                    Timestamp.from(attempt.startedAt()),
                    attempt.finishedAt() != null ? Timestamp.from(attempt.finishedAt()) : null);
        }

        @Override
        public void update(WorkflowExecutionAttempt attempt) {
            jdbc.update("""
                    UPDATE workflow_execution_attempts SET
                        outcome = ?, failure_category = ?, external_reference = ?,
                        diagnostics = CAST(? AS jsonb), finished_at = ?
                    WHERE id = ? AND tenant_id = ?
                    """,
                    attempt.outcome().name(), attempt.failureCategory(), attempt.externalReference(),
                    attempt.diagnostics() != null ? attempt.diagnostics() : "{}",
                    attempt.finishedAt() != null ? Timestamp.from(attempt.finishedAt()) : null,
                    attempt.id(), attempt.tenantId());
        }

        @Override
        public boolean hasSucceeded(UUID tenantId, String idempotencyKey) {
            if (idempotencyKey == null) return false;
            var count = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM workflow_execution_attempts
                    WHERE tenant_id = ? AND idempotency_key = ? AND outcome = 'SUCCEEDED'
                    """, Long.class, tenantId, idempotencyKey);
            return count != null && count > 0;
        }

        @Override
        public int nextAttemptNumber(UUID stepInstanceId) {
            var max = jdbc.queryForObject(
                    "SELECT COALESCE(MAX(attempt_number), 0) FROM workflow_execution_attempts "
                            + "WHERE step_instance_id = ?",
                    Integer.class, stepInstanceId);
            return max != null ? max : 0;
        }

        @Override
        public void insertIncident(WorkflowIncident incident) {
            jdbc.update("""
                    INSERT INTO workflow_incidents (
                        id, tenant_id, workflow_instance_id, step_instance_id, source,
                        severity, failure_category, status, owner, resolution,
                        created_at, updated_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, NULL, NULL, NOW(), NOW())
                    """,
                    incident.id(), incident.tenantId(), incident.workflowInstanceId(),
                    incident.workflowStepInstanceId(), incident.source(),
                    incident.severity().name(), incident.failureCategory(), incident.status().name());
        }
    }
}
