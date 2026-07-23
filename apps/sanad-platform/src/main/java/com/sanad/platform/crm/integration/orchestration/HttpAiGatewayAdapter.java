package com.sanad.platform.crm.integration.orchestration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Provider-neutral transport to the central AI Gateway. CRM never calls a
 * model provider directly. Missing configuration and transport failures return
 * an explicit UNAVAILABLE result so committed CRM transactions remain intact.
 */
@Component
public class HttpAiGatewayAdapter implements AiGatewayPort {
    private final ObjectMapper mapper;
    private final HttpClient client;
    private final String baseUrl;
    private final Duration timeout;

    public HttpAiGatewayAdapter(ObjectMapper mapper,
                                @Value("${sanad.ai-gateway.base-url:}") String baseUrl,
                                @Value("${sanad.ai-gateway.timeout-ms:5000}") long timeoutMs) {
        this.mapper = mapper;
        this.baseUrl = baseUrl == null ? "" : baseUrl.strip().replaceAll("/+$", "");
        this.timeout = Duration.ofMillis(Math.max(500, Math.min(timeoutMs, 20_000)));
        this.client = HttpClient.newBuilder().connectTimeout(this.timeout).build();
    }

    @Override
    public AiResult request(IntegrationEnvelope envelope, Capability capability, JsonNode minimizedPayload) {
        if (baseUrl.isBlank() || envelope.isExpired(Instant.now())) {
            return unavailable(Status.UNAVAILABLE, "AI_GATEWAY_NOT_CONFIGURED_OR_EXPIRED");
        }
        try {
            ObjectNode body = mapper.createObjectNode();
            body.put("contractName", envelope.contractName());
            body.put("contractVersion", envelope.contractVersion());
            body.put("tenantId", envelope.tenantId().toString());
            body.put("actorId", envelope.actorId().toString());
            body.put("correlationId", envelope.correlationId());
            body.put("causationId", envelope.causationId());
            body.put("idempotencyKey", envelope.idempotencyKey());
            body.put("capability", capability.name());
            body.put("sourceEntityType", envelope.sourceEntityType());
            body.put("sourceEntityId", envelope.sourceEntityId().toString());
            body.put("sourceEntityVersion", envelope.sourceEntityVersion());
            body.put("requiredCapability", envelope.requiredCapability());
            body.put("dataClassification", envelope.dataClassification());
            body.set("payload", minimizedPayload == null ? mapper.createObjectNode() : minimizedPayload);

            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/v1/ai/execute"))
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("X-Correlation-Id", envelope.correlationId())
                    .header("Idempotency-Key", envelope.idempotencyKey())
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 403) return unavailable(Status.POLICY_DENIED, "AI_POLICY_DENIED");
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return unavailable(Status.UNAVAILABLE, "AI_GATEWAY_HTTP_" + response.statusCode());
            }
            JsonNode value = mapper.readTree(response.body());
            Status status = parseStatus(value.path("status").asText("UNAVAILABLE"));
            List<String> references = new ArrayList<>();
            value.path("sourceReferences").forEach(node -> references.add(node.asText()));
            Instant generatedAt = parseInstant(value.path("generatedAt").asText(null));
            Instant expiresAt = parseInstant(value.path("expiresAt").asText(null));
            return new AiResult(status,
                    text(value, "generatedText"), text(value, "actionCode"), text(value, "explanation"),
                    value.hasNonNull("confidence") ? value.get("confidence").asDouble() : null,
                    generatedAt, expiresAt, value.path("humanConfirmationRequired").asBoolean(false),
                    references, text(value, "policyVersion"), text(value, "modelVersion"));
        } catch (java.net.http.HttpTimeoutException error) {
            return unavailable(Status.TIMED_OUT, "AI_GATEWAY_TIMEOUT");
        } catch (Exception error) {
            return unavailable(Status.UNAVAILABLE, "AI_GATEWAY_TRANSPORT_FAILURE");
        }
    }

    private AiResult unavailable(Status status, String explanation) {
        return new AiResult(status, null, null, explanation, null, null, null,
                false, List.of(), null, null);
    }

    private static Status parseStatus(String value) {
        try { return Status.valueOf(value); }
        catch (Exception ignored) { return Status.UNAVAILABLE; }
    }

    private static Instant parseInstant(String value) {
        try { return value == null ? null : Instant.parse(value); }
        catch (Exception ignored) { return null; }
    }

    private static String text(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.get(field).asText() : null;
    }
}
