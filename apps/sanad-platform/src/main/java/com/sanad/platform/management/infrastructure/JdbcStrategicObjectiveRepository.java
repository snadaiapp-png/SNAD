package com.sanad.platform.management.infrastructure;

import com.sanad.platform.management.domain.StrategicObjective;
import com.sanad.platform.management.domain.StrategicObjectiveRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JdbcTemplate implementation of {@link StrategicObjectiveRepository}.
 *
 * <p>Follows the SNAD platform pattern (NOT JPA) for consistency with
 * the rest of the codebase. All queries are parameterized with tenant_id
 * to enforce tenant isolation at the data layer.
 */
@Repository
public class JdbcStrategicObjectiveRepository implements StrategicObjectiveRepository {

    private final JdbcTemplate jdbc;

    public JdbcStrategicObjectiveRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final RowMapper<StrategicObjective> MAPPER = (rs, rowNum) -> new StrategicObjective(
            rs.getObject("id", UUID.class),
            rs.getObject("tenant_id", UUID.class),
            rs.getObject("parent_id", UUID.class),
            rs.getString("code"),
            rs.getString("title"),
            rs.getString("description"),
            StrategicObjective.Status.valueOf(rs.getString("status")),
            StrategicObjective.Priority.valueOf(rs.getString("priority")),
            rs.getObject("owner_user_id", UUID.class),
            rs.getDate("period_start").toLocalDate(),
            rs.getDate("period_end").toLocalDate(),
            rs.getInt("progress_pct"),
            rs.getLong("version"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant()
    );

    @Override
    public StrategicObjective save(StrategicObjective o) {
        if (o.id() == null || o.version() == 0) {
            return insert(o);
        }
        return update(o);
    }

    private StrategicObjective insert(StrategicObjective o) {
        jdbc.update("""
                INSERT INTO strategic_objectives
                    (id, tenant_id, parent_id, code, title, description, status, priority,
                     owner_user_id, period_start, period_end, progress_pct, version,
                     created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                o.id(), o.tenantId(), o.parentId(), o.code(), o.title(), o.description(),
                o.status().name(), o.priority().name(), o.ownerUserId(),
                java.sql.Date.valueOf(o.periodStart()), java.sql.Date.valueOf(o.periodEnd()),
                o.progressPct(), o.version(),
                Timestamp.from(o.createdAt()), Timestamp.from(o.updatedAt())
        );
        return o;
    }

    private StrategicObjective update(StrategicObjective o) {
        int affected = jdbc.update("""
                UPDATE strategic_objectives SET
                    parent_id = ?, title = ?, description = ?, status = ?, priority = ?,
                    owner_user_id = ?, period_start = ?, period_end = ?, progress_pct = ?,
                    version = ?, updated_at = ?
                WHERE id = ? AND tenant_id = ? AND version = ?
                """,
                o.parentId(), o.title(), o.description(), o.status().name(), o.priority().name(),
                o.ownerUserId(),
                java.sql.Date.valueOf(o.periodStart()), java.sql.Date.valueOf(o.periodEnd()),
                o.progressPct(), o.version(), Timestamp.from(o.updatedAt()),
                o.id(), o.tenantId(), o.version() - 1
        );
        if (affected == 0) {
            throw new org.springframework.dao.OptimisticLockingFailureException(
                    "StrategicObjective " + o.id() + " was modified by another transaction");
        }
        return o;
    }

    @Override
    public Optional<StrategicObjective> findById(UUID tenantId, UUID id) {
        return jdbc.query("""
                SELECT * FROM strategic_objectives WHERE tenant_id = ? AND id = ?
                """, MAPPER, tenantId, id).stream().findFirst();
    }

    @Override
    public Optional<StrategicObjective> findByCode(UUID tenantId, String code) {
        return jdbc.query("""
                SELECT * FROM strategic_objectives WHERE tenant_id = ? AND code = ?
                """, MAPPER, tenantId, code).stream().findFirst();
    }

    @Override
    public List<StrategicObjective> findByTenant(UUID tenantId, int limit) {
        return jdbc.query("""
                SELECT * FROM strategic_objectives WHERE tenant_id = ?
                ORDER BY updated_at DESC LIMIT ?
                """, MAPPER, tenantId, limit);
    }

    @Override
    public List<StrategicObjective> findByTenantAndStatus(UUID tenantId, StrategicObjective.Status status, int limit) {
        return jdbc.query("""
                SELECT * FROM strategic_objectives WHERE tenant_id = ? AND status = ?
                ORDER BY updated_at DESC LIMIT ?
                """, MAPPER, tenantId, status.name(), limit);
    }

    @Override
    public List<StrategicObjective> findByParent(UUID tenantId, UUID parentId) {
        return jdbc.query("""
                SELECT * FROM strategic_objectives WHERE tenant_id = ? AND parent_id = ?
                ORDER BY created_at ASC
                """, MAPPER, tenantId, parentId);
    }

    @Override
    public List<StrategicObjective> findActiveObjectivesForPeriod(UUID tenantId, LocalDate asOf) {
        return jdbc.query("""
                SELECT * FROM strategic_objectives
                WHERE tenant_id = ? AND status IN ('ACTIVE', 'AT_RISK', 'OFF_TRACK')
                  AND period_start <= ? AND period_end >= ?
                ORDER BY priority DESC, period_end ASC
                """, MAPPER, tenantId, java.sql.Date.valueOf(asOf), java.sql.Date.valueOf(asOf));
    }

    @Override
    public void deleteById(UUID tenantId, UUID id) {
        jdbc.update("DELETE FROM strategic_objectives WHERE tenant_id = ? AND id = ?",
                tenantId, id);
    }
}
