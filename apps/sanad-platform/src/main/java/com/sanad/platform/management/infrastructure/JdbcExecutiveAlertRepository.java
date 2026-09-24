package com.sanad.platform.management.infrastructure;

import com.sanad.platform.management.domain.ExecutiveAlert;
import com.sanad.platform.management.domain.ExecutiveAlertRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcExecutiveAlertRepository implements ExecutiveAlertRepository {

    private final JdbcTemplate jdbc;

    public JdbcExecutiveAlertRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final RowMapper<ExecutiveAlert> MAPPER = (rs, rowNum) -> new ExecutiveAlert(
            rs.getObject("id", UUID.class),
            rs.getObject("tenant_id", UUID.class),
            ExecutiveAlert.AlertType.valueOf(rs.getString("type")),
            ExecutiveAlert.Severity.valueOf(rs.getString("severity")),
            ExecutiveAlert.SourceEntityType.valueOf(rs.getString("source_entity_type")),
            rs.getObject("source_entity_id", UUID.class),
            rs.getString("title"),
            rs.getString("description"),
            ExecutiveAlert.Status.valueOf(rs.getString("status")),
            rs.getObject("acknowledged_by", UUID.class),
            rs.getTimestamp("acknowledged_at") != null ? rs.getTimestamp("acknowledged_at").toInstant() : null,
            rs.getObject("resolved_by", UUID.class),
            rs.getTimestamp("resolved_at") != null ? rs.getTimestamp("resolved_at").toInstant() : null,
            rs.getString("resolution"),
            rs.getObject("created_by", UUID.class),
            rs.getLong("version"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant()
    );

    @Override
    public ExecutiveAlert save(ExecutiveAlert a) {
        if (a.version() == 0) return insert(a);
        return update(a);
    }

    private ExecutiveAlert insert(ExecutiveAlert a) {
        jdbc.update("""
                INSERT INTO executive_alerts
                    (id, tenant_id, type, severity, source_entity_type, source_entity_id,
                     title, description, status, acknowledged_by, acknowledged_at,
                     resolved_by, resolved_at, resolution, created_by, version, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                a.id(), a.tenantId(), a.type().name(), a.severity().name(),
                a.sourceEntityType().name(), a.sourceEntityId(),
                a.title(), a.description(), a.status().name(),
                a.acknowledgedBy(),
                a.acknowledgedAt() != null ? Timestamp.from(a.acknowledgedAt()) : null,
                a.resolvedBy(),
                a.resolvedAt() != null ? Timestamp.from(a.resolvedAt()) : null,
                a.resolution(), a.createdBy(), a.version(),
                Timestamp.from(a.createdAt()), Timestamp.from(a.updatedAt())
        );
        return a;
    }

    private ExecutiveAlert update(ExecutiveAlert a) {
        jdbc.update("""
                UPDATE executive_alerts SET
                    status = ?, acknowledged_by = ?, acknowledged_at = ?,
                    resolved_by = ?, resolved_at = ?, resolution = ?,
                    version = ?, updated_at = ?
                WHERE id = ? AND tenant_id = ? AND version = ?
                """,
                a.status().name(), a.acknowledgedBy(),
                a.acknowledgedAt() != null ? Timestamp.from(a.acknowledgedAt()) : null,
                a.resolvedBy(),
                a.resolvedAt() != null ? Timestamp.from(a.resolvedAt()) : null,
                a.resolution(),
                a.version(), Timestamp.from(a.updatedAt()),
                a.id(), a.tenantId(), a.version() - 1
        );
        return a;
    }

    @Override
    public Optional<ExecutiveAlert> findById(UUID tenantId, UUID id) {
        return jdbc.query("SELECT * FROM executive_alerts WHERE tenant_id = ? AND id = ?",
                MAPPER, tenantId, id).stream().findFirst();
    }

    @Override
    public Optional<ExecutiveAlert> findBySource(UUID tenantId, ExecutiveAlert.SourceEntityType sourceType, UUID sourceId, ExecutiveAlert.AlertType type) {
        return jdbc.query("""
                SELECT * FROM executive_alerts
                WHERE tenant_id = ? AND source_entity_type = ? AND source_entity_id = ? AND type = ?
                """, MAPPER, tenantId, sourceType.name(), sourceId, type.name()).stream().findFirst();
    }

    @Override
    public List<ExecutiveAlert> findByTenant(UUID tenantId, int limit) {
        return jdbc.query("SELECT * FROM executive_alerts WHERE tenant_id = ? ORDER BY created_at DESC LIMIT ?",
                MAPPER, tenantId, limit);
    }

    @Override
    public List<ExecutiveAlert> findByTenantAndStatus(UUID tenantId, ExecutiveAlert.Status status, int limit) {
        return jdbc.query("SELECT * FROM executive_alerts WHERE tenant_id = ? AND status = ? ORDER BY created_at DESC LIMIT ?",
                MAPPER, tenantId, status.name(), limit);
    }
}
