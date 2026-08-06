package com.sanad.platform.crm.intelligence.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanad.platform.crm.integration.security.ServiceJwtProvider;
import com.sanad.platform.crm.intelligence.domain.PosDataPort;
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
import java.util.UUID;

/**
 * HTTP adapter for POS data provider.
 * Active when provider=http. Calls the external POS service to fetch customer snapshots.
 * Fail-closed: returns unavailable snapshot on any failure.
 */
@Component
@ConditionalOnProperty(name = "sanad.intelligence.pos.provider", havingValue = "http")
public class HttpPosDataAdapter implements PosDataPort {

    private static final Logger log = LoggerFactory.getLogger(HttpPosDataAdapter.class);

    private final ObjectMapper mapper;
    private final HttpClient client;
    private final ServiceJwtProvider serviceJwtProvider;
    private final String baseUrl;
    private final Duration timeout;

    @Autowired
    public HttpPosDataAdapter(
            ObjectMapper mapper,
            ServiceJwtProvider serviceJwtProvider,
            @org.springframework.beans.factory.annotation.Value("${sanad.intelligence.pos.base-url:}") String baseUrl,
            @org.springframework.beans.factory.annotation.Value("${sanad.intelligence.pos.timeout-ms:5000}") long timeoutMs) {
        this.mapper = mapper;
        this.serviceJwtProvider = serviceJwtProvider;
        this.baseUrl = normalizeBaseUrl(baseUrl);
        this.timeout = boundedTimeout(timeoutMs);
        this.client = HttpClient.newBuilder().connectTimeout(this.timeout).build();
    }

    @Override
    public PosCustomerSnapshot loadCustomerSnapshot(UUID tenantId, UUID accountId) {
        if (baseUrl.isBlank()) {
            log.warn("POS HTTP adapter not configured - base URL is empty");
            return PosCustomerSnapshot.unavailable(accountId);
        }
        if (serviceJwtProvider == null || !serviceJwtProvider.isConfigured()) {
            log.warn("POS HTTP adapter - service JWT provider not configured");
            return PosCustomerSnapshot.unavailable(accountId);
        }
        try {
            String serviceToken = serviceJwtProvider.mint(
                    tenantId, UUID.randomUUID().toString(), "1.0", "sanad-pos");
            HttpRequest request = HttpRequest.newBuilder(
                    URI.create(baseUrl + "/api/pos/customers/" + accountId))
                    .timeout(timeout)
                    .header("Accept", "application/json")
                    .header("Authorization", "Bearer " + serviceToken)
                    .header("X-Tenant-Id", tenantId.toString())
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 404) {
                log.debug("POS customer not found for accountId={}", accountId);
                return PosCustomerSnapshot.unavailable(accountId);
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("POS HTTP adapter received status {} for accountId={}", response.statusCode(), accountId);
                return PosCustomerSnapshot.unavailable(accountId);
            }
            JsonNode json = mapper.readTree(response.body());
            return parseSnapshot(json, accountId);
        } catch (Exception error) {
            log.warn("POS HTTP adapter failed for accountId={}: {}", accountId, error.getMessage());
            return PosCustomerSnapshot.unavailable(accountId);
        }
    }

    private PosCustomerSnapshot parseSnapshot(JsonNode json, UUID accountId) {
        try {
            UUID snapshotAccountId = json.has("accountId") ?
                    UUID.fromString(json.get("accountId").asText()) : accountId;
            int transactionCount30d = json.has("transactionCount30d") ? json.get("transactionCount30d").asInt() : 0;
            double avgTransactionValue = json.has("avgTransactionValue") ? json.get("avgTransactionValue").asDouble() : 0;
            String preferredStore = json.has("preferredStore") ?
                    json.get("preferredStore").asText("UNKNOWN") : "UNKNOWN";
            double loyaltyPointsBalance = json.has("loyaltyPointsBalance") ? json.get("loyaltyPointsBalance").asDouble() : 0;
            boolean available = json.has("available") ? json.get("available").asBoolean(true) : true;
            return new PosCustomerSnapshot(snapshotAccountId, transactionCount30d, avgTransactionValue,
                    preferredStore, loyaltyPointsBalance, available);
        } catch (Exception error) {
            log.warn("Failed to parse POS snapshot: {}", error.getMessage());
            return PosCustomerSnapshot.unavailable(accountId);
        }
    }

    private static String normalizeBaseUrl(String baseUrl) {
        return baseUrl == null ? "" : baseUrl.strip().replaceAll("/+$", "");
    }

    private static Duration boundedTimeout(long timeoutMs) {
        return Duration.ofMillis(Math.max(500, Math.min(timeoutMs, 20_000)));
    }
}
