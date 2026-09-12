package com.sanad.platform.workflow.notification;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * WEBHOOK channel provider (GATE R2.10). Strictly governed outbound
 * delivery:
 * <ul>
 *   <li>destinations come ONLY from the {@code workflow_webhook_endpoints}
 *       registry (HTTPS-validated, tenant-owned, status-gated);</li>
 *   <li>HMAC-SHA256 request signature + timestamp (replay window) when the
 *       endpoint has a registered secret;</li>
 *   <li>SSRF protection: host resolution rejects loopback, link-local,
 *       site-local and any-local addresses;</li>
 *   <li>bounded payload, timeout, single-shot per attempt (dispatcher owns
 *       retries).</li>
 * </ul>
 * No unrestricted generic HTTP execution is exposed to workflow authors.
 */
@Component
public class WebhookNotificationProvider implements WorkflowChannelProvider {

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(WebhookNotificationProvider.class);

    private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    private final Duration timeout;
    private final int maxPayloadBytes;
    private final HttpClient http;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper =
            new com.fasterxml.jackson.databind.ObjectMapper();

    public WebhookNotificationProvider(
            org.springframework.jdbc.core.JdbcTemplate jdbcTemplate,
            @Value("${sanad.workflow.webhook.timeout-seconds:10}") long timeoutSeconds,
            @Value("${sanad.workflow.webhook.max-payload-bytes:65536}") int maxPayloadBytes) {
        this.jdbc = jdbcTemplate;
        this.timeout = Duration.ofSeconds(timeoutSeconds);
        this.maxPayloadBytes = maxPayloadBytes;
        this.http = HttpClient.newBuilder().connectTimeout(timeout)
                .followRedirects(HttpClient.Redirect.NEVER).build();
    }

    @Override
    public WorkflowChannel channel() {
        return WorkflowChannel.WEBHOOK;
    }

    @Override
    public String providerType() {
        return "governed-webhook";
    }

    @Override
    public WorkflowDeliveryResult deliver(WorkflowDeliveryRequest request) {
        if (request.recipientAddress() == null || request.recipientAddress().isBlank()) {
            // recipient_address carries the resolved endpoint URL for webhook
            // intents (set by the webhook targeting step).
            return WorkflowDeliveryResult.terminalFailure(
                    WorkflowDeliveryResult.FC_INVALID_RECIPIENT);
        }
        String url = request.recipientAddress();
        try {
            URI uri = URI.create(url);
            if (!"https".equalsIgnoreCase(uri.getScheme())) {
                return WorkflowDeliveryResult.terminalFailure(
                        WorkflowDeliveryResult.FC_CONFIG_ERROR);
            }
            if (isPrivateOrLocal(InetAddress.getByName(uri.getHost()))) {
                log.warn("WEBHOOK intent {} blocked by SSRF protection: {}",
                        request.intentId(), uri.getHost());
                return WorkflowDeliveryResult.terminalFailure(
                        WorkflowDeliveryResult.FC_CONFIG_ERROR);
            }
            String secret = resolveSecret(request.tenantId(), url);
            String payload = buildPayload(request);
            if (payload.getBytes(StandardCharsets.UTF_8).length > maxPayloadBytes) {
                return WorkflowDeliveryResult.terminalFailure(
                        WorkflowDeliveryResult.FC_REJECTED);
            }
            long timestamp = System.currentTimeMillis() / 1000L;
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(uri)
                    .timeout(timeout)
                    .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .header("X-SNAD-Event-Type", request.eventType())
                    .header("X-SNAD-Intent-Id", request.intentId().toString())
                    .header("X-SNAD-Timestamp", Long.toString(timestamp))
                    .POST(HttpRequest.BodyPublishers.ofString(payload));
            if (!secret.isEmpty()) {
                builder.header("X-SNAD-Signature", "sha256=" + hmacSha256Hex(secret,
                        timestamp + "." + payload));
            }
            HttpResponse<String> response =
                    http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return WorkflowDeliveryResult.success("wh:" + request.intentId());
            }
            if (response.statusCode() == 410 || response.statusCode() == 404) {
                return WorkflowDeliveryResult.terminalFailure(
                        WorkflowDeliveryResult.FC_REJECTED);
            }
            return WorkflowDeliveryResult.retryableFailure(
                    WorkflowDeliveryResult.FC_TRANSIENT);
        } catch (java.net.http.HttpTimeoutException e) {
            return WorkflowDeliveryResult.retryableFailure(
                    WorkflowDeliveryResult.FC_TIMEOUT);
        } catch (Exception e) {
            log.warn("WEBHOOK intent {} delivery error: {}",
                    request.intentId(), e.getMessage());
            return WorkflowDeliveryResult.retryableFailure(
                    WorkflowDeliveryResult.FC_TRANSIENT);
        }
    }

    private String buildPayload(WorkflowDeliveryRequest request) {
        try {
            return objectMapper.writeValueAsString(Map.ofEntries(
                    Map.entry("eventType", request.eventType()),
                    Map.entry("tenantId", request.tenantId().toString()),
                    Map.entry("workflowInstanceId",
                            String.valueOf(request.workflowInstanceId())),
                    Map.entry("workItemId", String.valueOf(request.workItemId())),
                    Map.entry("externalActionId",
                            String.valueOf(request.externalActionId())),
                    Map.entry("correlationId", String.valueOf(request.correlationId())),
                    Map.entry("intentId", request.intentId().toString()),
                    Map.entry("priority",
                            request.priority() == null ? "NORMAL" : request.priority()),
                    Map.entry("deepLink",
                            request.deepLink() == null ? "" : request.deepLink()),
                    Map.entry("payload",
                            request.payload() == null ? Map.of() : request.payload()),
                    Map.entry("occurredAt", java.time.Instant.now().toString())));
        } catch (Exception e) {
            throw new IllegalStateException("Webhook payload not serializable", e);
        }
    }

    /** Endpoint secret (hashed at rest) resolved by exact tenant+URL. */
    private String resolveSecret(UUID tenantId, String url) {
        List<String> secrets = jdbc.queryForList("""
                SELECT secret_hash FROM workflow_webhook_endpoints
                 WHERE tenant_id = ? AND url = ? AND status = 'ACTIVE'
                """, String.class, tenantId, url);
        return secrets.isEmpty() ? "" : secrets.get(0);
    }

    static boolean isPrivateOrLocal(InetAddress address) {
        return address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isAnyLocalAddress()
                || address.isMulticastAddress();
    }

    public static String hmacSha256Hex(String secret, String message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"));
            byte[] digest = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16))
                        .append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA256 unavailable", e);
        }
    }
}
