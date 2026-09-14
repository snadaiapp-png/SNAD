package com.sanad.platform.workflow.notification;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/**
 * WHATSAPP channel provider (GATE R2.9). Recipients resolve EXCLUSIVELY
 * from CRM verified communication methods via
 * {@link WorkflowExternalRecipientResolver} — Workflow never stores a
 * separate customer phone truth. The transport is provider-neutral:
 * a governed outbound template-message POST against the configured
 * provider endpoint (Meta WhatsApp Cloud API contract shape).
 *
 * <p>Reality classes (contract SECTION 10): WHATSAPP_IMPLEMENTED=YES,
 * WHATSAPP_TESTED=YES (sandbox/mock transport contract); LIVE requires an
 * approved provider + credentials + explicit production authorization —
 * absent authorization is reported BLOCKED_EXTERNAL_DEPENDENCY and
 * deliveries fail closed (CHANNEL_DISABLED). No real customer WhatsApp
 * message is ever sent without explicit production authorization.</p>
 */
@Component
public class WhatsAppNotificationProvider implements WorkflowChannelProvider {

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(WhatsAppNotificationProvider.class);

    private final WorkflowExternalRecipientResolver recipientResolver;
    private final boolean enabled;
    private final String providerEndpoint;
    private final String providerToken;
    private final Duration timeout;
    private final HttpClient http;

    public WhatsAppNotificationProvider(
            WorkflowExternalRecipientResolver recipientResolver,
            @Value("${sanad.workflow.whatsapp.enabled:false}") boolean enabled,
            @Value("${sanad.workflow.whatsapp.endpoint:}") String providerEndpoint,
            @Value("${sanad.workflow.whatsapp.token:}") String providerToken,
            @Value("${sanad.workflow.whatsapp.timeout-seconds:10}") long timeoutSeconds) {
        this.recipientResolver = recipientResolver;
        this.enabled = enabled;
        this.providerEndpoint = providerEndpoint;
        this.providerToken = providerToken;
        this.timeout = Duration.ofSeconds(timeoutSeconds);
        this.http = HttpClient.newBuilder().connectTimeout(timeout).build();
    }

    @Override
    public WorkflowChannel channel() {
        return WorkflowChannel.WHATSAPP;
    }

    @Override
    public String providerType() {
        return "whatsapp-cloud-contract";
    }

    @Override
    public WorkflowDeliveryResult deliver(WorkflowDeliveryRequest request) {
        if (!enabled) {
            return WorkflowDeliveryResult.terminalFailure(
                    WorkflowDeliveryResult.FC_DISABLED);
        }
        if (providerEndpoint == null || providerEndpoint.isBlank()
                || providerToken == null || providerToken.isBlank()) {
            return WorkflowDeliveryResult.terminalFailure(
                    WorkflowDeliveryResult.FC_CONFIG_ERROR);
        }
        if (request.recipientParticipantId() == null) {
            return WorkflowDeliveryResult.terminalFailure(
                    WorkflowDeliveryResult.FC_INVALID_RECIPIENT);
        }
        Optional<WorkflowExternalRecipientResolver.ResolvedRecipient> recipient =
                recipientResolver.resolve(request.tenantId(),
                        request.recipientParticipantId(), "WHATSAPP");
        if (recipient.isEmpty()) {
            return WorkflowDeliveryResult.terminalFailure(
                    WorkflowDeliveryResult.FC_INVALID_RECIPIENT);
        }
        return sendOne(request, recipient.get().normalizedValue());
    }

    private WorkflowDeliveryResult sendOne(WorkflowDeliveryRequest request, String to) {
        try {
            Map<String, Object> body = Map.of(
                    "messaging_product", "whatsapp",
                    "to", to,
                    "type", "text",
                    "text", Map.of(
                            "body", (request.title() == null
                                    ? request.eventType() : request.title())
                                    + (request.body() == null ? "" : "\n" + request.body())));
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(providerEndpoint))
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + providerToken)
                    .POST(HttpRequest.BodyPublishers.ofString(
                            new com.fasterxml.jackson.databind.ObjectMapper()
                                    .writeValueAsString(body)))
                    .build();
            HttpResponse<String> response =
                    http.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return WorkflowDeliveryResult.success("wa:" + request.intentId());
            }
            if (response.statusCode() == 400 || response.statusCode() == 404) {
                return WorkflowDeliveryResult.terminalFailure(
                        WorkflowDeliveryResult.FC_INVALID_RECIPIENT);
            }
            return WorkflowDeliveryResult.retryableFailure(
                    WorkflowDeliveryResult.FC_TRANSIENT);
        } catch (java.net.http.HttpTimeoutException e) {
            return WorkflowDeliveryResult.retryableFailure(
                    WorkflowDeliveryResult.FC_TIMEOUT);
        } catch (Exception e) {
            log.warn("WHATSAPP intent {} delivery error: {}",
                    request.intentId(), e.getMessage());
            return WorkflowDeliveryResult.retryableFailure(
                    WorkflowDeliveryResult.FC_TRANSIENT);
        }
    }
}
