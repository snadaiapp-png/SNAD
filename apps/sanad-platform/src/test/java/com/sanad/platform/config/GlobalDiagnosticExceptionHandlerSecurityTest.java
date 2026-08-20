package com.sanad.platform.config;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.method.annotation.ExceptionHandlerMethodResolver;
import org.springframework.web.servlet.mvc.method.annotation.ServletInvocableHandlerMethod;

import java.lang.reflect.Method;
import java.sql.SQLException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression test for the production-safe {@link GlobalDiagnosticExceptionHandler}.
 *
 * <p>Verifies that <em>no</em> internal diagnostic information (exception
 * class name, raw exception message, root cause, SQL error text, constraint
 * name, table name) is ever returned in an HTTP 500 response body. Only the
 * safe fields {@code status}, {@code error}, {@code message}, and
 * {@code correlationId} are permitted.
 *
 * <p>This test prevents regression of the previous diagnostic disclosure
 * behaviour that returned {@code exceptionType}, {@code message} (raw),
 * and {@code cause} directly to clients.
 */
class GlobalDiagnosticExceptionHandlerSecurityTest {

    private final GlobalDiagnosticExceptionHandler handler = new GlobalDiagnosticExceptionHandler();

    @Test
    void genericException_returnsSafeBodyOnly() {
        // Given a SQL exception leaking internal DB details
        SQLException rootCause = new SQLException(
                "duplicate key value violates unique constraint \"uk_commerce_orders_tenant_number\"",
                "23505");
        RuntimeException ex = new RuntimeException(
                "PreparedStatementCallback; SQL [INSERT INTO commerce_orders ...]; "
                        + "nested exception is org.postgresql.util.PSQLException",
                rootCause);

        // When
        ResponseEntity<Map<String, Object>> response = handler.handleUnhandled(ex);

        // Then — HTTP 500
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);

        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();

        // Exactly four safe fields allowed — nothing else
        assertThat(body).hasSize(4);
        assertThat(body).containsOnlyKeys("status", "error", "message", "correlationId");

        // Safe values
        assertThat(body.get("status")).isEqualTo(500);
        assertThat(body.get("error")).isEqualTo("Internal Server Error");
        assertThat(body.get("message")).isEqualTo("An unexpected error occurred");
        assertThat(body.get("correlationId")).isInstanceOf(String.class);
        assertThat((String) body.get("correlationId")).matches("[0-9a-fA-F-]{36}");

        // Forbidden internal diagnostic strings must NEVER appear anywhere in the body
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
        // No "No message available" leak
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
