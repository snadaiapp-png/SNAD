package com.sanad.platform.subscription.lifecycle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * R0C-8 — runtime driver for trial expiration.
 *
 * <p>{@code SubscriptionLifecycle} defines the canonical trial-expiration
 * contract — {@code EXPIRE: TRIAL/TRIALING/GRACE_PERIOD → EXPIRED} — but
 * until R0C-8 nothing invoked it when {@code trial_ends_at} elapsed: an
 * expired trial remained TRIALING indefinitely (RED-01..04, see
 * {@code TrialExpirationRedPostgresTest}). This service closes that gap.</p>
 *
 * <p><b>Contract (resolved from repository evidence — no invented
 * semantics):</b></p>
 * <ul>
 *   <li><b>Lifecycle effect</b> — due trials ({@code status IN (TRIAL,
 *       TRIALING)}, non-null {@code trial_ends_at <= } execution time) are
 *       transitioned to {@code EXPIRED} exclusively through the canonical
 *       command authority {@link SubscriptionCommandService#execute} (never
 *       a direct status write). The public execute path owns the
 *       {@code subscription_commands} ledger row, the
 *       {@code SUBSCRIPTION_EXPIRE} platform audit entry and the
 *       {@code SubscriptionCancelledEvent} entitlement event — exactly once
 *       each.</li>
 *   <li><b>Billing effect</b> — NONE. {@code billing_state} is not written:
 *       {@code BillingStateService} remains its sole authority and no
 *       authoritative behavior requires a billing transition at trial end
 *       (expired rows are inert to the dunning scan). The stale
 *       "TRIALING → CURRENT, handled elsewhere" javadoc claim was corrected
 *       in the R0C-8 commit — "elsewhere" never existed.</li>
 *   <li><b>Invoice/credit/proration effect</b> — NONE. No invoice is issued,
 *       no credit or refund is moved; the operator-initiated RENEW remains
 *       the only sanctioned trial→paid conversion (it issues the recurring
 *       invoice).</li>
 *   <li><b>Time authority</b> — one execution timestamp per cycle
 *       ({@link Clock#instant()}); the injectable clock makes due-ness
 *       deterministic in tests. Trial ends themselves are read from the
 *       row, never recomputed.</li>
 *   <li><b>Idempotency &amp; concurrency</b> — each subscription is
 *       processed in its own transaction that first re-checks the row with
 *       {@code SELECT ... FOR UPDATE}: a second worker (or an operator
 *       CANCEL/TERMINATE/ACTIVATE that won the race) makes the re-check
 *       fail and the row is skipped — no second transition, no duplicate
 *       ledger/audit/event, no terminal-state resurrection. Rows are
 *       processed in a deterministic order (oldest trial end first), so
 *       concurrent workers never deadlock.</li>
 *   <li><b>Failure isolation</b> — one invalid subscription is logged and
 *       skipped; the cycle continues (per-subscription try/catch, SLF4J
 *       logging — never printStackTrace).</li>
 * </ul>
 *
 * <p><b>Scheduler wiring (repository convention — double gate):</b>
 * the {@code @Scheduled} method only fires when the global
 * {@code scheduling.enabled=true} ({@code SchedulingConfig}) AND the
 * job-level {@code sanad.tenancy.billing.trial-expiry-enabled} property /
 * {@code SANAD_TRIAL_EXPIRY_ENABLED} env var is set (default {@code false},
 * same shape as the dunning scheduler). The default cadence is one hour —
 * deliberately NOT sub-hour — and is overridable via
 * {@code sanad.tenancy.billing.trial-expiry-interval-ms}. In tests the bean
 * is created but the scheduled method is never invoked automatically;
 * tests call {@link #runTrialExpiryCycleOnce()} directly.</p>
 *
 * <p><b>Historical policy:</b> this driver intentionally does expire any
 * due trial it sees — but it is disabled by default, so production data is
 * untouched until an operator enables it. Discovered historical overdue
 * trials are classified REPORT_ONLY by the reconciliation battery
 * ({@code OVERDUE_TRIAL_NOT_TRANSITIONED}); operator-approved repair of
 * historical rows is a later task (see the R0C-8 plan §17/§29).</p>
 */
@Service
public class TrialExpirationService {

    private static final Logger log = LoggerFactory.getLogger(TrialExpirationService.class);

    /** Maximum due trials processed per scheduler tick (safety bound, WorkflowSlaScheduler convention). */
    static final int MAX_DUE_PER_TICK = 200;

    private final JdbcTemplate jdbc;
    private final SubscriptionCommandService commandService;
    private final TransactionTemplate transactions;
    private final Clock clock;

    @Autowired
    public TrialExpirationService(JdbcTemplate jdbc,
                                  SubscriptionCommandService commandService,
                                  PlatformTransactionManager transactionManager) {
        this(jdbc, commandService, new TransactionTemplate(transactionManager), Clock.systemUTC());
    }

    public TrialExpirationService(JdbcTemplate jdbc,
                                  SubscriptionCommandService commandService,
                                  TransactionTemplate transactions,
                                  Clock clock) {
        this.jdbc = jdbc;
        this.commandService = commandService;
        this.transactions = transactions;
        this.clock = clock;
    }

    /**
     * Scheduled trial-expiration cycle — one hour by default, gated by both
     * {@code scheduling.enabled} (global) and
     * {@code sanad.tenancy.billing.trial-expiry-enabled} (job-level, default
     * {@code false}; production enables it via
     * {@code SANAD_TRIAL_EXPIRY_ENABLED=true}).
     */
    @Scheduled(fixedDelayString = "${sanad.tenancy.billing.trial-expiry-interval-ms:3600000}",
               initialDelayString = "${sanad.tenancy.billing.trial-expiry-initial-delay-ms:60000}")
    public void runTrialExpiryCycle() {
        boolean enabled = Boolean.parseBoolean(
                System.getProperty("sanad.tenancy.billing.trial-expiry-enabled",
                        System.getenv() == null ? "false"
                                : System.getenv().getOrDefault("SANAD_TRIAL_EXPIRY_ENABLED", "false")));
        if (!enabled) {
            log.debug("TrialExpirationService: trial-expiry-enabled=false, skipping tick");
            return;
        }
        runTrialExpiryCycleOnce();
    }

    /**
     * Testable entry point — does NOT check the enable flag.
     * Tests call this directly to verify behavior.
     *
     * <p>Must run WITHOUT an outer transaction so each due trial commits (or
     * rolls back) in its own short transaction — one subscription's failure
     * cannot pollute the others' transaction state.</p>
     */
    @Transactional(propagation = Propagation.NEVER)
    public TrialExpiryResult runTrialExpiryCycleOnce() {
        // Single execution timestamp for the whole cycle — deterministic
        // due-ness (never a scattered Instant.now() per row).
        Instant executionTime = clock.instant();

        List<DueTrial> due = findDueTrials(executionTime);
        int expired = 0;
        int skipped = 0;
        int failed = 0;

        for (DueTrial trial : due) {
            try {
                if (expireIfStillDue(trial, executionTime)) {
                    expired++;
                } else {
                    skipped++;
                }
            } catch (Exception e) {
                failed++;
                // Failure isolation: log with full context and continue —
                // one invalid subscription must not prevent the cycle from
                // processing the remaining due trials.
                log.error("TrialExpirationService: failed to expire subscription {} "
                                + "(tenant {}, trial ended {}): {}",
                        trial.subscriptionId(), trial.tenantId(), trial.trialEndsAt(),
                        e.getMessage(), e);
            }
        }

        if (!due.isEmpty() || failed > 0) {
            log.info("TrialExpirationService: executedAt={} due={} expired={} skipped={} failed={}",
                    executionTime, due.size(), expired, skipped, failed);
        }
        return new TrialExpiryResult(due.size(), expired, skipped, failed, executionTime);
    }

    /**
     * Expires one due trial through the canonical authority, guarded by a
     * locked re-check inside a dedicated transaction.
     *
     * <p>The {@code SELECT ... FOR UPDATE} row lock serializes concurrent
     * workers: the second worker blocks until the first commits, then its
     * re-check sees the already-transitioned status and skips — exactly one
     * effective transition, ledger row, audit entry and entitlement event
     * per due trial ({@code DUPLICATE_EXPIRATION_TRANSITIONS = 0}). An
     * operator CANCEL/TERMINATE/ACTIVATE that won the race equally wins the
     * re-check: the driver never resurrects or rewrites a state it does not
     * own.</p>
     *
     * @return {@code true} if this call performed the expiration,
     *         {@code false} if the row was already handled / no longer due
     */
    private boolean expireIfStillDue(DueTrial trial, Instant executionTime) {
        Boolean expired = transactions.execute(txStatus -> {
            // Lock + re-check: the scan is only a hint — the row may have
            // changed between the scan and this transaction.
            Recheck row = jdbc.queryForObject(
                    "SELECT status, trial_ends_at FROM tenant_subscriptions WHERE id = ? FOR UPDATE",
                    (rs, i) -> new Recheck(rs.getString("status"), rs.getTimestamp("trial_ends_at")),
                    trial.subscriptionId());
            if (row == null || row.trialEndsAt() == null
                    || !"TRIAL".equals(row.status()) && !"TRIALING".equals(row.status())
                    || row.trialEndsAt().toInstant().isAfter(executionTime)) {
                // Already expired by another worker, renewed/cancelled/
                // activated by an operator, or trial end moved: skip — never
                // a blind status update.
                return Boolean.FALSE;
            }
            Instant trialEnd = row.trialEndsAt().toInstant();
            // The canonical public command path owns the side effects:
            // status transition (validated), subscription_commands ledger,
            // SUBSCRIPTION_EXPIRE audit and SubscriptionCancelledEvent —
            // exactly once each. System actor (null/null), joins THIS
            // transaction via REQUIRED propagation.
            commandService.execute(trial.subscriptionId(), "EXPIRE",
                    "Trial expired at " + trialEnd + " (trial-expiry runtime driver)",
                    null, null);
            return Boolean.TRUE;
        });
        return Boolean.TRUE.equals(expired);
    }

    private List<DueTrial> findDueTrials(Instant executionTime) {
        return jdbc.query(
                "SELECT id, tenant_id, trial_ends_at FROM tenant_subscriptions "
                        + "WHERE status IN ('TRIAL', 'TRIALING') "
                        + "AND trial_ends_at IS NOT NULL "
                        + "AND trial_ends_at <= ? "
                        + "ORDER BY trial_ends_at ASC, id ASC "
                        + "LIMIT " + MAX_DUE_PER_TICK,
                (rs, i) -> new DueTrial(
                        rs.getObject("id", UUID.class),
                        rs.getObject("tenant_id", UUID.class),
                        rs.getTimestamp("trial_ends_at").toInstant()),
                Timestamp.from(executionTime));
    }

    /** Result of one cycle — used for logging and test assertions. */
    public record TrialExpiryResult(
            int dueSeen, int expired, int skipped, int failed, Instant executedAt) {
    }

    record DueTrial(UUID subscriptionId, UUID tenantId, Instant trialEndsAt) {
    }

    record Recheck(String status, Timestamp trialEndsAt) {
    }
}
