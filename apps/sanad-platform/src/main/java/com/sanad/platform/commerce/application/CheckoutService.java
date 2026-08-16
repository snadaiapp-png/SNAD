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
import java.util.UUID;

/**
 * Checkout application service (v20260816.5).
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
 * <p>Supports idempotency: if a request with the same {@code idempotencyKey}
 * is replayed, the existing order is returned without re-charging the
 * customer.
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

        // Idempotency check
        if (request.idempotencyKey() != null && !request.idempotencyKey().isBlank()) {
            OrderResponse existing = orderService.findByIdempotencyKey(tenantId, storeId, request.idempotencyKey());
            if (existing != null) {
                return toCheckoutResponse(existing, "");
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
        // Create the order (PENDING)
        OrderResponse order = orderService.createOrderAtomically(
                tenantId, storeId, request.cartId(), orderNumber, customerRef, snapshot,
                cart.currency(), subtotal, discount, tax, shipping, grandTotal,
                null, request.idempotencyKey(), auth);

        // Create payment intent + verify (simulated adapter returns true immediately)
        String paymentRef = paymentPort.createPaymentIntent(tenantId, order.id(), grandTotal, cart.currency());
        boolean verified = paymentPort.verifyPayment(tenantId, paymentRef);

        // Update order to PAID/CONFIRMED + record payment_ref via metadata in snapshot
        updateOrderPostPayment(tenantId, order.id(), paymentRef, verified);

        if (verified) {
            // Confirm inventory (commit the reservation)
            for (CartItemResponse item : cart.items()) {
                try { inventory.confirm(tenantId, item.productId(), item.variantId(), item.quantity()); }
                catch (Exception ignored) {}
            }
            // Record in finance ledger
            try { financePort.recordOrder(tenantId, order.id()); } catch (Exception ignored) {}
            // Mark cart checked-out
            cartService.markCheckedOut(tenantId, request.cartId());
        }

        OrderResponse finalOrder = orderService.get(tenantId, storeId, order.id());
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
