package com.sanad.platform.crm.contract;

import com.sanad.platform.crm.error.CrmContractException;
import com.sanad.platform.crm.error.CrmErrorCode;
import com.sanad.platform.crm.error.CrmErrorResponse;
import com.sanad.platform.crm.error.CrmErrorResponse.CrmErrorBody;
import com.sanad.platform.crm.error.CrmErrorResponse.FieldError;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CRM-G2 Contract Test — Error Envelope (AC-13).
 * <p>
 * Verifies:
 *   - Every CrmErrorCode has a stable name, HTTP status, and default message.
 *   - The error envelope shape matches the documented contract:
 *     {@code { "error": { code, message, status, requestId, timestamp, fieldErrors, details } }}.
 *   - The error body NEVER contains stack traces, SQL, table names, package
 *     names, or exception class names.
 *   - The validation error response includes per-field details.
 * <p>
 * Branch: crm/003-stable-api-contracts
 */
class CrmErrorContractTest {

    @Test
    void everyErrorCodeHasHttpStatusAndDefaultMessage() {
        for (CrmErrorCode code : CrmErrorCode.values()) {
            assertTrue(code.httpStatus() >= 400 && code.httpStatus() <= 599,
                    code.name() + " has an out-of-range HTTP status: " + code.httpStatus());
            assertNotNull(code.defaultMessage(), code.name() + " must have a non-null default message");
            assertFalse(code.defaultMessage().isBlank(), code.name() + " must have a non-blank default message");
        }
    }

    @Test
    void notFoundCodesMapTo404() {
        assertEquals(404, CrmErrorCode.CRM_ACCOUNT_NOT_FOUND.httpStatus());
        assertEquals(404, CrmErrorCode.CRM_CONTACT_NOT_FOUND.httpStatus());
        assertEquals(404, CrmErrorCode.CRM_LEAD_NOT_FOUND.httpStatus());
        assertEquals(404, CrmErrorCode.CRM_OPPORTUNITY_NOT_FOUND.httpStatus());
        assertEquals(404, CrmErrorCode.CRM_ACTIVITY_NOT_FOUND.httpStatus());
        assertEquals(404, CrmErrorCode.CRM_PIPELINE_NOT_FOUND.httpStatus());
        assertEquals(404, CrmErrorCode.CRM_STAGE_NOT_FOUND.httpStatus());
        assertEquals(404, CrmErrorCode.CRM_IMPORT_NOT_FOUND.httpStatus());
        assertEquals(404, CrmErrorCode.CRM_CUSTOM_FIELD_NOT_FOUND.httpStatus());
        assertEquals(404, CrmErrorCode.RESOURCE_NOT_FOUND.httpStatus());
    }

    @Test
    void concurrencyConflictMapsTo412() {
        assertEquals(412, CrmErrorCode.CRM_CONCURRENCY_CONFLICT.httpStatus());
        assertTrue(CrmErrorCode.CRM_CONCURRENCY_CONFLICT.retryable(),
                "CRM_CONCURRENCY_CONFLICT must be retryable");
    }

    @Test
    void idempotencyConflictMapsTo409() {
        assertEquals(409, CrmErrorCode.CRM_IDEMPOTENCY_CONFLICT.httpStatus());
    }

    @Test
    void validationErrorMapsTo400() {
        assertEquals(400, CrmErrorCode.VALIDATION_ERROR.httpStatus());
    }

    @Test
    void unauthorizedMapsTo401AndForbiddenMapsTo403() {
        assertEquals(401, CrmErrorCode.UNAUTHORIZED.httpStatus());
        assertEquals(403, CrmErrorCode.CRM_CAPABILITY_REQUIRED.httpStatus());
        assertEquals(403, CrmErrorCode.CRM_TENANT_ACCESS_DENIED.httpStatus());
        assertEquals(403, CrmErrorCode.FORBIDDEN.httpStatus());
    }

    @Test
    void rateLimitedMapsTo429AndIsRetryable() {
        assertEquals(429, CrmErrorCode.RATE_LIMITED.httpStatus());
        assertTrue(CrmErrorCode.RATE_LIMITED.retryable());
    }

    @Test
    void internalErrorMapsTo500AndIsRetryable() {
        assertEquals(500, CrmErrorCode.INTERNAL_ERROR.httpStatus());
        assertTrue(CrmErrorCode.INTERNAL_ERROR.retryable());
    }

    @Test
    void errorResponseEnvelopeShapeMatchesContract() {
        UUID requestId = UUID.randomUUID();
        CrmErrorResponse response = CrmErrorResponse.of(
                CrmErrorCode.CRM_ACCOUNT_NOT_FOUND,
                "The requested account was not found.",
                requestId);

        assertNotNull(response.error());
        CrmErrorBody body = response.error();
        assertEquals("CRM_ACCOUNT_NOT_FOUND", body.code());
        assertEquals("The requested account was not found.", body.message());
        assertEquals(404, body.status());
        assertEquals(requestId, body.requestId());
        assertNotNull(body.timestamp());
        assertNotNull(body.fieldErrors());
        assertTrue(body.fieldErrors().isEmpty(), "non-validation errors must have empty fieldErrors");
        assertNotNull(body.details());
        assertTrue(body.details().isEmpty(), "default details must be empty");
    }

    @Test
    void validationErrorResponseIncludesFieldErrors() {
        UUID requestId = UUID.randomUUID();
        List<FieldError> fieldErrors = List.of(
                new FieldError("displayName", "REQUIRED", "Display name is required."),
                new FieldError("primaryCurrencyCode", "PATTERN", "Currency must be ISO alpha-3."));
        CrmErrorResponse response = CrmErrorResponse.validation(requestId, fieldErrors);

        assertEquals("VALIDATION_ERROR", response.error().code());
        assertEquals(400, response.error().status());
        assertEquals(2, response.error().fieldErrors().size());
        assertEquals("displayName", response.error().fieldErrors().get(0).field());
        assertEquals("REQUIRED", response.error().fieldErrors().get(0).code());
    }

    @Test
    void errorBodyDoesNotLeakInternalDetails() {
        // AC-13 — the error body must not contain stack traces, SQL,
        // table names, package names, or exception class names.
        UUID requestId = UUID.randomUUID();
        // Simulate an internal error with a message that an inexperienced
        // handler might leak.
        CrmContractException ex = new CrmContractException(
                CrmErrorCode.INTERNAL_ERROR,
                "An internal server error occurred. Please try again later.",
                requestId,
                new RuntimeException("org.postgresql.util.PSQLException: ERROR: duplicate key value violates unique constraint crm_accounts_pkey"));

        CrmErrorResponse response = CrmErrorResponse.of(ex.code(), ex.userMessage(), requestId);
        String message = response.error().message();
        assertTrue(!message.contains("org.postgresql"),
                "error message must not leak the JDBC driver class name");
        assertTrue(!message.contains("PSQLException"),
                "error message must not leak the exception class name");
        assertTrue(!message.contains("crm_accounts_pkey"),
                "error message must not leak the constraint name");
        assertTrue(!message.contains("duplicate key"),
                "error message must not leak the SQL state detail");
    }

    @Test
    void withDetailsAttachesStructuredContext() {
        UUID requestId = UUID.randomUUID();
        CrmErrorResponse response = CrmErrorResponse.of(
                CrmErrorCode.CRM_CONCURRENCY_CONFLICT, null, requestId);
        CrmErrorResponse withDetails = response.withDetails(Map.of("currentVersion", 5L, "expectedVersion", 4L));
        assertEquals(2, withDetails.error().details().size());
        assertEquals(5L, withDetails.error().details().get("currentVersion"));
    }
}
