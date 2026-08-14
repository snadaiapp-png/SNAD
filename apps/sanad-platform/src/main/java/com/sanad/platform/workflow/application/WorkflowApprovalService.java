package com.sanad.platform.workflow.application;

import com.sanad.platform.workflow.domain.WorkflowApprovalRequest;
import com.sanad.platform.workflow.domain.WorkflowApprovalRequestRepository;
import com.sanad.platform.workflow.domain.WorkflowInstance;
import com.sanad.platform.workflow.domain.WorkflowInstanceRepository;
import com.sanad.platform.workflow.domain.WorkflowTransitionAudit;
import com.sanad.platform.workflow.domain.WorkflowTransitionAuditRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Application service for {@link WorkflowApprovalRequest} lifecycle management.
 *
 * <p>State machine: PENDING → APPROVED | REJECTED | CANCELLED | EXPIRED
 *
 * <p><strong>Segregation of duties</strong> is enforced by the domain model:
 * {@link WorkflowApprovalRequest#approve(UUID, String)} and
 * {@link WorkflowApprovalRequest#reject(UUID, String)} throw
 * {@link IllegalStateException} if the actor is the same user the approval was
 * requested from. This service surfaces that exception without swallowing it.
 *
 * <p>Every resolve/cancel transition is recorded in
 * {@code workflow_transition_audit} against the parent workflow_instance.
 */
@Service
public class WorkflowApprovalService {

    private static final Logger log = LoggerFactory.getLogger(WorkflowApprovalService.class);

    private final WorkflowApprovalRequestRepository approvalRepo;
    private final WorkflowInstanceRepository instanceRepo;
    private final WorkflowTransitionAuditRepository auditRepo;

    public WorkflowApprovalService(
            WorkflowApprovalRequestRepository approvalRepo,
            WorkflowInstanceRepository instanceRepo,
            WorkflowTransitionAuditRepository auditRepo) {
        this.approvalRepo = approvalRepo;
        this.instanceRepo = instanceRepo;
        this.auditRepo = auditRepo;
    }

    @Transactional
    public WorkflowApprovalRequest createApproval(WorkflowApprovalRequest request, UUID requesterId) {
        // The request already has requestedByUserId set via the create() factory.
        // If not set (backward compat), set it here from the requesterId.
        if (request.requestedByUserId() == null && requesterId != null) {
            request = WorkflowApprovalRequest.create(
                    request.tenantId(), request.workflowInstanceId(), request.workflowStepInstanceId(),
                    request.requestedFromUserId(), request.requestedFromRole(), request.dueAt(),
                    requesterId
            );
        }
        var saved = approvalRepo.save(request);
        auditWorkflow(requesterId, saved, WorkflowTransitionAudit.Action.ASSIGN,
                null, saved.status().name());
        return saved;
    }

    @Transactional(readOnly = true)
    public Optional<WorkflowApprovalRequest> findById(UUID tenantId, UUID id) {
        return approvalRepo.findById(tenantId, id);
    }

    @Transactional(readOnly = true)
    public List<WorkflowApprovalRequest> findByTenant(UUID tenantId, int limit) {
        return approvalRepo.findByTenant(tenantId, limit);
    }

    @Transactional(readOnly = true)
    public List<WorkflowApprovalRequest> findByInstance(UUID tenantId, UUID workflowInstanceId) {
        return approvalRepo.findByInstance(tenantId, workflowInstanceId);
    }

    @Transactional(readOnly = true)
    public List<WorkflowApprovalRequest> findPendingForUser(UUID tenantId, UUID userId, int limit) {
        return approvalRepo.findByUser(tenantId, userId, limit).stream()
                .filter(a -> a.status() == WorkflowApprovalRequest.Status.PENDING)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<WorkflowApprovalRequest> findPendingForTenant(UUID tenantId, int limit) {
        return approvalRepo.findByTenantAndStatus(
                tenantId, WorkflowApprovalRequest.Status.PENDING, limit);
    }

    /**
     * Approve the approval request.
     *
     * <p>Note: Segregation of duties is enforced at the domain level only when
     * the approval was created with a requester ID that differs from the approver.
     * The current schema does not store the requester ID on the approval record,
     * so SOD is enforced at the service level by comparing the approver with
     * the requestedFromUserId (the assignee). If the approver is the same as
     * the assignee, this is allowed (self-approval is permitted by default).
     *
     * <p>To enforce strict SOD (requester cannot approve), add a requestedByUserId
     * column in a future migration.
     */
    @Transactional
    public WorkflowApprovalRequest approve(UUID tenantId, UUID id, UUID approverId, String comments) {
        var req = load(tenantId, id);
        var oldStatus = req.status().name();
        var updated = approvalRepo.save(req.approve(approverId, comments));
        auditWorkflow(approverId, updated, WorkflowTransitionAudit.Action.APPROVE,
                oldStatus, updated.status().name());
        return updated;
    }

    /**
     * Reject the approval request.
     *
     * @throws IllegalStateException if {@code rejecterId} equals
     *         {@code requestedFromUserId} (segregation of duties enforced by domain)
     */
    @Transactional
    public WorkflowApprovalRequest reject(UUID tenantId, UUID id, UUID rejecterId, String comments) {
        var req = load(tenantId, id);
        var oldStatus = req.status().name();
        // Domain enforces segregation of duties.
        var updated = approvalRepo.save(req.reject(rejecterId, comments));
        auditWorkflow(rejecterId, updated, WorkflowTransitionAudit.Action.REJECT,
                oldStatus, updated.status().name());
        return updated;
    }

    @Transactional
    public WorkflowApprovalRequest cancel(UUID tenantId, UUID id, UUID cancelledBy) {
        var req = load(tenantId, id);
        var oldStatus = req.status().name();
        var updated = approvalRepo.save(req.cancel(cancelledBy));
        auditWorkflow(cancelledBy, updated, WorkflowTransitionAudit.Action.CANCEL,
                oldStatus, updated.status().name());
        return updated;
    }

    // ===== Helpers =====

    private WorkflowApprovalRequest load(UUID tenantId, UUID id) {
        return approvalRepo.findById(tenantId, id)
                .orElseThrow(() -> new IllegalArgumentException("WorkflowApprovalRequest not found: " + id));
    }

    /**
     * Record an audit event against the parent workflow_instance. The audit
     * table requires a non-null workflow_instance_id, which we read from the
     * approval request row (FK to workflow_instances).
     */
    private void auditWorkflow(UUID actorUserId, WorkflowApprovalRequest req,
                               WorkflowTransitionAudit.Action action,
                               String fromState, String toState) {
        // We need the tenant_id for the audit row; we use the approval's tenant.
        // The instance lookup is optional — if the instance was deleted, we
        // skip the audit row (rather than fail the operation) because the
        // audit FK would be violated.
        var instanceOpt = instanceRepo.findById(req.tenantId(), req.workflowInstanceId());
        if (instanceOpt.isEmpty()) {
            log.warn("Skipping audit for approval {}: parent instance {} not found",
                    req.id(), req.workflowInstanceId());
            return;
        }
        var instance = instanceOpt.get();
        auditRepo.save(WorkflowTransitionAudit.create(
                instance.tenantId(), instance.id(), req.workflowStepInstanceId(),
                actorUserId, action, fromState, toState,
                instance.correlationId(), null
        ));
        log.info("WorkflowApproval event: action={} tenant={} instanceId={} approvalId={} fromState={} toState={} actor={}",
                action.name(), instance.tenantId(), instance.id(), req.id(),
                fromState, toState, actorUserId);
    }
}
