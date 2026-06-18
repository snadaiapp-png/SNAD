package com.sanad.platform.organization.api;

import com.sanad.platform.organization.exception.OrganizationAlreadyExistsException;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.stream.Collectors;

/**
 * Exception handler for the Organization REST API.
 *
 * <p>Translates domain/persistence exceptions into HTTP responses with a
 * structured {@link ApiErrorResponse} body. The handler is scoped to the
 * whole application (via {@link RestControllerAdvice}) but only handles
 * exception types raised by the Organization use cases; other controllers
 * can register their own handlers or rely on Spring Boot's default
 * error handling.</p>
 *
 * <h2>Mapping Rules</h2>
 * <ul>
 *   <li>{@link OrganizationAlreadyExistsException} -> {@code 409 Conflict}</li>
 *   <li>{@link EntityNotFoundException}           -> {@code 404 Not Found}</li>
 *   <li>{@link MethodArgumentNotValidException}   -> {@code 400 Bad Request}
 *       (with a comma-separated list of field errors in the message)</li>
 * </ul>
 */
@RestControllerAdvice
public class OrganizationApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(OrganizationApiExceptionHandler.class);

    /**
     * Handle duplicate Organization creation attempts.
     */
    @ExceptionHandler(OrganizationAlreadyExistsException.class)
    public ResponseEntity<ApiErrorResponse> handleOrganizationAlreadyExists(
            OrganizationAlreadyExistsException ex, HttpServletRequest request) {

        log.warn("Conflict on {}: tenantId={} name={}",
                request.getRequestURI(), ex.getTenantId(), ex.getName());

        ApiErrorResponse body = new ApiErrorResponse(
                Instant.now(),
                HttpStatus.CONFLICT.value(),
                HttpStatus.CONFLICT.getReasonPhrase(),
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    /**
     * Handle missing-Tenant (or any other entity-not-found) errors.
     */
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

    /**
     * Handle Bean Validation failures on the request body.
     *
     * <p>The message field aggregates all field-level errors into a
     * comma-separated list so clients can see every problem at once
     * instead of fixing them one at a time.</p>
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationFailure(
            MethodArgumentNotValidException ex, HttpServletRequest request) {

        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(this::formatFieldError)
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

    private String formatFieldError(FieldError fe) {
        return fe.getField() + ": " + fe.getDefaultMessage();
    }

    /**
     * Handle missing required query parameters (e.g. tenantId on GET endpoints).
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingParameter(
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

    /**
     * Handle constraint violations (e.g. @RequestParam validation, path variable
     * type conversion errors that surface as ConstraintViolationException).
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation(
            ConstraintViolationException ex, HttpServletRequest request) {

        String message = ex.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .collect(Collectors.joining("; "));
        if (message.isEmpty()) {
            message = "Constraint violation";
        }
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
