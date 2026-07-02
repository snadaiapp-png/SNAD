package com.sanad.platform.crm.platform;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
@ConditionalOnProperty(prefix = "sanad.crm.platform.messaging", name = "enabled", havingValue = "true")
public class CrmOutboxDispatcher {

    private static final Logger log = LoggerFactory.getLogger(CrmOutboxDispatcher.class);
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final RabbitTemplate rabbit;
    private final ObjectMapper objectMapper;
    private final CrmPlatformProperties properties;

    public CrmOutboxDispatcher(
            JdbcTemplate jdbc,
            TransactionTemplate transactions,
            RabbitTemplate rabbit,
            ObjectMapper objectMapper,
            CrmPlatformProperties properties) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.rabbit = rabbit;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${sanad.crm.platform.messaging.poll-interval:2s}")
    public void dispatch() {
        List<OutboxEvent> claimed = transactions.execute(status -> {
            List<OutboxEvent> rows = jdbc.query("""
                SELECT id, tenant_id, routing_key, payload::text, headers::text,
                       correlation_id, causation_id, attempts
                  FROM crm_platform.event_outbox
                 WHERE status IN ('PENDING','FAILED')
                   AND available_at <= now()
                 ORDER BY created_at
                 LIMIT ?
                 FOR UPDATE SKIP LOCKED
                """, this::map, properties.getMessaging().getBatchSize());
            for (OutboxEvent row : rows) {
                jdbc.update("""
                    UPDATE crm_platform.event_outbox
                       SET status='PROCESSING', attempts=attempts+1,
                           available_at=now() + interval '5 minutes', updated_at=now()
                     WHERE id=?
                    """, row.id());
            }
            return rows;
        });

        if (claimed == null || claimed.isEmpty()) {
            return;
        }

        for (OutboxEvent event : claimed) {
            publish(event);
        }
    }

    @Scheduled(fixedDelayString = "60s")
    public void recoverStaleClaims() {
        int recovered = jdbc.update("""
            UPDATE crm_platform.event_outbox
               SET status='FAILED', available_at=now(),
                   last_error='stale dispatcher claim recovered', updated_at=now()
             WHERE status='PROCESSING' AND available_at <= now()
            """);
        if (recovered > 0) {
            log.warn("Recovered {} stale CRM outbox claims", recovered);
        }
    }

    private void publish(OutboxEvent event) {
        try {
            Map<String, Object> payload = objectMapper.readValue(event.payload(), MAP_TYPE);
            Map<String, Object> headers = objectMapper.readValue(event.headers(), MAP_TYPE);
            rabbit.convertAndSend(
                properties.getMessaging().getExchange(),
                event.routingKey(),
                payload,
                message -> {
                    message.getMessageProperties().setHeader("tenantId", event.tenantId().toString());
                    message.getMessageProperties().setHeader("eventId", event.id().toString());
                    headers.forEach(message.getMessageProperties()::setHeader);
                    if (event.correlationId() != null) {
                        message.getMessageProperties().setCorrelationId(event.correlationId().toString());
                    }
                    if (event.causationId() != null) {
                        message.getMessageProperties().setHeader("causationId", event.causationId().toString());
                    }
                    return message;
                });
            jdbc.update("""
                UPDATE crm_platform.event_outbox
                   SET status='PUBLISHED', published_at=now(), updated_at=now(), last_error=NULL
                 WHERE id=?
                """, event.id());
        } catch (Exception failure) {
            int attempted = event.attempts() + 1;
            if (attempted >= properties.getMessaging().getMaxAttempts()) {
                moveToDeadLetter(event, failure);
            } else {
                long delaySeconds = Math.min(3600, 1L << Math.min(attempted, 12));
                jdbc.update("""
                    UPDATE crm_platform.event_outbox
                       SET status='FAILED', available_at=now() + (? * interval '1 second'),
                           last_error=?, updated_at=now()
                     WHERE id=?
                    """, delaySeconds, truncate(failure.getMessage()), event.id());
            }
            log.error("CRM outbox event {} failed", event.id(), failure);
        }
    }

    private void moveToDeadLetter(OutboxEvent event, Exception failure) {
        transactions.executeWithoutResult(status -> {
            jdbc.update("""
                INSERT INTO crm_platform.event_dead_letter
                    (tenant_id, source_event_id, routing_key, payload, headers,
                     failure_code, failure_message)
                VALUES (?, ?, ?, ?::jsonb, ?::jsonb, ?, ?)
                """,
                event.tenantId(), event.id(), event.routingKey(), event.payload(), event.headers(),
                failure.getClass().getSimpleName(), truncate(failure.getMessage()));
            jdbc.update("""
                UPDATE crm_platform.event_outbox
                   SET status='DEAD', last_error=?, updated_at=now()
                 WHERE id=?
                """, truncate(failure.getMessage()), event.id());
        });
    }

    private OutboxEvent map(ResultSet rs, int rowNumber) throws SQLException {
        return new OutboxEvent(
            rs.getObject("id", UUID.class),
            rs.getObject("tenant_id", UUID.class),
            rs.getString("routing_key"),
            rs.getString("payload"),
            rs.getString("headers"),
            rs.getObject("correlation_id", UUID.class),
            rs.getObject("causation_id", UUID.class),
            rs.getInt("attempts"),
            OffsetDateTime.now());
    }

    private String truncate(String value) {
        if (value == null) {
            return "unknown failure";
        }
        return value.length() <= 4000 ? value : value.substring(0, 4000);
    }

    private record OutboxEvent(
        UUID id,
        UUID tenantId,
        String routingKey,
        String payload,
        String headers,
        UUID correlationId,
        UUID causationId,
        int attempts,
        OffsetDateTime claimedAt) {}
}
