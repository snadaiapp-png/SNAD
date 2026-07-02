package com.sanad.platform.crm.platform;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@ConditionalOnProperty(prefix = "sanad.crm.platform.ai-gateway", name = "enabled", havingValue = "true")
public class CrmAiGatewayClient {

    private final RestClient client;

    public CrmAiGatewayClient(RestClient.Builder builder, CrmPlatformProperties properties) {
        RestClient.Builder configured = builder
            .baseUrl(properties.getAiGateway().getEndpoint().toString());
        if (!properties.getAiGateway().getApiKey().isBlank()) {
            configured.defaultHeader(
                "Authorization",
                "Bearer " + properties.getAiGateway().getApiKey());
        }
        this.client = configured.build();
    }

    public JsonNode summarize(UUID tenantId, UUID actorId, String minimizedText) {
        return invoke("/v1/summarize", tenantId, actorId, minimizedText);
    }

    public JsonNode score(UUID tenantId, UUID actorId, String minimizedText) {
        return invoke("/v1/score", tenantId, actorId, minimizedText);
    }

    public JsonNode nextAction(UUID tenantId, UUID actorId, String minimizedText) {
        return invoke("/v1/next-action", tenantId, actorId, minimizedText);
    }

    private JsonNode invoke(String path, UUID tenantId, UUID actorId, String minimizedText) {
        JsonNode response = client.post()
            .uri(path)
            .contentType(MediaType.APPLICATION_JSON)
            .header("X-SNAD-Tenant-ID", tenantId.toString())
            .header("X-SNAD-Actor-ID", actorId.toString())
            .header("X-Request-ID", UUID.randomUUID().toString())
            .body(Map.of("text", minimizedText))
            .retrieve()
            .body(JsonNode.class);
        if (response == null || !response.path("advisory").asBoolean(false)) {
            throw new IllegalStateException("AI gateway response violated the advisory-only policy");
        }
        return response;
    }
}
