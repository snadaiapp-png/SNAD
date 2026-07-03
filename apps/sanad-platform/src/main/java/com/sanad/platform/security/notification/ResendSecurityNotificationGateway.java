package com.sanad.platform.security.notification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Direct Resend API adapter for account-recovery and credential-change notifications.
 *
 * <p>Calls Resend's REST API directly from the backend, eliminating the need
 * for the Vercel email-proxy middleware. This simplifies the architecture:
 *   Backend → Resend API → User inbox
 * Instead of:
 *   Backend → Vercel email-proxy → Resend API → User inbox
 *
 * <p>Requires the following environment variables on Render:
 * <ul>
 *   <li>{@code SECURITY_NOTIFICATION_PROVIDER=resend}</li>
 *   <li>{@code SECURITY_NOTIFICATION_RESEND_API_KEY=re_xxxxxxxx}</li>
 *   <li>{@code SECURITY_NOTIFICATION_FROM=Sender Name <noreply@domain.com>}</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(prefix = "snad.security.notifications", name = "provider", havingValue = "resend")
public class ResendSecurityNotificationGateway implements SecurityNotificationGateway {
    private static final String RESEND_API_URL = "https://api.resend.com/emails";

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final SecurityNotificationProperties properties;
    private final ObjectMapper objectMapper;

    public ResendSecurityNotificationGateway(
            SecurityNotificationProperties properties,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public void deliver(SecurityMessage message) {
        String apiKey = properties.getResendApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("Resend API key is not configured (SECURITY_NOTIFICATION_RESEND_API_KEY)");
        }
        String fromAddress = properties.getFromAddress();
        if (fromAddress == null || fromAddress.isBlank()) {
            throw new IllegalStateException("Security notification sender is not configured (SECURITY_NOTIFICATION_FROM)");
        }
        if (message.getDestination() == null || message.getDestination().isBlank()) {
            throw new IllegalStateException("Security notification destination is missing");
        }

        HttpRequest request = HttpRequest.newBuilder(URI.create(RESEND_API_URL))
                .timeout(Duration.ofSeconds(20))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload(message, fromAddress)))
                .build();

        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (status < 200 || status >= 300) {
                throw new IllegalStateException("Resend API returned " + status + ": " + response.body());
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Resend notification delivery interrupted", exception);
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Resend notification delivery failed", exception);
        }
    }

    private String payload(SecurityMessage message, String fromAddress) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("from", fromAddress);
        payload.put("to", new String[]{message.getDestination()});
        payload.put("subject", message.getSubject());
        // Prefer HTML body; fall back to text body if HTML is empty
        if (message.getHtmlBody() != null && !message.getHtmlBody().isBlank()) {
            payload.put("html", message.getHtmlBody());
        } else if (message.getTextBody() != null && !message.getTextBody().isBlank()) {
            payload.put("text", message.getTextBody());
        } else {
            throw new IllegalStateException("Security notification has no body content");
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize security notification", exception);
        }
    }
}
