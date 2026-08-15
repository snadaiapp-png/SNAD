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
public class JdbcAnalyticsDataSourceRepository implements AnalyticsDataSourceRepository {
    private final JdbcTemplate jdbc;
    public JdbcAnalyticsDataSourceRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    private static final RowMapper<AnalyticsDataSource> MAPPER = (rs, rowNum) -> new AnalyticsDataSource(
            rs.getObject("id", UUID.class), rs.getObject("tenant_id", UUID.class),
            rs.getString("code"), rs.getString("name"), rs.getString("description"),
            AnalyticsDataSource.SourceType.valueOf(rs.getString("source_type")),
            rs.getString("module"), rs.getString("configuration"),
            AnalyticsDataSource.Status.valueOf(rs.getString("status")),
            rs.getTimestamp("last_tested_at") != null ? rs.getTimestamp("last_tested_at").toInstant() : null,
            rs.getString("last_test_status"),
            rs.getObject("created_by", UUID.class), rs.getLong("version"),
            rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant()
    );

    @Override
    public AnalyticsDataSource save(AnalyticsDataSource ds) {
        jdbc.update("""
            INSERT INTO analytics_data_sources (id, tenant_id, code, name, description, source_type,
                module, configuration, status, last_tested_at, last_test_status, created_by, version, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (id) DO UPDATE SET name=EXCLUDED.name, description=EXCLUDED.description,
                source_type=EXCLUDED.source_type, module=EXCLUDED.module, configuration=EXCLUDED.configuration,
                status=EXCLUDED.status, last_tested_at=EXCLUDED.last_tested_at,
                last_test_status=EXCLUDED.last_test_status, version=EXCLUDED.version, updated_at=EXCLUDED.updated_at
            """, ds.id(), ds.tenantId(), ds.code(), ds.name(), ds.description(),
            ds.sourceType().name(), ds.module(), ds.configuration(), ds.status().name(),
            ds.lastTestedAt() != null ? Timestamp.from(ds.lastTestedAt()) : null,
            ds.lastTestStatus(), ds.createdBy(), ds.version(),
            Timestamp.from(ds.createdAt()), Timestamp.from(ds.updatedAt()));
        return ds;
    }
    @Override public Optional<AnalyticsDataSource> findById(UUID t, UUID i) { return jdbc.query("SELECT * FROM analytics_data_sources WHERE tenant_id=? AND id=?", MAPPER, t, i).stream().findFirst(); }
    @Override public Optional<AnalyticsDataSource> findByCode(UUID t, String c) { return jdbc.query("SELECT * FROM analytics_data_sources WHERE tenant_id=? AND code=?", MAPPER, t, c).stream().findFirst(); }
    @Override public List<AnalyticsDataSource> findByTenant(UUID t, int l) { return jdbc.query("SELECT * FROM analytics_data_sources WHERE tenant_id=? ORDER BY created_at DESC LIMIT ?", MAPPER, t, Math.max(1,Math.min(l,1000))); }
    @Override public List<AnalyticsDataSource> findByTenantAndStatus(UUID t, AnalyticsDataSource.Status s, int l) { return jdbc.query("SELECT * FROM analytics_data_sources WHERE tenant_id=? AND status=? ORDER BY created_at DESC LIMIT ?", MAPPER, t, s.name(), Math.max(1,Math.min(l,1000))); }
}
