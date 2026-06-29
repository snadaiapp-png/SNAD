package com.sanad.platform.security.notification;

import com.sanad.platform.security.notification.SecurityMessage;
import com.sanad.platform.security.notification.SecurityNotificationGateway;
import com.sanad.platform.security.notification.SecurityNotificationProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * HTTP API adapter for account-recovery notifications via Resend.
 *
 * Uses Resend's REST API (HTTPS port 443) instead of SMTP (port 587/465)
 * because Render free tier blocks outbound SMTP connections.
 *
 * Configuration:
 *   snad.security.notifications.provider=resend-api
 *   snad.security.notifications.from-address=onboarding@resend.dev
 *   RESEND_API_KEY env var must be set
 */
@Component
@ConditionalOnProperty(
        prefix = "snad.security.notifications",
        name = "provider",
        havingValue = "resend-api"
)
public class ResendApiSecurityNotificationGateway implements SecurityNotificationGateway {

    private static final String RESEND_API_URL = "https://api.resend.com/emails";

    private final SecurityNotificationProperties properties;
    private final RestTemplate restTemplate;

    public ResendApiSecurityNotificationGateway(SecurityNotificationProperties properties) {
        this.properties = properties;
        this.restTemplate = new RestTemplate();
    }

    @Override
    public void deliver(SecurityMessage message) {
        String fromAddress = properties.getFromAddress();
        if (fromAddress == null || fromAddress.isBlank()) {
            throw new IllegalStateException("Security notification sender is not configured");
        }
        if (message.getDestination() == null || message.getDestination().isBlank()) {
            throw new IllegalStateException("Security notification destination is missing");
        }

        String apiKey = System.getenv("RESEND_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("RESEND_API_KEY environment variable is not set");
        }

        Map<String, Object> emailPayload = new HashMap<>();
        emailPayload.put("from", fromAddress.trim());
        emailPayload.put("to", List.of(message.getDestination().trim()));
        emailPayload.put("subject", message.getSubject());
        emailPayload.put("html", message.getHtmlBody());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(emailPayload, headers);

        try {
            restTemplate.postForEntity(RESEND_API_URL, request, String.class);
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Resend API notification delivery failed", exception);
        }
    }
}
