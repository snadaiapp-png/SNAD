package com.sanad.platform.subscription.billing.infrastructure;

import com.sanad.platform.subscription.billing.domain.BillingPaymentProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Production-safe default R0C13 provider adapter.
 *
 * <p>DISABLED means exactly that: no provider customer creation, payment
 * intent, state query, refund, or event verification is permitted.</p>
 */
@Component
@ConditionalOnProperty(
        name = "sanad.subscription.billing.provider.mode",
        havingValue = "DISABLED",
        matchIfMissing = false)
public class DisabledBillingPaymentProvider implements BillingPaymentProvider {

    @Override
    public ProviderCustomer ensureProviderCustomer(EnsureCustomerCommand command) {
        throw disabled();
    }

    @Override
    public PaymentIntent createPaymentIntent(CreatePaymentIntentCommand command) {
        throw disabled();
    }

    @Override
    public PaymentState queryPaymentState(QueryPaymentStateQuery query) {
        throw disabled();
    }

    @Override
    public RefundResult requestRefund(RefundCommand command) {
        throw disabled();
    }

    @Override
    public ProviderEventEnvelope verifyAndParseEvent(byte[] payload, String signatureHeader) {
        throw disabled();
    }

    private static IllegalStateException disabled() {
        return new IllegalStateException(
                "R0C13 billing payment provider mode is DISABLED; external payment operations are unavailable");
    }
}
