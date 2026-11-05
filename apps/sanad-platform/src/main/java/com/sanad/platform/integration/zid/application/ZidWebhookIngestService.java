package com.sanad.platform.integration.zid.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanad.platform.integration.zid.domain.ZidConnectionRecord;
import com.sanad.platform.integration.zid.domain.ZidIntegrationException;
import com.sanad.platform.integration.zid.infrastructure.ZidConnectionRepository;
import com.sanad.platform.integration.zid.infrastructure.ZidWebhookVerifier;
import com.sanad.platform.security.rls.TenantRlsTransactionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

/**
 * Webhook ingestion: signature-first, binding-second, inbox-third —
 * mirroring the battle-tested {@code BillingWebhookService} sequence.
 *
 * <ol>
 *   <li>Verify the raw-body signature (fail-closed; never persists
 *       anything on failure — the caller maps to 401/503).</li>
 *   <li>Parse the payload and resolve the trusted binding by store id.
 *       Unbound stores are REJECTED with 422 and nothing is stored
 *       (fail-closed; only a PII-free operational log line is
 *       emitted).</li>
 *   <li>Insert into the idempotent inbox under
 *       UNIQUE (zid_store_id, payload_sha256): exact redelivery is a
 *       no-op (202 with duplicate=true); a differing payload under the
 *       same event id is a replay mismatch (409).</li>
 *   <li>Apply the tenant GUC for the resolved binding before any DB
 *       write so the FORCE RLS policies accept the row.</li>
 * </ol>
 *
 * <p>Event payloads are stored for processing/audit but NEVER logged
 * verbatim; log lines carry only store id, event type and digest.</p>
 */
@Service
public class ZidWebhookIngestService {

    private static final Logger log = LoggerFactory.getLogger(ZidWebhookIngestService.class);

    private final ZidWebhookVerifier verifier;
    private final ZidConnectionRepository connections;
    private final ZidSyncAuditService auditService;
    private final TenantRlsTransactionContext tenantRlsContext;
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public ZidWebhookIngestService(ZidWebhookVerifier verifier,
                                   ZidConnectionRepository connections,
                                   ZidSyncAuditService auditService,
                                   TenantRlsTransactionContext tenantRlsContext,
                                   NamedParameterJdbcTemplate jdbc,
                                   ObjectMapper objectMapper) {
        this.verifier = verifier;
        this.connections = connections;
        this.auditService = auditService;
        this.tenantRlsContext = tenantRlsContext;
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public record Receipt(UUID eventId, boolean duplicate, String status) {
    }

    public enum Outcome {
        /** Signature invalid — respond 401. */
        INVALID_SIGNATURE,
        /** Webhook secret not configured — respond 503. */
        NOT_CONFIGURED,
        /** Store unknown/unbound — respond 422, nothing persisted. */
        TRUSTED_BINDING_NOT_FOUND,
        /** Same event id with different payload — respond 409. */
        REPLAY_MISMATCH,
        /** Stored for processing — respond 202. */
        ACCEPTED
    }

    public record IngestResult(Outcome outcome, Receipt receipt) {
    }

    @Transactional
    public IngestResult ingest(byte[] rawBody, String signatureHeader) {
        if (!verifier.isConfigured()) {
            return new IngestResult(Outcome.NOT_CONFIGURED, null);
        }
        try {
            verifier.verify(rawBody, signatureHeader);
        } catch (ZidIntegrationException e) {
            return new IngestResult(Outcome.INVALID_SIGNATURE, null);
        }

        JsonNode payload;
        try {
            payload = objectMapper.readTree(new String(rawBody, StandardCharsets.UTF_8));
        } catch (java.io.IOException e) {
            log.info("Zid webhook payload rejected: unparseable JSON");
            return new IngestResult(Outcome.TRUSTED_BINDING_NOT_FOUND, null);
        }

        String zidStoreId = extractStoreId(payload);
        if (zidStoreId == null) {
            log.info("Zid webhook rejected: payload lacks a store identifier (type={})",
                    payload.path("event").asText("unknown"));
            return new IngestResult(Outcome.TRUSTED_BINDING_NOT_FOUND, null);
        }

        ZidConnectionRecord connection = connections.findByStoreId(zidStoreId).orElse(null);
        if (connection == null) {
            log.info("Zid webhook rejected: store {} has no trusted binding", zidStoreId);
            return new IngestResult(Outcome.TRUSTED_BINDING_NOT_FOUND, null);
        }

        // Tenant scope established from the STORED binding (never from the payload).
        tenantRlsContext.applyForCurrentTransaction(connection.tenantId());

        String payloadSha256 = sha256Hex(rawBody);
        String eventType = payload.path("event").asText(payload.path("type").asText(null));
        String eventId = payload.path("id").asText(payload.path("event_id").asText(null));

        int inserted;
        try {
            inserted = jdbcInsertInbox(connection, zidStoreId, eventType, eventId, payloadSha256, payload);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            boolean identical = inboxContainsIdentical(zidStoreId, payloadSha256);
            if (identical) {
                return new IngestResult(Outcome.ACCEPTED, new Receipt(null, true, "DUPLICATE"));
            }
            return new IngestResult(Outcome.REPLAY_MISMATCH, null);
        }

        auditService.record(connection.tenantId(), connection.id(),
                ZidSyncAuditService.SyncType.WEBHOOK, eventType == null ? "unknown" : eventType,
                ZidSyncAuditService.Direction.WEBHOOK_INGEST, ZidSyncAuditService.Status.SUCCESS,
                1, 0, null, null);
        log.info("Zid webhook accepted: store={} type={} sha256={}",
                zidStoreId, eventType, payloadSha256.substring(0, 12));
        return new IngestResult(Outcome.ACCEPTED, new Receipt(null, false, "RECEIVED"));
    }

    private int jdbcInsertInbox(ZidConnectionRecord connection, String zidStoreId, String eventType,
                                String eventId, String payloadSha256, JsonNode payload) {
        return jdbc.update("""
                INSERT INTO zid_webhook_events
                    (tenant_id, connection_id, zid_store_id, event_type, event_id,
                     payload_sha256, raw_payload, signature_verified, status, attempts,
                     received_at)
                VALUES
                    (:tenantId, :connectionId, :zidStoreId, :eventType, :eventId,
                     :payloadSha256, :rawPayload::jsonb, TRUE, 'RECEIVED', 0, NOW())
                """, new MapSqlParameterSource()
                .addValue("tenantId", connection.tenantId())
                .addValue("connectionId", connection.id())
                .addValue("zidStoreId", zidStoreId)
                .addValue("eventType", eventType)
                .addValue("eventId", eventId)
                .addValue("payloadSha256", payloadSha256)
                .addValue("rawPayload", payload.toString()));
    }

    private boolean inboxContainsIdentical(String zidStoreId, String payloadSha256) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM zid_webhook_events
                WHERE zid_store_id = :zidStoreId AND payload_sha256 = :payloadSha256
                """, new MapSqlParameterSource()
                .addValue("zidStoreId", zidStoreId)
                .addValue("payloadSha256", payloadSha256), Integer.class);
        return count != null && count > 0;
    }

    /**
     * Extracts the store identifier from the payload. Zid's webhook
     * event envelope field names are not fully documented in the public
     * docs, so the common candidates are probed defensively; events
     * without any store identifier are rejected rather than guessed.
     */
    static String extractStoreId(JsonNode payload) {
        for (String field : new String[]{"store_id", "storeId", "merchant"}) {
            JsonNode node = payload.path(field);
            if (node.isValueNode() && node.asText() != null && !node.asText().isBlank()) {
                return node.asText();
            }
        }
        JsonNode nested = payload.path("store");
        for (String field : new String[]{"id", "store_id"}) {
            JsonNode node = nested.path(field);
            if (node.isValueNode() && !node.asText().isBlank()) {
                return node.asText();
            }
        }
        JsonNode data = payload.path("data");
        for (String field : new String[]{"store_id", "storeId"}) {
            JsonNode node = data.path(field);
            if (node.isValueNode() && !node.asText().isBlank()) {
                return node.asText();
            }
        }
        return null;
    }

    private static String sha256Hex(byte[] body) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(body));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
