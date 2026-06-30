package com.sanad.platform.user.api;

import com.sanad.platform.shared.api.ApiErrorResponse;
import com.sanad.platform.shared.api.ErrorCode;
import com.sanad.platform.shared.api.FieldValidationError;
import com.sanad.platform.shared.api.RequestIdFilter;
import com.sanad.platform.user.exception.DuplicateUserEmailException;
import com.sanad.platform.user.exception.UserNotFoundException;
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
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Stage 03A — User domain exception handler.
 *
 * <p>All responses use the unified {@link ApiErrorResponse} record (not the
 * legacy POJO) with Content-Type {@code application/problem+json}.
 * Raw {@code ex.getMessage()} is NEVER returned to the client.</p>
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = UserController.class)
public class UserApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(UserApiExceptionHandler.class);

    private String requestId() {
        return MDC.get(RequestIdFilter.MDC_KEY);
    }

    @ExceptionHandler(DuplicateUserEmailException.class)
    public ResponseEntity<ApiErrorResponse> handleDuplicateEmail(
            DuplicateUserEmailException ex, HttpServletRequest request) {
        log.warn("Duplicate user email: requestId={} path={} type={}",
                requestId(), request.getRequestURI(), ex.getClass().getSimpleName());
        return build(ErrorCode.SANAD_CON_001, "A user with this email already exists.", request);
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleUserNotFound(
            UserNotFoundException ex, HttpServletRequest request) {
        log.warn("User not found: requestId={} path={}", requestId(), request.getRequestURI());
        return build(ErrorCode.SANAD_RES_001, "The requested user was not found.", request);
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleEntityNotFound(
            EntityNotFoundException ex, HttpServletRequest request) {
        log.warn("Entity not found: requestId={} path={} type={}",
                requestId(), request.getRequestURI(), ex.getClass().getSimpleName());
        return build(ErrorCode.SANAD_RES_001, "The requested resource was not found.", request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<FieldValidationError> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new FieldValidationError(fe.getField(), fe.getCode(), fe.getDefaultMessage()))
                .collect(Collectors.toList());
        return build(ErrorCode.SANAD_VAL_001, "One or more fields are invalid", request, errors);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingParameter(
            MissingServletRequestParameterException ex, HttpServletRequest request) {
        return build(ErrorCode.SANAD_VAL_003,
                "Required parameter '" + ex.getParameterName() + "' is missing",
                request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        return build(ErrorCode.SANAD_VAL_004,
                "Parameter type mismatch for '" + ex.getName() + "'",
                request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(
            IllegalArgumentException ex, HttpServletRequest request) {
        // IllegalArgumentException messages can echo caller input — use a safe static detail.
        log.warn("Illegal argument: requestId={} path={} type={}",
                requestId(), request.getRequestURI(), ex.getClass().getSimpleName());
        return build(ErrorCode.SANAD_VAL_001, "The request contained an invalid argument.", request);
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
