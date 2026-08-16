package com.sanad.platform.commerce.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanad.platform.admin.service.PlatformAuditService;
import com.sanad.platform.commerce.api.CommerceDtos.*;
import com.sanad.platform.commerce.domain.CommerceCustomerPort;
import com.sanad.platform.commerce.domain.CommerceDomain;
import com.sanad.platform.commerce.domain.CommerceFinancePort;
import com.sanad.platform.commerce.domain.PaymentGatewayPort;
import com.sanad.platform.commerce.domain.ShippingQuotePort;
import com.sanad.platform.commerce.domain.TaxCalculationPort;
import com.sanad.platform.commerce.domain.InventoryAvailabilityPort;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Order application service (v20260816.5).
 *
 * <p>Tenant + store-scoped order read / list / cancel / getItems.
 * Order creation itself lives in {@link CheckoutService} (it must
 * atomically convert a cart into an order, calculate tax + shipping,
 * reserve / confirm inventory, create a payment intent, etc.).
 *
 * <p>Order status history is appended on every status transition (cancel).
 */
@Service
public class OrderService {

    private final JdbcTemplate jdbc;
    private final PlatformAuditService auditService;
    private final ObjectMapper objectMapper;

    public OrderService(JdbcTemplate jdbc, PlatformAuditService auditService, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public OrderResponse get(UUID tenantId, UUID storeId, UUID orderId) {
        return getOrThrow(tenantId, storeId, orderId);
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> list(UUID tenantId, UUID storeId) {
        return jdbc.query("SELECT * FROM commerce_orders WHERE tenant_id = ? AND store_id = ? ORDER BY created_at DESC",
                this::mapRow, tenantId, storeId);
    }

    @Transactional
    public OrderResponse cancel(UUID tenantId, UUID storeId, UUID orderId, Authentication auth) {
        OrderResponse existing = getOrThrow(tenantId, storeId, orderId);
        if (existing.status() == CommerceDomain.OrderStatus.COMPLETED
                || existing.status() == CommerceDomain.OrderStatus.CANCELLED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "cannot cancel order in status: " + existing.status());
        }
        Instant now = Instant.now();
        String prevStatus = existing.status().name();
        String prevPayment = existing.paymentStatus().name();
        jdbc.update("UPDATE commerce_orders SET status = 'CANCELLED', fulfillment_status = 'CANCELLED', "
                        + "updated_at = ?, version = version + 1 WHERE tenant_id = ? AND id = ?",
                Timestamp.from(now), tenantId, orderId);
        // Append history
        jdbc.update("INSERT INTO commerce_order_status_history (id, tenant_id, order_id, from_status, to_status, "
                        + "from_payment, to_payment, reason, actor, created_at) "
                        + "VALUES (?, ?, ?, ?, 'CANCELLED', ?, 'FAILED', 'cancelled by user', ?, ?)",
                UUID.randomUUID(), tenantId, orderId, prevStatus, prevPayment, actorUserId(auth), Timestamp.from(now));
        audit(tenantId, auth, "ORDER.CANCELLED", orderId, "order=" + existing.orderNumber());
        return getOrThrow(tenantId, storeId, orderId);
    }

    @Transactional(readOnly = true)
    public List<OrderItemResponse> getItems(UUID tenantId, UUID storeId, UUID orderId) {
        getOrThrow(tenantId, storeId, orderId); // ensure order exists in tenant+store scope
        return jdbc.query("SELECT * FROM commerce_order_items WHERE tenant_id = ? AND order_id = ? ORDER BY created_at",
                this::mapItemRow, tenantId, orderId);
    }

    // ===== Helpers =====
    private OrderResponse getOrThrow(UUID tenantId, UUID storeId, UUID orderId) {
        try {
            return jdbc.queryForObject(
                    "SELECT * FROM commerce_orders WHERE tenant_id = ? AND store_id = ? AND id = ?",
                    this::mapRow, tenantId, storeId, orderId);
        } catch (EmptyResultDataAccessException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "order not found: " + orderId);
        }
    }

    private UUID actorUserId(Authentication auth) {
        if (auth == null || auth.getName() == null) return null;
        try { return UUID.fromString(auth.getName()); } catch (Exception e) { return null; }
    }

    private void audit(UUID tenantId, Authentication auth, String action, UUID resourceId, String reason) {
        try { auditService.success(auth, tenantId, action, "ORDER", resourceId == null ? null : resourceId.toString(), reason, null, null); }
        catch (Exception ignored) {}
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> fromJson(String json) {
        if (json == null || json.isBlank()) return null;
        try { return objectMapper.readValue(json, Map.class); }
        catch (Exception e) { return null; }
    }

    private OrderResponse mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new OrderResponse(
                rs.getObject("id", UUID.class), rs.getObject("tenant_id", UUID.class),
                rs.getObject("store_id", UUID.class), rs.getString("order_number"),
                rs.getObject("cart_id", UUID.class), rs.getString("customer_reference"),
                fromJson(rs.getString("customer_snapshot")),
                rs.getString("currency"),
                rs.getBigDecimal("subtotal"), rs.getBigDecimal("discount_total"),
                rs.getBigDecimal("tax_total"), rs.getBigDecimal("shipping_total"),
                rs.getBigDecimal("grand_total"),
                CommerceDomain.PaymentStatus.valueOf(rs.getString("payment_status")),
                CommerceDomain.FulfillmentStatus.valueOf(rs.getString("fulfillment_status")),
                CommerceDomain.OrderStatus.valueOf(rs.getString("status")),
                rs.getString("idempotency_key"),
                rs.getLong("version"),
                rs.getObject("created_at", Timestamp.class).toInstant(),
                rs.getObject("updated_at", Timestamp.class).toInstant());
    }

    private OrderItemResponse mapItemRow(ResultSet rs, int rowNum) throws SQLException {
        return new OrderItemResponse(
                rs.getObject("id", UUID.class), rs.getObject("order_id", UUID.class),
                rs.getObject("product_id", UUID.class), rs.getObject("variant_id", UUID.class),
                rs.getString("product_name"), rs.getString("product_sku"),
                fromJson(rs.getString("variant_options")),
                rs.getInt("quantity"), rs.getBigDecimal("unit_price"),
                rs.getBigDecimal("discount"), rs.getBigDecimal("tax"),
                rs.getBigDecimal("line_total"),
                rs.getObject("created_at", Timestamp.class).toInstant());
    }

    // ===== Checkout helpers (package-private, invoked by CheckoutService) =====

    /**
     * Generate the next human-readable order number for a store.
     * Format: {@code ORD-<yyyy>-<mm>-<sequence>}.
     */
    String generateOrderNumber(UUID tenantId, UUID storeId) {
        Instant now = Instant.now();
        java.time.ZonedDateTime zdt = now.atZone(java.time.ZoneOffset.UTC);
        String prefix = String.format("ORD-%04d%02d-", zdt.getYear(), zdt.getMonthValue());
        Integer maxSeq = jdbc.queryForObject(
                "SELECT COUNT(*) FROM commerce_orders WHERE tenant_id = ? AND store_id = ? "
                        + "AND order_number LIKE ?",
                Integer.class, tenantId, storeId, prefix + "%");
        int next = (maxSeq == null ? 0 : maxSeq) + 1;
        return prefix + String.format("%05d", next);
    }

    /**
     * Persist a new order (called by CheckoutService inside an atomic transaction).
     */
    OrderResponse createOrderAtomically(UUID tenantId, UUID storeId, UUID cartId, String orderNumber,
                                          String customerReference, Map<String, Object> customerSnapshot,
                                          String currency, BigDecimal subtotal, BigDecimal discountTotal,
                                          BigDecimal taxTotal, BigDecimal shippingTotal, BigDecimal grandTotal,
                                          String paymentRef, String idempotencyKey, Authentication auth) {
        UUID orderId = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update("INSERT INTO commerce_orders (id, tenant_id, store_id, order_number, cart_id, "
                        + "customer_reference, customer_snapshot, currency, subtotal, discount_total, tax_total, "
                        + "shipping_total, grand_total, payment_status, fulfillment_status, status, "
                        + "idempotency_key, version, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?, 'PENDING', 'UNFULFILLED', 'PENDING', ?, 0, ?, ?)",
                orderId, tenantId, storeId, orderNumber, cartId, customerReference,
                toJson(customerSnapshot), currency, subtotal, discountTotal, taxTotal,
                shippingTotal, grandTotal, idempotencyKey,
                Timestamp.from(now), Timestamp.from(now));
        // Copy cart items to order items (snapshot product name + sku)
        List<CartItemResponse> cartItems = jdbc.query(
                "SELECT ci.id, ci.tenant_id, ci.cart_id, ci.product_id, ci.variant_id, ci.quantity, "
                        + "ci.unit_price, ci.currency, ci.line_total, ci.created_at, ci.updated_at "
                        + "FROM commerce_cart_items ci WHERE ci.tenant_id = ? AND ci.cart_id = ?",
                (rs, rowNum) -> new CartItemResponse(
                        rs.getObject("id", UUID.class), rs.getObject("cart_id", UUID.class),
                        rs.getObject("product_id", UUID.class), rs.getObject("variant_id", UUID.class),
                        rs.getInt("quantity"), rs.getBigDecimal("unit_price"), rs.getString("currency"),
                        rs.getBigDecimal("line_total"),
                        rs.getObject("created_at", Timestamp.class).toInstant(),
                        rs.getObject("updated_at", Timestamp.class).toInstant()),
                tenantId, cartId);
        BigDecimal perItemTax = taxTotal.divide(BigDecimal.valueOf(Math.max(1, cartItems.size())), 2, RoundingMode.HALF_UP);
        BigDecimal taxAccum = BigDecimal.ZERO;
        for (int i = 0; i < cartItems.size(); i++) {
            CartItemResponse ci = cartItems.get(i);
            BigDecimal itemTax = (i == cartItems.size() - 1)
                    ? taxTotal.subtract(taxAccum) // last item: ensure total reconciles
                    : perItemTax;
            taxAccum = taxAccum.add(itemTax);
            String productName;
            String productSku;
            Map<String, Object> variantOptions = null;
            try {
                Map<String, Object> prod = jdbc.queryForMap(
                        "SELECT name, sku FROM commerce_products WHERE tenant_id = ? AND id = ?",
                        tenantId, ci.productId());
                productName = (String) prod.get("name");
                productSku = (String) prod.get("sku");
            } catch (EmptyResultDataAccessException e) {
                productName = "Unknown product";
                productSku = null;
            }
            if (ci.variantId() != null) {
                try {
                    Map<String, Object> variant = jdbc.queryForMap(
                            "SELECT name, options FROM commerce_product_variants WHERE tenant_id = ? AND id = ?",
                            tenantId, ci.variantId());
                    variantOptions = fromJson((String) variant.get("options"));
                } catch (EmptyResultDataAccessException ignored) {}
            }
            jdbc.update("INSERT INTO commerce_order_items (id, tenant_id, order_id, product_id, variant_id, "
                            + "product_name, product_sku, variant_options, quantity, unit_price, discount, tax, line_total, created_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, 0, ?, ?, ?)",
                    UUID.randomUUID(), tenantId, orderId, ci.productId(), ci.variantId(),
                    productName, productSku, toJson(variantOptions),
                    ci.quantity(), ci.unitPrice(), itemTax, ci.lineTotal(), Timestamp.from(now));
        }
        // Initial status history
        jdbc.update("INSERT INTO commerce_order_status_history (id, tenant_id, order_id, from_status, to_status, "
                        + "from_payment, to_payment, reason, actor, created_at) "
                        + "VALUES (?, ?, ?, NULL, 'PENDING', NULL, 'PENDING', ?, ?, ?)",
                UUID.randomUUID(), tenantId, orderId, "checkout", actorUserId(auth), Timestamp.from(now));
        audit(tenantId, auth, "ORDER.CREATED", orderId, "order=" + orderNumber);
        return getOrThrow(tenantId, storeId, orderId);
    }

    /**
     * Resolve an existing order by its idempotency key (returns null if none).
     */
    OrderResponse findByIdempotencyKey(UUID tenantId, UUID storeId, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) return null;
        try {
            return jdbc.queryForObject(
                    "SELECT * FROM commerce_orders WHERE tenant_id = ? AND store_id = ? AND idempotency_key = ?",
                    this::mapRow, tenantId, storeId, idempotencyKey);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    private String toJson(Map<String, Object> map) {
        try { return objectMapper.writeValueAsString(map); }
        catch (Exception e) { return "{}"; }
    }
}
