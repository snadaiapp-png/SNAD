package com.sanad.platform.subscription.provisioning;

import com.sanad.platform.subscription.lifecycle.SubscriptionCommandService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Runs provisioning jobs. A subscription becomes ACTIVE only after its
 * PROVISION_SUBSCRIPTION job succeeds.
 *
 * <p>Steps are keyed ({@code UNIQUE(job_id, step_key)}); already-succeeded
 * steps are skipped on retry, making the runner idempotent. Failures mark the
 * job RETRYING (first attempt) or FAILED — never silently successful.
 *
 * <p>R0C-7 — lifecycle convergence: the final VALIDATE step no longer writes
 * {@code tenant_subscriptions.status} directly. Successful validation routes
 * the activation through the canonical lifecycle command authority
 * (ACTIVATE: status + command ledger + SubscriptionLifecycle validation). An
 * already-ACTIVE subscription is an idempotent no-op (re-provisioning and
 * retries succeed without a second transition); a terminal or otherwise
 * non-activatable subscription fails closed. Job status, step status, retry
 * and idempotency behavior are unchanged.</p>
 */
@Service
public class ProvisioningJobRunner {

    public static final List<String> PROVISION_STEPS = List.of(
            "ENABLE_APPLICATIONS", "RESOLVE_ENTITLEMENTS", "VALIDATE");

    private static final Set<String> TERMINAL_STATUSES =
            Set.of("CANCELLED", "EXPIRED", "TERMINATED");

    private final JdbcTemplate jdbc;
    private final SubscriptionCommandService commandService;

    public ProvisioningJobRunner(JdbcTemplate jdbc, SubscriptionCommandService commandService) {
        this.jdbc = jdbc;
        this.commandService = commandService;
    }

    public record JobOutcome(UUID jobId, String status, List<String> skippedSteps) {
    }

    @Transactional
    public JobOutcome run(UUID jobId) {
        Map<String, Object> job = jdbc.queryForMap(
                "SELECT * FROM provisioning_jobs WHERE id = ?", jobId);
        UUID tenantId = (UUID) job.get("tenant_id");
        UUID subscriptionId = (UUID) job.get("subscription_id");
        int attempts = job.get("attempts") instanceof Number n ? n.intValue() + 1 : 1;

        jdbc.update(
                "UPDATE provisioning_jobs SET status = 'RUNNING', started_at = NOW(), "
                        + "attempts = ?, updated_at = NOW() WHERE id = ?",
                attempts, jobId);

        List<String> completed = jdbc.queryForList(
                "SELECT step_key FROM provisioning_job_steps WHERE job_id = ? AND status = 'SUCCEEDED'",
                String.class, jobId);
        List<String> skipped = new ArrayList<>();
        List<String> failures = new ArrayList<>();

        for (String step : PROVISION_STEPS) {
            if (completed.contains(step)) {
                skipped.add(step);
                continue;
            }
            try {
                String detail = executeStep(step, jobId, tenantId, subscriptionId);
                recordStep(jobId, step, "SUCCEEDED", detail);
            } catch (Exception e) {
                recordStep(jobId, step, "FAILED", truncate(e.getMessage()));
                failures.add(step + ": " + truncate(e.getMessage()));
            }
        }

        if (!failures.isEmpty()) {
            String jobStatus = attempts <= 1 ? "RETRYING" : "FAILED";
            jdbc.update(
                    "UPDATE provisioning_jobs SET status = '" + jobStatus + "', error_code = 'STEP_FAILED', "
                            + "error_message = ?, updated_at = NOW() WHERE id = ?",
                    truncate(String.join("; ", failures)), jobId);
            return new JobOutcome(jobId, "FAILED", skipped);
        }

        jdbc.update(
                "UPDATE provisioning_jobs SET status = 'SUCCEEDED', completed_at = NOW(), "
                        + "updated_at = NOW(), error_code = NULL, error_message = NULL WHERE id = ?",
                jobId);
        return new JobOutcome(jobId, "SUCCEEDED", skipped);
    }

    private String executeStep(String step, UUID jobId, UUID tenantId, UUID subscriptionId) {
        return switch (step) {
            case "ENABLE_APPLICATIONS" -> {
                Integer items = jdbc.queryForObject(
                        "SELECT COUNT(*) FROM subscription_items WHERE subscription_id = ? AND status = 'ACTIVE'",
                        Integer.class, subscriptionId);
                if (items == null || items == 0) {
                    throw new IllegalStateException("Subscription has no ACTIVE items to provision");
                }
                yield items + " active item(s) resolved from catalog";
            }
            case "RESOLVE_ENTITLEMENTS" -> {
                UUID planId = jdbc.queryForObject(
                        "SELECT plan_id FROM tenant_subscriptions WHERE id = ?",
                        UUID.class, subscriptionId);
                yield "entitlement anchor resolved: plan " + planId;
            }
            case "VALIDATE" -> {
                String status = jdbc.queryForObject(
                        "SELECT status FROM tenant_subscriptions WHERE id = ?",
                        String.class, subscriptionId);
                if (status != null && TERMINAL_STATUSES.contains(status)) {
                    throw new IllegalStateException(
                            "Subscription is terminal (" + status + "); refusing to activate");
                }
                if ("ACTIVE".equals(status)) {
                    // Idempotent re-provision/retry: the subscription is already
                    // ACTIVE — no transition occurs, the job still succeeds.
                    yield "subscription already ACTIVE (idempotent)";
                }
                // R0C-7: transition the subscription to ACTIVE through the
                // canonical lifecycle authority — the job's final contract.
                // ACTIVATE only accepts DRAFT/PENDING_ACTIVATION/PENDING_PAYMENT/
                // TRIAL/TRIALING; anything else fails the step (fail-closed).
                commandService.applyCanonicalTransition(subscriptionId, "ACTIVATE",
                        "Provisioning validated", null, null);
                yield "subscription ACTIVE";
            }
            default -> throw new IllegalArgumentException("Unknown provisioning step: " + step);
        };
    }

    private void recordStep(UUID jobId, String stepKey, String status, String detail) {
        // R0C-7 defect fix: created_at is NOT NULL without a default — the
        // legacy INSERT omitted it, so every real-PostgreSQL provisioning run
        // failed at the first step record (masked by mocked-jdbc unit tests).
        jdbc.update("""
                        INSERT INTO provisioning_job_steps (
                            id, job_id, step_key, status, detail, completed_at, created_at
                        ) VALUES (?, ?, ?, ?, ?, ?, NOW())
                        """,
                UUID.randomUUID(), jobId, stepKey, status, detail,
                Timestamp.from(Instant.now()));
    }

    private static String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 1000 ? value : value.substring(0, 1000);
    }
}
