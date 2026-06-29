package com.sanad.platform.security.notification;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * HTTP API adapter for account-recovery notifications via Resend.
 *
 * Uses Resend's REST API (HTTPS port 443) instead of SMTP (port 587/465)
 * because Render free tier blocks outbound SMTP connections.
 *
 * Activated by: snad.security.notifications.provider=resend-api
 * Requires env: RESEND_API_KEY
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

    public ResendApiSecurityNotificationGateway(SecurityNotificationProperties properties) {
        this.properties = properties;
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

        // Build JSON payload using StringBuilder (no external dependency)
        String jsonPayload = buildJsonPayload(
                fromAddress.trim(),
                message.getDestination().trim(),
                message.getSubject(),
                message.getHtmlBody()
        );

        try {
            URL url = new URL(RESEND_API_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(15000);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(jsonPayload.getBytes(StandardCharsets.UTF_8));
            }

            int responseCode = conn.getResponseCode();
            if (responseCode < 200 || responseCode >= 300) {
                String errorBody = new String(conn.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
                throw new IllegalStateException(
                        "Resend API failed with status " + responseCode + ": " + errorBody);
            }
            conn.disconnect();
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Resend API notification delivery failed", exception);
        }
    }

    private String buildJsonPayload(String from, String to, String subject, String html) {
        // Escape JSON strings
        String escapedSubject = escapeJson(subject);
        String escapedHtml = escapeJson(html);
        return "{\"from\":\"" + from + "\","
                + "\"to\":[\"" + to + "\"],"
                + "\"subject\":\"" + escapedSubject + "\","
                + "\"html\":\"" + escapedHtml + "\"}";
    }

    private String escapeJson(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\n", "\\n")
                   .replace("\r", "\\r")
                   .replace("\t", "\\t");
    }
}
