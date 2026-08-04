package com.sanad.platform.crm.intelligence.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanad.platform.crm.integration.security.ServiceJwtProvider;
import com.sanad.platform.crm.intelligence.domain.AccountingDataPort;
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
 * HTTP adapter for Accounting data provider.
 * Active when provider=http. Calls the external accounting service to fetch customer snapshots.
 * Fail-closed: returns unavailable snapshot on any failure.
 */
@Component
@ConditionalOnProperty(name = "sanad.intelligence.accounting.provider", havingValue = "http")
public class HttpAccountingDataAdapter implements AccountingDataPort {

    private static final Logger log = LoggerFactory.getLogger(HttpAccountingDataAdapter.class);

    private final ObjectMapper mapper;
    private final HttpClient client;
    private final ServiceJwtProvider serviceJwtProvider;
    private final String baseUrl;
    private final Duration timeout;

    @Autowired
    public HttpAccountingDataAdapter(
            ObjectMapper mapper,
            ServiceJwtProvider serviceJwtProvider,
            @org.springframework.beans.factory.annotation.Value("${sanad.intelligence.accounting.base-url:}") String baseUrl,
            @org.springframework.beans.factory.annotation.Value("${sanad.intelligence.accounting.timeout-ms:5000}") long timeoutMs) {
        this.mapper = mapper;
        this.serviceJwtProvider = serviceJwtProvider;
        this.baseUrl = normalizeBaseUrl(baseUrl);
        this.timeout = boundedTimeout(timeoutMs);
        this.client = HttpClient.newBuilder().connectTimeout(this.timeout).build();
    }

    @Override
    public AccountingSnapshot loadSnapshot(UUID tenantId, UUID accountId) {
        if (baseUrl.isBlank()) {
            log.warn("Accounting HTTP adapter not configured - base URL is empty");
            return AccountingSnapshot.unavailable(accountId);
        }
        if (serviceJwtProvider == null || !serviceJwtProvider.isConfigured()) {
            log.warn("Accounting HTTP adapter - service JWT provider not configured");
            return AccountingSnapshot.unavailable(accountId);
        }
        try {
            String serviceToken = serviceJwtProvider.mint(
                    tenantId, UUID.randomUUID().toString(), "1.0", "sanad-accounting");
            HttpRequest request = HttpRequest.newBuilder(
                    URI.create(baseUrl + "/api/accounting/customers/" + accountId))
                    .timeout(timeout)
                    .header("Accept", "application/json")
                    .header("Authorization", "Bearer " + serviceToken)
                    .header("X-Tenant-Id", tenantId.toString())
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 404) {
                log.debug("Accounting customer not found for accountId={}", accountId);
                return AccountingSnapshot.unavailable(accountId);
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("Accounting HTTP adapter received status {} for accountId={}", response.statusCode(), accountId);
                return AccountingSnapshot.unavailable(accountId);
            }
            JsonNode json = mapper.readTree(response.body());
            return parseSnapshot(json, accountId);
        } catch (Exception error) {
            log.warn("Accounting HTTP adapter failed for accountId={}: {}", accountId, error.getMessage());
            return AccountingSnapshot.unavailable(accountId);
        }
    }

    private AccountingSnapshot parseSnapshot(JsonNode json, UUID accountId) {
        try {
            UUID snapshotAccountId = json.has("accountId") ?
                    UUID.fromString(json.get("accountId").asText()) : accountId;
            double totalReceivable = json.has("totalReceivable") ? json.get("totalReceivable").asDouble() : 0;
            double totalPayable = json.has("totalPayable") ? json.get("totalPayable").asDouble() : 0;
            int daysSalesOutstanding = json.has("daysSalesOutstanding") ? json.get("daysSalesOutstanding").asInt() : 0;
            String creditRating = json.has("creditRating") ?
                    json.get("creditRating").asText("UNKNOWN") : "UNKNOWN";
            double revenueYtd = json.has("revenueYtd") ? json.get("revenueYtd").asDouble() : 0;
            double grossMargin = json.has("grossMargin") ? json.get("grossMargin").asDouble() : 0;
            boolean available = json.has("available") ? json.get("available").asBoolean(true) : true;
            return new AccountingSnapshot(snapshotAccountId, totalReceivable, totalPayable,
                    daysSalesOutstanding, creditRating, revenueYtd, grossMargin, available);
        } catch (Exception error) {
            log.warn("Failed to parse Accounting snapshot: {}", error.getMessage());
            return AccountingSnapshot.unavailable(accountId);
        }
    }

    private static String normalizeBaseUrl(String baseUrl) {
        return baseUrl == null ? "" : baseUrl.strip().replaceAll("/+$", "");
    }

    private static Duration boundedTimeout(long timeoutMs) {
        return Duration.ofMillis(Math.max(500, Math.min(timeoutMs, 20_000)));
    }
}
