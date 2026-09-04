package com.sanad.platform.subscription.read;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Overview dashboard read model. Computed server-side from SQL aggregates —
 * the UI never derives business metrics from client-side arrays.
 *
 * <p>Metrics that cannot be computed reliably from current data (churn,
 * expansion revenue — no historical snapshots exist) are reported as null;
 * the UI renders them as N/A instead of inventing values.
 */
@Service
public class ExecutiveOverviewService {

    private final JdbcTemplate jdbc;

    public ExecutiveOverviewService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record Overview(
            long totalTenants,
            long activeSubscriptions,
            long trials,
            long pastDue,
            long renewalsNext30Days,
            Map<String, Long> mrrMinorByCurrency,
            Map<String, Long> arrMinorByCurrency,
            Long churnPercent,
            Long expansionRevenueMinor,
            OffsetDateTime generatedAt) {
    }

    @Transactional(readOnly = true)
    public Overview overview() {
        long totalTenants = count("SELECT COUNT(*) FROM tenants");
        long active = count(
                "SELECT COUNT(*) FROM tenant_subscriptions WHERE status = 'ACTIVE'");
        long trials = count(
                "SELECT COUNT(*) FROM tenant_subscriptions WHERE status IN ('TRIAL', 'TRIALING')");
        long pastDue = count(
                "SELECT COUNT(*) FROM tenant_subscriptions WHERE status = 'PAST_DUE'");
        long renewals = count("""
                        SELECT COUNT(*) FROM tenant_subscriptions
                        WHERE status IN ('ACTIVE', 'TRIAL', 'TRIALING')
                          AND current_period_end BETWEEN NOW() AND NOW() + INTERVAL '30 days'
                        """);

        // MRR per currency: monthly price for MONTHLY cycles, annual/12 for ANNUAL.
        // Derived from plan_versions (versioned contracts); money = minor units.
        List<Map<String, Object>> mrrRows = jdbc.queryForList("""
                        SELECT p.currency_code,
                               COALESCE(SUM(
                                   CASE s.billing_cycle
                                       WHEN 'ANNUAL' THEN pv.annual_price_minor / 12
                                       ELSE pv.monthly_price_minor
                                   END), 0) AS mrr_minor
                        FROM tenant_subscriptions s
                        JOIN plan_versions pv ON pv.id = COALESCE(s.plan_version_id, (
                                SELECT pv2.id FROM plan_versions pv2
                                WHERE pv2.plan_id = s.plan_id AND pv2.status = 'ACTIVE'
                                ORDER BY pv2.version_number DESC LIMIT 1))
                        JOIN saas_plans p ON p.id = s.plan_id
                        WHERE s.status IN ('ACTIVE', 'PAST_DUE', 'GRACE_PERIOD')
                        GROUP BY p.currency_code
                        """);

        Map<String, Long> mrrByCurrency = new java.util.LinkedHashMap<>();
        Map<String, Long> arrByCurrency = new java.util.LinkedHashMap<>();
        for (Map<String, Object> row : mrrRows) {
            String currency = String.valueOf(row.get("currency_code"));
            long mrr = ((Number) row.get("mrr_minor")).longValue();
            mrrByCurrency.put(currency, mrr);
            arrByCurrency.put(currency, mrr * 12);
        }

        return new Overview(totalTenants, active, trials, pastDue, renewals,
                mrrByCurrency, arrByCurrency,
                null, null, OffsetDateTime.now());
    }

    private long count(String sql) {
        Long result = jdbc.queryForObject(sql, Long.class);
        return result != null ? result : 0;
    }
}
