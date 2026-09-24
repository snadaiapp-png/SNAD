package com.sanad.platform.workflow.infrastructure;

import com.sanad.platform.workflow.domain.WorkflowStepInstance;
import com.sanad.platform.workflow.domain.WorkflowStepInstanceRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

/**
 * JdbcTemplate implementation of {@link WorkflowStepInstanceRepository}.
 *
 * <p>Optimistic locking is enforced via the {@code version} column.
 * Reads by {@code workflow_instance_id} are NOT tenant-scoped at the SQL level
 * because the instance_id is globally unique (it is a UUID primary key on
 * {@code workflow_instances}); reads by status are tenant-scoped.
 */
@Repository
public class JdbcWorkflowStepInstanceRepository implements WorkflowStepInstanceRepository {

    private final JdbcTemplate jdbc;

    public JdbcWorkflowStepInstanceRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final RowMapper<WorkflowStepInstance> MAPPER = (rs, rowNum) -> new WorkflowStepInstance(
            rs.getObject("id", UUID.class),
            rs.getObject("tenant_id", UUID.class),
            rs.getObject("workflow_instance_id", UUID.class),
            rs.getObject("workflow_step_id", UUID.class),
            rs.getString("step_key"),
            WorkflowStepInstance.Status.valueOf(rs.getString("status")),
            rs.getObject("assigned_user_id", UUID.class),
            rs.getString("assigned_role"),
            rs.getTimestamp("started_at") != null ? rs.getTimestamp("started_at").toInstant() : null,
            rs.getTimestamp("completed_at") != null ? rs.getTimestamp("completed_at").toInstant() : null,
            rs.getTimestamp("due_at") != null ? rs.getTimestamp("due_at").toInstant() : null,
            rs.getInt("attempt_count"),
            rs.getString("result"),
            rs.getLong("version"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant()
    );

    @Override
    public WorkflowStepInstance save(WorkflowStepInstance stepInstance) {
        if (stepInstance.version() == 0) {
            return insert(stepInstance);
        }
        return update(stepInstance);
    }

    private WorkflowStepInstance insert(WorkflowStepInstance s) {
        jdbc.update("""
                INSERT INTO workflow_step_instances
                    (id, tenant_id, workflow_instance_id, workflow_step_id, step_key, status,
                     assigned_user_id, assigned_role, started_at, completed_at, due_at,
                     attempt_count, result, version, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                s.id(), s.tenantId(), s.workflowInstanceId(), s.workflowStepId(),
                s.stepKey(), s.status().name(),
                s.assignedUserId(), s.assignedRole(),
                s.startedAt() != null ? Timestamp.from(s.startedAt()) : null,
                s.completedAt() != null ? Timestamp.from(s.completedAt()) : null,
                s.dueAt() != null ? Timestamp.from(s.dueAt()) : null,
                s.attemptCount(),
                s.result(),
                s.version(),
                Timestamp.from(s.createdAt()), Timestamp.from(s.updatedAt())
        );
        return s;
    }

    private WorkflowStepInstance update(WorkflowStepInstance s) {
        int affected = jdbc.update("""
                UPDATE workflow_step_instances SET
                    status = ?, assigned_user_id = ?, assigned_role = ?, started_at = ?,
                    completed_at = ?, due_at = ?, attempt_count = ?, result = ?,
                    version = ?, updated_at = ?
                WHERE id = ? AND tenant_id = ? AND version = ?
                """,
                s.status().name(),
                s.assignedUserId(), s.assignedRole(),
                s.startedAt() != null ? Timestamp.from(s.startedAt()) : null,
                s.completedAt() != null ? Timestamp.from(s.completedAt()) : null,
                s.dueAt() != null ? Timestamp.from(s.dueAt()) : null,
                s.attemptCount(),
                s.result(),
                s.version(), Timestamp.from(s.updatedAt()),
                s.id(), s.tenantId(), s.version() - 1
        );
        if (affected == 0) {
            throw new org.springframework.dao.OptimisticLockingFailureException(
                    "WorkflowStepInstance " + s.id() + " was modified by another transaction");
        }
        return s;
    }

    @Override
    public List<WorkflowStepInstance> findByInstance(UUID workflowInstanceId) {
        return jdbc.query("""
                SELECT * FROM workflow_step_instances
                WHERE workflow_instance_id = ?
                ORDER BY created_at ASC
                """, MAPPER, workflowInstanceId);
    }

    @Override
    public List<WorkflowStepInstance> findByTenantAndStatus(
            UUID tenantId, WorkflowStepInstance.Status status, int limit) {
        return jdbc.query("""
                SELECT * FROM workflow_step_instances
                WHERE tenant_id = ? AND status = ?
                ORDER BY updated_at DESC LIMIT ?
                """, MAPPER, tenantId, status.name(), limit);
    }
}
