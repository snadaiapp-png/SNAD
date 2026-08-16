package com.sanad.platform.commerce.domain;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Payment gateway port (v20260816.5).
 *
 * <p><b>SIMULATED / TEST ONLY</b> — the default {@code SimulatedPaymentAdapter}
 * always returns success with a fake payment reference. This port exists so
 * that the commerce checkout flow can be exercised end-to-end without a real
 * PSP integration. DO NOT use the default adapter in production — wire a real
 * implementation (e.g. Stripe, HyperPay, Moyasar, Tap) instead.
 */
public interface PaymentGatewayPort {

    /**
     * Create a payment intent for an order and return the payment reference
     * (e.g. {@code pi_3O...} or an internal reference). The reference will be
     * stored on the order and used by {@link #verifyPayment(UUID, String)}.
     *
     * @param tenantId the tenant that owns the order
     * @param orderId  the order being paid
     * @param amount   the amount to charge (in major currency units)
     * @param currency the ISO-4217 currency code (e.g. {@code SAR})
     * @return a payment reference (never null)
     */
    String createPaymentIntent(UUID tenantId, UUID orderId, BigDecimal amount, String currency);

    /**
     * Verify that a payment reference has been settled successfully.
     *
     * @param tenantId    the tenant that owns the order
     * @param paymentRef  the reference returned by {@link #createPaymentIntent}
     * @return {@code true} if the payment is captured / verified
     */
    boolean verifyPayment(UUID tenantId, String paymentRef);

    /**
     * Refund (partially or fully) the captured payment.
     *
     * @param tenantId    the tenant that owns the order
     * @param paymentRef  the reference returned by {@link #createPaymentIntent}
     * @param amount      the amount to refund (must be {@code >= 0} and {@code <=} captured)
     * @return {@code true} if the refund was accepted by the gateway
     */
    boolean refund(UUID tenantId, String paymentRef, BigDecimal amount);
}
