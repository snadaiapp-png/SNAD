package com.sanad.platform.admin.service;

import com.sanad.platform.subscription.lifecycle.SubscriptionCommandService;
import com.sanad.platform.subscription.lifecycle.SubscriptionLifecycle;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Subscription billing-state machine + dunning scheduler.
 *
 * <p>The {@code billing_state} column on {@code tenant_subscriptions}
 * (added by V20260815_20) is derived from invoice due dates and a
 * configurable grace period. This service is responsible for:
 *
 * <ul>
 *   <li>{@link #evaluateAndTransition(UUID)} — single-tenant evaluation
 *       invoked by {@code markInvoicePaid} (after a successful payment)
 *       and by the {@link #runDunningCycle()} scheduler.</li>
 *   <li>{@link #runDunningCycle()} — platform-wide scheduled job that
 *       scans subscriptions in CURRENT/PAST_DUE state and transitions
 *       them based on overdue invoices.</li>
 * </ul>
 *
 * <p>State transitions:
 * <pre>
 *   TRIALING   → (none)      (R0C-8 resolved: no automatic billing
 *                            transition out of TRIALING — the trial's
 *                            LIFECYCLE expiration TRIAL/TRIALING → EXPIRED
 *                            is owned by TrialExpirationService through the
 *                            canonical EXPIRE command; billing_state is NOT
 *                            written at trial end. The pre-R0C-8 javadoc
 *                            claimed "TRIALING → CURRENT when trial_ends_at
 *                            elapses, handled elsewhere" — that "elsewhere"
 *                            never existed and the claim is withdrawn.)
 *   CURRENT    → PAST_DUE   (when ≥1 invoice is past due_at + grace)
 *   PAST_DUE   → SUSPENDED   (after a configurable secondary grace)
 *   SUSPENDED  → CURRENT    (when all overdue invoices are paid)
 *   ANY        → CANCELLED   (set by SaasAdministrationService.cancelSubscription)
 * </pre>
 *
 * <p>R0C-7 — lifecycle convergence: this service remains the sole authority
 * for {@code billing_state}, but every billing transition that must be
 * reflected in {@code tenant_subscriptions.status} now flows through the
 * canonical lifecycle command authority
 * ({@link SubscriptionCommandService#applyCanonicalTransition}) inside the
 * SAME transaction: MARK_PAST_DUE, SUSPEND, PAYMENT_RECEIVED. When the
 * lifecycle authority rejects the transition for the current status (e.g. a
 * CANCELLED subscription can never be resurrected by a payment), the whole
 * transition is skipped atomically — {@code billing_state} and
 * {@code status} can never diverge (BILLING_LIFECYCLE_PARTIAL_STATE is
 * impossible; the legacy best-effort swallowed mirror is gone).
 *
 * <p>The dunning scheduler is enabled via the {@code sanad.tenancy.billing.dunning-enabled}
 * property (default {@code false}). Production deployments enable it
 * via env var {@code SANAD_DUNNING_ENABLED=true}. The cadence is
 * fixed at one hour (cannot be lower).
 */
@Service
public class BillingStateService {

    /** Hours of grace after the invoice due_at before transitioning CURRENT → PAST_DUE. */
    private static final long PAST_DUE_GRACE_HOURS = 24L * 3L;     // 3 days

    /** Hours of grace in PAST_DUE before transitioning PAST_DUE → SUSPENDED. */
    private static final long SUSPEND_GRACE_HOURS = 24L * 7L;     // 7 days

    private final JdbcTemplate jdbc;
    private final PlatformAuditService auditService;
    private final SubscriptionCommandService commandService;

    public BillingStateService(JdbcTemplate jdbc, PlatformAuditService auditService,
                               SubscriptionCommandService commandService) {
        this.jdbc = jdbc;
        this.auditService = auditService;
        this.commandService = commandService;
    }

    /**
     * Evaluate one tenant's subscription billing state and apply the appropriate
     * transition. Idempotent — if no transition is needed, no rows are updated.
     *
     * @return the new billing_state (or null if subscription not found)
     */
    @Transactional
    public String evaluateAndTransition(UUID tenantId) {
        if (tenantId == null) return null;
        Map<String, Object> sub = findSubscription(tenantId);
        if (sub == null) return null;

        String currentState = (String) sub.get("billing_state");
        if ("CANCELLED".equals(currentState) || "TRIALING".equals(currentState)) {
            return currentState; // no automatic transitions out of these
        }

        long overdueCount = countOverdueInvoices(tenantId, 0L);          // past due_at (no grace)
        long pastDueGraceOverdueCount = countOverdueInvoices(tenantId, PAST_DUE_GRACE_HOURS);
        long suspendGraceOverdueCount = countOverdueInvoices(tenantId, SUSPEND_GRACE_HOURS);

        String targetState;
        if (suspendGraceOverdueCount > 0 && !"SUSPENDED".equals(currentState)) {
            targetState = "SUSPENDED";
        } else if (pastDueGraceOverdueCount > 0 && !"PAST_DUE".equals(currentState)
                && !"SUSPENDED".equals(currentState)) {
            targetState = "PAST_DUE";
        } else if (overdueCount == 0
                && ("PAST_DUE".equals(currentState) || "SUSPENDED".equals(currentState))) {
            targetState = "CURRENT";
        } else {
            return currentState; // no change
        }

        applyTransition(tenantId, currentState, targetState);
        return targetState;
    }

    /**
     * Run the dunning cycle across all active subscriptions. Called by the
     * {@link #runDunningCycle()} scheduler; also exposed for manual triggering
     * by the {@code BILLING.ADMIN} capability.
     *
     * <p>This method is intentionally defensive — one tenant's failure does not
     * abort the cycle.
     *
     * @return the number of subscriptions evaluated
     */
    @Transactional
    public int runDunningCycleOnce() {
        List<UUID> tenantIds = jdbc.queryForList(
                "SELECT tenant_id FROM tenant_subscriptions "
                        + "WHERE billing_state IN ('CURRENT','PAST_DUE','SUSPENDED')",
                UUID.class);
        int evaluated = 0;
        for (UUID tenantId : tenantIds) {
            try {
                evaluateAndTransition(tenantId);
                evaluated++;
            } catch (Exception e) {
                // log and continue — one tenant's failure must not abort the cycle
                //noinspection CallToPrintStackTrace
                e.printStackTrace();
            }
        }
        return evaluated;
    }

    /**
     * Scheduled dunning cycle — runs every hour. Enabled only when
     * {@code sanad.tenancy.billing.dunning-enabled=true}.
     */
    @Scheduled(fixedDelayString = "${sanad.tenancy.billing.dunning-interval-ms:3600000}")
    public void runDunningCycle() {
        boolean enabled = Boolean.parseBoolean(
                System.getProperty("sanad.tenancy.billing.dunning-enabled",
                        System.getenv() == null ? "false"
                                : System.getenv().getOrDefault("SANAD_DUNNING_ENABLED", "false")));
        if (!enabled) return;
        runDunningCycleOnce();
    }

    // ============================================================
    // Helpers
    // ============================================================

    private Map<String, Object> findSubscription(UUID tenantId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT billing_state FROM tenant_subscriptions WHERE tenant_id = ?",
                tenantId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private long countOverdueInvoices(UUID tenantId, long graceHours) {
        Instant threshold = Instant.now().minusSeconds(graceHours * 3600L);
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM billing_invoices "
                        + "WHERE tenant_id = ? AND status = 'OPEN' AND due_at < ?",
                Long.class, tenantId, Timestamp.from(threshold));
        return count == null ? 0L : count;
    }

    private void applyTransition(UUID tenantId, String fromState, String toState) {
        if (fromState.equals(toState)) return;

        // R0C-7: resolve the subscription and map the billing target state to
        // the canonical lifecycle command that owns the status transition.
        Map<String, Object> sub = jdbc.queryForList(
                "SELECT id, status FROM tenant_subscriptions WHERE tenant_id = ?", tenantId)
                .stream().findFirst().orElse(null);
        if (sub == null) return;

        UUID subscriptionId = (UUID) sub.get("id");
        String status = (String) sub.get("status");

        String command = switch (toState) {
            case "PAST_DUE" -> "MARK_PAST_DUE";
            case "SUSPENDED" -> "SUSPEND";
            case "CURRENT" -> "PAYMENT_RECEIVED";
            default -> null; // never a production dunning target (CANCELLED/TRIALING are early-returned)
        };
        String targetStatus = switch (toState) {
            case "CURRENT" -> "ACTIVE";
            case "PAST_DUE" -> "PAST_DUE";
            case "SUSPENDED" -> "SUSPENDED";
            default -> null;
        };

        if (command == null || targetStatus == null) {
            // Unsupported dunning target: nothing to converge on — report and
            // leave both columns untouched (no partial state).
            return;
        }

        if (status.equals(targetStatus)) {
            // The lifecycle status already reflects the target — update the
            // billing state alone; the pair stays consistent.
            jdbc.update(
                    "UPDATE tenant_subscriptions SET billing_state = ?, updated_at = ? WHERE tenant_id = ?",
                    toState, Timestamp.from(Instant.now()), tenantId);
        } else if (SubscriptionLifecycle.isLegal(command, status)) {
            // billing_state + canonical lifecycle transition commit (or roll
            // back) as ONE unit — a failure in the status write rolls the
            // billing_state write back with it.
            jdbc.update(
                    "UPDATE tenant_subscriptions SET billing_state = ?, updated_at = ? WHERE tenant_id = ?",
                    toState, Timestamp.from(Instant.now()), tenantId);
            commandService.applyCanonicalTransition(subscriptionId, command,
                    "Billing state " + fromState + " -> " + toState, null, null);
        } else {
            // The lifecycle authority rejects the transition for the current
            // status (e.g. a CANCELLED subscription must never be resurrected
            // by a payment, and a PAUSED one must not be force-dunned). Skip
            // the transition atomically — billing_state and status stay as
            // they are; no partial state, no swallowed exception.
            return;
        }

        try {
            auditService.success(null, tenantId,
                    "SUBSCRIPTION.BILLING_STATE.CHANGED", "TENANT_SUBSCRIPTION",
                    tenantId.toString(),
                    "from=" + fromState + ",to=" + toState, fromState, toState);
        } catch (Exception ignored) {
            // audit failure must not break the state machine
        }
    }
}
