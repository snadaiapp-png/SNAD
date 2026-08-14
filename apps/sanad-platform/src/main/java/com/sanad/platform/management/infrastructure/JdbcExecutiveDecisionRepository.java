package com.sanad.platform.management.infrastructure;

import com.sanad.platform.management.domain.ExecutiveDecision;
import com.sanad.platform.management.domain.ExecutiveDecisionRepository;
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
public class JdbcExecutiveDecisionRepository implements ExecutiveDecisionRepository {

    private final JdbcTemplate jdbc;

    public JdbcExecutiveDecisionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final RowMapper<ExecutiveDecision> MAPPER = (rs, rowNum) -> new ExecutiveDecision(
            rs.getObject("id", UUID.class),
            rs.getObject("tenant_id", UUID.class),
            rs.getString("decision_number"),
            rs.getString("title"),
            rs.getString("description"),
            rs.getString("rationale"),
            rs.getString("category"),
            ExecutiveDecision.Priority.valueOf(rs.getString("priority")),
            ExecutiveDecision.Status.valueOf(rs.getString("status")),
            rs.getString("impact"),
            rs.getString("expected_outcome"),
            rs.getString("actual_outcome"),
            rs.getObject("owner_user_id", UUID.class),
            rs.getObject("created_by", UUID.class),
            rs.getObject("decided_by", UUID.class),
            rs.getDate("decision_date") != null ? rs.getDate("decision_date").toLocalDate() : null,
            rs.getDate("due_date") != null ? rs.getDate("due_date").toLocalDate() : null,
            rs.getTimestamp("executed_at") != null ? rs.getTimestamp("executed_at").toInstant() : null,
            rs.getTimestamp("completed_at") != null ? rs.getTimestamp("completed_at").toInstant() : null,
            rs.getLong("version"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant()
    );

    @Override
    public ExecutiveDecision save(ExecutiveDecision d) {
        if (d.version() == 0) return insert(d);
        return update(d);
    }

    private ExecutiveDecision insert(ExecutiveDecision d) {
        jdbc.update("""
                INSERT INTO executive_decisions
                    (id, tenant_id, decision_number, title, description, rationale, category,
                     priority, status, impact, expected_outcome, actual_outcome, owner_user_id,
                     created_by, decided_by, decision_date, due_date, executed_at, completed_at,
                     version, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                d.id(), d.tenantId(), d.decisionNumber(), d.title(), d.description(), d.rationale(),
                d.category(), d.priority().name(), d.status().name(), d.impact(), d.expectedOutcome(),
                d.actualOutcome(), d.ownerUserId(), d.createdBy(), d.decidedBy(),
                d.decisionDate() != null ? Date.valueOf(d.decisionDate()) : null,
                d.dueDate() != null ? Date.valueOf(d.dueDate()) : null,
                d.executedAt() != null ? Timestamp.from(d.executedAt()) : null,
                d.completedAt() != null ? Timestamp.from(d.completedAt()) : null,
                d.version(), Timestamp.from(d.createdAt()), Timestamp.from(d.updatedAt())
        );
        return d;
    }

    private ExecutiveDecision update(ExecutiveDecision d) {
        int affected = jdbc.update("""
                UPDATE executive_decisions SET
                    title = ?, description = ?, rationale = ?, category = ?, priority = ?, status = ?,
                    impact = ?, expected_outcome = ?, actual_outcome = ?, owner_user_id = ?,
                    decided_by = ?, decision_date = ?, due_date = ?, executed_at = ?, completed_at = ?,
                    version = ?, updated_at = ?
                WHERE id = ? AND tenant_id = ? AND version = ?
                """,
                d.title(), d.description(), d.rationale(), d.category(), d.priority().name(),
                d.status().name(), d.impact(), d.expectedOutcome(), d.actualOutcome(),
                d.ownerUserId(), d.decidedBy(),
                d.decisionDate() != null ? Date.valueOf(d.decisionDate()) : null,
                d.dueDate() != null ? Date.valueOf(d.dueDate()) : null,
                d.executedAt() != null ? Timestamp.from(d.executedAt()) : null,
                d.completedAt() != null ? Timestamp.from(d.completedAt()) : null,
                d.version(), Timestamp.from(d.updatedAt()),
                d.id(), d.tenantId(), d.version() - 1
        );
        if (affected == 0) {
            throw new org.springframework.dao.OptimisticLockingFailureException(
                    "ExecutiveDecision " + d.id() + " was modified by another transaction");
        }
        return d;
    }

    @Override
    public Optional<ExecutiveDecision> findById(UUID tenantId, UUID id) {
        return jdbc.query("SELECT * FROM executive_decisions WHERE tenant_id = ? AND id = ?",
                MAPPER, tenantId, id).stream().findFirst();
    }

    @Override
    public Optional<ExecutiveDecision> findByNumber(UUID tenantId, String decisionNumber) {
        return jdbc.query("SELECT * FROM executive_decisions WHERE tenant_id = ? AND decision_number = ?",
                MAPPER, tenantId, decisionNumber).stream().findFirst();
    }

    @Override
    public List<ExecutiveDecision> findByTenant(UUID tenantId, int limit) {
        return jdbc.query("SELECT * FROM executive_decisions WHERE tenant_id = ? ORDER BY updated_at DESC LIMIT ?",
                MAPPER, tenantId, limit);
    }

    @Override
    public List<ExecutiveDecision> findByTenantAndStatus(UUID tenantId, ExecutiveDecision.Status status, int limit) {
        return jdbc.query("SELECT * FROM executive_decisions WHERE tenant_id = ? AND status = ? ORDER BY updated_at DESC LIMIT ?",
                MAPPER, tenantId, status.name(), limit);
    }

    @Override
    public void deleteById(UUID tenantId, UUID id) {
        jdbc.update("DELETE FROM executive_decisions WHERE tenant_id = ? AND id = ?", tenantId, id);
    }
}
