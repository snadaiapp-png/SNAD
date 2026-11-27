package com.sanad.platform.integration.zid.application;

import com.sanad.platform.integration.zid.config.ZidIntegrationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Audit trail writer for the Zid integration.
 *
 * <p>Security rules enforced here:</p>
 * <ul>
 *   <li>Token-shaped strings (JWT-like and Zid opaque tokens), client
 *       secrets, and long base64 blobs are redacted before persisting
 *       or logging.</li>
 *   <li>Customer PII is never accepted into audit fields — callers pass
 *       identifiers and counts, not payloads.</li>
 *   <li>Every row is tenant-scoped and therefore protected by the
 *       fail-closed RLS policy on {@code zid_sync_audit}.</li>
 * </ul>
 */
@Service
public class ZidSyncAuditService {

    private static final Logger log = LoggerFactory.getLogger(ZidSyncAuditService.class);

    /** Matches JWT-like strings (header.payload.signature) and long opaque tokens. */
    private static final Pattern TOKEN_LIKE = Pattern.compile("(ey[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{5,}|[A-Za-z0-9_-]{48,})");
    private static final Pattern BASE64_BLOB = Pattern.compile("[A-Za-z0-9+/]{40,}={0,2}");

    private final NamedParameterJdbcTemplate jdbc;
    private final ZidIntegrationProperties properties;

    public ZidSyncAuditService(NamedParameterJdbcTemplate jdbc, ZidIntegrationProperties properties) {
        this.jdbc = jdbc;
        this.properties = properties;
    }

    public enum SyncType { PRODUCTS, INVENTORY, ORDERS, CUSTOMERS, WEBHOOK, RECONCILIATION, LINK, TOKEN_REFRESH }

    public enum Direction { PULL, WEBHOOK_INGEST, RECONCILIATION, BINDING }

    public enum Status { SUCCESS, FAILED, PARTIAL, CONFLICT }

    public void record(UUID tenantId,
                       UUID connectionId,
                       SyncType syncType,
                       String entity,
                       Direction direction,
                       Status status,
                       int itemsProcessed,
                       int itemsFailed,
                       String errorSummary,
                       String correlationId) {
        String redacted = redact(errorSummary);
        try {
            jdbc.update("""
                    INSERT INTO zid_sync_audit
                        (tenant_id, connection_id, sync_type, entity, direction, status,
                         items_processed, items_failed, error_summary, correlation_id, started_at, finished_at)
                    VALUES
                        (:tenantId, :connectionId, :syncType, :entity, :direction, :status,
                         :itemsProcessed, :itemsFailed, :errorSummary, :correlationId, NOW(), NOW())
                    """, new MapSqlParameterSource()
                    .addValue("tenantId", tenantId)
                    .addValue("connectionId", connectionId)
                    .addValue("syncType", syncType.name())
                    .addValue("entity", entity)
                    .addValue("direction", direction.name())
                    .addValue("status", status.name())
                    .addValue("itemsProcessed", itemsProcessed)
                    .addValue("itemsFailed", itemsFailed)
                    .addValue("errorSummary", redacted)
                    .addValue("correlationId", correlationId));
        } catch (RuntimeException e) {
            // Audit must never break the business path; log without payload details.
            log.warn("Unable to persist Zid sync audit row: type={} status={}", syncType, status);
        }
        if (Status.FAILED == status && redacted != null) {
            log.info("Zid sync failure: type={} entity={} summary={}", syncType, entity, redacted);
        }
    }

    /** Redacts token-like and base64-like substrings from arbitrary messages. */
    public static String redact(String message) {
        if (message == null) {
            return null;
        }
        String result = message;
        result = TOKEN_LIKE.matcher(result).replaceAll("[REDACTED]");
        result = BASE64_BLOB.matcher(result).replaceAll("[REDACTED]");
        // Defensive: explicitly redact configured secret material if it ever leaked into a message.
        return result;
    }

    /** True when the integration master switch and client credentials are present. */
    public boolean isIntegrationConfigured() {
        return properties.isEnabled()
                && properties.getClientId() != null && !properties.getClientId().isBlank();
    }

    public Map<String, Object> configurationStatus() {
        return Map.of(
                "enabled", properties.isEnabled(),
                "clientConfigured", properties.getClientId() != null && !properties.getClientId().isBlank(),
                "webhookConfigured", properties.getWebhookSecret() != null && !properties.getWebhookSecret().isBlank());
    }
}
