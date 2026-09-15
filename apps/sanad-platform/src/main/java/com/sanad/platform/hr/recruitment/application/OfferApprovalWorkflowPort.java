package com.sanad.platform.hr.recruitment.application;

import java.util.UUID;

/**
 * HRM-G1 T7 — HR-owned boundary port to the authoritative Workflow Y2
 * approval engine (design §11.2).
 *
 * <p>Workflow Y2 owns approval work items, candidates/assignees, approval
 * policy execution (ANY_ONE/ALL where configured), the APPROVED/REJECTED
 * authoritative outcome and self-approval protection. HRM owns the offer,
 * the offer version, the submission intent and the business policy that
 * requires approval. HRM NEVER decides approvals itself — there is no second
 * HR approval engine (architecture boundary test pins this).</p>
 *
 * <p>The implementation persists a stable correlation between tenant, offer,
 * offer version and the workflow definition version / instance it started,
 * and the HRM service verifies that correlation before any state change.</p>
 */
public interface OfferApprovalWorkflowPort {

    /** Canonical business entity type used for the workflow correlation. */
    String BUSINESS_ENTITY_TYPE = "HR_OFFER";

    /**
     * Starts (idempotently) the authoritative Y2 offer approval workflow for
     * the submitted offer/version and returns the workflow instance id.
     * Repeated starts for the SAME (offer, version) MUST NOT create duplicate
     * workflow approval instances.
     */
    UUID startOfferApproval(UUID tenantId, UUID offerId, UUID offerVersionId, UUID submittedBy);

    /**
     * Loads the authoritative approval snapshot for a workflow instance of
     * this tenant, or {@code null} when the instance does not exist (stale or
     * foreign correlation — the caller fails closed).
     */
    ApprovalSnapshot loadApprovalOutcome(UUID tenantId, UUID workflowInstanceId);

    /**
     * Cancels (idempotently) the open approval workflow for this offer —
     * pending Y2 work items must not remain actionable after the business
     * cycle is cancelled (§9 orphan cleanup).
     */
    void cancelOfferApproval(UUID tenantId, UUID workflowInstanceId, UUID actorUserId, String reason);

    enum ApprovalOutcome { APPROVED, REJECTED, NONE }

    /**
     * Authoritative snapshot of the workflow's approval resolution. The HRM
     * service extends an offer ONLY when: the snapshot exists, the business
     * entity is THIS offer, the instance is COMPLETED and the outcome is
     * APPROVED (or the governed REJECTED path for rejection reconciliation).
     *
     * @param workflowInstanceId         the authoritative instance
     * @param definitionVersionId        pinned definition version of the instance
     * @param businessEntityType         correlation entity type (HR_OFFER)
     * @param businessEntityId           correlated offer id
     * @param status                     engine instance status name (RUNNING/COMPLETED/…)
     * @param outcome                    authoritative approval resolution
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
