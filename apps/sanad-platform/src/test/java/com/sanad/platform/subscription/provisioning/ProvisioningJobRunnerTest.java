package com.sanad.platform.subscription.provisioning;

import com.sanad.platform.subscription.lifecycle.SubscriptionCommandService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ProvisioningJobRunner}.
 *
 * <p>Proves: steps are keyed/idempotent (already-SUCCEEDED steps are skipped),
 * success finalizes the job, failures mark the job RETRYING (not silently
 * succeeded), and completing PROVISION_SUBSCRIPTION drives the subscription
 * to ACTIVE via the canonical lifecycle transition (R0C-7: no direct status
 * write; an already-ACTIVE subscription is an idempotent no-op; a terminal
 * subscription is never activated).</p>
 *
 * <p>PATH-B G1 — PROVISIONING_STATUS_TRUTH_INVARIANT: for every run, the
 * returned {@code JobOutcome.status} MUST equal the final persisted
 * {@code provisioning_jobs.status}. The final status is derived from the
 * failure disposition (RETRYABLE vs NON_RETRYABLE) AND the retry policy —
 * never from the attempt count alone. A canonical/terminal rejection persists
 * FAILED and returns FAILED immediately; it is never parked in RETRYING.
 * This supersedes the prior Wave-1 adjudication that let a VALIDATE refusal
 * return FAILED while the row kept RETRYING bookkeeping.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ProvisioningJobRunner — idempotent provisioning")
class ProvisioningJobRunnerTest {

    @Mock
    private JdbcTemplate jdbc;

    @Mock
    private SubscriptionCommandService commandService;

    private ProvisioningJobRunner runner;

    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID SUBSCRIPTION_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID JOB_ID = UUID.fromString("f1000000-0000-0000-0000-000000000001");

    @BeforeEach
    void setUp() {
        runner = new ProvisioningJobRunner(jdbc, commandService);
    }

    private void jobRow(String status, int attempts) {
        when(jdbc.queryForMap(eq("SELECT * FROM provisioning_jobs WHERE id = ?"), eq(JOB_ID)))
                .thenReturn(Map.of(
                        "id", JOB_ID,
                        "tenant_id", TENANT_ID,
                        "subscription_id", SUBSCRIPTION_ID,
                        "action", "PROVISION_SUBSCRIPTION",
                        "status", status,
                        "attempts", attempts));
    }

    private void noCompletedSteps() {
        when(jdbc.<String>queryForList(
                contains("SELECT step_key FROM provisioning_job_steps"),
                eq(String.class), eq(JOB_ID)))
                .thenReturn(List.of());
    }

    @Test
    @DisplayName("success: all steps recorded, job SUCCEEDED, canonical ACTIVATE transition")
    void successPath() {
        jobRow("PENDING", 0);
        noCompletedSteps();
        when(jdbc.queryForObject(
                contains("SELECT COUNT(*) FROM subscription_items"), eq(Integer.class),
                eq(SUBSCRIPTION_ID))).thenReturn(2);
        when(jdbc.<java.util.UUID>queryForObject(
                contains("SELECT plan_id FROM tenant_subscriptions"), eq(java.util.UUID.class),
                eq(SUBSCRIPTION_ID))).thenReturn(
                java.util.UUID.fromString("c3000000-0000-0000-0000-000000000001"));
        when(jdbc.queryForObject(
                contains("SELECT status FROM tenant_subscriptions"), eq(String.class),
                eq(SUBSCRIPTION_ID))).thenReturn("PENDING_ACTIVATION");

        ProvisioningJobRunner.JobOutcome outcome = runner.run(JOB_ID);

        assertThat(outcome.status()).isEqualTo("SUCCEEDED");
        verify(jdbc).update(contains("UPDATE provisioning_jobs SET status = 'RUNNING'"),
                any(), eq(JOB_ID));
        verify(jdbc).update(contains("INSERT INTO provisioning_job_steps"),
                any(), eq(JOB_ID), eq("RESOLVE_ENTITLEMENTS"), any(), any(), any());
        verify(jdbc).update(contains("UPDATE provisioning_jobs SET status = 'SUCCEEDED'"),
                eq(JOB_ID));
        // R0C-7: activation flows through the canonical command authority —
        // the runner never writes tenant_subscriptions.status directly.
        verify(commandService).applyCanonicalTransition(
                eq(SUBSCRIPTION_ID), eq("ACTIVATE"), eq("Provisioning validated"), any(), any());
        verify(jdbc, never()).update(contains("UPDATE tenant_subscriptions SET status"), (Object) any());
    }

    @Test
    @DisplayName("retry: already-succeeded steps are skipped (idempotent)")
    void retrySkipsCompletedSteps() {
        jobRow("RETRYING", 1);
        when(jdbc.<String>queryForList(
                contains("SELECT step_key FROM provisioning_job_steps"),
                eq(String.class), eq(JOB_ID)))
                .thenReturn(List.of("ENABLE_APPLICATIONS"));

        ProvisioningJobRunner.JobOutcome outcome = runner.run(JOB_ID);

        assertThat(outcome.status()).isEqualTo("SUCCEEDED");
        assertThat(outcome.skippedSteps()).containsExactly("ENABLE_APPLICATIONS");
    }

    @ParameterizedTest
    @ValueSource(strings = {"CANCELLED", "EXPIRED", "TERMINATED"})
    @DisplayName("terminal subscriptions are never provisioned to ACTIVE — outcome FAILED and durable status FAILED")
    void terminalSubscriptionNotActivated(String terminalStatus) {
        jobRow("PENDING", 0);
        noCompletedSteps();
        when(jdbc.queryForObject(
                contains("SELECT COUNT(*) FROM subscription_items"), eq(Integer.class),
                eq(SUBSCRIPTION_ID))).thenReturn(1);
        when(jdbc.<java.util.UUID>queryForObject(
                contains("SELECT plan_id FROM tenant_subscriptions"), eq(java.util.UUID.class),
                eq(SUBSCRIPTION_ID))).thenReturn(
                java.util.UUID.fromString("c3000000-0000-0000-0000-000000000001"));
        when(jdbc.queryForObject(
                contains("SELECT status FROM tenant_subscriptions"), eq(String.class),
                eq(SUBSCRIPTION_ID)))
                .thenReturn(terminalStatus);

        ProvisioningJobRunner.JobOutcome outcome = runner.run(JOB_ID);

        // Governed invariant: outcome == final persisted provisioning_jobs.status.
        assertThat(outcome.status()).isEqualTo("FAILED");
        verify(jdbc).update(contains("SET status = 'FAILED', error_code = 'STEP_FAILED'"),
                any(), eq(JOB_ID));
        verify(jdbc, never()).update(contains("SET status = 'RETRYING', error_code"),
                any(), eq(JOB_ID));
        verify(commandService, never()).applyCanonicalTransition(
                any(), any(), any(), any(), any());
        verify(jdbc, never()).update(contains("UPDATE tenant_subscriptions SET status = 'ACTIVE'"), (Object) any());
    }

    @Test
    @DisplayName("failed prerequisite stops pipeline before VALIDATE can activate")
    void failedPrerequisiteCannotActivateSubscription() {
        jobRow("PENDING", 0);
        noCompletedSteps();
        when(jdbc.queryForObject(
                contains("SELECT COUNT(*) FROM subscription_items"), eq(Integer.class),
                eq(SUBSCRIPTION_ID))).thenReturn(0);

        ProvisioningJobRunner.JobOutcome outcome = runner.run(JOB_ID);

        // CASE 1/CASE 8: recoverable prerequisite failure on the first eligible
        // attempt — outcome RETRYING and the durable row RETRYING; the pipeline
        // stops before RESOLVE_ENTITLEMENTS/VALIDATE can run.
        assertThat(outcome.status()).isEqualTo("RETRYING");
        verify(jdbc).update(contains("SET status = 'RETRYING', error_code = 'STEP_FAILED'"),
                any(), eq(JOB_ID));
        verify(commandService, never()).applyCanonicalTransition(
                any(), any(), any(), any(), any());
        verify(jdbc, never()).queryForObject(
                contains("SELECT status FROM tenant_subscriptions"), eq(String.class),
                eq(SUBSCRIPTION_ID));
        verify(jdbc, never()).update(
                contains("INSERT INTO provisioning_job_steps"),
                any(), eq(JOB_ID), eq("RESOLVE_ENTITLEMENTS"), any(), any(), any());
        verify(jdbc, never()).update(
                contains("INSERT INTO provisioning_job_steps"),
                any(), eq(JOB_ID), eq("VALIDATE"), any(), any(), any());
    }

    @Test
    @DisplayName("CASE 2: recoverable prerequisite failure after the retry budget — outcome FAILED and durable status FAILED")
    void recoverableFailureAfterRetryBudgetFailsTruthfully() {
        jobRow("RETRYING", 1); // attempts becomes 2 > retry budget
        noCompletedSteps();
        when(jdbc.queryForObject(
                contains("SELECT COUNT(*) FROM subscription_items"), eq(Integer.class),
                eq(SUBSCRIPTION_ID))).thenReturn(0);

        ProvisioningJobRunner.JobOutcome outcome = runner.run(JOB_ID);

        assertThat(outcome.status()).isEqualTo("FAILED");
        verify(jdbc).update(contains("SET status = 'FAILED', error_code = 'STEP_FAILED'"),
                any(), eq(JOB_ID));
        verify(jdbc, never()).update(contains("SET status = 'RETRYING', error_code"),
                any(), eq(JOB_ID));
    }

    @Test
    @DisplayName("retry rewrites previously failed keyed step instead of inserting a duplicate")
    void retryUsesConflictSafeStepUpsert() {
        jobRow("RETRYING", 1);
        noCompletedSteps();
        when(jdbc.queryForObject(
                contains("SELECT COUNT(*) FROM subscription_items"), eq(Integer.class),
                eq(SUBSCRIPTION_ID))).thenReturn(1);
        when(jdbc.<UUID>queryForObject(
                contains("SELECT plan_id FROM tenant_subscriptions"), eq(UUID.class),
                eq(SUBSCRIPTION_ID))).thenReturn(
                UUID.fromString("c3000000-0000-0000-0000-000000000001"));
        when(jdbc.queryForObject(
                contains("SELECT status FROM tenant_subscriptions"), eq(String.class),
                eq(SUBSCRIPTION_ID))).thenReturn("ACTIVE");

        ProvisioningJobRunner.JobOutcome outcome = runner.run(JOB_ID);

        assertThat(outcome.status()).isEqualTo("SUCCEEDED");
        verify(jdbc, atLeastOnce()).update(
                contains("ON CONFLICT (job_id, step_key)"),
                any(), eq(JOB_ID), any(), any(), any(), any());
    }

    @Test
    @DisplayName("already-ACTIVE subscription: idempotent no-op, no canonical transition")
    void alreadyActiveSubscriptionIsIdempotentNoOp() {
        jobRow("PENDING", 0);
        noCompletedSteps();
        when(jdbc.queryForObject(
                contains("SELECT COUNT(*) FROM subscription_items"), eq(Integer.class),
                eq(SUBSCRIPTION_ID))).thenReturn(1);
        when(jdbc.<java.util.UUID>queryForObject(
                contains("SELECT plan_id FROM tenant_subscriptions"), eq(java.util.UUID.class),
                eq(SUBSCRIPTION_ID))).thenReturn(
                java.util.UUID.fromString("c3000000-0000-0000-0000-000000000001"));
        when(jdbc.queryForObject(
                contains("SELECT status FROM tenant_subscriptions"), eq(String.class),
                eq(SUBSCRIPTION_ID))).thenReturn("ACTIVE");

        ProvisioningJobRunner.JobOutcome outcome = runner.run(JOB_ID);

        assertThat(outcome.status()).isEqualTo("SUCCEEDED");
        // No lifecycle transition occurs — the subscription is already ACTIVE.
        verify(commandService, never()).applyCanonicalTransition(
                any(), any(), any(), any(), any());
        verify(jdbc, never()).update(contains("UPDATE tenant_subscriptions SET status"), (Object) any());
    }

    @Test
    @DisplayName("PROVISIONING_STATUS_TRUTH_INVARIANT: PAUSED canonical rejection persists FAILED and returns FAILED — never DB RETRYING vs outcome FAILED")
    void canonicalRejectionPersistedStatusMatchesOutcome() {
        // RED regression for the Wave-1 contradiction: the old runner returned
        // outcome FAILED for a VALIDATE refusal on the first attempt while the
        // durable row was persisted RETRYING (forbidden: DB RETRYING /
        // outcome FAILED). The governed invariant requires both to say FAILED.
        jobRow("PENDING", 0);
        noCompletedSteps();
        when(jdbc.queryForObject(
                contains("SELECT COUNT(*) FROM subscription_items"), eq(Integer.class),
                eq(SUBSCRIPTION_ID))).thenReturn(1);
        when(jdbc.<java.util.UUID>queryForObject(
                contains("SELECT plan_id FROM tenant_subscriptions"), eq(java.util.UUID.class),
                eq(SUBSCRIPTION_ID))).thenReturn(
                java.util.UUID.fromString("c3000000-0000-0000-0000-000000000001"));
        when(jdbc.queryForObject(
                contains("SELECT status FROM tenant_subscriptions"), eq(String.class),
                eq(SUBSCRIPTION_ID))).thenReturn("PAUSED");
        org.mockito.Mockito.doThrow(new IllegalStateException(
                "Illegal subscription transition: ACTIVATE from PAUSED"))
                .when(commandService).applyCanonicalTransition(
                        eq(SUBSCRIPTION_ID), eq("ACTIVATE"), any(), any(), any());

        ProvisioningJobRunner.JobOutcome outcome = runner.run(JOB_ID);

        // CASE 6: canonical rejection is authoritative — immediately FAILED on
        // BOTH the returned outcome and the durable job row.
        assertThat(outcome.status()).isEqualTo("FAILED");
        verify(jdbc).update(contains("SET status = 'FAILED', error_code = 'STEP_FAILED'"),
                any(), eq(JOB_ID));
        verify(jdbc, never()).update(contains("SET status = 'RETRYING', error_code"),
                any(), eq(JOB_ID));
        // No direct state mutation: the activation is only attempted through
        // the canonical authority, which refused it.
        verify(jdbc, never()).update(contains("UPDATE tenant_subscriptions SET status"), (Object) any());
    }
}
