package com.sanad.platform.crm.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.sanad.platform.crm.integration.application.CrmWorkflowUseCases;
import com.sanad.platform.crm.integration.orchestration.CrmIntegrationStore;
import com.sanad.platform.crm.integration.orchestration.IntegrationErrorCode;
import com.sanad.platform.crm.integration.orchestration.IntegrationException;
import com.sanad.platform.security.authorization.RequireCapability;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;
import java.util.UUID;

/** Governed browser-facing CRM workflow integration boundary. */
@RestController
@RequestMapping("/api/v2/crm/integrations/workflows")
public class CrmWorkflowController {

    private final CrmOwnershipHttpSupport http;
    private final CrmWorkflowUseCases useCases;

    public CrmWorkflowController(
            CrmOwnershipHttpSupport http,
            CrmWorkflowUseCases useCases) {
        this.http = http;
        this.useCases = useCases;
    }

    @RequireCapability("CRM.WORKFLOW.EXECUTE")
    @PostMapping
    public ResponseEntity<CrmIntegrationStore.StoredRequest> dispatch(
            Authentication authentication,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage,
            @Valid @RequestBody WorkflowDispatchRequest body,
            HttpServletRequest request) {
        var context = http.context(authentication);
        var trace = http.trace(request);
        try {
            CrmIntegrationStore.StoredRequest result = useCases.dispatchWorkflow(
                    context.tenantId(),
                    context.userId(),
                    trace.correlationId().toString(),
                    trace.requestId().toString(),
                    idempotencyKey,
                    body.workflowType(),
                    body.sourceEntityType(),
                    body.sourceEntityId(),
                    body.sourceEntityVersion(),
                    body.payload(),
                    parseLocale(acceptLanguage));
            return response(result, trace, HttpStatus.ACCEPTED);
        } catch (IntegrationException error) {
            return mapException(error, trace);
        }
    }

    @RequireCapability("CRM.WORKFLOW.EXECUTE")
    @GetMapping("/{requestId}")
    public ResponseEntity<CrmIntegrationStore.StoredRequest> status(
            Authentication authentication,
            @PathVariable UUID requestId,
            HttpServletRequest request) {
        var context = http.context(authentication);
        var trace = http.trace(request);
        try {
            return response(
                    useCases.getWorkflowStatus(context.tenantId(), requestId),
                    trace,
                    HttpStatus.OK);
        } catch (IntegrationException error) {
            return mapException(error, trace);
        }
    }

    @RequireCapability("CRM.WORKFLOW.EXECUTE")
    @PostMapping("/{requestId}/cancel")
    public ResponseEntity<CrmIntegrationStore.StoredRequest> cancel(
            Authentication authentication,
            @PathVariable UUID requestId,
            @RequestHeader("If-Match") String ifMatch,
            @Valid @RequestBody(required = false) WorkflowCancelRequest body,
            HttpServletRequest request) {
        var context = http.context(authentication);
        var trace = http.trace(request);
        long expectedVersion = parseIfMatch(ifMatch);
        if (expectedVersion < 0) {
            return errorResponse(
                    HttpStatus.PRECONDITION_FAILED,
                    IntegrationErrorCode.INTEGRATION_VERSION_MISMATCH,
                    trace);
        }
        try {
            CrmIntegrationStore.StoredRequest result = useCases.cancelWorkflow(
                    context.tenantId(),
                    requestId,
                    expectedVersion,
                    trace.correlationId().toString(),
                    body == null ? null : body.reason());
            return response(result, trace, HttpStatus.OK);
        } catch (IntegrationException error) {
            return mapException(error, trace);
        }
    }

    public record WorkflowDispatchRequest(
            @NotNull CrmWorkflowUseCases.WorkflowType workflowType,
            @NotBlank String sourceEntityType,
            @NotNull UUID sourceEntityId,
            @PositiveOrZero long sourceEntityVersion,
            JsonNode payload) {
    }

    public record WorkflowCancelRequest(String reason) {
    }

    private ResponseEntity<CrmIntegrationStore.StoredRequest> response(
            CrmIntegrationStore.StoredRequest result,
            CrmOwnershipHttpSupport.RequestTrace trace,
            HttpStatus status) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Request-ID", trace.requestId().toString());
        headers.set("X-Correlation-ID", trace.correlationId().toString());
        headers.setETag(etag(result.version()));
        return ResponseEntity.status(status)
                .headers(headers)
                .contentType(MediaType.APPLICATION_JSON)
                .body(result);
    }

    private ResponseEntity<CrmIntegrationStore.StoredRequest> mapException(
            IntegrationException error,
            CrmOwnershipHttpSupport.RequestTrace trace) {
        HttpStatus status = HttpStatus.resolve(error.httpStatus());
        return errorResponse(
                status == null ? HttpStatus.INTERNAL_SERVER_ERROR : status,
                error.errorCode(),
                trace);
    }

    private ResponseEntity<CrmIntegrationStore.StoredRequest> errorResponse(
            HttpStatus status,
            IntegrationErrorCode code,
            CrmOwnershipHttpSupport.RequestTrace trace) {
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Request-ID", trace.requestId().toString())
                .header("X-Correlation-ID", trace.correlationId().toString())
                .header("X-Error-Code", code.name())
                .body(null);
    }

    private static String etag(long version) {
        return "\"" + version + "\"";
    }

    private static long parseIfMatch(String value) {
        if (value == null || value.isBlank() || "*".equals(value.strip())) return -1;
        try {
            return Long.parseLong(value.strip().replace("\"", ""));
        } catch (NumberFormatException invalid) {
            return -1;
        }
    }

    private static Locale parseLocale(String acceptLanguage) {
        if (acceptLanguage == null || acceptLanguage.isBlank()) return Locale.ENGLISH;
        String primary = acceptLanguage.split(",", 2)[0].split(";", 2)[0].strip();
        Locale locale = Locale.forLanguageTag(primary);
        return locale.getLanguage().isBlank() ? Locale.ENGLISH : locale;
    }
}
