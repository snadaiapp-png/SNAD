package com.sanad.platform.crm.reports.infrastructure;

import com.sanad.platform.crm.reports.domain.ReportsRepository;
import com.sanad.platform.crm.reports.domain.ReportsRepository.*;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * JDBC implementation of {@link ReportsRepository}.
 * <p>
 * All queries are tenant-scoped and read-only. Uses aggregate SQL
 * (COUNT, SUM, AVG, GROUP BY) for efficient reporting.
 * <p>
 * Branch: feature/crm-reports
 */
@Repository
public class JdbcReportsRepository implements ReportsRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcReportsRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public SalesPipelineReport getSalesPipelineReport(UUID tenantId) {
        Map<String, Object> params = Map.of("t", tenantId);

        // Get stage-level summaries
        List<StageSummary> stages = jdbc.queryForList(
                "SELECT s.name AS stage_name, s.id AS stage_id, " +
                "  COUNT(o.id) AS opp_count, " +
                "  COALESCE(SUM(o.amount), 0) AS total_amount, " +
                "  COALESCE(AVG(o.probability), 0) AS avg_prob " +
                "FROM crm_pipeline_stages s " +
                "LEFT JOIN crm_opportunities o ON o.stage_id = s.id AND o.tenant_id = :t " +
                "  AND o.status NOT IN ('CLOSED_LOST', 'CANCELLED') " +
                "WHERE s.tenant_id = :t " +
                "GROUP BY s.id, s.name, s.sequence " +
                "ORDER BY s.sequence ASC",
                params)
                .stream()
                .map(row -> new StageSummary(
                        (String) row.get("stage_name"),
                        (UUID) row.get("stage_id"),
                        ((Number) row.get("opp_count")).intValue(),
                        toBigDecimal(row.get("total_amount")),
                        toBigDecimal(row.get("avg_prob"))))
                .toList();

        // Totals
        Map<String, Object> totals = jdbc.queryForMap(
                "SELECT COUNT(*) AS cnt, COALESCE(SUM(amount), 0) AS total, COALESCE(SUM(amount * probability / 100.0), 0) AS weighted " +
                "FROM crm_opportunities WHERE tenant_id = :t AND status NOT IN ('CLOSED_LOST', 'CANCELLED')",
                params);

        return new SalesPipelineReport(
                stages,
                toBigDecimal(totals.get("total")),
                ((Number) totals.get("cnt")).intValue(),
                toBigDecimal(totals.get("weighted")));
    }

    @Override
    public LeadConversionReport getLeadConversionReport(UUID tenantId) {
        Map<String, Object> params = Map.of("t", tenantId);

        Map<String, Object> totals = jdbc.queryForMap(
                "SELECT " +
                "  COUNT(*) AS total, " +
                "  SUM(CASE WHEN status='CONVERTED' THEN 1 ELSE 0 END) AS converted, " +
                "  SUM(CASE WHEN status='QUALIFIED' THEN 1 ELSE 0 END) AS qualified, " +
                "  SUM(CASE WHEN status='DISQUALIFIED' THEN 1 ELSE 0 END) AS disqualified, " +
                "  SUM(CASE WHEN status='NEW' THEN 1 ELSE 0 END) AS new_leads " +
                "FROM crm_leads WHERE tenant_id = :t",
                params);

        int totalLeads = ((Number) totals.get("total")).intValue();
        int convertedLeads = ((Number) totals.get("converted")).intValue();
        double conversionRate = totalLeads > 0 ? (convertedLeads * 100.0 / totalLeads) : 0.0;

        // By source
        List<LeadSourceSummary> bySource = jdbc.queryForList(
                "SELECT COALESCE(source, 'Unknown') AS source, " +
                "  COUNT(*) AS cnt, " +
                "  SUM(CASE WHEN status='CONVERTED' THEN 1 ELSE 0 END) AS converted " +
                "FROM crm_leads WHERE tenant_id = :t " +
                "GROUP BY COALESCE(source, 'Unknown') " +
                "ORDER BY cnt DESC",
                params)
                .stream()
                .map(row -> new LeadSourceSummary(
                        (String) row.get("source"),
                        ((Number) row.get("cnt")).intValue(),
                        ((Number) row.get("converted")).intValue()))
                .toList();

        return new LeadConversionReport(
                totalLeads,
                convertedLeads,
                ((Number) totals.get("qualified")).intValue(),
                ((Number) totals.get("disqualified")).intValue(),
                ((Number) totals.get("new_leads")).intValue(),
                conversionRate,
                bySource);
    }

    @Override
    public ActivitySummaryReport getActivitySummaryReport(UUID tenantId) {
        Map<String, Object> params = Map.of("t", tenantId);

        // Activities
        Map<String, Object> actTotals = jdbc.queryForMap(
                "SELECT COUNT(*) AS total, " +
                "  SUM(CASE WHEN status IN ('OPEN','IN_PROGRESS') THEN 1 ELSE 0 END) AS open_cnt, " +
                "  SUM(CASE WHEN status='COMPLETED' THEN 1 ELSE 0 END) AS completed " +
                "FROM crm_activities WHERE tenant_id = :t",
                params);

        // Tasks
        Map<String, Object> taskTotals = jdbc.queryForMap(
                "SELECT COUNT(*) AS total, " +
                "  SUM(CASE WHEN status IN ('OPEN','IN_PROGRESS') THEN 1 ELSE 0 END) AS open_cnt, " +
                "  SUM(CASE WHEN status='COMPLETED' THEN 1 ELSE 0 END) AS completed " +
                "FROM crm_tasks WHERE tenant_id = :t",
                params);

        // Activities by type
        List<ActivityTypeBreakdown> byType = jdbc.queryForList(
                "SELECT activity_type, COUNT(*) AS cnt, " +
                "  SUM(CASE WHEN status IN ('OPEN','IN_PROGRESS') THEN 1 ELSE 0 END) AS open_cnt " +
                "FROM crm_activities WHERE tenant_id = :t " +
                "GROUP BY activity_type ORDER BY cnt DESC",
                params)
                .stream()
                .map(row -> new ActivityTypeBreakdown(
                        (String) row.get("activity_type"),
                        ((Number) row.get("cnt")).intValue(),
                        ((Number) row.get("open_cnt")).intValue()))
                .toList();

        return new ActivitySummaryReport(
                ((Number) actTotals.get("total")).intValue(),
                ((Number) actTotals.get("open_cnt")).intValue(),
                ((Number) actTotals.get("completed")).intValue(),
                ((Number) taskTotals.get("total")).intValue(),
                ((Number) taskTotals.get("open_cnt")).intValue(),
                ((Number) taskTotals.get("completed")).intValue(),
                byType);
    }

    @Override
    public AccountGrowthReport getAccountGrowthReport(UUID tenantId) {
        Map<String, Object> params = Map.of("t", tenantId);

        Map<String, Object> totals = jdbc.queryForMap(
                "SELECT COUNT(*) AS total, " +
                "  SUM(CASE WHEN lifecycle_status='ACTIVE' THEN 1 ELSE 0 END) AS active, " +
                "  SUM(CASE WHEN created_at >= DATE_TRUNC('month', CURRENT_DATE) THEN 1 ELSE 0 END) AS new_month, " +
                "  SUM(CASE WHEN created_at >= DATE_TRUNC('quarter', CURRENT_DATE) THEN 1 ELSE 0 END) AS new_quarter " +
                "FROM crm_accounts WHERE tenant_id = :t",
                params);

        // Monthly growth (last 6 months)
        List<MonthlyGrowth> monthlyGrowth = jdbc.queryForList(
                "SELECT TO_CHAR(DATE_TRUNC('month', created_at), 'YYYY-MM') AS month, " +
                "  COUNT(*) AS new_accounts " +
                "FROM crm_accounts WHERE tenant_id = :t " +
                "  AND created_at >= DATE_TRUNC('month', CURRENT_DATE) - INTERVAL '5 months' " +
                "GROUP BY DATE_TRUNC('month', created_at) " +
                "ORDER BY month DESC",
                params)
                .stream()
                .map(row -> new MonthlyGrowth(
                        (String) row.get("month"),
                        ((Number) row.get("new_accounts")).intValue(),
                        0)) // cumulative computed below
                .toList();

        // Compute cumulative
        int cumulative = ((Number) totals.get("total")).intValue();
        List<MonthlyGrowth> withCumulative = new java.util.ArrayList<>();
        for (MonthlyGrowth m : monthlyGrowth) {
            cumulative -= m.newAccounts();
            withCumulative.add(new MonthlyGrowth(m.month(), m.newAccounts(), cumulative + m.newAccounts()));
        }

        return new AccountGrowthReport(
                ((Number) totals.get("total")).intValue(),
                ((Number) totals.get("active")).intValue(),
                ((Number) totals.get("new_month")).intValue(),
                ((Number) totals.get("new_quarter")).intValue(),
                withCumulative);
    }

    private static BigDecimal toBigDecimal(Object v) {
        if (v == null) return BigDecimal.ZERO;
        if (v instanceof BigDecimal bd) return bd;
        if (v instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        try { return new BigDecimal(String.valueOf(v)); } catch (Exception e) { return BigDecimal.ZERO; }
    }
}
