package com.sanad.platform.analytics.infrastructure;

import com.sanad.platform.analytics.domain.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcAnalyticsReportRepository implements AnalyticsReportRepository {
    private final JdbcTemplate jdbc;
    public JdbcAnalyticsReportRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    private static final RowMapper<AnalyticsReport> MAPPER = (rs, rowNum) -> new AnalyticsReport(
            rs.getObject("id", UUID.class), rs.getObject("tenant_id", UUID.class),
            rs.getString("code"), rs.getString("name"), rs.getString("description"),
            AnalyticsReport.ReportType.valueOf(rs.getString("report_type")),
            rs.getObject("data_source_id", UUID.class), rs.getString("query_text"),
            rs.getString("parameters"), rs.getString("schedule_cron"),
            AnalyticsReport.OutputFormat.valueOf(rs.getString("output_format")),
            AnalyticsReport.Status.valueOf(rs.getString("status")),
            rs.getTimestamp("last_executed_at") != null ? rs.getTimestamp("last_executed_at").toInstant() : null,
            rs.getString("last_execution_status"),
            rs.getObject("created_by", UUID.class), rs.getLong("version"),
            rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant()
    );

    @Override
    public AnalyticsReport save(AnalyticsReport r) {
        jdbc.update("""
            INSERT INTO analytics_reports (id, tenant_id, code, name, description, report_type, data_source_id,
                query_text, parameters, schedule_cron, output_format, status, last_executed_at,
                last_execution_status, created_by, version, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (id) DO UPDATE SET name=EXCLUDED.name, description=EXCLUDED.description,
                report_type=EXCLUDED.report_type, data_source_id=EXCLUDED.data_source_id,
                query_text=EXCLUDED.query_text, parameters=EXCLUDED.parameters,
                schedule_cron=EXCLUDED.schedule_cron, output_format=EXCLUDED.output_format,
                status=EXCLUDED.status, last_executed_at=EXCLUDED.last_executed_at,
                last_execution_status=EXCLUDED.last_execution_status, version=EXCLUDED.version, updated_at=EXCLUDED.updated_at
            """, r.id(), r.tenantId(), r.code(), r.name(), r.description(), r.reportType().name(),
            r.dataSourceId(), r.queryText(), r.parameters(), r.scheduleCron(),
            r.outputFormat().name(), r.status().name(),
            r.lastExecutedAt() != null ? Timestamp.from(r.lastExecutedAt()) : null,
            r.lastExecutionStatus(), r.createdBy(), r.version(),
            Timestamp.from(r.createdAt()), Timestamp.from(r.updatedAt()));
        return r;
    }
    @Override public Optional<AnalyticsReport> findById(UUID t, UUID i) { return jdbc.query("SELECT * FROM analytics_reports WHERE tenant_id=? AND id=?", MAPPER, t, i).stream().findFirst(); }
    @Override public Optional<AnalyticsReport> findByCode(UUID t, String c) { return jdbc.query("SELECT * FROM analytics_reports WHERE tenant_id=? AND code=?", MAPPER, t, c).stream().findFirst(); }
    @Override public List<AnalyticsReport> findByTenant(UUID t, int l) { return jdbc.query("SELECT * FROM analytics_reports WHERE tenant_id=? ORDER BY created_at DESC LIMIT ?", MAPPER, t, Math.max(1,Math.min(l,1000))); }
    @Override public List<AnalyticsReport> findByTenantAndStatus(UUID t, AnalyticsReport.Status s, int l) { return jdbc.query("SELECT * FROM analytics_reports WHERE tenant_id=? AND status=? ORDER BY created_at DESC LIMIT ?", MAPPER, t, s.name(), Math.max(1,Math.min(l,1000))); }
}
