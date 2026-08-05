package com.sanad.platform.crm.email.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanad.platform.crm.email.domain.EmailMessage;
import com.sanad.platform.crm.email.domain.EmailPort;
import com.sanad.platform.crm.email.domain.EmailSendResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * HTTP proxy adapter for CRM email delivery.
 * <p>
 * Calls the Vercel email-proxy endpoint which forwards to Resend.
 * Activated when {@code snad.crm.email.provider=http-proxy}.
 * <p>
 * Flow: Backend → Vercel email-proxy → Resend API → User inbox
 */
@Component
@ConditionalOnProperty(prefix = "snad.crm.email", name = "provider", havingValue = "http-proxy")
public class HttpProxyEmailAdapter implements EmailPort {

    private static final Logger log = LoggerFactory.getLogger(HttpProxyEmailAdapter.class);

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final EmailProperties properties;
    private final ObjectMapper objectMapper;

    public HttpProxyEmailAdapter(EmailProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public EmailSendResult send(UUID tenantId, EmailMessage message) {
        String endpoint = properties.getProxyEndpoint();
        if (endpoint == null || endpoint.isBlank()) {
            throw new IllegalStateException("HTTP proxy endpoint is not configured");
        }
        String bearerToken = properties.getProxyBearerToken();
        if (bearerToken == null || bearerToken.isBlank()) {
            throw new IllegalStateException("HTTP proxy bearer token is not configured");
        }

        String toAddresses = message.to().stream()
                .map(a -> a.value())
                .collect(Collectors.joining(","));

        log.info("Sending email via HTTP proxy: tenant={}, to={}, subject={}", tenantId, toAddresses, message.subject());

        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(Duration.ofSeconds(20))
                .header("Authorization", "Bearer " + bearerToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload(message)))
                .build();

        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (status < 200 || status >= 300) {
                log.error("HTTP proxy returned {}: {}", status, response.body());
                return EmailSendResult.failure(null, "http-proxy", "Proxy returned " + status);
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> body = objectMapper.readValue(response.body(), Map.class);
            String messageId = (String) body.get("id");

            log.info("Email sent via HTTP proxy: messageId={}", messageId);
            return EmailSendResult.success(null, messageId, "http-proxy");

        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.error("HTTP proxy email delivery interrupted", exception);
            return EmailSendResult.failure(null, "http-proxy", "Interrupted");
        } catch (Exception exception) {
            log.error("HTTP proxy email delivery failed", exception);
            return EmailSendResult.failure(null, "http-proxy", exception.getMessage());
        }
    }

    @Override
    public boolean isAvailable() {
        String endpoint = properties.getProxyEndpoint();
        return endpoint != null && !endpoint.isBlank();
    }

    @Override
    public String providerName() {
        return "http-proxy";
    }

    private String payload(EmailMessage message) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("destination", message.to().get(0).value());
        payload.put("subject", message.subject());
        if (message.htmlBody() != null && !message.htmlBody().isBlank()) {
            payload.put("htmlBody", message.htmlBody());
        } else if (message.textBody() != null && !message.textBody().isBlank()) {
            payload.put("htmlBody", "<p>" + message.textBody() + "</p>");
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize email payload", exception);
        }
    }
}
