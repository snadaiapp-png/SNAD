package com.sanad.platform.workflow.infrastructure;

import com.sanad.platform.workflow.domain.WorkflowApprovalRequest;
import com.sanad.platform.workflow.domain.WorkflowApprovalRequestRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JdbcTemplate implementation of {@link WorkflowApprovalRequestRepository}.
 *
 * <p>Optimistic locking is enforced via the {@code version} column. Tenant
 * isolation is enforced by including {@code tenant_id = ?} in every query.
 *
 * <p>Segregation of duties is enforced by the {@link WorkflowApprovalRequest}
 * domain model itself (the {@code approve()} and {@code reject()} methods
 * reject same-actor resolutions); this repository does not duplicate that rule.
 */
@Repository
public class JdbcWorkflowApprovalRequestRepository implements WorkflowApprovalRequestRepository {

    private final JdbcTemplate jdbc;

    public JdbcWorkflowApprovalRequestRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final RowMapper<WorkflowApprovalRequest> MAPPER = (rs, rowNum) -> new WorkflowApprovalRequest(
            rs.getObject("id", UUID.class),
            rs.getObject("tenant_id", UUID.class),
            rs.getObject("workflow_instance_id", UUID.class),
            rs.getObject("workflow_step_instance_id", UUID.class),
            rs.getObject("requested_from_user_id", UUID.class),
            rs.getString("requested_from_role"),
            rs.getObject("requested_by_user_id", UUID.class),
            WorkflowApprovalRequest.Status.valueOf(rs.getString("status")),
            rs.getTimestamp("requested_at").toInstant(),
            rs.getTimestamp("due_at") != null ? rs.getTimestamp("due_at").toInstant() : null,
            rs.getObject("acted_by", UUID.class),
            rs.getTimestamp("acted_at") != null ? rs.getTimestamp("acted_at").toInstant() : null,
            rs.getString("decision"),
            rs.getString("comments"),
            rs.getLong("version"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant()
    );

    @Override
    public WorkflowApprovalRequest save(WorkflowApprovalRequest req) {
        if (req.version() == 0) {
            return insert(req);
        }
        return update(req);
    }

    private WorkflowApprovalRequest insert(WorkflowApprovalRequest r) {
        jdbc.update("""
                INSERT INTO workflow_approval_requests
                    (id, tenant_id, workflow_instance_id, workflow_step_instance_id,
                     requested_from_user_id, requested_from_role, requested_by_user_id,
                     status, requested_at, due_at, acted_by, acted_at, decision, comments,
                     version, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                r.id(), r.tenantId(), r.workflowInstanceId(), r.workflowStepInstanceId(),
                r.requestedFromUserId(), r.requestedFromRole(), r.requestedByUserId(),
                r.status().name(),
                Timestamp.from(r.requestedAt()),
                r.dueAt() != null ? Timestamp.from(r.dueAt()) : null,
                r.actedBy(),
                r.actedAt() != null ? Timestamp.from(r.actedAt()) : null,
                r.decision(),
                r.comments(),
                r.version(),
                Timestamp.from(r.createdAt()), Timestamp.from(r.updatedAt())
        );
        return r;
    }

    private WorkflowApprovalRequest update(WorkflowApprovalRequest r) {
        int affected = jdbc.update("""
                UPDATE workflow_approval_requests SET
                    status = ?, acted_by = ?, acted_at = ?, decision = ?, comments = ?,
                    version = ?, updated_at = ?
                WHERE id = ? AND tenant_id = ? AND version = ?
                """,
                r.status().name(),
                r.actedBy(),
                r.actedAt() != null ? Timestamp.from(r.actedAt()) : null,
                r.decision(),
                r.comments(),
                r.version(), Timestamp.from(r.updatedAt()),
                r.id(), r.tenantId(), r.version() - 1
        );
        if (affected == 0) {
            throw new org.springframework.dao.OptimisticLockingFailureException(
                    "WorkflowApprovalRequest " + r.id() + " was modified by another transaction");
        }
        return r;
    }

    @Override
    public Optional<WorkflowApprovalRequest> findById(UUID tenantId, UUID id) {
        return jdbc.query("""
                SELECT * FROM workflow_approval_requests WHERE tenant_id = ? AND id = ?
                """, MAPPER, tenantId, id).stream().findFirst();
    }

    @Override
    public List<WorkflowApprovalRequest> findByTenant(UUID tenantId, int limit) {
        return jdbc.query("""
                SELECT * FROM workflow_approval_requests WHERE tenant_id = ?
                ORDER BY updated_at DESC LIMIT ?
                """, MAPPER, tenantId, limit);
    }

    @Override
    public List<WorkflowApprovalRequest> findByTenantAndStatus(
            UUID tenantId, WorkflowApprovalRequest.Status status, int limit) {
        return jdbc.query("""
                SELECT * FROM workflow_approval_requests
                WHERE tenant_id = ? AND status = ?
                ORDER BY requested_at ASC LIMIT ?
                """, MAPPER, tenantId, status.name(), limit);
    }

    @Override
    public List<WorkflowApprovalRequest> findByInstance(UUID tenantId, UUID workflowInstanceId) {
        return jdbc.query("""
                SELECT * FROM workflow_approval_requests
                WHERE tenant_id = ? AND workflow_instance_id = ?
                ORDER BY requested_at ASC
                """, MAPPER, tenantId, workflowInstanceId);
    }

    @Override
    public List<WorkflowApprovalRequest> findByUser(UUID tenantId, UUID userId, int limit) {
        return jdbc.query("""
                SELECT * FROM workflow_approval_requests
                WHERE tenant_id = ? AND requested_from_user_id = ?
                ORDER BY requested_at DESC LIMIT ?
                """, MAPPER, tenantId, userId, limit);
    }
}
