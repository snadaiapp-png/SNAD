package com.sanad.platform.commerce.application;

import com.sanad.platform.admin.service.PlatformAuditService;
import com.sanad.platform.commerce.api.CommerceDtos.*;
import com.sanad.platform.commerce.domain.CommerceCustomerPort;
import com.sanad.platform.commerce.domain.CommerceFinancePort;
import com.sanad.platform.commerce.domain.InventoryAvailabilityPort;
import com.sanad.platform.commerce.domain.PaymentGatewayPort;
import com.sanad.platform.commerce.domain.ShippingQuotePort;
import com.sanad.platform.commerce.domain.TaxCalculationPort;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Checkout application service (v20260820.3).
 *
 * <p>Converts a cart into an order atomically:
 * <ol>
 *   <li>Resolve (or create) the customer reference (guest email or CRM contact).</li>
 *   <li>Re-validate cart items + inventory.</li>
 *   <li>Calculate subtotal (already on cart), tax ({@link TaxCalculationPort}),
 *       shipping ({@link ShippingQuotePort}).</li>
 *   <li>Create a payment intent via {@link PaymentGatewayPort}.</li>
 *   <li>Verify the payment (the simulated adapter returns {@code true} immediately).</li>
 *   <li>Persist the order + order items (snapshot) via {@link OrderService}.</li>
 *   <li>Confirm inventory + record the order in Finance ({@link CommerceFinancePort}).</li>
 *   <li>Mark the cart {@code CHECKED_OUT}.</li>
 * </ol>
 *
 * <p><strong>Idempotency (v20260820.3 — PostgreSQL-safe)</strong>:
 * The previous implementation used a Java catch-and-query pattern:
 * <pre>{@code
 *   try { createOrderAtomically(...); }
 *   catch (DuplicateKeyException dup) { findByIdempotencyKey(...); }
 * }</pre>
 * This is unsafe on PostgreSQL because a unique-constraint violation ABORTS
 * the surrounding transaction, and the subsequent SELECT in the same
 * transaction fails with {@code current transaction is aborted, commands
 * ignored until end of transaction block}.
 *
 * <p>The new implementation uses PostgreSQL's native
 * {@code INSERT ... ON CONFLICT (tenant_id, store_id, idempotency_key)
 * DO NOTHING RETURNING id} (see {@link OrderService#tryClaimIdempotencyKey}).
 * This is a single atomic statement — no constraint violation is ever raised,
 * so the surrounding transaction stays alive. If the INSERT returns zero
 * rows (concurrent winner claimed the key first), the caller falls back to
 * {@link OrderService#findByIdempotencyKey} to read the winner's order. The
 * winner lookup runs in the same transaction, but since no constraint
 * violation occurred, the transaction is not aborted.
 *
 * <p>This is the canonical PostgreSQL pattern for atomic idempotency claim
 * without transaction abort.
 */
@Service
public class CheckoutService {

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
        if (request == null || request.cartId() == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "cartId is required");

        // ===== Sequential idempotency replay (fast path) — with request-identity check =====
        if (request.idempotencyKey() != null && !request.idempotencyKey().isBlank()) {
            OrderResponse existing = orderService.findByIdempotencyKey(tenantId, request.idempotencyKey());
            if (existing != null) {
                // Verify the existing order's request identity matches this request.
                // An idempotency key identifies ONE logical request — if the caller
                // reuses it with a different cart or store, that's a contract violation
                // (IDEMPOTENCY_KEY_REUSE_MISMATCH) and MUST surface as HTTP 409 — NOT
                // as a silent return-winner.
                if (!java.util.Objects.equals(existing.cartId(), request.cartId())
                        || !java.util.Objects.equals(existing.storeId(), storeId)) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "IDEMPOTENCY_KEY_REUSE_MISMATCH: key already bound to a different cart or store");
                }
                return toCheckoutResponse(existing, "");
            }
        }

        // ===== Sequential no-key replay (cart already checked out) =====
        // When no idempotency key is supplied, the canonical cart invariant
        // (uk_commerce_orders_tenant_cart) guarantees at most one order per
        // (tenant_id, cart_id). If the cart already has an order, this is a
        // sequential replay — return the existing order. (Concurrent no-key
        // requests are handled atomically by tryClaimIdempotencyKey below.)
        if (request.idempotencyKey() == null || request.idempotencyKey().isBlank()) {
            OrderResponse existingByCart = orderService.findByCart(tenantId, request.cartId());
            if (existingByCart != null) {
                return toCheckoutResponse(existingByCart, "");
            }
        }

        CartResponse cart = cartService.calculateTotals(tenantId, storeId, request.cartId());
        if (cart.status() != com.sanad.platform.commerce.domain.CommerceDomain.CartStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "cart is not active: " + cart.status());
        }
        if (cart.items() == null || cart.items().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "cart is empty");
        }

        // Resolve customer
        String customerRef;
        if (request.customerContactId() != null) {
            customerRef = customerPort.resolveByContact(tenantId, request.customerContactId());
        } else if (request.customerEmail() != null && !request.customerEmail().isBlank()) {
            customerRef = customerPort.resolveOrCreateGuest(tenantId, request.customerEmail(), request.customerName());
        } else if (cart.customerRef() != null && !cart.customerRef().isBlank()) {
            customerRef = cart.customerRef();
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "customerEmail or customerContactId is required for checkout");
        }

        // Build customer snapshot for the order
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("customerRef", customerRef);
        if (request.customerEmail() != null) snapshot.put("email", request.customerEmail());
        if (request.customerName() != null) snapshot.put("name", request.customerName());
        if (request.metadata() != null) snapshot.putAll(request.metadata());

        BigDecimal subtotal = cart.subtotal() != null ? cart.subtotal() : BigDecimal.ZERO;
        BigDecimal discount = BigDecimal.ZERO;
        BigDecimal tax = taxPort.calculateTax(tenantId, storeId, subtotal, cart.currency());
        BigDecimal shipping = shippingPort.getQuote(tenantId, storeId, subtotal, cart.currency());
        BigDecimal grandTotal = subtotal.add(tax).add(shipping).subtract(discount);

        // Validate + reserve inventory (the cart already reserved when items were added — re-check is best-effort)
        for (CartItemResponse item : cart.items()) {
            int available = inventory.getAvailability(tenantId, item.productId(), item.variantId());
            if (available < item.quantity()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "insufficient stock for product " + item.productId());
            }
        }

        String orderNumber = orderService.generateOrderNumber(tenantId, storeId);
        UUID orderId = UUID.randomUUID();

        // ===== Atomic idempotency / cart claim (PostgreSQL-safe) =====
        // tryClaimIdempotencyKey uses INSERT ... ON CONFLICT DO NOTHING RETURNING.
        // For idempotency-key requests: ON CONFLICT (tenant_id, idempotency_key) DO NOTHING.
        // For no-key requests: ON CONFLICT (tenant_id, cart_id) WHERE cart_id IS NOT NULL DO NOTHING.
        // Either way, no constraint violation is ever raised, so the surrounding
        // transaction is never aborted.
        Optional<UUID> claimed = orderService.tryClaimIdempotencyKey(
                tenantId, storeId, orderId, orderNumber, request.cartId(),
                customerRef, snapshot, cart.currency(), subtotal, discount,
                tax, shipping, grandTotal, request.idempotencyKey(), auth);

        if (claimed.isEmpty()) {
            // Concurrent winner claimed this idempotency key OR this cart.
            // Transaction is still alive (no constraint violation). Fall back to
            // reading the winner's order — and verify request identity.
            OrderResponse winner;
            if (request.idempotencyKey() != null && !request.idempotencyKey().isBlank()) {
                winner = orderService.findByIdempotencyKey(tenantId, request.idempotencyKey());
                if (winner != null) {
                    // Request-identity check — same as sequential path
                    if (!java.util.Objects.equals(winner.cartId(), request.cartId())
                            || !java.util.Objects.equals(winner.storeId(), storeId)) {
                        throw new ResponseStatusException(HttpStatus.CONFLICT,
                                "IDEMPOTENCY_KEY_REUSE_MISMATCH: key already bound to a different cart or store");
                    }
                    return toCheckoutResponse(winner, "");
                }
            } else {
                // No-key concurrent path — winner is the order that claimed this cart
                winner = orderService.findByCart(tenantId, request.cartId());
                if (winner != null) {
                    return toCheckoutResponse(winner, "");
                }
            }
            // Should not happen — if the INSERT-ON-CONFLICT returned no rows,
            // there must be a winner. Surface as a controlled 409 to avoid
            // an unexplained 500.
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "idempotency key already claimed but winner could not be loaded");
        }

        // ===== We are the winner — proceed with order items + status history =====
        // The order row is already persisted (status=PENDING, payment_status=PENDING).
        // Now copy cart items to order items, append initial status history,
        // then run payment + inventory + finance + markCheckedOut.
        orderService.completeOrderItemsAndHistory(tenantId, storeId, orderId, request.cartId(),
                orderNumber, customerRef, snapshot, cart.currency(),
                subtotal, discount, tax, shipping, grandTotal,
                request.idempotencyKey(), auth);

        // Create payment intent + verify (simulated adapter returns true immediately)
        String paymentRef = paymentPort.createPaymentIntent(tenantId, orderId, grandTotal, cart.currency());
        boolean verified = paymentPort.verifyPayment(tenantId, paymentRef);

        // Update order to PAID/CONFIRMED + record payment_ref via metadata in snapshot
        updateOrderPostPayment(tenantId, orderId, paymentRef, verified);

        if (verified) {
            // Confirm inventory (commit the reservation)
            for (CartItemResponse item : cart.items()) {
                try { inventory.confirm(tenantId, item.productId(), item.variantId(), item.quantity()); }
                catch (Exception ignored) {}
            }
            // Record in finance ledger
            try { financePort.recordOrder(tenantId, orderId); } catch (Exception ignored) {}
            // Mark cart checked-out
            cartService.markCheckedOut(tenantId, request.cartId());
        }

        OrderResponse finalOrder = orderService.get(tenantId, storeId, orderId);
        return toCheckoutResponse(finalOrder, paymentRef);
    }

    private void updateOrderPostPayment(UUID tenantId, UUID orderId, String paymentRef, boolean verified) {
        String newPaymentStatus = verified ? "PAID" : "FAILED";
        String newOrderStatus = verified ? "CONFIRMED" : "PENDING";
        java.sql.Timestamp now = java.sql.Timestamp.from(java.time.Instant.now());
        jdbc.update("UPDATE commerce_orders SET payment_status = ?, status = ?, updated_at = ?, version = version + 1 "
                        + "WHERE tenant_id = ? AND id = ?",
                newPaymentStatus, newOrderStatus, now, tenantId, orderId);
        // Append status history
        jdbc.update("INSERT INTO commerce_order_status_history (id, tenant_id, order_id, from_status, to_status, "
                        + "from_payment, to_payment, reason, actor, created_at) "
                        + "VALUES (?, ?, ?, 'PENDING', ?, 'PENDING', ?, ?, ?, ?)",
                UUID.randomUUID(), tenantId, orderId, newOrderStatus, newPaymentStatus,
                "payment_ref=" + paymentRef + ",verified=" + verified, null, now);
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
