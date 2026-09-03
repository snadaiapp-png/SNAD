package com.sanad.platform.subscription.lifecycle;

import com.sanad.platform.admin.service.PlatformAuditService;
import com.sanad.platform.module.entitlement.SubscriptionEntitlementListener;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Map;
import java.util.UUID;

/**
 * Executes subscription lifecycle commands (activate, suspend, cancel, …).
 *
 * <p>Every command:
 * <ol>
 *   <li>is validated against {@link SubscriptionLifecycle} (illegal
 *       transitions are rejected before any write)</li>
 *   <li>updates {@code tenant_subscriptions.status}</li>
 *   <li>writes a {@code subscription_commands} ledger row</li>
 *   <li>writes a platform audit record</li>
 *   <li>fires the existing entitlement-recalculation events after commit</li>
 * </ol>
 *
 * <p>The status is never set directly by callers — the frontend invokes
 * commands only.
 *
 * <p>R0C-7 — single-writer convergence: this service is the ONLY authority
 * that may transition {@code tenant_subscriptions.status}. The legacy admin
 * engine, the billing state machine, and provisioning converge through the
 * internal {@link #applyCanonicalTransition} primitive (status + validation +
 * domain command ledger — no audit, no entitlement events, which remain owned
 * by each caller). The public {@link #execute} keeps the full lifecycle side
 * effects (platform audit + entitlement events).
 */
@Service
public class SubscriptionCommandService {

    private final JdbcTemplate jdbc;
    private final PlatformAuditService auditService;
    private final ApplicationEventPublisher eventPublisher;

    public SubscriptionCommandService(JdbcTemplate jdbc,
                                      PlatformAuditService auditService,
                                      ApplicationEventPublisher eventPublisher) {
        this.jdbc = jdbc;
        this.auditService = auditService;
        this.eventPublisher = eventPublisher;
    }

    public record CommandResult(UUID subscriptionId, String command,
                                String fromStatus, String toStatus) {
    }

    /**
     * R0C-7 canonical lifecycle transition primitive — the single writer of
     * {@code tenant_subscriptions.status} transitions.
     *
     * <p>Responsibilities (and nothing else):</p>
     * <ul>
     *   <li>read the current status (fail-closed on unknown subscription)</li>
     *   <li>validate the transition via {@link SubscriptionLifecycle}</li>
     *   <li>write the new status plus domain-owned transition metadata
     *       ({@code cancelled_at} is set on CANCEL/TERMINATE and cleared on
     *       RESUME, mirroring the legacy revival contract) — as a GUARDED
     *       update ({@code WHERE status = <validated fromStatus>}) so the
     *       write can never blindly overwrite a state another writer
     *       committed concurrently: a zero-affected-rows outcome re-reads
     *       the row and fails closed (R0C-8 — discovered by the
     *       activate-vs-expiry race proof; the read-validate-write window
     *       previously allowed a lost update to resurrect a terminal
     *       state, e.g. ACTIVE over a committed EXPIRED)</li>
     *   <li>write the {@code subscription_commands} domain ledger row</li>
     *   <li>join the caller's transaction (REQUIRED propagation)</li>
     * </ul>
     *
     * <p>No platform audit, no entitlement events, no caller-specific
     * metadata — those remain with each converged caller (legacy engine,
     * billing state machine, provisioning) so no side effect is ever
     * duplicated.</p>
     */
    @Transactional
    public CommandResult applyCanonicalTransition(UUID subscriptionId, String command, String reason,
                                                  UUID actorTenantId, UUID actorUserId) {
        Map<String, Object> row = readSubscription(subscriptionId);
        UUID tenantId = (UUID) row.get("tenant_id");
        String fromStatus = (String) row.get("status");

        SubscriptionLifecycle.Transition transition =
                SubscriptionLifecycle.transition(command, fromStatus);
        String toStatus = transition.toStatus();

        if (!toStatus.equals(fromStatus)) {
            // toStatus comes from the whitelisted SubscriptionLifecycle table — safe to inline.
            // R0C-8: the update is guarded by the validated fromStatus — the
            // single writer never performs a blind status overwrite. If a
            // concurrent writer committed a different status in the
            // read-validate-write window, zero rows are affected here and
            // the transition fails closed (no partial ledger, no
            // resurrection); the caller surfaces the rejection.
            int updated = switch (command) {
                case "CANCEL", "TERMINATE" -> jdbc.update(
                        "UPDATE tenant_subscriptions SET status = '" + toStatus + "', "
                                + "cancelled_at = NOW(), updated_at = NOW() WHERE id = ? AND status = ?",
                        subscriptionId, fromStatus);
                case "RESUME" -> jdbc.update(
                        "UPDATE tenant_subscriptions SET status = '" + toStatus + "', "
                                + "cancelled_at = NULL, updated_at = NOW() WHERE id = ? AND status = ?",
                        subscriptionId, fromStatus);
                default -> jdbc.update(
                        "UPDATE tenant_subscriptions SET status = '" + toStatus + "', "
                                + "updated_at = NOW() WHERE id = ? AND status = ?",
                        subscriptionId, fromStatus);
            };
            if (updated == 0) {
                String current = (String) readSubscription(subscriptionId).get("status");
                throw new IllegalStateException(
                        "Concurrent subscription transition: " + command + " validated from "
                                + fromStatus + " but the row is now " + current
                                + " — refusing the blind overwrite");
            }
        }

        jdbc.update("""
                        INSERT INTO subscription_commands (
                            id, subscription_id, tenant_id, command, from_status, to_status,
                            reason, actor_tenant_id, actor_user_id, correlation_id, created_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())
                        """,
                UUID.randomUUID(), subscriptionId, tenantId, command, fromStatus, toStatus,
                reason, actorTenantId, actorUserId, null);
        return new CommandResult(subscriptionId, command, fromStatus, toStatus);
    }

    @Transactional
    public CommandResult execute(UUID subscriptionId, String command, String reason) {
        return execute(subscriptionId, command, reason, null, null);
    }

    @Transactional
    public CommandResult execute(UUID subscriptionId, String command, String reason,
                                 UUID actorTenantId, UUID actorUserId) {
        // R0C-7: the public command path IS the canonical primitive plus the
        // lifecycle-owned platform audit and entitlement event — the
        // transition SQL exists exactly once (applyCanonicalTransition).
        Map<String, Object> row = readSubscription(subscriptionId);
        UUID tenantId = (UUID) row.get("tenant_id");
        UUID planId = (UUID) row.get("plan_id");
        String fromStatus = (String) row.get("status");

        CommandResult result =
                applyCanonicalTransition(subscriptionId, command, reason, actorTenantId, actorUserId);

        auditService.success(null, tenantId, "SUBSCRIPTION_" + command,
                "subscription", subscriptionId.toString(), reason, fromStatus, result.toStatus());

        publishEntitlementEventAfterCommit(command, tenantId, subscriptionId, planId);
        return result;
    }

    private Map<String, Object> readSubscription(UUID subscriptionId) {
        try {
            return jdbc.queryForMap(
                    "SELECT tenant_id, plan_id, status FROM tenant_subscriptions WHERE id = ?",
                    subscriptionId);
        } catch (EmptyResultDataAccessException e) {
            throw new IllegalArgumentException("Unknown subscription: " + subscriptionId);
        }
    }

    private void publishEntitlementEventAfterCommit(String command, UUID tenantId,
                                                    UUID subscriptionId, UUID planId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            publishEvent(command, tenantId, subscriptionId, planId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                publishEvent(command, tenantId, subscriptionId, planId);
            }
        });
    }

    private void publishEvent(String command, UUID tenantId, UUID subscriptionId, UUID planId) {
        switch (command) {
            case "ACTIVATE", "RENEW", "PAYMENT_RECEIVED" -> eventPublisher.publishEvent(
                    new SubscriptionEntitlementListener.SubscriptionActivatedEvent(
                            tenantId, subscriptionId, planId));
            case "SUSPEND" -> eventPublisher.publishEvent(
                    new SubscriptionEntitlementListener.SubscriptionSuspendedEvent(tenantId, subscriptionId));
            case "RESUME" -> eventPublisher.publishEvent(
                    new SubscriptionEntitlementListener.SubscriptionResumedEvent(tenantId, subscriptionId));
            case "CANCEL", "TERMINATE", "EXPIRE" -> eventPublisher.publishEvent(
                    new SubscriptionEntitlementListener.SubscriptionCancelledEvent(tenantId, subscriptionId));
            default -> { /* no entitlement event for this command */ }
        }
    }
}
