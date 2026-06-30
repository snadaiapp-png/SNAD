package com.sanad.platform.security.api;

import com.sanad.platform.security.exception.AccountInactiveException;
import com.sanad.platform.security.exception.AmbiguousTenantException;
import com.sanad.platform.security.exception.InvalidCredentialsException;
import com.sanad.platform.security.exception.InvalidResetTokenException;
import com.sanad.platform.security.exception.LoginRateLimitException;
import com.sanad.platform.security.exception.PasswordResetRateLimitException;
import com.sanad.platform.security.exception.RefreshTokenReplayException;
import com.sanad.platform.security.exception.RegistrationConflictException;
import com.sanad.platform.security.exception.SelfRegistrationRateLimitException;
import com.sanad.platform.shared.api.ApiErrorResponse;
import com.sanad.platform.shared.api.ErrorCode;
import com.sanad.platform.shared.api.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Stage 03A — Auth domain exception handler.
 *
 * <p>Uses the unified shared {@link ApiErrorResponse} record (not the legacy
 * POJO) with Content-Type {@code application/problem+json}.
 * Raw {@code ex.getMessage()} is NEVER returned to the client; only static,
 * message-catalog style details are used.</p>
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackages = "com.sanad.platform.security")
public class AuthApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(AuthApiExceptionHandler.class);

    private String requestId() {
        return MDC.get(RequestIdFilter.MDC_KEY);
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidCredentials(
            InvalidCredentialsException ex, HttpServletRequest request) {
        log.warn("Invalid credentials: requestId={} path={}", requestId(), request.getRequestURI());
        return build(ErrorCode.SANAD_AUTH_002, "The provided credentials are invalid.", request);
    }

    @ExceptionHandler(AccountInactiveException.class)
    public ResponseEntity<ApiErrorResponse> handleAccountInactive(
            AccountInactiveException ex, HttpServletRequest request) {
        log.warn("Account inactive: requestId={} path={}", requestId(), request.getRequestURI());
        return build(ErrorCode.SANAD_AUTH_001, "The account is not active.", request);
    }

    @ExceptionHandler(RefreshTokenReplayException.class)
    public ResponseEntity<ApiErrorResponse> handleRefreshReplay(
            RefreshTokenReplayException ex, HttpServletRequest request) {
        log.warn("Refresh token replay: requestId={} path={}", requestId(), request.getRequestURI());
        return build(ErrorCode.SANAD_AUTH_004, "The session has been revoked.", request);
    }

    @ExceptionHandler(AmbiguousTenantException.class)
    public ResponseEntity<ApiErrorResponse> handleAmbiguousTenant(
            AmbiguousTenantException ex, HttpServletRequest request) {
        // Tenant IDs are UUIDs — safe to log, but the message detail is static.
        log.warn("Ambiguous tenant: requestId={} path={} count={}",
                requestId(), request.getRequestURI(),
                ex.getTenantIds() == null ? 0 : ex.getTenantIds().size());
        return build(ErrorCode.SANAD_TEN_003,
                "Multiple tenants match this identity — tenant disambiguation required.",
                request);
    }

    @ExceptionHandler(LoginRateLimitException.class)
    public ResponseEntity<ApiErrorResponse> handleRateLimit(
            LoginRateLimitException ex, HttpServletRequest request) {
        log.warn("Login rate limited: requestId={} path={}", requestId(), request.getRequestURI());
        return rateLimit(request, "300");
    }

    @ExceptionHandler(InvalidResetTokenException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidResetToken(
            InvalidResetTokenException ex, HttpServletRequest request) {
        log.warn("Invalid reset token: requestId={} path={}", requestId(), request.getRequestURI());
        return build(ErrorCode.SANAD_VAL_001, "The reset token is invalid or expired.", request);
    }

    @ExceptionHandler(PasswordResetRateLimitException.class)
    public ResponseEntity<ApiErrorResponse> handlePasswordResetRateLimit(
            PasswordResetRateLimitException ex, HttpServletRequest request) {
        log.warn("Password reset rate limited: requestId={} path={}", requestId(), request.getRequestURI());
        return rateLimit(request, "3600");
    }

    @ExceptionHandler(RegistrationConflictException.class)
    public ResponseEntity<ApiErrorResponse> handleRegistrationConflict(
            RegistrationConflictException ex, HttpServletRequest request) {
        log.warn("Registration conflict: requestId={} path={}", requestId(), request.getRequestURI());
        return build(ErrorCode.SANAD_CON_001, "A resource with this identifier already exists.",
                request);
    }

    @ExceptionHandler(SelfRegistrationRateLimitException.class)
    public ResponseEntity<ApiErrorResponse> handleRegistrationRateLimit(
            SelfRegistrationRateLimitException ex, HttpServletRequest request) {
        log.warn("Self-registration rate limited: requestId={} path={}", requestId(), request.getRequestURI());
        return rateLimit(request, "3600");
    }

    private ResponseEntity<ApiErrorResponse> rateLimit(HttpServletRequest request, String retryAfter) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Retry-After", retryAfter);
        ApiErrorResponse body = new ApiErrorResponse(
                ErrorCode.SANAD_RATE_001.code(),
                ErrorCode.SANAD_RATE_001.title(),
                ErrorCode.SANAD_RATE_001.status(),
                "Rate limit exceeded. Retry after the indicated delay.",
                request.getRequestURI(),
                requestId());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .headers(headers)
                .contentType(MediaType.valueOf("application/problem+json"))
                .body(body);
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
