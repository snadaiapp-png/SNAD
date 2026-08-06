package com.sanad.platform.crm.email.infrastructure;

import com.sanad.platform.crm.email.domain.EmailMessage;
import com.sanad.platform.crm.email.domain.EmailPort;
import com.sanad.platform.crm.email.domain.EmailSendResult;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * SMTP adapter for CRM email delivery.
 * <p>
 * Uses Spring's JavaMailSender for SMTP transport.
 * Activated when {@code snad.crm.email.provider=smtp}.
 */
@Component
@ConditionalOnProperty(prefix = "snad.crm.email", name = "provider", havingValue = "smtp")
public class SmtpEmailAdapter implements EmailPort {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailAdapter.class);

    private final JavaMailSender mailSender;
    private final EmailProperties properties;

    public SmtpEmailAdapter(JavaMailSender mailSender, EmailProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    @Override
    public EmailSendResult send(UUID tenantId, EmailMessage message) {
        String fromAddress = properties.getFromAddress();
        if (fromAddress == null || fromAddress.isBlank()) {
            throw new IllegalStateException("CRM email sender is not configured");
        }

        String toAddresses = message.to().stream()
                .map(a -> a.value())
                .collect(Collectors.joining(","));

        log.info("Sending email via SMTP: tenant={}, to={}, subject={}", tenantId, toAddresses, message.subject());

        MimeMessage mimeMessage = mailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(
                    mimeMessage,
                    true,
                    StandardCharsets.UTF_8.name());
            helper.setFrom(fromAddress.trim());
            helper.setTo(message.to().stream().map(a -> a.value()).toArray(String[]::new));
            if (!message.cc().isEmpty()) {
                helper.setCc(message.cc().stream().map(a -> a.value()).toArray(String[]::new));
            }
            if (!message.bcc().isEmpty()) {
                helper.setBcc(message.bcc().stream().map(a -> a.value()).toArray(String[]::new));
            }
            helper.setSubject(message.subject());
            helper.setText(message.textBody(), message.htmlBody());
            mailSender.send(mimeMessage);

            log.info("Email sent via SMTP: to={}", toAddresses);
            return EmailSendResult.success(null, "smtp-local-" + UUID.randomUUID(), "smtp");

        } catch (MessagingException | MailException exception) {
            log.error("SMTP email delivery failed", exception);
            return EmailSendResult.failure(null, "smtp", exception.getMessage());
        }
    }

    @Override
    public boolean isAvailable() {
        return true; // JavaMailSender is auto-configured by Spring Boot
    }

    @Override
    public String providerName() {
        return "smtp";
    }
}
