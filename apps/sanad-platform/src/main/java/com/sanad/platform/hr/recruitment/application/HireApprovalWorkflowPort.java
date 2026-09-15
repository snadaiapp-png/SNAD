package com.sanad.platform.hr.recruitment.application;

import java.util.Optional;
import java.util.UUID;

/**
 * HRM-G1 T8 — HR-owned boundary port to the authoritative Workflow Y2 engine
 * for the OPTIONAL hire approval (design §11.3; directive T8.8).
 *
 * <p>Tenant policy default is OFF. When the authoritative tenant policy
 * ({@code hr_tenant_policies.HRM.RECRUITMENT.HIRE_APPROVAL = ON}) enables it,
 * the conversion command may proceed ONLY with an APPROVED Y2 instance whose
 * correlation binds THIS tenant and offer. Workflow Y2 owns the work items,
 * eligibility ({@code HRM.RECRUITMENT.HIRE.APPROVE}), ANY_ONE policy,
 * self-approval DENY and the authoritative outcome — HRM never decides
 * approvals itself.</p>
 */
public interface HireApprovalWorkflowPort {

    /** Canonical business entity type for the hire-approval correlation. */
    String BUSINESS_ENTITY_TYPE = "HR_OFFER_HIRE";

    /**
     * Starts (idempotently) the authoritative Y2 hire approval for the offer
     * — one approval instance per (offer_id, conversion-attempt window);
     * repeated starts reuse the open instance instead of duplicating it.
     */
    UUID startHireApproval(UUID tenantId, UUID offerId, UUID submittedBy);

    /**
     * Loads the authoritative approval snapshot, or {@code null} when the
     * instance does not exist in this tenant (the caller fails closed).
     */
    ApprovalSnapshot loadHireApprovalOutcome(UUID tenantId, UUID workflowInstanceId);

    /**
     * Finds the latest approval instance correlated to this offer (any
     * state), or empty when no approval cycle was ever started.
     */
    Optional<UUID> findLatestApproval(UUID tenantId, UUID offerId);

    /** Cancels (idempotently) the open hire approval for the offer. */
    void cancelHireApproval(UUID tenantId, UUID workflowInstanceId, UUID actorUserId, String reason);

    enum ApprovalOutcome { APPROVED, REJECTED, NONE }

    /**
     * Authoritative snapshot of the hire approval resolution.
     *
     * @param workflowInstanceId  the authoritative instance
     * @param definitionVersionId pinned definition version of the instance
     * @param businessEntityType  correlation entity type (HR_OFFER_HIRE)
     * @param businessEntityId    correlated offer id
     * @param status              engine instance status name
     * @param outcome             authoritative approval resolution
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
