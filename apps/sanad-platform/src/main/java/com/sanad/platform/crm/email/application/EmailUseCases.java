package com.sanad.platform.crm.email.application;

import com.sanad.platform.crm.email.domain.EmailAddress;
import com.sanad.platform.crm.email.domain.EmailLogPort;
import com.sanad.platform.crm.email.domain.EmailLogPort.EmailLogEntry;
import com.sanad.platform.crm.email.domain.EmailMessage;
import com.sanad.platform.crm.email.domain.EmailPort;
import com.sanad.platform.crm.email.domain.EmailSendResult;
import com.sanad.platform.crm.email.domain.EmailTemplatePort;
import com.sanad.platform.crm.email.infrastructure.EmailProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Email application service — orchestrates email sending, template rendering,
 * and logging for the CRM email bounded context.
 * <p>
 * Follows the same pattern as {@code CaseUseCases}: thin facade with
 * {@code @Transactional} on write methods, delegating to port interfaces.
 */
public class EmailUseCases {

    private static final Logger log = LoggerFactory.getLogger(EmailUseCases.class);

    private final EmailPort emailPort;
    private final EmailTemplatePort templatePort;
    private final EmailLogPort logPort;
    private final EmailProperties properties;

    public EmailUseCases(EmailPort emailPort, EmailTemplatePort templatePort,
                         EmailLogPort logPort, EmailProperties properties) {
        this.emailPort = emailPort;
        this.templatePort = templatePort;
        this.logPort = logPort;
        this.properties = properties;
    }

    /**
     * Send an email with optional template rendering.
     *
     * @param tenantId the tenant scope
     * @param userId   the user sending the email
     * @param message  the email message
     * @return the send result
     */
    @Transactional
    public EmailSendResult send(UUID tenantId, UUID userId, EmailMessage message) {
        // Render template if specified
        EmailMessage resolvedMessage = message;
        if (message.templateName() != null && !message.templateName().isBlank()) {
            resolvedMessage = renderTemplate(message);
        }

        // Create log entry before sending
        EmailLogEntry logEntry = new EmailLogEntry(
                null, tenantId, userId,
                resolvedMessage.from().value(),
                resolvedMessage.to().get(0).value(),
                resolvedMessage.subject(),
                "PENDING",
                emailPort.providerName(),
                null,
                resolvedMessage.relatedEntityType(),
                resolvedMessage.relatedEntityId(),
                resolvedMessage.templateName(),
                null, null, null, null, null,
                Instant.now()
        );
        EmailLogEntry createdLog = logPort.create(tenantId, logEntry);

        // Send via provider
        EmailSendResult result = emailPort.send(tenantId, resolvedMessage);

        // Update log with result
        EmailLogEntry updatedLog = new EmailLogEntry(
                createdLog.id(), createdLog.tenantId(), createdLog.userId(),
                createdLog.fromAddress(), createdLog.toAddress(), createdLog.subject(),
                result.isSuccess() ? "SENT" : "FAILED",
                result.provider(), result.providerMessageId(),
                createdLog.relatedEntityType(), createdLog.relatedEntityId(),
                createdLog.templateName(),
                result.sentAt(), null, null, null,
                result.errorMessage(),
                createdLog.createdAt()
        );
        logPort.update(tenantId, updatedLog);

        log.info("Email {} to {} via {}: logId={}",
                result.isSuccess() ? "sent" : "failed",
                resolvedMessage.to().get(0).value(),
                result.provider(),
                createdLog.id());

        return new EmailSendResult(
                createdLog.id(),
                result.providerMessageId(),
                result.status(),
                result.provider(),
                result.sentAt(),
                result.errorMessage()
        );
    }

    /**
     * Render a template and return the fully resolved message.
     */
    private EmailMessage renderTemplate(EmailMessage message) {
        String locale = message.metadata().getOrDefault("locale", "en");
        EmailTemplatePort.RenderedEmail rendered = templatePort.render(
                message.templateName(),
                message.templateVariables(),
                locale
        );

        return EmailMessage.builder()
                .from(message.from())
                .to(message.to().toArray(EmailAddress[]::new))
                .cc(message.cc().toArray(EmailAddress[]::new))
                .bcc(message.bcc().toArray(EmailAddress[]::new))
                .subject(rendered.subject())
                .textBody(rendered.textBody())
                .htmlBody(rendered.htmlBody())
                .tenantId(message.tenantId())
                .relatedEntityType(message.relatedEntityType())
                .relatedEntityId(message.relatedEntityId())
                .metadata(message.metadata())
                .build();
    }

    /**
     * Find email log entries by related entity.
     */
    public List<EmailLogEntry> findByRelatedEntity(UUID tenantId, String entityType, String entityId) {
        return logPort.findByRelatedEntity(tenantId, entityType, entityId);
    }

    /**
     * List all email log entries for a tenant.
     */
    public List<EmailLogEntry> listLogs(UUID tenantId, int limit) {
        return logPort.findAll(tenantId, limit);
    }

    /**
     * Find an email log entry by ID with tenant scope.
     */
    public EmailLogEntry findById(UUID tenantId, UUID logId) {
        return logPort.findById(tenantId, logId);
    }

    /**
     * Find an email log entry by ID without tenant scope.
     * Used by tracking endpoints.
     */
    public EmailLogEntry findByLogId(UUID logId) {
        return logPort.findByLogId(logId);
    }

    /**
     * Record an email open event.
     */
    @Transactional
    public void recordOpen(UUID tenantId, UUID logId) {
        logPort.recordOpen(tenantId, logId, Instant.now());
    }

    /**
     * Record a click event.
     */
    @Transactional
    public void recordClick(UUID tenantId, UUID logId, String url) {
        logPort.recordClick(tenantId, logId, url, Instant.now());
    }

    /**
     * Check if the email provider is available.
     */
    public boolean isProviderAvailable() {
        return emailPort.isAvailable();
    }

    /**
     * Get the current provider name.
     */
    public String getProviderName() {
        return emailPort.providerName();
    }
}
