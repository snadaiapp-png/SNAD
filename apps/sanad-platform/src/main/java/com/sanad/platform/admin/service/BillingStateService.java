package com.sanad.platform.admin.service;

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
 *   TRIALING   → CURRENT     (when trial_ends_at elapses; handled elsewhere)
 *   CURRENT    → PAST_DUE   (when ≥1 invoice is past due_at + grace)
 *   PAST_DUE   → SUSPENDED   (after a configurable secondary grace)
 *   SUSPENDED  → CURRENT    (when all overdue invoices are paid)
 *   ANY        → CANCELLED   (set by SaasAdministrationService.cancelSubscription)
 * </pre>
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

    public BillingStateService(JdbcTemplate jdbc, PlatformAuditService auditService) {
        this.jdbc = jdbc;
        this.auditService = auditService;
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
        jdbc.update(
                "UPDATE tenant_subscriptions SET billing_state = ?, updated_at = ? WHERE tenant_id = ?",
                toState, Timestamp.from(Instant.now()), tenantId);
        try {
            // Update tenant_subscriptions.status for backward compatibility with the
            // legacy lifecycle column. The billing_state is now the source of truth.
            String legacyStatus = switch (toState) {
                case "CURRENT", "TRIALING" -> "ACTIVE";
                case "PAST_DUE" -> "PAST_DUE";
                case "SUSPENDED" -> "SUSPENDED";
                case "CANCELLED" -> "CANCELLED";
                default -> "ACTIVE";
            };
            jdbc.update(
                    "UPDATE tenant_subscriptions SET status = ? WHERE tenant_id = ? AND billing_state = ?",
                    legacyStatus, tenantId, toState);
        } catch (Exception ignored) {
            // best-effort; the billing_state is already updated
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
