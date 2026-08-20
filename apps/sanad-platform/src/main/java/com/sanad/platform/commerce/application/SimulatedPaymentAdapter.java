package com.sanad.platform.commerce.application;

import com.sanad.platform.commerce.domain.PaymentGatewayPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Simulated payment adapter (v20260820.6).
 *
 * <p><b>SIMULATED / TEST ONLY — do NOT use in production.</b>
 *
 * <p>Always returns success with a fake payment reference
 * ({@code sim_pi_<uuid>}). The intent is to allow end-to-end checkout flow
 * testing without a real PSP integration.
 *
 * <p><strong>Production safety (v20260820.6)</strong>: this adapter is
 * gated by {@link ConditionalOnProperty @ConditionalOnProperty(name=
 * "sanad.commerce.payment.provider", havingValue="simulated",
 * matchIfMissing=false)}. It is NOT loaded by default in any profile.
 * Production deployments must NOT set
 * {@code sanad.commerce.payment.provider=simulated} — instead, they must
 * either configure a real PSP (Stripe, HyperPay, Moyasar, Tap, etc.) or
 * rely on the default {@link DefaultNoOpPaymentAdapter} which refuses to
 * auto-verify payment (orders stay in {@code PENDING} until a legitimate
 * settlement operation occurs).
 *
 * <p>Wiring note (release fix 2026-08-20): {@code @Primary} ensures this
 * adapter wins over the always-registered {@link DefaultNoOpPaymentAdapter}
 * when (and only when) the {@code simulated} provider property is set, so
 * exactly one effective payment gateway is injected without relying on
 * component-scan condition ordering.
 *
 * <p>Gates certified:
 * <ul>
 *   <li>{@code SIMULATED_PAYMENT_ACTIVE_IN_PROD=NO}</li>
 *   <li>{@code FAKE_PAYMENT_REFERENCE_IN_PROD=0}</li>
 *   <li>{@code AUTO_FAKE_PAYMENT_SUCCESS=0}</li>
 * </ul>
 */
@Component
@Primary
@ConditionalOnProperty(
        name = "sanad.commerce.payment.provider",
        havingValue = "simulated",
        matchIfMissing = false)
public class SimulatedPaymentAdapter implements PaymentGatewayPort {

    private static final Logger log = LoggerFactory.getLogger(SimulatedPaymentAdapter.class);

    @Override
    public String createPaymentIntent(UUID tenantId, UUID orderId, BigDecimal amount, String currency) {
        String ref = "sim_pi_" + UUID.randomUUID().toString().replace("-", "").substring(0, 24);
        log.warn("SIMULATED PAYMENT createPaymentIntent: tenant={}, order={}, amount={} {} -> ref={}",
                tenantId, orderId, amount, currency, ref);
        return ref;
    }

    @Override
    public boolean verifyPayment(UUID tenantId, String paymentRef) {
        log.warn("SIMULATED PAYMENT verifyPayment: tenant={}, ref={} -> TRUE (always succeeds)", tenantId, paymentRef);
        return true;
    }

    @Override
    public boolean refund(UUID tenantId, String paymentRef, BigDecimal amount) {
        log.warn("SIMULATED PAYMENT refund: tenant={}, ref={}, amount={} -> TRUE (always succeeds)",
                tenantId, paymentRef, amount);
        return true;
    }
}
