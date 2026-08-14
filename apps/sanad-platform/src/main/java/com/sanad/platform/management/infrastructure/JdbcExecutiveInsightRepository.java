package com.sanad.platform.management.infrastructure;

import com.sanad.platform.management.domain.ExecutiveInsight;
import com.sanad.platform.management.domain.ExecutiveInsightRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcExecutiveInsightRepository implements ExecutiveInsightRepository {

    private final JdbcTemplate jdbc;

    public JdbcExecutiveInsightRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final RowMapper<ExecutiveInsight> MAPPER = (rs, rowNum) -> new ExecutiveInsight(
            rs.getObject("id", UUID.class),
            rs.getObject("tenant_id", UUID.class),
            ExecutiveInsight.InsightType.valueOf(rs.getString("type")),
            rs.getString("title"),
            rs.getString("description"),
            rs.getBigDecimal("confidence"),
            rs.getString("evidence"),
            rs.getString("model_name"),
            rs.getString("model_version"),
            rs.getBoolean("advisory"),
            ExecutiveInsight.InsightStatus.valueOf(rs.getString("status")),
            rs.getObject("generated_by", UUID.class),
            rs.getTimestamp("created_at").toInstant()
    );

    @Override
    public ExecutiveInsight save(ExecutiveInsight i) {
        jdbc.update("""
                INSERT INTO executive_insights
                    (id, tenant_id, type, title, description, confidence, evidence,
                     model_name, model_version, advisory, status, generated_by, created_at)
                VALUES (?, ?, ?, ?, ?, ?, CAST(? AS jsonb), ?, ?, ?, ?, ?, ?)
                """,
                i.id(), i.tenantId(), i.type().name(), i.title(), i.description(),
                i.confidence(), i.evidence(),
                i.modelName(), i.modelVersion(), i.advisory(),
                i.status().name(), i.generatedBy(),
                Timestamp.from(i.createdAt())
        );
        return i;
    }

    @Override
    public Optional<ExecutiveInsight> findById(UUID tenantId, UUID id) {
        return jdbc.query("SELECT * FROM executive_insights WHERE tenant_id = ? AND id = ?",
                MAPPER, tenantId, id).stream().findFirst();
    }

    @Override
    public List<ExecutiveInsight> findByTenant(UUID tenantId, int limit) {
        return jdbc.query("SELECT * FROM executive_insights WHERE tenant_id = ? ORDER BY created_at DESC LIMIT ?",
                MAPPER, tenantId, limit);
    }

    @Override
    public List<ExecutiveInsight> findByTenantAndStatus(UUID tenantId, ExecutiveInsight.InsightStatus status, int limit) {
        return jdbc.query("SELECT * FROM executive_insights WHERE tenant_id = ? AND status = ? ORDER BY created_at DESC LIMIT ?",
                MAPPER, tenantId, status.name(), limit);
    }

    @Override
    public List<ExecutiveInsight> findByTenantAndType(UUID tenantId, ExecutiveInsight.InsightType type, int limit) {
        return jdbc.query("SELECT * FROM executive_insights WHERE tenant_id = ? AND type = ? ORDER BY created_at DESC LIMIT ?",
                MAPPER, tenantId, type.name(), limit);
    }
}
