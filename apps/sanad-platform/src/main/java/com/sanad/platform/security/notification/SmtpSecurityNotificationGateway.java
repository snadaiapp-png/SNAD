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

        // Try SMTP first; if it fails (e.g. Render free tier blocks SMTP),
        // fall back to Resend HTTP API if RESEND_API_KEY is set.
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    mimeMessage,
                    true,
                    StandardCharsets.UTF_8.name());
            helper.setFrom(fromAddress.trim());
            helper.setTo(message.getDestination().trim());
            helper.setSubject(message.getSubject());
            helper.setText(message.getTextBody(), message.getHtmlBody());
            mailSender.send(mimeMessage);
        } catch (MessagingException | MailException smtpException) {
            // Check if Resend API fallback is available
            String resendApiKey = System.getenv("RESEND_API_KEY");
            if (resendApiKey != null && !resendApiKey.isBlank()) {
                deliverViaResendApi(fromAddress.trim(), message, resendApiKey);
            } else {
                throw new IllegalStateException("SMTP security notification delivery failed", smtpException);
            }
        }
    }

    /**
     * Fallback: send email via Resend HTTP API (HTTPS port 443).
     * Used when SMTP ports are blocked (e.g. Render free tier).
     */
    private void deliverViaResendApi(String fromAddress, SecurityMessage message, String apiKey) {
        try {
            String jsonPayload = "{\"from\":\"" + fromAddress + "\","
                    + "\"to\":[\"" + message.getDestination().trim() + "\"],"
                    + "\"subject\":\"" + escapeJson(message.getSubject()) + "\","
                    + "\"html\":\"" + escapeJson(message.getHtmlBody()) + "\"}";

            java.net.URL url = new java.net.URL("https://api.resend.com/emails");
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(15000);

            try (java.io.OutputStream os = conn.getOutputStream()) {
                os.write(jsonPayload.getBytes(StandardCharsets.UTF_8));
            }

            int responseCode = conn.getResponseCode();
            conn.disconnect();

            if (responseCode < 200 || responseCode >= 300) {
                throw new IllegalStateException(
                        "Resend API fallback failed with status " + responseCode);
            }
        } catch (java.io.IOException ioException) {
            throw new IllegalStateException(
                    "Resend API fallback failed", ioException);
        }
    }

    private static String escapeJson(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\n", "\\n")
                   .replace("\r", "\\r")
                   .replace("\t", "\\t");
    }
}
