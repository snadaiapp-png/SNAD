package com.sanad.platform.crm.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanad.platform.crm.integration.orchestration.AiGatewayPort;
import com.sanad.platform.crm.integration.orchestration.CrmIntegrationStore;
import com.sanad.platform.crm.integration.orchestration.IntegrationEnvelope;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.UUID;

/** Governed CRM boundary to the central Workflow and AI integration layer. */
@RestController
@RequestMapping("/api/v2/crm/integrations")
public class CrmIntegrationController {
    private final CrmOwnershipHttpSupport http;
    private final CrmIntegrationStore store;
    private final AiGatewayPort ai;
    private final ObjectMapper mapper;

    public CrmIntegrationController(CrmOwnershipHttpSupport http,
                                    CrmIntegrationStore store,
                                    AiGatewayPort ai,
                                    ObjectMapper mapper) {
        this.http = http;
        this.store = store;
        this.ai = ai;
        this.mapper = mapper;
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
        Instant now = Instant.now();
        IntegrationEnvelope envelope = new IntegrationEnvelope(
                "crm.ai." + body.capability().name().toLowerCase(Locale.ROOT), "1.0",
                context.tenantId(), context.userId(), trace.correlationId().toString(),
                trace.requestId().toString(), idempotencyKey, body.sourceEntityType(),
                body.sourceEntityId(), body.sourceEntityVersion(), now, now.plus(30, ChronoUnit.SECONDS),
                Locale.ENGLISH, "CRM.AI.READ", body.dataClassification());

        CrmIntegrationStore.StoredRequest stored = store.create(envelope, "AI", body.payload());
        AiGatewayPort.AiResult result = ai.request(envelope, body.capability(), body.payload());
        String status = switch (result.status()) {
            case AVAILABLE, PARTIAL -> "COMPLETED";
            case TIMED_OUT -> "TIMED_OUT";
            case POLICY_DENIED -> "POLICY_DENIED";
            case UNSAFE_OUTPUT -> "UNSAFE_OUTPUT";
            case UNAVAILABLE -> "UNAVAILABLE";
        };
        JsonNode resultJson = mapper.valueToTree(result);
        store.complete(context.tenantId(), stored.id(), status, null, resultJson,
                status.equals("COMPLETED") ? null : result.explanation());
        CrmIntegrationStore.StoredRequest completed = store.find(context.tenantId(), stored.id()).orElseThrow();
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Request-ID", trace.requestId().toString());
        headers.set("X-Correlation-ID", trace.correlationId().toString());
        return ResponseEntity.status(HttpStatus.ACCEPTED).headers(headers).body(completed);
    }

    @RequireCapability("CRM.AI.READ")
    @GetMapping("/{requestId}")
    public ResponseEntity<CrmIntegrationStore.StoredRequest> status(
            Authentication authentication,
            @PathVariable UUID requestId,
            HttpServletRequest request) {
        var context = http.context(authentication);
        var trace = http.trace(request);
        return store.find(context.tenantId(), requestId)
                .map(value -> ResponseEntity.ok()
                        .header("X-Request-ID", trace.requestId().toString())
                        .header("X-Correlation-ID", trace.correlationId().toString())
                        .body(value))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    public record AiRequest(
            @NotNull AiGatewayPort.Capability capability,
            @NotBlank String sourceEntityType,
            @NotNull UUID sourceEntityId,
            @PositiveOrZero long sourceEntityVersion,
            @NotBlank String dataClassification,
            @NotNull JsonNode payload) { }
}
