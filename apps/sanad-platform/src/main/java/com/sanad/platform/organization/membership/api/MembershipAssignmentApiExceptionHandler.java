package com.sanad.platform.organization.membership.api;

import com.sanad.platform.organization.membership.exception.OrganizationMembershipNotFoundException;
import com.sanad.platform.organization.membership.exception.OrganizationMembershipUserLinkConflictException;
import com.sanad.platform.shared.api.ApiErrorResponse;
import com.sanad.platform.shared.api.ErrorCode;
import com.sanad.platform.shared.api.RequestIdFilter;
import com.sanad.platform.user.api.UserMembershipController;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Stage 03A — Membership-assignment exception handler.
 *
 * <p>Uses the unified shared {@link ApiErrorResponse} record (not the legacy POJO).
 * Raw {@code ex.getMessage()} is NEVER returned to the client.</p>
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = {
        OrganizationMembershipAssignmentController.class,
        UserMembershipController.class
})
public class MembershipAssignmentApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(MembershipAssignmentApiExceptionHandler.class);

    private String requestId() {
        return MDC.get(RequestIdFilter.MDC_KEY);
    }

    @ExceptionHandler(OrganizationMembershipUserLinkConflictException.class)
    public ResponseEntity<ApiErrorResponse> handleConflict(
            OrganizationMembershipUserLinkConflictException exception,
            HttpServletRequest request) {
        log.warn("Assignment conflict: requestId={} path={}",
                requestId(), request.getRequestURI());
        return build(ErrorCode.SANAD_CON_001,
                "The membership assignment conflicts with an existing link.",
                request);
    }

    @ExceptionHandler({OrganizationMembershipNotFoundException.class, EntityNotFoundException.class})
    public ResponseEntity<ApiErrorResponse> handleNotFound(
            RuntimeException exception, HttpServletRequest request) {
        log.warn("Assignment not found: requestId={} path={} type={}",
                requestId(), request.getRequestURI(), exception.getClass().getSimpleName());
        return build(ErrorCode.SANAD_RES_001,
                "The requested membership or assignment was not found.", request);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingParameter(
            MissingServletRequestParameterException exception,
            HttpServletRequest request) {
        return build(ErrorCode.SANAD_VAL_003,
                "Required parameter '" + exception.getParameterName() + "' is missing",
                request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException exception,
            HttpServletRequest request) {
        return build(ErrorCode.SANAD_VAL_004,
                "Parameter type mismatch for '" + exception.getName() + "'",
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
}
