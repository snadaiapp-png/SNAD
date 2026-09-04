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

    @Transactional
    public CommandResult execute(UUID subscriptionId, String command, String reason) {
        return execute(subscriptionId, command, reason, null, null);
    }

    @Transactional
    public CommandResult execute(UUID subscriptionId, String command, String reason,
                                 UUID actorTenantId, UUID actorUserId) {
        Map<String, Object> row;
        try {
            row = jdbc.queryForMap(
                    "SELECT tenant_id, plan_id, status FROM tenant_subscriptions WHERE id = ?",
                    subscriptionId);
        } catch (EmptyResultDataAccessException e) {
            throw new IllegalArgumentException("Unknown subscription: " + subscriptionId);
        }
        UUID tenantId = (UUID) row.get("tenant_id");
        UUID planId = (UUID) row.get("plan_id");
        String fromStatus = (String) row.get("status");

        SubscriptionLifecycle.Transition transition =
                SubscriptionLifecycle.transition(command, fromStatus);
        String toStatus = transition.toStatus();

        if (!toStatus.equals(fromStatus)) {
            // toStatus comes from the whitelisted SubscriptionLifecycle table — safe to inline
            if ("CANCEL".equals(command) || "TERMINATE".equals(command)) {
                jdbc.update(
                        "UPDATE tenant_subscriptions SET status = '" + toStatus + "', "
                                + "cancelled_at = NOW(), updated_at = NOW() WHERE id = ?",
                        subscriptionId);
            } else {
                jdbc.update(
                        "UPDATE tenant_subscriptions SET status = '" + toStatus + "', "
                                + "updated_at = NOW() WHERE id = ?",
                        subscriptionId);
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

        auditService.success(null, tenantId, "SUBSCRIPTION_" + command,
                "subscription", subscriptionId.toString(), reason, fromStatus, toStatus);

        publishEntitlementEventAfterCommit(command, tenantId, subscriptionId, planId);
        return new CommandResult(subscriptionId, command, fromStatus, toStatus);
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
