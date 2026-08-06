package com.sanad.platform.crm.intelligence.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanad.platform.crm.integration.security.ServiceJwtProvider;
import com.sanad.platform.crm.intelligence.domain.HrmDataPort;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * HTTP adapter for HRM data provider.
 * Active when provider=http. Calls the external HRM service to fetch account team snapshots.
 * Fail-closed: returns unavailable snapshot on any failure.
 */
@Component
@ConditionalOnProperty(name = "sanad.intelligence.hrm.provider", havingValue = "http")
public class HttpHrmDataAdapter implements HrmDataPort {

    private static final Logger log = LoggerFactory.getLogger(HttpHrmDataAdapter.class);

    private final ObjectMapper mapper;
    private final HttpClient client;
    private final ServiceJwtProvider serviceJwtProvider;
    private final String baseUrl;
    private final Duration timeout;

    @Autowired
    public HttpHrmDataAdapter(
            ObjectMapper mapper,
            ServiceJwtProvider serviceJwtProvider,
            @org.springframework.beans.factory.annotation.Value("${sanad.intelligence.hrm.base-url:}") String baseUrl,
            @org.springframework.beans.factory.annotation.Value("${sanad.intelligence.hrm.timeout-ms:5000}") long timeoutMs) {
        this.mapper = mapper;
        this.serviceJwtProvider = serviceJwtProvider;
        this.baseUrl = normalizeBaseUrl(baseUrl);
        this.timeout = boundedTimeout(timeoutMs);
        this.client = HttpClient.newBuilder().connectTimeout(this.timeout).build();
    }

    @Override
    public HrmAccountTeamSnapshot loadAccountTeam(UUID tenantId, UUID accountId) {
        if (baseUrl.isBlank()) {
            log.warn("HRM HTTP adapter not configured - base URL is empty");
            return HrmAccountTeamSnapshot.unavailable(accountId);
        }
        if (serviceJwtProvider == null || !serviceJwtProvider.isConfigured()) {
            log.warn("HRM HTTP adapter - service JWT provider not configured");
            return HrmAccountTeamSnapshot.unavailable(accountId);
        }
        try {
            String serviceToken = serviceJwtProvider.mint(
                    tenantId, UUID.randomUUID().toString(), "1.0", "sanad-hrm");
            HttpRequest request = HttpRequest.newBuilder(
                    URI.create(baseUrl + "/api/hrm/accounts/" + accountId))
                    .timeout(timeout)
                    .header("Accept", "application/json")
                    .header("Authorization", "Bearer " + serviceToken)
                    .header("X-Tenant-Id", tenantId.toString())
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 404) {
                log.debug("HRM account not found for accountId={}", accountId);
                return HrmAccountTeamSnapshot.unavailable(accountId);
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("HRM HTTP adapter received status {} for accountId={}", response.statusCode(), accountId);
                return HrmAccountTeamSnapshot.unavailable(accountId);
            }
            JsonNode json = mapper.readTree(response.body());
            return parseSnapshot(json, accountId);
        } catch (Exception error) {
            log.warn("HRM HTTP adapter failed for accountId={}: {}", accountId, error.getMessage());
            return HrmAccountTeamSnapshot.unavailable(accountId);
        }
    }

    private HrmAccountTeamSnapshot parseSnapshot(JsonNode json, UUID accountId) {
        try {
            UUID snapshotAccountId = json.has("accountId") ?
                    UUID.fromString(json.get("accountId").asText()) : accountId;
            String accountManagerName = json.has("accountManagerName") ?
                    json.get("accountManagerName").asText("N/A") : "N/A";
            String accountManagerEmail = json.has("accountManagerEmail") ?
                    json.get("accountManagerEmail").asText(null) : null;
            List<String> teamMembers = new ArrayList<>();
            if (json.has("teamMembers") && json.get("teamMembers").isArray()) {
                json.get("teamMembers").forEach(node -> teamMembers.add(node.asText()));
            }
            int teamSize = json.has("teamSize") ? json.get("teamSize").asInt(teamMembers.size()) : teamMembers.size();
            String coverageStatus = json.has("coverageStatus") ?
                    json.get("coverageStatus").asText("UNKNOWN") : "UNKNOWN";
            boolean available = json.has("available") ? json.get("available").asBoolean(true) : true;
            return new HrmAccountTeamSnapshot(snapshotAccountId, accountManagerName, accountManagerEmail,
                    teamMembers, teamSize, coverageStatus, available);
        } catch (Exception error) {
            log.warn("Failed to parse HRM snapshot: {}", error.getMessage());
            return HrmAccountTeamSnapshot.unavailable(accountId);
        }
    }

    private static String normalizeBaseUrl(String baseUrl) {
        return baseUrl == null ? "" : baseUrl.strip().replaceAll("/+$", "");
    }

    private static Duration boundedTimeout(long timeoutMs) {
        return Duration.ofMillis(Math.max(500, Math.min(timeoutMs, 20_000)));
    }
}
