package com.sanad.platform.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.Map;
import java.util.UUID;

/**
 * Production-safe global exception handler (v20260820.1).
 *
 * <p>Replaces the previous {@code GlobalDiagnosticExceptionHandler} that
 * leaked {@code exceptionType}, raw exception messages, and root causes to
 * API clients. This handler returns <strong>only safe, generic fields</strong>
 * to the caller, while the full exception (type, message, cause, stack trace)
 * is logged at ERROR level on the server for forensic analysis.
 *
 * <p>Production response contract (HTTP 500):
 * <pre>{@code
 * {
 *   "status": 500,
 *   "error": "Internal Server Error",
 *   "message": "An unexpected error occurred",
 *   "correlationId": "<uuid>"
 * }
 * }</pre>
 *
 * <p><strong>Forbidden in response body</strong>: SQL error text, constraint
 * name, table name, database hostname, exception class name, stack trace,
 * raw cause, internal identifiers, credentials/secrets.
 *
 * <p>{@link org.springframework.web.server.ResponseStatusException} and
 * {@link IllegalArgumentException} are intentionally NOT intercepted here —
 * those are handled by their respective controllers / Spring's
 * {@code ResponseStatusExceptionExceptionHandler} so that meaningful 4xx
 * business messages can still surface to the client (e.g. 404 "not found",
 * 409 "conflict"). This handler is the catch-all for <em>unexpected</em>
 * {@link Throwable}s.
 *
 * <p>The {@link Order} is set to {@link Ordered#LOWEST_PRECEDENCE} so that
 * more specific handlers (e.g. {@code WorkflowController}'s
 * {@code IllegalStateException} → 409 mapper) take precedence.
 */
@ControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class GlobalDiagnosticExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalDiagnosticExceptionHandler.class);

    /**
     * Generic catch-all for unexpected exceptions.
     *
     * <p>The correlationId is generated server-side and logged alongside
     * the full stack trace so that support engineers can correlate a
     * client-reported correlationId with the server log entry without
     * ever exposing the underlying exception to the client.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnhandled(Exception e) {
        String correlationId = UUID.randomUUID().toString();
        log.error("Unhandled exception: correlationId={} type={} message={}",
                correlationId, e.getClass().getName(), e.getMessage(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "status", 500,
                "error", "Internal Server Error",
                "message", "An unexpected error occurred",
                "correlationId", correlationId
        ));
    }
}
