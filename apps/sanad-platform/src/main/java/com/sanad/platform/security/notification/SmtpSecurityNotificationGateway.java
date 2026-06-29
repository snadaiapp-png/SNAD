package com.sanad.platform.security.notification;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * SMTP adapter for account-recovery and credential-change notifications.
 *
 * Credentials are supplied exclusively through Spring Mail runtime
 * configuration. No mailbox password or provider secret is stored here.
 */
@Component
@ConditionalOnProperty(
        prefix = "snad.security.notifications",
        name = "provider",
        havingValue = "smtp"
)
public class SmtpSecurityNotificationGateway implements SecurityNotificationGateway {
    private final JavaMailSender mailSender;
    private final SecurityNotificationProperties properties;

    public SmtpSecurityNotificationGateway(
            JavaMailSender mailSender,
            SecurityNotificationProperties properties
    ) {
        this.mailSender = mailSender;
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

        MimeMessage mimeMessage = mailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(
                    mimeMessage,
                    true,
                    StandardCharsets.UTF_8.name());
            helper.setFrom(fromAddress.trim());
            helper.setTo(message.getDestination().trim());
            helper.setSubject(message.getSubject());
            helper.setText(message.getTextBody(), message.getHtmlBody());
            mailSender.send(mimeMessage);
        } catch (MessagingException | MailException exception) {
            throw new IllegalStateException("SMTP security notification delivery failed", exception);
        }
    }
}
