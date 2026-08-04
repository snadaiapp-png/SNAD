package com.sanad.platform.crm.error;

/**
 * CRM API Contract — Stable Error Codes Catalog.
 * <p>
 * Each constant is a stable, public error code that the frontend may
 * branch on. The HTTP status associated with each code is documented in
 * {@code docs/crm/contracts/CRM-ERROR-CATALOG.md} and enforced by
 * {@link CrmExceptionHandler}.
 * <p>
 * Adding a new code is a NON-BREAKING change (frontend treats unknown
 * codes as {@code INTERNAL_ERROR}). Removing or renaming a code is a
 * BREAKING change and must follow the deprecation policy in
 * {@code docs/crm/contracts/CRM-API-VERSIONING-POLICY.md}.
 * <p>
 * Branch: crm/003-stable-api-contracts
 * Gate: CRM-G2 — API Contract and Concurrency Gate
 */
public enum CrmErrorCode {

    // ── Not-found codes (HTTP 404) ───────────────────────────────────────
    CRM_ACCOUNT_NOT_FOUND(404, "The requested CRM account was not found.", false),
    CRM_CONTACT_NOT_FOUND(404, "The requested CRM contact was not found.", false),
    CRM_LEAD_NOT_FOUND(404, "The requested CRM lead was not found.", false),
    CRM_OPPORTUNITY_NOT_FOUND(404, "The requested CRM opportunity was not found.", false),
    CRM_ACTIVITY_NOT_FOUND(404, "The requested CRM activity was not found.", false),
    CRM_TASK_NOT_FOUND(404, "The requested CRM task was not found.", false),
    CRM_CASE_NOT_FOUND(404, "The requested CRM case was not found.", false),
    CRM_NOTE_NOT_FOUND(404, "The requested CRM note was not found.", false),
    CRM_TAG_NOT_FOUND(404, "The requested CRM tag was not found.", false),
    CRM_PIPELINE_NOT_FOUND(404, "The requested CRM pipeline was not found.", false),
    CRM_STAGE_NOT_FOUND(404, "The requested CRM pipeline stage was not found.", false),
    CRM_IMPORT_NOT_FOUND(404, "The requested CRM import job was not found.", false),
    CRM_CUSTOM_FIELD_NOT_FOUND(404, "The requested CRM custom field was not found.", false),
    CRM_EMAIL_NOT_FOUND(404, "The requested email log entry was not found.", false),
    RESOURCE_NOT_FOUND(404, "The requested resource was not found.", false),

    // ── Duplicate / state-conflict codes (HTTP 409) ──────────────────────
    CRM_DUPLICATE_ACCOUNT(409, "An account with the same identity already exists.", false),
    CRM_DUPLICATE_CONTACT(409, "A contact with the same email already exists.", false),
    CRM_DUPLICATE_LEAD(409, "A lead with the same identity already exists.", false),
    CRM_LEAD_ALREADY_CONVERTED(409, "The lead has already been converted and cannot be converted again.", false),
    CRM_NOTE_ALREADY_ARCHIVED(409, "The note has already been archived.", false),
    CRM_DUPLICATE_TAG(409, "A tag with the same name already exists.", false),
    CRM_IDEMPOTENCY_CONFLICT(409, "The Idempotency-Key was already used with a different request payload.", false),
    CONFLICT(409, "The request conflicts with the current state of the resource.", false),

    // ── Invalid state-transition codes (HTTP 422) ────────────────────────
    CRM_INVALID_LEAD_TRANSITION(422, "The requested lead status transition is not allowed.", false),
    CRM_INVALID_OPPORTUNITY_STAGE(422, "The requested opportunity stage move is not allowed.", false),
    CRM_INVALID_TASK_TRANSITION(422, "The requested task status transition is not allowed.", false),
    CRM_INVALID_CASE_TRANSITION(422, "The requested case status transition is not allowed.", false),
    CRM_IMPORT_MAPPING_INVALID(422, "The import mapping is invalid or incomplete.", false),
    CRM_CUSTOM_FIELD_VALIDATION_FAILED(422, "One or more custom field values failed validation.", false),

    // ── Precondition required (HTTP 428) ─────────────────────────────────
    CRM_PRECONDITION_REQUIRED(428, "The If-Match header is required for this operation.", false),

    // ── Idempotency key required (HTTP 400) ──────────────────────────────
    CRM_IDEMPOTENCY_KEY_REQUIRED(400, "The Idempotency-Key header is required for this operation.", false),

    // ── Concurrency (HTTP 412) ───────────────────────────────────────────
    CRM_CONCURRENCY_CONFLICT(412, "The resource was modified by another operation. Please refresh and retry.", true),

    // ── Authorization (HTTP 401 / 403) ───────────────────────────────────
    UNAUTHORIZED(401, "Authentication is required to access this resource.", false),
    CRM_TENANT_ACCESS_DENIED(403, "Access to the requested resource was denied.", false),
    CRM_CAPABILITY_REQUIRED(403, "The authenticated principal lacks the required capability.", false),
    FORBIDDEN(403, "Access to the requested resource was denied.", false),

    // ── Validation / client errors (HTTP 400) ────────────────────────────
    VALIDATION_ERROR(400, "The request contains invalid fields.", false),

    // ── Rate-limiting (HTTP 429) ─────────────────────────────────────────
    RATE_LIMITED(429, "Too many requests. Please slow down.", true),

    // ── Catch-all (HTTP 500) ─────────────────────────────────────────────
    INTERNAL_ERROR(500, "An internal server error occurred. Please try again later.", true),

    // ── Intelligence codes ─────────────────────────────────────────────
    CRM_SCORE_NOT_FOUND(404, "The requested score was not found.", false),
    CRM_SEGMENT_NOT_FOUND(404, "The requested segment was not found.", false),
    CRM_SEGMENT_MEMBER_NOT_FOUND(404, "The segment membership was not found.", false),
    CRM_NBA_NOT_FOUND(404, "The requested next best action was not found.", false),
    CRM_NBA_EXPIRED(409, "The next best action has expired.", false),
    CRM_NBA_VERSION_CONFLICT(409, "The next best action has been modified by another request.", false),
    CRM_SCORE_CALCULATION_FAILED(500, "Score calculation failed.", true),

    // ── Email codes ──────────────────────────────────────────────────────
    CRM_EMAIL_SEND_FAILED(500, "Failed to send email via the configured provider.", true);

    private final int httpStatus;
    private final String defaultMessage;
    private final boolean retryable;

    CrmErrorCode(int httpStatus, String defaultMessage, boolean retryable) {
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
        this.retryable = retryable;
    }

    public int httpStatus() { return httpStatus; }
    public String defaultMessage() { return defaultMessage; }
    public boolean retryable() { return retryable; }
}
