package com.sanad.platform.crm.ownership.infrastructure;

import com.sanad.platform.crm.ownership.domain.OwnershipDomainException;
import com.sanad.platform.crm.ownership.domain.TransferRequest;
import com.sanad.platform.crm.ownership.domain.TransferRequestRepository;
import com.sanad.platform.crm.ownership.domain.TransferState;
import com.sanad.platform.crm.ownership.domain.TransferStateConflictException;
import com.sanad.platform.crm.ownership.domain.TransferStep;
import com.sanad.platform.crm.ownership.domain.TransferStepDecision;
import com.sanad.platform.crm.ownership.domain.UnauthorizedTransferApproverException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.sanad.platform.crm.ownership.infrastructure.OwnershipJdbcSupport.toUuidArrayJson;
import static com.sanad.platform.crm.ownership.infrastructure.OwnershipJdbcSupport.transferRequestMapper;
import static com.sanad.platform.crm.ownership.infrastructure.OwnershipJdbcSupport.transferStepMapper;

@Repository
public class JdbcTransferRequestRepository implements TransferRequestRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public JdbcTransferRequestRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public TransferRequest save(TransferRequest request) {
        UUID id = request.id() != null ? request.id() : UUID.randomUUID();
        jdbc.update("""
                INSERT INTO crm_transfer_requests
                  (id, tenant_id, record_type, record_ids,
                   requester_user_id, current_owner_user_id,
                   proposed_owner_user_id, proposed_owner_team_id,
                   transfer_type, temporary_end_date, reason, policy, state,
                   current_approval_step, workflow_run_id,
                   executed_at, executed_by_user_id, failure_reason,
                   created_at, updated_at)
                VALUES
                  (:id, :tenantId, :recordType, CAST(:recordIds AS jsonb),
                   :requesterUserId, :currentOwnerUserId,
                   :proposedOwnerUserId, :proposedOwnerTeamId,
                   :transferType, :temporaryEndDate, :reason, :policy, :state,
                   :currentApprovalStep, :workflowRunId,
                   :executedAt, :executedByUserId, :failureReason,
                   CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("tenantId", request.tenantId())
                .addValue("recordType", request.recordType().name())
                .addValue("recordIds", toUuidArrayJson(request.recordIds()))
                .addValue("requesterUserId", request.requesterUserId())
                .addValue("currentOwnerUserId", request.currentOwnerUserId())
                .addValue("proposedOwnerUserId", request.proposedOwnerUserId())
                .addValue("proposedOwnerTeamId", request.proposedOwnerTeamId())
                .addValue("transferType", request.transferType().name())
                .addValue("temporaryEndDate", request.temporaryEndDate() != null
                        ? Timestamp.from(request.temporaryEndDate()) : null)
                .addValue("reason", request.reason())
                .addValue("policy", request.policy().name())
                .addValue("state", request.state().name())
                .addValue("currentApprovalStep", request.currentApprovalStep())
                .addValue("workflowRunId", request.workflowRunId())
                .addValue("executedAt", request.executedAt() != null
                        ? Timestamp.from(request.executedAt()) : null)
                .addValue("executedByUserId", request.executedByUserId())
                .addValue("failureReason", request.failureReason()));
        return findById(request.tenantId(), id).orElseThrow();
    }

    @Override
    public Optional<TransferRequest> findById(UUID tenantId, UUID transferId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject("""
                    SELECT * FROM crm_transfer_requests
                     WHERE tenant_id=:tenantId AND id=:id
                    """, new MapSqlParameterSource()
                    .addValue("tenantId", tenantId)
                    .addValue("id", transferId), transferRequestMapper()));
        } catch (EmptyResultDataAccessException missing) {
            return Optional.empty();
        }
    }

    @Override
    public List<TransferRequest> findByRequester(UUID tenantId, UUID requesterUserId) {
        return jdbc.query("""
                SELECT * FROM crm_transfer_requests
                 WHERE tenant_id=:tenantId AND requester_user_id=:userId
                 ORDER BY created_at DESC, id
                """, new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("userId", requesterUserId), transferRequestMapper());
    }

    @Override
    public List<TransferRequest> findByState(UUID tenantId, TransferState state) {
        return jdbc.query("""
                SELECT * FROM crm_transfer_requests
                 WHERE tenant_id=:tenantId AND state=:state
                 ORDER BY updated_at DESC, id
                """, new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("state", state.name()), transferRequestMapper());
    }

    @Override
    public List<TransferRequest> findByProposedOwner(UUID tenantId, UUID proposedOwnerUserId) {
        return jdbc.query("""
                SELECT * FROM crm_transfer_requests
                 WHERE tenant_id=:tenantId
                   AND proposed_owner_user_id=:userId
                   AND state IN ('SUBMITTED','UNDER_REVIEW')
                 ORDER BY created_at DESC, id
                """, new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("userId", proposedOwnerUserId), transferRequestMapper());
    }

    @Override
    @Transactional
    public TransferStep addStep(UUID tenantId,
                                UUID transferRequestId,
                                int stepNumber,
                                UUID approverUserId) {
        UUID requesterUserId = lockAndReadRequester(tenantId, transferRequestId);
        if (requesterUserId.equals(approverUserId)) {
            throw new UnauthorizedTransferApproverException(
                    tenantId, transferRequestId, approverUserId);
        }
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO crm_transfer_steps
                  (id, tenant_id, transfer_request_id, step_number,
                   approver_user_id, created_at)
                VALUES (:id, :tenantId, :transferRequestId, :stepNumber,
                        :approverUserId, CURRENT_TIMESTAMP)
                """, new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("tenantId", tenantId)
                .addValue("transferRequestId", transferRequestId)
                .addValue("stepNumber", stepNumber)
                .addValue("approverUserId", approverUserId));
        return findStep(tenantId, transferRequestId, stepNumber).orElseThrow();
    }

    @Override
    public Optional<TransferStep> findStep(UUID tenantId,
                                           UUID transferRequestId,
                                           int stepNumber) {
        try {
            return Optional.ofNullable(jdbc.queryForObject("""
                    SELECT * FROM crm_transfer_steps
                     WHERE tenant_id=:tenantId
                       AND transfer_request_id=:transferId
                       AND step_number=:stepNumber
                    """, new MapSqlParameterSource()
                    .addValue("tenantId", tenantId)
                    .addValue("transferId", transferRequestId)
                    .addValue("stepNumber", stepNumber), transferStepMapper()));
        } catch (EmptyResultDataAccessException missing) {
            return Optional.empty();
        }
    }

    @Override
    public List<TransferStep> findSteps(UUID tenantId, UUID transferRequestId) {
        return jdbc.query("""
                SELECT * FROM crm_transfer_steps
                 WHERE tenant_id=:tenantId AND transfer_request_id=:transferId
                 ORDER BY step_number, id
                """, new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("transferId", transferRequestId), transferStepMapper());
    }

    @Override
    @Transactional
    public void decideStep(UUID tenantId,
                           UUID transferRequestId,
                           int stepNumber,
                           TransferStepDecision decision,
                           UUID approverUserId,
                           String comment) {
        UUID requesterUserId = lockAndReadRequester(tenantId, transferRequestId);
        if (requesterUserId.equals(approverUserId)) {
            throw new UnauthorizedTransferApproverException(
                    tenantId, transferRequestId, approverUserId);
        }
        int rows = jdbc.update("""
                UPDATE crm_transfer_steps
                   SET decision=:decision,
                       decided_at=CURRENT_TIMESTAMP,
                       comment=:comment
                 WHERE tenant_id=:tenantId
                   AND transfer_request_id=:transferId
                   AND step_number=:stepNumber
                   AND approver_user_id=:approverUserId
                   AND decision IS NULL
                """, new MapSqlParameterSource()
                .addValue("decision", decision.name())
                .addValue("comment", comment)
                .addValue("tenantId", tenantId)
                .addValue("transferId", transferRequestId)
                .addValue("stepNumber", stepNumber)
                .addValue("approverUserId", approverUserId));
        if (rows != 1) {
            throw new UnauthorizedTransferApproverException(
                    tenantId, transferRequestId, approverUserId);
        }
    }

    @Override
    @Transactional
    public void setWorkflowReference(UUID tenantId,
                                     UUID transferRequestId,
                                     UUID workflowRunId,
                                     int currentApprovalStep) {
        int rows = jdbc.update("""
                UPDATE crm_transfer_requests
                   SET workflow_run_id=:workflowRunId,
                       current_approval_step=:currentStep,
                       updated_at=CURRENT_TIMESTAMP
                 WHERE tenant_id=:tenantId
                   AND id=:id
                   AND workflow_run_id IS NULL
                   AND state IN ('DRAFT','SUBMITTED','UNDER_REVIEW')
                """, new MapSqlParameterSource()
                .addValue("workflowRunId", workflowRunId)
                .addValue("currentStep", currentApprovalStep)
                .addValue("tenantId", tenantId)
                .addValue("id", transferRequestId));
        if (rows != 1) {
            throw new OwnershipDomainException(
                    "Transfer workflow reference already set or transfer is not reviewable: "
                            + transferRequestId);
        }
    }

    @Override
    @Transactional
    public void updateState(UUID tenantId,
                            UUID transferRequestId,
                            TransferState newState,
                            UUID executedByUserId,
                            String failureReason) {
        TransferState currentState;
        try {
            String stored = jdbc.queryForObject("""
                    SELECT state FROM crm_transfer_requests
                     WHERE tenant_id=:tenantId AND id=:id FOR UPDATE
                    """, new MapSqlParameterSource()
                    .addValue("tenantId", tenantId)
                    .addValue("id", transferRequestId), String.class);
            currentState = TransferState.valueOf(stored);
        } catch (EmptyResultDataAccessException missing) {
            throw new OwnershipDomainException("Transfer not found: " + transferRequestId);
        }

        if (!isAllowedTransition(currentState, newState)) {
            throw new TransferStateConflictException(
                    tenantId, transferRequestId, currentState, newState);
        }
        int rows = jdbc.update("""
                UPDATE crm_transfer_requests
                   SET state=:state,
                       executed_at=CASE
                           WHEN :state IN ('COMPLETED','FAILED') THEN CURRENT_TIMESTAMP
                           ELSE executed_at END,
                       executed_by_user_id=CASE
                           WHEN :state IN ('COMPLETED','FAILED') THEN :executedBy
                           ELSE executed_by_user_id END,
                       failure_reason=CASE WHEN :state='FAILED' THEN :failureReason ELSE NULL END,
                       updated_at=CURRENT_TIMESTAMP
                 WHERE tenant_id=:tenantId AND id=:id AND state=:currentState
                """, new MapSqlParameterSource()
                .addValue("state", newState.name())
                .addValue("currentState", currentState.name())
                .addValue("executedBy", executedByUserId)
                .addValue("failureReason", failureReason)
                .addValue("tenantId", tenantId)
                .addValue("id", transferRequestId));
        if (rows != 1) {
            throw new TransferStateConflictException(
                    tenantId, transferRequestId, currentState, newState);
        }
    }

    private UUID lockAndReadRequester(UUID tenantId, UUID transferRequestId) {
        try {
            return jdbc.queryForObject("""
                    SELECT requester_user_id FROM crm_transfer_requests
                     WHERE tenant_id=:tenantId AND id=:id FOR UPDATE
                    """, new MapSqlParameterSource()
                    .addValue("tenantId", tenantId)
                    .addValue("id", transferRequestId), UUID.class);
        } catch (EmptyResultDataAccessException missing) {
            throw new OwnershipDomainException("Transfer not found: " + transferRequestId);
        }
    }

    private boolean isAllowedTransition(TransferState current, TransferState target) {
        if (current == target) return true;
        return switch (current) {
            case DRAFT -> EnumSet.of(TransferState.SUBMITTED, TransferState.CANCELLED).contains(target);
            case SUBMITTED -> EnumSet.of(
                    TransferState.UNDER_REVIEW, TransferState.APPROVED,
                    TransferState.REJECTED, TransferState.CANCELLED).contains(target);
            case UNDER_REVIEW -> EnumSet.of(
                    TransferState.APPROVED, TransferState.REJECTED,
                    TransferState.CANCELLED).contains(target);
            case APPROVED -> EnumSet.of(
                    TransferState.COMPLETED, TransferState.FAILED,
                    TransferState.CANCELLED).contains(target);
            case REJECTED, CANCELLED, COMPLETED, FAILED -> false;
        };
    }
}
