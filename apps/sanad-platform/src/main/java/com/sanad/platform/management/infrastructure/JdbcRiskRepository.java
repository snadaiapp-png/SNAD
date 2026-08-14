package com.sanad.platform.management.infrastructure;

import com.sanad.platform.management.domain.Risk;
import com.sanad.platform.management.domain.RiskRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcRiskRepository implements RiskRepository {

    private final JdbcTemplate jdbc;

    public JdbcRiskRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final RowMapper<Risk> MAPPER = (rs, rowNum) -> new Risk(
            rs.getObject("id", UUID.class),
            rs.getObject("tenant_id", UUID.class),
            rs.getString("code"),
            rs.getString("title"),
            rs.getString("description"),
            rs.getString("category"),
            Risk.Status.valueOf(rs.getString("status")),
            rs.getInt("probability"),
            rs.getInt("impact"),
            rs.getInt("risk_score"),
            Risk.Severity.valueOf(rs.getString("severity")),
            rs.getObject("owner_user_id", UUID.class),
            rs.getObject("identified_by", UUID.class),
            rs.getTimestamp("identified_at").toInstant(),
            rs.getDate("due_date") != null ? rs.getDate("due_date").toLocalDate() : null,
            rs.getString("mitigation"),
            rs.getString("contingency"),
            rs.getString("treatment_strategy"),
            rs.getString("residual_risk"),
            rs.getTimestamp("closed_at") != null ? rs.getTimestamp("closed_at").toInstant() : null,
            rs.getLong("version"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant()
    );

    @Override
    public Risk save(Risk r) {
        if (r.version() == 0) return insert(r);
        return update(r);
    }

    private Risk insert(Risk r) {
        jdbc.update("""
                INSERT INTO risks
                    (id, tenant_id, code, title, description, category, status,
                     probability, impact, risk_score, severity, owner_user_id, identified_by,
                     identified_at, due_date, mitigation, contingency, treatment_strategy,
                     residual_risk, closed_at, version, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                r.id(), r.tenantId(), r.code(), r.title(), r.description(), r.category(),
                r.status().name(), r.probability(), r.impact(), r.riskScore(),
                r.severity().name(), r.ownerUserId(), r.identifiedBy(),
                Timestamp.from(r.identifiedAt()),
                r.dueDate() != null ? Date.valueOf(r.dueDate()) : null,
                r.mitigation(), r.contingency(), r.treatmentStrategy(), r.residualRisk(),
                r.closedAt() != null ? Timestamp.from(r.closedAt()) : null,
                r.version(), Timestamp.from(r.createdAt()), Timestamp.from(r.updatedAt())
        );
        return r;
    }

    private Risk update(Risk r) {
        int affected = jdbc.update("""
                UPDATE risks SET
                    title = ?, description = ?, category = ?, status = ?,
                    probability = ?, impact = ?, risk_score = ?, severity = ?,
                    owner_user_id = ?, due_date = ?, mitigation = ?, contingency = ?,
                    treatment_strategy = ?, residual_risk = ?, closed_at = ?,
                    version = ?, updated_at = ?
                WHERE id = ? AND tenant_id = ? AND version = ?
                """,
                r.title(), r.description(), r.category(), r.status().name(),
                r.probability(), r.impact(), r.riskScore(), r.severity().name(),
                r.ownerUserId(),
                r.dueDate() != null ? Date.valueOf(r.dueDate()) : null,
                r.mitigation(), r.contingency(), r.treatmentStrategy(), r.residualRisk(),
                r.closedAt() != null ? Timestamp.from(r.closedAt()) : null,
                r.version(), Timestamp.from(r.updatedAt()),
                r.id(), r.tenantId(), r.version() - 1
        );
        if (affected == 0) {
            throw new org.springframework.dao.OptimisticLockingFailureException(
                    "Risk " + r.id() + " was modified by another transaction");
        }
        return r;
    }

    @Override
    public Optional<Risk> findById(UUID tenantId, UUID id) {
        return jdbc.query("SELECT * FROM risks WHERE tenant_id = ? AND id = ?",
                MAPPER, tenantId, id).stream().findFirst();
    }

    @Override
    public Optional<Risk> findByCode(UUID tenantId, String code) {
        return jdbc.query("SELECT * FROM risks WHERE tenant_id = ? AND code = ?",
                MAPPER, tenantId, code).stream().findFirst();
    }

    @Override
    public List<Risk> findByTenant(UUID tenantId, int limit) {
        return jdbc.query("SELECT * FROM risks WHERE tenant_id = ? ORDER BY risk_score DESC, updated_at DESC LIMIT ?",
                MAPPER, tenantId, limit);
    }

    @Override
    public List<Risk> findByTenantAndStatus(UUID tenantId, Risk.Status status, int limit) {
        return jdbc.query("SELECT * FROM risks WHERE tenant_id = ? AND status = ? ORDER BY risk_score DESC LIMIT ?",
                MAPPER, tenantId, status.name(), limit);
    }

    @Override
    public List<Risk> findByTenantAndSeverity(UUID tenantId, Risk.Severity severity, int limit) {
        return jdbc.query("SELECT * FROM risks WHERE tenant_id = ? AND severity = ? ORDER BY risk_score DESC LIMIT ?",
                MAPPER, tenantId, severity.name(), limit);
    }

    @Override
    public void deleteById(UUID tenantId, UUID id) {
        jdbc.update("DELETE FROM risks WHERE tenant_id = ? AND id = ?", tenantId, id);
    }
}
