package com.sanad.platform.crm.web;

import com.sanad.platform.crm.integration.application.CrmIntegrationUseCases;
import com.sanad.platform.crm.integration.orchestration.AiGatewayPort;
import com.sanad.platform.crm.integration.orchestration.CrmIntegrationStore;
import com.sanad.platform.security.authorization.RequireCapability;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Governed CRM boundary to the central Workflow and AI integration layer.
 * Controller is HTTP boundary only — all orchestration is in CrmIntegrationUseCases.
 */
@RestController
@RequestMapping("/api/v2/crm/integrations")
public class CrmIntegrationController {
    private final CrmOwnershipHttpSupport http;
    private final CrmIntegrationUseCases useCases;

    public CrmIntegrationController(CrmOwnershipHttpSupport http, CrmIntegrationUseCases useCases) {
        this.http = http;
        this.useCases = useCases;
    }

    @RequireCapability("CRM.AI.READ")
    @PostMapping("/ai")
    public ResponseEntity<CrmIntegrationStore.StoredRequest> requestAi(
            Authentication authentication,
            @Valid @RequestBody AiRequest body,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            HttpServletRequest request) {
        var context = http.context(authentication);
        var trace = http.trace(request);
        var result = useCases.requestAiInsight(
                context.tenantId(), context.userId(),
                trace.correlationId().toString(), trace.requestId().toString(),
                idempotencyKey, body.capability(),
                body.sourceEntityType(), body.sourceEntityId(), body.sourceEntityVersion(),
                body.userIntent());
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Request-ID", trace.requestId().toString());
        headers.set("X-Correlation-ID", trace.correlationId().toString());
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
                .body(result);
    }

    @RequireCapability("CRM.AI.CONFIRM")
    @PostMapping("/{requestId}/confirm")
    public ResponseEntity<CrmIntegrationStore.StoredRequest> confirm(
            Authentication authentication,
            @PathVariable UUID requestId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            @Valid @RequestBody ConfirmRequest body,
            HttpServletRequest request) {
        var context = http.context(authentication);
        var trace = http.trace(request);
        var result = useCases.confirmRecommendation(
                context.tenantId(), context.userId(), requestId,
                trace.correlationId().toString(), idempotencyKey,
                body.expectedEntityVersion());
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Request-ID", trace.requestId().toString());
        headers.set("X-Correlation-ID", trace.correlationId().toString());
        return ResponseEntity.ok().headers(headers).body(result);
    }

    @RequireCapability("CRM.AI.CONFIRM")
    @PostMapping("/{requestId}/reject")
    public ResponseEntity<CrmIntegrationStore.StoredRequest> reject(
            Authentication authentication,
            @PathVariable UUID requestId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody RejectRequest body,
            HttpServletRequest request) {
        var context = http.context(authentication);
        var trace = http.trace(request);
        var result = useCases.rejectRecommendation(
                context.tenantId(), context.userId(), requestId,
                trace.correlationId().toString(), idempotencyKey,
                body.reason());
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Request-ID", trace.requestId().toString());
        headers.set("X-Correlation-ID", trace.correlationId().toString());
        return ResponseEntity.ok().headers(headers).body(result);
    }

    // ============================================================
    // Request DTOs — client sends only safe, minimal fields
    // ============================================================

    public record AiRequest(
            @NotNull AiGatewayPort.Capability capability,
            @NotBlank String sourceEntityType,
            @NotNull UUID sourceEntityId,
            @PositiveOrZero long sourceEntityVersion,
            String userIntent) { }

    public record ConfirmRequest(@PositiveOrZero long expectedEntityVersion) { }
    public record RejectRequest(String reason) { }
}
