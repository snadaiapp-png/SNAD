package com.sanad.platform.hr.api.v2;

/**
 * HRM-G0 / WS5 Task 2 — canonical HRM v2 error codes.
 *
 * <p>Each code carries its fixed HTTP status. Codes are stable identifiers:
 * localized message text must never change them. Status mapping follows the
 * WS5 plan: 400 validation, 403 scope denial, 404 missing canonical resource,
 * 409 state/occupancy/idempotency/concurrency/migration conflicts, 422
 * structurally valid requests blocked by compliance or business validation.
 */
public enum HrApiErrorCode {

    /** Canonical resource does not exist in the requesting tenant. */
    HRM_PERSON_NOT_FOUND(404),

    /** Person-level conflict: duplicate ACTIVE identity value or user already linked. */
    HRM_PERSON_CONFLICT(409),

    /** Canonical resource does not exist in the requesting tenant. */
    HRM_EMPLOYMENT_NOT_FOUND(404),

    /** Canonical assignment does not exist in the requesting tenant. */
    HRM_ASSIGNMENT_NOT_FOUND(404),

    /** Canonical org unit does not exist in the requesting tenant. */
    HRM_ORG_UNIT_NOT_FOUND(404),

    /** Canonical job does not exist in the requesting tenant. */
    HRM_JOB_NOT_FOUND(404),

    /** Canonical position does not exist in the requesting tenant. */
    HRM_POSITION_NOT_FOUND(404),

    /** Canonical contract does not exist in the requesting tenant. */
    HRM_CONTRACT_NOT_FOUND(404),

    /** Canonical compensation package does not exist in the requesting tenant. */
    HRM_COMPENSATION_NOT_FOUND(404),

    /** Requested lifecycle transition is illegal for the current state. */
    HRM_INVALID_STATE_TRANSITION(409),

    /** Activation prerequisites are unmet (position, contract, compliance). */
    HRM_ACTIVATION_BLOCKED(409),

    /** Position already occupied by another active assignment. */
    HRM_POSITION_OCCUPIED(409),

    /** Assignment overlaps an existing active assignment for the worker. */
    HRM_ASSIGNMENT_OVERLAP(409),

    /** Employment-level conflict (duplicate primary, concurrent mutation). */
    HRM_EMPLOYMENT_CONFLICT(409),

    /** Principal lacks the required capability or tenant scope. */
    HRM_SCOPE_DENIED(403),

    /** Jurisdiction-specific terms requested but Country Pack is not certified. */
    HRM_COUNTRY_PACK_NOT_CERTIFIED(422),

    /** Compliance engine blocked the action under current rules. */
    HRM_COMPLIANCE_BLOCKED(422),

    /** Compliance override exists but still requires an authorized approver. */
    HRM_OVERRIDE_APPROVAL_REQUIRED(422),

    /** Action requires statutory legal review before it may proceed. */
    HRM_LEGAL_REVIEW_REQUIRED(422),

    /** Idempotency key replayed with a different command fingerprint. */
    HRM_IDEMPOTENCY_CONFLICT(409),

    /** Optimistic concurrency check failed (stale aggregate version). */
    HRM_CONCURRENCY_CONFLICT(409),

    /** Legacy v1 write lacks authoritative context; migration required first. */
    HRM_MIGRATION_REQUIRED(409),

    /** Request body failed Jakarta bean validation (envelope-only code). */
    HRM_VALIDATION_FAILED(400);

    private final int httpStatus;

    HrApiErrorCode(int httpStatus) {
        this.httpStatus = httpStatus;
    }

    /** Fixed HTTP status for this code; never derived from message text. */
    public int httpStatus() {
        return httpStatus;
    }
}
