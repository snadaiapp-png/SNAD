package com.sanad.platform.shared.api;

import com.sanad.platform.shared.api.exceptions.BusinessRuleException;
import com.sanad.platform.shared.api.exceptions.ConflictException;
import com.sanad.platform.shared.api.exceptions.InvalidPaginationException;
import com.sanad.platform.shared.api.exceptions.ResourceNotFoundException;
import com.sanad.platform.shared.api.exceptions.TenantContextException;
import com.sanad.platform.shared.api.exceptions.TypedBusinessException;
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
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.beans.BeanInstantiationException;
import org.springframework.validation.BindException;

import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Global exception handler that produces unified {@link ApiErrorResponse} bodies
 * with Content-Type {@code application/problem+json}.
 *
 * <h2>Stage 03A — Exception Safety</h2>
 * <ul>
 *   <li>Never returns {@code ex.getMessage()} to the client for unexpected exceptions.</li>
 *   <li>Catch-all {@code @ExceptionHandler(Exception.class)} returns
 *       {@code SANAD-GEN-001 / 500 / "An unexpected error occurred"} with the
 *       requestId logged for correlation.</li>
 *   <li>Actuator endpoints are NOT covered by this advice (proven by
 *       {@code GlobalExceptionHandlerActuatorExclusionTest}).</li>
 *   <li>Domain-specific {@code @RestControllerAdvice} classes (Auth, User, Organization,
 *       Access, Membership) remain in place for domain exception types but now
 *       all produce the unified {@link ApiErrorResponse} record.</li>
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @org.springframework.beans.factory.annotation.Autowired
    private com.sanad.platform.audit.service.TenantSecurityDenialAuditService tenantDenialAuditService;

    /** Safe generic detail returned for unexpected exceptions. */
    public static final String GENERIC_ERROR_DETAIL =
            "An unexpected error occurred. Please retry, and contact support with the request ID if the issue persists.";

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

    // ------------------------------------------------------------------
    // Typed business exceptions (Stage 03A)
    // ------------------------------------------------------------------

    @ExceptionHandler(TypedBusinessException.class)
    public ResponseEntity<ApiErrorResponse> handleTypedBusiness(
            TypedBusinessException ex, HttpServletRequest req) {
        // Safe: detail is a static, message-catalog style string set at construction.
        // Diagnostics are logged but never sent to the client.
        if (ex.errorCode().status() >= 500) {
            log.error("Business error [{}] requestId={} path={} diagnostics={}",
                    ex.errorCode().code(), requestId(), path(req), ex.diagnostics(), ex);
        } else {
            log.warn("Business error [{}] requestId={} path={} diagnostics={}",
                    ex.errorCode().code(), requestId(), path(req), ex.diagnostics());
        }
        return build(ex.errorCode(), ex.safeClientDetail(), req);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleResourceNotFound(
            ResourceNotFoundException ex, HttpServletRequest req) {
        return handleTypedBusiness(ex, req);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiErrorResponse> handleConflict(
            ConflictException ex, HttpServletRequest req) {
        return handleTypedBusiness(ex, req);
    }

    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<ApiErrorResponse> handleBusinessRule(
            BusinessRuleException ex, HttpServletRequest req) {
        return handleTypedBusiness(ex, req);
    }

    @ExceptionHandler(TenantContextException.class)
    public ResponseEntity<ApiErrorResponse> handleTenantContext(
            TenantContextException ex, HttpServletRequest req) {
        return handleTypedBusiness(ex, req);
    }

    // ------------------------------------------------------------------
    // Stage 05A.2.2 §4 — Capability denial
    // ------------------------------------------------------------------

    @ExceptionHandler(com.sanad.platform.security.authorization.CapabilityDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleCapabilityDenied(
            com.sanad.platform.security.authorization.CapabilityDeniedException ex,
            HttpServletRequest req) {
        log.warn("Capability denied: requestId={} path={} tenant={} user={} capability={} reason={}",
                requestId(), req.getRequestURI(), ex.getTenantId(),
                ex.getUserId(), ex.getCapabilityCode(), ex.getReason());
        // Stage 05A.2.8: Record tenant-scoped denial audit (REQUIRES_NEW)
        try {
            if (tenantDenialAuditService != null) {
                tenantDenialAuditService.recordDenial(
                    "CAPABILITY_DENIED",
                    "Organization",
                    "ACCESS",
                    "SANAD-SEC-001",
                    "Capability denied: " + ex.getCapabilityCode(),
                    403);
            }
        } catch (Exception auditEx) {
            log.warn("Failed to record capability denial audit: {}", auditEx.getMessage());
        }
        return build(ErrorCode.SANAD_SEC_001, "Access denied — capability required: " + ex.getCapabilityCode(), req);
    }

    // ------------------------------------------------------------------
    // Stage 05A.2.9 §10 — Idempotency HTTP semantics
    // ------------------------------------------------------------------

    @ExceptionHandler(com.sanad.platform.idempotency.service.IdempotentCommandExecutor.IdempotencyPayloadConflictException.class)
    public ResponseEntity<ApiErrorResponse> handleIdempotencyConflict(
            com.sanad.platform.idempotency.service.IdempotentCommandExecutor.IdempotencyPayloadConflictException ex,
            HttpServletRequest req) {
        return build(ErrorCode.SANAD_IDEMP_002, "Idempotency key conflict — payload mismatch", req);
    }

    @ExceptionHandler(com.sanad.platform.idempotency.service.IdempotentCommandExecutor.IdempotencyInProgressException.class)
    public ResponseEntity<ApiErrorResponse> handleIdempotencyInProgress(
            com.sanad.platform.idempotency.service.IdempotentCommandExecutor.IdempotencyInProgressException ex,
            HttpServletRequest req) {
        return build(ErrorCode.SANAD_IDEMP_003, "Request is still processing — retry later", req);
    }

    @ExceptionHandler(com.sanad.platform.idempotency.service.IdempotentCommandExecutor.IdempotencyExpiredException.class)
    public ResponseEntity<ApiErrorResponse> handleIdempotencyExpired(
            com.sanad.platform.idempotency.service.IdempotentCommandExecutor.IdempotencyExpiredException ex,
            HttpServletRequest req) {
        return build(ErrorCode.SANAD_IDEMP_004, "Idempotency record has expired", req);
    }

    @ExceptionHandler(InvalidPaginationException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidPagination(
            InvalidPaginationException ex, HttpServletRequest req) {
        return handleTypedBusiness(ex, req);
    }

    // ------------------------------------------------------------------
    // Spring / Jakarta standard exceptions
    // ------------------------------------------------------------------

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
        // Never propagate the parser's underlying error message — it can echo the malformed body.
        log.warn("Malformed request body: requestId={} path={}", requestId(), path(req));
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
        log.warn("Authentication failed: requestId={} path={} type={}",
                requestId(), path(req), ex.getClass().getSimpleName());
        return build(ErrorCode.SANAD_AUTH_001, "Authentication is required", req);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(AccessDeniedException ex, HttpServletRequest req) {
        log.warn("Access denied: requestId={} path={}", requestId(), path(req));
        return build(ErrorCode.SANAD_SEC_001, "Access is denied for this resource", req);
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(EntityNotFoundException ex, HttpServletRequest req) {
        // EntityNotFoundException messages can include entity class names — never return raw.
        log.warn("Entity not found: requestId={} path={} type={}",
                requestId(), path(req), ex.getClass().getSimpleName());
        return build(ErrorCode.SANAD_RES_001, "The requested resource was not found", req);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleDataIntegrity(DataIntegrityViolationException ex, HttpServletRequest req) {
        // JDBC constraint violation messages expose schema internals — never return raw.
        log.warn("Data integrity violation: requestId={} path={} type={}",
                requestId(), path(req), ex.getClass().getSimpleName());
        return build(ErrorCode.SANAD_CON_001, "A data conflict occurred", req);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiErrorResponse> handleResponseStatus(ResponseStatusException ex, HttpServletRequest req) {
        ErrorCode ec = switch (ex.getStatusCode().value()) {
            case 400 -> ErrorCode.SANAD_VAL_001;
            case 401 -> ErrorCode.SANAD_AUTH_001;
            case 403 -> ErrorCode.SANAD_SEC_001;
            case 404 -> ErrorCode.SANAD_RES_001;
            case 409 -> ErrorCode.SANAD_CON_001;
            case 422 -> ErrorCode.SANAD_BIZ_001;
            case 429 -> ErrorCode.SANAD_RATE_001;
            default -> ErrorCode.SANAD_GEN_001;
        };
        // Use the safe title rather than ex.getReason() (which can be raw).
        return build(ec, ec.title(), req);
    }

    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public ResponseEntity<ApiErrorResponse> handleNoHandler(Exception ex, HttpServletRequest req) {
        // Spring 6's NoResourceFoundException is thrown for unmatched paths (404).
        // We must NOT let this fall through to the catch-all (which returns 500).
        log.warn("No handler/resource: requestId={} path={} type={}",
                requestId(), path(req), ex.getClass().getSimpleName());
        return build(ErrorCode.SANAD_RES_001, "The requested resource was not found.", req);
    }

    @ExceptionHandler(BeanInstantiationException.class)
    public ResponseEntity<ApiErrorResponse> handleBeanInstantiation(
            BeanInstantiationException ex, HttpServletRequest req) {
        // PageRequestParams throws InvalidPaginationException from its compact
        // constructor; Spring wraps it in BeanInstantiationException during
        // @ModelAttribute binding. We need to unwrap and re-handle.
        Throwable cause = ex.getCause();
        if (cause instanceof TypedBusinessException typed) {
            return handleTypedBusiness(typed, req);
        }
        log.warn("Bean instantiation failed: requestId={} path={} type={}",
                requestId(), path(req), ex.getClass().getSimpleName());
        return build(ErrorCode.SANAD_VAL_001, "The request parameters are invalid.", req);
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiErrorResponse> handleBindException(
            BindException ex, HttpServletRequest req) {
        List<FieldValidationError> errors = ex.getBindingResult().getFieldErrors().stream()
            .map(fe -> new FieldValidationError(fe.getField(), fe.getCode(), fe.getDefaultMessage()))
            .collect(Collectors.toList());
        return build(ErrorCode.SANAD_VAL_001, "One or more request parameters are invalid",
                req, errors);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArg(IllegalArgumentException ex, HttpServletRequest req) {
        // IllegalArgumentException messages can echo caller input — use a safe static detail.
        log.warn("Illegal argument: requestId={} path={} type={}",
                requestId(), path(req), ex.getClass().getSimpleName());
        return build(ErrorCode.SANAD_VAL_001, "The request contained an invalid argument.", req);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalState(IllegalStateException ex, HttpServletRequest req) {
        // IllegalStateException can leak internal state — use a safe static detail.
        log.warn("Illegal state: requestId={} path={} type={}",
                requestId(), path(req), ex.getClass().getSimpleName());
        return build(ErrorCode.SANAD_BIZ_001, "The request could not be processed in the current state.", req);
    }

    // ------------------------------------------------------------------
    // Catch-all (Stage 03A §9)
    // ------------------------------------------------------------------

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception ex, HttpServletRequest req) {
        // 1. Log the full internal diagnostic with the requestId for correlation.
        //    The stack trace is logged ONLY here (server-side), never sent to the client.
        log.error("Unexpected exception — requestId={} path={} type={} message={}",
                requestId(), path(req), ex.getClass().getName(),
                ex.getMessage() == null ? "<null>" : ex.getMessage(), ex);

        // 2. Return a generic, safe detail to the caller.
        return build(ErrorCode.SANAD_GEN_001, GENERIC_ERROR_DETAIL, req);
    }
}
