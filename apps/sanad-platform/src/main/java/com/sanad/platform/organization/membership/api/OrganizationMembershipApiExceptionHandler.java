package com.sanad.platform.organization.membership.api;

import com.sanad.platform.organization.membership.exception.OrganizationMembershipAlreadyExistsException;
import com.sanad.platform.organization.membership.exception.OrganizationMembershipNotFoundException;
import com.sanad.platform.shared.api.ApiErrorResponse;
import com.sanad.platform.shared.api.ErrorCode;
import com.sanad.platform.shared.api.FieldValidationError;
import com.sanad.platform.shared.api.RequestIdFilter;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
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
 * Stage 03A — Membership domain exception handler.
 *
 * <p>Uses the unified shared {@link ApiErrorResponse} record (not the legacy POJO)
 * with Content-Type {@code application/problem+json}. Raw {@code ex.getMessage()}
 * is NEVER returned to the client.</p>
 */
@RestControllerAdvice
public class OrganizationMembershipApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(OrganizationMembershipApiExceptionHandler.class);

    private String requestId() {
        return MDC.get(RequestIdFilter.MDC_KEY);
    }

    @ExceptionHandler(OrganizationMembershipAlreadyExistsException.class)
    public ResponseEntity<ApiErrorResponse> handleAlreadyExists(
            OrganizationMembershipAlreadyExistsException ex, HttpServletRequest request) {
        log.warn("Membership conflict: requestId={} path={} tenantId={} orgId={} email={}",
                requestId(), request.getRequestURI(), ex.getTenantId(),
                ex.getOrganizationId(), ex.getEmail());
        return build(ErrorCode.SANAD_CON_001,
                "A membership with these parameters already exists.", request);
    }

    @ExceptionHandler(OrganizationMembershipNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(
            OrganizationMembershipNotFoundException ex, HttpServletRequest request) {
        log.warn("Membership not found: requestId={} path={} membershipId={}",
                requestId(), request.getRequestURI(), ex.getMembershipId());
        return build(ErrorCode.SANAD_RES_001,
                "The requested membership was not found.", request);
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleEntityNotFound(
            EntityNotFoundException ex, HttpServletRequest request) {
        log.warn("Entity not found: requestId={} path={} type={}",
                requestId(), request.getRequestURI(), ex.getClass().getSimpleName());
        return build(ErrorCode.SANAD_RES_001,
                "The requested resource was not found.", request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<FieldValidationError> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new FieldValidationError(fe.getField(), fe.getCode(), fe.getDefaultMessage()))
                .collect(Collectors.toList());
        return build(ErrorCode.SANAD_VAL_001, "One or more fields are invalid",
                request, errors);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingParam(
            MissingServletRequestParameterException ex, HttpServletRequest request) {
        return build(ErrorCode.SANAD_VAL_003,
                "Required parameter '" + ex.getParameterName() + "' is missing",
                request);
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
