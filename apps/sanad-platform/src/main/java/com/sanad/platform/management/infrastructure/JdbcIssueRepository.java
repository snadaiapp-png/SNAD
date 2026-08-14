package com.sanad.platform.management.infrastructure;

import com.sanad.platform.management.domain.Issue;
import com.sanad.platform.management.domain.IssueRepository;
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
public class JdbcIssueRepository implements IssueRepository {

    private final JdbcTemplate jdbc;

    public JdbcIssueRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final RowMapper<Issue> MAPPER = (rs, rowNum) -> new Issue(
            rs.getObject("id", UUID.class),
            rs.getObject("tenant_id", UUID.class),
            rs.getString("code"),
            rs.getString("title"),
            rs.getString("description"),
            Issue.Severity.valueOf(rs.getString("severity")),
            Issue.Priority.valueOf(rs.getString("priority")),
            Issue.Status.valueOf(rs.getString("status")),
            rs.getString("source"),
            rs.getString("impact"),
            rs.getString("root_cause"),
            rs.getString("resolution"),
            rs.getObject("owner_user_id", UUID.class),
            rs.getObject("reported_by", UUID.class),
            rs.getTimestamp("reported_at").toInstant(),
            rs.getDate("due_date") != null ? rs.getDate("due_date").toLocalDate() : null,
            rs.getTimestamp("resolved_at") != null ? rs.getTimestamp("resolved_at").toInstant() : null,
            rs.getTimestamp("closed_at") != null ? rs.getTimestamp("closed_at").toInstant() : null,
            rs.getLong("version"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant()
    );

    @Override
    public Issue save(Issue i) {
        if (i.version() == 0) return insert(i);
        return update(i);
    }

    private Issue insert(Issue i) {
        jdbc.update("""
                INSERT INTO issues
                    (id, tenant_id, code, title, description, severity, priority, status,
                     source, impact, root_cause, resolution, owner_user_id, reported_by,
                     reported_at, due_date, resolved_at, closed_at, version, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                i.id(), i.tenantId(), i.code(), i.title(), i.description(),
                i.severity().name(), i.priority().name(), i.status().name(),
                i.source(), i.impact(), i.rootCause(), i.resolution(),
                i.ownerUserId(), i.reportedBy(), Timestamp.from(i.reportedAt()),
                i.dueDate() != null ? Date.valueOf(i.dueDate()) : null,
                i.resolvedAt() != null ? Timestamp.from(i.resolvedAt()) : null,
                i.closedAt() != null ? Timestamp.from(i.closedAt()) : null,
                i.version(), Timestamp.from(i.createdAt()), Timestamp.from(i.updatedAt())
        );
        return i;
    }

    private Issue update(Issue i) {
        int affected = jdbc.update("""
                UPDATE issues SET
                    title = ?, description = ?, severity = ?, priority = ?, status = ?,
                    source = ?, impact = ?, root_cause = ?, resolution = ?, owner_user_id = ?,
                    due_date = ?, resolved_at = ?, closed_at = ?, version = ?, updated_at = ?
                WHERE id = ? AND tenant_id = ? AND version = ?
                """,
                i.title(), i.description(), i.severity().name(), i.priority().name(),
                i.status().name(), i.source(), i.impact(), i.rootCause(), i.resolution(),
                i.ownerUserId(),
                i.dueDate() != null ? Date.valueOf(i.dueDate()) : null,
                i.resolvedAt() != null ? Timestamp.from(i.resolvedAt()) : null,
                i.closedAt() != null ? Timestamp.from(i.closedAt()) : null,
                i.version(), Timestamp.from(i.updatedAt()),
                i.id(), i.tenantId(), i.version() - 1
        );
        if (affected == 0) {
            throw new org.springframework.dao.OptimisticLockingFailureException(
                    "Issue " + i.id() + " was modified by another transaction");
        }
        return i;
    }

    @Override
    public Optional<Issue> findById(UUID tenantId, UUID id) {
        return jdbc.query("SELECT * FROM issues WHERE tenant_id = ? AND id = ?",
                MAPPER, tenantId, id).stream().findFirst();
    }

    @Override
    public Optional<Issue> findByCode(UUID tenantId, String code) {
        return jdbc.query("SELECT * FROM issues WHERE tenant_id = ? AND code = ?",
                MAPPER, tenantId, code).stream().findFirst();
    }

    @Override
    public List<Issue> findByTenant(UUID tenantId, int limit) {
        return jdbc.query("SELECT * FROM issues WHERE tenant_id = ? ORDER BY updated_at DESC LIMIT ?",
                MAPPER, tenantId, limit);
    }

    @Override
    public List<Issue> findByTenantAndStatus(UUID tenantId, Issue.Status status, int limit) {
        return jdbc.query("SELECT * FROM issues WHERE tenant_id = ? AND status = ? ORDER BY updated_at DESC LIMIT ?",
                MAPPER, tenantId, status.name(), limit);
    }

    @Override
    public void deleteById(UUID tenantId, UUID id) {
        jdbc.update("DELETE FROM issues WHERE tenant_id = ? AND id = ?", tenantId, id);
    }
}
