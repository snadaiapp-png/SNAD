package com.sanad.platform.management.infrastructure;

import com.sanad.platform.management.domain.ManagementAuditEntry;
import com.sanad.platform.management.domain.ManagementAuditRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

@Repository
public class JdbcManagementAuditRepository implements ManagementAuditRepository {

    private final JdbcTemplate jdbc;

    public JdbcManagementAuditRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final RowMapper<ManagementAuditEntry> MAPPER = (rs, rowNum) -> new ManagementAuditEntry(
            rs.getObject("id", UUID.class),
            rs.getObject("tenant_id", UUID.class),
            rs.getObject("actor_user_id", UUID.class),
            ManagementAuditEntry.EntityType.valueOf(rs.getString("entity_type")),
            rs.getObject("entity_id", UUID.class),
            ManagementAuditEntry.Action.valueOf(rs.getString("action")),
            rs.getString("from_state"),
            rs.getString("to_state"),
            rs.getString("changes"),
            rs.getObject("correlation_id", UUID.class),
            rs.getTimestamp("created_at").toInstant()
    );

    @Override
    public ManagementAuditEntry save(ManagementAuditEntry entry) {
        jdbc.update("""
                INSERT INTO management_audit_trail
                    (id, tenant_id, actor_user_id, entity_type, entity_id, action,
                     from_state, to_state, changes, correlation_id, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                entry.id(), entry.tenantId(), entry.actorUserId(),
                entry.entityType().name(), entry.entityId(), entry.action().name(),
                entry.fromState(), entry.toState(), entry.changes(),
                entry.correlationId(),
                Timestamp.from(entry.createdAt())
        );
        return entry;
    }

    @Override
    public List<ManagementAuditEntry> findByEntity(UUID tenantId, ManagementAuditEntry.EntityType entityType, UUID entityId) {
        return jdbc.query("""
                SELECT * FROM management_audit_trail
                WHERE tenant_id = ? AND entity_type = ? AND entity_id = ?
                ORDER BY created_at DESC
                """, MAPPER, tenantId, entityType.name(), entityId);
    }

    @Override
    public List<ManagementAuditEntry> findByTenant(UUID tenantId, int limit) {
        return jdbc.query("""
                SELECT * FROM management_audit_trail WHERE tenant_id = ?
                ORDER BY created_at DESC LIMIT ?
                """, MAPPER, tenantId, limit);
    }

    @Override
    public List<ManagementAuditEntry> findByActor(UUID tenantId, UUID actorUserId, int limit) {
        return jdbc.query("""
                SELECT * FROM management_audit_trail
                WHERE tenant_id = ? AND actor_user_id = ?
                ORDER BY created_at DESC LIMIT ?
                """, MAPPER, tenantId, actorUserId, limit);
    }
}
