package com.sanad.platform.management.infrastructure;

import com.sanad.platform.management.domain.KpiDefinition;
import com.sanad.platform.management.domain.KpiDefinitionRepository;
import com.sanad.platform.management.domain.KeyResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcKpiDefinitionRepository implements KpiDefinitionRepository {

    private final JdbcTemplate jdbc;

    public JdbcKpiDefinitionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final RowMapper<KpiDefinition> MAPPER = (rs, rowNum) -> new KpiDefinition(
            rs.getObject("id", UUID.class),
            rs.getObject("tenant_id", UUID.class),
            rs.getString("code"),
            rs.getString("name"),
            rs.getString("description"),
            rs.getString("category"),
            KeyResult.MetricUnit.valueOf(rs.getString("metric_unit")),
            KeyResult.Direction.valueOf(rs.getString("direction")),
            rs.getString("formula"),
            rs.getString("source_system"),
            KpiDefinition.Status.valueOf(rs.getString("status")),
            rs.getObject("owner_user_id", UUID.class),
            rs.getLong("version"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant()
    );

    @Override
    public KpiDefinition save(KpiDefinition def) {
        if (def.version() == 0) {
            return insert(def);
        }
        return update(def);
    }

    private KpiDefinition insert(KpiDefinition def) {
        jdbc.update("""
                INSERT INTO kpi_definitions
                    (id, tenant_id, code, name, description, category, metric_unit,
                     direction, formula, source_system, status, owner_user_id, version,
                     created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                def.id(), def.tenantId(), def.code(), def.name(), def.description(),
                def.category(), def.metricUnit().name(), def.direction().name(),
                def.formula(), def.sourceSystem(), def.status().name(),
                def.ownerUserId(), def.version(),
                Timestamp.from(def.createdAt()), Timestamp.from(def.updatedAt())
        );
        return def;
    }

    private KpiDefinition update(KpiDefinition def) {
        int affected = jdbc.update("""
                UPDATE kpi_definitions SET
                    name = ?, description = ?, category = ?, metric_unit = ?, direction = ?,
                    formula = ?, source_system = ?, status = ?, owner_user_id = ?,
                    version = ?, updated_at = ?
                WHERE id = ? AND tenant_id = ? AND version = ?
                """,
                def.name(), def.description(), def.category(),
                def.metricUnit().name(), def.direction().name(),
                def.formula(), def.sourceSystem(), def.status().name(),
                def.ownerUserId(), def.version(), Timestamp.from(def.updatedAt()),
                def.id(), def.tenantId(), def.version() - 1
        );
        if (affected == 0) {
            throw new org.springframework.dao.OptimisticLockingFailureException(
                    "KpiDefinition " + def.id() + " was modified by another transaction");
        }
        return def;
    }

    @Override
    public Optional<KpiDefinition> findById(UUID tenantId, UUID id) {
        return jdbc.query("""
                SELECT * FROM kpi_definitions WHERE tenant_id = ? AND id = ?
                """, MAPPER, tenantId, id).stream().findFirst();
    }

    @Override
    public Optional<KpiDefinition> findByCode(UUID tenantId, String code) {
        return jdbc.query("""
                SELECT * FROM kpi_definitions WHERE tenant_id = ? AND code = ?
                """, MAPPER, tenantId, code).stream().findFirst();
    }

    @Override
    public List<KpiDefinition> findByTenant(UUID tenantId, int limit) {
        return jdbc.query("""
                SELECT * FROM kpi_definitions WHERE tenant_id = ?
                ORDER BY updated_at DESC LIMIT ?
                """, MAPPER, tenantId, limit);
    }

    @Override
    public List<KpiDefinition> findByTenantAndStatus(UUID tenantId, KpiDefinition.Status status, int limit) {
        return jdbc.query("""
                SELECT * FROM kpi_definitions WHERE tenant_id = ? AND status = ?
                ORDER BY name ASC LIMIT ?
                """, MAPPER, tenantId, status.name(), limit);
    }

    @Override
    public List<KpiDefinition> findByTenantAndCategory(UUID tenantId, String category, int limit) {
        return jdbc.query("""
                SELECT * FROM kpi_definitions WHERE tenant_id = ? AND category = ?
                ORDER BY name ASC LIMIT ?
                """, MAPPER, tenantId, category, limit);
    }

    @Override
    public void deleteById(UUID tenantId, UUID id) {
        jdbc.update("DELETE FROM kpi_definitions WHERE tenant_id = ? AND id = ?",
                tenantId, id);
    }
}
