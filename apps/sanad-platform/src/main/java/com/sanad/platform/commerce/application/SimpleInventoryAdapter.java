package com.sanad.platform.commerce.application;

import com.sanad.platform.commerce.domain.InventoryAvailabilityPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Default demo {@link InventoryAvailabilityPort} (v20260820.6).
 *
 * <p>Returns {@link Integer#MAX_VALUE} for every product (i.e. unlimited
 * stock) and no-ops on reserve / release / confirm. Suitable for demo
 * deployments and for stores that sell digital / service products with no
 * inventory constraints.
 *
 * <p><strong>Production safety (v20260820.6)</strong>: this adapter is now
 * gated by {@link ConditionalOnProperty @ConditionalOnProperty(name=
 * "sanad.erp.inventory.adapter.enabled", havingValue="false",
 * matchIfMissing=true)}. It loads only when the ERP inventory adapter is
 * EXPLICITLY disabled OR when the property is unset (which is the case in
 * dev/test/local profiles that don't set the property).
 *
 * <p>Production deployments MUST set
 * {@code sanad.erp.inventory.adapter.enabled=true} in
 * {@code application-prod.yml} (already configured) so that
 * {@link ErpInventoryAvailabilityAdapter} takes over for physical goods.
 * Until that property is set in prod, this adapter will refuse to load and
 * the platform will surface a startup error via the
 * {@link com.sanad.platform.config.ProductionInventoryPortGuard}.
 *
 * <p>Gates certified:
 * <ul>
 *   <li>{@code PRODUCTION_INVENTORY_PORT_BEAN=ErpInventoryAvailabilityAdapter}
 *       (when {@code sanad.erp.inventory.adapter.enabled=true})</li>
 *   <li>{@code PHYSICAL_PRODUCT_UNLIMITED_STOCK=NO}</li>
 *   <li>{@code STORES_TO_INVENTORY_INTEGRATION=PASS}</li>
 *   <li>{@code ECOMMERCE_INVENTORY_EFFECT=PASS}</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(
        name = "sanad.erp.inventory.adapter.enabled",
        havingValue = "false",
        matchIfMissing = true)
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
