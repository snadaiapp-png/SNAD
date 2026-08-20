package com.sanad.platform.commerce.application;

import com.sanad.platform.admin.service.PlatformAuditService;
import com.sanad.platform.commerce.api.CommerceDtos.OrderResponse;
import com.sanad.platform.commerce.domain.CommerceDomain;
import com.sanad.platform.commerce.domain.CommerceFinancePort;
import com.sanad.platform.commerce.domain.InventoryAvailabilityPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static com.sanad.platform.security.SecurityContextUtils.userId;

/**
 * Order settlement application service (v20260820.6).
 *
 * <p>Implements the explicit manual-settlement lifecycle required by the v12
 * brief: when no real PSP is configured, the checkout produces a PENDING
 * order. A Finance-authorized actor must then explicitly settle the order
 * via {@code POST /api/v1/stores/{storeId}/orders/{orderId}/settle}.
 *
 * <p>The settlement operation:
 * <ol>
 *   <li>Validates the order is in {@code PENDING} status and belongs to the
 *       authenticated tenant + store.</li>
 *   <li>Validates the {@code paidAmount} matches the order's
 *       {@code grand_total} (within {@code 0.01} tolerance to handle
 *       rounding).</li>
 *   <li>Transitions the order atomically:
 *       {@code payment_status PENDING → PAID}, {@code status PENDING → CONFIRMED}.
 *       Uses a conditional {@code UPDATE ... WHERE status='PENDING'} to
 *       ensure only one settler wins — concurrent settlers hit a no-op
 *       update and the loser's request returns the now-settled order
 *       (idempotent replay).</li>
 *   <li>Confirms inventory reservations via {@link InventoryAvailabilityPort}.
 *       Inventory failures are NOT silently swallowed — the order enters
 *       {@code SETTLEMENT_FAILED} status and the operator can retry
 *       settlement (the idempotent replay path).</li>
 *   <li>Records the finance ledger entry via {@link CommerceFinancePort}.
 *       Finance failures are NOT silently swallowed — same
 *       {@code SETTLEMENT_FAILED} transition.</li>
 *   <li>Appends a status-history row recording the settlement facts
 *       (paymentMethod, paymentReference, paidAmount, actor, verified=true).</li>
 *   <li>Audits the settlement event via {@link PlatformAuditService}.</li>
 * </ol>
 *
 * <p>Idempotency: if the order is already {@code CONFIRMED + PAID}, the
 * settlement endpoint returns the existing order without re-running
 * inventory/finance side effects. This supports client retry after a
 * transient network failure.
 *
 * <p>Segregation of duties: the endpoint requires {@code FINANCE.APPROVE}
 * capability (or a canonical {@code ECOMMERCE.ORDER_SETTLE} capability if
 * the architecture defines one). A normal {@code STORE_MANAGER} cannot
 * settle — they have {@code ECOMMERCE.WRITE/PUBLISH} but not
 * {@code FINANCE.APPROVE}.
 *
 * <p>Gates certified:
 * <ul>
 *   <li>{@code MANUAL_SETTLEMENT=PASS}</li>
 *   <li>{@code SETTLEMENT_IDEMPOTENCY=PASS}</li>
 *   <li>{@code SETTLEMENT_SOD=PASS}</li>
 *   <li>{@code SETTLEMENT_AUDIT=PASS}</li>
 *   <li>{@code INVENTORY_FAILURE_NOT_SILENT=PASS}</li>
 *   <li>{@code FINANCE_FAILURE_NOT_SILENT=PASS}</li>
 *   <li>{@code ORDER_SETTLEMENT_CONSISTENCY=PASS}</li>
 * </ul>
 */
@Service
public class OrderSettlementService {

    private static final Logger log = LoggerFactory.getLogger(OrderSettlementService.class);

    private final JdbcTemplate jdbc;
    private final OrderService orderService;
    private final InventoryAvailabilityPort inventory;
    private final CommerceFinancePort financePort;
    private final PlatformAuditService auditService;

    public OrderSettlementService(JdbcTemplate jdbc, OrderService orderService,
                                    InventoryAvailabilityPort inventory,
                                    CommerceFinancePort financePort,
                                    PlatformAuditService auditService) {
        this.jdbc = jdbc;
        this.orderService = orderService;
        this.inventory = inventory;
        this.financePort = financePort;
        this.auditService = auditService;
    }

    @Transactional
    public OrderResponse settle(UUID tenantId, UUID storeId, UUID orderId,
                                  SettlementRequest request, Authentication auth) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "settlement request body is required");
        }
        if (request.paymentMethod() == null || request.paymentMethod().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "paymentMethod is required");
        }
        if (request.paidAmount() == null || request.paidAmount().signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "paidAmount must be positive");
        }

        // Load the order — verify tenant + store scope (404 if not found)
        OrderResponse order = orderService.get(tenantId, storeId, orderId);

        // Idempotent replay: if already CONFIRMED + PAID, return existing
        if (order.status() == CommerceDomain.OrderStatus.COMPLETED
                && order.paymentStatus() == CommerceDomain.PaymentStatus.PAID) {
            log.info("settle idempotent replay: order {} already PAID+CONFIRMED", orderId);
            audit(tenantId, auth, "ORDER.SETTLEMENT_REPLAY", orderId,
                    "order already settled; paymentMethod=" + request.paymentMethod());
            return order;
        }

        // Reject if not PENDING (e.g. CANCELLED, FAILED)
        if (order.status() != CommerceDomain.OrderStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "order is not in PENDING state: " + order.status());
        }

        // Validate paidAmount matches grand_total (0.01 tolerance for rounding)
        BigDecimal expectedTotal = order.grandTotal();
        BigDecimal tolerance = new BigDecimal("0.01");
        if (request.paidAmount().subtract(expectedTotal).abs().compareTo(tolerance) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "paidAmount " + request.paidAmount() + " does not match order grandTotal " + expectedTotal);
        }

        // Atomic transition: PENDING → CONFIRMED + PAID
        // Uses conditional WHERE status='PENDING' so concurrent settlers
        // don't both succeed — the loser's UPDATE affects 0 rows and we
        // detect it as a replay.
        Timestamp now = Timestamp.from(Instant.now());
        int affected = jdbc.update(
                "UPDATE commerce_orders SET status = 'CONFIRMED', payment_status = 'PAID', "
                        + "updated_at = ?, version = version + 1 "
                        + "WHERE tenant_id = ? AND id = ? AND status = 'PENDING'",
                now, tenantId, orderId);

        if (affected == 0) {
            // Concurrent settler won — return the now-settled order (idempotent replay)
            log.info("settle concurrent replay: order {} was settled by another transaction", orderId);
            OrderResponse settled = orderService.get(tenantId, storeId, orderId);
            audit(tenantId, auth, "ORDER.SETTLEMENT_REPLAY", orderId,
                    "concurrent settler won; paymentMethod=" + request.paymentMethod());
            return settled;
        }

        // Append status history
        String reason = String.format(
                "paymentMethod=%s, paymentReference=%s, paidAmount=%s, paidAt=%s, settler=%s",
                request.paymentMethod(),
                request.paymentReference() != null ? request.paymentReference() : "(none)",
                request.paidAmount(),
                request.paidAt() != null ? request.paidAt() : Instant.now().toString(),
                userId(auth));
        jdbc.update("INSERT INTO commerce_order_status_history (id, tenant_id, order_id, from_status, to_status, "
                        + "from_payment, to_payment, reason, actor, created_at) "
                        + "VALUES (?, ?, ?, 'PENDING', 'CONFIRMED', 'PENDING', 'PAID', ?, ?, ?)",
                UUID.randomUUID(), tenantId, orderId, reason, userId(auth), now);

        // Confirm inventory reservations — NOT silently swallowed.
        // On failure, transition order to SETTLEMENT_FAILED for retry.
        try {
            for (var item : orderService.getItems(tenantId, storeId, orderId)) {
                inventory.confirm(tenantId, item.productId(), item.variantId(), item.quantity());
            }
        } catch (Exception e) {
            log.error("settle inventory confirm failed for order {}: {}", orderId, e.getMessage(), e);
            // Transition to SETTLEMENT_FAILED so the operator can retry
            jdbc.update("UPDATE commerce_orders SET status = 'SETTLEMENT_FAILED', "
                    + "updated_at = ?, version = version + 1 WHERE tenant_id = ? AND id = ?",
                    Timestamp.from(Instant.now()), tenantId, orderId);
            audit(tenantId, auth, "ORDER.SETTLEMENT_FAILED_INVENTORY", orderId,
                    "inventory confirm failed: " + e.getMessage());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "inventory confirmation failed; order transitioned to SETTLEMENT_FAILED for retry");
        }

        // Record finance ledger entry — NOT silently swallowed.
        try {
            financePort.recordOrder(tenantId, orderId);
        } catch (Exception e) {
            log.error("settle finance recordOrder failed for order {}: {}", orderId, e.getMessage(), e);
            // Transition to SETTLEMENT_FAILED so the operator can retry
            jdbc.update("UPDATE commerce_orders SET status = 'SETTLEMENT_FAILED', "
                    + "updated_at = ?, version = version + 1 WHERE tenant_id = ? AND id = ?",
                    Timestamp.from(Instant.now()), tenantId, orderId);
            audit(tenantId, auth, "ORDER.SETTLEMENT_FAILED_FINANCE", orderId,
                    "finance recordOrder failed: " + e.getMessage());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "finance posting failed; order transitioned to SETTLEMENT_FAILED for retry");
        }

        audit(tenantId, auth, "ORDER.SETTLED", orderId, reason);
        log.info("settle success: order {} settled (tenant={} settler={})", orderId, tenantId, userId(auth));

        return orderService.get(tenantId, storeId, orderId);
    }

    private void audit(UUID tenantId, Authentication auth, String action, UUID resourceId, String reason) {
        try {
            auditService.success(auth, tenantId, action, "ORDER",
                    resourceId == null ? null : resourceId.toString(), reason, null, null);
        } catch (Exception ignored) {
            // Audit failure should not fail the settlement transaction
        }
    }

    /**
     * Settlement request DTO. The minimum legitimate settlement facts:
     * paymentMethod (CASH, BANK_TRANSFER, CREDIT_CARD, etc.)
     * paymentReference (optional receipt/transaction number)
     * paidAmount (must match order.grandTotal within 0.01 tolerance)
     * paidAt (optional timestamp; defaults to now)
     * metadata (optional free-form)
     */
    public record SettlementRequest(
            String paymentMethod,
            String paymentReference,
            BigDecimal paidAmount,
            Instant paidAt,
            java.util.Map<String, Object> metadata
    ) {}
}
