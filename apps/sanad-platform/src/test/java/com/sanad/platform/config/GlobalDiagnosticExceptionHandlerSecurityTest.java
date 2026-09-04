package com.sanad.platform.config;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression test for the production-safe {@link GlobalDiagnosticExceptionHandler}.
 *
 * <p>Verifies two dimensions:
 *
 * <ol>
 *   <li><b>HTTP semantics</b> — expected 4xx conditions return their canonical
 *       status (404 for missing resources, 400 for malformed JSON, 400 for
 *       validation failures, 409 for business conflicts, declared status for
 *       {@link ResponseStatusException}). Only truly unexpected exceptions
 *       return 500.</li>
 *   <li><b>Information disclosure</b> — no internal diagnostic information
 *       (exception class name, raw message, root cause, SQL error text,
 *       constraint name, table name) ever appears in any response body.</li>
 * </ol>
 *
 * <p>This test prevents regression of the previous diagnostic disclosure
 * behaviour that returned {@code exceptionType}, {@code message} (raw),
 * and {@code cause} directly to clients, AND the v8 interim regression
 * that collapsed 404s into 500s.
 */
class GlobalDiagnosticExceptionHandlerSecurityTest {

    private final GlobalDiagnosticExceptionHandler handler = new GlobalDiagnosticExceptionHandler();

    // ===== HTTP semantics tests =====

    @Test
    void noResourceFoundException_returns404Not500() {
        // Render logs showed these being captured as 500 ERROR; verify 404.
        NoResourceFoundException ex = new NoResourceFoundException(
                org.springframework.http.HttpMethod.GET, "/nonexistent/path");
        ResponseEntity<Map<String, Object>> response = handler.handleNoResourceFound(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("status")).isEqualTo(404);
        assertThat(body.get("error")).isEqualTo("Not Found");
        // No internal exception class leak
        assertThat(body.toString()).doesNotContain("NoResourceFoundException");
    }

    @Test
    void malformedJson_returns400Not500() {
        HttpMessageNotReadableException ex = new HttpMessageNotReadableException(
                "JSON parse error: Unexpected character",
                null, null);

        ResponseEntity<Map<String, Object>> response = handler.handleNotReadable(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("status")).isEqualTo(400);
        // Safe message, no internal parse error details
        assertThat(body.get("message")).isEqualTo("Malformed JSON body");
        assertThat(body.toString()).doesNotContain("JSON parse error");
        assertThat(body.toString()).doesNotContain("Unexpected character");
    }

    @Test
    void validationException_returns400WithSafeMessage() {
        // Construct MethodArgumentNotValidException with required MethodParameter + BindingResult
        org.springframework.validation.BeanPropertyBindingResult bindingResult =
                new org.springframework.validation.BeanPropertyBindingResult(new Object(), "request");
        // Use reflection to construct MethodParameter for a synthetic no-arg method
        java.lang.reflect.Method syntheticMethod;
        try {
            // Use Object.getClass() — guaranteed to exist on JVM
            syntheticMethod = Object.class.getMethod("getClass");
        } catch (NoSuchMethodException nsme) {
            throw new AssertionError(nsme);
        }
        org.springframework.core.MethodParameter parameter =
                new org.springframework.core.MethodParameter(syntheticMethod, -1);
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(parameter, bindingResult);

        ResponseEntity<Map<String, Object>> response = handler.handleValidation(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("status")).isEqualTo(400);
        assertThat(response.getBody().get("message")).isEqualTo("Validation failure");
    }

    @Test
    void illegalArgument_returns400Not500() {
        IllegalArgumentException ex = new IllegalArgumentException(
                "WorkflowStep not found: definition=abc stepKey=xyz");

        ResponseEntity<Map<String, Object>> response = handler.handleIllegalArgument(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("status")).isEqualTo(400);
        // Safe message — never the underlying stepKey/definition leak
        assertThat(response.getBody().get("message")).isEqualTo("Invalid request");
        assertThat(response.getBody().toString()).doesNotContain("definition=abc");
    }

    @Test
    void illegalState_returns409Not500() {
        IllegalStateException ex = new IllegalStateException(
                "Cannot advance from COMPLETED (requires IN_PROGRESS)");

        ResponseEntity<Map<String, Object>> response = handler.handleIllegalState(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().get("status")).isEqualTo(409);
        assertThat(response.getBody().get("message")).isEqualTo("Request conflicts with current state");
        assertThat(response.getBody().toString()).doesNotContain("Cannot advance");
    }

    @Test
    void responseStatusException_4xx_preservesDeclaredStatus() {
        // 404 ResponseStatusException (e.g. order not found) must stay 404, not become 500
        ResponseStatusException ex = new ResponseStatusException(HttpStatus.NOT_FOUND, "order not found: 12345");

        ResponseEntity<Map<String, Object>> response = handler.handleResponseStatus(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().get("status")).isEqualTo(404);
        // The business reason is preserved (no underlying stack trace)
        assertThat(response.getBody().get("message")).isEqualTo("order not found: 12345");
    }

    @Test
    void responseStatusException_409_preservesConflictStatus() {
        ResponseStatusException ex = new ResponseStatusException(HttpStatus.CONFLICT, "cart is not active: CHECKED_OUT");

        ResponseEntity<Map<String, Object>> response = handler.handleResponseStatus(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().get("status")).isEqualTo(409);
        assertThat(response.getBody().get("message")).isEqualTo("cart is not active: CHECKED_OUT");
    }

    @Test
    void responseStatusException_400_preservesBadRequestStatus() {
        ResponseStatusException ex = new ResponseStatusException(HttpStatus.BAD_REQUEST, "cartId is required");

        ResponseEntity<Map<String, Object>> response = handler.handleResponseStatus(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("status")).isEqualTo(400);
        assertThat(response.getBody().get("message")).isEqualTo("cartId is required");
    }

    // ===== 403 authorization-deny rendering =====

    /**
     * RBAC denials thrown from the {@code CapabilityAuthorizationAspect}
     * (@Before advice on controller methods) surface as handler-invocation
     * exceptions. They must render as HTTP 403 — never as a sanitized 500.
     *
     * <p>Regression: the generic {@code @ExceptionHandler(Exception.class)}
     * catch-all used to match {@code AccessDeniedException} first, turning a
     * deny-by-default decision into a 5xx (production: the audit/v2
     * CAPABILITY_NOT_FOUND deny surfaced as HTTP 500 instead of 403).</p>
     */
    @Test
    void accessDeniedException_renders403Not500() throws Exception {
        org.springframework.security.access.AccessDeniedException ex =
                new org.springframework.security.access.AccessDeniedException("غير مصرح بهذه العملية");

        // Direct handler-level assertion.
        ResponseEntity<Map<String, Object>> response = handler.handleAccessDenied(ex);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().get("status")).isEqualTo(403);
        // Safe message — never the Arabic deny detail, class name, or cause.
        assertThat(response.getBody().get("message")).isEqualTo("Authorization required");
        assertThat(response.getBody().toString()).doesNotContain("AccessDeniedException");

        // End-to-end MVC rendering: a controller-thrown AccessDeniedException
        // (exactly how the capability aspect surfaces denials) must be
        // resolved to 403 by the advice, not fall into the 500 catch-all.
        org.springframework.test.web.servlet.MockMvc mockMvc = org.springframework.test.web.servlet.setup
                .MockMvcBuilders.standaloneSetup(new DenyingController())
                .setControllerAdvice(handler)
                .build();
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/deny"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.status").value(403))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.message").value("Authorization required"));
    }

    @org.springframework.web.bind.annotation.RestController
    static class DenyingController {
        @org.springframework.web.bind.annotation.GetMapping("/deny")
        String deny() {
            throw new org.springframework.security.access.AccessDeniedException("capability not granted");
        }
    }

    // ===== Information disclosure tests =====

    @Test
    void genericException_returnsSafeBodyOnly_noLeak() {
        java.sql.SQLException rootCause = new java.sql.SQLException(
                "duplicate key value violates unique constraint \"uk_commerce_orders_tenant_number\"",
                "23505");
        RuntimeException ex = new RuntimeException(
                "PreparedStatementCallback; SQL [INSERT INTO commerce_orders ...]; "
                        + "nested exception is org.postgresql.util.PSQLException",
                rootCause);

        ResponseEntity<Map<String, Object>> response = handler.handleUnhandled(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        // Exactly four safe fields allowed — nothing else
        assertThat(body).hasSize(4);
        assertThat(body).containsOnlyKeys("status", "error", "message", "correlationId");
        assertThat(body.get("status")).isEqualTo(500);
        assertThat(body.get("error")).isEqualTo("Internal Server Error");
        assertThat(body.get("message")).isEqualTo("An unexpected error occurred");
        assertThat(body.get("correlationId")).isInstanceOf(String.class);
        assertThat((String) body.get("correlationId")).matches("[0-9a-fA-F-]{36}");

        // Forbidden internal diagnostic strings must NEVER appear in the body
        String bodyJson = body.toString();
        assertThat(bodyJson).doesNotContain("duplicate key");
        assertThat(bodyJson).doesNotContain("uk_commerce_orders");
        assertThat(bodyJson).doesNotContain("commerce_orders");
        assertThat(bodyJson).doesNotContain("PSQLException");
        assertThat(bodyJson).doesNotContain("SQLException");
        assertThat(bodyJson).doesNotContain("RuntimeException");
        assertThat(bodyJson).doesNotContain("exceptionType");
        assertThat(bodyJson).doesNotContain("cause");
        assertThat(bodyJson).doesNotContain("constraint");
        assertThat(bodyJson).doesNotContain("INSERT INTO");
        assertThat(bodyJson).doesNotContain("nested exception");
    }

    @Test
    void nullMessage_exception_doesNotLeakNullIndicator() {
        RuntimeException ex = new RuntimeException(); // null message
        ResponseEntity<Map<String, Object>> response = handler.handleUnhandled(ex);
        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body).hasSize(4);
        assertThat(body.get("message")).isEqualTo("An unexpected error occurred");
        assertThat(body.toString()).doesNotContain("No message available");
    }

    @Test
    void jdbcCredentialsInMessage_neverLeak() {
        RuntimeException ex = new RuntimeException(
                "Failed to obtain JDBC connection: jdbc:postgresql://prod-db.internal.snad.io:5432/snad?password=s3cretP@ss");
        ResponseEntity<Map<String, Object>> response = handler.handleUnhandled(ex);
        Map<String, Object> body = response.getBody();
        String bodyJson = body.toString();
        assertThat(bodyJson).doesNotContain("s3cretP@ss");
        assertThat(bodyJson).doesNotContain("prod-db.internal.snad.io");
        assertThat(bodyJson).doesNotContain("5432/snad");
    }
}
