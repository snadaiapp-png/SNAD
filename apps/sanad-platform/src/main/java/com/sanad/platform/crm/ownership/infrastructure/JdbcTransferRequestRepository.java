package com.sanad.platform.crm.ownership.infrastructure;

import com.sanad.platform.crm.ownership.domain.*;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static com.sanad.platform.crm.ownership.infrastructure.OwnershipJdbcSupport.*;

@Repository
public class JdbcTransferRequestRepository implements TransferRequestRepository {
    private final NamedParameterJdbcTemplate jdbc;
    public JdbcTransferRequestRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override @Transactional
    public TransferRequest save(TransferRequest r) {
        UUID id = r.id() != null ? r.id() : UUID.randomUUID();
        jdbc.update("""
            INSERT INTO crm_transfer_requests
              (id, tenant_id, record_type, record_ids, requester_user_id, current_owner_user_id,
               proposed_owner_user_id, proposed_owner_team_id, transfer_type, temporary_end_date,
               reason, policy, state, current_approval_step, workflow_run_id,
               executed_at, executed_by_user_id, failure_reason, created_at, updated_at)
            VALUES
              (:id, :tenantId, :recordType, :recordIds, :requesterUserId, :currentOwnerUserId,
               :proposedOwnerUserId, :proposedOwnerTeamId, :transferType, :temporaryEndDate,
               :reason, :policy, :state, :currentApprovalStep, :workflowRunId,
               :executedAt, :executedByUserId, :failureReason, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            """, new MapSqlParameterSource()
                .addValue("id", id).addValue("tenantId", r.tenantId())
                .addValue("recordType", r.recordType().name()).addValue("recordIds", toUuidArrayJson(r.recordIds()))
                .addValue("requesterUserId", r.requesterUserId()).addValue("currentOwnerUserId", r.currentOwnerUserId())
                .addValue("proposedOwnerUserId", r.proposedOwnerUserId()).addValue("proposedOwnerTeamId", r.proposedOwnerTeamId())
                .addValue("transferType", r.transferType().name()).addValue("temporaryEndDate", r.temporaryEndDate())
                .addValue("reason", r.reason()).addValue("policy", r.policy().name()).addValue("state", r.state().name())
                .addValue("currentApprovalStep", r.currentApprovalStep()).addValue("workflowRunId", r.workflowRunId())
                .addValue("executedAt", r.executedAt()).addValue("executedByUserId", r.executedByUserId())
                .addValue("failureReason", r.failureReason()));
        return findById(r.tenantId(), id).orElseThrow();
    }

    @Override
    public Optional<TransferRequest> findById(UUID tenantId, UUID transferId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject("SELECT * FROM crm_transfer_requests WHERE tenant_id=:tenantId AND id=:id",
                new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("id", transferId), transferRequestMapper()));
        } catch (EmptyResultDataAccessException e) { return Optional.empty(); }
    }

    @Override
    public List<TransferRequest> findByRequester(UUID tenantId, UUID requesterUserId) {
        return jdbc.query("SELECT * FROM crm_transfer_requests WHERE tenant_id=:tenantId AND requester_user_id=:userId ORDER BY created_at DESC",
            new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("userId", requesterUserId), transferRequestMapper());
    }

    @Override
    public List<TransferRequest> findByState(UUID tenantId, TransferState state) {
        return jdbc.query("SELECT * FROM crm_transfer_requests WHERE tenant_id=:tenantId AND state=:state ORDER BY updated_at DESC",
            new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("state", state.name()), transferRequestMapper());
    }

    @Override
    public List<TransferRequest> findByProposedOwner(UUID tenantId, UUID proposedOwnerUserId) {
        return jdbc.query("SELECT * FROM crm_transfer_requests WHERE tenant_id=:tenantId AND proposed_owner_user_id=:userId AND state IN ('SUBMITTED','UNDER_REVIEW') ORDER BY created_at DESC",
            new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("userId", proposedOwnerUserId), transferRequestMapper());
    }

    @Override @Transactional
    public TransferStep addStep(UUID tenantId, UUID transferRequestId, int stepNumber, UUID approverUserId) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO crm_transfer_steps
              (id, tenant_id, transfer_request_id, step_number, approver_user_id, created_at)
            VALUES
              (:id, :tenantId, :transferRequestId, :stepNumber, :approverUserId, CURRENT_TIMESTAMP)
            """, new MapSqlParameterSource().addValue("id", id).addValue("tenantId", tenantId)
                .addValue("transferRequestId", transferRequestId).addValue("stepNumber", stepNumber)
                .addValue("approverUserId", approverUserId));
        return findStep(tenantId, transferRequestId, stepNumber).orElseThrow();
    }

    @Override
    public Optional<TransferStep> findStep(UUID tenantId, UUID transferRequestId, int stepNumber) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                "SELECT * FROM crm_transfer_steps WHERE tenant_id=:tenantId AND transfer_request_id=:transferId AND step_number=:stepNumber",
                new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("transferId", transferRequestId).addValue("stepNumber", stepNumber),
                transferStepMapper()));
        } catch (EmptyResultDataAccessException e) { return Optional.empty(); }
    }

    @Override
    public List<TransferStep> findSteps(UUID tenantId, UUID transferRequestId) {
        return jdbc.query("SELECT * FROM crm_transfer_steps WHERE tenant_id=:tenantId AND transfer_request_id=:transferId ORDER BY step_number",
            new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("transferId", transferRequestId), transferStepMapper());
    }

    @Override @Transactional
    public void decideStep(UUID tenantId, UUID transferRequestId, int stepNumber,
                           TransferStepDecision decision, UUID approverUserId, String comment) {
        int rows = jdbc.update("""
            UPDATE crm_transfer_steps SET decision=:decision, decided_at=CURRENT_TIMESTAMP, comment=:comment
            WHERE tenant_id=:tenantId AND transfer_request_id=:transferId AND step_number=:stepNumber
              AND approver_user_id=:approverUserId AND decision IS NULL
            """, new MapSqlParameterSource().addValue("decision", decision.name()).addValue("comment", comment)
                .addValue("tenantId", tenantId).addValue("transferId", transferRequestId).addValue("stepNumber", stepNumber)
                .addValue("approverUserId", approverUserId));
        if (rows == 0) throw new UnauthorizedTransferApproverException(tenantId, transferRequestId, approverUserId);
    }

    @Override @Transactional
    public void updateState(UUID tenantId, UUID transferRequestId, TransferState newState,
                            UUID executedByUserId, String failureReason) {
        int rows = jdbc.update("""
            UPDATE crm_transfer_requests SET state=:state, executed_at=CASE WHEN :state IN ('COMPLETED','FAILED') THEN CURRENT_TIMESTAMP ELSE executed_at END,
              executed_by_user_id=CASE WHEN :state IN ('COMPLETED','FAILED') THEN :executedBy ELSE executed_by_user_id END,
              failure_reason=:failureReason, updated_at=CURRENT_TIMESTAMP
            WHERE tenant_id=:tenantId AND id=:id
            """, new MapSqlParameterSource().addValue("state", newState.name()).addValue("executedBy", executedByUserId)
                .addValue("failureReason", failureReason).addValue("tenantId", tenantId).addValue("id", transferRequestId));
        if (rows == 0) throw new OwnershipDomainException("Transfer not found: " + transferRequestId);
    }
}
