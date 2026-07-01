package com.sanad.platform.audit.api;

import com.sanad.platform.audit.exception.AuditEventNotFoundException;
import com.sanad.platform.shared.api.ApiErrorResponse;
import com.sanad.platform.shared.api.ErrorCode;
import com.sanad.platform.shared.api.RequestIdFilter;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Stage 05 §22 — Exception handler for the audit bounded context.
 */
@RestControllerAdvice
public class AuditEventApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(AuditEventApiExceptionHandler.class);

    private String requestId() {
        return MDC.get(RequestIdFilter.MDC_KEY);
    }

    @ExceptionHandler(AuditEventNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleAuditNotFound(
            AuditEventNotFoundException ex, HttpServletRequest request) {
        log.warn("Audit event not found: requestId={} path={} eventId={}",
                requestId(), request.getRequestURI(), ex.getEventId());
        return build(ErrorCode.SANAD_RES_001, "The requested audit event was not found.", request);
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleEntityNotFound(
            EntityNotFoundException ex, HttpServletRequest request) {
        log.warn("Audit entity not found: requestId={} path={}",
                requestId(), request.getRequestURI());
        return build(ErrorCode.SANAD_RES_001, "The requested audit event was not found.", request);
    }

    private ResponseEntity<ApiErrorResponse> build(ErrorCode ec, String detail, HttpServletRequest req) {
        ApiErrorResponse body = new ApiErrorResponse(
                ec.code(), ec.title(), ec.status(), detail, req.getRequestURI(), requestId());
        return ResponseEntity.status(HttpStatus.valueOf(ec.status()))
                .contentType(MediaType.valueOf("application/problem+json"))
                .body(body);
    }
}
