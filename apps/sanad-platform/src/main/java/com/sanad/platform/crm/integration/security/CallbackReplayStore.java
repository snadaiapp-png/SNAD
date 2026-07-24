package com.sanad.platform.crm.integration.security;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

/** Durable nonce/JTI replay protection for authenticated integration callbacks. */
@Component
public class CallbackReplayStore {

    private final JdbcTemplate jdbc;

    public CallbackReplayStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Consumes a callback token identity exactly once. Returns false when either
     * the service/JTI tuple or service/nonce tuple was previously observed.
     */
    public boolean consume(
            UUID tenantId,
            String serviceName,
            String jti,
            String nonce,
            String correlationId,
            Instant expiresAt) {
        try {
            int inserted = jdbc.update(
                    "INSERT INTO crm_integration_callback_replay " +
                            "(tenant_id, service_name, jti, nonce, correlation_id, received_at, expires_at) " +
                            "VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, ?)",
                    tenantId,
                    required(serviceName, "serviceName"),
                    required(jti, "jti"),
                    required(nonce, "nonce"),
                    required(correlationId, "correlationId"),
                    Timestamp.from(expiresAt));
            return inserted == 1;
        } catch (DuplicateKeyException replay) {
            return false;
        }
    }

    @Scheduled(cron = "${sanad.service-auth.replay-cleanup-cron:0 17 * * * *}")
    public void deleteExpired() {
        jdbc.update("DELETE FROM crm_integration_callback_replay WHERE expires_at <= CURRENT_TIMESTAMP");
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.strip();
    }
}
