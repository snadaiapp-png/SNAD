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
 * ERP-backed Commerce inventory provider.
 *
 * <p>PHYSICAL and BUNDLE products are stock-controlled and therefore fail
 * closed when the ERP item mapping, active warehouse, reservation, or stock
 * mutation cannot be completed. DIGITAL and SERVICE products explicitly
 * bypass stock operations.
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
        if (!isStockControlled(tenantId, productId)) {
            return Integer.MAX_VALUE;
        }
        UUID itemId = requireItemId(tenantId, productId, variantId);
        UUID warehouseId = requireDefaultWarehouse(tenantId);
        try {
            BigDecimal available = jdbc.queryForObject(
                    "SELECT on_hand - reserved FROM erp_inventory_balances "
                            + "WHERE tenant_id = ? AND warehouse_id = ? AND item_id = ?",
                    BigDecimal.class, tenantId, warehouseId, itemId);
            if (available == null) return 0;
            return Math.max(available.intValue(), 0);
        } catch (EmptyResultDataAccessException e) {
            return 0;
        }
    }

    @Override
    public boolean reserve(UUID tenantId, UUID productId, UUID variantId, int quantity) {
        if (!isStockControlled(tenantId, productId)) {
            return true;
        }
        UUID itemId = requireItemId(tenantId, productId, variantId);
        UUID warehouseId = requireDefaultWarehouse(tenantId);
        String externalRef = externalReference(productId, variantId);
        ReservationResponse reservation = reservationService.reserve(
                tenantId, warehouseId, itemId, BigDecimal.valueOf(quantity),
                "COMMERCE", externalRef, null);
        if (reservation == null) {
            throw new IllegalStateException(
                    "ERP reservation failed for product " + productId + " in tenant " + tenantId);
        }
        return true;
    }

    @Override
    public void release(UUID tenantId, UUID productId, UUID variantId, int quantity) {
        if (!isStockControlled(tenantId, productId)) {
            return;
        }
        requireItemId(tenantId, productId, variantId);
        requireDefaultWarehouse(tenantId);
        String externalRef = externalReference(productId, variantId);
        try {
            ReservationResponse existing = findReservation(tenantId, externalRef,
                    "status IN ('PENDING','RESERVED')");
            if (existing != null) {
                reservationService.release(tenantId, existing.id(), null);
            }
        } catch (EmptyResultDataAccessException e) {
            // Idempotent release: there is no active reservation left to release.
            log.debug("release: no reservation found for externalRef={}", externalRef);
        }
    }

    @Override
    public void confirm(UUID tenantId, UUID productId, UUID variantId, int quantity) {
        if (!isStockControlled(tenantId, productId)) {
            return;
        }
        UUID itemId = requireItemId(tenantId, productId, variantId);
        UUID warehouseId = requireDefaultWarehouse(tenantId);
        String externalRef = externalReference(productId, variantId);
        try {
            ReservationResponse existing = findReservation(tenantId, externalRef,
                    "status = 'RESERVED'");
            if (existing != null) {
                reservationService.confirm(tenantId, existing.id(), null);
                return;
            }
        } catch (EmptyResultDataAccessException e) {
            log.debug("confirm: no reservation found for externalRef={} — applying direct fulfillment", externalRef);
        }

        // A direct fulfillment is allowed when there was no prior reservation,
        // but failure must propagate so the surrounding Commerce transaction
        // rolls back instead of marking a physical order paid without stock.
        inventoryService.adjustStock(tenantId, warehouseId, itemId,
                BigDecimal.valueOf(quantity),
                com.sanad.platform.erp.domain.ErpDomain.MovementType.FULFILLMENT,
                "COMMERCE_CONFIRM:" + externalRef, null, null);
    }

    boolean isStockControlled(UUID tenantId, UUID productId) {
        if (productId == null) {
            throw new IllegalStateException("productId is required for ERP inventory");
        }
        final String productType;
        try {
            productType = jdbc.queryForObject(
                    "SELECT product_type FROM commerce_products WHERE tenant_id = ? AND id = ?",
                    String.class, tenantId, productId);
        } catch (EmptyResultDataAccessException e) {
            throw new IllegalStateException(
                    "Commerce product not found for ERP inventory: " + productId, e);
        }
        if (productType == null) {
            throw new IllegalStateException("Commerce product_type is missing for product " + productId);
        }
        return switch (productType.toUpperCase(java.util.Locale.ROOT)) {
            case "DIGITAL", "SERVICE" -> false;
            case "PHYSICAL", "BUNDLE" -> true;
            default -> throw new IllegalStateException(
                    "Unsupported commerce product_type for ERP inventory: " + productType);
        };
    }

    private UUID requireItemId(UUID tenantId, UUID productId, UUID variantId) {
        UUID itemId = resolveItemId(tenantId, productId, variantId);
        if (itemId == null) {
            throw new IllegalStateException(
                    "ERP item mapping is required for stock-controlled product " + productId
                            + (variantId != null ? " variant " + variantId : ""));
        }
        return itemId;
    }

    private UUID requireDefaultWarehouse(UUID tenantId) {
        UUID warehouseId = resolveDefaultWarehouse(tenantId);
        if (warehouseId == null) {
            throw new IllegalStateException(
                    "An ACTIVE ERP warehouse is required for tenant " + tenantId);
        }
        return warehouseId;
    }

    private ReservationResponse findReservation(UUID tenantId, String externalRef, String statusPredicate) {
        return jdbc.queryForObject(
                "SELECT * FROM erp_inventory_reservations WHERE tenant_id = ? AND external_reference = ? "
                        + "AND " + statusPredicate + " ORDER BY created_at DESC LIMIT 1",
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
    }

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
