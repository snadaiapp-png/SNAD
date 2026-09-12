package com.sanad.platform.workflow.notification;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * PUSH channel provider (GATE R2.8). Provider-neutral contract implemented
 * for the Expo push transport over the EXISTING mobile device registry
 * ({@code mobile_device_registry}: per-user device rows with push_token,
 * is_active flag = revocation boundary; invalid-token rows are deactivated).
 *
 * <p>Reality classes (contract SECTION 10): PUSH_IMPLEMENTED=YES,
 * PUSH_TESTED=YES (sandbox/mock transport contract); PUSH_LIVE requires
 * approved provider credentials — absent credentials are reported as
 * BLOCKED_EXTERNAL_DEPENDENCY and deliveries fail closed (CHANNEL_DISABLED),
 * never silently dropped, never fake LIVE.</p>
 */
@Component
public class PushNotificationProvider implements WorkflowChannelProvider {

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(PushNotificationProvider.class);

    private final JdbcTemplate jdbc;
    private final boolean pushEnabled;
    private final String expoPushUrl;
    private final Duration timeout;
    private final HttpClient http;

    public PushNotificationProvider(
            JdbcTemplate jdbc,
            @Value("${sanad.workflow.push.enabled:false}") boolean pushEnabled,
            @Value("${sanad.workflow.push.expo-url:https://exp.host/--/api/v2/push/send}")
            String expoPushUrl,
            @Value("${sanad.workflow.push.timeout-seconds:10}") long timeoutSeconds) {
        this.jdbc = jdbc;
        this.pushEnabled = pushEnabled;
        this.expoPushUrl = expoPushUrl;
        this.timeout = Duration.ofSeconds(timeoutSeconds);
        this.http = HttpClient.newBuilder().connectTimeout(timeout).build();
    }

    @Override
    public WorkflowChannel channel() {
        return WorkflowChannel.PUSH;
    }

    @Override
    public String providerType() {
        return "expo-push";
    }

    @Override
    public WorkflowDeliveryResult deliver(WorkflowDeliveryRequest request) {
        if (!pushEnabled) {
            return WorkflowDeliveryResult.terminalFailure(
                    WorkflowDeliveryResult.FC_DISABLED);
        }
        if (request.recipientUserId() == null) {
            return WorkflowDeliveryResult.terminalFailure(
                    WorkflowDeliveryResult.FC_INVALID_RECIPIENT);
        }
        List<String> tokens = jdbc.queryForList("""
                SELECT push_token FROM mobile_device_registry
                 WHERE tenant_id = ? AND user_id = ? AND is_active = TRUE
                   AND push_token IS NOT NULL AND push_token <> ''
                """, String.class, request.tenantId(), request.recipientUserId());
        if (tokens.isEmpty()) {
            return WorkflowDeliveryResult.terminalFailure(
                    WorkflowDeliveryResult.FC_INVALID_RECIPIENT);
        }
        boolean anyAccepted = false;
        for (String token : tokens) {
            WorkflowDeliveryResult result = sendOne(request, token);
            if (result.delivered()) {
                anyAccepted = true;
            } else if (WorkflowDeliveryResult.FC_INVALID_RECIPIENT
                    .equals(result.failureCategory())) {
                // Registry hygiene: a device token the provider rejected is
                // deactivated (revocation / invalid token handling).
                jdbc.update("""
                        UPDATE mobile_device_registry
                           SET is_active = FALSE, updated_at = NOW()
                         WHERE tenant_id = ? AND user_id = ? AND push_token = ?
                        """, request.tenantId(), request.recipientUserId(), token);
            }
        }
        return anyAccepted
                ? WorkflowDeliveryResult.success("expo:" + request.intentId())
                : WorkflowDeliveryResult.retryableFailure(WorkflowDeliveryResult.FC_TRANSIENT);
    }

    private WorkflowDeliveryResult sendOne(WorkflowDeliveryRequest request, String token) {
        try {
            Map<String, Object> body = Map.of(
                    "to", token,
                    "title", request.title() == null ? request.eventType() : request.title(),
                    "body", request.body() == null ? "" : request.body(),
                    "data", Map.of(
                            "eventType", request.eventType(),
                            "intentId", request.intentId().toString(),
                            "deepLink", request.deepLink() == null ? "" : request.deepLink()),
                    "sound", "default");
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(expoPushUrl))
                    .timeout(timeout)
                    .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .header("Accept", MediaType.APPLICATION_JSON_VALUE)
                    .POST(HttpRequest.BodyPublishers.ofString(
                            new com.fasterxml.jackson.databind.ObjectMapper()
                                    .writeValueAsString(body)))
                    .build();
            HttpResponse<String> response =
                    http.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return WorkflowDeliveryResult.success("expo:" + request.intentId());
            }
            if (response.statusCode() == 404 || response.statusCode() == 400) {
                return WorkflowDeliveryResult.terminalFailure(
                        WorkflowDeliveryResult.FC_INVALID_RECIPIENT);
            }
            return WorkflowDeliveryResult.retryableFailure(
                    WorkflowDeliveryResult.FC_TRANSIENT);
        } catch (java.net.http.HttpTimeoutException e) {
            return WorkflowDeliveryResult.retryableFailure(
                    WorkflowDeliveryResult.FC_TIMEOUT);
        } catch (Exception e) {
            log.warn("PUSH intent {} delivery error: {}", request.intentId(), e.getMessage());
            return WorkflowDeliveryResult.retryableFailure(
                    WorkflowDeliveryResult.FC_TRANSIENT);
        }
    }
}
