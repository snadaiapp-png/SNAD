package com.sanad.platform.workflow.notification;

import com.sanad.platform.crm.email.domain.EmailAddress;
import com.sanad.platform.crm.email.domain.EmailMessage;
import com.sanad.platform.crm.email.domain.EmailPort;
import com.sanad.platform.crm.email.infrastructure.EmailProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * EMAIL channel provider (GATE R2.7). Delegates to the EXISTING approved
 * platform email stack ({@link EmailPort} — the CRM email bounded context
 * with Resend/SMTP/HTTP-proxy/local adapters). No new email transport is
 * created; no provider secrets are handled here (the port owns them).
 *
 * <p>Reality classes (contract SECTION 10): the engineering contract is
 * IMPLEMENTED and TESTED through the port; LIVE depends on the deployed
 * provider configuration. Without approved production credentials the
 * channel must be reported EMAIL_LIVE=BLOCKED_EXTERNAL_DEPENDENCY, never
 * fake LIVE.</p>
 */
@Component
public class EmailNotificationProvider implements WorkflowChannelProvider {

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(EmailNotificationProvider.class);

    /**
     * ObjectProvider (not direct injection): in profiles where the platform
     * has no configured email transport bean, this provider still registers
     * and deliveries fail CLOSED on the intent (CONFIG_ERROR) — startup is
     * never broken and no delivery is silently dropped.
     */
    private final ObjectProvider<EmailPort> emailPort;
    private final EmailProperties emailProperties;

    public EmailNotificationProvider(ObjectProvider<EmailPort> emailPort,
                                     EmailProperties emailProperties) {
        this.emailPort = emailPort;
        this.emailProperties = emailProperties;
    }

    @Override
    public WorkflowChannel channel() {
        return WorkflowChannel.EMAIL;
    }

    @Override
    public String providerType() {
        EmailPort port = emailPort.getIfAvailable();
        return "platform-email:" + (port == null ? "unconfigured" : port.providerName());
    }

    @Override
    public WorkflowDeliveryResult deliver(WorkflowDeliveryRequest request) {
        if (request.recipientAddress() == null || request.recipientAddress().isBlank()) {
            return WorkflowDeliveryResult.terminalFailure(
                    WorkflowDeliveryResult.FC_INVALID_RECIPIENT);
        }
        String from = emailProperties.getFromAddress();
        if (from == null || from.isBlank()) {
            return WorkflowDeliveryResult.terminalFailure(
                    WorkflowDeliveryResult.FC_CONFIG_ERROR);
        }
        EmailPort port = emailPort.getIfAvailable();
        if (port == null) {
            log.warn("EMAIL intent {} failed closed: no platform EmailPort bean "
                    + "configured for this profile", request.intentId());
            return WorkflowDeliveryResult.terminalFailure(
                    WorkflowDeliveryResult.FC_CONFIG_ERROR);
        }
        try {
            var result = port.send(request.tenantId(), EmailMessage.builder()
                    .from(EmailAddress.of(from))
                    .to(EmailAddress.of(request.recipientAddress()))
                    .subject(request.title() == null ? request.eventType() : request.title())
                    .textBody(request.body() == null ? "" : request.body())
                    .templateName(request.templateKey() == null ? "" : request.templateKey())
                    .tenantId(request.tenantId().toString())
                    .relatedEntityType("workflow_notification_intent")
                    .relatedEntityId(request.intentId().toString())
                    .metadata(Map.of(
                            "eventType", request.eventType(),
                            "workflowInstanceId", String.valueOf(request.workflowInstanceId()),
                            "intentId", request.intentId().toString()))
                    .build());
            if (result.isSuccess()) {
                return WorkflowDeliveryResult.success(result.providerMessageId());
            }
            return WorkflowDeliveryResult.retryableFailure(
                    WorkflowDeliveryResult.FC_TRANSIENT);
        } catch (IllegalArgumentException addressError) {
            return WorkflowDeliveryResult.terminalFailure(
                    WorkflowDeliveryResult.FC_INVALID_RECIPIENT);
        }
    }
}
