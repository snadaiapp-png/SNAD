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

/** Provider-neutral HTTPS adapter for the external email delivery service. */
@Component
@ConditionalOnProperty(prefix = "snad.security.notifications", name = "provider", havingValue = "http")
public class HttpSecurityNotificationGateway implements SecurityNotificationGateway {
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final SecurityNotificationProperties properties;
    private final ObjectMapper objectMapper;

    public HttpSecurityNotificationGateway(
            SecurityNotificationProperties properties,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public void deliver(SecurityMessage message) {
        URI endpoint = endpoint();
        HttpRequest.Builder builder = HttpRequest.newBuilder(endpoint)
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload(message)));

        if (!properties.getBearerToken().isBlank()) {
            builder.header("Authorization", "Bearer " + properties.getBearerToken());
        }

        try {
            HttpResponse<Void> response = client.send(builder.build(), HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Security notification provider returned " + response.statusCode());
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Security notification delivery interrupted", exception);
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Security notification delivery failed", exception);
        }
    }

    private URI endpoint() {
        if (properties.getEndpoint() == null || properties.getEndpoint().isBlank()) {
            throw new IllegalStateException("Security notification endpoint is not configured");
        }
        URI endpoint = URI.create(properties.getEndpoint());
        if (!"https".equalsIgnoreCase(endpoint.getScheme())) {
            throw new IllegalStateException("Security notification endpoint must use HTTPS");
        }
        return endpoint;
    }

    private String payload(SecurityMessage message) {
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("from", properties.getFromAddress());
        payload.put("destination", message.getDestination());
        payload.put("subject", message.getSubject());
        payload.put("textBody", message.getTextBody());
        payload.put("htmlBody", message.getHtmlBody());
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize security notification", exception);
        }
    }
}
