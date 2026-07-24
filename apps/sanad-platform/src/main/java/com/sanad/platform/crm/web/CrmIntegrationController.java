package com.sanad.platform.crm.web;

import com.sanad.platform.crm.integration.application.CrmIntegrationUseCases;
import com.sanad.platform.crm.integration.orchestration.AiGatewayPort;
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
import org.springframework.web.bind.annotation.*;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Governed CRM boundary to the central Workflow and AI integration layer.
 *
 * <p>Controller is HTTP boundary only — all orchestration is in
 * {@link CrmIntegrationUseCases}. Entity identity for confirmation comes from
 * the stored integration request, NOT from client payload.</p>
 *
 * <p><strong>If-Match is enforced atomically:</strong> the controller parses
 * the {@code If-Match} header and passes the version into the use case; the
 * use case passes it into {@code transitionStatus} which performs an atomic
 * UPDATE...WHERE version=? — no separate read-check in the controller.</p>
 *
 * <p><strong>Standard ETag:</strong> responses use the quoted form
 * {@code ETag: "7"} per RFC 7232. The {@code If-Match} header accepts either
 * quoted or unquoted form.</p>
 */
@RestController
@RequestMapping("/api/v2/crm/integrations")
public class CrmIntegrationController {
    private final CrmOwnershipHttpSupport http;
    private final CrmIntegrationUseCases useCases;

    public CrmIntegrationController(CrmOwnershipHttpSupport http,
                                    CrmIntegrationUseCases useCases) {
        this.http = http;
        this.useCases = useCases;
    }

    @RequireCapability("CRM.AI.READ")
    @PostMapping("/ai")
    public ResponseEntity<CrmIntegrationStore.StoredRequest> requestAi(
            Authentication authentication,
            @Valid @RequestBody AiRequest body,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage,
            HttpServletRequest request) {
        var context = http.context(authentication);
        var trace = http.trace(request);
        Locale requestedLocale = parseLocale(acceptLanguage);
        var result = useCases.requestAiInsight(
                context.tenantId(), context.userId(),
                trace.correlationId().toString(), trace.requestId().toString(),
                idempotencyKey, body.capability(),
                body.sourceEntityType(), body.sourceEntityId(), body.sourceEntityVersion(),
                body.userIntent(), requestedLocale);
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Request-ID", trace.requestId().toString());
        headers.set("X-Correlation-ID", trace.correlationId().toString());
        headers.set("ETag", etag(result.version()));
        return ResponseEntity.status(HttpStatus.ACCEPTED).headers(headers).body(result);
    }

    @RequireCapability("CRM.AI.READ")
    @GetMapping("/{requestId}")
    public ResponseEntity<CrmIntegrationStore.StoredRequest> status(
            Authentication authentication,
            @PathVariable UUID requestId,
            HttpServletRequest request) {
        var context = http.context(authentication);
        var trace = http.trace(request);
        var result = useCases.getStatus(context.tenantId(), requestId);
        if (result == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok()
                .header("X-Request-ID", trace.requestId().toString())
                .header("X-Correlation-ID", trace.correlationId().toString())
                .header("ETag", etag(result.version()))
                .body(result);
    }

    @RequireCapability("CRM.AI.CONFIRM")
    @PostMapping("/{requestId}/confirm")
    public ResponseEntity<CrmIntegrationStore.StoredRequest> confirm(
            Authentication authentication,
            @PathVariable UUID requestId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("If-Match") String ifMatch,
            @Valid @RequestBody ConfirmRequest body,
            HttpServletRequest request) {
        var context = http.context(authentication);
        var trace = http.trace(request);

        long expectedIntegrationVersion = parseIfMatch(ifMatch);
        if (expectedIntegrationVersion < 0) {
            return preconditionFailed(IntegrationErrorCode.INTEGRATION_VERSION_MISMATCH,
                    "Invalid If-Match header: " + ifMatch);
        }

        try {
            var result = useCases.confirmRecommendation(
                    context.tenantId(), context.userId(), requestId,
                    trace.correlationId().toString(), idempotencyKey,
                    body.expectedEntityVersion(), expectedIntegrationVersion);
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Request-ID", trace.requestId().toString());
            headers.set("X-Correlation-ID", trace.correlationId().toString());
            headers.set("ETag", etag(result.version()));
            return ResponseEntity.ok().headers(headers).body(result);
        } catch (IntegrationException e) {
            return mapException(e);
        }
    }

    @RequireCapability("CRM.AI.CONFIRM")
    @PostMapping("/{requestId}/reject")
    public ResponseEntity<CrmIntegrationStore.StoredRequest> reject(
            Authentication authentication,
            @PathVariable UUID requestId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("If-Match") String ifMatch,
            @Valid @RequestBody RejectRequest body,
            HttpServletRequest request) {
        var context = http.context(authentication);
        var trace = http.trace(request);

        long expectedIntegrationVersion = parseIfMatch(ifMatch);
        if (expectedIntegrationVersion < 0) {
            return preconditionFailed(IntegrationErrorCode.INTEGRATION_VERSION_MISMATCH,
                    "Invalid If-Match header: " + ifMatch);
        }

        try {
            var result = useCases.rejectRecommendation(
                    context.tenantId(), context.userId(), requestId,
                    trace.correlationId().toString(), idempotencyKey,
                    body.reason(), expectedIntegrationVersion);
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Request-ID", trace.requestId().toString());
            headers.set("X-Correlation-ID", trace.correlationId().toString());
            headers.set("ETag", etag(result.version()));
            return ResponseEntity.ok().headers(headers).body(result);
        } catch (IntegrationException e) {
            return mapException(e);
        }
    }

    // ============================================================
    // Request DTOs — client sends only safe, minimal fields
    // No entity identity in ConfirmRequest — comes from stored request
    // ============================================================

    public record AiRequest(
            @NotNull AiGatewayPort.Capability capability,
            @NotBlank String sourceEntityType,
            @NotNull UUID sourceEntityId,
            @PositiveOrZero long sourceEntityVersion,
            String userIntent) { }

    public record ConfirmRequest(
            @PositiveOrZero long expectedEntityVersion) { }

    public record RejectRequest(String reason) { }

    // ============================================================
    // Helpers
    // ============================================================

    /** Standard ETag form: quoted version number, e.g. {@code "7"} (RFC 7232). */
    private static String etag(long version) {
        return "\"" + version + "\"";
    }

    /**
     * Parse If-Match header. Accepts both quoted ("7") and unquoted (7) forms.
     * Returns -1 if the header is missing or unparseable.
     */
    private static long parseIfMatch(String ifMatch) {
        if (ifMatch == null || ifMatch.isBlank()) return -1;
        try {
            String trimmed = ifMatch.trim().replaceAll("\"", "").trim();
            // RFC 7232 allows wildcard "*" — interpret as "any version".
            if ("*".equals(trimmed)) return Long.MAX_VALUE;
            return Long.parseLong(trimmed);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static Locale parseLocale(String acceptLanguage) {
        if (acceptLanguage == null || acceptLanguage.isBlank()) return null;
        try {
            String primary = acceptLanguage.split(",")[0].trim();
            String[] parts = primary.split(";")[0].split("-");
            return parts.length == 1
                    ? new Locale(parts[0])
                    : new Locale(parts[0], parts[1]);
        } catch (Exception e) {
            return null;
        }
    }

    private ResponseEntity<CrmIntegrationStore.StoredRequest> mapException(IntegrationException e) {
        HttpStatus status = HttpStatus.resolve(e.httpStatus());
        if (status == null) status = HttpStatus.INTERNAL_SERVER_ERROR;
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Error-Code", e.errorCode().name())
                .body(null);
    }

    private ResponseEntity<CrmIntegrationStore.StoredRequest> preconditionFailed(
            IntegrationErrorCode code, String detail) {
        return ResponseEntity.status(HttpStatus.PRECONDITION_FAILED)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Error-Code", code.name())
                .body(null);
    }
}
