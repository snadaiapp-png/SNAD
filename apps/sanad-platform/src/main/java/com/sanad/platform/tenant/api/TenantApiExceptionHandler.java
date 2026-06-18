package com.sanad.platform.tenant.api;

import com.sanad.platform.organization.api.ApiErrorResponse;
import com.sanad.platform.tenant.exception.TenantAlreadyExistsException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

/**
 * Exception handler for Tenant-specific exceptions.
 *
 * <p>This is a {@link RestControllerAdvice} (global) but only handles
 * the {@link TenantAlreadyExistsException} type. The generic exceptions
 * ({@link jakarta.persistence.EntityNotFoundException EntityNotFoundException},
 * {@link org.springframework.web.bind.MethodArgumentNotValidException
 * MethodArgumentNotValidException}, etc.) are already handled globally
 * by {@link OrganizationApiExceptionHandler} and reused as-is.</p>
 */
@RestControllerAdvice
public class TenantApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(TenantApiExceptionHandler.class);

    @ExceptionHandler(TenantAlreadyExistsException.class)
    public ResponseEntity<ApiErrorResponse> handleTenantAlreadyExists(
            TenantAlreadyExistsException ex, HttpServletRequest request) {

        log.warn("Conflict on {}: subdomain={}", request.getRequestURI(), ex.getSubdomain());

        ApiErrorResponse body = new ApiErrorResponse(
                Instant.now(),
                HttpStatus.CONFLICT.value(),
                HttpStatus.CONFLICT.getReasonPhrase(),
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }
}
