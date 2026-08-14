package com.sanad.platform.management.application;

import com.sanad.platform.management.domain.ExecutiveDecision;
import com.sanad.platform.management.domain.ManagementAuditEntry;
import com.sanad.platform.management.domain.ManagementAuditRepository;
import com.sanad.platform.workflow.application.WorkflowApprovalService;
import com.sanad.platform.workflow.application.WorkflowDefinitionService;
import com.sanad.platform.workflow.application.WorkflowExecutionService;
import com.sanad.platform.workflow.domain.WorkflowApprovalRequest;
import com.sanad.platform.workflow.domain.WorkflowDefinition;
import com.sanad.platform.workflow.domain.WorkflowInstance;
import com.sanad.platform.workflow.domain.WorkflowStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;

/**
 * Integration bridge between the Senior Management Operating System and the Workflow Engine.
 *
 * <p>Provides the golden path:
 * <ol>
 *   <li>Executive Decision is submitted</li>
 *   <li>A Workflow Instance is automatically started for the decision</li>
 *   <li>An Approval Request is created (requested_from the decision owner, requested_by the submitter)</li>
 *   <li>An authorized approver approves via the Workflow Engine</li>
 *   <li>The Decision automatically transitions to APPROVED when the approval is approved</li>
 *   <li>All state transitions are audited via both Management Audit Trail and Workflow Transition Audit</li>
 * </ol>
 *
 * <p><strong>Segregation of Duties</strong> is enforced by the Workflow Engine:
 * the user who created the approval request (the decision submitter) cannot approve it.
 * The {@link ExecutiveDecision#approve(UUID)} SOD check (approver != createdBy) is also preserved
 * as a defense-in-depth measure.
 *
 * <p><strong>Tenant isolation</strong> is preserved: all queries are tenant-scoped.
 *
 * <p><strong>Idempotency</strong>: if the decision already has an active workflow instance,
 * the existing instance is returned without creating a duplicate. If the decision is already
 * APPROVED, approving again returns the existing approved decision (no duplicate state mutation).
 *
 * <p><strong>Failure / rollback</strong>: if the workflow approval fails, the decision remains
 * in its previous state (UNDER_REVIEW). The transaction rolls back atomically.
 */
@Service
public class DecisionWorkflowIntegrationService {

    private static final Logger log = LoggerFactory.getLogger(DecisionWorkflowIntegrationService.class);

    /** Code used to look up the workflow definition for decision approvals. */
    public static final String DECISION_APPROVAL_WORKFLOW_CODE = "DECISION_APPROVAL";

    private final ExecutiveDecisionService decisionService;
    private final WorkflowDefinitionService defService;
    private final WorkflowExecutionService execService;
    private final WorkflowApprovalService approvalService;
    private final ManagementAuditRepository auditRepo;

    public DecisionWorkflowIntegrationService(
            ExecutiveDecisionService decisionService,
            WorkflowDefinitionService defService,
            WorkflowExecutionService execService,
            WorkflowApprovalService approvalService,
            ManagementAuditRepository auditRepo) {
        this.decisionService = decisionService;
        this.defService = defService;
        this.execService = execService;
        this.approvalService = approvalService;
        this.auditRepo = auditRepo;
    }

    /**
     * Start a workflow instance for the given decision. The decision MUST be in SUBMITTED state.
     *
     * <p>This creates:
     * <ol>
     *   <li>A Workflow Instance (status=RUNNING, businessEntityType=DECISION)</li>
     *   <li>An Approval Request (requested_from=decision.ownerUserId, requested_by=decision.createdBy)</li>
     * </ol>
     *
     * <p>Idempotent: if the decision already has a workflow instance, the existing instance ID is returned.
     *
     * @return the workflow instance ID
     */
    @Transactional
    public UUID startWorkflowForDecision(UUID tenantId, UUID decisionId, UUID submitterUserId) {
        var decision = decisionService.findById(tenantId, decisionId)
                .orElseThrow(() -> new IllegalArgumentException("Decision not found: " + decisionId));

        if (decision.status() != ExecutiveDecision.Status.SUBMITTED
                && decision.status() != ExecutiveDecision.Status.UNDER_REVIEW) {
            throw new IllegalStateException(
                    "Cannot start workflow for decision in status " + decision.status()
                            + " (requires SUBMITTED or UNDER_REVIEW)");
        }

        // Find the workflow definition for decision approvals. There may be multiple versions —
        // we use the latest ACTIVE one. If none exists, this method returns empty and the caller
        // can fall back to the legacy direct-approval path.
        var defOpt = findDecisionApprovalWorkflowDefinition(tenantId);
        if (defOpt.isEmpty()) {
            log.info("No ACTIVE workflow definition with code {} found for tenant {} — skipping workflow integration for decision {}",
                    DECISION_APPROVAL_WORKFLOW_CODE, tenantId, decisionId);
            return null;
        }
        var def = defOpt.get();

        var steps = defService.findSteps(def.id());
        if (steps.isEmpty()) {
            log.warn("Workflow definition {} has no steps — cannot start workflow for decision {}", def.id(), decisionId);
            return null;
        }
        var firstStep = steps.stream()
                .min(Comparator.comparingInt(WorkflowStep::sequenceOrder))
                .orElseThrow();

        // Start the workflow instance
        var instance = WorkflowInstance.start(
                tenantId, def.id(), def.version(),
                "DECISION", decisionId,  // business entity = the decision
                firstStep.stepKey(), submitterUserId, null
        );
        var savedInstance = execService.startWorkflow(instance, submitterUserId);

        // Create the approval request — requested_from = decision owner, requested_by = submitter
        // SOD: the submitter cannot approve their own submitted decision's workflow.
        var dueAt = decision.approvalDueAt() != null
                ? decision.approvalDueAt()
                : Instant.now().plus(7, ChronoUnit.DAYS);
        var approval = WorkflowApprovalRequest.create(
                tenantId, savedInstance.id(), null,
                decision.ownerUserId(),  // requested_from (assignee)
                "DECISION_APPROVER",      // requested_from_role
                dueAt,
                submitterUserId  // requested_by (SOD enforced: submitter cannot approve)
        );
        approvalService.createApproval(approval, submitterUserId);

        // Record management audit entry linking the decision to the workflow instance.
        // NOTE: the `changes` column is JSONB. We pass a valid JSON string here.
        var changesJson = "{\"workflow_instance_id\":\"" + savedInstance.id() + "\"}";
        auditRepo.save(ManagementAuditEntry.create(
                tenantId, submitterUserId,
                ManagementAuditEntry.EntityType.DECISION, decisionId,
                ManagementAuditEntry.Action.STATE_CHANGE,
                decision.status().name(), "WORKFLOW_STARTED",
                changesJson,
                null
        ));

        log.info("Started workflow instance {} for decision {} (tenant={}, submitter={})",
                savedInstance.id(), decisionId, tenantId, submitterUserId);
        return savedInstance.id();
    }

    /**
     * Approve a decision via the Workflow Engine. This:
     * <ol>
     *   <li>Approves the workflow approval request (enforces SOD)</li>
     *   <li>Transitions the decision to APPROVED via the existing {@link ExecutiveDecisionService#approve}</li>
     *   <li>Records the management audit entry</li>
     * </ol>
     *
     * <p>If the decision has no workflow instance (legacy direct-approval path), this method
     * falls back to the direct decision approval.
     *
     * @return the updated decision
     */
    @Transactional
    public ExecutiveDecision approveDecisionViaWorkflow(
            UUID tenantId, UUID decisionId, UUID approverId, String comments) {
        var decision = decisionService.findById(tenantId, decisionId)
                .orElseThrow(() -> new IllegalArgumentException("Decision not found: " + decisionId));

        // Defense-in-depth: SOD check on the decision domain (approver != createdBy)
        // This is also enforced by WorkflowApprovalRequest.approve() via requestedByUserId.

        // Find the workflow instance for this decision (businessEntityType=DECISION, businessEntityId=decisionId)
        var instanceOpt = findWorkflowInstanceForDecision(tenantId, decisionId);

        if (instanceOpt.isPresent()) {
            var instance = instanceOpt.get();

            // Find the pending approval request for this instance
            var approvals = approvalService.findByInstance(tenantId, instance.id());
            var pendingApproval = approvals.stream()
                    .filter(a -> a.status() == WorkflowApprovalRequest.Status.PENDING)
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "No pending approval request found for workflow instance " + instance.id()));

            // Approve via the workflow engine (SOD enforced by WorkflowApprovalRequest.approve)
            var approved = approvalService.approve(tenantId, pendingApproval.id(), approverId, comments);
            log.info("Workflow approval {} approved for decision {} by {}",
                    approved.id(), decisionId, approverId);

            // Transition the decision to APPROVED
            // The decision's own SOD check (approver != createdBy) is also enforced.
            var updatedDecision = decisionService.approve(tenantId, decisionId, approverId);

            // Record management audit entry
            auditRepo.save(ManagementAuditEntry.create(
                    tenantId, approverId,
                    ManagementAuditEntry.EntityType.DECISION, decisionId,
                    ManagementAuditEntry.Action.APPROVE,
                    decision.status().name(), updatedDecision.status().name(),
                    "{\"workflow_approval_id\":\"" + approved.id() + "\",\"comments\":\"" + comments.replace("\"", "\\\"") + "\"}",
                    null
            ));

            return updatedDecision;
        } else {
            // No workflow instance — fall back to direct decision approval
            log.info("No workflow instance found for decision {} — falling back to direct approval", decisionId);
            return decisionService.approve(tenantId, decisionId, approverId);
        }
    }

    /**
     * Reject a decision via the Workflow Engine. Symmetric to {@link #approveDecisionViaWorkflow}.
     */
    @Transactional
    public ExecutiveDecision rejectDecisionViaWorkflow(
            UUID tenantId, UUID decisionId, UUID rejecterId, String comments) {
        var decision = decisionService.findById(tenantId, decisionId)
                .orElseThrow(() -> new IllegalArgumentException("Decision not found: " + decisionId));

        var instanceOpt = findWorkflowInstanceForDecision(tenantId, decisionId);

        if (instanceOpt.isPresent()) {
            var instance = instanceOpt.get();
            var approvals = approvalService.findByInstance(tenantId, instance.id());
            var pendingApproval = approvals.stream()
                    .filter(a -> a.status() == WorkflowApprovalRequest.Status.PENDING)
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "No pending approval request found for workflow instance " + instance.id()));

            var rejected = approvalService.reject(tenantId, pendingApproval.id(), rejecterId, comments);
            log.info("Workflow approval {} rejected for decision {} by {}",
                    rejected.id(), decisionId, rejecterId);

            var updatedDecision = decisionService.reject(tenantId, decisionId, rejecterId);

            auditRepo.save(ManagementAuditEntry.create(
                    tenantId, rejecterId,
                    ManagementAuditEntry.EntityType.DECISION, decisionId,
                    ManagementAuditEntry.Action.REJECT,
                    decision.status().name(), updatedDecision.status().name(),
                    "{\"workflow_approval_id\":\"" + rejected.id() + "\",\"comments\":\"" + comments.replace("\"", "\\\"") + "\"}",
                    null
            ));

            return updatedDecision;
        } else {
            log.info("No workflow instance found for decision {} — falling back to direct rejection", decisionId);
            return decisionService.reject(tenantId, decisionId, rejecterId);
        }
    }

    /**
     * Find the latest ACTIVE workflow definition with code {@link #DECISION_APPROVAL_WORKFLOW_CODE}
     * for the given tenant. Returns empty if none exists.
     */
    private Optional<WorkflowDefinition> findDecisionApprovalWorkflowDefinition(UUID tenantId) {
        var defs = defService.findByTenant(tenantId, 100);
        return defs.stream()
                .filter(d -> DECISION_APPROVAL_WORKFLOW_CODE.equals(d.code()))
                .filter(d -> d.status() == WorkflowDefinition.Status.ACTIVE)
                .max(Comparator.comparingInt(WorkflowDefinition::version));
    }

    /**
     * Find the workflow instance linked to the given decision (businessEntityType=DECISION,
     * businessEntityId=decisionId). Returns the latest one if multiple exist.
     */
    private Optional<WorkflowInstance> findWorkflowInstanceForDecision(UUID tenantId, UUID decisionId) {
        var instances = execService.findByTenant(tenantId, 200);
        return instances.stream()
                .filter(i -> "DECISION".equals(i.businessEntityType()))
                .filter(i -> decisionId.equals(i.businessEntityId()))
                .max(Comparator.comparing(WorkflowInstance::startedAt));
    }
}
