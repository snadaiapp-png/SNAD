package com.sanad.platform.management.infrastructure;

import com.sanad.platform.management.domain.StrategicInitiative;
import com.sanad.platform.management.domain.StrategicInitiativeRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcStrategicInitiativeRepository implements StrategicInitiativeRepository {

    private final JdbcTemplate jdbc;

    public JdbcStrategicInitiativeRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final RowMapper<StrategicInitiative> MAPPER = (rs, rowNum) -> new StrategicInitiative(
            rs.getObject("id", UUID.class),
            rs.getObject("tenant_id", UUID.class),
            rs.getObject("objective_id", UUID.class),
            rs.getString("code"),
            rs.getString("name"),
            rs.getString("description"),
            StrategicInitiative.Status.valueOf(rs.getString("status")),
            rs.getObject("owner_user_id", UUID.class),
            rs.getDate("start_date") != null ? rs.getDate("start_date").toLocalDate() : null,
            rs.getDate("target_end_date") != null ? rs.getDate("target_end_date").toLocalDate() : null,
            rs.getDate("actual_end_date") != null ? rs.getDate("actual_end_date").toLocalDate() : null,
            rs.getInt("progress_pct"),
            rs.getObject("budget_minor", Long.class),
            rs.getLong("spent_minor"),
            rs.getLong("version"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant()
    );

    @Override
    public StrategicInitiative save(StrategicInitiative i) {
        if (i.version() == 0) {
            return insert(i);
        }
        return update(i);
    }

    private StrategicInitiative insert(StrategicInitiative i) {
        jdbc.update("""
                INSERT INTO strategic_initiatives
                    (id, tenant_id, objective_id, code, name, description, status,
                     owner_user_id, start_date, target_end_date, actual_end_date,
                     progress_pct, budget_minor, spent_minor, version,
                     created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                i.id(), i.tenantId(), i.objectiveId(), i.code(), i.name(), i.description(),
                i.status().name(), i.ownerUserId(),
                i.startDate() != null ? Date.valueOf(i.startDate()) : null,
                i.targetEndDate() != null ? Date.valueOf(i.targetEndDate()) : null,
                i.actualEndDate() != null ? Date.valueOf(i.actualEndDate()) : null,
                i.progressPct(), i.budgetMinor(), i.spentMinor(), i.version(),
                Timestamp.from(i.createdAt()), Timestamp.from(i.updatedAt())
        );
        return i;
    }

    private StrategicInitiative update(StrategicInitiative i) {
        int affected = jdbc.update("""
                UPDATE strategic_initiatives SET
                    name = ?, description = ?, status = ?, owner_user_id = ?,
                    start_date = ?, target_end_date = ?, actual_end_date = ?,
                    progress_pct = ?, budget_minor = ?, spent_minor = ?,
                    version = ?, updated_at = ?
                WHERE id = ? AND tenant_id = ? AND version = ?
                """,
                i.name(), i.description(), i.status().name(), i.ownerUserId(),
                i.startDate() != null ? Date.valueOf(i.startDate()) : null,
                i.targetEndDate() != null ? Date.valueOf(i.targetEndDate()) : null,
                i.actualEndDate() != null ? Date.valueOf(i.actualEndDate()) : null,
                i.progressPct(), i.budgetMinor(), i.spentMinor(),
                i.version(), Timestamp.from(i.updatedAt()),
                i.id(), i.tenantId(), i.version() - 1
        );
        if (affected == 0) {
            throw new org.springframework.dao.OptimisticLockingFailureException(
                    "StrategicInitiative " + i.id() + " was modified by another transaction");
        }
        return i;
    }

    @Override
    public Optional<StrategicInitiative> findById(UUID tenantId, UUID id) {
        return jdbc.query("""
                SELECT * FROM strategic_initiatives WHERE tenant_id = ? AND id = ?
                """, MAPPER, tenantId, id).stream().findFirst();
    }

    @Override
    public Optional<StrategicInitiative> findByCode(UUID tenantId, String code) {
        return jdbc.query("""
                SELECT * FROM strategic_initiatives WHERE tenant_id = ? AND code = ?
                """, MAPPER, tenantId, code).stream().findFirst();
    }

    @Override
    public List<StrategicInitiative> findByObjective(UUID tenantId, UUID objectiveId) {
        return jdbc.query("""
                SELECT * FROM strategic_initiatives WHERE tenant_id = ? AND objective_id = ?
                ORDER BY created_at ASC
                """, MAPPER, tenantId, objectiveId);
    }

    @Override
    public List<StrategicInitiative> findByTenant(UUID tenantId, int limit) {
        return jdbc.query("""
                SELECT * FROM strategic_initiatives WHERE tenant_id = ?
                ORDER BY updated_at DESC LIMIT ?
                """, MAPPER, tenantId, limit);
    }

    @Override
    public List<StrategicInitiative> findByTenantAndStatus(UUID tenantId, StrategicInitiative.Status status, int limit) {
        return jdbc.query("""
                SELECT * FROM strategic_initiatives WHERE tenant_id = ? AND status = ?
                ORDER BY updated_at DESC LIMIT ?
                """, MAPPER, tenantId, status.name(), limit);
    }

    @Override
    public void deleteById(UUID tenantId, UUID id) {
        jdbc.update("DELETE FROM strategic_initiatives WHERE tenant_id = ? AND id = ?",
                tenantId, id);
    }
}
