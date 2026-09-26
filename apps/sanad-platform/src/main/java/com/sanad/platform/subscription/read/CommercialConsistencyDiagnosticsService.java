package com.sanad.platform.subscription.read;

import com.sanad.platform.security.rls.TenantRlsTransactionContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Read-only diagnostics for commercial convergence across tenant, subscription,
 * dunning, billing projection and Finance/reconciliation state.
 *
 * <p>This service intentionally performs SELECT-only diagnostics. It never
 * repairs production data and never guesses a plan, subscription or billing
 * state. Returned evidence is intended for operator review/remediation.</p>
 *
 * <p>R0C13 billing/reconciliation tables are protected by FORCE RLS. A global
 * control-plane scan therefore enumerates tenant ids from the tenant directory,
 * then deliberately re-scopes the current read-only transaction to each trusted
 * tenant id before evaluating that tenant's diagnostics. RLS remains enabled and
 * no BYPASSRLS/superuser path is used.</p>
 */
@Service
public class CommercialConsistencyDiagnosticsService {

    private final JdbcTemplate jdbc;
    private final TenantRlsTransactionContext tenantRlsContext;

    public CommercialConsistencyDiagnosticsService(
            JdbcTemplate jdbc,
            TenantRlsTransactionContext tenantRlsContext
    ) {
        this.jdbc = jdbc;
        this.tenantRlsContext = tenantRlsContext;
    }

    public record CommercialAnomaly(
            String code,
            UUID tenantId,
            UUID subscriptionId,
            UUID invoiceId,
            String evidence
    ) {
    }

    @Transactional(readOnly = true)
    public List<CommercialAnomaly> scan() {
        List<UUID> tenantIds = jdbc.queryForList(
                "SELECT id FROM tenants ORDER BY id",
                UUID.class);
        List<CommercialAnomaly> anomalies = new ArrayList<>();

        for (UUID tenantId : tenantIds) {
            // This is transaction-local (set_config(..., true)); changing the
            // value for the next trusted tenant cannot escape this transaction.
            tenantRlsContext.applyForCurrentTransaction(tenantId);
            anomalies.addAll(scanCurrentTenant());
        }

        anomalies.sort(java.util.Comparator
                .comparing(CommercialAnomaly::code)
                .thenComparing(CommercialAnomaly::tenantId)
                .thenComparing(CommercialAnomaly::subscriptionId,
                        java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder()))
                .thenComparing(CommercialAnomaly::invoiceId,
                        java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())));
        return List.copyOf(anomalies);
    }

    private List<CommercialAnomaly> scanCurrentTenant() {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                WITH scope AS (
                    SELECT NULLIF(current_setting('app.tenant_id', true), '')::uuid AS tenant_id
                ), effective AS (
                    SELECT s.tenant_id,
                           COUNT(*) FILTER (WHERE s.status NOT IN ('CANCELLED','EXPIRED','TERMINATED')) AS effective_count,
                           MAX(s.id::text) FILTER (WHERE s.status NOT IN ('CANCELLED','EXPIRED','TERMINATED'))::uuid AS effective_subscription_id,
                           COUNT(*) FILTER (WHERE s.status IN ('CANCELLED','EXPIRED','TERMINATED')) AS terminal_count
                    FROM tenant_subscriptions s
                    JOIN scope sc ON sc.tenant_id = s.tenant_id
                    GROUP BY s.tenant_id
                ), anomalies AS (
                    SELECT 'ACTIVE_WITHOUT_EFFECTIVE_SUBSCRIPTION'::text AS code,
                           t.id AS tenant_id,
                           NULL::uuid AS subscription_id,
                           NULL::uuid AS invoice_id,
                           ('tenant=' || t.status || ',effective=0')::text AS evidence
                    FROM tenants t
                    JOIN scope sc ON sc.tenant_id = t.id
                    LEFT JOIN effective e ON e.tenant_id = t.id
                    WHERE t.status = 'ACTIVE' AND COALESCE(e.effective_count, 0) = 0

                    UNION ALL

                    SELECT 'ACTIVE_WITH_TERMINAL_HISTORY_ONLY',
                           t.id,
                           NULL::uuid,
                           NULL::uuid,
                           ('tenant=ACTIVE,terminalHistory=' || COALESCE(e.terminal_count, 0))::text
                    FROM tenants t
                    JOIN scope sc ON sc.tenant_id = t.id
                    JOIN effective e ON e.tenant_id = t.id
                    WHERE t.status = 'ACTIVE'
                      AND e.effective_count = 0
                      AND e.terminal_count > 0

                    UNION ALL

                    SELECT 'MULTIPLE_EFFECTIVE_SUBSCRIPTIONS',
                           e.tenant_id,
                           e.effective_subscription_id,
                           NULL::uuid,
                           ('effectiveCount=' || e.effective_count)::text
                    FROM effective e
                    WHERE e.effective_count > 1

                    UNION ALL

                    SELECT 'DUNNING_STATE_MISMATCH',
                           s.tenant_id,
                           s.id,
                           NULL::uuid,
                           ('subscription=' || s.status || ',billing=' || COALESCE(s.billing_state, '<null>'))::text
                    FROM tenant_subscriptions s
                    JOIN scope sc ON sc.tenant_id = s.tenant_id
                    WHERE s.status NOT IN ('CANCELLED','EXPIRED','TERMINATED')
                      AND ((s.status IN ('PAST_DUE','GRACE_PERIOD') AND COALESCE(s.billing_state,'') NOT IN ('PAST_DUE','SUSPENDED'))
                        OR (s.status = 'SUSPENDED' AND COALESCE(s.billing_state,'') <> 'SUSPENDED')
                        OR (s.status IN ('ACTIVE','TRIAL','TRIALING') AND COALESCE(s.billing_state,'') = 'SUSPENDED'))

                    UNION ALL

                    SELECT 'FINANCE_LINK_MISSING',
                           bi.tenant_id,
                           bi.subscription_id,
                           bi.id,
                           ('invoice=' || bi.invoice_number || ',financeLink=missing')::text
                    FROM billing_invoices bi
                    JOIN scope sc ON sc.tenant_id = bi.tenant_id
                    LEFT JOIN subscription_billing_finance_links fl
                      ON fl.tenant_id = bi.tenant_id AND fl.billing_invoice_id = bi.id
                    WHERE fl.id IS NULL

                    UNION ALL

                    SELECT 'SETTLEMENT_RECONCILIATION_MISMATCH',
                           ri.tenant_id,
                           fl.subscription_id,
                           ri.billing_invoice_id,
                           ('classification=' || ri.classification || ',state=' || ri.state)::text
                    FROM subscription_billing_reconciliation_items ri
                    JOIN scope sc ON sc.tenant_id = ri.tenant_id
                    LEFT JOIN subscription_billing_finance_links fl
                      ON fl.tenant_id = ri.tenant_id AND fl.id = ri.finance_link_id
                    WHERE ri.classification <> 'MATCHED' AND ri.state = 'OPEN'

                    UNION ALL

                    SELECT 'CURRENCY_MISMATCH',
                           ri.tenant_id,
                           fl.subscription_id,
                           ri.billing_invoice_id,
                           ('expected=' || COALESCE(ri.expected_currency,'<null>') || ',observed=' || COALESCE(ri.observed_currency,'<null>'))::text
                    FROM subscription_billing_reconciliation_items ri
                    JOIN scope sc ON sc.tenant_id = ri.tenant_id
                    LEFT JOIN subscription_billing_finance_links fl
                      ON fl.tenant_id = ri.tenant_id AND fl.id = ri.finance_link_id
                    WHERE ri.classification = 'CURRENCY_MISMATCH' AND ri.state = 'OPEN'

                    UNION ALL

                    SELECT 'INVALID_EFFECTIVE_PLAN_REFERENCE',
                           s.tenant_id,
                           s.id,
                           NULL::uuid,
                           ('planId=' || s.plan_id || ',planVersionId=' || COALESCE(s.plan_version_id::text,'<null>'))::text
                    FROM tenant_subscriptions s
                    JOIN scope sc ON sc.tenant_id = s.tenant_id
                    LEFT JOIN saas_plans p ON p.id = s.plan_id
                    LEFT JOIN plan_versions pv ON pv.id = s.plan_version_id AND pv.plan_id = s.plan_id
                    WHERE s.status NOT IN ('CANCELLED','EXPIRED','TERMINATED')
                      AND (p.id IS NULL OR s.plan_version_id IS NULL OR pv.id IS NULL)
                )
                SELECT code, tenant_id, subscription_id, invoice_id, evidence
                FROM anomalies
                """);

        return rows.stream().map(this::map).toList();
    }

    private CommercialAnomaly map(Map<String, Object> row) {
        return new CommercialAnomaly(
                string(row.get("code")),
                uuid(row.get("tenant_id")),
                uuid(row.get("subscription_id")),
                uuid(row.get("invoice_id")),
                string(row.get("evidence")));
    }

    private static String string(Object value) {
        return value == null ? null : value.toString();
    }

    private static UUID uuid(Object value) {
        if (value == null) return null;
        if (value instanceof UUID id) return id;
        return UUID.fromString(value.toString());
    }
}
