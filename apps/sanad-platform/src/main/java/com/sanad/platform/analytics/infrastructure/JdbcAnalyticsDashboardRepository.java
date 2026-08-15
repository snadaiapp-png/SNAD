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
public class JdbcAnalyticsDashboardRepository implements AnalyticsDashboardRepository {
    private final JdbcTemplate jdbc;
    public JdbcAnalyticsDashboardRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    private static final RowMapper<AnalyticsDashboard> MAPPER = (rs, rowNum) -> new AnalyticsDashboard(
            rs.getObject("id", UUID.class), rs.getObject("tenant_id", UUID.class),
            rs.getString("code"), rs.getString("name"), rs.getString("description"),
            AnalyticsDashboard.DashboardType.valueOf(rs.getString("dashboard_type")),
            rs.getString("configuration"), AnalyticsDashboard.Status.valueOf(rs.getString("status")),
            rs.getObject("created_by", UUID.class), rs.getLong("version_lock"), rs.getLong("version"),
            rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant()
    );

    @Override
    public AnalyticsDashboard save(AnalyticsDashboard d) {
        jdbc.update("""
            INSERT INTO analytics_dashboards (id, tenant_id, code, name, description, dashboard_type,
                configuration, status, created_by, version_lock, version, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, CAST(? AS jsonb), ?, ?, ?, ?, ?, ?)
            ON CONFLICT (id) DO UPDATE SET name=EXCLUDED.name, description=EXCLUDED.description,
                dashboard_type=EXCLUDED.dashboard_type, configuration=EXCLUDED.configuration,
                status=EXCLUDED.status, version_lock=EXCLUDED.version_lock, version=EXCLUDED.version, updated_at=EXCLUDED.updated_at
            """, d.id(), d.tenantId(), d.code(), d.name(), d.description(), d.dashboardType().name(),
            d.configuration(), d.status().name(), d.createdBy(), d.versionLock(), d.version(),
            Timestamp.from(d.createdAt()), Timestamp.from(d.updatedAt()));
        return d;
    }
    @Override public Optional<AnalyticsDashboard> findById(UUID t, UUID i) { return jdbc.query("SELECT * FROM analytics_dashboards WHERE tenant_id=? AND id=?", MAPPER, t, i).stream().findFirst(); }
    @Override public Optional<AnalyticsDashboard> findByCode(UUID t, String c) { return jdbc.query("SELECT * FROM analytics_dashboards WHERE tenant_id=? AND code=?", MAPPER, t, c).stream().findFirst(); }
    @Override public List<AnalyticsDashboard> findByTenant(UUID t, int l) { return jdbc.query("SELECT * FROM analytics_dashboards WHERE tenant_id=? ORDER BY created_at DESC LIMIT ?", MAPPER, t, Math.max(1,Math.min(l,1000))); }
    @Override public List<AnalyticsDashboard> findByTenantAndStatus(UUID t, AnalyticsDashboard.Status s, int l) { return jdbc.query("SELECT * FROM analytics_dashboards WHERE tenant_id=? AND status=? ORDER BY created_at DESC LIMIT ?", MAPPER, t, s.name(), Math.max(1,Math.min(l,1000))); }
}
