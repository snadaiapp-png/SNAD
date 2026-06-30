package com.sanad.platform.organization.api;

import com.sanad.platform.organization.exception.OrganizationAlreadyExistsException;
import com.sanad.platform.shared.api.ApiErrorResponse;
import com.sanad.platform.shared.api.ErrorCode;
import com.sanad.platform.shared.api.FieldValidationError;
import com.sanad.platform.shared.api.RequestIdFilter;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Stage 03A — Organization domain exception handler.
 *
 * <p>NOTE: The legacy {@code com.sanad.platform.organization.api.ApiErrorResponse}
 * POJO has been DELETED. All responses now use the unified shared
 * {@link ApiErrorResponse} record with Content-Type
 * {@code application/problem+json}. Raw {@code ex.getMessage()} is NEVER
 * returned to the client.</p>
 */
@RestControllerAdvice
public class OrganizationApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(OrganizationApiExceptionHandler.class);

    private String requestId() {
        return MDC.get(RequestIdFilter.MDC_KEY);
    }

    @ExceptionHandler(OrganizationAlreadyExistsException.class)
    public ResponseEntity<ApiErrorResponse> handleOrganizationAlreadyExists(
            OrganizationAlreadyExistsException ex, HttpServletRequest request) {
        log.warn("Organization conflict: requestId={} path={} tenantId={} name={}",
                requestId(), request.getRequestURI(), ex.getTenantId(), ex.getName());
        return build(ErrorCode.SANAD_CON_001,
                "An organization with this name already exists in the tenant.",
                request);
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleEntityNotFound(
            EntityNotFoundException ex, HttpServletRequest request) {
        log.warn("Not found: requestId={} path={} type={}",
                requestId(), request.getRequestURI(), ex.getClass().getSimpleName());
        return build(ErrorCode.SANAD_RES_001,
                "The requested organization was not found.", request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationFailure(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<FieldValidationError> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new FieldValidationError(fe.getField(), fe.getCode(), fe.getDefaultMessage()))
                .collect(Collectors.toList());
        return build(ErrorCode.SANAD_VAL_001, "One or more fields are invalid",
                request, errors);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingParameter(
            MissingServletRequestParameterException ex, HttpServletRequest request) {
        return build(ErrorCode.SANAD_VAL_003,
                "Required parameter '" + ex.getParameterName() + "' is missing",
                request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation(
            ConstraintViolationException ex, HttpServletRequest request) {
        List<FieldValidationError> errors = ex.getConstraintViolations().stream()
                .map(cv -> new FieldValidationError(
                        cv.getPropertyPath().toString(), "CONSTRAINT", cv.getMessage()))
                .collect(Collectors.toList());
        return build(ErrorCode.SANAD_VAL_001, "Constraint validation failed",
                request, errors);
    }

    private ResponseEntity<ApiErrorResponse> build(ErrorCode ec, String detail, HttpServletRequest req) {
        ApiErrorResponse body = new ApiErrorResponse(
                ec.code(), ec.title(), ec.status(),
                detail, req.getRequestURI(), requestId());
        return ResponseEntity.status(HttpStatus.valueOf(ec.status()))
                .contentType(MediaType.valueOf("application/problem+json"))
                .body(body);
    }

    private ResponseEntity<ApiErrorResponse> build(ErrorCode ec, String detail,
                                                     HttpServletRequest req,
                                                     List<FieldValidationError> errors) {
        ApiErrorResponse body = new ApiErrorResponse(
                ec.code(), ec.title(), ec.status(),
                detail, req.getRequestURI(), requestId(), errors);
        return ResponseEntity.status(HttpStatus.valueOf(ec.status()))
                .contentType(MediaType.valueOf("application/problem+json"))
                .body(body);
    }
}
