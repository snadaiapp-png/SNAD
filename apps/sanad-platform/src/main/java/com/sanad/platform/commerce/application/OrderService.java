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
     * Generate the next human-readable order number for a tenant+period using an
     * atomic, monotonic DB allocator.
     *
     * <p>Format: {@code ORD-<yyyyMM>-<NNNNN>} where the NNNNN part is allocated
     * atomically via {@code INSERT ... ON CONFLICT DO UPDATE ... RETURNING} on
     * the {@code commerce_order_number_sequences} table. This is safe under
     * concurrent transactions across multiple application processes (Render).
     *
     * <p>The allocator never decrements on order cancellation/deletion, so
     * sequence numbers are never reused (ORDER_NUMBER_NO_REUSE).
     *
     * <p>Each (tenant_id, period=YYYYMM) pair maintains an independent counter
     * — so the same tenant's multiple stores share the per-tenant+per-month
     * sequence, which prevents cross-store collisions within a tenant (the
     * unique constraint {@code uk_commerce_orders_tenant_number} is scoped to
     * {@code (tenant_id, order_number)} and therefore also satisfied).
     */
    String generateOrderNumber(UUID tenantId, UUID storeId) {
        java.time.ZonedDateTime zdt = java.time.Instant.now().atZone(java.time.ZoneOffset.UTC);
        String period = String.format("%04d%02d", zdt.getYear(), zdt.getMonthValue());
        String prefix = "ORD-" + period + "-";
        long next = allocateNextSequence(tenantId, period);
        return prefix + String.format("%05d", next);
    }

    /**
     * Atomically allocate the next sequence value for a (tenant_id, period)
     * pair using a portable UPSERT. On PostgreSQL the
     * {@code INSERT ... ON CONFLICT (tenant_id, period) DO UPDATE SET
     * last_value = last_value + 1 RETURNING last_value} form is fully atomic.
     *
     * <p>H2 (PostgreSQL compatibility mode, used by local / integration tests)
     * supports the same syntax, so the code path is identical between test
     * and prod. As an extra safety net, a {@link org.springframework.dao.DuplicateKeyException}
     * triggers a single retry of the UPDATE branch — this guards against any
     * edge case where the UPSERT clause does not match (e.g. column-list
     * inference differences between minor H2 versions).
     */
    private long allocateNextSequence(UUID tenantId, String period) {
        try {
            // Atomic UPSERT — first call inserts with last_value=1; subsequent
            // callsites hit the ON CONFLICT branch and increment to 2, 3, ...
            return jdbc.queryForObject(
                    """
                    INSERT INTO commerce_order_number_sequences (tenant_id, period, last_value)
                    VALUES (?, ?, 1)
                    ON CONFLICT (tenant_id, period) DO UPDATE
                        SET last_value = commerce_order_number_sequences.last_value + 1,
                            updated_at = NOW()
                    RETURNING last_value
                    """,
                    Long.class, tenantId, period);
        } catch (org.springframework.dao.DuplicateKeyException dup) {
            // Defensive fallback — the UPSERT clause should prevent this in
            // practice, but if a future driver / minor-version mismatch makes
            // ON CONFLICT fall through, the row already exists. Re-issue a
            // direct UPDATE ... RETURNING.
            return jdbc.queryForObject(
                    """
                    UPDATE commerce_order_number_sequences
                    SET last_value = last_value + 1, updated_at = NOW()
                    WHERE tenant_id = ? AND period = ?
                    RETURNING last_value
                    """,
                    Long.class, tenantId, period);
        }
    }

    /**
     * Persist a new order (called by CheckoutService inside an atomic transaction).
     *
     * <p><strong>Deprecated v20260820.3</strong>: CheckoutService now uses the
     * atomic {@link #tryClaimIdempotencyKey} + {@link #completeOrderItemsAndHistory}
     * pair to avoid the PostgreSQL transaction-abort race. This method is
     * retained for backward compatibility with any direct callers and tests
     * that exercise the monolithic create path.
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
        completeOrderItemsAndHistory(tenantId, storeId, orderId, cartId, orderNumber,
                customerReference, customerSnapshot, currency,
                subtotal, discountTotal, taxTotal, shippingTotal, grandTotal,
                idempotencyKey, auth);
        return getOrThrow(tenantId, storeId, orderId);
    }

    /**
     * Copy cart items to order items (snapshot product name + sku) and append
     * the initial PENDING status history row. Called by CheckoutService
     * AFTER the order row has been atomically claimed via
     * {@link #tryClaimIdempotencyKey}.
     *
     * <p>This separation lets the CheckoutService atomically claim the
     * idempotency key (or detect a concurrent winner) BEFORE paying the
     * cost of copying cart items. The cost is paid only by the winner.
     */
    void completeOrderItemsAndHistory(UUID tenantId, UUID storeId, UUID orderId, UUID cartId,
                                       String orderNumber,
                                       String customerReference, Map<String, Object> customerSnapshot,
                                       String currency, BigDecimal subtotal, BigDecimal discountTotal,
                                       BigDecimal taxTotal, BigDecimal shippingTotal, BigDecimal grandTotal,
                                       String idempotencyKey, Authentication auth) {
        Instant now = Instant.now();
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

    /**
     * Attempt to claim an idempotency key atomically. Uses PostgreSQL's
     * {@code INSERT ... ON CONFLICT DO NOTHING RETURNING} to make the
     * claim-or-detect-existing operation a single atomic statement that
     * never aborts the surrounding transaction (no DuplicateKeyException,
     * no transaction-abort-then-query race).
     *
     * <p>Returns:
     * <ul>
     *   <li>{@link Optional#empty()} if the idempotency key was already
     *       claimed by a concurrent request — the caller MUST fall back to
     *       {@link #findByIdempotencyKey(UUID, UUID, String)} to read the
     *       winner's order.</li>
     *   <li>{@link Optional#of(orderId)} if this caller successfully claimed
     *       the idempotency key and persisted the order row — the caller
     *       MUST proceed to copy cart items + status history + payment +
     *       inventory + finance + markCheckedOut.</li>
     * </ul>
     *
     * <p>When {@code idempotencyKey} is null or blank, this method always
     * inserts (no ON CONFLICT clause) — idempotency replay is the caller's
     * responsibility in that case (sequential only).
     */
    java.util.Optional<UUID> tryClaimIdempotencyKey(UUID tenantId, UUID storeId, UUID orderId,
                                                     String orderNumber, UUID cartId,
                                                     String customerReference, Map<String, Object> customerSnapshot,
                                                     String currency, BigDecimal subtotal, BigDecimal discountTotal,
                                                     BigDecimal taxTotal, BigDecimal shippingTotal, BigDecimal grandTotal,
                                                     String idempotencyKey, Authentication auth) {
        Instant now = Instant.now();
        String sql;
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            // Atomic claim-or-detect. ON CONFLICT (tenant_id, store_id, idempotency_key) DO NOTHING
            // means a concurrent winner will leave our insert as a no-op, and RETURNING id will
            // return zero rows — telling us to fall back to findByIdempotencyKey.
            sql = "INSERT INTO commerce_orders (id, tenant_id, store_id, order_number, cart_id, "
                    + "customer_reference, customer_snapshot, currency, subtotal, discount_total, tax_total, "
                    + "shipping_total, grand_total, payment_status, fulfillment_status, status, "
                    + "idempotency_key, version, created_at, updated_at) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?, 'PENDING', 'UNFULFILLED', 'PENDING', ?, 0, ?, ?) "
                    + "ON CONFLICT (tenant_id, store_id, idempotency_key) DO NOTHING "
                    + "RETURNING id";
        } else {
            // No idempotency key supplied — plain insert (caller accepts potential
            // DuplicateKeyException if two no-key requests race on the same order_number
            // — but the order_number is allocated atomically via the sequence allocator,
            // so this is only an issue if two requests without idempotency keys race
            // on the same cart, which the cart.status guard catches).
            sql = "INSERT INTO commerce_orders (id, tenant_id, store_id, order_number, cart_id, "
                    + "customer_reference, customer_snapshot, currency, subtotal, discount_total, tax_total, "
                    + "shipping_total, grand_total, payment_status, fulfillment_status, status, "
                    + "idempotency_key, version, created_at, updated_at) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?, 'PENDING', 'UNFULFILLED', 'PENDING', ?, 0, ?, ?) "
                    + "RETURNING id";
        }
        try {
            UUID insertedId = jdbc.queryForObject(sql,
                    (rs, rowNum) -> rs.getObject("id", UUID.class),
                    orderId, tenantId, storeId, orderNumber, cartId, customerReference,
                    toJson(customerSnapshot), currency, subtotal, discountTotal, taxTotal,
                    shippingTotal, grandTotal, idempotencyKey,
                    Timestamp.from(now), Timestamp.from(now));
            return java.util.Optional.of(insertedId);
        } catch (EmptyResultDataAccessException e) {
            // ON CONFLICT DO NOTHING returned no rows — a concurrent request
            // claimed this idempotency key first. Tell the caller to fall back
            // to findByIdempotencyKey. The surrounding transaction is NOT aborted
            // because no constraint violation was raised.
            return java.util.Optional.empty();
        }
    }

    private String toJson(Map<String, Object> map) {
        try { return objectMapper.writeValueAsString(map); }
        catch (Exception e) { return "{}"; }
    }
}
