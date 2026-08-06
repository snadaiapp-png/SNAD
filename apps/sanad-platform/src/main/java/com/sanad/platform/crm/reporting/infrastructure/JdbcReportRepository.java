package com.sanad.platform.crm.reporting.infrastructure;

import com.sanad.platform.crm.reporting.domain.ReportRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * JDBC implementation of the ReportRepository port.
 * Provides tenant-isolated reporting queries.
 */
@Repository
public class JdbcReportRepository implements ReportRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcReportRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Map<String, Object>> getLeadCountsByStatus(UUID tenantId, Instant dateFrom, Instant dateTo) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("t", tenantId)
                .addValue("dateFrom", dateFrom)
                .addValue("dateTo", dateTo);
        return jdbc.queryForList(
                "SELECT status, COUNT(*) as count FROM crm_leads " +
                "WHERE tenant_id = :t AND created_at BETWEEN :dateFrom AND :dateTo " +
                "GROUP BY status ORDER BY status", params);
    }

    @Override
    public List<Map<String, Object>> getOpportunityCountsByStage(UUID tenantId, Instant dateFrom, Instant dateTo) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("t", tenantId)
                .addValue("dateFrom", dateFrom)
                .addValue("dateTo", dateTo);
        return jdbc.queryForList(
                "SELECT ps.name as stage_name, COUNT(*) as count, SUM(o.amount) as total_amount " +
                "FROM crm_opportunities o " +
                "JOIN crm_pipeline_stages ps ON o.tenant_id = ps.tenant_id AND o.stage_id = ps.id " +
                "WHERE o.tenant_id = :t AND o.created_at BETWEEN :dateFrom AND :dateTo " +
                "GROUP BY ps.name ORDER BY ps.sequence", params);
    }

    @Override
    public List<Map<String, Object>> getActivityCountsByType(UUID tenantId, Instant dateFrom, Instant dateTo) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("t", tenantId)
                .addValue("dateFrom", dateFrom)
                .addValue("dateTo", dateTo);
        return jdbc.queryForList(
                "SELECT activity_type, status, COUNT(*) as count " +
                "FROM crm_activities " +
                "WHERE tenant_id = :t AND created_at BETWEEN :dateFrom AND :dateTo " +
                "GROUP BY activity_type, status ORDER BY activity_type", params);
    }

    @Override
    public List<Map<String, Object>> getEmailEngagementMetrics(UUID tenantId, Instant dateFrom, Instant dateTo) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("t", tenantId)
                .addValue("dateFrom", dateFrom)
                .addValue("dateTo", dateTo);
        return jdbc.queryForList(
                "SELECT status, COUNT(*) as count " +
                "FROM crm_email_logs " +
                "WHERE tenant_id = :t AND sent_at BETWEEN :dateFrom AND :dateTo " +
                "GROUP BY status ORDER BY status", params);
    }

    @Override
    public List<Map<String, Object>> getConversionFunnel(UUID tenantId, Instant dateFrom, Instant dateTo) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("t", tenantId)
                .addValue("dateFrom", dateFrom)
                .addValue("dateTo", dateTo);
        return jdbc.queryForList(
                "SELECT " +
                "  (SELECT COUNT(*) FROM crm_leads WHERE tenant_id = :t AND created_at BETWEEN :dateFrom AND :dateTo) as total_leads, " +
                "  (SELECT COUNT(*) FROM crm_leads WHERE tenant_id = :t AND status = 'CONTACTED' AND created_at BETWEEN :dateFrom AND :dateTo) as contacted, " +
                "  (SELECT COUNT(*) FROM crm_leads WHERE tenant_id = :t AND status = 'QUALIFIED' AND created_at BETWEEN :dateFrom AND :dateTo) as qualified, " +
                "  (SELECT COUNT(*) FROM crm_leads WHERE tenant_id = :t AND status = 'CONVERTED' AND created_at BETWEEN :dateFrom AND :dateTo) as converted", params);
    }

    @Override
    public List<Map<String, Object>> getSalesForecast(UUID tenantId, Instant dateFrom, Instant dateTo) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("t", tenantId)
                .addValue("dateFrom", dateFrom)
                .addValue("dateTo", dateTo);
        return jdbc.queryForList(
                "SELECT " +
                "  ps.name as stage_name, " +
                "  COUNT(*) as opportunity_count, " +
                "  SUM(o.amount) as total_amount, " +
                "  AVG(o.probability) as avg_probability, " +
                "  SUM(o.amount * o.probability / 100) as weighted_amount " +
                "FROM crm_opportunities o " +
                "JOIN crm_pipeline_stages ps ON o.tenant_id = ps.tenant_id AND o.stage_id = ps.id " +
                "WHERE o.tenant_id = :t AND o.status = 'OPEN' " +
                "AND o.expected_close_date BETWEEN :dateFrom::date AND :dateTo::date " +
                "GROUP BY ps.name ORDER BY ps.sequence", params);
    }

    @Override
    public Map<String, Object> getSummaryStats(UUID tenantId, Instant dateFrom, Instant dateTo) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("t", tenantId)
                .addValue("dateFrom", dateFrom)
                .addValue("dateTo", dateTo);
        return jdbc.queryForMap(
                "SELECT " +
                "  (SELECT COUNT(*) FROM crm_accounts WHERE tenant_id = :t) as total_accounts, " +
                "  (SELECT COUNT(*) FROM crm_contacts WHERE tenant_id = :t) as total_contacts, " +
                "  (SELECT COUNT(*) FROM crm_leads WHERE tenant_id = :t) as total_leads, " +
                "  (SELECT COUNT(*) FROM crm_opportunities WHERE tenant_id = :t AND status = 'OPEN') as open_opportunities, " +
                "  (SELECT COALESCE(SUM(amount), 0) FROM crm_opportunities WHERE tenant_id = :t AND status = 'OPEN') as pipeline_value, " +
                "  (SELECT COUNT(*) FROM crm_activities WHERE tenant_id = :t AND status IN ('OPEN', 'IN_PROGRESS')) as pending_activities", params);
    }
}
