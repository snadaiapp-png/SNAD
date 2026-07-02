package com.sanad.platform.crm.platform;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
@ConditionalOnProperty(prefix = "sanad.crm.platform.notifications", name = "enabled", havingValue = "true")
public class CrmNotificationOutboxDispatcher {

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final CrmPlatformProperties properties;

    public CrmNotificationOutboxDispatcher(
            JdbcTemplate jdbc,
            TransactionTemplate transactions,
            CrmPlatformProperties properties) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "5s")
    public void dispatchPendingNotifications() {
        transactions.executeWithoutResult(status -> {
            List<Map<String, Object>> messages = jdbc.queryForList("""
                SELECT id, tenant_id, channel, recipient, locale, subject, body,
                       template_key, variables, idempotency_key
                  FROM crm_platform.notification_message
                 WHERE status IN ('PENDING','FAILED')
                   AND available_at <= now()
                 ORDER BY created_at
                 LIMIT ?
                 FOR UPDATE SKIP LOCKED
                """, properties.getNotifications().getBatchSize());

            for (Map<String, Object> message : messages) {
                UUID id = (UUID) message.get("id");
                UUID tenantId = (UUID) message.get("tenant_id");
                int inserted = jdbc.update("""
                    INSERT INTO crm_platform.event_outbox
                        (tenant_id, aggregate_type, aggregate_id, event_type,
                         routing_key, payload, headers, correlation_id)
                    SELECT ?, 'NOTIFICATION', ?, 'NotificationDeliveryRequested',
                           'crm.notification.delivery.requested',
                           jsonb_build_object(
                               'notificationId', ?,
                               'channel', ?,
                               'recipient', ?,
                               'locale', ?,
                               'subject', ?,
                               'body', ?,
                               'templateKey', ?,
                               'variables', ?::jsonb,
                               'idempotencyKey', ?),
                           '{}'::jsonb, ?
                     WHERE NOT EXISTS (
                         SELECT 1
                           FROM crm_platform.event_outbox existing
                          WHERE existing.tenant_id=?
                            AND existing.aggregate_type='NOTIFICATION'
                            AND existing.aggregate_id=?
                            AND existing.event_type='NotificationDeliveryRequested'
                            AND existing.status IN ('PENDING','PROCESSING','PUBLISHED')
                     )
                    """,
                    tenantId, id, id,
                    message.get("channel"), message.get("recipient"), message.get("locale"),
                    message.get("subject"), message.get("body"), message.get("template_key"),
                    String.valueOf(message.get("variables")), message.get("idempotency_key"), id,
                    tenantId, id);
                if (inserted == 1) {
                    jdbc.update("""
                        UPDATE crm_platform.notification_message
                           SET status='PROCESSING', attempts=attempts+1,
                               available_at=now() + interval '5 minutes'
                         WHERE id=?
                        """, id);
                }
            }
        });
    }
}
