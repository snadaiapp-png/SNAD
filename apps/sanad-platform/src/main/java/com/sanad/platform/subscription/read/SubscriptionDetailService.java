package com.sanad.platform.subscription.read;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Subscription detail read model — assembles the sections rendered by the
 * detail page: overview, items, invoices, changes (command ledger + legacy
 * change events), provisioning jobs and audit references.
 *
 * <p>Names are display names; raw UUIDs are only exposed as identifiers,
 * never as user-facing labels (mission §21).
 */
@Service
public class SubscriptionDetailService {

    private final JdbcTemplate jdbc;

    public SubscriptionDetailService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record SubscriptionDetail(
            UUID id,
            Map<String, Object> overview,
            List<Map<String, Object>> items,
            List<Map<String, Object>> invoices,
            List<Map<String, Object>> changes,
            List<Map<String, Object>> provisioningJobs,
            List<Map<String, Object>> audit) {
    }

    @Transactional(readOnly = true)
    public SubscriptionDetail detail(UUID subscriptionId) {
        Map<String, Object> overview;
        try {
            overview = jdbc.queryForMap("""
                            SELECT s.id, s.tenant_id, t.name AS tenant_name, t.subdomain AS tenant_code,
                                   t.country_code, s.status, s.billing_cycle, s.seat_quantity,
                                   s.plan_id, p.name AS plan_name, p.code AS plan_code,
                                   pv.version_number AS plan_version,
                                   s.credit_balance_minor, p.currency_code,
                                   s.started_at, s.trial_ends_at,
                                   s.current_period_start, s.current_period_end,
                                   s.cancel_at_period_end, s.cancelled_at, s.created_at
                            FROM tenant_subscriptions s
                            JOIN tenants t ON t.id = s.tenant_id
                            LEFT JOIN saas_plans p ON p.id = s.plan_id
                            LEFT JOIN plan_versions pv ON pv.id = s.plan_version_id
                            WHERE s.id = ?
                            """, subscriptionId);
        } catch (EmptyResultDataAccessException e) {
            throw new IllegalArgumentException("Unknown subscription: " + subscriptionId);
        }

        return new SubscriptionDetail(
                subscriptionId,
                overview,
                jdbc.queryForList("""
                                SELECT id, item_type, name_snapshot, quantity, unit_amount_minor,
                                       currency_code, status, plan_version_id, created_at
                                FROM subscription_items WHERE subscription_id = ?
                                ORDER BY created_at, id
                                """, subscriptionId),
                jdbc.queryForList("""
                                SELECT id, invoice_number, status, currency_code, subtotal_minor,
                                       tax_minor, total_minor, amount_paid_minor,
                                       period_start, period_end, due_at, paid_at
                                FROM billing_invoices WHERE subscription_id = ?
                                ORDER BY created_at DESC LIMIT 50
                                """, subscriptionId),
                jdbc.query("""
                                SELECT 'COMMAND' AS source, command AS action, from_status, to_status,
                                        reason, created_at
                                FROM subscription_commands WHERE subscription_id = ?
                                UNION ALL
                                SELECT 'EVENT' AS source, action, NULL, NULL, reason, created_at
                                FROM subscription_change_events WHERE subscription_id = ?
                                ORDER BY created_at DESC LIMIT 100
                                """,
                        (rs, n) -> Map.<String, Object>of(
                                "source", rs.getString("source"),
                                "action", rs.getString("action"),
                                "fromStatus", rs.getObject("from_status"),
                                "toStatus", rs.getObject("to_status"),
                                "reason", rs.getString("reason"),
                                "createdAt", rs.getObject("created_at")),
                        subscriptionId, subscriptionId),
                jdbc.queryForList("""
                                SELECT id, action, status, attempts, started_at, completed_at,
                                       error_code, error_message, created_at
                                FROM provisioning_jobs WHERE subscription_id = ?
                                ORDER BY created_at DESC LIMIT 50
                                """, subscriptionId),
                jdbc.queryForList("""
                                SELECT id, action, resource_type, resource_id, reason, result, created_at
                                FROM platform_audit_logs
                                WHERE (resource_type = 'subscription' AND resource_id = ?::text)
                                   OR (resource_type = 'subscription_item' AND resource_id IN (
                                        SELECT id::text FROM subscription_items WHERE subscription_id = ?))
                                ORDER BY created_at DESC LIMIT 100
                                """, subscriptionId, subscriptionId));
    }
}
