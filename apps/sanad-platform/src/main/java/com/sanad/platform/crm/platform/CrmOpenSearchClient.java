package com.sanad.platform.crm.platform;

import java.util.Locale;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

@Component
@ConditionalOnProperty(prefix = "sanad.crm.platform.search", name = "enabled", havingValue = "true")
public class CrmOpenSearchClient {

    private final RestClient client;
    private final String indexPrefix;

    public CrmOpenSearchClient(RestClient.Builder builder, CrmPlatformProperties properties) {
        this.client = builder.baseUrl(properties.getSearch().getEndpoint().toString()).build();
        this.indexPrefix = properties.getSearch().getIndexPrefix();
    }

    public void upsert(
            UUID tenantId,
            String entityType,
            UUID entityId,
            long documentVersion,
            String payload) {
        client.put()
            .uri(buildUri(tenantId, entityType, entityId, documentVersion))
            .contentType(MediaType.APPLICATION_JSON)
            .body(payload == null || payload.isBlank() ? "{}" : payload)
            .retrieve()
            .toBodilessEntity();
    }

    public void delete(
            UUID tenantId,
            String entityType,
            UUID entityId,
            long documentVersion) {
        client.delete()
            .uri(buildUri(tenantId, entityType, entityId, documentVersion))
            .retrieve()
            .toBodilessEntity();
    }

    private java.net.URI buildUri(
            UUID tenantId,
            String entityType,
            UUID entityId,
            long documentVersion) {
        return UriComponentsBuilder.fromPath("/{index}/_doc/{id}")
            .queryParam("routing", tenantId)
            .queryParam("version", Math.max(1, documentVersion))
            .queryParam("version_type", "external_gte")
            .buildAndExpand(indexName(entityType), entityId)
            .encode()
            .toUri();
    }

    private String indexName(String entityType) {
        String suffix = entityType.toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9_-]", "-");
        return indexPrefix + "-" + suffix;
    }
}
