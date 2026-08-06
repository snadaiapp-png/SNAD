package com.sanad.platform.crm.intelligence.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanad.platform.crm.integration.security.ServiceJwtProvider;
import com.sanad.platform.crm.intelligence.domain.ErpDataPort;
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
import java.util.UUID;

/**
 * HTTP adapter for ERP data provider.
 * Active when provider=http. Calls the external ERP service to fetch customer snapshots.
 * Fail-closed: returns unavailable snapshot on any failure.
 */
@Component
@ConditionalOnProperty(name = "sanad.intelligence.erp.provider", havingValue = "http")
public class HttpErpDataAdapter implements ErpDataPort {

    private static final Logger log = LoggerFactory.getLogger(HttpErpDataAdapter.class);

    private final ObjectMapper mapper;
    private final HttpClient client;
    private final ServiceJwtProvider serviceJwtProvider;
    private final String baseUrl;
    private final Duration timeout;

    @Autowired
    public HttpErpDataAdapter(
            ObjectMapper mapper,
            ServiceJwtProvider serviceJwtProvider,
            @org.springframework.beans.factory.annotation.Value("${sanad.intelligence.erp.base-url:}") String baseUrl,
            @org.springframework.beans.factory.annotation.Value("${sanad.intelligence.erp.timeout-ms:5000}") long timeoutMs) {
        this.mapper = mapper;
        this.serviceJwtProvider = serviceJwtProvider;
        this.baseUrl = normalizeBaseUrl(baseUrl);
        this.timeout = boundedTimeout(timeoutMs);
        this.client = HttpClient.newBuilder().connectTimeout(this.timeout).build();
    }

    @Override
    public ErpCustomerSnapshot loadCustomerSnapshot(UUID tenantId, UUID accountId) {
        if (baseUrl.isBlank()) {
            log.warn("ERP HTTP adapter not configured - base URL is empty");
            return ErpCustomerSnapshot.unavailable(accountId);
        }
        if (serviceJwtProvider == null || !serviceJwtProvider.isConfigured()) {
            log.warn("ERP HTTP adapter - service JWT provider not configured");
            return ErpCustomerSnapshot.unavailable(accountId);
        }
        try {
            String serviceToken = serviceJwtProvider.mint(
                    tenantId, UUID.randomUUID().toString(), "1.0", "sanad-erp");
            HttpRequest request = HttpRequest.newBuilder(
                    URI.create(baseUrl + "/api/erp/customers/" + accountId))
                    .timeout(timeout)
                    .header("Accept", "application/json")
                    .header("Authorization", "Bearer " + serviceToken)
                    .header("X-Tenant-Id", tenantId.toString())
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 404) {
                log.debug("ERP customer not found for accountId={}", accountId);
                return ErpCustomerSnapshot.unavailable(accountId);
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("ERP HTTP adapter received status {} for accountId={}", response.statusCode(), accountId);
                return ErpCustomerSnapshot.unavailable(accountId);
            }
            JsonNode json = mapper.readTree(response.body());
            return parseSnapshot(json, accountId);
        } catch (Exception error) {
            log.warn("ERP HTTP adapter failed for accountId={}: {}", accountId, error.getMessage());
            return ErpCustomerSnapshot.unavailable(accountId);
        }
    }

    private ErpCustomerSnapshot parseSnapshot(JsonNode json, UUID accountId) {
        try {
            UUID snapshotAccountId = json.has("accountId") ?
                    UUID.fromString(json.get("accountId").asText()) : accountId;
            double totalRevenue = json.has("totalRevenue") ? json.get("totalRevenue").asDouble() : 0;
            int orderCount = json.has("orderCount") ? json.get("orderCount").asInt() : 0;
            double outstandingBalance = json.has("outstandingBalance") ? json.get("outstandingBalance").asDouble() : 0;
            String paymentStatus = json.has("paymentStatus") ? json.get("paymentStatus").asText("UNKNOWN") : "UNKNOWN";
            String creditStatus = json.has("creditStatus") ? json.get("creditStatus").asText("UNKNOWN") : "UNKNOWN";
            Instant lastOrderAt = json.has("lastOrderAt") && !json.get("lastOrderAt").isNull() ?
                    Instant.parse(json.get("lastOrderAt").asText()) : null;
            boolean available = json.has("available") ? json.get("available").asBoolean(true) : true;
            return new ErpCustomerSnapshot(snapshotAccountId, totalRevenue, orderCount,
                    outstandingBalance, paymentStatus, creditStatus, lastOrderAt, available);
        } catch (Exception error) {
            log.warn("Failed to parse ERP snapshot: {}", error.getMessage());
            return ErpCustomerSnapshot.unavailable(accountId);
        }
    }

    private static String normalizeBaseUrl(String baseUrl) {
        return baseUrl == null ? "" : baseUrl.strip().replaceAll("/+$", "");
    }

    private static Duration boundedTimeout(long timeoutMs) {
        return Duration.ofMillis(Math.max(500, Math.min(timeoutMs, 20_000)));
    }
}
