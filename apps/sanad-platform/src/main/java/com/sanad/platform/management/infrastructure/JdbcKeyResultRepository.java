package com.sanad.platform.management.infrastructure;

import com.sanad.platform.management.domain.KeyResult;
import com.sanad.platform.management.domain.KeyResultRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.sql.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcKeyResultRepository implements KeyResultRepository {

    private final JdbcTemplate jdbc;

    public JdbcKeyResultRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final RowMapper<KeyResult> MAPPER = (rs, rowNum) -> new KeyResult(
            rs.getObject("id", UUID.class),
            rs.getObject("tenant_id", UUID.class),
            rs.getObject("objective_id", UUID.class),
            rs.getString("title"),
            rs.getString("description"),
            KeyResult.MetricUnit.valueOf(rs.getString("metric_unit")),
            rs.getBigDecimal("baseline_value"),
            rs.getBigDecimal("target_value"),
            rs.getBigDecimal("current_value"),
            KeyResult.Direction.valueOf(rs.getString("direction")),
            KeyResult.Status.valueOf(rs.getString("status")),
            rs.getInt("weight_pct"),
            rs.getObject("owner_user_id", UUID.class),
            rs.getDate("due_date") != null ? rs.getDate("due_date").toLocalDate() : null,
            rs.getLong("version"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant()
    );

    @Override
    public KeyResult save(KeyResult kr) {
        if (kr.version() == 0) {
            return insert(kr);
        }
        return update(kr);
    }

    private KeyResult insert(KeyResult kr) {
        jdbc.update("""
                INSERT INTO key_results
                    (id, tenant_id, objective_id, title, description, metric_unit,
                     baseline_value, target_value, current_value, direction, status,
                     weight_pct, owner_user_id, due_date, version, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                kr.id(), kr.tenantId(), kr.objectiveId(), kr.title(), kr.description(),
                kr.metricUnit().name(),
                kr.baselineValue(), kr.targetValue(), kr.currentValue(),
                kr.direction().name(), kr.status().name(),
                kr.weightPct(), kr.ownerUserId(),
                kr.dueDate() != null ? Date.valueOf(kr.dueDate()) : null,
                kr.version(),
                Timestamp.from(kr.createdAt()), Timestamp.from(kr.updatedAt())
        );
        return kr;
    }

    private KeyResult update(KeyResult kr) {
        int affected = jdbc.update("""
                UPDATE key_results SET
                    title = ?, description = ?, metric_unit = ?, baseline_value = ?,
                    target_value = ?, current_value = ?, direction = ?, status = ?,
                    weight_pct = ?, owner_user_id = ?, due_date = ?, version = ?, updated_at = ?
                WHERE id = ? AND tenant_id = ? AND version = ?
                """,
                kr.title(), kr.description(), kr.metricUnit().name(),
                kr.baselineValue(), kr.targetValue(), kr.currentValue(),
                kr.direction().name(), kr.status().name(),
                kr.weightPct(), kr.ownerUserId(),
                kr.dueDate() != null ? Date.valueOf(kr.dueDate()) : null,
                kr.version(), Timestamp.from(kr.updatedAt()),
                kr.id(), kr.tenantId(), kr.version() - 1
        );
        if (affected == 0) {
            throw new org.springframework.dao.OptimisticLockingFailureException(
                    "KeyResult " + kr.id() + " was modified by another transaction");
        }
        return kr;
    }

    @Override
    public Optional<KeyResult> findById(UUID tenantId, UUID id) {
        return jdbc.query("""
                SELECT * FROM key_results WHERE tenant_id = ? AND id = ?
                """, MAPPER, tenantId, id).stream().findFirst();
    }

    @Override
    public List<KeyResult> findByObjective(UUID tenantId, UUID objectiveId) {
        return jdbc.query("""
                SELECT * FROM key_results WHERE tenant_id = ? AND objective_id = ?
                ORDER BY weight_pct DESC, created_at ASC
                """, MAPPER, tenantId, objectiveId);
    }

    @Override
    public List<KeyResult> findByTenant(UUID tenantId, int limit) {
        return jdbc.query("""
                SELECT * FROM key_results WHERE tenant_id = ?
                ORDER BY updated_at DESC LIMIT ?
                """, MAPPER, tenantId, limit);
    }

    @Override
    public List<KeyResult> findByTenantAndStatus(UUID tenantId, KeyResult.Status status, int limit) {
        return jdbc.query("""
                SELECT * FROM key_results WHERE tenant_id = ? AND status = ?
                ORDER BY updated_at DESC LIMIT ?
                """, MAPPER, tenantId, status.name(), limit);
    }

    @Override
    public void deleteById(UUID tenantId, UUID id) {
        jdbc.update("DELETE FROM key_results WHERE tenant_id = ? AND id = ?",
                tenantId, id);
    }
}
