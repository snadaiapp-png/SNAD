package com.sanad.platform.integration.zid;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanad.platform.integration.zid.config.ZidIntegrationProperties;
import com.sanad.platform.integration.zid.domain.ZidIntegrationException;
import com.sanad.platform.integration.zid.infrastructure.ZidConnector;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Wire-level connector tests against a local stub HTTP server:
 * verifies the documented header families are actually sent, and the
 * 401/429/5xx error classification contract (Retry-After honored on
 * 429).
 */
class ZidConnectorHttpStubTest {

    private HttpServer server;
    private ZidConnector connector;
    private ZidIntegrationProperties properties;

    private final java.util.Map<String, String> capturedHeaders = new java.util.LinkedHashMap<>();
    private final AtomicReference<String> capturedPath = new AtomicReference<>();

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/products/", exchange -> {
            capturedHeaders.clear();
            exchange.getRequestHeaders().forEach((k, v) -> capturedHeaders.put(k, String.join(",", v)));
            capturedPath.set(exchange.getRequestURI().toString());
            byte[] body = "{\"results\":[],\"count\":0}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        server.start();
        int port = server.getAddress().getPort();

        properties = new ZidIntegrationProperties();
        properties.setApiBaseUrl("http://127.0.0.1:" + port + "/v1");
        properties.setOauthBaseUrl("http://127.0.0.1:" + port + "/oauth");
        connector = new ZidConnector(properties, new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private ZidConnector.ConnectionCredentials credentials() {
        return new ZidConnector.ConnectionCredentials("auth-value", "manager-value", "3");
    }

    @Test
    void sendsVerifiedHeaderFamilies() {
        ZidConnector.ZidResponse response = connector.get(credentials(), "products/",
                Map.of("page", "1", "page_size", "50"));
        assertThat(response.isSuccess()).isTrue();
        // Case-insensitive lookup (JDK HttpServer normalizes header case).
        Map<String, String> headers = lower(capturedHeaders);
        // Documented orders-family headers
        assertThat(headers.get("authorization")).isEqualTo("auth-value");
        assertThat(headers.get("x-manager-token")).isEqualTo("manager-value");
        // Documented products-family headers
        assertThat(headers.get("access-token")).isEqualTo("manager-value");
        assertThat(headers.get("store-id")).isEqualTo("3");
        assertThat(headers.get("role")).isEqualTo("Manager");
        assertThat(headers.get("accept-language")).isEqualTo("en");
    }

    private static Map<String, String> lower(Map<String, String> headers) {
        Map<String, String> out = new java.util.LinkedHashMap<>();
        headers.forEach((k, v) -> out.put(k.toLowerCase(java.util.Locale.ROOT), v));
        return out;
    }

    @Test
    void buildsAuthorizeUrlWithOnlyNonSecretParameters() {
        String url = connector.buildAuthorizeUrl("cid", "https://app.example/cb", "state123");
        assertThat(url).startsWith("http://127.0.0.1").contains("/oauth2/authorize")
                .contains("client_id=cid")
                .contains("redirect_uri=https://app.example/cb")
                .contains("response_type=code")
                .contains("state=state123");
        assertThat(url).doesNotContain("secret");
    }

    @Test
    void classifies401AsTokenRejected() throws IOException {
        HttpServer s = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        try {
            startSimple(s, 401, "{}");
            properties.setApiBaseUrl("http://127.0.0.1:" + s.getAddress().getPort() + "/v1");
            ZidConnector fresh = new ZidConnector(properties, new ObjectMapper());
            assertThatThrownBy(() -> fresh.get(credentials(), "products/", Map.of()))
                    .isInstanceOf(ZidIntegrationException.class)
                    .satisfies(e -> assertThat(((ZidIntegrationException) e).reason())
                            .isEqualTo(ZidIntegrationException.Reason.TOKEN_REJECTED));
        } finally {
            s.stop(0);
        }
    }

    @Test
    void classifies429AsRateLimitedWithRetryAfter() throws IOException {
        HttpServer s = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        try {
            startSimple(s, 429, "{\"message\":\"rate limited\"}", "Retry-After: 30");
            properties.setApiBaseUrl("http://127.0.0.1:" + s.getAddress().getPort() + "/v1");
            ZidConnector fresh = new ZidConnector(properties, new ObjectMapper());
            assertThatThrownBy(() -> fresh.get(credentials(), "products/", Map.of()))
                    .isInstanceOf(ZidConnector.ZidRateLimitedException.class)
                    .satisfies(e -> assertThat(((ZidConnector.ZidRateLimitedException) e).retryAfter())
                            .hasValue(java.time.Duration.ofSeconds(30)));
        } finally {
            s.stop(0);
        }
    }

    @Test
    void classifies5xxAsTransientFailure() throws IOException {
        HttpServer s = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        try {
            startSimple(s, 503, "overloaded");
            properties.setApiBaseUrl("http://127.0.0.1:" + s.getAddress().getPort() + "/v1");
            ZidConnector fresh = new ZidConnector(properties, new ObjectMapper());
            assertThatThrownBy(() -> fresh.get(credentials(), "products/", Map.of()))
                    .isInstanceOf(ZidIntegrationException.class)
                    .satisfies(e -> assertThat(((ZidIntegrationException) e).reason())
                            .isEqualTo(ZidIntegrationException.Reason.TRANSIENT_FAILURE));
        } finally {
            s.stop(0);
        }
    }

    private void startSimple(HttpServer server, int status, String body, String... extraHeaders) {
        server.createContext("/", exchange -> {
            byte[] payload = body.getBytes(StandardCharsets.UTF_8);
            for (String header : extraHeaders) {
                String[] parts = header.split(":\\s*", 2);
                exchange.getResponseHeaders().add(parts[0], parts[1]);
            }
            exchange.sendResponseHeaders(status, payload.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(payload);
            }
        });
        server.start();
    }
}
