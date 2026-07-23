package com.sanad.platform.crm.web;

import com.sanad.platform.crm.error.CrmContractException;
import com.sanad.platform.crm.ownership.domain.ActiveMembershipExistsException;
import com.sanad.platform.crm.ownership.domain.ActiveRuleVersionExistsException;
import com.sanad.platform.crm.ownership.domain.ArchiveBlockedWithActiveMembershipsException;
import com.sanad.platform.crm.ownership.domain.AssignmentNotFoundException;
import com.sanad.platform.crm.ownership.domain.AssignmentRuleNotFoundException;
import com.sanad.platform.crm.ownership.domain.ConcurrentClaimConflictException;
import com.sanad.platform.crm.ownership.domain.CrossTenantAccessException;
import com.sanad.platform.crm.ownership.domain.OwnershipDomainException;
import com.sanad.platform.crm.ownership.domain.PrimaryMembershipConflictException;
import com.sanad.platform.crm.ownership.domain.QueueCapacityExceededException;
import com.sanad.platform.crm.ownership.domain.QueueNotFoundException;
import com.sanad.platform.crm.ownership.domain.TeamCodeConflictException;
import com.sanad.platform.crm.ownership.domain.TeamNotFoundException;
import com.sanad.platform.crm.ownership.domain.TerritoryCycleException;
import com.sanad.platform.crm.ownership.domain.TerritoryNotFoundException;
import com.sanad.platform.crm.ownership.domain.TransferStateConflictException;
import com.sanad.platform.crm.ownership.domain.UnauthorizedTransferApproverException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** RFC 7807 handler dedicated to the CRM-008 governed ownership surface. */
@RestControllerAdvice(assignableTypes = {
        CrmOwnershipResourceController.class,
        CrmOwnershipAssignmentController.class,
        CrmOwnershipTransferController.class
})
public class CrmOwnershipProblemHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(CrmOwnershipProblemHandler.class);

    @ExceptionHandler(CrmContractException.class)
    public ResponseEntity<ProblemDetail> contract(
            CrmContractException error,
            HttpServletRequest request) {
        return problem(
                error.code().httpStatus(), error.code().name(), error.userMessage(),
                error.requestId(), request, null, error);
    }

    @ExceptionHandler({
            TeamNotFoundException.class,
            QueueNotFoundException.class,
            TerritoryNotFoundException.class,
            AssignmentRuleNotFoundException.class,
            AssignmentNotFoundException.class,
            CrossTenantAccessException.class
    })
    public ResponseEntity<ProblemDetail> notFound(
            OwnershipDomainException error,
            HttpServletRequest request) {
        return problem(404, "CRM_OWNERSHIP_NOT_FOUND", safe(error.getMessage()),
                null, request, null, error);
    }

    @ExceptionHandler(ConcurrentClaimConflictException.class)
    public ResponseEntity<ProblemDetail> precondition(
            ConcurrentClaimConflictException error,
            HttpServletRequest request) {
        return problem(412, "CRM_CONCURRENCY_CONFLICT", "The ownership state changed concurrently.",
                null, request, null, error);
    }

    @ExceptionHandler({
            TeamCodeConflictException.class,
            ActiveMembershipExistsException.class,
            PrimaryMembershipConflictException.class,
            QueueCapacityExceededException.class,
            TerritoryCycleException.class,
            ActiveRuleVersionExistsException.class,
            ArchiveBlockedWithActiveMembershipsException.class,
            TransferStateConflictException.class
    })
    public ResponseEntity<ProblemDetail> conflict(
            OwnershipDomainException error,
            HttpServletRequest request) {
        return problem(409, "CRM_OWNERSHIP_CONFLICT", safe(error.getMessage()),
                null, request, null, error);
    }

    @ExceptionHandler(UnauthorizedTransferApproverException.class)
    public ResponseEntity<ProblemDetail> unauthorizedApprover(
            UnauthorizedTransferApproverException error,
            HttpServletRequest request) {
        return problem(403, "CRM_TRANSFER_APPROVER_FORBIDDEN", "The caller cannot approve this transfer.",
                null, request, null, error);
    }

    @ExceptionHandler(OwnershipDomainException.class)
    public ResponseEntity<ProblemDetail> domain(
            OwnershipDomainException error,
            HttpServletRequest request) {
        return problem(400, "CRM_OWNERSHIP_VALIDATION", safe(error.getMessage()),
                null, request, null, error);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ProblemDetail> authentication(
            AuthenticationException error,
            HttpServletRequest request) {
        return problem(401, "UNAUTHORIZED", "Authentication is required.",
                null, request, null, error);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> accessDenied(
            AccessDeniedException error,
            HttpServletRequest request) {
        return problem(403, "CRM_CAPABILITY_REQUIRED", "The required capability is not granted.",
                null, request, null, error);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> constraint(
            ConstraintViolationException error,
            HttpServletRequest request) {
        List<Map<String, String>> fields = error.getConstraintViolations().stream()
                .map(value -> Map.of(
                        "field", value.getPropertyPath().toString(),
                        "message", value.getMessage()))
                .toList();
        return problem(400, "VALIDATION_ERROR", "The request parameters are invalid.",
                null, request, Map.of("fieldErrors", fields), error);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException error,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest webRequest) {
        HttpServletRequest request = request(webRequest);
        List<Map<String, String>> fields = error.getBindingResult().getFieldErrors().stream()
                .map(value -> Map.of(
                        "field", value.getField(),
                        "code", value.getCode() == null ? "INVALID" : value.getCode(),
                        "message", value.getDefaultMessage() == null ? "Invalid value" : value.getDefaultMessage()))
                .toList();
        ResponseEntity<ProblemDetail> response = problem(
                400, "VALIDATION_ERROR", "The request body is invalid.",
                null, request, Map.of("fieldErrors", fields), error);
        return ResponseEntity.status(response.getStatusCode())
                .headers(response.getHeaders())
                .body(response.getBody());
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException error,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest webRequest) {
        ResponseEntity<ProblemDetail> response = problem(
                400, "VALIDATION_ERROR", "The request body is missing or malformed.",
                null, request(webRequest), null, error);
        return ResponseEntity.status(response.getStatusCode())
                .headers(response.getHeaders())
                .body(response.getBody());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> unexpected(
            Exception error,
            HttpServletRequest request) {
        return problem(500, "INTERNAL_ERROR", "An internal error occurred.",
                null, request, null, error);
    }

    private ResponseEntity<ProblemDetail> problem(
            int status,
            String code,
            String detail,
            UUID explicitRequestId,
            HttpServletRequest request,
            Map<String, Object> extra,
            Throwable cause) {
        UUID requestId = explicitRequestId != null
                ? explicitRequestId : headerUuid(request, "X-Request-ID");
        UUID correlationId = headerUuid(request, "X-Correlation-ID");
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatusCode.valueOf(status), detail);
        problem.setType(URI.create("urn:snad:crm:problem:" + code.toLowerCase()));
        problem.setTitle(title(status));
        if (request != null && request.getRequestURI() != null) {
            problem.setInstance(URI.create(request.getRequestURI()));
        }
        problem.setProperty("code", code);
        problem.setProperty("requestId", requestId);
        problem.setProperty("correlationId", correlationId);
        problem.setProperty("timestamp", Instant.now());
        if (extra != null) extra.forEach(problem::setProperty);

        if (status >= 500) {
            log.error("[CRM-008] requestId={} correlationId={} status={} code={}",
                    requestId, correlationId, status, code, cause);
        } else {
            log.debug("[CRM-008] requestId={} correlationId={} status={} code={}",
                    requestId, correlationId, status, code);
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        headers.set("X-Request-ID", requestId.toString());
        headers.set("X-Correlation-ID", correlationId.toString());
        return ResponseEntity.status(status).headers(headers).body(problem);
    }

    private UUID headerUuid(HttpServletRequest request, String name) {
        if (request != null) {
            String value = request.getHeader(name);
            if (value != null && !value.isBlank()) {
                try {
                    return UUID.fromString(value);
                } catch (IllegalArgumentException ignored) {
                    // Error responses always emit a valid identifier.
                }
            }
        }
        return UUID.randomUUID();
    }

    private HttpServletRequest request(WebRequest request) {
        if (request instanceof org.springframework.web.context.request.ServletWebRequest servlet) {
            return servlet.getRequest();
        }
        return null;
    }

    private String safe(String message) {
        if (message == null || message.isBlank()) return "The ownership request is invalid.";
        String normalized = message.replaceAll("(?i)(select|insert|update|delete)\\s+[^;]+", "database operation");
        return normalized.length() > 500 ? normalized.substring(0, 500) : normalized;
    }

    private String title(int status) {
        return switch (status) {
            case 400 -> "Bad Request";
            case 401 -> "Unauthorized";
            case 403 -> "Forbidden";
            case 404 -> "Not Found";
            case 409 -> "Conflict";
            case 412 -> "Precondition Failed";
            default -> status >= 500 ? "Internal Server Error" : HttpStatus.valueOf(status).getReasonPhrase();
        };
    }
}
