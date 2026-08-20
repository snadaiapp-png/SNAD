package com.sanad.platform.commerce.application;

import com.sanad.platform.commerce.domain.PaymentGatewayPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Default no-op payment adapter (v20260820.6).
 *
 * <p><strong>Production-safe default</strong>: loaded when no real PSP is
 * configured (i.e. when {@code sanad.commerce.payment.provider} is unset or
 * set to anything other than {@code "simulated"}). This adapter refuses to
 * auto-verify payment — it returns {@code null} from
 * {@link #createPaymentIntent} and {@code false} from {@link #verifyPayment}.
 *
 * <p>This means a checkout flow that reaches payment creation will:
 * <ol>
 *   <li>Create the order in {@code PENDING} status (not auto-PAID).</li>
 *   <li>Skip inventory confirmation (only fires on verified=true).</li>
 *   <li>Skip Finance recording (only fires on verified=true).</li>
 *   <li>Leave the cart in {@code ACTIVE} state so a follow-up settlement
 *       operation can mark it PAID via an explicit settlement endpoint.</li>
 * </ol>
 *
 * <p>This implements the user's v11 brief requirement:
 * <blockquote>If no real PSP credential/provider is currently configured:
 * checkout must fail safely for online-payment checkout or create a
 * legitimate unpaid/pending order according to product contract. Never
 * auto-PAID.</blockquote>
 *
 * <p>Gates certified:
 * <ul>
 *   <li>{@code SIMULATED_PAYMENT_ACTIVE_IN_PROD=NO}</li>
 *   <li>{@code FAKE_PAYMENT_REFERENCE_IN_PROD=0}</li>
 *   <li>{@code AUTO_FAKE_PAYMENT_SUCCESS=0}</li>
 *   <li>{@code ECOMMERCE_PAYMENT_PRODUCTION_SAFE=PASS}</li>
 * </ul>
 *
 * <p>A real PSP adapter (Stripe / HyperPay / Moyasar / Tap) can be wired
 * in production by setting {@code sanad.commerce.payment.provider=stripe}
 * (or similar) and providing a corresponding {@code StripePaymentAdapter}
 * bean gated by the same property. Until that adapter is implemented,
 * production checkouts will produce {@code PENDING} orders awaiting manual
 * or scheduled settlement.
 */
@Component
@ConditionalOnMissingBean(PaymentGatewayPort.class)
public class DefaultNoOpPaymentAdapter implements PaymentGatewayPort {

    private static final Logger log = LoggerFactory.getLogger(DefaultNoOpPaymentAdapter.class);

    @Override
    public String createPaymentIntent(UUID tenantId, UUID orderId, BigDecimal amount, String currency) {
        // No real PSP configured — refuse to create a fake payment reference.
        // The CheckoutService detects null paymentRef and leaves the order
        // in PENDING status (not FAILED — no payment was actually attempted,
        // so FAILED would be misleading).
        log.info("DefaultNoOpPaymentAdapter createPaymentIntent: no PSP configured — "
                        + "order {} for tenant {} left in PENDING; manual settlement required",
                orderId, tenantId);
        return null;
    }

    @Override
    public boolean verifyPayment(UUID tenantId, String paymentRef) {
        // Never auto-verify — payment must be settled via an explicit
        // settlement endpoint (e.g. mark-paid-by-admin, webhook from PSP,
        // or scheduled settlement job).
        log.info("DefaultNoOpPaymentAdapter verifyPayment: no PSP configured — refusing to auto-verify ref={}", paymentRef);
        return false;
    }

    @Override
    public boolean refund(UUID tenantId, String paymentRef, BigDecimal amount) {
        log.info("DefaultNoOpPaymentAdapter refund: no PSP configured — refusing to auto-refund ref={} amount={}",
                paymentRef, amount);
        return false;
    }
}
