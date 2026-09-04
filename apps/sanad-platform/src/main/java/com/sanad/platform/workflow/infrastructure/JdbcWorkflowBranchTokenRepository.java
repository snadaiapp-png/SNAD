package com.sanad.platform.workflow.infrastructure;

import com.sanad.platform.workflow.domain.WorkflowBranchToken;
import com.sanad.platform.workflow.domain.WorkflowBranchTokenRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcWorkflowBranchTokenRepository implements WorkflowBranchTokenRepository {

    private final JdbcTemplate jdbc;

    public JdbcWorkflowBranchTokenRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final RowMapper<WorkflowBranchToken> MAPPER = (rs, rowNum) -> new WorkflowBranchToken(
            rs.getObject("id", UUID.class),
            rs.getObject("tenant_id", UUID.class),
            rs.getObject("workflow_instance_id", UUID.class),
            rs.getObject("fork_step_instance_id", UUID.class),
            rs.getString("branch_key"),
            WorkflowBranchToken.Status.valueOf(rs.getString("status")),
            rs.getObject("join_step_id", UUID.class),
            rs.getLong("version"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant()
    );

    @Override
    public WorkflowBranchToken insert(WorkflowBranchToken token) {
        jdbc.update("""
                INSERT INTO workflow_branch_tokens (
                    id, tenant_id, workflow_instance_id, fork_step_instance_id, branch_key,
                    status, join_step_id, version, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, token.id(), token.tenantId(), token.workflowInstanceId(),
                token.forkStepInstanceId(), token.branchKey(), token.status().name(),
                token.joinStepId(), token.version(),
                Timestamp.from(token.createdAt()), Timestamp.from(token.updatedAt()));
        return token;
    }

    @Override
    public WorkflowBranchToken save(WorkflowBranchToken token) {
        int affected = jdbc.update("""
                UPDATE workflow_branch_tokens SET status = ?, version = version + 1, updated_at = ?
                WHERE id = ? AND tenant_id = ? AND version = ?
                """, token.status().name(), Timestamp.from(token.updatedAt()),
                token.id(), token.tenantId(), token.version());
        if (affected == 0) {
            throw new org.springframework.dao.OptimisticLockingFailureException(
                    "WorkflowBranchToken " + token.id() + " was modified by another transaction");
        }
        return token;
    }

    @Override
    public Optional<WorkflowBranchToken> findById(UUID tenantId, UUID id) {
        return jdbc.query("SELECT * FROM workflow_branch_tokens WHERE tenant_id = ? AND id = ?",
                MAPPER, tenantId, id).stream().findFirst();
    }

    @Override
    public List<WorkflowBranchToken> findByFork(UUID tenantId, UUID workflowInstanceId, UUID forkStepInstanceId) {
        return jdbc.query("""
                SELECT * FROM workflow_branch_tokens
                WHERE tenant_id = ? AND workflow_instance_id = ? AND fork_step_instance_id = ?
                ORDER BY created_at ASC
                """, MAPPER, tenantId, workflowInstanceId, forkStepInstanceId);
    }

    @Override
    public List<WorkflowBranchToken> findByJoin(UUID tenantId, UUID workflowInstanceId, UUID joinStepId) {
        return jdbc.query("""
                SELECT * FROM workflow_branch_tokens
                WHERE tenant_id = ? AND workflow_instance_id = ? AND join_step_id = ?
                ORDER BY created_at ASC
                """, MAPPER, tenantId, workflowInstanceId, joinStepId);
    }
}
