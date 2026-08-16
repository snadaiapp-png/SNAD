package com.sanad.platform.commerce.domain;

import java.util.UUID;

/**
 * Inventory availability port (v20260816.5).
 *
 * <p>Decouples commerce checkout / cart operations from the underlying
 * inventory system. The default {@code SimpleInventoryAdapter} returns
 * unlimited stock for demo / test deployments. A production deployment
 * would provide a real implementation backed by ERP / WMS / PIM.
 *
 * <p>Implementations MUST be tenant-scoped — every method takes a
 * {@code tenantId} and MUST NOT leak stock across tenants.
 */
public interface InventoryAvailabilityPort {

    /**
     * Return available quantity for a product (or variant if provided).
     * Returns {@link Integer#MAX_VALUE} if the product is unlimited / digital.
     */
    int getAvailability(UUID tenantId, UUID productId, UUID variantId);

    /**
     * Reserve {@code quantity} units. Returns {@code true} if the reservation
     * succeeded (i.e. sufficient stock was available), {@code false} otherwise.
     */
    boolean reserve(UUID tenantId, UUID productId, UUID variantId, int quantity);

    /**
     * Release a previously reserved quantity back to the pool (e.g. on cart
     * expiry / abandonment / cancellation).
     */
    void release(UUID tenantId, UUID productId, UUID variantId, int quantity);

    /**
     * Convert a reservation into a confirmed stock decrement (called when an
     * order is paid / confirmed).
     */
    void confirm(UUID tenantId, UUID productId, UUID variantId, int quantity);
}
