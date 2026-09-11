package com.sanad.platform.subscription.billing.application;

import com.sanad.platform.admin.service.PlatformAuditWriter;
import com.sanad.platform.security.rls.TenantRlsTransactionContext;
import com.sanad.platform.subscription.billing.domain.BillingPaymentProvider;
import com.sanad.platform.subscription.billing.infrastructure.BillingWebhookResolutionContext;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

/**
 * R13-G05 signed webhook ingress.
 *
 * <p>Order is fail-closed: verify signature first, resolve tenant only from a
 * stored provider-payment binding under a short-lived provider-scoped SELECT
 * policy, switch to normal tenant RLS, then atomically persist inbox, invoke
 * the G06 settlement authority, persist audit/outbox, and mark processed.
 * Raw webhook payload bytes are never stored.</p>
 */
@Service
public class BillingWebhookService {

    public static final String OUTBOX_EVENT_TYPE =
            "BILLING.PROVIDER_EVENT_RECEIVED.v1";

    private final BillingPaymentProvider provider;
    private final JdbcTemplate jdbc;
    private final BillingWebhookResolutionContext webhookResolutionContext;
    private final TenantRlsTransactionContext tenantRlsContext;
    private final PlatformAuditWriter auditWriter;
    private final BillingSettlementService settlementService;

    public BillingWebhookService(
            BillingPaymentProvider provider,
            JdbcTemplate jdbc,
            BillingWebhookResolutionContext webhookResolutionContext,
            TenantRlsTransactionContext tenantRlsContext,
            PlatformAuditWriter auditWriter,
            BillingSettlementService settlementService
    ) {
        this.provider = provider;
        this.jdbc = jdbc;
        this.webhookResolutionContext = webhookResolutionContext;
        this.tenantRlsContext = tenantRlsContext;
        this.auditWriter = auditWriter;
        this.settlementService = settlementService;
    }

    @Transactional
    public WebhookReceipt receive(byte[] payload, String signatureHeader) {
        BillingPaymentProvider.ProviderEventEnvelope envelope =
                provider.verifyAndParseEvent(payload, signatureHeader);
        String providerCode = provider.providerCode();

        PaymentBinding binding;
        webhookResolutionContext.applyVerifiedProviderForCurrentTransaction(providerCode);
        try {
            binding = resolveTrustedBinding(
                    providerCode, envelope.providerPaymentRef());
        } finally {
            webhookResolutionContext.clearForCurrentTransaction();
        }

        tenantRlsContext.applyForCurrentTransaction(binding.tenantId());

        UUID eventRowId = UUID.randomUUID();
        int inserted = jdbc.update(
                "INSERT INTO subscription_billing_provider_events "
                        + "(id, tenant_id, subscription_id, billing_invoice_id, provider, "
                        + "provider_event_id, provider_payment_ref, event_type, payload_sha256, "
                        + "signature_verified, signature_verified_at, processing_state, "
                        + "attempt_count, received_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, TRUE, ?, 'RECEIVED', 1, ?, ?) "
                        + "ON CONFLICT (provider, provider_event_id) DO NOTHING",
                eventRowId,
                binding.tenantId(),
                binding.subscriptionId(),
                binding.billingInvoiceId(),
                providerCode,
                envelope.providerEventId(),
                envelope.providerPaymentRef(),
                envelope.eventType(),
                envelope.payloadSha256(),
                Timestamp.from(Instant.now()),
                Timestamp.from(Instant.now()),
                Timestamp.from(Instant.now()));

        if (inserted == 0) {
            ExistingEvent existing = findExistingEvent(
                    binding.tenantId(), providerCode, envelope.providerEventId());
            if (existing == null
                    || !binding.subscriptionId().equals(existing.subscriptionId())
                    || !binding.billingInvoiceId().equals(existing.billingInvoiceId())
                    || !envelope.providerPaymentRef().equals(existing.providerPaymentRef())
                    || !envelope.eventType().equals(existing.eventType())
                    || !envelope.payloadSha256().equalsIgnoreCase(existing.payloadSha256())) {
                throw new ReplayMismatchException(
                        "Provider event id replay does not match the original trusted event");
            }
            return new WebhookReceipt(envelope.providerEventId(), true);
        }

        // G06 convergence runs in this same transaction. Any Finance, SCP
        // projection or canonical lifecycle failure rolls the inbox back and
        // leaves the signed provider event retryable.
        settlementService.handleVerifiedProviderEvent(
                binding.tenantId(),
                binding.paymentAttemptId(),
                binding.billingInvoiceId(),
                envelope.providerPaymentRef(),
                envelope.eventType());

        String idempotencyKey =
                outboxIdempotencyKey(providerCode, envelope.providerEventId());

        jdbc.update(
                "INSERT INTO subscription_billing_outbox "
                        + "(event_id, tenant_id, event_type, event_version, aggregate_type, "
                        + "aggregate_id, idempotency_key, payload_metadata, status, available_at, created_at) "
                        + "VALUES (?, ?, ?, 1, 'BILLING_INVOICE', ?, ?, "
                        + "jsonb_build_object("
                        + "'provider', ?, "
                        + "'providerEventId', ?, "
                        + "'providerPaymentRef', ?, "
                        + "'eventType', ?, "
                        + "'payloadSha256', ?"
                        + "), 'READY', ?, ?)",
                UUID.randomUUID(),
                binding.tenantId(),
                OUTBOX_EVENT_TYPE,
                binding.billingInvoiceId(),
                idempotencyKey,
                providerCode,
                envelope.providerEventId(),
                envelope.providerPaymentRef(),
                envelope.eventType(),
                envelope.payloadSha256(),
                Timestamp.from(Instant.now()),
                Timestamp.from(Instant.now()));

        auditWriter.writeSuccess(
                null,
                null,
                binding.tenantId(),
                "BILLING.WEBHOOK.ACCEPTED",
                "BILLING_PROVIDER_EVENT",
                envelope.providerEventId(),
                "Cryptographically verified provider webhook accepted",
                null,
                Map.of(
                        "provider", providerCode,
                        "providerEventId", envelope.providerEventId(),
                        "providerPaymentRef", envelope.providerPaymentRef(),
                        "eventType", envelope.eventType(),
                        "payloadSha256", envelope.payloadSha256(),
                        "billingInvoiceId", binding.billingInvoiceId().toString()),
                providerCode + ":" + envelope.providerEventId(),
                Instant.now());

        int processed = jdbc.update(
                "UPDATE subscription_billing_provider_events "
                        + "SET processing_state = 'PROCESSED', processed_at = ?, updated_at = ? "
                        + "WHERE id = ? AND tenant_id = ? AND processing_state = 'RECEIVED'",
                Timestamp.from(Instant.now()),
                Timestamp.from(Instant.now()),
                eventRowId,
                binding.tenantId());
        if (processed != 1) {
            throw new IllegalStateException(
                    "Provider event processed marker could not be persisted");
        }

        return new WebhookReceipt(envelope.providerEventId(), false);
    }

    private PaymentBinding resolveTrustedBinding(
            String providerCode,
            String providerPaymentRef
    ) {
        try {
            PaymentBinding binding = jdbc.queryForObject(
                    "SELECT tenant_id, subscription_id, billing_invoice_id, id "
                            + "FROM subscription_billing_payment_attempts "
                            + "WHERE provider = ? AND provider_payment_ref = ?",
                    (rs, rowNum) -> new PaymentBinding(
                            rs.getObject("tenant_id", UUID.class),
                            rs.getObject("subscription_id", UUID.class),
                            rs.getObject("billing_invoice_id", UUID.class),
                            rs.getObject("id", UUID.class)),
                    providerCode,
                    providerPaymentRef);
            if (binding == null) {
                throw new BindingNotFoundException(
                        "No trusted provider payment binding exists for this webhook");
            }
            return binding;
        } catch (EmptyResultDataAccessException e) {
            throw new BindingNotFoundException(
                    "No trusted provider payment binding exists for this webhook");
        }
    }

    private ExistingEvent findExistingEvent(
            UUID tenantId,
            String providerCode,
            String providerEventId
    ) {
        try {
            return jdbc.queryForObject(
                    "SELECT subscription_id, billing_invoice_id, provider_payment_ref, "
                            + "event_type, payload_sha256 "
                            + "FROM subscription_billing_provider_events "
                            + "WHERE tenant_id = ? AND provider = ? AND provider_event_id = ?",
                    (rs, rowNum) -> new ExistingEvent(
                            rs.getObject("subscription_id", UUID.class),
                            rs.getObject("billing_invoice_id", UUID.class),
                            rs.getString("provider_payment_ref"),
                            rs.getString("event_type"),
                            rs.getString("payload_sha256")),
                    tenantId,
                    providerCode,
                    providerEventId);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    public static String outboxIdempotencyKey(
            String providerCode,
            String providerEventId
    ) {
        String material = providerCode + ":" + providerEventId;
        return "PROVIDER_EVENT:" + providerCode + ":" + sha256Hex(
                material.getBytes(StandardCharsets.UTF_8));
    }

    private static String sha256Hex(byte[] value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    public record WebhookReceipt(
            String providerEventId,
            boolean duplicate
    ) {}

    public static class BindingNotFoundException extends RuntimeException {
        public BindingNotFoundException(String message) {
            super(message);
        }
    }

    public static class ReplayMismatchException extends RuntimeException {
        public ReplayMismatchException(String message) {
            super(message);
        }
    }

    private record PaymentBinding(
            UUID tenantId,
            UUID subscriptionId,
            UUID billingInvoiceId,
            UUID paymentAttemptId
    ) {}

    private record ExistingEvent(
            UUID subscriptionId,
            UUID billingInvoiceId,
            String providerPaymentRef,
            String eventType,
            String payloadSha256
    ) {}
}
