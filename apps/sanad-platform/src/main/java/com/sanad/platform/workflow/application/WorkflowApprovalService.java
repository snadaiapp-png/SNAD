package com.sanad.platform.workflow.application;

import com.sanad.platform.workflow.domain.WorkflowApprovalPolicy;
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
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class WorkflowApprovalService {

    private static final Logger log = LoggerFactory.getLogger(WorkflowApprovalService.class);

    private final WorkflowApprovalRequestRepository approvalRepo;
    private final WorkflowInstanceRepository instanceRepo;
    private final WorkflowStepInstanceRepository stepInstanceRepo;
    private final WorkflowDefinitionRepository defRepo;
    private final WorkflowTransitionAuditRepository auditRepo;
    private final WorkflowApprovalPolicyEngine approvalPolicyEngine;
    private final WorkflowGraphExecutionService graphExecutionService;

    public WorkflowApprovalService(
            WorkflowApprovalRequestRepository approvalRepo,
            WorkflowInstanceRepository instanceRepo,
            WorkflowStepInstanceRepository stepInstanceRepo,
            WorkflowDefinitionRepository defRepo,
            WorkflowTransitionAuditRepository auditRepo,
            WorkflowApprovalPolicyEngine approvalPolicyEngine,
            WorkflowGraphExecutionService graphExecutionService) {
        this.approvalRepo = approvalRepo;
        this.instanceRepo = instanceRepo;
        this.stepInstanceRepo = stepInstanceRepo;
        this.defRepo = defRepo;
        this.auditRepo = auditRepo;
        this.approvalPolicyEngine = approvalPolicyEngine;
        this.graphExecutionService = graphExecutionService;
    }

    @Transactional
    public WorkflowApprovalRequest createApproval(WorkflowApprovalRequest request, UUID requesterId) {
        validateReferences(request);
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

    private void validateReferences(WorkflowApprovalRequest request) {
        var tenantId = request.tenantId();
        var instanceId = request.workflowInstanceId();
        var stepInstanceId = request.workflowStepInstanceId();

        WorkflowInstance instance = instanceRepo.findById(tenantId, instanceId)
                .orElseThrow(() -> badRequest("WorkflowInstance not found for tenant"));

        if (stepInstanceId != null) {
            WorkflowStepInstance stepInstance = stepInstanceRepo.findById(tenantId, stepInstanceId)
                    .orElseThrow(() -> badRequest("WorkflowStepInstance not found for tenant"));
            if (!stepInstance.tenantId().equals(tenantId)) {
                throw badRequest("Step instance tenant mismatch");
            }
            if (!stepInstance.workflowInstanceId().equals(instanceId)) {
                throw badRequest("Step instance does not belong to this workflow instance");
            }
            WorkflowStep step = defRepo.findSteps(instance.workflowDefinitionId()).stream()
                    .filter(s -> s.id().equals(stepInstance.workflowStepId()))
                    .findFirst()
                    .orElseThrow(() -> badRequest(
                            "Step does not belong to the workflow definition of the instance"));
            if (!step.workflowDefinitionId().equals(instance.workflowDefinitionId())) {
                throw badRequest("Step definition does not match the instance definition");
            }
        }

        if (request.requestedFromUserId() != null) {
            UUID zero = new UUID(0L, 0L);
            if (request.requestedFromUserId().equals(zero)) {
                throw badRequest("requestedFromUserId is invalid");
            }
        }
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

    /** Legacy/internal overload retained for compatibility with existing callers. */
    @Transactional
    public WorkflowApprovalRequest approve(UUID tenantId, UUID id, UUID approverId, String comments) {
        return approveLoaded(tenantId, load(tenantId, id), approverId, comments);
    }

    /** API/Y2 overload: fails closed when the client acts on a stale approval version. */
    @Transactional
    public WorkflowApprovalRequest approve(
            UUID tenantId, UUID id, UUID approverId, long expectedVersion, String comments) {
        var req = load(tenantId, id);
        requireExpectedVersion(req, expectedVersion);
        return approveLoaded(tenantId, req, approverId, comments);
    }

    private WorkflowApprovalRequest approveLoaded(
            UUID tenantId, WorkflowApprovalRequest req, UUID approverId, String comments) {
        var oldStatus = req.status().name();
        var updated = approvalRepo.save(req.approve(approverId, comments));
        auditWorkflow(approverId, updated, WorkflowTransitionAudit.Action.APPROVE,
                oldStatus, updated.status().name());
        resolvePolicyAndAdvance(tenantId, updated, approverId);
        return updated;
    }

    /** Legacy/internal overload retained for compatibility with existing callers. */
    @Transactional
    public WorkflowApprovalRequest reject(UUID tenantId, UUID id, UUID rejecterId, String comments) {
        return rejectLoaded(tenantId, load(tenantId, id), rejecterId, comments);
    }

    /** API/Y2 overload: fails closed when the client acts on a stale approval version. */
    @Transactional
    public WorkflowApprovalRequest reject(
            UUID tenantId, UUID id, UUID rejecterId, long expectedVersion, String comments) {
        var req = load(tenantId, id);
        requireExpectedVersion(req, expectedVersion);
        return rejectLoaded(tenantId, req, rejecterId, comments);
    }

    private WorkflowApprovalRequest rejectLoaded(
            UUID tenantId, WorkflowApprovalRequest req, UUID rejecterId, String comments) {
        var oldStatus = req.status().name();
        var updated = approvalRepo.save(req.reject(rejecterId, comments));
        auditWorkflow(rejecterId, updated, WorkflowTransitionAudit.Action.REJECT,
                oldStatus, updated.status().name());
        resolvePolicyAndAdvance(tenantId, updated, rejecterId);
        return updated;
    }

    private void requireExpectedVersion(WorkflowApprovalRequest req, long expectedVersion) {
        if (req.version() != expectedVersion) {
            throw new OptimisticLockingFailureException(
                    "WorkflowApprovalRequest " + req.id()
                            + " expected version " + expectedVersion
                            + " but was " + req.version());
        }
    }

    private void resolvePolicyAndAdvance(UUID tenantId, WorkflowApprovalRequest updated, UUID actorId) {
        if (updated.workflowStepInstanceId() == null || updated.workflowInstanceId() == null) {
            return;
        }
        var siblings = approvalRepo.findByInstance(tenantId, updated.workflowInstanceId()).stream()
                .filter(r -> updated.workflowStepInstanceId().equals(r.workflowStepInstanceId()))
                .toList();
        var policy = new WorkflowApprovalPolicy(updated.approvalPolicy(), updated.selfApprovalPolicy());
        var resolution = approvalPolicyEngine.resolve(policy, siblings);
        if (!resolution.stepComplete()) {
            return;
        }
        for (UUID cancelId : resolution.requestsToCancel()) {
            approvalRepo.findById(tenantId, cancelId)
                    .filter(r -> r.status() == WorkflowApprovalRequest.Status.PENDING)
                    .ifPresent(r -> approvalRepo.save(r.cancel(actorId)));
        }
        var parentInstance = instanceRepo.findById(tenantId, updated.workflowInstanceId());
        if (parentInstance.isEmpty()
                || parentInstance.get().engineGeneration() != WorkflowInstance.EngineGeneration.Y2) {
            return;
        }
        String outcome = "APPROVE".equals(resolution.outcome()) ? "APPROVE" : "REJECT";
        graphExecutionService.advance(tenantId, updated.workflowInstanceId(), outcome, actorId);
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

    private WorkflowApprovalRequest load(UUID tenantId, UUID id) {
        return approvalRepo.findById(tenantId, id)
                .orElseThrow(() -> new IllegalArgumentException("WorkflowApprovalRequest not found: " + id));
    }

    private void auditWorkflow(UUID actorUserId, WorkflowApprovalRequest req,
                               WorkflowTransitionAudit.Action action,
                               String fromState, String toState) {
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
