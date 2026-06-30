package com.sanad.platform.shared.api;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ApiErrorResponseTest {

    @Test
    void createsResponseWithAllFields() {
        var error = new ApiErrorResponse(
            "SANAD-VAL-001", "Validation failed", 400,
            "email is invalid", "/api/v1/users", "req-123",
            List.of(new FieldValidationError("email", "INVALID", "must be valid"))
        );
        assertEquals("SANAD-VAL-001", error.code());
        assertEquals(400, error.status());
        assertEquals("req-123", error.requestId());
        assertNotNull(error.timestamp());
        assertEquals(1, error.errors().size());
    }

    @Test
    void createsResponseWithoutErrors() {
        var error = new ApiErrorResponse(
            "SANAD-GEN-001", "Unexpected error", 500,
            "Something went wrong", "/api/v1/users", "req-456"
        );
        assertNull(error.errors());
        assertEquals("SANAD-GEN-001", error.code());
    }

    @Test
    void typeUriIsGenerated() {
        var error = new ApiErrorResponse(
            "SANAD-VAL-001", "Validation", 400, "detail", "/path", "req"
        );
        assertTrue(error.type().contains("val-001"));
    }

    @Test
    void timestampIsPopulated() {
        var error = new ApiErrorResponse(
            "SANAD-GEN-001", "Error", 500, "detail", "/path", "req"
        );
        assertNotNull(error.timestamp());
        assertTrue(error.timestamp().isBefore(Instant.now().plusSeconds(1)));
    }
}
