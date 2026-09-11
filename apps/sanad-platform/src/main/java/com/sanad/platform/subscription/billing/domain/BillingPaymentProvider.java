package com.sanad.platform.subscription.billing.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * R0C13 SaaS billing payment-provider contract.
 *
 * <p>This contract is intentionally distinct from Commerce PaymentGatewayPort.
 * Money crosses this boundary only as integer minor units plus an ISO-4217
 * currency code. Provider event envelopes intentionally contain no tenant id;
 * tenant resolution belongs to the trusted binding/inbox pipeline in G05.</p>
 */
public interface BillingPaymentProvider {

    ProviderCustomer ensureProviderCustomer(EnsureCustomerCommand command);

    PaymentIntent createPaymentIntent(CreatePaymentIntentCommand command);

    PaymentState queryPaymentState(QueryPaymentStateQuery query);

    RefundResult requestRefund(RefundCommand command);

    ProviderEventEnvelope verifyAndParseEvent(byte[] payload, String signatureHeader);

    record EnsureCustomerCommand(
            UUID tenantId,
            String customerKey,
            String idempotencyKey
    ) {
        public EnsureCustomerCommand {
            Objects.requireNonNull(tenantId, "tenantId");
            customerKey = requireText(customerKey, "customerKey");
            idempotencyKey = requireText(idempotencyKey, "idempotencyKey");
        }
    }

    record ProviderCustomer(
            String providerCustomerRef
    ) {
        public ProviderCustomer {
            providerCustomerRef = requireText(providerCustomerRef, "providerCustomerRef");
        }
    }

    record CreatePaymentIntentCommand(
            UUID tenantId,
            UUID billingInvoiceId,
            String providerCustomerRef,
            long amountMinor,
            String currencyCode,
            String idempotencyKey
    ) {
        public CreatePaymentIntentCommand {
            Objects.requireNonNull(tenantId, "tenantId");
            Objects.requireNonNull(billingInvoiceId, "billingInvoiceId");
            providerCustomerRef = requireText(providerCustomerRef, "providerCustomerRef");
            if (amountMinor <= 0) {
                throw new IllegalArgumentException("amountMinor must be positive");
            }
            currencyCode = requireCurrency(currencyCode);
            idempotencyKey = requireText(idempotencyKey, "idempotencyKey");
        }
    }

    record PaymentIntent(
            String providerPaymentRef,
            long amountMinor,
            String currencyCode,
            PaymentStatus status
    ) {
        public PaymentIntent {
            providerPaymentRef = requireText(providerPaymentRef, "providerPaymentRef");
            if (amountMinor <= 0) {
                throw new IllegalArgumentException("amountMinor must be positive");
            }
            currencyCode = requireCurrency(currencyCode);
            Objects.requireNonNull(status, "status");
        }
    }

    record QueryPaymentStateQuery(
            UUID tenantId,
            String providerPaymentRef
    ) {
        public QueryPaymentStateQuery {
            Objects.requireNonNull(tenantId, "tenantId");
            providerPaymentRef = requireText(providerPaymentRef, "providerPaymentRef");
        }
    }

    record PaymentState(
            String providerPaymentRef,
            long amountMinor,
            String currencyCode,
            PaymentStatus status
    ) {
        public PaymentState {
            providerPaymentRef = requireText(providerPaymentRef, "providerPaymentRef");
            if (amountMinor <= 0) {
                throw new IllegalArgumentException("amountMinor must be positive");
            }
            currencyCode = requireCurrency(currencyCode);
            Objects.requireNonNull(status, "status");
        }
    }

    record RefundCommand(
            UUID tenantId,
            String providerPaymentRef,
            long amountMinor,
            String currencyCode,
            String idempotencyKey
    ) {
        public RefundCommand {
            Objects.requireNonNull(tenantId, "tenantId");
            providerPaymentRef = requireText(providerPaymentRef, "providerPaymentRef");
            if (amountMinor <= 0) {
                throw new IllegalArgumentException("amountMinor must be positive");
            }
            currencyCode = requireCurrency(currencyCode);
            idempotencyKey = requireText(idempotencyKey, "idempotencyKey");
        }
    }

    record RefundResult(
            String providerRefundRef,
            long amountMinor,
            String currencyCode,
            RefundStatus status
    ) {
        public RefundResult {
            providerRefundRef = requireText(providerRefundRef, "providerRefundRef");
            if (amountMinor <= 0) {
                throw new IllegalArgumentException("amountMinor must be positive");
            }
            currencyCode = requireCurrency(currencyCode);
            Objects.requireNonNull(status, "status");
        }
    }

    /**
     * Signature-verified provider event metadata.
     *
     * <p>No tenant id is accepted from the provider payload at this boundary.
     * G05 resolves tenant/subscription/invoice only from trusted stored refs.</p>
     */
    record ProviderEventEnvelope(
            String providerEventId,
            String eventType,
            String providerPaymentRef,
            String payloadSha256
    ) {
        public ProviderEventEnvelope {
            providerEventId = requireText(providerEventId, "providerEventId");
            eventType = requireText(eventType, "eventType");
            providerPaymentRef = requireText(providerPaymentRef, "providerPaymentRef");
            payloadSha256 = requireText(payloadSha256, "payloadSha256");
            if (!payloadSha256.matches("^[0-9a-fA-F]{64}$")) {
                throw new IllegalArgumentException("payloadSha256 must be a SHA-256 hex digest");
            }
        }
    }

    enum PaymentStatus {
        CREATED,
        PENDING,
        SUCCEEDED,
        FAILED,
        CANCELLED,
        REFUNDED
    }

    enum RefundStatus {
        ACCEPTED,
        SUCCEEDED,
        FAILED
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }

    private static String requireCurrency(String value) {
        String currency = requireText(value, "currencyCode").toUpperCase(java.util.Locale.ROOT);
        if (!currency.matches("^[A-Z]{3}$")) {
            throw new IllegalArgumentException("currencyCode must be a three-letter ISO code");
        }
        return currency;
    }
}
