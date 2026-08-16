package com.sanad.platform.commerce.application;

import com.sanad.platform.commerce.domain.InventoryAvailabilityPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Default {@link InventoryAvailabilityPort} (v20260816.5).
 *
 * <p>Returns {@link Integer#MAX_VALUE} for every product (i.e. unlimited
 * stock) and no-ops on reserve / release / confirm. Suitable for demo
 * deployments and for stores that sell digital / service products with no
 * inventory constraints.
 *
 * <p>A production deployment should provide a real implementation backed by
 * ERP / WMS / PIM inventory tables.
 */
@Component
public class SimpleInventoryAdapter implements InventoryAvailabilityPort {

    private static final Logger log = LoggerFactory.getLogger(SimpleInventoryAdapter.class);

    @Override
    public int getAvailability(UUID tenantId, UUID productId, UUID variantId) {
        return Integer.MAX_VALUE;
    }

    @Override
    public boolean reserve(UUID tenantId, UUID productId, UUID variantId, int quantity) {
        log.debug("reserve (no-op): tenant={}, product={}, variant={}, qty={}", tenantId, productId, variantId, quantity);
        return true;
    }

    @Override
    public void release(UUID tenantId, UUID productId, UUID variantId, int quantity) {
        log.debug("release (no-op): tenant={}, product={}, variant={}, qty={}", tenantId, productId, variantId, quantity);
    }

    @Override
    public void confirm(UUID tenantId, UUID productId, UUID variantId, int quantity) {
        log.debug("confirm (no-op): tenant={}, product={}, variant={}, qty={}", tenantId, productId, variantId, quantity);
    }
}
