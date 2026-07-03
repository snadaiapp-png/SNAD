package com.sanad.platform.organization.membership.api;

import com.sanad.platform.organization.api.ApiErrorResponse;
import com.sanad.platform.organization.membership.exception.OrganizationMembershipAlreadyExistsException;
import com.sanad.platform.organization.membership.exception.OrganizationMembershipNotFoundException;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.stream.Collectors;

/**
 * Exception handler for Organization Membership REST API.
 *
 * <p>Reuses the existing {@link ApiErrorResponse} structured body.
 * The generic handlers (EntityNotFoundException, MethodArgumentNotValidException,
 * MissingServletRequestParameterException) are also declared here so that
 * membership-specific exception messages are not swallowed by the Organization
 * handler. Spring will pick the most specific {@code @RestControllerAdvice}
 * for each exception type.</p>
 */
@RestControllerAdvice
public class OrganizationMembershipApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(OrganizationMembershipApiExceptionHandler.class);

    @ExceptionHandler(OrganizationMembershipAlreadyExistsException.class)
    public ResponseEntity<ApiErrorResponse> handleAlreadyExists(
            OrganizationMembershipAlreadyExistsException ex, HttpServletRequest request) {

        log.warn("Conflict on {}: email={}", request.getRequestURI(), ex.getEmail());

        ApiErrorResponse body = new ApiErrorResponse(
                Instant.now(),
                HttpStatus.CONFLICT.value(),
                HttpStatus.CONFLICT.getReasonPhrase(),
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(OrganizationMembershipNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(
            OrganizationMembershipNotFoundException ex, HttpServletRequest request) {

        log.warn("Not found on {}: membershipId={}", request.getRequestURI(), ex.getMembershipId());

        ApiErrorResponse body = new ApiErrorResponse(
                Instant.now(),
                HttpStatus.NOT_FOUND.value(),
                HttpStatus.NOT_FOUND.getReasonPhrase(),
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleEntityNotFound(
            EntityNotFoundException ex, HttpServletRequest request) {

        log.warn("Not found on {}: {}", request.getRequestURI(), ex.getMessage());

        ApiErrorResponse body = new ApiErrorResponse(
                Instant.now(),
                HttpStatus.NOT_FOUND.value(),
                HttpStatus.NOT_FOUND.getReasonPhrase(),
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {

        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));

        log.warn("Bad request on {}: {}", request.getRequestURI(), message);

        ApiErrorResponse body = new ApiErrorResponse(
                Instant.now(),
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                message.isEmpty() ? "Validation failed" : message,
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingParam(
            MissingServletRequestParameterException ex, HttpServletRequest request) {

        String message = "Missing required query parameter: " + ex.getParameterName();
        log.warn("Bad request on {}: {}", request.getRequestURI(), message);

        ApiErrorResponse body = new ApiErrorResponse(
                Instant.now(),
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                message,
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }
}
