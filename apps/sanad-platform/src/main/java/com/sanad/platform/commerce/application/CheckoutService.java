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

        // Create payment intent + verify.
        // v20260820.6: DefaultNoOpPaymentAdapter returns null/false when no real
        // PSP is configured. The order is left in PENDING status awaiting
        // explicit settlement. SimulatedPaymentAdapter (gated by property)
        // returns sim_pi_* and verifies=true for dev/test.
        String paymentRef = paymentPort.createPaymentIntent(tenantId, orderId, grandTotal, cart.currency());
        boolean verified;
        if (paymentRef == null) {
            // No PSP configured — never auto-verify, never auto-PAID.
            // Order stays PENDING; cart stays ACTIVE so a follow-up settlement
            // can drive it to PAID via an explicit endpoint.
            verified = false;
        } else {
            verified = paymentPort.verifyPayment(tenantId, paymentRef);
        }

        // Update order payment + status. v20260820.6 introduces a third state
        // for "no PSP / awaiting settlement" — payment_status=PENDING (not FAILED),
        // order_status=PENDING.
        updateOrderPostPayment(tenantId, orderId, paymentRef, verified);

        // v20260820.6 (v12 brief): LOCK THE CART IMMEDIATELY after order creation.
        // The cart invariant (uk_commerce_orders_tenant_cart) already guarantees
        // one order per cart at the DB level, but the cart's status must reflect
        // "converted to an order" — even if payment is still PENDING.
        // Mark CHECKED_OUT immediately so subsequent cart operations reject.
        // If payment later settles, the order transitions to PAID+CONFIRMED
        // via OrderSettlementService — but the cart stays CHECKED_OUT regardless.
        cartService.markCheckedOut(tenantId, request.cartId());

        if (verified) {
            // Confirm inventory (commit the reservation) — NOT silently swallowed.
            // On failure, the order must enter SETTLEMENT_FAILED for retry.
            // Note: for the no-PSP path, inventory confirmation happens at
            // settlement time (OrderSettlementService), not at checkout.
            for (CartItemResponse item : cart.items()) {
                try { inventory.confirm(tenantId, item.productId(), item.variantId(), item.quantity()); }
                catch (Exception e) {
                    log.error("inventory confirm failed for order {}: {}", orderId, e.getMessage(), e);
                    // Transition to SETTLEMENT_FAILED so the operator can retry
                    try {
                        jdbc.update("UPDATE commerce_orders SET status = 'SETTLEMENT_FAILED', "
                                + "updated_at = ?, version = version + 1 WHERE tenant_id = ? AND id = ?",
                                java.sql.Timestamp.from(java.time.Instant.now()), tenantId, orderId);
                    } catch (Exception ex) { log.error("failed to mark SETTLEMENT_FAILED: {}", ex.getMessage()); }
                    throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                            "inventory confirmation failed; order transitioned to SETTLEMENT_FAILED for retry");
                }
            }
            // Record in finance ledger — NOT silently swallowed.
            try {
                financePort.recordOrder(tenantId, orderId);
            } catch (Exception e) {
                log.error("finance recordOrder failed for order {}: {}", orderId, e.getMessage(), e);
                try {
                    jdbc.update("UPDATE commerce_orders SET status = 'SETTLEMENT_FAILED', "
                            + "updated_at = ?, version = version + 1 WHERE tenant_id = ? AND id = ?",
                            java.sql.Timestamp.from(java.time.Instant.now()), tenantId, orderId);
                } catch (Exception ex) { log.error("failed to mark SETTLEMENT_FAILED: {}", ex.getMessage()); }
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "finance posting failed; order transitioned to SETTLEMENT_FAILED for retry");
            }
        } else if (paymentRef == null) {
            // No PSP configured — order is PENDING, cart is CHECKED_OUT (locked
            // above). Manual settlement via POST /api/v1/stores/{storeId}/orders/{orderId}/settle
            // will drive the order to PAID+CONFIRMED with inventory + finance
            // side-effects persisted atomically.
            log.info("checkout: order {} created in PENDING state — no PSP configured; "
                    + "manual settlement required (tenant={})", orderId, tenantId);
        } else {
            // Real PSP declined (paymentRef set, verified=false) — order is FAILED.
            log.warn("checkout: payment verification failed for order {} (tenant={})", orderId, tenantId);
        }

        OrderResponse finalOrder = orderService.get(tenantId, storeId, orderId);
        return toCheckoutResponse(finalOrder, paymentRef);
    }

    private void updateOrderPostPayment(UUID tenantId, UUID orderId, String paymentRef, boolean verified) {
        // v20260820.6: Three payment states:
        //   paymentRef=null + verified=false → PENDING (no PSP configured, awaiting settlement)
        //   paymentRef!=null + verified=true → PAID + CONFIRMED
        //   paymentRef!=null + verified=false → FAILED + PENDING
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
        // Append status history
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
