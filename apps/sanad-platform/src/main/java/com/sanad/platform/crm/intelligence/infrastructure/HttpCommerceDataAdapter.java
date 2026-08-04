package com.sanad.platform.crm.intelligence.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanad.platform.crm.integration.security.ServiceJwtProvider;
import com.sanad.platform.crm.intelligence.domain.CommerceDataPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * HTTP adapter for Commerce data provider.
 * Active when provider=http. Calls the external commerce service to fetch customer snapshots.
 * Fail-closed: returns unavailable snapshot on any failure.
 */
@Component
@ConditionalOnProperty(name = "sanad.intelligence.commerce.provider", havingValue = "http")
public class HttpCommerceDataAdapter implements CommerceDataPort {

    private static final Logger log = LoggerFactory.getLogger(HttpCommerceDataAdapter.class);

    private final ObjectMapper mapper;
    private final HttpClient client;
    private final ServiceJwtProvider serviceJwtProvider;
    private final String baseUrl;
    private final Duration timeout;

    @Autowired
    public HttpCommerceDataAdapter(
            ObjectMapper mapper,
            ServiceJwtProvider serviceJwtProvider,
            @org.springframework.beans.factory.annotation.Value("${sanad.intelligence.commerce.base-url:}") String baseUrl,
            @org.springframework.beans.factory.annotation.Value("${sanad.intelligence.commerce.timeout-ms:5000}") long timeoutMs) {
        this.mapper = mapper;
        this.serviceJwtProvider = serviceJwtProvider;
        this.baseUrl = normalizeBaseUrl(baseUrl);
        this.timeout = boundedTimeout(timeoutMs);
        this.client = HttpClient.newBuilder().connectTimeout(this.timeout).build();
    }

    @Override
    public CommerceSnapshot loadSnapshot(UUID tenantId, UUID accountId) {
        if (baseUrl.isBlank()) {
            log.warn("Commerce HTTP adapter not configured - base URL is empty");
            return CommerceSnapshot.unavailable(accountId);
        }
        if (serviceJwtProvider == null || !serviceJwtProvider.isConfigured()) {
            log.warn("Commerce HTTP adapter - service JWT provider not configured");
            return CommerceSnapshot.unavailable(accountId);
        }
        try {
            String serviceToken = serviceJwtProvider.mint(
                    tenantId, UUID.randomUUID().toString(), "1.0", "sanad-commerce");
            HttpRequest request = HttpRequest.newBuilder(
                    URI.create(baseUrl + "/api/commerce/customers/" + accountId))
                    .timeout(timeout)
                    .header("Accept", "application/json")
                    .header("Authorization", "Bearer " + serviceToken)
                    .header("X-Tenant-Id", tenantId.toString())
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 404) {
                log.debug("Commerce customer not found for accountId={}", accountId);
                return CommerceSnapshot.unavailable(accountId);
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("Commerce HTTP adapter received status {} for accountId={}", response.statusCode(), accountId);
                return CommerceSnapshot.unavailable(accountId);
            }
            JsonNode json = mapper.readTree(response.body());
            return parseSnapshot(json, accountId);
        } catch (Exception error) {
            log.warn("Commerce HTTP adapter failed for accountId={}: {}", accountId, error.getMessage());
            return CommerceSnapshot.unavailable(accountId);
        }
    }

    private CommerceSnapshot parseSnapshot(JsonNode json, UUID accountId) {
        try {
            UUID snapshotAccountId = json.has("accountId") ?
                    UUID.fromString(json.get("accountId").asText()) : accountId;
            int orderCount90d = json.has("orderCount90d") ? json.get("orderCount90d").asInt() : 0;
            double avgOrderValue = json.has("avgOrderValue") ? json.get("avgOrderValue").asDouble() : 0;
            String preferredChannel = json.has("preferredChannel") ?
                    json.get("preferredChannel").asText("UNKNOWN") : "UNKNOWN";
            double cartAbandonmentRate = json.has("cartAbandonmentRate") ? json.get("cartAbandonmentRate").asDouble() : 0;
            List<String> productCategories = new ArrayList<>();
            if (json.has("productCategories") && json.get("productCategories").isArray()) {
                json.get("productCategories").forEach(node -> productCategories.add(node.asText()));
            }
            Instant lastPurchaseAt = json.has("lastPurchaseAt") && !json.get("lastPurchaseAt").isNull() ?
                    Instant.parse(json.get("lastPurchaseAt").asText()) : null;
            boolean available = json.has("available") ? json.get("available").asBoolean(true) : true;
            return new CommerceSnapshot(snapshotAccountId, orderCount90d, avgOrderValue,
                    preferredChannel, cartAbandonmentRate, productCategories, lastPurchaseAt, available);
        } catch (Exception error) {
            log.warn("Failed to parse Commerce snapshot: {}", error.getMessage());
            return CommerceSnapshot.unavailable(accountId);
        }
    }

    private static String normalizeBaseUrl(String baseUrl) {
        return baseUrl == null ? "" : baseUrl.strip().replaceAll("/+$", "");
    }

    private static Duration boundedTimeout(long timeoutMs) {
        return Duration.ofMillis(Math.max(500, Math.min(timeoutMs, 20_000)));
    }
}
