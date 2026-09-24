package com.sanad.platform.management.infrastructure;

import com.sanad.platform.management.domain.KpiTarget;
import com.sanad.platform.management.domain.KpiTargetRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcKpiTargetRepository implements KpiTargetRepository {

    private final JdbcTemplate jdbc;

    public JdbcKpiTargetRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final RowMapper<KpiTarget> MAPPER = (rs, rowNum) -> new KpiTarget(
            rs.getObject("id", UUID.class),
            rs.getObject("tenant_id", UUID.class),
            rs.getObject("kpi_definition_id", UUID.class),
            rs.getDate("period_start").toLocalDate(),
            rs.getDate("period_end").toLocalDate(),
            rs.getBigDecimal("target_value"),
            rs.getBigDecimal("minimum_value"),
            rs.getBigDecimal("stretch_value"),
            rs.getObject("owner_user_id", UUID.class),
            KpiTarget.Status.valueOf(rs.getString("status")),
            rs.getLong("version"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant()
    );

    @Override
    public KpiTarget save(KpiTarget t) {
        if (t.version() == 0) {
            return insert(t);
        }
        return update(t);
    }

    private KpiTarget insert(KpiTarget t) {
        jdbc.update("""
                INSERT INTO kpi_targets
                    (id, tenant_id, kpi_definition_id, period_start, period_end,
                     target_value, minimum_value, stretch_value, owner_user_id,
                     status, version, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                t.id(), t.tenantId(), t.kpiDefinitionId(),
                Date.valueOf(t.periodStart()), Date.valueOf(t.periodEnd()),
                t.targetValue(), t.minimumValue(), t.stretchValue(), t.ownerUserId(),
                t.status().name(), t.version(),
                Timestamp.from(t.createdAt()), Timestamp.from(t.updatedAt())
        );
        return t;
    }

    private KpiTarget update(KpiTarget t) {
        int affected = jdbc.update("""
                UPDATE kpi_targets SET
                    period_start = ?, period_end = ?, target_value = ?,
                    minimum_value = ?, stretch_value = ?, owner_user_id = ?,
                    status = ?, version = ?, updated_at = ?
                WHERE id = ? AND tenant_id = ? AND version = ?
                """,
                Date.valueOf(t.periodStart()), Date.valueOf(t.periodEnd()),
                t.targetValue(), t.minimumValue(), t.stretchValue(), t.ownerUserId(),
                t.status().name(), t.version(), Timestamp.from(t.updatedAt()),
                t.id(), t.tenantId(), t.version() - 1
        );
        if (affected == 0) {
            throw new org.springframework.dao.OptimisticLockingFailureException(
                    "KpiTarget " + t.id() + " was modified by another transaction");
        }
        return t;
    }

    @Override
    public Optional<KpiTarget> findById(UUID tenantId, UUID id) {
        return jdbc.query("""
                SELECT * FROM kpi_targets WHERE tenant_id = ? AND id = ?
                """, MAPPER, tenantId, id).stream().findFirst();
    }

    @Override
    public Optional<KpiTarget> findActiveForDate(UUID kpiDefinitionId, java.time.LocalDate asOf) {
        return jdbc.query("""
                SELECT * FROM kpi_targets
                WHERE kpi_definition_id = ? AND status = 'ACTIVE'
                  AND period_start <= ? AND period_end >= ?
                ORDER BY period_start DESC LIMIT 1
                """, MAPPER, kpiDefinitionId, Date.valueOf(asOf), Date.valueOf(asOf))
                .stream().findFirst();
    }

    @Override
    public List<KpiTarget> findByKpiDefinition(UUID tenantId, UUID kpiDefinitionId) {
        return jdbc.query("""
                SELECT * FROM kpi_targets WHERE tenant_id = ? AND kpi_definition_id = ?
                ORDER BY period_start DESC
                """, MAPPER, tenantId, kpiDefinitionId);
    }

    @Override
    public List<KpiTarget> findByTenant(UUID tenantId, int limit) {
        return jdbc.query("""
                SELECT * FROM kpi_targets WHERE tenant_id = ?
                ORDER BY period_start DESC LIMIT ?
                """, MAPPER, tenantId, limit);
    }

    @Override
    public List<KpiTarget> findActiveByTenant(UUID tenantId, int limit) {
        return jdbc.query("""
                SELECT * FROM kpi_targets WHERE tenant_id = ? AND status = 'ACTIVE'
                ORDER BY period_end ASC LIMIT ?
                """, MAPPER, tenantId, limit);
    }
}
