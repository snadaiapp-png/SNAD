package com.sanad.platform.subscription.billing.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanad.platform.subscription.billing.domain.BillingPaymentProvider;
import com.sanad.platform.subscription.billing.domain.ProviderEventVerificationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Explicit non-production sandbox adapter for the R0C13 provider-neutral port.
 *
 * <p>No network calls and no committed provider credentials. TEST webhook
 * verification uses an environment-supplied ephemeral HMAC key. The adapter
 * can only load when mode=TEST is explicit and the active profile is not prod.</p>
 */
@Component
@Profile("!prod")
@ConditionalOnProperty(
        name = "sanad.subscription.billing.provider.mode",
        havingValue = "TEST",
        matchIfMissing = false)
public class TestBillingPaymentProvider implements BillingPaymentProvider {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final byte[] webhookSecret;
    private final ConcurrentMap<String, ProviderCustomer> customers = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, StoredIntent> intentsByIdempotency = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, StoredIntent> intentsByReference = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, StoredRefund> refundsByIdempotency = new ConcurrentHashMap<>();

    public TestBillingPaymentProvider(
            @Value("${sanad.subscription.billing.provider.test-webhook-secret:}")
            String webhookSecret
    ) {
        if (webhookSecret == null || webhookSecret.isBlank()) {
            throw new IllegalStateException(
                    "R0C13 TEST provider requires an ephemeral test webhook secret");
        }
        this.webhookSecret = webhookSecret.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public String providerCode() {
        return "TEST";
    }

    @Override
    public ProviderCustomer ensureProviderCustomer(EnsureCustomerCommand command) {
        String key = command.tenantId() + ":" + command.idempotencyKey();
        ProviderCustomer candidate = new ProviderCustomer(
                "test_cus_" + compactUuid("CUSTOMER:" + command.tenantId() + ":" + command.customerKey()));
        ProviderCustomer existing = customers.putIfAbsent(key, candidate);
        if (existing != null && !existing.equals(candidate)) {
            throw new IllegalStateException(
                    "TEST provider customer idempotency mismatch for key " + command.idempotencyKey());
        }
        return existing != null ? existing : candidate;
    }

    @Override
    public PaymentIntent createPaymentIntent(CreatePaymentIntentCommand command) {
        String key = command.tenantId() + ":" + command.idempotencyKey();
        PaymentIntent candidate = new PaymentIntent(
                "test_pi_" + compactUuid("INTENT:" + key),
                command.amountMinor(),
                command.currencyCode(),
                PaymentStatus.PENDING);
        StoredIntent storedCandidate = new StoredIntent(command, candidate);

        StoredIntent existing = intentsByIdempotency.putIfAbsent(key, storedCandidate);
        if (existing != null) {
            if (!existing.command().equals(command)) {
                throw new IllegalStateException(
                        "TEST provider payment idempotency mismatch for key " + command.idempotencyKey());
            }
            return existing.intent();
        }

        intentsByReference.put(candidate.providerPaymentRef(), storedCandidate);
        return candidate;
    }

    @Override
    public PaymentState queryPaymentState(QueryPaymentStateQuery query) {
        StoredIntent stored = requireIntent(query.tenantId(), query.providerPaymentRef());
        PaymentIntent intent = stored.intent();
        return new PaymentState(
                intent.providerPaymentRef(),
                intent.amountMinor(),
                intent.currencyCode(),
                intent.status());
    }

    @Override
    public RefundResult requestRefund(RefundCommand command) {
        StoredIntent stored = requireIntent(command.tenantId(), command.providerPaymentRef());
        PaymentIntent intent = stored.intent();
        if (!intent.currencyCode().equals(command.currencyCode())) {
            throw new IllegalStateException(
                    "TEST provider refund currency mismatch for " + command.providerPaymentRef());
        }
        if (command.amountMinor() > intent.amountMinor()) {
            throw new IllegalStateException(
                    "TEST provider refund amount exceeds payment amount for " + command.providerPaymentRef());
        }

        String key = command.tenantId() + ":" + command.idempotencyKey();
        RefundResult candidate = new RefundResult(
                "test_re_" + compactUuid("REFUND:" + key),
                command.amountMinor(),
                command.currencyCode(),
                RefundStatus.ACCEPTED);
        StoredRefund refundCandidate = new StoredRefund(command, candidate);
        StoredRefund existing = refundsByIdempotency.putIfAbsent(key, refundCandidate);
        if (existing != null) {
            if (!existing.command().equals(command)) {
                throw new IllegalStateException(
                        "TEST provider refund idempotency mismatch for key " + command.idempotencyKey());
            }
            return existing.result();
        }
        return candidate;
    }

    @Override
    public ProviderEventEnvelope verifyAndParseEvent(byte[] payload, String signatureHeader) {
        if (payload == null || payload.length == 0) {
            throw new ProviderEventVerificationException(
                    ProviderEventVerificationException.Reason.MALFORMED_PAYLOAD,
                    "provider event payload must not be empty");
        }

        String expected = "test-hmac-sha256=" + hmacSha256Hex(payload);
        if (signatureHeader == null
                || !MessageDigest.isEqual(
                        expected.getBytes(StandardCharsets.UTF_8),
                        signatureHeader.trim().getBytes(StandardCharsets.UTF_8))) {
            throw new ProviderEventVerificationException(
                    ProviderEventVerificationException.Reason.INVALID_SIGNATURE,
                    "TEST provider event signature verification failed");
        }

        try {
            JsonNode root = objectMapper.readTree(payload);
            String eventId = requiredText(root, "eventId");
            String eventType = requiredText(root, "eventType");
            String paymentRef = requiredText(root, "paymentRef");

            StoredIntent stored = intentsByReference.get(paymentRef);
            if (stored != null) {
                PaymentStatus next = statusFromEvent(eventType, stored.intent().status());
                StoredIntent updated = new StoredIntent(
                        stored.command(),
                        new PaymentIntent(
                                stored.intent().providerPaymentRef(),
                                stored.intent().amountMinor(),
                                stored.intent().currencyCode(),
                                next));
                intentsByReference.put(paymentRef, updated);
                intentsByIdempotency.put(
                        stored.command().tenantId() + ":" + stored.command().idempotencyKey(),
                        updated);
            }

            return new ProviderEventEnvelope(
                    eventId, eventType, paymentRef, sha256Hex(payload));
        } catch (ProviderEventVerificationException e) {
            throw e;
        } catch (IllegalArgumentException e) {
            throw new ProviderEventVerificationException(
                    ProviderEventVerificationException.Reason.MALFORMED_PAYLOAD,
                    "TEST provider event payload is invalid", e);
        } catch (Exception e) {
            throw new ProviderEventVerificationException(
                    ProviderEventVerificationException.Reason.MALFORMED_PAYLOAD,
                    "TEST provider event payload is invalid", e);
        }
    }

    private StoredIntent requireIntent(UUID tenantId, String paymentRef) {
        StoredIntent stored = intentsByReference.get(paymentRef);
        if (stored == null || !stored.command().tenantId().equals(tenantId)) {
            throw new IllegalArgumentException(
                    "TEST provider payment reference not found for tenant");
        }
        return stored;
    }

    private String hmacSha256Hex(byte[] payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(webhookSecret, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA256 is unavailable", e);
        }
    }

    private static PaymentStatus statusFromEvent(String eventType, PaymentStatus current) {
        return switch (eventType) {
            case "payment.succeeded" -> PaymentStatus.SUCCEEDED;
            case "payment.failed" -> PaymentStatus.FAILED;
            case "payment.cancelled" -> PaymentStatus.CANCELLED;
            case "payment.refunded" -> PaymentStatus.REFUNDED;
            default -> current;
        };
    }

    private static String requiredText(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || !node.isTextual() || node.asText().isBlank()) {
            throw new IllegalArgumentException(
                    "TEST provider event field is required: " + field);
        }
        return node.asText().trim();
    }

    private static String sha256Hex(byte[] payload) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(payload));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private static String compactUuid(String material) {
        return UUID.nameUUIDFromBytes(material.getBytes(StandardCharsets.UTF_8))
                .toString().replace("-", "");
    }

    private record StoredIntent(
            CreatePaymentIntentCommand command,
            PaymentIntent intent
    ) {}

    private record StoredRefund(
            RefundCommand command,
            RefundResult result
    ) {}
}
