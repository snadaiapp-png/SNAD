package com.sanad.platform.crm.platform;

import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "sanad.crm.platform.notifications", name = "enabled", havingValue = "true")
public class CrmEmailNotificationWorker {

    private static final Logger log = LoggerFactory.getLogger(CrmEmailNotificationWorker.class);

    private final JdbcTemplate jdbc;
    private final JavaMailSender mailSender;
    private final CrmPlatformProperties properties;

    public CrmEmailNotificationWorker(
            JdbcTemplate jdbc,
            JavaMailSender mailSender,
            CrmPlatformProperties properties) {
        this.jdbc = jdbc;
        this.mailSender = mailSender;
        this.properties = properties;
    }

    @RabbitListener(queues = CrmPlatformConfiguration.NOTIFICATION_QUEUE)
    public void deliver(Map<String, Object> event) {
        UUID id = UUID.fromString(String.valueOf(event.get("notificationId")));
        Map<String, Object> message = jdbc.queryForMap("""
            SELECT channel, recipient, subject, body, status, attempts
              FROM crm_platform.notification_message
             WHERE id=?
            """, id);

        String status = String.valueOf(message.get("status"));
        if ("SENT".equals(status) || "DELIVERED".equals(status)) {
            return;
        }
        if (!"EMAIL".equals(String.valueOf(message.get("channel")))) {
            markFailure(id, "Notification provider is not configured for this channel");
            return;
        }

        try {
            SimpleMailMessage email = new SimpleMailMessage();
            email.setFrom(properties.getNotifications().getFrom());
            email.setTo(String.valueOf(message.get("recipient")));
            email.setSubject(nullableText(message.get("subject")));
            email.setText(nullableText(message.get("body")));
            mailSender.send(email);
            jdbc.update("""
                UPDATE crm_platform.notification_message
                   SET status='SENT', sent_at=now(), last_error=NULL
                 WHERE id=?
                """, id);
        } catch (Exception failure) {
            markFailure(id, failure.getMessage());
            log.warn("CRM email notification {} failed", id, failure);
            throw failure;
        }
    }

    private void markFailure(UUID id, String error) {
        jdbc.update("""
            UPDATE crm_platform.notification_message
               SET status=CASE WHEN attempts >= 10 THEN 'DEAD' ELSE 'FAILED' END,
                   available_at=now() + interval '1 minute', last_error=?
             WHERE id=?
            """, truncate(error), id);
    }

    private String nullableText(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String truncate(String value) {
        if (value == null) {
            return "notification delivery failed";
        }
        return value.length() <= 2000 ? value : value.substring(0, 2000);
    }
}
