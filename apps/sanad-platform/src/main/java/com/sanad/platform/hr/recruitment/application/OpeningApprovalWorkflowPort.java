package com.sanad.platform.hr.recruitment.application;

import java.util.UUID;

/**
 * HRM-G1 T7 — HR-owned boundary port to the authoritative Workflow Y2
 * approval engine for JOB OPENING publication (design §11.1).
 *
 * <p>Workflow Y2 owns the approval work items, candidates, policy execution,
 * the APPROVED/REJECTED authoritative outcome and self-approval protection.
 * HRM owns the opening, the submission intent and the business policy that
 * requires approval. There is NO second HR approval engine.</p>
 */
public interface OpeningApprovalWorkflowPort {

    /** Canonical business entity type used for the workflow correlation. */
    String BUSINESS_ENTITY_TYPE = "HR_JOB_OPENING";

    /**
     * Starts (idempotently) the authoritative Y2 job-opening approval workflow
     * for the submitted opening. One open workflow approval per
     * (opening_id, submitted-state-period): repeated submissions MUST return
     * the same logical approval/work-item reference, never a duplicate
     * instance.
     */
    UUID startOpeningApproval(UUID tenantId, UUID openingId, UUID submittedBy);

    /**
     * Loads the authoritative approval snapshot for a workflow instance of
     * this tenant, or {@code null} when the instance does not exist (stale or
     * foreign correlation — the caller fails closed).
     */
    ApprovalSnapshot loadOpeningApprovalOutcome(UUID tenantId, UUID workflowInstanceId);

    /**
     * Cancels (idempotently) the open approval workflow — pending Y2 work
     * items must not remain actionable after the opening is rejected back to
     * DRAFT or cancelled (orphan cleanup, §9).
     */
    void cancelOpeningApproval(UUID tenantId, UUID workflowInstanceId, UUID actorUserId, String reason);

    enum ApprovalOutcome { APPROVED, REJECTED, NONE }

    /**
     * Authoritative snapshot of the workflow's approval resolution (same
     * contract as the offer approval port).
     */
    record ApprovalSnapshot(
            UUID workflowInstanceId,
            UUID definitionVersionId,
            String businessEntityType,
            UUID businessEntityId,
            String status,
            ApprovalOutcome outcome) {
    }
}
