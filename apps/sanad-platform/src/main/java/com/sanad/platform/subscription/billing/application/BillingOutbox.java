package com.sanad.platform.subscription.billing.application;

import com.sanad.platform.security.rls.TenantRlsTransactionContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * R13-G07.0 reusable billing outbox application boundary.
 *
 * <p>Single transactional write path for every typed and versioned billing
 * fact of the approved R0C13 design. Properties guaranteed here:</p>
 *
 * <ul>
 *   <li>typed, versioned event names (family {@code BILLING.*.v1});</li>
 *   <li>tenant scoped — the FORCE-RLS tenant context is applied by this
 *       boundary for the current transaction before the insert;</li>
 *   <li>deterministic/idempotent — callers pass a stable idempotency key and
 *       the {@code uq_subscription_billing_outbox_idempotency} constraint
 *       collapses replays via {@code ON CONFLICT DO NOTHING};</li>
 *   <li>transactional — the event joins the caller's canonical local
 *       mutation transaction; a rollback removes both together;</li>
 *   <li>no raw webhook payload, no PAN/CVC/secrets — metadata is restricted
 *       to scalar trace fields and re-validated against the sensitive-key
 *       deny list that mirrors the database CHECK constraint.</li>
 * </ul>
 *
 * <p>Callers emit an event only where a real application-owned production
 * transition exists. Emitting is never a substitute for the mutation; it
 * always happens inside the same transaction, after the mutation succeeded.</p>
 */
@Service
public class BillingOutbox {

    /** Outbox schema version written to the event_version column. */
    public static final int EVENT_VERSION = 1;

    public static final String AGGREGATE_BILLING_INVOICE = "BILLING_INVOICE";
    public static final String AGGREGATE_PAYMENT_ATTEMPT = "BILLING_PAYMENT_ATTEMPT";
    public static final String AGGREGATE_RECONCILIATION_RUN = "BILLING_RECONCILIATION_RUN";

    /** Approved R0C13 event family — G07.0 design conformance. */
    public static final String TYPE_INVOICE_ISSUED = "BILLING.INVOICE_ISSUED.v1";
    public static final String TYPE_PAYMENT_PENDING = "BILLING.PAYMENT_PENDING.v1";
    public static final String TYPE_PAYMENT_SUCCEEDED = "BILLING.PAYMENT_SUCCEEDED.v1";
    public static final String TYPE_PAYMENT_FAILED = "BILLING.PAYMENT_FAILED.v1";
    public static final String TYPE_REFUND_RECORDED = "BILLING.REFUND_RECORDED.v1";
    public static final String TYPE_RECONCILIATION_EXCEPTION =
            "BILLING.RECONCILIATION_EXCEPTION.v1";

    /** Existing G05 production event. Retained; never removed by G07.0. */
    public static final String TYPE_PROVIDER_EVENT_RECEIVED =
            "BILLING.PROVIDER_EVENT_RECEIVED.v1";

    private static final Map<String, String> AGGREGATE_TYPE_BY_EVENT = Map.of(
            TYPE_INVOICE_ISSUED, AGGREGATE_BILLING_INVOICE,
            TYPE_PAYMENT_PENDING, AGGREGATE_PAYMENT_ATTEMPT,
            TYPE_PAYMENT_SUCCEEDED, AGGREGATE_PAYMENT_ATTEMPT,
            TYPE_PAYMENT_FAILED, AGGREGATE_PAYMENT_ATTEMPT,
            TYPE_REFUND_RECORDED, AGGREGATE_PAYMENT_ATTEMPT,
            TYPE_RECONCILIATION_EXCEPTION, AGGREGATE_RECONCILIATION_RUN,
            TYPE_PROVIDER_EVENT_RECEIVED, AGGREGATE_BILLING_INVOICE);

    /**
     * Mirrors the database CHECK constraint on payload_metadata. The
     * application refuses earlier (and additionally rejects raw-payload
     * carriers) so that a violation can never reach the database.
     */
    private static final Set<String> FORBIDDEN_METADATA_KEYS = Set.of(
            "card_number", "pan", "cvc", "cvv", "track_data", "pin",
            "password", "secret", "api_key", "apikey", "token", "authorization",
            "payload", "rawpayload", "body", "raw_body");

    private final JdbcTemplate jdbc;
    private final TenantRlsTransactionContext tenantRlsContext;

    public BillingOutbox(
            JdbcTemplate jdbc,
            TenantRlsTransactionContext tenantRlsContext
    ) {
        this.jdbc = jdbc;
        this.tenantRlsContext = tenantRlsContext;
    }

    /**
     * Conflict semantics for the idempotency-key unique constraint.
     *
     * <p>IGNORE_DUPLICATE collapses genuine replays of a per-aggregate
     * business fact (e.g. one PAYMENT_SUCCEEDED per attempt). FAIL_ON_CONFLICT
     * propagates the unique violation so the caller's transaction rolls back —
     * the G05 webhook contract requires the provider-event-accepted outbox
     * write to be a mandatory, failure-propagating part of ingress.</p>
     */
    public enum ConflictPolicy {
        IGNORE_DUPLICATE,
        FAIL_ON_CONFLICT
    }

    /**
     * Emits one typed/versioned billing fact transactionally and idempotently.
     *
     * @param tenantId       owning tenant; also applied as the FORCE-RLS
     *                       context for the current transaction
     * @param eventType      one of the typed {@code TYPE_*} constants
     * @param aggregateId    aggregate the fact is about (deterministic per
     *                       event family)
     * @param idempotencyKey stable caller-owned key; replays collapse on the
     *                       database unique constraint
     * @param metadata       scalar trace fields only (String/Number/Boolean/
     *                       UUID); sensitive keys and raw payloads are refused
     */
    public void emit(
            UUID tenantId,
            String eventType,
            UUID aggregateId,
            String idempotencyKey,
            Map<String, Object> metadata
    ) {
        emit(tenantId, eventType, aggregateId, idempotencyKey, metadata,
                ConflictPolicy.IGNORE_DUPLICATE);
    }

    public void emit(
            UUID tenantId,
            String eventType,
            UUID aggregateId,
            String idempotencyKey,
            Map<String, Object> metadata,
            ConflictPolicy conflictPolicy
    ) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId must not be null");
        }
        if (eventType == null || !AGGREGATE_TYPE_BY_EVENT.containsKey(eventType)) {
            throw new IllegalArgumentException(
                    "Unsupported billing outbox event type: " + eventType);
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()
                || idempotencyKey.length() > 200) {
            throw new IllegalArgumentException(
                    "idempotencyKey must be 1..200 characters");
        }
        String json = sanitizeAndSerialize(metadata);

        String conflictClause = conflictPolicy == ConflictPolicy.IGNORE_DUPLICATE
                ? " ON CONFLICT (tenant_id, idempotency_key) DO NOTHING"
                : "";

        tenantRlsContext.applyForCurrentTransaction(tenantId);
        jdbc.update(
                "INSERT INTO subscription_billing_outbox "
                        + "(event_id, tenant_id, event_type, event_version, aggregate_type, "
                        + "aggregate_id, idempotency_key, payload_metadata, status, available_at, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, 'READY', ?, ?)" + conflictClause,
                UUID.randomUUID(),
                tenantId,
                eventType,
                EVENT_VERSION,
                AGGREGATE_TYPE_BY_EVENT.get(eventType),
                aggregateId,
                idempotencyKey,
                json,
                Timestamp.from(Instant.now()),
                Timestamp.from(Instant.now()));
    }

    /**
     * Serializes caller metadata to a jsonb literal after enforcing the
     * sensitive-key deny list. Keys must be simple identifiers; values must
     * be scalars (String/Number/Boolean/UUID/null). No nested structures and
     * therefore no raw payloads can cross this boundary.
     */
    private static String sanitizeAndSerialize(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return "{}";
        }
        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : metadata.entrySet()) {
            String key = entry.getKey();
            if (key == null || !key.matches("^[A-Za-z0-9_.]{1,60}$")) {
                throw new IllegalArgumentException(
                        "Outbox metadata key must be a simple identifier: " + key);
            }
            if (FORBIDDEN_METADATA_KEYS.contains(key.toLowerCase(java.util.Locale.ROOT))) {
                throw new IllegalArgumentException(
                        "Outbox metadata must not contain sensitive field: " + key);
            }
            Object value = entry.getValue();
            if (!(value == null
                    || value instanceof String
                    || value instanceof Number
                    || value instanceof Boolean
                    || value instanceof UUID)) {
                throw new IllegalArgumentException(
                        "Outbox metadata values must be scalars; key=" + key);
            }
            if (!first) {
                json.append(',');
            }
            first = false;
            json.append('"').append(escape(key)).append("\":");
            if (value == null) {
                json.append("null");
            } else if (value instanceof Number || value instanceof Boolean) {
                json.append(value);
            } else {
                json.append('"').append(escape(String.valueOf(value))).append('"');
            }
        }
        return json.append('}').toString();
    }

    private static String escape(String value) {
        StringBuilder out = new StringBuilder(value.length() + 8);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.toString();
    }
}
