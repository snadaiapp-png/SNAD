package com.sanad.platform.commerce.application;

import com.sanad.platform.commerce.api.CommerceDtos.CartItemResponse;
import com.sanad.platform.commerce.api.CommerceDtos.CartResponse;
import com.sanad.platform.commerce.api.CommerceDtos.CheckoutRequest;
import com.sanad.platform.commerce.domain.CommerceDomain;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CheckoutIdempotencyFingerprintTest {

    @Test
    void fingerprint_isDeterministicForEquivalentMetadataAndSensitiveToBusinessPayload() {
        UUID tenantId = UUID.randomUUID();
        UUID storeId = UUID.randomUUID();
        UUID cartId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        Instant now = Instant.parse("2026-08-20T12:00:00Z");

        CartItemResponse item = new CartItemResponse(
                UUID.randomUUID(), cartId, productId, null, 2,
                new BigDecimal("100.00"), "SAR", new BigDecimal("200.00"), now, now);
        CartResponse cart = new CartResponse(
                cartId, tenantId, storeId, null, "SAR", CommerceDomain.CartStatus.ACTIVE,
                new BigDecimal("200.00"), now.plusSeconds(3600), List.of(item), 1L, now, now);

        Map<String, Object> metadataA = new LinkedHashMap<>();
        metadataA.put("channel", "WEB");
        metadataA.put("campaign", "AUG");
        Map<String, Object> metadataB = new LinkedHashMap<>();
        metadataB.put("campaign", "AUG");
        metadataB.put("channel", "WEB");

        CheckoutRequest requestA = new CheckoutRequest(
                cartId, "idem-1", "Customer@Example.COM ", "Customer", null, metadataA);
        CheckoutRequest requestEquivalent = new CheckoutRequest(
                cartId, "idem-1", "customer@example.com", "Customer", null, metadataB);
        CheckoutRequest changed = new CheckoutRequest(
                cartId, "idem-1", "customer@example.com", "Different Customer", null, metadataB);

        String fingerprintA = CheckoutService.computeIdempotencyFingerprint(tenantId, storeId, requestA, cart);
        String fingerprintEquivalent = CheckoutService.computeIdempotencyFingerprint(
                tenantId, storeId, requestEquivalent, cart);
        String fingerprintChanged = CheckoutService.computeIdempotencyFingerprint(
                tenantId, storeId, changed, cart);

        assertThat(fingerprintA).matches("[0-9a-f]{64}");
        assertThat(fingerprintEquivalent).isEqualTo(fingerprintA);
        assertThat(fingerprintChanged).isNotEqualTo(fingerprintA);
    }
}
