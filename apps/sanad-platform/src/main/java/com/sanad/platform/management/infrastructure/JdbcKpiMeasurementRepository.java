package com.sanad.platform.management.infrastructure;

import com.sanad.platform.management.domain.KpiMeasurement;
import com.sanad.platform.management.domain.KpiMeasurementRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcKpiMeasurementRepository implements KpiMeasurementRepository {

    private final JdbcTemplate jdbc;

    public JdbcKpiMeasurementRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final RowMapper<KpiMeasurement> MAPPER = (rs, rowNum) -> new KpiMeasurement(
            rs.getObject("id", UUID.class),
            rs.getObject("tenant_id", UUID.class),
            rs.getObject("kpi_definition_id", UUID.class),
            rs.getObject("kpi_target_id", UUID.class),
            rs.getDate("period").toLocalDate(),
            rs.getBigDecimal("measured_value"),
            rs.getBigDecimal("previous_value"),
            rs.getBigDecimal("variance_pct"),
            KpiMeasurement.Status.valueOf(rs.getString("status")),
            rs.getString("evidence"),
            rs.getObject("measured_by", UUID.class),
            rs.getTimestamp("measured_at").toInstant()
    );

    @Override
    public KpiMeasurement save(KpiMeasurement m) {
        jdbc.update("""
                INSERT INTO kpi_measurements
                    (id, tenant_id, kpi_definition_id, kpi_target_id, period,
                     measured_value, previous_value, variance_pct, status, evidence,
                     measured_by, measured_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                m.id(), m.tenantId(), m.kpiDefinitionId(), m.kpiTargetId(),
                Date.valueOf(m.period()),
                m.measuredValue(), m.previousValue(), m.variancePct(),
                m.status().name(), m.evidence(),
                m.measuredBy(), Timestamp.from(m.measuredAt())
        );
        return m;
    }

    @Override
    public Optional<KpiMeasurement> findById(UUID tenantId, UUID id) {
        return jdbc.query("""
                SELECT * FROM kpi_measurements WHERE tenant_id = ? AND id = ?
                """, MAPPER, tenantId, id).stream().findFirst();
    }

    @Override
    public Optional<KpiMeasurement> findLatest(UUID kpiDefinitionId) {
        return jdbc.query("""
                SELECT * FROM kpi_measurements WHERE kpi_definition_id = ?
                ORDER BY period DESC LIMIT 1
                """, MAPPER, kpiDefinitionId).stream().findFirst();
    }

    @Override
    public Optional<KpiMeasurement> findByPeriod(UUID kpiDefinitionId, java.time.LocalDate period) {
        return jdbc.query("""
                SELECT * FROM kpi_measurements
                WHERE kpi_definition_id = ? AND period = ?
                """, MAPPER, kpiDefinitionId, Date.valueOf(period)).stream().findFirst();
    }

    @Override
    public List<KpiMeasurement> findByKpiDefinition(UUID kpiDefinitionId, int limit) {
        return jdbc.query("""
                SELECT * FROM kpi_measurements WHERE kpi_definition_id = ?
                ORDER BY period DESC LIMIT ?
                """, MAPPER, kpiDefinitionId, limit);
    }

    @Override
    public List<KpiMeasurement> findLatestForDefinitions(List<UUID> kpiDefinitionIds) {
        if (kpiDefinitionIds == null || kpiDefinitionIds.isEmpty()) {
            return List.of();
        }
        // Use DISTINCT ON (kpi_definition_id) to get the latest measurement per KPI
        var placeholders = String.join(",", kpiDefinitionIds.stream().map(id -> "?").toList());
        return jdbc.query("""
                SELECT DISTINCT ON (kpi_definition_id) * FROM kpi_measurements
                WHERE kpi_definition_id IN (%s)
                ORDER BY kpi_definition_id, period DESC
                """.formatted(placeholders), MAPPER, kpiDefinitionIds.toArray());
    }

    @Override
    public long countByKpiDefinition(UUID kpiDefinitionId) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM kpi_measurements WHERE kpi_definition_id = ?",
                Long.class, kpiDefinitionId);
        return count != null ? count : 0;
    }
}
