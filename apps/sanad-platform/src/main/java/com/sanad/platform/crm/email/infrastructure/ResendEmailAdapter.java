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
 * Resend API adapter for CRM email delivery.
 * <p>
 * Calls Resend's REST API directly from the backend.
 * Activated when {@code snad.crm.email.provider=resend}.
 */
@Component
@ConditionalOnProperty(prefix = "snad.crm.email", name = "provider", havingValue = "resend")
public class ResendEmailAdapter implements EmailPort {

    private static final Logger log = LoggerFactory.getLogger(ResendEmailAdapter.class);
    private static final String RESEND_API_URL = "https://api.resend.com/emails";

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final EmailProperties properties;
    private final ObjectMapper objectMapper;

    public ResendEmailAdapter(EmailProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public EmailSendResult send(UUID tenantId, EmailMessage message) {
        String apiKey = properties.getResendApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("Resend API key is not configured (RESEND_API_KEY)");
        }
        String fromAddress = properties.getFromAddress();
        if (fromAddress == null || fromAddress.isBlank()) {
            throw new IllegalStateException("CRM email sender is not configured (FROM_ADDRESS)");
        }

        String toAddresses = message.to().stream()
                .map(a -> a.value())
                .collect(Collectors.joining(","));

        log.info("Sending email via Resend: tenant={}, to={}, subject={}", tenantId, toAddresses, message.subject());

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
                log.error("Resend API returned {}: {}", status, response.body());
                return EmailSendResult.failure(null, "resend", "Resend API returned " + status);
            }

            // Extract message ID from response
            @SuppressWarnings("unchecked")
            Map<String, Object> body = objectMapper.readValue(response.body(), Map.class);
            String messageId = (String) body.get("id");

            log.info("Email sent via Resend: messageId={}", messageId);
            return EmailSendResult.success(null, messageId, "resend");

        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.error("Resend email delivery interrupted", exception);
            return EmailSendResult.failure(null, "resend", "Interrupted");
        } catch (Exception exception) {
            log.error("Resend email delivery failed", exception);
            return EmailSendResult.failure(null, "resend", exception.getMessage());
        }
    }

    @Override
    public boolean isAvailable() {
        String apiKey = properties.getResendApiKey();
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public String providerName() {
        return "resend";
    }

    private String payload(EmailMessage message, String fromAddress) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("from", fromAddress);
        payload.put("to", message.to().stream().map(a -> a.value()).collect(Collectors.toList()));
        if (!message.cc().isEmpty()) {
            payload.put("cc", message.cc().stream().map(a -> a.value()).collect(Collectors.toList()));
        }
        if (!message.bcc().isEmpty()) {
            payload.put("bcc", message.bcc().stream().map(a -> a.value()).collect(Collectors.toList()));
        }
        payload.put("subject", message.subject());
        if (message.htmlBody() != null && !message.htmlBody().isBlank()) {
            payload.put("html", message.htmlBody());
        } else if (message.textBody() != null && !message.textBody().isBlank()) {
            payload.put("text", message.textBody());
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize email payload", exception);
        }
    }
}
