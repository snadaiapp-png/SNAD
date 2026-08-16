package com.sanad.platform.commerce.application;

import com.sanad.platform.commerce.domain.PaymentGatewayPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Default {@link PaymentGatewayPort} (v20260816.5).
 *
 * <p><b>SIMULATED / TEST ONLY — do NOT use in production.</b>
 *
 * <p>Always returns success with a fake payment reference
 * ({@code sim_pi_<uuid>}). The intent is to allow end-to-end checkout flow
 * testing without a real PSP integration. Wire a real payment gateway
 * (Stripe, HyperPay, Moyasar, Tap, etc.) before going live.
 */
@Component
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
