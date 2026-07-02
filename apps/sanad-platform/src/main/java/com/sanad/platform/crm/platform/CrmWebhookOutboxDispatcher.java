package com.sanad.platform.crm.platform;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(prefix = "sanad.crm.platform.webhooks", name = "enabled", havingValue = "true")
public class CrmWebhookOutboxDispatcher {

    private final JdbcTemplate jdbc;

    public CrmWebhookOutboxDispatcher(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Scheduled(fixedDelayString = "5s")
    @Transactional
    public void dispatch() {
        jdbc.update("""
            INSERT INTO crm_platform.event_outbox
                (tenant_id, aggregate_type, aggregate_id, event_type, routing_key, payload, headers)
            SELECT delivery.tenant_id,
                   'WEBHOOK_DELIVERY',
                   delivery.id,
                   'WebhookDeliveryRequested',
                   'crm.webhook.delivery.requested',
                   jsonb_build_object(
                       'deliveryId', delivery.id,
                       'subscriptionId', subscription.id,
                       'eventId', delivery.event_id,
                       'endpointUrl', subscription.endpoint_url,
                       'secretReference', subscription.secret_reference,
                       'timeoutSeconds', subscription.timeout_seconds,
                       'payload', delivery.payload),
                   jsonb_build_object('signatureRequired', true)
              FROM crm_platform.webhook_delivery delivery
              JOIN crm_platform.webhook_subscription subscription
                ON subscription.id=delivery.subscription_id
             WHERE delivery.status IN ('PENDING','FAILED')
               AND delivery.available_at <= now()
               AND subscription.active=true
               AND delivery.attempts < subscription.max_attempts
               AND NOT EXISTS (
                   SELECT 1 FROM crm_platform.event_outbox event
                    WHERE event.aggregate_type='WEBHOOK_DELIVERY'
                      AND event.aggregate_id=delivery.id
                      AND event.event_type='WebhookDeliveryRequested'
                      AND event.status IN ('PENDING','PROCESSING','PUBLISHED')
               )
             ORDER BY delivery.created_at
             LIMIT 100
            """);
        jdbc.update("""
            UPDATE crm_platform.webhook_delivery delivery
               SET status='PROCESSING', attempts=attempts+1,
                   available_at=now() + interval '5 minutes'
             WHERE delivery.status IN ('PENDING','FAILED')
               AND EXISTS (
                   SELECT 1 FROM crm_platform.event_outbox event
                    WHERE event.aggregate_type='WEBHOOK_DELIVERY'
                      AND event.aggregate_id=delivery.id
                      AND event.event_type='WebhookDeliveryRequested'
               )
            """);
    }
}
