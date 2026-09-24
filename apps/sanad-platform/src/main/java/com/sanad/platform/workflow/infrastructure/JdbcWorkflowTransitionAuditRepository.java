package com.sanad.platform.workflow.infrastructure;

import com.sanad.platform.workflow.domain.WorkflowTransitionAudit;
import com.sanad.platform.workflow.domain.WorkflowTransitionAuditRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

/**
 * JdbcTemplate implementation of {@link WorkflowTransitionAuditRepository}.
 *
 * <p>This table is <strong>append-only</strong>: {@link #save(WorkflowTransitionAudit)}
 * always performs an INSERT, never an UPDATE or DELETE. The audit trail is an
 * immutable provenance record of every workflow state change.
 *
 * <p>The {@code actor_user_id} column is nullable to support system-generated
 * events (e.g. SLA expiry, scheduled transitions). The {@code metadata} column
 * is JSONB and is written via {@code CAST(? AS jsonb)}.
 */
@Repository
public class JdbcWorkflowTransitionAuditRepository implements WorkflowTransitionAuditRepository {

    private final JdbcTemplate jdbc;

    public JdbcWorkflowTransitionAuditRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final RowMapper<WorkflowTransitionAudit> MAPPER = (rs, rowNum) -> new WorkflowTransitionAudit(
            rs.getObject("id", UUID.class),
            rs.getObject("tenant_id", UUID.class),
            rs.getObject("workflow_instance_id", UUID.class),
            rs.getObject("workflow_step_instance_id", UUID.class),
            rs.getObject("actor_user_id", UUID.class),
            rs.getString("action"),
            rs.getString("from_state"),
            rs.getString("to_state"),
            rs.getObject("correlation_id", UUID.class),
            rs.getString("metadata"),
            rs.getTimestamp("created_at").toInstant()
    );

    @Override
    public WorkflowTransitionAudit save(WorkflowTransitionAudit audit) {
        // Append-only: always INSERT. No UPDATE.
        jdbc.update("""
                INSERT INTO workflow_transition_audit
                    (id, tenant_id, workflow_instance_id, workflow_step_instance_id,
                     actor_user_id, action, from_state, to_state, correlation_id,
                     metadata, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), ?)
                """,
                audit.id(), audit.tenantId(),
                audit.workflowInstanceId(),
                audit.workflowStepInstanceId(),
                audit.actorUserId(),
                audit.action(),
                audit.fromState(),
                audit.toState(),
                audit.correlationId(),
                audit.metadata() != null ? audit.metadata() : "{}",
                Timestamp.from(audit.createdAt())
        );
        return audit;
    }

    @Override
    public List<WorkflowTransitionAudit> findByInstance(UUID tenantId, UUID workflowInstanceId) {
        return jdbc.query("""
                SELECT * FROM workflow_transition_audit
                WHERE tenant_id = ? AND workflow_instance_id = ?
                ORDER BY created_at ASC
                """, MAPPER, tenantId, workflowInstanceId);
    }

    @Override
    public List<WorkflowTransitionAudit> findByTenant(UUID tenantId, int limit) {
        return jdbc.query("""
                SELECT * FROM workflow_transition_audit
                WHERE tenant_id = ?
                ORDER BY created_at DESC LIMIT ?
                """, MAPPER, tenantId, limit);
    }
}
