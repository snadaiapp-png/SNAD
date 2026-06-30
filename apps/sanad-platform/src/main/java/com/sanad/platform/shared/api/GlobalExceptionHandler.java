package com.sanad.platform.shared.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Global exception handler that produces unified ApiErrorResponse bodies
 * with Content-Type application/problem+json.
 * This supplements (does not replace) the domain-specific exception handlers.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private String requestId() {
        String id = org.slf4j.MDC.get(RequestIdFilter.MDC_KEY);
        return id != null ? id : UUID.randomUUID().toString();
    }

    private String path(HttpServletRequest req) {
        return req != null ? req.getRequestURI() : "unknown";
    }

    private ResponseEntity<ApiErrorResponse> build(ErrorCode ec, String detail, HttpServletRequest req) {
        ApiErrorResponse body = new ApiErrorResponse(ec.code(), ec.title(), ec.status(),
            detail, path(req), requestId());
        return ResponseEntity.status(ec.status())
            .contentType(MediaType.valueOf("application/problem+json"))
            .body(body);
    }

    private ResponseEntity<ApiErrorResponse> build(ErrorCode ec, String detail,
                                                     HttpServletRequest req, List<FieldValidationError> errors) {
        ApiErrorResponse body = new ApiErrorResponse(ec.code(), ec.title(), ec.status(),
            detail, path(req), requestId(), errors);
        return ResponseEntity.status(ec.status())
            .contentType(MediaType.valueOf("application/problem+json"))
            .body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        List<FieldValidationError> errors = ex.getBindingResult().getFieldErrors().stream()
            .map(fe -> new FieldValidationError(fe.getField(), fe.getCode(), fe.getDefaultMessage()))
            .collect(Collectors.toList());
        return build(ErrorCode.SANAD_VAL_001, "One or more fields are invalid", req, errors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraint(ConstraintViolationException ex, HttpServletRequest req) {
        List<FieldValidationError> errors = ex.getConstraintViolations().stream()
            .map(cv -> new FieldValidationError(cv.getPropertyPath().toString(), "CONSTRAINT", cv.getMessage()))
            .collect(Collectors.toList());
        return build(ErrorCode.SANAD_VAL_001, "Constraint validation failed", req, errors);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleMalformed(HttpMessageNotReadableException ex, HttpServletRequest req) {
        return build(ErrorCode.SANAD_VAL_002, "Request body is malformed or unreadable", req);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingParam(MissingServletRequestParameterException ex, HttpServletRequest req) {
        return build(ErrorCode.SANAD_VAL_003, "Required parameter '" + ex.getParameterName() + "' is missing", req);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest req) {
        return build(ErrorCode.SANAD_VAL_004, "Parameter type mismatch for '" + ex.getName() + "'", req);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiErrorResponse> handleAuth(AuthenticationException ex, HttpServletRequest req) {
        return build(ErrorCode.SANAD_AUTH_001, "Authentication is required", req);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(AccessDeniedException ex, HttpServletRequest req) {
        return build(ErrorCode.SANAD_SEC_001, "Access is denied for this resource", req);
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(EntityNotFoundException ex, HttpServletRequest req) {
        return build(ErrorCode.SANAD_RES_001, "The requested resource was not found", req);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConflict(DataIntegrityViolationException ex, HttpServletRequest req) {
        return build(ErrorCode.SANAD_CON_001, "A data conflict occurred", req);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArg(IllegalArgumentException ex, HttpServletRequest req) {
        return build(ErrorCode.SANAD_BIZ_001, ex.getMessage() != null ? ex.getMessage() : "Business rule violation", req);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalState(IllegalStateException ex, HttpServletRequest req) {
        return build(ErrorCode.SANAD_BIZ_001, ex.getMessage() != null ? ex.getMessage() : "Business rule violation", req);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiErrorResponse> handleResponseStatus(ResponseStatusException ex, HttpServletRequest req) {
        ErrorCode ec = switch (ex.getStatusCode().value()) {
            case 400 -> ErrorCode.SANAD_VAL_001;
            case 401 -> ErrorCode.SANAD_AUTH_001;
            case 403 -> ErrorCode.SANAD_SEC_001;
            case 404 -> ErrorCode.SANAD_RES_001;
            case 409 -> ErrorCode.SANAD_CON_001;
            case 429 -> ErrorCode.SANAD_RATE_001;
            default -> ErrorCode.SANAD_GEN_001;
        };
        return build(ec, ex.getReason() != null ? ex.getReason() : ec.title(), req);
    }

    // Note: A catch-all Exception handler is intentionally omitted to avoid
    // interfering with Spring Boot's internal error handling for actuator
    // endpoints and other non-controller paths. Domain-specific handlers
    // should be added as needed for business exceptions.
}
