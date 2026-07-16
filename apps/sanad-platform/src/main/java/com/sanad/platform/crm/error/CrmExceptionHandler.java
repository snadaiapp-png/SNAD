package com.sanad.platform.crm.error;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * CRM API Contract — Central Exception Handler.
 * <p>
 * Translates every exception that escapes a CRM controller into the
 * standard {@link CrmErrorResponse} envelope. The handler guarantees:
 *   - A stable {@code requestId} is always present (read from the
 *     {@code X-Request-ID} header if set, otherwise generated).
 *   - A stable {@code code} from {@link CrmErrorCode} is always present.
 *   - No stack traces, SQL, table names, package names, or other internal
 *     leakage appears in the body. (Verified by {@code CrmErrorContractTest}.)
 *   - 5xx errors are logged at ERROR with the requestId so operators can
 *     correlate logs with user reports. 4xx errors are logged at DEBUG.
 * <p>
 * Branch: crm/003-stable-api-contracts
 */
@RestControllerAdvice(assignableTypes = {
        com.sanad.platform.crm.web.CrmController.class,
        com.sanad.platform.crm.party.web.CustomerMasterController.class
})
public class CrmExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(CrmExceptionHandler.class);
    private static final Pattern NOT_FOUND_PATTERN = Pattern.compile(
            "(?i)(account|contact|lead|opportunity|activity|pipeline|stage|import|custom\\s*field)\\s+not\\s+found");
    private static final Pattern DUPLICATE_PATTERN = Pattern.compile(
            "(?i)duplicate|already\\s+exists|unique\\s+constraint");
    private static final Pattern CONVERTED_PATTERN = Pattern.compile(
            "(?i)already\\s+converted|lead.*converted");

    @ExceptionHandler(CrmContractException.class)
    public ResponseEntity<CrmErrorResponse> handleContract(CrmContractException ex, WebRequest request) {
        UUID requestId = resolveRequestId(request);
        CrmErrorResponse body = CrmErrorResponse.of(ex.code(), ex.userMessage(), requestId);
        log(ex.code().httpStatus(), ex.code().name(), ex.userMessage(), requestId, ex);
        return ResponseEntity.status(ex.code().httpStatus()).body(body);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<CrmErrorResponse> handleOptimisticLock(OptimisticLockingFailureException ex, WebRequest request) {
        return simple(CrmErrorCode.CRM_CONCURRENCY_CONFLICT, request, ex);
    }

    @ExceptionHandler(EmptyResultDataAccessException.class)
    public ResponseEntity<CrmErrorResponse> handleEmptyResult(EmptyResultDataAccessException ex, WebRequest request) {
        return simple(CrmErrorCode.RESOURCE_NOT_FOUND, request, ex);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<CrmErrorResponse> handleDataIntegrity(DataIntegrityViolationException ex, WebRequest request) {
        String message = ex.getMostSpecificCause() == null ? "" : String.valueOf(ex.getMostSpecificCause().getMessage());
        if (DUPLICATE_PATTERN.matcher(message).find()) {
            return simple(CrmErrorCode.CONFLICT, request, ex);
        }
        return simple(CrmErrorCode.VALIDATION_ERROR, request, ex);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        UUID requestId = resolveRequestId(request);
        List<CrmErrorResponse.FieldError> fieldErrors = new ArrayList<>();
        ex.getBindingResult().getFieldErrors().forEach(fe ->
                fieldErrors.add(new CrmErrorResponse.FieldError(
                        fe.getField(),
                        fe.getCode() == null ? "INVALID" : fe.getCode(),
                        fe.getDefaultMessage() == null ? "Invalid value" : fe.getDefaultMessage())));
        CrmErrorResponse body = CrmErrorResponse.validation(requestId, fieldErrors);
        log(400, "VALIDATION_ERROR", "Bean validation failed", requestId, ex);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<CrmErrorResponse> handleConstraintViolation(ConstraintViolationException ex, WebRequest request) {
        UUID requestId = resolveRequestId(request);
        List<CrmErrorResponse.FieldError> fieldErrors = new ArrayList<>();
        ex.getConstraintViolations().forEach(cv ->
                fieldErrors.add(new CrmErrorResponse.FieldError(
                        cv.getPropertyPath() == null ? "" : cv.getPropertyPath().toString(),
                        cv.getConstraintDescriptor() == null || cv.getConstraintDescriptor().getAnnotation() == null
                                ? "INVALID"
                                : cv.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName().toUpperCase(),
                        cv.getMessage())));
        CrmErrorResponse body = CrmErrorResponse.validation(requestId, fieldErrors);
        log(400, "VALIDATION_ERROR", "Constraint violation", requestId, ex);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        UUID requestId = resolveRequestId(request);
        CrmErrorResponse body = CrmErrorResponse.of(
                CrmErrorCode.VALIDATION_ERROR,
                "The request body is missing or is not valid JSON.",
                requestId);
        log(400, "VALIDATION_ERROR", "Malformed JSON", requestId, ex);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<CrmErrorResponse> handleAuthn(AuthenticationException ex, WebRequest request) {
        return simple(CrmErrorCode.UNAUTHORIZED, request, ex);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<CrmErrorResponse> handleAccessDenied(AccessDeniedException ex, WebRequest request) {
        return simple(CrmErrorCode.CRM_CAPABILITY_REQUIRED, request, ex);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<CrmErrorResponse> handleResponseStatus(ResponseStatusException ex, WebRequest request) {
        UUID requestId = resolveRequestId(request);
        String message = ex.getReason() == null ? ex.getStatusCode().toString() : ex.getReason();
        CrmErrorCode code = mapStatusToCode(ex.getStatusCode().value(), message);
        CrmErrorResponse body = CrmErrorResponse.of(code, message, requestId);
        log(code.httpStatus(), code.name(), message, requestId, ex);
        return ResponseEntity.status(ex.getStatusCode()).body(body);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<CrmErrorResponse> handleIllegalArg(IllegalArgumentException ex, WebRequest request) {
        return simple(CrmErrorCode.VALIDATION_ERROR, request, ex);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<CrmErrorResponse> handleIllegalState(IllegalStateException ex, WebRequest request) {
        return simple(CrmErrorCode.CONFLICT, request, ex);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<CrmErrorResponse> handleAny(Exception ex, WebRequest request) {
        UUID requestId = resolveRequestId(request);
        CrmErrorResponse body = CrmErrorResponse.of(
                CrmErrorCode.INTERNAL_ERROR,
                CrmErrorCode.INTERNAL_ERROR.defaultMessage(),
                requestId);
        // Log full stack at ERROR for operators; do NOT include in body.
        log.error("[CRM] requestId={} INTERNAL_ERROR: {}", requestId, ex.toString(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    // ────────────────────────────────────────────────────────────────────
    // Helpers
    // ────────────────────────────────────────────────────────────────────

    private ResponseEntity<CrmErrorResponse> simple(CrmErrorCode code, WebRequest request, Throwable cause) {
        UUID requestId = resolveRequestId(request);
        CrmErrorResponse body = CrmErrorResponse.of(code, code.defaultMessage(), requestId);
        log(code.httpStatus(), code.name(), code.defaultMessage(), requestId, cause);
        return ResponseEntity.status(code.httpStatus()).body(body);
    }

    private void log(int status, String code, String message, UUID requestId, Throwable cause) {
        if (status >= 500) {
            log.error("[CRM] requestId={} {} {}: {}", requestId, status, code, message, cause);
        } else if (status >= 400) {
            log.debug("[CRM] requestId={} {} {}: {}", requestId, status, code, message, cause);
        }
    }

    private UUID resolveRequestId(WebRequest request) {
        if (request == null) return UUID.randomUUID();
        Object header = request.getHeader("X-Request-ID");
        if (header instanceof String s && !s.isBlank()) {
            try {
                return UUID.fromString(s);
            } catch (IllegalArgumentException ignored) { /* fall through */ }
        }
        // Also stash on the servlet request attribute so handlers downstream
        // can read the same id.
        return UUID.randomUUID();
    }

    private CrmErrorCode mapStatusToCode(int status, String message) {
        return switch (status) {
            case 400 -> CrmErrorCode.VALIDATION_ERROR;
            case 401 -> CrmErrorCode.UNAUTHORIZED;
            case 403 -> CrmErrorCode.CRM_CAPABILITY_REQUIRED;
            case 404 -> classifyNotFound(message);
            case 409 -> classifyConflict(message);
            case 412 -> CrmErrorCode.CRM_CONCURRENCY_CONFLICT;
            case 422 -> CrmErrorCode.VALIDATION_ERROR;
            case 429 -> CrmErrorCode.RATE_LIMITED;
            default -> status >= 500 ? CrmErrorCode.INTERNAL_ERROR : CrmErrorCode.INTERNAL_ERROR;
        };
    }

    private CrmErrorCode classifyNotFound(String message) {
        if (message == null) return CrmErrorCode.RESOURCE_NOT_FOUND;
        Matcher m = NOT_FOUND_PATTERN.matcher(message);
        if (m.find()) {
            String entity = m.group(1).toLowerCase().replaceAll("\\s+", "");
            return switch (entity) {
                case "account" -> CrmErrorCode.CRM_ACCOUNT_NOT_FOUND;
                case "contact" -> CrmErrorCode.CRM_CONTACT_NOT_FOUND;
                case "lead" -> CrmErrorCode.CRM_LEAD_NOT_FOUND;
                case "opportunity" -> CrmErrorCode.CRM_OPPORTUNITY_NOT_FOUND;
                case "activity" -> CrmErrorCode.CRM_ACTIVITY_NOT_FOUND;
                case "pipeline" -> CrmErrorCode.CRM_PIPELINE_NOT_FOUND;
                case "stage" -> CrmErrorCode.CRM_STAGE_NOT_FOUND;
                case "import" -> CrmErrorCode.CRM_IMPORT_NOT_FOUND;
                case "customfield" -> CrmErrorCode.CRM_CUSTOM_FIELD_NOT_FOUND;
                default -> CrmErrorCode.RESOURCE_NOT_FOUND;
            };
        }
        return CrmErrorCode.RESOURCE_NOT_FOUND;
    }

    private CrmErrorCode classifyConflict(String message) {
        if (message != null && CONVERTED_PATTERN.matcher(message).find()) {
            return CrmErrorCode.CRM_LEAD_ALREADY_CONVERTED;
        }
        return CrmErrorCode.CONFLICT;
    }
}
