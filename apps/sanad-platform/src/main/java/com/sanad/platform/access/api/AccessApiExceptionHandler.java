package com.sanad.platform.access.api;

import com.sanad.platform.access.AccessConflictException;
import com.sanad.platform.access.AccessResourceNotFoundException;
import com.sanad.platform.shared.api.ApiErrorResponse;
import com.sanad.platform.shared.api.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Stage 03A — Access domain exception handler.
 *
 * <p>All responses use the unified {@link ApiErrorResponse} record (not the
 * legacy POJO) with Content-Type {@code application/problem+json}.
 * Raw {@code ex.getMessage()} is NEVER returned to the client.</p>
 */
@RestControllerAdvice(basePackages = "com.sanad.platform.access")
public class AccessApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(AccessApiExceptionHandler.class);

    private String requestId(HttpServletRequest req) {
        return org.slf4j.MDC.get("requestId");
    }

    @ExceptionHandler(AccessResourceNotFoundException.class)
    ResponseEntity<ApiErrorResponse> notFound(
            AccessResourceNotFoundException exception, HttpServletRequest request) {
        log.warn("Access resource not found: requestId={} path={} type={}",
                requestId(request), request.getRequestURI(), exception.getClass().getSimpleName());
        return build(ErrorCode.SANAD_RES_001, "The requested access resource was not found.", request);
    }

    @ExceptionHandler({AccessConflictException.class})
    ResponseEntity<ApiErrorResponse> conflict(
            RuntimeException exception, HttpServletRequest request) {
        log.warn("Access conflict: requestId={} path={} type={}",
                requestId(request), request.getRequestURI(), exception.getClass().getSimpleName());
        return build(ErrorCode.SANAD_CON_001, "An access conflict was detected.", request);
    }

    private ResponseEntity<ApiErrorResponse> build(ErrorCode ec, String detail, HttpServletRequest req) {
        ApiErrorResponse body = new ApiErrorResponse(
                ec.code(), ec.title(), ec.status(),
                detail, req.getRequestURI(),
                requestId(req));
        return ResponseEntity.status(HttpStatus.valueOf(ec.status()))
                .contentType(MediaType.valueOf("application/problem+json"))
                .body(body);
    }
}
