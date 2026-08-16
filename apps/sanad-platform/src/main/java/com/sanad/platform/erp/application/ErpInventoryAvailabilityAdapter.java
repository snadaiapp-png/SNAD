package com.sanad.platform.erp.application;

import com.sanad.platform.commerce.domain.InventoryAvailabilityPort;
import com.sanad.platform.erp.api.ErpDtos.ReservationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * ERP-backed inventory provider (v20260816.7).
 *
 * <p><b>ERP-backed inventory provider — replaces {@link SimpleInventoryAdapter}
 * when ERP is active.</b>
 *
 * <p>Implements the existing {@link InventoryAvailabilityPort} contract used by
 * the Commerce platform's cart / checkout flow. Activated only when
 * {@code sanad.erp.inventory.adapter.enabled=true} (default {@code false});
 * when active, it is marked {@link Primary @Primary} so it takes precedence
 * over the simulated {@code SimpleInventoryAdapter}.
 *
 * <p>Maps commerce product/variant to ERP item by SKU:
 * <ol>
 *   <li>If a {@code variantId} is supplied, look up
 *       {@code commerce_product_variants.sku}.</li>
 *   <li>Otherwise fall back to {@code commerce_products.sku}.</li>
 *   <li>Look up {@code erp_items.sku = resolved_sku}.</li>
 * </ol>
 *
 * <p>Warehouse selection: prefers the tenant's primary warehouse
 * ({@code erp_warehouses.is_primary = TRUE}); falls back to the first ACTIVE
 * warehouse. If no warehouse is configured, returns 0 availability.
 *
 * <p>All reservations created by this adapter are tagged with
 * {@code source = "COMMERCE"} and an external reference derived from the
 * commerce product/variant IDs, so they can be cross-referenced from the
 * commerce side. The adapter uses {@code @Lazy} on ERP service dependencies
 * to avoid circular dependency instantiation order issues.
 */
@Component
@Primary
@ConditionalOnProperty(name = "sanad.erp.inventory.adapter.enabled", havingValue = "true")
public class ErpInventoryAvailabilityAdapter implements InventoryAvailabilityPort {

    private static final Logger log = LoggerFactory.getLogger(ErpInventoryAvailabilityAdapter.class);

    private final JdbcTemplate jdbc;
    private final ErpInventoryService inventoryService;
    private final ErpInventoryReservationService reservationService;

    public ErpInventoryAvailabilityAdapter(JdbcTemplate jdbc,
                                              @Lazy ErpInventoryService inventoryService,
                                              @Lazy ErpInventoryReservationService reservationService) {
        this.jdbc = jdbc;
        this.inventoryService = inventoryService;
        this.reservationService = reservationService;
    }

    @Override
    public int getAvailability(UUID tenantId, UUID productId, UUID variantId) {
        UUID itemId = resolveItemId(tenantId, productId, variantId);
        if (itemId == null) {
            log.debug("getAvailability: no ERP item mapping for product={} variant={}", productId, variantId);
            return 0;
        }
        UUID warehouseId = resolveDefaultWarehouse(tenantId);
        if (warehouseId == null) return 0;
        try {
            BigDecimal available = jdbc.queryForObject(
                    "SELECT on_hand - reserved FROM erp_inventory_balances "
                            + "WHERE tenant_id = ? AND warehouse_id = ? AND item_id = ?",
                    BigDecimal.class, tenantId, warehouseId, itemId);
            if (available == null) return 0;
            int v = available.intValue();
            return Math.max(v, 0);
        } catch (EmptyResultDataAccessException e) {
            return 0;
        }
    }

    @Override
    public boolean reserve(UUID tenantId, UUID productId, UUID variantId, int quantity) {
        UUID itemId = resolveItemId(tenantId, productId, variantId);
        if (itemId == null) {
            log.warn("reserve: no ERP item mapping — falling back to no-op (product={} variant={})", productId, variantId);
            return true; // behave like SimpleInventoryAdapter when no mapping
        }
        UUID warehouseId = resolveDefaultWarehouse(tenantId);
        if (warehouseId == null) {
            log.warn("reserve: no default warehouse for tenant — falling back to no-op");
            return true;
        }
        String externalRef = externalReference(productId, variantId);
        try {
            ReservationResponse r = reservationService.reserve(tenantId, warehouseId, itemId,
                    BigDecimal.valueOf(quantity), "COMMERCE", externalRef, null);
            return r != null;
        } catch (Exception e) {
            log.warn("reserve failed: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public void release(UUID tenantId, UUID productId, UUID variantId, int quantity) {
        UUID itemId = resolveItemId(tenantId, productId, variantId);
        if (itemId == null) return;
        UUID warehouseId = resolveDefaultWarehouse(tenantId);
        if (warehouseId == null) return;
        String externalRef = externalReference(productId, variantId);
        try {
            ReservationResponse existing = jdbc.queryForObject(
                    "SELECT * FROM erp_inventory_reservations WHERE tenant_id = ? AND external_reference = ? "
                            + "AND status IN ('PENDING','RESERVED') ORDER BY created_at DESC LIMIT 1",
                    (rs, rowNum) -> new ReservationResponse(
                            rs.getObject("id", UUID.class),
                            rs.getObject("tenant_id", UUID.class),
                            rs.getObject("warehouse_id", UUID.class),
                            rs.getObject("item_id", UUID.class),
                            rs.getBigDecimal("quantity"),
                            rs.getString("source"),
                            rs.getString("external_reference"),
                            com.sanad.platform.erp.domain.ErpDomain.ReservationStatus
                                    .valueOf(rs.getString("status")),
                            null, rs.getLong("version"),
                            rs.getObject("created_at", java.sql.Timestamp.class).toInstant(),
                            rs.getObject("updated_at", java.sql.Timestamp.class).toInstant()),
                    tenantId, externalRef);
            if (existing != null) {
                reservationService.release(tenantId, existing.id(), null);
            }
        } catch (EmptyResultDataAccessException e) {
            log.debug("release: no reservation found for externalRef={}", externalRef);
        } catch (Exception e) {
            log.warn("release failed: {}", e.getMessage());
        }
    }

    @Override
    public void confirm(UUID tenantId, UUID productId, UUID variantId, int quantity) {
        UUID itemId = resolveItemId(tenantId, productId, variantId);
        if (itemId == null) return;
        UUID warehouseId = resolveDefaultWarehouse(tenantId);
        if (warehouseId == null) return;
        String externalRef = externalReference(productId, variantId);
        try {
            ReservationResponse existing = jdbc.queryForObject(
                    "SELECT * FROM erp_inventory_reservations WHERE tenant_id = ? AND external_reference = ? "
                            + "AND status = 'RESERVED' ORDER BY created_at DESC LIMIT 1",
                    (rs, rowNum) -> new ReservationResponse(
                            rs.getObject("id", UUID.class),
                            rs.getObject("tenant_id", UUID.class),
                            rs.getObject("warehouse_id", UUID.class),
                            rs.getObject("item_id", UUID.class),
                            rs.getBigDecimal("quantity"),
                            rs.getString("source"),
                            rs.getString("external_reference"),
                            com.sanad.platform.erp.domain.ErpDomain.ReservationStatus
                                    .valueOf(rs.getString("status")),
                            null, rs.getLong("version"),
                            rs.getObject("created_at", java.sql.Timestamp.class).toInstant(),
                            rs.getObject("updated_at", java.sql.Timestamp.class).toInstant()),
                    tenantId, externalRef);
            if (existing != null) {
                reservationService.confirm(tenantId, existing.id(), null);
            }
        } catch (EmptyResultDataAccessException e) {
            log.debug("confirm: no reservation found for externalRef={} — applying direct fulfillment", externalRef);
            // No prior reservation — apply direct fulfillment (issue movement)
            try {
                inventoryService.adjustStock(tenantId, warehouseId, itemId,
                        BigDecimal.valueOf(quantity),
                        com.sanad.platform.erp.domain.ErpDomain.MovementType.FULFILLMENT,
                        "COMMERCE_CONFIRM:" + externalRef, null, null);
            } catch (Exception ex) {
                log.warn("confirm direct fulfillment failed: {}", ex.getMessage());
            }
        } catch (Exception e) {
            log.warn("confirm failed: {}", e.getMessage());
        }
    }

    // ===== Helpers =====
    private UUID resolveItemId(UUID tenantId, UUID productId, UUID variantId) {
        if (productId == null) return null;
        String sku = null;
        if (variantId != null) {
            try {
                sku = jdbc.queryForObject(
                        "SELECT sku FROM commerce_product_variants WHERE tenant_id = ? AND id = ?",
                        String.class, tenantId, variantId);
            } catch (EmptyResultDataAccessException ignored) {}
        }
        if (sku == null || sku.isBlank()) {
            try {
                sku = jdbc.queryForObject(
                        "SELECT sku FROM commerce_products WHERE tenant_id = ? AND id = ?",
                        String.class, tenantId, productId);
            } catch (EmptyResultDataAccessException ignored) {}
        }
        if (sku == null || sku.isBlank()) return null;
        try {
            return jdbc.queryForObject(
                    "SELECT id FROM erp_items WHERE tenant_id = ? AND sku = ? LIMIT 1",
                    UUID.class, tenantId, sku);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    private UUID resolveDefaultWarehouse(UUID tenantId) {
        try {
            return jdbc.queryForObject(
                    "SELECT id FROM erp_warehouses WHERE tenant_id = ? AND status = 'ACTIVE' "
                            + "ORDER BY is_primary DESC, created_at ASC LIMIT 1",
                    UUID.class, tenantId);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    private String externalReference(UUID productId, UUID variantId) {
        return "COMMERCE:product=" + productId
                + (variantId != null ? ",variant=" + variantId : "");
    }
}
