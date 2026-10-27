package com.sanad.platform.integration.zid.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanad.platform.integration.zid.config.ZidIntegrationProperties;
import com.sanad.platform.integration.zid.domain.ZidIntegrationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Outbound HTTP gateway to the official Zid APIs.
 *
 * <p><b>Header families (verified from docs.zid.sa, 2026-10-28):</b></p>
 * <ul>
 *   <li>Orders API: {@code Authorization: <authorization token>} and
 *       {@code X-Manager-Token: <access token>} and
 *       {@code Accept-Language: en|ar}.</li>
 *   <li>Products/stock API: {@code Access-Token: <access token>},
 *       {@code Store-Id: <store id>}, {@code Role: Manager},
 *       {@code Accept-Language: en|ar}. The same family also documents
 *       {@code Authorization} on the OAuth page ("almost every API
 *       request requires both"), so both families are sent together to
 *       remain compatible with both API generations.</li>
 * </ul>
 *
 * <p><b>Error classification:</b> 401 maps to {@code TOKEN_REJECTED}
 * (operator must re-authorize); 429 maps to a retryable rate-limit
 * failure honoring {@code Retry-After}; 5xx and IO/timeouts map to
 * retryable {@code TRANSIENT_FAILURE}. No response body is ever logged
 * verbatim (bodies may echo merchant data); only the status code is.</p>
 */
@Component
public class ZidConnector {

    private static final Logger log = LoggerFactory.getLogger(ZidConnector.class);

    private final ZidIntegrationProperties properties;
    private final HttpClient http;
    private final ObjectMapper objectMapper;

    public ZidConnector(ZidIntegrationProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.http = HttpClient.newBuilder()
                .connectTimeout(properties.getConnectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    /** Authenticated values for one store connection. */
    public record ConnectionCredentials(String authorizationToken, String managerToken, String zidStoreId) {
    }

    /** A classified API response. */
    public record ZidResponse(int statusCode, JsonNode body, Optional<Duration> retryAfter) {
        public boolean isSuccess() {
            return statusCode >= 200 && statusCode < 300;
        }
    }

    /**
     * GET a paginated collection endpoint using the verified header
     * families. Query parameters are appended by the caller as a map of
     * already-encoded-safe values (only simple tokens/numbers).
     */
    public ZidResponse get(ConnectionCredentials credentials, String path, Map<String, String> queryParams) {
        StringBuilder url = new StringBuilder(ensureTrailingSlash(properties.getApiBaseUrl()));
        url.append(stripLeadingSlash(path));
        if (queryParams != null && !queryParams.isEmpty()) {
            url.append(url.toString().contains("?") ? '&' : '?');
            List<String> pairs = new ArrayList<>();
            queryParams.forEach((k, v) -> pairs.add(k + "=" + v));
            url.append(String.join("&", pairs));
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url.toString()))
                .timeout(properties.getRequestTimeout())
                .header("Accept", "application/json")
                .header("Accept-Language", "en")
                .header("Authorization", credentials.authorizationToken())
                .header("X-Manager-Token", credentials.managerToken())
                .header("Access-Token", credentials.managerToken())
                .header("Store-Id", credentials.zidStoreId())
                .header("Role", "Manager")
                .GET()
                .build();
        return execute(request);
    }

    /**
     * POST the OAuth token exchange / refresh (form-encoded, unauthenticated
     * except by client credentials). The token endpoint sits beneath the
     * OAuth base URL, not the API base URL.
     */
    public ZidResponse postTokenForm(String path, Map<String, String> formFields) {
        StringBuilder url = new StringBuilder(stripTrailingSlash(properties.getOauthBaseUrl()));
        url.append(path.startsWith("/") ? path : "/" + path);

        StringBuilder body = new StringBuilder();
        formFields.forEach((k, v) -> {
            if (body.length() > 0) body.append('&');
            body.append(k).append('=').append(v);
        });

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url.toString()))
                .timeout(properties.getRequestTimeout())
                .header("Accept", "application/json")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();
        return execute(request);
    }

    /**
     * Build the absolute authorize URL for the merchant consent screen.
     * Only non-secret parameters are included; the client secret is
     * never placed in a browser-facing URL.
     */
    public String buildAuthorizeUrl(String clientId, String redirectUri, String state) {
        StringBuilder url = new StringBuilder(stripTrailingSlash(properties.getOauthBaseUrl()));
        url.append(properties.getAuthorizePath().startsWith("/") ? "" : "/");
        url.append(properties.getAuthorizePath());
        url.append("?client_id=").append(clientId);
        url.append("&redirect_uri=").append(redirectUri);
        url.append("&response_type=code");
        url.append("&state=").append(state);
        return url.toString();
    }

    private ZidResponse execute(HttpRequest request) {
        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (java.net.http.HttpTimeoutException e) {
            throw new ZidIntegrationException(ZidIntegrationException.Reason.TRANSIENT_FAILURE,
                    "Zid API request timed out", e);
        } catch (IOException e) {
            throw new ZidIntegrationException(ZidIntegrationException.Reason.TRANSIENT_FAILURE,
                    "Zid API network failure", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ZidIntegrationException(ZidIntegrationException.Reason.TRANSIENT_FAILURE,
                    "Zid API request interrupted", e);
        }

        Optional<Duration> retryAfter = Optional.empty();
        if (response.statusCode() == 429) {
            String header = response.headers().firstValue("Retry-After").orElse(null);
            if (header != null) {
                try {
                    retryAfter = Optional.of(Duration.ofSeconds(Long.parseLong(header.trim())));
                } catch (NumberFormatException ignored) {
                    retryAfter = Optional.empty();
                }
            }
        }

        JsonNode body = null;
        if (response.body() != null && !response.body().isBlank()) {
            try {
                body = objectMapper.readTree(response.body());
            } catch (Exception e) {
                // Non-JSON body — keep body null; status code still drives classification.
                log.debug("Zid API returned a non-JSON body with status {}", response.statusCode());
            }
        }

        int status = response.statusCode();
        if (status == 401) {
            throw new ZidIntegrationException(ZidIntegrationException.Reason.TOKEN_REJECTED,
                    "Zid API rejected the stored credentials (401)");
        }
        if (status == 429) {
            throw new ZidRateLimitedException("Zid API rate limit reached (429)", retryAfter);
        }
        if (status >= 500) {
            throw new ZidIntegrationException(ZidIntegrationException.Reason.TRANSIENT_FAILURE,
                    "Zid API server error (" + status + ")");
        }
        return new ZidResponse(status, body, retryAfter);
    }

    private static String ensureTrailingSlash(String value) {
        return value.endsWith("/") ? value : value + "/";
    }

    private static String stripTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private static String stripLeadingSlash(String value) {
        return value.startsWith("/") ? value.substring(1) : value;
    }

    /** Retryable rate-limit failure carrying the Retry-After hint. */
    public static class ZidRateLimitedException extends ZidIntegrationException {
        private final Optional<Duration> retryAfter;

        public ZidRateLimitedException(String message, Optional<Duration> retryAfter) {
            super(Reason.TRANSIENT_FAILURE, message);
            this.retryAfter = retryAfter;
        }

        public Optional<Duration> retryAfter() {
            return retryAfter;
        }
    }
}
