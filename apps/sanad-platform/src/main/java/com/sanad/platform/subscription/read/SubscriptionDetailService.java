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
                            SELECT s.id, s.tenant_id AS "tenantId", t.name AS "tenantName", t.subdomain AS "tenantCode",
                                   t.country_code AS "countryCode", s.status, s.billing_cycle AS "billingCycle", s.seat_quantity AS "seatQuantity",
                                   s.plan_id AS "planId", p.name AS "planName", p.code AS "planCode",
                                   pv.version_number AS "planVersion",
                                   s.credit_balance_minor AS "creditBalanceMinor", p.currency_code AS "currencyCode",
                                   s.started_at AS "startedAt", s.trial_ends_at AS "trialEndsAt",
                                   s.current_period_start AS "currentPeriodStart", s.current_period_end AS "currentPeriodEnd",
                                   s.cancel_at_period_end AS "cancelAtPeriodEnd", s.cancelled_at AS "cancelledAt", s.created_at AS "createdAt"
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
                                SELECT id, item_type AS "itemType", name_snapshot AS "nameSnapshot", quantity,
                                       unit_amount_minor AS "unitAmountMinor",
                                       currency_code AS "currencyCode", status, plan_version_id AS "planVersionId", created_at AS "createdAt"
                                FROM subscription_items WHERE subscription_id = ?
                                ORDER BY created_at, id
                                """, subscriptionId),
                jdbc.queryForList("""
                                SELECT id, invoice_number AS "invoiceNumber", status, currency_code AS "currencyCode",
                                       subtotal_minor AS "subtotalMinor",
                                       tax_minor AS "taxMinor", total_minor AS "totalMinor", amount_paid_minor AS "amountPaidMinor",
                                       period_start AS "periodStart", period_end AS "periodEnd", due_at AS "dueAt", paid_at AS "paidAt"
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
                        (rs, n) -> {
                            // R0C-4 defect B: legacy EVENT rows carry literal NULL
                            // from/to statuses (and reason is nullable); Map.of
                            // rejects null values and NPEd the whole timeline.
                            Map<String, Object> row = new java.util.LinkedHashMap<>();
                            row.put("source", rs.getString("source"));
                            row.put("action", rs.getString("action"));
                            row.put("fromStatus", rs.getObject("from_status"));
                            row.put("toStatus", rs.getObject("to_status"));
                            row.put("reason", rs.getString("reason"));
                            row.put("createdAt", rs.getObject("created_at"));
                            return row;
                        },
                        subscriptionId, subscriptionId),
                jdbc.queryForList("""
                                SELECT id, action, status, attempts, started_at AS "startedAt", completed_at AS "completedAt",
                                       error_code AS "errorCode", error_message AS "errorMessage", created_at AS "createdAt"
                                FROM provisioning_jobs WHERE subscription_id = ?
                                ORDER BY created_at DESC LIMIT 50
                                """, subscriptionId),
                jdbc.queryForList("""
                                SELECT id, action, resource_type AS "resourceType", resource_id AS "resourceId", reason, result, created_at AS "createdAt"
                                FROM platform_audit_logs
                                WHERE (resource_type = 'subscription' AND resource_id = ?::text)
                                   OR (resource_type = 'subscription_item' AND resource_id IN (
                                        SELECT id::text FROM subscription_items WHERE subscription_id = ?))
                                ORDER BY created_at DESC LIMIT 100
                                """, subscriptionId, subscriptionId));
    }
}
