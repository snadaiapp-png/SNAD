package com.sanad.platform.crm.error;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * CRM API Contract — Standard Error Envelope.
 * <p>
 * Every CRM error response uses this shape:
 * <pre>{@code
 * {
 *   "error": {
 *     "code": "CRM_ACCOUNT_NOT_FOUND",
 *     "message": "The requested account was not found.",
 *     "status": 404,
 *     "requestId": "uuid",
 *     "timestamp": "2026-07-13T10:00:00Z",
 *     "fieldErrors": [],
 *     "details": {}
 *   }
 * }
 * }</pre>
 * <p>
 * For validation errors, {@code fieldErrors} contains one entry per
 * offending field:
 * <pre>{@code
 * {
 *   "error": {
 *     "code": "VALIDATION_ERROR",
 *     ...
 *     "fieldErrors": [
 *       { "field": "displayName", "code": "REQUIRED", "message": "Display name is required." }
 *     ]
 *   }
 * }
 * }</pre>
 * <p>
 * The envelope NEVER contains stack traces, SQL statements, table names,
 * column names, package names, exception class names, tenant IDs of other
 * tenants, secrets, or tokens. This is enforced by
 * {@link CrmExceptionHandler} and verified by {@code CrmErrorContractTest}.
 * <p>
 * Branch: crm/003-stable-api-contracts
 */
public record CrmErrorResponse(
        CrmErrorBody error) {

    public static CrmErrorResponse of(CrmErrorCode code, String message, UUID requestId) {
        return new CrmErrorResponse(new CrmErrorBody(
                code.name(),
                message == null ? code.defaultMessage() : message,
                code.httpStatus(),
                requestId,
                Instant.now(),
                List.of(),
                Map.of()));
    }

    public static CrmErrorResponse validation(UUID requestId, List<FieldError> fieldErrors) {
        return new CrmErrorResponse(new CrmErrorBody(
                CrmErrorCode.VALIDATION_ERROR.name(),
                CrmErrorCode.VALIDATION_ERROR.defaultMessage(),
                CrmErrorCode.VALIDATION_ERROR.httpStatus(),
                requestId,
                Instant.now(),
                fieldErrors == null ? List.of() : List.copyOf(fieldErrors),
                Map.of()));
    }

    public record CrmErrorBody(
            String code,
            String message,
            int status,
            UUID requestId,
            Instant timestamp,
            List<FieldError> fieldErrors,
            Map<String, Object> details) {}

    public record FieldError(
            String field,
            String code,
            String message) {}

    /**
     * Convenience: an empty details map is the default. Use
     * {@link #withDetails(Map)} to attach structured context (e.g. the
     * conflicting version number on a concurrency error).
     */
    public CrmErrorResponse withDetails(Map<String, Object> details) {
        return new CrmErrorResponse(new CrmErrorBody(
                error.code(),
                error.message(),
                error.status(),
                error.requestId(),
                error.timestamp(),
                error.fieldErrors(),
                details == null ? Collections.emptyMap() : Collections.unmodifiableMap(details)));
    }
}
