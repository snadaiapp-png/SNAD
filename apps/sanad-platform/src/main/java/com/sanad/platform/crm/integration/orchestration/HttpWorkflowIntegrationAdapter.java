package com.sanad.platform.crm.integration.orchestration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sanad.platform.crm.integration.security.ServiceJwtProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/** Provider-neutral authenticated HTTP transport to the central Workflow Engine. */
@Component
public class HttpWorkflowIntegrationAdapter implements WorkflowIntegrationPort {
    private final ObjectMapper mapper;
    private final HttpClient client;
    private final ServiceJwtProvider serviceJwtProvider;
    private final String baseUrl;
    private final String audience;
    private final Duration timeout;

    @Autowired
    public HttpWorkflowIntegrationAdapter(
            ObjectMapper mapper,
            ServiceJwtProvider serviceJwtProvider,
            @Value("${sanad.workflow-engine.base-url:}") String baseUrl,
            @Value("${sanad.workflow-engine.audience:sanad-workflow-engine}") String audience,
            @Value("${sanad.workflow-engine.timeout-ms:5000}") long timeoutMs) {
        this.mapper = mapper;
        this.serviceJwtProvider = serviceJwtProvider;
        this.baseUrl = normalizeBaseUrl(baseUrl);
        this.audience = audience == null || audience.isBlank() ? "sanad-workflow-engine" : audience.strip();
        this.timeout = boundedTimeout(timeoutMs);
        this.client = HttpClient.newBuilder().connectTimeout(this.timeout).build();
    }

    /** Constructor retained for isolated fail-closed transport tests. */
    public HttpWorkflowIntegrationAdapter(ObjectMapper mapper, String baseUrl, long timeoutMs) {
        this.mapper = mapper;
        this.serviceJwtProvider = null;
        this.baseUrl = normalizeBaseUrl(baseUrl);
        this.audience = "sanad-workflow-engine";
        this.timeout = boundedTimeout(timeoutMs);
        this.client = HttpClient.newBuilder().connectTimeout(this.timeout).build();
    }

    @Override
    public WorkflowDispatch dispatch(IntegrationEnvelope envelope, String workflowType, JsonNode minimizedPayload) {
        if (baseUrl.isBlank() || envelope.isExpired(Instant.now())) {
            return new WorkflowDispatch(
                    null, Status.UNAVAILABLE, Instant.now(),
                    "WORKFLOW_ENGINE_NOT_CONFIGURED_OR_EXPIRED");
        }
        if (serviceJwtProvider == null || !serviceJwtProvider.isConfigured()) {
            return new WorkflowDispatch(
                    null, Status.UNAVAILABLE, Instant.now(),
                    "WORKFLOW_SERVICE_AUTH_NOT_CONFIGURED");
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
            body.put("workflowType", workflowType);
            body.put("sourceEntityType", envelope.sourceEntityType());
            body.put("sourceEntityId", envelope.sourceEntityId().toString());
            body.put("sourceEntityVersion", envelope.sourceEntityVersion());
            body.put("requiredCapability", envelope.requiredCapability());
            body.put("dataClassification", envelope.dataClassification());
            body.set("payload", minimizedPayload == null ? mapper.createObjectNode() : minimizedPayload);

            String serviceToken = serviceJwtProvider.mint(
                    envelope.tenantId(), envelope.correlationId(), envelope.contractVersion(), audience);
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/v1/workflows/runs"))
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("Authorization", "Bearer " + serviceToken)
                    .header("X-Correlation-Id", envelope.correlationId())
                    .header("Idempotency-Key", envelope.idempotencyKey())
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 401) {
                return new WorkflowDispatch(
                        null, Status.UNAVAILABLE, Instant.now(), "WORKFLOW_SERVICE_UNAUTHORIZED");
            }
            if (response.statusCode() == 403) {
                return new WorkflowDispatch(
                        null, Status.REJECTED, Instant.now(), "WORKFLOW_POLICY_DENIED");
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return new WorkflowDispatch(
                        null, Status.UNAVAILABLE, Instant.now(),
                        "WORKFLOW_HTTP_" + response.statusCode());
            }
            JsonNode value = mapper.readTree(response.body());
            Status status = parseStatus(value.path("status").asText("UNAVAILABLE"));
            UUID runId = value.hasNonNull("workflowRunId")
                    ? UUID.fromString(value.get("workflowRunId").asText()) : null;
            Instant acceptedAt = parseInstant(value.path("acceptedAt").asText(null));
            if (acceptedAt == null) acceptedAt = Instant.now();
            String errorCode = value.hasNonNull("errorCode") ? value.get("errorCode").asText() : null;
            return new WorkflowDispatch(runId, status, acceptedAt, errorCode);
        } catch (java.net.http.HttpTimeoutException error) {
            return new WorkflowDispatch(null, Status.TIMED_OUT, Instant.now(), "WORKFLOW_TIMEOUT");
        } catch (Exception error) {
            return new WorkflowDispatch(null, Status.UNAVAILABLE, Instant.now(), "WORKFLOW_TRANSPORT_FAILURE");
        }
    }

    @Override
    public void cancel(
            UUID tenantId,
            UUID workflowRunId,
            String correlationId,
            String idempotencyKey,
            String reason) {
        if (baseUrl.isBlank()) {
            throw new IllegalStateException("Workflow Engine is not configured");
        }
        if (serviceJwtProvider == null || !serviceJwtProvider.isConfigured()) {
            throw new IllegalStateException("Workflow service authentication is not configured");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Workflow cancellation idempotency key is required");
        }
        try {
            ObjectNode body = mapper.createObjectNode();
            body.put("tenantId", tenantId.toString());
            body.put("correlationId", correlationId);
            body.put("idempotencyKey", idempotencyKey.strip());
            body.put("reason", reason == null ? "Cancelled by CRM" : reason);
            String serviceToken = serviceJwtProvider.mint(
                    tenantId, correlationId, "1.0", audience);
            HttpRequest request = HttpRequest.newBuilder(
                            URI.create(baseUrl + "/v1/workflows/runs/" + workflowRunId + "/cancel"))
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + serviceToken)
                    .header("X-Correlation-Id", correlationId)
                    .header("Idempotency-Key", idempotencyKey.strip())
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                    .build();
            HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException(
                        "Workflow cancellation failed with HTTP " + response.statusCode());
            }
        } catch (RuntimeException error) {
            throw error;
        } catch (Exception error) {
            throw new IllegalStateException("Workflow cancellation transport failed", error);
        }
    }

    private static Status parseStatus(String value) {
        try {
            return Status.valueOf(value);
        } catch (Exception ignored) {
            return Status.UNAVAILABLE;
        }
    }

    private static Instant parseInstant(String value) {
        try {
            return value == null ? null : Instant.parse(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String normalizeBaseUrl(String baseUrl) {
        return baseUrl == null ? "" : baseUrl.strip().replaceAll("/+$", "");
    }

    private static Duration boundedTimeout(long timeoutMs) {
        return Duration.ofMillis(Math.max(500, Math.min(timeoutMs, 20_000)));
    }
}
