package com.sanad.platform.commerce.application;

import com.sanad.platform.admin.service.PlatformAuditService;
import com.sanad.platform.commerce.api.CommerceDtos.*;
import com.sanad.platform.commerce.domain.CommerceDomain;
import com.sanad.platform.commerce.domain.InventoryAvailabilityPort;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Cart application service (v20260816.5).
 *
 * <p>Tenant + store-scoped cart lifecycle: create / addItem / updateItem /
 * removeItem / clear / calculateTotals.
 *
 * <p>The cart stores a <b>price snapshot</b> ({@code unit_price} +
 * {@code line_total}) on each cart item at the moment it is added — so that
 * later price changes do not retroactively alter the cart. Totals are
 * recomputed from the items on every mutation.
 *
 * <p>Stock is validated against {@link InventoryAvailabilityPort} when an
 * item is added (best-effort — the default adapter returns unlimited stock).
 */
@Service
public class CartService {

    private final JdbcTemplate jdbc;
    private final PlatformAuditService auditService;
    private final ProductService productService;
    private final InventoryAvailabilityPort inventory;

    public CartService(JdbcTemplate jdbc, PlatformAuditService auditService,
                       ProductService productService, InventoryAvailabilityPort inventory) {
        this.jdbc = jdbc;
        this.auditService = auditService;
        this.productService = productService;
        this.inventory = inventory;
    }

    @Transactional
    public CartResponse create(UUID tenantId, UUID storeId, CreateCartRequest request, Authentication auth) {
        ensureStore(tenantId, storeId);
        String currency = (request != null && request.currency() != null && !request.currency().isBlank())
                ? request.currency() : defaultCurrencyFor(tenantId, storeId);
        String customerRef = request != null ? request.customerRef() : null;
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        // Carts expire 7 days after creation by default
        Instant expiresAt = now.plus(java.time.Duration.ofDays(7));
        jdbc.update("INSERT INTO commerce_carts (id, tenant_id, store_id, customer_ref, currency, status, "
                        + "subtotal, expires_at, version, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, 'ACTIVE', 0, ?, 0, ?, ?)",
                id, tenantId, storeId, customerRef, currency,
                Timestamp.from(expiresAt), Timestamp.from(now), Timestamp.from(now));
        audit(tenantId, auth, "CART.CREATED", id, "store=" + storeId);
        return getOrThrow(tenantId, storeId, id);
    }

    @Transactional
    public CartResponse addItem(UUID tenantId, UUID storeId, UUID cartId,
                                  AddCartItemRequest request, Authentication auth) {
        CartResponse cart = getOrThrow(tenantId, storeId, cartId);
        ensureCartActive(cart);
        if (request == null || request.productId() == null || request.quantity() <= 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "productId and positive quantity are required");

        // Snapshot the price from the most relevant ACTIVE price row
        BigDecimal unitPrice = resolveActivePrice(tenantId, storeId, request.productId(), request.variantId());

        // Stock check (best-effort)
        int available = inventory.getAvailability(tenantId, request.productId(), request.variantId());
        if (available < request.quantity()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "insufficient stock: requested " + request.quantity() + ", available " + available);
        }
        inventory.reserve(tenantId, request.productId(), request.variantId(), request.quantity());

        UUID itemId = UUID.randomUUID();
        Instant now = Instant.now();
        BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(request.quantity()));
        jdbc.update("INSERT INTO commerce_cart_items (id, tenant_id, cart_id, product_id, variant_id, "
                        + "quantity, unit_price, currency, line_total, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                itemId, tenantId, cartId, request.productId(), request.variantId(),
                request.quantity(), unitPrice, cart.currency(), lineTotal,
                Timestamp.from(now), Timestamp.from(now));
        recalculateTotals(tenantId, cartId);
        audit(tenantId, auth, "CART.ITEM_ADDED", cartId, "item=" + itemId);
        return getOrThrow(tenantId, storeId, cartId);
    }

    @Transactional
    public CartResponse updateItem(UUID tenantId, UUID storeId, UUID cartId, UUID itemId,
                                     UpdateCartItemRequest request, Authentication auth) {
        CartResponse cart = getOrThrow(tenantId, storeId, cartId);
        ensureCartActive(cart);
        if (request == null || request.quantity() <= 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "positive quantity is required");
        CartItemResponse item = getItemOrThrow(tenantId, cartId, itemId);
        int delta = request.quantity() - item.quantity();
        if (delta > 0) {
            int available = inventory.getAvailability(tenantId, item.productId(), item.variantId());
            if (available < delta) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "insufficient stock for quantity increase");
            }
            inventory.reserve(tenantId, item.productId(), item.variantId(), delta);
        } else if (delta < 0) {
            inventory.release(tenantId, item.productId(), item.variantId(), -delta);
        }
        Instant now = Instant.now();
        BigDecimal newLineTotal = item.unitPrice().multiply(BigDecimal.valueOf(request.quantity()));
        jdbc.update("UPDATE commerce_cart_items SET quantity = ?, line_total = ?, updated_at = ? "
                        + "WHERE tenant_id = ? AND id = ?", request.quantity(), newLineTotal, Timestamp.from(now), tenantId, itemId);
        recalculateTotals(tenantId, cartId);
        audit(tenantId, auth, "CART.ITEM_UPDATED", cartId, "item=" + itemId + ",qty=" + request.quantity());
        return getOrThrow(tenantId, storeId, cartId);
    }

    @Transactional
    public CartResponse removeItem(UUID tenantId, UUID storeId, UUID cartId, UUID itemId, Authentication auth) {
        CartResponse cart = getOrThrow(tenantId, storeId, cartId);
        ensureCartActive(cart);
        CartItemResponse item = getItemOrThrow(tenantId, cartId, itemId);
        inventory.release(tenantId, item.productId(), item.variantId(), item.quantity());
        jdbc.update("DELETE FROM commerce_cart_items WHERE tenant_id = ? AND id = ?", tenantId, itemId);
        recalculateTotals(tenantId, cartId);
        audit(tenantId, auth, "CART.ITEM_REMOVED", cartId, "item=" + itemId);
        return getOrThrow(tenantId, storeId, cartId);
    }

    @Transactional
    public CartResponse clear(UUID tenantId, UUID storeId, UUID cartId, Authentication auth) {
        CartResponse cart = getOrThrow(tenantId, storeId, cartId);
        ensureCartActive(cart);
        for (CartItemResponse item : cart.items()) {
            try {
                inventory.release(tenantId, item.productId(), item.variantId(), item.quantity());
            } catch (Exception ignored) {}
        }
        jdbc.update("DELETE FROM commerce_cart_items WHERE tenant_id = ? AND cart_id = ?", tenantId, cartId);
        recalculateTotals(tenantId, cartId);
        audit(tenantId, auth, "CART.CLEARED", cartId, "store=" + storeId);
        return getOrThrow(tenantId, storeId, cartId);
    }

    @Transactional(readOnly = true)
    public CartResponse calculateTotals(UUID tenantId, UUID storeId, UUID cartId) {
        return getOrThrow(tenantId, storeId, cartId);
    }

    // ===== Helpers =====
    void recalculateTotals(UUID tenantId, UUID cartId) {
        BigDecimal subtotal = jdbc.query(
                "SELECT COALESCE(SUM(line_total), 0) FROM commerce_cart_items WHERE tenant_id = ? AND cart_id = ?",
                (rs) -> { rs.next(); return rs.getBigDecimal(1); },
                tenantId, cartId);
        if (subtotal == null) subtotal = BigDecimal.ZERO;
        jdbc.update("UPDATE commerce_carts SET subtotal = ?, updated_at = ?, version = version + 1 "
                        + "WHERE tenant_id = ? AND id = ?", subtotal, Timestamp.from(Instant.now()), tenantId, cartId);
    }

    private BigDecimal resolveActivePrice(UUID tenantId, UUID storeId, UUID productId, UUID variantId) {
        try {
            // Prefer the price for the specific variant; fall back to product-level price
            if (variantId != null) {
                BigDecimal v = jdbc.queryForObject(
                        "SELECT amount FROM commerce_prices WHERE tenant_id = ? AND product_id = ? AND variant_id = ? "
                                + "AND status = 'ACTIVE' ORDER BY created_at DESC LIMIT 1",
                        BigDecimal.class, tenantId, productId, variantId);
                if (v != null) return v;
            }
            BigDecimal p = jdbc.queryForObject(
                    "SELECT amount FROM commerce_prices WHERE tenant_id = ? AND product_id = ? "
                            + "AND variant_id IS NULL AND status = 'ACTIVE' ORDER BY created_at DESC LIMIT 1",
                    BigDecimal.class, tenantId, productId);
            if (p != null) return p;
        } catch (EmptyResultDataAccessException ignored) {}
        // Default to zero if no price configured — allows free / pay-what-you-want items
        return BigDecimal.ZERO;
    }

    private String defaultCurrencyFor(UUID tenantId, UUID storeId) {
        try {
            return jdbc.queryForObject(
                    "SELECT default_currency FROM commerce_stores WHERE tenant_id = ? AND id = ?",
                    String.class, tenantId, storeId);
        } catch (EmptyResultDataAccessException e) {
            return "SAR";
        }
    }

    private void ensureStore(UUID tenantId, UUID storeId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM commerce_stores WHERE tenant_id = ? AND id = ?",
                Integer.class, tenantId, storeId);
        if (count == null || count == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "store not found");
    }

    private void ensureCartActive(CartResponse cart) {
        if (cart.status() != CommerceDomain.CartStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "cart is not active: " + cart.status());
        }
    }

    private CartResponse getOrThrow(UUID tenantId, UUID storeId, UUID cartId) {
        try {
            CartResponse cart = jdbc.queryForObject(
                    "SELECT * FROM commerce_carts WHERE tenant_id = ? AND store_id = ? AND id = ?",
                    this::mapRow, tenantId, storeId, cartId);
            if (cart != null) {
                List<CartItemResponse> items = jdbc.query(
                        "SELECT * FROM commerce_cart_items WHERE tenant_id = ? AND cart_id = ? ORDER BY created_at",
                        this::mapItemRow, tenantId, cartId);
                return new CartResponse(cart.id(), cart.tenantId(), cart.storeId(), cart.customerRef(),
                        cart.currency(), cart.status(), cart.subtotal(), cart.expiresAt(), items,
                        cart.version(), cart.createdAt(), cart.updatedAt());
            }
            return cart;
        } catch (EmptyResultDataAccessException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "cart not found: " + cartId);
        }
    }

    private CartItemResponse getItemOrThrow(UUID tenantId, UUID cartId, UUID itemId) {
        try {
            return jdbc.queryForObject(
                    "SELECT * FROM commerce_cart_items WHERE tenant_id = ? AND cart_id = ? AND id = ?",
                    this::mapItemRow, tenantId, cartId, itemId);
        } catch (EmptyResultDataAccessException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "cart item not found: " + itemId);
        }
    }

    private UUID actorUserId(Authentication auth) {
        if (auth == null || auth.getName() == null) return null;
        try { return UUID.fromString(auth.getName()); } catch (Exception e) { return null; }
    }

    private void audit(UUID tenantId, Authentication auth, String action, UUID resourceId, String reason) {
        try { auditService.success(auth, tenantId, action, "COMMERCE", resourceId == null ? null : resourceId.toString(), reason, null, null); }
        catch (Exception ignored) {}
    }

    private CartResponse mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new CartResponse(
                rs.getObject("id", UUID.class), rs.getObject("tenant_id", UUID.class),
                rs.getObject("store_id", UUID.class), rs.getString("customer_ref"),
                rs.getString("currency"),
                CommerceDomain.CartStatus.valueOf(rs.getString("status")),
                rs.getBigDecimal("subtotal"),
                rs.getObject("expires_at", Timestamp.class) == null ? null
                        : rs.getObject("expires_at", Timestamp.class).toInstant(),
                List.of(),
                rs.getLong("version"),
                rs.getObject("created_at", Timestamp.class).toInstant(),
                rs.getObject("updated_at", Timestamp.class).toInstant());
    }

    private CartItemResponse mapItemRow(ResultSet rs, int rowNum) throws SQLException {
        return new CartItemResponse(
                rs.getObject("id", UUID.class), rs.getObject("cart_id", UUID.class),
                rs.getObject("product_id", UUID.class), rs.getObject("variant_id", UUID.class),
                rs.getInt("quantity"), rs.getBigDecimal("unit_price"), rs.getString("currency"),
                rs.getBigDecimal("line_total"),
                rs.getObject("created_at", Timestamp.class).toInstant(),
                rs.getObject("updated_at", Timestamp.class).toInstant());
    }

    // Exposed for the checkout service to mark cart CHECKED_OUT
    @Transactional
    public void markCheckedOut(UUID tenantId, UUID cartId) {
        jdbc.update("UPDATE commerce_carts SET status = 'CHECKED_OUT', updated_at = ?, version = version + 1 "
                        + "WHERE tenant_id = ? AND id = ?", Timestamp.from(Instant.now()), tenantId, cartId);
    }
}
