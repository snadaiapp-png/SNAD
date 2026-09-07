package com.sanad.platform.workflow.infrastructure;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * JdbcTemplate read-model queries (design decision AL3). Every statement
 * starts from {@code tenant_id} — a read model mirrors committed
 * authoritative state and never grants authorization.
 */
@Repository
public class JdbcWorkflowOperationalQueryRepository {

    private final JdbcTemplate jdbc;

    public JdbcWorkflowOperationalQueryRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record TaskRow(UUID workItemId, String title, String status,
                          String assignmentMode, String type, Instant dueAt, long version) {}

    public List<TaskRow> findMyTasks(UUID tenantId, UUID employeeId, int limit) {
        return jdbc.query("""
                SELECT id, title, status, assignment_mode, type, due_at, version
                FROM workflow_work_items
                WHERE tenant_id = ?
                  AND status NOT IN ('COMPLETED', 'CANCELLED', 'EXPIRED')
                  AND (assignee_employee_id = ? OR claimed_by_employee_id = ?)
                ORDER BY priority DESC, due_at NULLS LAST, created_at ASC
                LIMIT ?
                """,
                (rs, n) -> new TaskRow(
                        rs.getObject("id", UUID.class),
                        rs.getString("title"),
                        rs.getString("status"),
                        rs.getString("assignment_mode"),
                        rs.getString("type"),
                        rs.getTimestamp("due_at") != null ? rs.getTimestamp("due_at").toInstant() : null,
                        rs.getLong("version")),
                tenantId, employeeId, employeeId, limit);
    }

    public List<TaskRow> findPoolTasks(UUID tenantId, UUID employeeId, int limit) {
        return jdbc.query("""
                SELECT wi.id, wi.title, wi.status, wi.assignment_mode, wi.type, wi.due_at, wi.version
                FROM workflow_work_items wi
                WHERE wi.tenant_id = ? AND wi.status = 'AVAILABLE'
                  AND EXISTS (
                      SELECT 1 FROM workflow_work_item_candidates c
                      WHERE c.tenant_id = wi.tenant_id
                        AND c.work_item_id = wi.id
                        AND c.employee_id = ?
                  )
                ORDER BY wi.priority DESC, wi.due_at NULLS LAST, wi.created_at ASC
                LIMIT ?
                """,
                (rs, n) -> new TaskRow(
                        rs.getObject("id", UUID.class),
                        rs.getString("title"),
                        rs.getString("status"),
                        rs.getString("assignment_mode"),
                        rs.getString("type"),
                        rs.getTimestamp("due_at") != null ? rs.getTimestamp("due_at").toInstant() : null,
                        rs.getLong("version")),
                tenantId, employeeId, limit);
    }

    public List<Map<String, Object>> findMyApprovals(UUID tenantId, UUID userId, int limit) {
        return jdbc.queryForList("""
                SELECT id, workflow_instance_id, status, decision, due_at, version
                FROM workflow_approval_requests
                WHERE tenant_id = ? AND requested_from_user_id = ? AND status = 'PENDING'
                ORDER BY due_at NULLS LAST, requested_at ASC
                LIMIT ?
                """, tenantId, userId, limit);
    }

    public List<Map<String, Object>> definitionSummaries(UUID tenantId, int limit) {
        return jdbc.queryForList("""
                SELECT id, code, name, version, status, engine_generation, publication_state, updated_at
                FROM workflow_definitions
                WHERE tenant_id = ?
                ORDER BY updated_at DESC
                LIMIT ?
                """, tenantId, limit);
    }

    public List<Map<String, Object>> searchInstances(UUID tenantId, String status, int limit) {
        return jdbc.queryForList("""
                SELECT id, workflow_definition_id, workflow_version, status, current_step_key, started_at
                FROM workflow_instances
                WHERE tenant_id = ? AND status = ?
                ORDER BY started_at DESC
                LIMIT ?
                """, tenantId, status, limit);
    }

    public List<Map<String, Object>> openIncidents(UUID tenantId, int limit) {
        return jdbc.queryForList("""
                SELECT id, workflow_instance_id, source, severity, failure_category, status, created_at
                FROM workflow_incidents
                WHERE tenant_id = ? AND status IN ('OPEN', 'ACKNOWLEDGED')
                ORDER BY created_at ASC
                LIMIT ?
                """, tenantId, limit);
    }

    public int countByStatus(UUID tenantId, String status) {
        var count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM workflow_work_items WHERE tenant_id = ? AND status = ?
                """, Long.class, tenantId, status);
        return count != null ? count.intValue() : 0;
    }

    public int countOverdueSteps(UUID tenantId) {
        var count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM workflow_step_instances
                WHERE tenant_id = ? AND status = 'IN_PROGRESS' AND due_at < NOW()
                """, Long.class, tenantId);
        return count != null ? count.intValue() : 0;
    }

    public int countOverdueApprovals(UUID tenantId) {
        var count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM workflow_approval_requests
                WHERE tenant_id = ? AND status = 'PENDING' AND due_at < NOW()
                """, Long.class, tenantId);
        return count != null ? count.intValue() : 0;
    }

    public record OpenIncidentAggregate(int count, long oldestAgeMinutes) {}

    public OpenIncidentAggregate openIncidentAggregate(UUID tenantId) {
        return jdbc.query("""
                SELECT COUNT(*) AS cnt, MIN(created_at) AS oldest
                FROM workflow_incidents
                WHERE tenant_id = ? AND status IN ('OPEN', 'ACKNOWLEDGED')
                """,
                rs -> {
                    rs.next();
                    int count = rs.getInt("cnt");
                    Timestamp oldest = rs.getTimestamp("oldest");
                    long ageMinutes = oldest != null
                            ? Math.max(0, java.time.Duration.between(oldest.toInstant(), Instant.now()).toMinutes())
                            : 0;
                    return new OpenIncidentAggregate(count, ageMinutes);
                },
                tenantId);
    }

    public long inboxLagSeconds(UUID tenantId) {
        var lag = jdbc.queryForObject("""
                SELECT COALESCE(EXTRACT(EPOCH FROM (NOW() - MIN(received_at))), 0)
                FROM workflow_event_inbox
                WHERE tenant_id = ? AND status = 'RECEIVED'
                """, Double.class, tenantId);
        return lag != null ? lag.longValue() : 0;
    }

    public long outboxLagSeconds(UUID tenantId) {
        var lag = jdbc.queryForObject("""
                SELECT COALESCE(EXTRACT(EPOCH FROM (NOW() - MIN(available_at))), 0)
                FROM workflow_event_outbox
                WHERE tenant_id = ? AND status = 'PENDING'
                """, Double.class, tenantId);
        return lag != null ? lag.longValue() : 0;
    }

    public int stuckJoins(UUID tenantId) {
        var count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM workflow_branch_tokens t
                WHERE t.tenant_id = ? AND t.status = 'RUNNING'
                  AND EXISTS (
                      SELECT 1 FROM workflow_branch_tokens o
                      WHERE o.tenant_id = t.tenant_id
                        AND o.workflow_instance_id = t.workflow_instance_id
                        AND o.join_step_id = t.join_step_id
                        AND o.status = 'COMPLETED'
                  )
                """, Long.class, tenantId);
        return count != null ? count.intValue() : 0;
    }

    public int countNotificationsByStatus(UUID tenantId, String deliveryStatus) {
        var count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM workflow_notification_intents
                WHERE tenant_id = ? AND delivery_status = ?
                """, Long.class, tenantId, deliveryStatus);
        return count != null ? count.intValue() : 0;
    }

    public long countAttemptsByOutcome(UUID tenantId, List<String> outcomes) {
        String placeholders = String.join(",", java.util.Collections.nCopies(outcomes.size(), "?"));
        Object[] args = new Object[outcomes.size() + 1];
        args[0] = tenantId;
        for (int i = 0; i < outcomes.size(); i++) {
            args[i + 1] = outcomes.get(i);
        }
        var count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM workflow_execution_attempts "
                        + "WHERE tenant_id = ? AND outcome IN (" + placeholders + ")",
                Long.class, args);
        return count != null ? count : 0;
    }

}
