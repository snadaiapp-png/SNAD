package com.sanad.platform.workflow.infrastructure;

import com.sanad.platform.workflow.domain.WorkflowWorkItem;
import com.sanad.platform.workflow.domain.WorkflowWorkItemCandidate;
import com.sanad.platform.workflow.domain.WorkflowWorkItemRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JdbcTemplate adapter for central WorkItems (NOT JPA), following the
 * platform pattern: hand-written SQL, optimistic version columns, tenant
 * scoping on every statement.
 */
@Repository
public class JdbcWorkflowWorkItemRepository implements WorkflowWorkItemRepository {

    private final JdbcTemplate jdbc;

    public JdbcWorkflowWorkItemRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final RowMapper<WorkflowWorkItem> ITEM_MAPPER = (rs, rowNum) -> new WorkflowWorkItem(
            rs.getObject("id", UUID.class),
            rs.getObject("tenant_id", UUID.class),
            rs.getObject("workflow_instance_id", UUID.class),
            rs.getObject("workflow_step_instance_id", UUID.class),
            WorkflowWorkItem.Type.valueOf(rs.getString("type")),
            WorkflowWorkItem.Status.valueOf(rs.getString("status")),
            rs.getObject("assignee_employee_id", UUID.class),
            rs.getObject("claimed_by_employee_id", UUID.class),
            WorkflowWorkItem.AssignmentMode.valueOf(rs.getString("assignment_mode")),
            rs.getString("source_module"),
            rs.getString("source_entity_type"),
            rs.getObject("source_entity_id", UUID.class),
            rs.getString("title"),
            rs.getString("description"),
            rs.getInt("priority"),
            toInstant(rs, "due_at"),
            toInstant(rs, "sla_due_at"),
            toInstant(rs, "claimed_at"),
            toInstant(rs, "completed_at"),
            rs.getLong("version"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant()
    );

    private static Instant toInstant(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        var ts = rs.getTimestamp(column);
        return ts != null ? ts.toInstant() : null;
    }

    @Override
    public WorkflowWorkItem insert(WorkflowWorkItem item) {
        jdbc.update("""
                INSERT INTO workflow_work_items (
                    id, tenant_id, workflow_instance_id, workflow_step_instance_id,
                    type, status, assignee_employee_id, claimed_by_employee_id, assignment_mode,
                    source_module, source_entity_type, source_entity_id, title, description,
                    priority, due_at, sla_due_at, claimed_at, completed_at,
                    version, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                item.id(), item.tenantId(), item.workflowInstanceId(), item.workflowStepInstanceId(),
                item.type().name(), item.status().name(),
                item.assigneeEmployeeId(), item.claimedByEmployeeId(), item.assignmentMode().name(),
                item.sourceModule(), item.sourceEntityType(), item.sourceEntityId(),
                item.title(), item.description(), item.priority(),
                toTimestamp(item.dueAt()), toTimestamp(item.slaDueAt()),
                toTimestamp(item.claimedAt()), toTimestamp(item.completedAt()),
                item.version(), Timestamp.from(item.createdAt()), Timestamp.from(item.updatedAt())
        );
        return item;
    }

    @Override
    public Optional<WorkflowWorkItem> findById(UUID tenantId, UUID id) {
        return jdbc.query("""
                SELECT * FROM workflow_work_items WHERE tenant_id = ? AND id = ?
                """, ITEM_MAPPER, tenantId, id).stream().findFirst();
    }

    @Override
    public int claimAvailableItem(UUID tenantId, UUID workItemId, UUID employeeId, long expectedVersion) {
        return jdbc.update("""
                UPDATE workflow_work_items
                SET status = 'CLAIMED', claimed_by_employee_id = ?, claimed_at = NOW(),
                    version = version + 1, updated_at = NOW()
                WHERE tenant_id = ? AND id = ? AND version = ? AND status = 'AVAILABLE'
                  AND EXISTS (
                      SELECT 1 FROM workflow_work_item_candidates c
                      WHERE c.work_item_id = workflow_work_items.id AND c.employee_id = ?
                  )
                """, employeeId, tenantId, workItemId, expectedVersion, employeeId);
    }

    @Override
    public int releaseClaimedItem(UUID tenantId, UUID workItemId, UUID employeeId, long expectedVersion) {
        return jdbc.update("""
                UPDATE workflow_work_items
                SET status = 'AVAILABLE', claimed_by_employee_id = NULL, claimed_at = NULL,
                    version = version + 1, updated_at = NOW()
                WHERE tenant_id = ? AND id = ? AND version = ? AND status = 'CLAIMED'
                  AND claimed_by_employee_id = ?
                """, tenantId, workItemId, expectedVersion, employeeId);
    }

    @Override
    public int completeClaimedItem(UUID tenantId, UUID workItemId, UUID employeeId, long expectedVersion) {
        return jdbc.update("""
                UPDATE workflow_work_items
                SET status = 'COMPLETED', completed_at = NOW(),
                    version = version + 1, updated_at = NOW()
                WHERE tenant_id = ? AND id = ? AND version = ? AND status = 'CLAIMED'
                  AND claimed_by_employee_id = ?
                """, tenantId, workItemId, expectedVersion, employeeId);
    }

    @Override
    public int reassignItem(UUID tenantId, UUID workItemId, UUID newAssigneeEmployeeId, long expectedVersion) {
        return jdbc.update("""
                UPDATE workflow_work_items
                SET assignee_employee_id = ?, claimed_by_employee_id = NULL,
                    claimed_at = NULL, status = 'CLAIMED',
                    version = version + 1, updated_at = NOW()
                WHERE tenant_id = ? AND id = ? AND version = ?
                  AND status IN ('AVAILABLE', 'CLAIMED', 'ASSIGNEE_UNAVAILABLE')
                  AND EXISTS (
                      SELECT 1 FROM hr_employees e
                      WHERE e.tenant_id = workflow_work_items.tenant_id AND e.id = ?
                  )
                """, newAssigneeEmployeeId, tenantId, workItemId, expectedVersion, newAssigneeEmployeeId);
    }

    @Override
    public List<WorkflowWorkItem> findMyWork(UUID tenantId, UUID employeeId, int limit) {
        return jdbc.query("""
                SELECT * FROM workflow_work_items
                WHERE tenant_id = ?
                  AND (assignee_employee_id = ? OR claimed_by_employee_id = ?)
                  AND status NOT IN ('COMPLETED', 'CANCELLED', 'EXPIRED')
                ORDER BY priority DESC, due_at NULLS LAST, created_at ASC
                LIMIT ?
                """, ITEM_MAPPER, tenantId, employeeId, employeeId, limit);
    }

    @Override
    public List<WorkflowWorkItem> findPoolWork(UUID tenantId, UUID employeeId, int limit) {
        return jdbc.query("""
                SELECT wi.* FROM workflow_work_items wi
                WHERE wi.tenant_id = ? AND wi.status = 'AVAILABLE'
                  AND EXISTS (
                      SELECT 1 FROM workflow_work_item_candidates c
                      WHERE c.work_item_id = wi.id AND c.employee_id = ?
                  )
                ORDER BY wi.priority DESC, wi.due_at NULLS LAST, wi.created_at ASC
                LIMIT ?
                """, ITEM_MAPPER, tenantId, employeeId, limit);
    }

    @Override
    public void insertCandidates(UUID workItemId, List<WorkflowWorkItemCandidate> candidates) {
        jdbc.batchUpdate("""
                INSERT INTO workflow_work_item_candidates
                    (tenant_id, work_item_id, employee_id, resolution_source, resolved_at, snapshot_metadata)
                VALUES (?, ?, ?, ?, ?, CAST(? AS jsonb))
                ON CONFLICT (work_item_id, employee_id) DO NOTHING
                """,
                candidates,
                candidates.size(),
                (java.sql.PreparedStatement ps, WorkflowWorkItemCandidate candidate) -> {
                    ps.setObject(1, candidate.tenantId());
                    ps.setObject(2, candidate.workItemId());
                    ps.setObject(3, candidate.employeeId());
                    ps.setString(4, candidate.resolutionSource());
                    ps.setTimestamp(5, Timestamp.from(candidate.resolvedAt()));
                    ps.setString(6, candidate.snapshotMetadata() != null ? candidate.snapshotMetadata() : "{}");
                });
    }

    @Override
    public List<WorkflowWorkItemCandidate> findCandidates(UUID tenantId, UUID workItemId) {
        return jdbc.query("""
                SELECT * FROM workflow_work_item_candidates
                WHERE tenant_id = ? AND work_item_id = ?
                ORDER BY resolved_at ASC
                """, (rs, rowNum) -> new WorkflowWorkItemCandidate(
                        rs.getObject("tenant_id", UUID.class),
                        rs.getObject("work_item_id", UUID.class),
                        rs.getObject("employee_id", UUID.class),
                        rs.getString("resolution_source"),
                        rs.getTimestamp("resolved_at").toInstant(),
                        rs.getString("snapshot_metadata")),
                tenantId, workItemId);
    }

    @Override
    public boolean isCandidate(UUID tenantId, UUID workItemId, UUID employeeId) {
        var count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM workflow_work_item_candidates
                WHERE tenant_id = ? AND work_item_id = ? AND employee_id = ?
                """, Long.class, tenantId, workItemId, employeeId);
        return count != null && count > 0;
    }

    private static Timestamp toTimestamp(Instant instant) {
        return instant != null ? Timestamp.from(instant) : null;
    }
}
