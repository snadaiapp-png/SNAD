package com.sanad.platform.management.infrastructure;

import com.sanad.platform.management.domain.Escalation;
import com.sanad.platform.management.domain.EscalationRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcEscalationRepository implements EscalationRepository {

    private final JdbcTemplate jdbc;

    public JdbcEscalationRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final RowMapper<Escalation> MAPPER = (rs, rowNum) -> new Escalation(
            rs.getObject("id", UUID.class),
            rs.getObject("tenant_id", UUID.class),
            rs.getString("code"),
            Escalation.SourceEntityType.valueOf(rs.getString("source_entity_type")),
            rs.getObject("source_entity_id", UUID.class),
            rs.getString("reason"),
            Escalation.Severity.valueOf(rs.getString("severity")),
            Escalation.Status.valueOf(rs.getString("status")),
            rs.getInt("escalation_level"),
            rs.getObject("assigned_to", UUID.class),
            rs.getTimestamp("sla_deadline") != null ? rs.getTimestamp("sla_deadline").toInstant() : null,
            rs.getTimestamp("resolved_at") != null ? rs.getTimestamp("resolved_at").toInstant() : null,
            rs.getString("resolution"),
            rs.getObject("created_by", UUID.class),
            rs.getLong("version"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant()
    );

    @Override
    public Escalation save(Escalation e) {
        if (e.version() == 0) return insert(e);
        return update(e);
    }

    private Escalation insert(Escalation e) {
        jdbc.update("""
                INSERT INTO escalations
                    (id, tenant_id, code, source_entity_type, source_entity_id, reason,
                     severity, status, escalation_level, assigned_to, sla_deadline,
                     resolved_at, resolution, created_by, version, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                e.id(), e.tenantId(), e.code(), e.sourceEntityType().name(),
                e.sourceEntityId(), e.reason(), e.severity().name(), e.status().name(),
                e.escalationLevel(), e.assignedTo(),
                e.slaDeadline() != null ? Timestamp.from(e.slaDeadline()) : null,
                e.resolvedAt() != null ? Timestamp.from(e.resolvedAt()) : null,
                e.resolution(), e.createdBy(),
                e.version(), Timestamp.from(e.createdAt()), Timestamp.from(e.updatedAt())
        );
        return e;
    }

    private Escalation update(Escalation e) {
        int affected = jdbc.update("""
                UPDATE escalations SET
                    severity = ?, status = ?, escalation_level = ?, assigned_to = ?,
                    sla_deadline = ?, resolved_at = ?, resolution = ?,
                    version = ?, updated_at = ?
                WHERE id = ? AND tenant_id = ? AND version = ?
                """,
                e.severity().name(), e.status().name(), e.escalationLevel(),
                e.assignedTo(),
                e.slaDeadline() != null ? Timestamp.from(e.slaDeadline()) : null,
                e.resolvedAt() != null ? Timestamp.from(e.resolvedAt()) : null,
                e.resolution(),
                e.version(), Timestamp.from(e.updatedAt()),
                e.id(), e.tenantId(), e.version() - 1
        );
        if (affected == 0) {
            throw new org.springframework.dao.OptimisticLockingFailureException(
                    "Escalation " + e.id() + " was modified by another transaction");
        }
        return e;
    }

    @Override
    public Optional<Escalation> findById(UUID tenantId, UUID id) {
        return jdbc.query("SELECT * FROM escalations WHERE tenant_id = ? AND id = ?",
                MAPPER, tenantId, id).stream().findFirst();
    }

    @Override
    public List<Escalation> findByTenant(UUID tenantId, int limit) {
        return jdbc.query("SELECT * FROM escalations WHERE tenant_id = ? ORDER BY created_at DESC LIMIT ?",
                MAPPER, tenantId, limit);
    }

    @Override
    public List<Escalation> findByTenantAndStatus(UUID tenantId, Escalation.Status status, int limit) {
        return jdbc.query("SELECT * FROM escalations WHERE tenant_id = ? AND status = ? ORDER BY created_at DESC LIMIT ?",
                MAPPER, tenantId, status.name(), limit);
    }

    @Override
    public List<Escalation> findBySourceEntity(UUID tenantId, Escalation.SourceEntityType sourceType, UUID sourceId) {
        return jdbc.query("SELECT * FROM escalations WHERE tenant_id = ? AND source_entity_type = ? AND source_entity_id = ? ORDER BY created_at DESC",
                MAPPER, tenantId, sourceType.name(), sourceId);
    }

    @Override
    public void deleteById(UUID tenantId, UUID id) {
        jdbc.update("DELETE FROM escalations WHERE tenant_id = ? AND id = ?", tenantId, id);
    }
}
