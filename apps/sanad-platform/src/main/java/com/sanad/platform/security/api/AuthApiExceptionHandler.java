package com.sanad.platform.security.api;

import com.sanad.platform.organization.api.ApiErrorResponse;
import com.sanad.platform.security.exception.AccountInactiveException;
import com.sanad.platform.security.exception.AmbiguousTenantException;
import com.sanad.platform.security.exception.InvalidCredentialsException;
import com.sanad.platform.security.exception.InvalidResetTokenException;
import com.sanad.platform.security.exception.LoginRateLimitException;
import com.sanad.platform.security.exception.PasswordResetRateLimitException;
import com.sanad.platform.security.exception.RefreshTokenReplayException;
import com.sanad.platform.security.exception.RegistrationConflictException;
import com.sanad.platform.security.exception.SelfRegistrationRateLimitException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;

/**
 * Exception handler for authentication endpoints.
 *
 * <p>Maps auth-domain exceptions to {@link ApiErrorResponse} bodies
 * with appropriate HTTP status codes. Never exposes passwords, tokens,
 * or internal security details in error messages.</p>
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackages = "com.sanad.platform.security")
public class AuthApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(AuthApiExceptionHandler.class);

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidCredentials(
            InvalidCredentialsException ex, HttpServletRequest request) {
        return error(HttpStatus.UNAUTHORIZED, ex.getMessage(), request, ex);
    }

    @ExceptionHandler(AccountInactiveException.class)
    public ResponseEntity<ApiErrorResponse> handleAccountInactive(
            AccountInactiveException ex, HttpServletRequest request) {
        return error(HttpStatus.UNAUTHORIZED, ex.getMessage(), request, ex);
    }

    @ExceptionHandler(RefreshTokenReplayException.class)
    public ResponseEntity<ApiErrorResponse> handleRefreshReplay(
            RefreshTokenReplayException ex, HttpServletRequest request) {
        return error(HttpStatus.UNAUTHORIZED, ex.getMessage(), request, ex);
    }

    @ExceptionHandler(AmbiguousTenantException.class)
    public ResponseEntity<ApiErrorResponse> handleAmbiguousTenant(
            AmbiguousTenantException ex, HttpServletRequest request) {
        java.util.List<String> tenantIdStrings = ex.getTenantIds().stream()
                .map(java.util.UUID::toString)
                .collect(java.util.stream.Collectors.toList());
        ApiErrorResponse response = new ApiErrorResponse(
                Instant.now(),
                409,
                "Conflict",
                ex.getMessage(),
                request.getRequestURI(),
                tenantIdStrings
        );
        return ResponseEntity.status(409).body(response);
    }

    @ExceptionHandler(LoginRateLimitException.class)
    public ResponseEntity<ApiErrorResponse> handleRateLimit(
            LoginRateLimitException ex, HttpServletRequest request) {
        log.warn("Auth rate limited: path={} message={}", request.getRequestURI(), ex.getMessage());
        return rateLimit(ex.getMessage(), request, "300");
    }

    @ExceptionHandler(InvalidResetTokenException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidResetToken(
            InvalidResetTokenException ex, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, ex.getMessage(), request, ex);
    }

    @ExceptionHandler(PasswordResetRateLimitException.class)
    public ResponseEntity<ApiErrorResponse> handlePasswordResetRateLimit(
            PasswordResetRateLimitException ex, HttpServletRequest request) {
        log.warn("Password reset rate limited: path={} message={}", request.getRequestURI(), ex.getMessage());
        return rateLimit(ex.getMessage(), request, "3600");
    }

    @ExceptionHandler(RegistrationConflictException.class)
    public ResponseEntity<ApiErrorResponse> handleRegistrationConflict(
            RegistrationConflictException ex, HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, ex.getMessage(), request, ex);
    }

    @ExceptionHandler(SelfRegistrationRateLimitException.class)
    public ResponseEntity<ApiErrorResponse> handleRegistrationRateLimit(
            SelfRegistrationRateLimitException ex, HttpServletRequest request) {
        log.warn("Registration rate limited: path={} message={}", request.getRequestURI(), ex.getMessage());
        return rateLimit(ex.getMessage(), request, "3600");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("Validation failed");
        return error(HttpStatus.BAD_REQUEST, message, request, ex);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingParam(
            MissingServletRequestParameterException ex, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST,
                "Missing required query parameter: " + ex.getParameterName(), request, ex);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST,
                "Invalid value for parameter: " + ex.getName(), request, ex);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(
            IllegalArgumentException ex, HttpServletRequest request) {
        String message = (ex.getMessage() != null && !ex.getMessage().isBlank())
                ? ex.getMessage() : "Invalid request";
        return error(HttpStatus.BAD_REQUEST, message, request, ex);
    }

    private ResponseEntity<ApiErrorResponse> rateLimit(
            String message, HttpServletRequest request, String retryAfter) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", retryAfter)
                .body(new ApiErrorResponse(
                        Instant.now(),
                        HttpStatus.TOO_MANY_REQUESTS.value(),
                        HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase(),
                        message,
                        request.getRequestURI()
                ));
    }

    private ResponseEntity<ApiErrorResponse> error(
            HttpStatus status, String message, HttpServletRequest request, Exception ex) {
        log.warn("Auth API request failed: method={} path={} status={} exception={}",
                request.getMethod(), request.getRequestURI(), status.value(),
                ex.getClass().getSimpleName());
        return ResponseEntity.status(status).body(new ApiErrorResponse(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                request.getRequestURI()
        ));
    }
}
