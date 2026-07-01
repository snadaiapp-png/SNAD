package com.sanad.platform.idempotency.service;

import java.util.Map;
import java.util.UUID;

/**
 * Stage 05A.2.1 §9 — Result of an idempotent command execution.
 *
 * <p>Carries the HTTP status, approved headers, response body (canonical
 * JSON), resource ID, and the lease version that was used for completion.
 * The caller uses this to build the HTTP response.</p>
 *
 * @param httpStatus the HTTP status code (e.g. 201)
 * @param headers approved response headers (Location, Content-Type, etc.)
 * @param body canonical JSON response body
 * @param resourceId the created resource ID (for convenience)
 * @param replayed true if this was a replay of a previous response
 * @param leaseVersion the lease version used for completion (0 if replayed)
 */
public record IdempotentHttpResult<T>(
        int httpStatus,
        Map<String, String> headers,
        String body,
        UUID resourceId,
        boolean replayed,
        long leaseVersion,
        T businessResult
) {}
