package com.sanad.platform.commerce.application;

import com.sanad.platform.admin.service.PlatformAuditService;
import com.sanad.platform.commerce.api.CommerceDtos.*;
import com.sanad.platform.commerce.domain.CommerceCustomerPort;
import com.sanad.platform.commerce.domain.CommerceFinancePort;
import com.sanad.platform.commerce.domain.InventoryAvailabilityPort;
import com.sanad.platform.commerce.domain.PaymentGatewayPort;
import com.sanad.platform.commerce.domain.ShippingQuotePort;
import com.sanad.platform.commerce.domain.TaxCalculationPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Atomic Commerce checkout.
 *
 * <p>The one-order-per-cart and tenant-scoped idempotency-key invariants are
 * claimed through a single PostgreSQL INSERT with bare ON CONFLICT DO NOTHING.
 * Every new order persists a SHA-256 fingerprint of the business request and
 * cart snapshot in the column introduced by V20260820_6. A key replay with a
 * different fingerprint is rejected with HTTP 409.
 */
@Service
public class CheckoutService {

    private static final Logger log = LoggerFactory.getLogger(CheckoutService.class);

    private final JdbcTemplate jdbc;
    private final PlatformAuditService auditService;
    private final CartService cartService;
    private final OrderService orderService;
    private final CommerceCustomerPort customerPort;
    private final TaxCalculationPort taxPort;
    private final ShippingQuotePort shippingPort;
    private final PaymentGatewayPort paymentPort;
    private final CommerceFinancePort financePort;
    private final InventoryAvailabilityPort inventory;

    public CheckoutService(JdbcTemplate jdbc, PlatformAuditService auditService,
                           CartService cartService, OrderService orderService,
                           CommerceCustomerPort customerPort, TaxCalculationPort taxPort,
                           ShippingQuotePort shippingPort, PaymentGatewayPort paymentPort,
                           CommerceFinancePort financePort, InventoryAvailabilityPort inventory) {
        this.jdbc = jdbc;
        this.auditService = auditService;
        this.cartService = cartService;
        this.orderService = orderService;
        this.customerPort = customerPort;
        this.taxPort = taxPort;
        this.shippingPort = shippingPort;
        this.paymentPort = paymentPort;
        this.financePort = financePort;
        this.inventory = inventory;
    }

    @Transactional
    public CheckoutResponse checkout(UUID tenantId, UUID storeId, CheckoutRequest request, Authentication auth) {
        if (request == null || request.cartId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "cartId is required");
        }

        // Totals can be read even for a CHECKED_OUT cart, which allows us to
        // verify a sequential replay's full request fingerprint before returning.
        CartResponse cart = cartService.calculateTotals(tenantId, storeId, request.cartId());
        String fingerprint = computeIdempotencyFingerprint(tenantId, storeId, request, cart);
        boolean keyed = request.idempotencyKey() != null && !request.idempotencyKey().isBlank();

        if (keyed) {
            OrderResponse existing = orderService.findByIdempotencyKey(tenantId, request.idempotencyKey());
            if (existing != null) {
                verifyReplayIdentity(tenantId, storeId, request, fingerprint, existing);
                return toCheckoutResponse(existing, "");
            }
        } else {
            OrderResponse existingByCart = orderService.findByCart(tenantId, request.cartId());
            if (existingByCart != null) {
                return toCheckoutResponse(existingByCart, "");
            }
        }

        if (cart.status() != com.sanad.platform.commerce.domain.CommerceDomain.CartStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "cart is not active: " + cart.status());
        }
        if (cart.items() == null || cart.items().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "cart is empty");
        }

        String customerRef;
        if (request.customerContactId() != null) {
            customerRef = customerPort.resolveByContact(tenantId, request.customerContactId());
        } else if (request.customerEmail() != null && !request.customerEmail().isBlank()) {
            customerRef = customerPort.resolveOrCreateGuest(
                    tenantId, request.customerEmail().trim(), request.customerName());
        } else if (cart.customerRef() != null && !cart.customerRef().isBlank()) {
            customerRef = cart.customerRef();
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "customerEmail or customerContactId is required for checkout");
        }

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("customerRef", customerRef);
        if (request.customerEmail() != null) snapshot.put("email", request.customerEmail().trim());
        if (request.customerName() != null) snapshot.put("name", request.customerName().trim());
        if (request.metadata() != null) snapshot.putAll(request.metadata());

        BigDecimal subtotal = cart.subtotal() != null ? cart.subtotal() : BigDecimal.ZERO;
        BigDecimal discount = BigDecimal.ZERO;
        BigDecimal tax = taxPort.calculateTax(tenantId, storeId, subtotal, cart.currency());
        BigDecimal shipping = shippingPort.getQuote(tenantId, storeId, subtotal, cart.currency());
        BigDecimal grandTotal = subtotal.add(tax).add(shipping).subtract(discount);

        for (CartItemResponse item : cart.items()) {
            int available = inventory.getAvailability(tenantId, item.productId(), item.variantId());
            if (available < item.quantity()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "insufficient stock for product " + item.productId());
            }
        }

        String orderNumber = orderService.generateOrderNumber(tenantId, storeId);
        UUID orderId = UUID.randomUUID();
        Optional<UUID> claimed = orderService.tryClaimIdempotencyKey(
                tenantId, storeId, orderId, orderNumber, request.cartId(),
                customerRef, snapshot, cart.currency(), subtotal, discount,
                tax, shipping, grandTotal, request.idempotencyKey(), auth);

        if (claimed.isEmpty()) {
            // The winner may have collided on EITHER unique invariant. For a
            // keyed request, first look up by key; if absent, resolve the cart
            // winner (same cart, different key) and compare its fingerprint.
            OrderResponse winner = keyed
                    ? orderService.findByIdempotencyKey(tenantId, request.idempotencyKey())
                    : null;
            if (winner == null) {
                winner = orderService.findByCart(tenantId, request.cartId());
            }
            if (winner != null) {
                if (keyed) {
                    verifyReplayIdentity(tenantId, storeId, request, fingerprint, winner);
                }
                return toCheckoutResponse(winner, "");
            }
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "checkout claim lost but winner could not be loaded");
        }

        // Persist the request identity in the same transaction as the claim.
        // The column and index already exist from V20260820_6; no migration is
        // required for this corrective closure.
        int fingerprintRows = jdbc.update(
                "UPDATE commerce_orders SET idempotency_fingerprint=? WHERE tenant_id=? AND id=?",
                fingerprint, tenantId, orderId);
        if (fingerprintRows != 1) {
            throw new IllegalStateException("Failed to persist checkout idempotency fingerprint for " + orderId);
        }

        orderService.completeOrderItemsAndHistory(tenantId, storeId, orderId, request.cartId(),
                orderNumber, customerRef, snapshot, cart.currency(),
                subtotal, discount, tax, shipping, grandTotal,
                request.idempotencyKey(), auth);

        String paymentRef = paymentPort.createPaymentIntent(
                tenantId, orderId, grandTotal, cart.currency());
        boolean verified = paymentRef != null && paymentPort.verifyPayment(tenantId, paymentRef);

        // A verified PSP payment must settle physical inventory and Finance
        // BEFORE Commerce is marked PAID. All operations participate in the
        // surrounding transaction, so any failure rolls the claim/order back.
        if (verified) {
            for (CartItemResponse item : cart.items()) {
                inventory.confirm(tenantId, item.productId(), item.variantId(), item.quantity());
            }
            financePort.markOrderSettled(tenantId, orderId, grandTotal);
        }

        updateOrderPostPayment(tenantId, orderId, paymentRef, verified);
        cartService.markCheckedOut(tenantId, request.cartId());

        if (!verified && paymentRef == null) {
            log.info("checkout: order {} created PENDING — manual settlement required (tenant={})",
                    orderId, tenantId);
        } else if (!verified) {
            log.warn("checkout: payment verification failed for order {} (tenant={})", orderId, tenantId);
        }

        OrderResponse finalOrder = orderService.get(tenantId, storeId, orderId);
        return toCheckoutResponse(finalOrder, paymentRef);
    }

    private void verifyReplayIdentity(UUID tenantId, UUID storeId, CheckoutRequest request,
                                      String fingerprint, OrderResponse existing) {
        if (!java.util.Objects.equals(existing.cartId(), request.cartId())
                || !java.util.Objects.equals(existing.storeId(), storeId)) {
            throw idempotencyMismatch("key/cart/store identity differs from the existing order");
        }
        String persisted = orderService.findIdempotencyFingerprint(tenantId, existing.id());
        if (persisted == null || persisted.isBlank()) {
            // Backward-compatible handling for orders created before the
            // fingerprint column began being populated. Their cart/store
            // invariant remains enforced; all new orders are strict.
            log.warn("Legacy idempotency replay without persisted fingerprint: tenant={} order={}",
                    tenantId, existing.id());
            return;
        }
        if (!persisted.equals(fingerprint)) {
            throw idempotencyMismatch("key was reused with a different checkout payload");
        }
    }

    private ResponseStatusException idempotencyMismatch(String detail) {
        return new ResponseStatusException(HttpStatus.CONFLICT,
                "IDEMPOTENCY_KEY_REUSE_MISMATCH: " + detail);
    }

    /**
     * Deterministic SHA-256 identity for the business request. The idempotency
     * key itself is deliberately excluded so same-cart/different-key concurrent
     * requests can be compared as the same logical checkout.
     */
    static String computeIdempotencyFingerprint(UUID tenantId, UUID storeId,
                                                CheckoutRequest request, CartResponse cart) {
        StringBuilder canonical = new StringBuilder(1024);
        append(canonical, "tenant", tenantId);
        append(canonical, "store", storeId);
        append(canonical, "cart", request.cartId());
        append(canonical, "contact", request.customerContactId());
        append(canonical, "email", normalizeEmail(request.customerEmail()));
        append(canonical, "name", normalizeText(request.customerName()));
        append(canonical, "cartCustomer", normalizeText(cart.customerRef()));
        append(canonical, "currency", normalizeText(cart.currency()));
        append(canonical, "subtotal", decimal(cart.subtotal()));
        append(canonical, "metadata", canonicalValue(request.metadata()));

        List<CartItemResponse> items = new ArrayList<>(
                cart.items() == null ? List.of() : cart.items());
        items.sort(Comparator
                .comparing((CartItemResponse i) -> String.valueOf(i.productId()))
                .thenComparing(i -> String.valueOf(i.variantId()))
                .thenComparingInt(CartItemResponse::quantity)
                .thenComparing(i -> decimal(i.unitPrice()))
                .thenComparing(i -> normalizeText(i.currency())));
        int index = 0;
        for (CartItemResponse item : items) {
            String prefix = "item[" + index++ + "].";
            append(canonical, prefix + "product", item.productId());
            append(canonical, prefix + "variant", item.variantId());
            append(canonical, prefix + "qty", item.quantity());
            append(canonical, prefix + "unitPrice", decimal(item.unitPrice()));
            append(canonical, prefix + "currency", normalizeText(item.currency()));
            append(canonical, prefix + "lineTotal", decimal(item.lineTotal()));
        }

        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is not available", impossible);
        }
    }

    private static void append(StringBuilder out, String key, Object value) {
        String text = value == null ? "<null>" : String.valueOf(value);
        out.append(key.length()).append(':').append(key).append('=')
                .append(text.length()).append(':').append(text).append(';');
    }

    private static String canonicalValue(Object value) {
        if (value == null) return "<null>";
        if (value instanceof Map<?, ?> map) {
            TreeMap<String, Object> sorted = new TreeMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                sorted.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            StringBuilder out = new StringBuilder("{");
            for (Map.Entry<String, Object> entry : sorted.entrySet()) {
                append(out, entry.getKey(), canonicalValue(entry.getValue()));
            }
            return out.append('}').toString();
        }
        if (value instanceof Collection<?> collection) {
            StringBuilder out = new StringBuilder("[");
            int i = 0;
            for (Object item : collection) append(out, String.valueOf(i++), canonicalValue(item));
            return out.append(']').toString();
        }
        if (value instanceof BigDecimal decimal) return decimal(decimal);
        return String.valueOf(value);
    }

    private static String normalizeEmail(String value) {
        return value == null ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeText(String value) {
        return value == null ? null : value.trim();
    }

    private static String decimal(BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros().toPlainString();
    }

    private void updateOrderPostPayment(UUID tenantId, UUID orderId, String paymentRef, boolean verified) {
        String newPaymentStatus;
        String newOrderStatus;
        String reason;
        if (paymentRef == null) {
            newPaymentStatus = "PENDING";
            newOrderStatus = "PENDING";
            reason = "no PSP configured; manual settlement required";
        } else if (verified) {
            newPaymentStatus = "PAID";
            newOrderStatus = "CONFIRMED";
            reason = "payment_ref=" + paymentRef + ",verified=true";
        } else {
            newPaymentStatus = "FAILED";
            newOrderStatus = "PENDING";
            reason = "payment_ref=" + paymentRef + ",verified=false";
        }
        java.sql.Timestamp now = java.sql.Timestamp.from(java.time.Instant.now());
        jdbc.update("UPDATE commerce_orders SET payment_status = ?, status = ?, updated_at = ?, version = version + 1 "
                        + "WHERE tenant_id = ? AND id = ?",
                newPaymentStatus, newOrderStatus, now, tenantId, orderId);
        jdbc.update("INSERT INTO commerce_order_status_history (id, tenant_id, order_id, from_status, to_status, "
                        + "from_payment, to_payment, reason, actor, created_at) "
                        + "VALUES (?, ?, ?, 'PENDING', ?, 'PENDING', ?, ?, ?, ?)",
                UUID.randomUUID(), tenantId, orderId, newOrderStatus, newPaymentStatus,
                reason, null, now);
    }

    private CheckoutResponse toCheckoutResponse(OrderResponse order, String paymentRef) {
        return new CheckoutResponse(
                order.id(), order.orderNumber(), paymentRef,
                order.customerReference(), order.currency(),
                order.subtotal(), order.discountTotal(), order.taxTotal(),
                order.shippingTotal(), order.grandTotal(),
                order.paymentStatus().name(), order.fulfillmentStatus().name(),
                order.status().name(), order.createdAt());
    }
}
