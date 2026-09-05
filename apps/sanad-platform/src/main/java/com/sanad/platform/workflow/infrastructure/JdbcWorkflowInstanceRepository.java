package com.sanad.platform.workflow.infrastructure;

import com.sanad.platform.workflow.domain.WorkflowInstance;
import com.sanad.platform.workflow.domain.WorkflowInstanceRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JdbcTemplate implementation of {@link WorkflowInstanceRepository}.
 *
 * <p>Optimistic locking is enforced via the {@code version} column. Tenant
 * isolation is enforced by including {@code tenant_id = ?} in every query.
 */
@Repository
public class JdbcWorkflowInstanceRepository implements WorkflowInstanceRepository {

    private final JdbcTemplate jdbc;

    public JdbcWorkflowInstanceRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final RowMapper<WorkflowInstance> MAPPER = (rs, rowNum) -> new WorkflowInstance(
            rs.getObject("id", UUID.class),
            rs.getObject("tenant_id", UUID.class),
            rs.getObject("workflow_definition_id", UUID.class),
            rs.getInt("workflow_version"),
            rs.getString("business_entity_type"),
            rs.getObject("business_entity_id", UUID.class),
            WorkflowInstance.Status.valueOf(rs.getString("status")),
            rs.getString("current_step_key"),
            rs.getObject("started_by", UUID.class),
            rs.getTimestamp("started_at").toInstant(),
            rs.getTimestamp("completed_at") != null ? rs.getTimestamp("completed_at").toInstant() : null,
            rs.getTimestamp("cancelled_at") != null ? rs.getTimestamp("cancelled_at").toInstant() : null,
            rs.getObject("cancelled_by", UUID.class),
            rs.getString("cancel_reason"),
            rs.getObject("correlation_id", UUID.class),
            rs.getLong("version"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant()
    );

    @Override
    public WorkflowInstance save(WorkflowInstance instance) {
        if (instance.version() == 0) {
            return insert(instance);
        }
        return update(instance);
    }

    private WorkflowInstance insert(WorkflowInstance i) {
        jdbc.update("""
                INSERT INTO workflow_instances
                    (id, tenant_id, workflow_definition_id, workflow_version, business_entity_type,
                     business_entity_id, status, current_step_key, started_by, started_at,
                     completed_at, cancelled_at, cancelled_by, cancel_reason, correlation_id,
                     version, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                i.id(), i.tenantId(), i.workflowDefinitionId(), i.workflowVersion(),
                i.businessEntityType(), i.businessEntityId(),
                i.status().name(), i.currentStepKey(),
                i.startedBy(),
                Timestamp.from(i.startedAt()),
                i.completedAt() != null ? Timestamp.from(i.completedAt()) : null,
                i.cancelledAt() != null ? Timestamp.from(i.cancelledAt()) : null,
                i.cancelledBy(),
                i.cancelReason(),
                i.correlationId(),
                i.version(),
                Timestamp.from(i.createdAt()), Timestamp.from(i.updatedAt())
        );
        return i;
    }

    private WorkflowInstance update(WorkflowInstance i) {
        int affected = jdbc.update("""
                UPDATE workflow_instances SET
                    status = ?, current_step_key = ?, completed_at = ?, cancelled_at = ?,
                    cancelled_by = ?, cancel_reason = ?, version = ?, updated_at = ?
                WHERE id = ? AND tenant_id = ? AND version = ?
                """,
                i.status().name(), i.currentStepKey(),
                i.completedAt() != null ? Timestamp.from(i.completedAt()) : null,
                i.cancelledAt() != null ? Timestamp.from(i.cancelledAt()) : null,
                i.cancelledBy(),
                i.cancelReason(),
                i.version(), Timestamp.from(i.updatedAt()),
                i.id(), i.tenantId(), i.version() - 1
        );
        if (affected == 0) {
            throw new org.springframework.dao.OptimisticLockingFailureException(
                    "WorkflowInstance " + i.id() + " was modified by another transaction");
        }
        return i;
    }

    @Override
    public Optional<WorkflowInstance> findById(UUID tenantId, UUID id) {
        return jdbc.query("""
                SELECT * FROM workflow_instances WHERE tenant_id = ? AND id = ?
                """, MAPPER, tenantId, id).stream().findFirst();
    }

    @Override
    public List<WorkflowInstance> findByTenant(UUID tenantId, int limit) {
        return jdbc.query("""
                SELECT * FROM workflow_instances WHERE tenant_id = ?
                ORDER BY updated_at DESC LIMIT ?
                """, MAPPER, tenantId, limit);
    }

    @Override
    public List<WorkflowInstance> findByTenantAndStatus(
            UUID tenantId, WorkflowInstance.Status status, int limit) {
        return jdbc.query("""
                SELECT * FROM workflow_instances
                WHERE tenant_id = ? AND status = ?
                ORDER BY updated_at DESC LIMIT ?
                """, MAPPER, tenantId, status.name(), limit);
    }

    @Override
    public List<WorkflowInstance> findByBusinessEntity(
            UUID tenantId, String entityType, UUID entityId) {
        return jdbc.query("""
                SELECT * FROM workflow_instances
                WHERE tenant_id = ? AND business_entity_type = ? AND business_entity_id = ?
                ORDER BY created_at DESC
                """, MAPPER, tenantId, entityType, entityId);
    }
}
