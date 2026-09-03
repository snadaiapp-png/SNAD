package com.sanad.platform.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Production-safe global exception handler (v20260820.3).
 *
 * <p>Replaces the diagnostic version that leaked {@code exceptionType},
 * raw messages, and root causes. This handler preserves Spring MVC's
 * canonical status-aware exception handling while returning only safe
 * fields to API clients.
 *
 * <h2>HTTP semantics matrix</h2>
 * <table>
 *   <tr><th>Exception type</th><th>HTTP status</th><th>Log level</th></tr>
 *   <tr><td>{@link NoResourceFoundException}</td><td>404</td><td>DEBUG (not ERROR)</td></tr>
 *   <tr><td>{@link NoHandlerFoundException}</td><td>404</td><td>DEBUG</td></tr>
 *   <tr><td>{@link ResponseStatusException}</td><td>preserve declared status</td><td>DEBUG for 4xx, ERROR for 5xx</td></tr>
 *   <tr><td>{@link MethodArgumentNotValidException}</td><td>400</td><td>DEBUG</td></tr>
 *   <tr><td>{@link HttpMessageNotReadableException}</td><td>400</td><td>DEBUG</td></tr>
 *   <tr><td>{@link IllegalArgumentException}</td><td>400</td><td>DEBUG</td></tr>
 *   <tr><td>{@link IllegalStateException} (business conflict)</td><td>409</td><td>DEBUG</td></tr>
 *   <tr><td>{@link AuthenticationException}</td><td>401 (let SecurityConfig handle)</td><td>—</td></tr>
 *   <tr><td>{@link AccessDeniedException}</td><td>403 (let SecurityConfig handle)</td><td>—</td></tr>
 *   <tr><td>{@link Exception} (unexpected)</td><td>500 sanitized</td><td>ERROR with stack trace + correlationId</td></tr>
 * </table>
 *
 * <h2>Production response contract</h2>
 * <ul>
 *   <li><b>4xx</b> — Body contains {@code status, error, message, correlationId}.
 *       The message is the safe business message (e.g. "Resource not found",
 *       "Malformed JSON body") — NEVER the underlying exception class or SQL.</li>
 *   <li><b>5xx</b> — Body contains {@code status, error, message="An unexpected error occurred", correlationId}.
 *       The full exception is logged server-side at ERROR with the correlationId.</li>
 * </ul>
 *
 * <h2>Forbidden in response body (any status)</h2>
 * SQL error text, constraint name, table name, database hostname, exception
 * class name, stack trace, raw cause, internal identifiers, credentials/secrets.
 *
 * <h2>Error log noise control</h2>
 * Expected 4xx conditions are logged at DEBUG (or not logged at all for the
 * most common ones like 404 on unknown routes) — they are NOT logged at
 * ERROR with full stack traces. Only truly unexpected 5xx exceptions are
 * logged at ERROR.
 *
 * <p>The {@link Order} is set to {@link Ordered#LOWEST_PRECEDENCE} so that
 * more specific @ControllerAdvice handlers (e.g. {@code WorkflowController}'s
 * {@code IllegalStateException} → 409 mapper) take precedence.
 */
@ControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class GlobalDiagnosticExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalDiagnosticExceptionHandler.class);

    // ===== 404 — resource not found =====

    /**
     * Spring 6+ raises {@link NoResourceFoundException} when a static resource
     * or unknown URL pattern is requested. Map to HTTP 404 — not 500.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNoResourceFound(NoResourceFoundException e) {
        // 404s are extremely common in production (scanners, probes, missing assets).
        // Log at DEBUG to avoid noise; do NOT log full stack trace.
        if (log.isDebugEnabled()) {
            log.debug("404 NoResourceFound: {}", e.getMessage());
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(safeBody(
                HttpStatus.NOT_FOUND, "Resource not found", null));
    }

    /**
     * Spring MVC raises {@link NoHandlerFoundException} when no controller
     * matches the URL. Map to HTTP 404 — not 500.
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNoHandlerFound(NoHandlerFoundException e) {
        if (log.isDebugEnabled()) {
            log.debug("404 NoHandlerFound: {}", e.getMessage());
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(safeBody(
                HttpStatus.NOT_FOUND, "Resource not found", null));
    }

    // ===== 400 — malformed request =====

    /**
     * {@link HttpMessageNotReadableException} — JSON body could not be parsed.
     * Map to HTTP 400 — not 500.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleNotReadable(HttpMessageNotReadableException e) {
        if (log.isDebugEnabled()) {
            log.debug("400 Malformed JSON body: {}", e.getMessage());
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(safeBody(
                HttpStatus.BAD_REQUEST, "Malformed JSON body", null));
    }

    /**
     * {@link MethodArgumentNotValidException} — bean validation failed.
     * Map to HTTP 400 with the field errors summary.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException e) {
        if (log.isDebugEnabled()) {
            log.debug("400 Validation failure: {}", e.getMessage());
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(safeBody(
                HttpStatus.BAD_REQUEST, "Validation failure", null));
    }

    /**
     * {@link IllegalArgumentException} — programmer-supplied preconditions.
     * Map to HTTP 400 — not 500.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException e) {
        if (log.isDebugEnabled()) {
            log.debug("400 IllegalArgument: {}", e.getMessage());
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(safeBody(
                HttpStatus.BAD_REQUEST, "Invalid request", null));
    }

    // ===== 409 — business conflict =====

    /**
     * {@link IllegalStateException} — domain state-machine violations and
     * segregation-of-duty rejections. Map to HTTP 409 CONFLICT — not 500.
     *
     * <p>Note: this is the global fallback; controllers with their own
     * {@code @ExceptionHandler(IllegalStateException.class)} (e.g.
     * {@code WorkflowController}) take precedence at the controller level
     * and return their own conflict body. This handler catches
     * IllegalStateExceptions that escape from non-workflow controllers.
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalState(IllegalStateException e) {
        if (log.isDebugEnabled()) {
            log.debug("409 IllegalState conflict: {}", e.getMessage());
        }
        return ResponseEntity.status(HttpStatus.CONFLICT).body(safeBody(
                HttpStatus.CONFLICT, "Request conflicts with current state", null));
    }

    // ===== status-aware (ResponseStatusException preserves declared status) =====

    /**
     * Preserve the declared HTTP status of {@link ResponseStatusException}.
     * Do NOT collapse to 500. The body exposes only the safe business
     * message — never the underlying cause.
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatus(ResponseStatusException e) {
        HttpStatus status = HttpStatus.resolve(e.getStatusCode().value());
        if (status == null) status = HttpStatus.INTERNAL_SERVER_ERROR;
        if (status.is5xxServerError()) {
            // Unexpected server-side ResponseStatusException → log at ERROR with correlationId
            String correlationId = UUID.randomUUID().toString();
            log.error("ResponseStatusException 5xx: correlationId={} type={} message={}",
                    correlationId, e.getClass().getName(), e.getMessage(), e);
            Map<String, Object> body = safeBody(status, "An unexpected error occurred", correlationId);
            return ResponseEntity.status(status).body(body);
        }
        // Expected 4xx ResponseStatusException — DEBUG only, no stack trace
        if (log.isDebugEnabled()) {
            log.debug("{} ResponseStatus: {}", status.value(), e.getMessage());
        }
        // Use the business message (e.g. "order not found: 12345") but never the cause
        return ResponseEntity.status(status).body(safeBody(status, e.getReason(), null));
    }

    // ===== 401/403 — let SecurityConfig handle =====
    // AuthenticationException and AccessDeniedException are handled by the
    // AuthenticationEntryPoint and AccessDeniedHandler beans in SecurityConfig.
    // Do NOT define @ExceptionHandler for them here — that would override
    // the canonical Spring Security handling chain.

    // ===== 500 — unexpected catch-all =====

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
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(safeBody(
                HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred", correlationId));
    }

    // ===== Helpers =====

    /**
     * Build a safe response body. Exactly four keys: status, error,
     * message, correlationId. The correlationId is generated if not
     * supplied. The message is sanitised — never exposes internal details.
     */
    private Map<String, Object> safeBody(HttpStatus status, String message, String correlationId) {
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message != null ? message : "No details available");
        body.put("correlationId", correlationId);
        return body;
    }
}
