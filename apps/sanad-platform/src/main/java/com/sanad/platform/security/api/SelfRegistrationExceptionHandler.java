package com.sanad.platform.security.api;

import com.sanad.platform.organization.api.ApiErrorResponse;
import com.sanad.platform.security.exception.RegistrationConflictException;
import com.sanad.platform.security.exception.SelfRegistrationRateLimitException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

/** Error mapping dedicated to public account registration. */
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
@RestControllerAdvice(basePackages = "com.sanad.platform.security")
public class SelfRegistrationExceptionHandler {

    @ExceptionHandler(RegistrationConflictException.class)
    public ResponseEntity<ApiErrorResponse> handleConflict(
            RegistrationConflictException exception,
            HttpServletRequest request
    ) {
        return response(HttpStatus.CONFLICT, exception.getMessage(), request);
    }

    @ExceptionHandler(SelfRegistrationRateLimitException.class)
    public ResponseEntity<ApiErrorResponse> handleRateLimit(
            SelfRegistrationRateLimitException exception,
            HttpServletRequest request
    ) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", "3600")
                .body(body(HttpStatus.TOO_MANY_REQUESTS, exception.getMessage(), request));
    }

    private ResponseEntity<ApiErrorResponse> response(
            HttpStatus status,
            String message,
            HttpServletRequest request
    ) {
        return ResponseEntity.status(status).body(body(status, message, request));
    }

    private ApiErrorResponse body(HttpStatus status, String message, HttpServletRequest request) {
        return new ApiErrorResponse(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                request.getRequestURI());
    }
}
