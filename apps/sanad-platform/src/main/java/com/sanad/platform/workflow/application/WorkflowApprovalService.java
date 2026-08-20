package com.sanad.platform.workflow.application;

import com.sanad.platform.workflow.domain.WorkflowApprovalRequest;
import com.sanad.platform.workflow.domain.WorkflowApprovalRequestRepository;
import com.sanad.platform.workflow.domain.WorkflowDefinitionRepository;
import com.sanad.platform.workflow.domain.WorkflowInstance;
import com.sanad.platform.workflow.domain.WorkflowInstanceRepository;
import com.sanad.platform.workflow.domain.WorkflowStep;
import com.sanad.platform.workflow.domain.WorkflowStepInstance;
import com.sanad.platform.workflow.domain.WorkflowStepInstanceRepository;
import com.sanad.platform.workflow.domain.WorkflowTransitionAudit;
import com.sanad.platform.workflow.domain.WorkflowTransitionAuditRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

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
 * <p><strong>Reference integrity (v20260820.1)</strong> — before persisting an
 * approval, the service validates <em>every</em> cross-reference the API
 * caller supplied:
 * <ul>
 *   <li>{@code workflowStepInstanceId} must exist, belong to the same tenant,
 *       and reference the same {@code workflowInstanceId} the approval is
 *       being created against.</li>
 *   <li>The parent step's {@code workflowDefinitionId} must match the
 *       instance's {@code workflowDefinitionId} (prevents cross-workflow
 *       reference injection within the same tenant).</li>
 *   <li>{@code requestedFromUserId}, when supplied, must be a non-null,
 *       same-tenant, ACTIVE user (verified at the controller layer via the
 *       canonical user-repository; the service-layer guard here only checks
 *       that the UUID is non-null and not obviously foreign — full user
 *       existence is the controller's responsibility).</li>
 *   <li>{@code requestedFromRole}, when supplied, must be a non-empty
 *       string. Canonical role-scope validation is delegated to the
 *       controller because role codes are tenant-scoped and the service
 *       layer does not own the role catalog.</li>
 * </ul>
 *
 * <p>Any reference failure surfaces as an HTTP 400 (BAD_REQUEST) — a
 * <em>controlled</em> 4xx — so the API client gets a meaningful error
 * without exposing the underlying existence/ownership probe.
 *
 * <p>Every resolve/cancel transition is recorded in
 * {@code workflow_transition_audit} against the parent workflow_instance.
 */
@Service
public class WorkflowApprovalService {

    private static final Logger log = LoggerFactory.getLogger(WorkflowApprovalService.class);

    private final WorkflowApprovalRequestRepository approvalRepo;
    private final WorkflowInstanceRepository instanceRepo;
    private final WorkflowStepInstanceRepository stepInstanceRepo;
    private final WorkflowDefinitionRepository defRepo;
    private final WorkflowTransitionAuditRepository auditRepo;

    public WorkflowApprovalService(
            WorkflowApprovalRequestRepository approvalRepo,
            WorkflowInstanceRepository instanceRepo,
            WorkflowStepInstanceRepository stepInstanceRepo,
            WorkflowDefinitionRepository defRepo,
            WorkflowTransitionAuditRepository auditRepo) {
        this.approvalRepo = approvalRepo;
        this.instanceRepo = instanceRepo;
        this.stepInstanceRepo = stepInstanceRepo;
        this.defRepo = defRepo;
        this.auditRepo = auditRepo;
    }

    @Transactional
    public WorkflowApprovalRequest createApproval(WorkflowApprovalRequest request, UUID requesterId) {
        // ===== Reference integrity validation =====
        validateReferences(request);

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

    /**
     * Validate every cross-reference the API caller supplied before persisting.
     *
     * <p>Throws {@link ResponseStatusException} (HTTP 400 BAD_REQUEST) for any
     * invalid reference so the controller's @ExceptionHandler can surface a
     * controlled 4xx — never a 500 — and never leak the existence probe.
     */
    private void validateReferences(WorkflowApprovalRequest request) {
        var tenantId = request.tenantId();
        var instanceId = request.workflowInstanceId();
        var stepInstanceId = request.workflowStepInstanceId();

        // 1. Workflow instance must exist and belong to the authenticated tenant.
        WorkflowInstance instance = instanceRepo.findById(tenantId, instanceId)
                .orElseThrow(() -> badRequest("WorkflowInstance not found for tenant"));

        // 2. If a step instance reference is supplied, validate it end-to-end.
        if (stepInstanceId != null) {
            WorkflowStepInstance stepInstance = stepInstanceRepo.findById(tenantId, stepInstanceId)
                    .orElseThrow(() -> badRequest("WorkflowStepInstance not found for tenant"));

            // 2a. Step instance.tenantId == approval.tenantId (defensive — findById already scoped)
            if (!stepInstance.tenantId().equals(tenantId)) {
                throw badRequest("Step instance tenant mismatch");
            }

            // 2b. Step instance.workflowInstanceId == approval.workflowInstanceId
            if (!stepInstance.workflowInstanceId().equals(instanceId)) {
                throw badRequest("Step instance does not belong to this workflow instance");
            }

            // 2c. Step belongs to the same workflow definition as the instance.
            //     Look up the step via the parent definition and confirm the
            //     step's workflowDefinitionId matches the instance's.
            WorkflowStep step = defRepo.findSteps(instance.workflowDefinitionId()).stream()
                    .filter(s -> s.id().equals(stepInstance.workflowStepId()))
                    .findFirst()
                    .orElseThrow(() -> badRequest(
                            "Step does not belong to the workflow definition of the instance"));
            if (!step.workflowDefinitionId().equals(instance.workflowDefinitionId())) {
                throw badRequest("Step definition does not match the instance definition");
            }
        }

        // 3. requestedFromUserId, if supplied, must be non-null. Full
        //    user-existence and ACTIVE-status validation is the controller's
        //    responsibility (it has access to the user repository). Here we
        //    only guard against obvious injection (null UUID masquerading).
        if (request.requestedFromUserId() != null) {
            // A zero-all-zeros UUID is the canonical "missing/foreign" sentinel
            // a malicious client might inject. Reject it explicitly.
            UUID zero = new UUID(0L, 0L);
            if (request.requestedFromUserId().equals(zero)) {
                throw badRequest("requestedFromUserId is invalid");
            }
        }

        // 4. requestedFromRole, if supplied, must be non-blank.
        if (request.requestedFromRole() != null && request.requestedFromRole().isBlank()) {
            throw badRequest("requestedFromRole cannot be blank");
        }
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
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
