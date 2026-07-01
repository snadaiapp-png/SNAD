package com.sanad.platform.shared.api;

/**
 * Centralized error code catalog for SNAD API.
 * Pattern: SANAD-<DOMAIN>-<NUMBER>
 */
public enum ErrorCode {
    // General
    SANAD_GEN_001("SANAD-GEN-001", "Unexpected server error", 500),
    SANAD_GEN_002("SANAD-GEN-002", "Service unavailable", 503),

    // Validation
    SANAD_VAL_001("SANAD-VAL-001", "Request validation failed", 400),
    SANAD_VAL_002("SANAD-VAL-002", "Malformed request body", 400),
    SANAD_VAL_003("SANAD-VAL-003", "Missing required parameter", 400),
    SANAD_VAL_004("SANAD-VAL-004", "Invalid parameter type", 400),

    // Authentication
    SANAD_AUTH_001("SANAD-AUTH-001", "Authentication required", 401),
    SANAD_AUTH_002("SANAD-AUTH-002", "Invalid credentials", 401),
    SANAD_AUTH_003("SANAD-AUTH-003", "Token expired", 401),
    SANAD_AUTH_004("SANAD-AUTH-004", "Session revoked", 401),

    // Security / Authorization
    SANAD_SEC_001("SANAD-SEC-001", "Access denied", 403),

    // Tenant
    SANAD_TEN_001("SANAD-TEN-001", "Tenant context missing", 403),
    SANAD_TEN_002("SANAD-TEN-002", "Tenant access denied", 403),
    SANAD_TEN_003("SANAD-TEN-003", "Ambiguous tenant — disambiguation required", 409),

    // Resource
    SANAD_RES_001("SANAD-RES-001", "Resource not found", 404),

    // Conflict
    SANAD_CON_001("SANAD-CON-001", "Resource conflict", 409),

    // Business validation
    SANAD_BIZ_001("SANAD-BIZ-001", "Business rule violation", 422),

    // Pagination
    SANAD_PAG_001("SANAD-PAG-001", "Invalid pagination parameters", 400),
    SANAD_PAG_002("SANAD-PAG-002", "Invalid sort field", 400),

    // Rate limit
    SANAD_RATE_001("SANAD-RATE-001", "Rate limit exceeded", 429),

    // Audit (Stage 05)
    SANAD_AUDIT_001("SANAD-AUDIT-001", "Audit event not found", 404),
    SANAD_AUDIT_002("SANAD-AUDIT-002", "Audit integrity check failed", 409),

    // Idempotency (Stage 05)
    SANAD_IDEMP_001("SANAD-IDEMP-001", "Idempotency key required", 400),
    SANAD_IDEMP_002("SANAD-IDEMP-002", "Idempotency key conflict — payload mismatch", 409),
    SANAD_IDEMP_003("SANAD-IDEMP-003", "Idempotency key in progress — retry later", 409),
    SANAD_IDEMP_004("SANAD-IDEMP-004", "Idempotency record expired", 410);

    private final String code;
    private final String title;
    private final int status;

    ErrorCode(String code, String title, int status) {
        this.code = code;
        this.title = title;
        this.status = status;
    }

    public String code() { return code; }
    public String title() { return title; }
    public int status() { return status; }
}
