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
 * Order settlement application service.
 *
 * <p>Safe transaction order:
 * lock order → validate → inventory → Finance settlement → Commerce state
 * transition → history/audit → commit. Inventory or Finance failure therefore
 * prevents a half-settled Commerce order.
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

        OrderRow orderRow;
        try {
            orderRow = jdbc.queryForObject(
                    "SELECT id, store_id, status, payment_status, grand_total, currency "
                            + "FROM commerce_orders WHERE tenant_id = ? AND id = ? "
                            + "FOR UPDATE",
                    (rs, rowNum) -> new OrderRow(
                            rs.getObject("id", UUID.class),
                            rs.getObject("store_id", UUID.class),
                            rs.getString("status"),
                            rs.getString("payment_status"),
                            rs.getBigDecimal("grand_total"),
                            rs.getString("currency")),
                    tenantId, orderId);
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "order not found: " + orderId);
        }
        if (orderRow == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "order not found: " + orderId);
        }
        if (!storeId.equals(orderRow.storeId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "order not found in this store: " + orderId);
        }

        boolean alreadyPaid = "PAID".equals(orderRow.paymentStatus())
                && ("CONFIRMED".equals(orderRow.status()) || "COMPLETED".equals(orderRow.status())
                    || "PAID".equals(orderRow.status()));
        if (alreadyPaid) {
            log.info("settle idempotent replay: order {} already {}+PAID — returning existing order",
                    orderId, orderRow.status());
            audit(tenantId, auth, "ORDER.SETTLEMENT_REPLAY", orderId,
                    "order already " + orderRow.status() + "+PAID; paymentMethod=" + request.paymentMethod());
            return orderService.get(tenantId, storeId, orderId);
        }

        if (!"PENDING".equals(orderRow.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "order is not in PENDING state: " + orderRow.status());
        }

        BigDecimal expectedTotal = orderRow.grandTotal();
        BigDecimal tolerance = new BigDecimal("0.01");
        if (request.paidAmount().subtract(expectedTotal).abs().compareTo(tolerance) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "paidAmount " + request.paidAmount() + " does not match order grandTotal " + expectedTotal);
        }

        for (var item : orderService.getItems(tenantId, storeId, orderId)) {
            inventory.confirm(tenantId, item.productId(), item.variantId(), item.quantity());
        }

        // The Finance operation owns create/link/settle idempotency. It must
        // complete before Commerce can become PAID.
        financePort.markOrderSettled(tenantId, orderId, request.paidAmount());

        Timestamp now = Timestamp.from(Instant.now());
        int affected = jdbc.update(
                "UPDATE commerce_orders SET status = 'CONFIRMED', payment_status = 'PAID', "
                        + "updated_at = ?, version = version + 1 "
                        + "WHERE tenant_id = ? AND id = ? AND status = 'PENDING'",
                now, tenantId, orderId);
        if (affected == 0) {
            log.info("settle concurrent replay: order {} was settled by another transaction", orderId);
            audit(tenantId, auth, "ORDER.SETTLEMENT_REPLAY", orderId,
                    "concurrent settler won; paymentMethod=" + request.paymentMethod());
            return orderService.get(tenantId, storeId, orderId);
        }

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

        audit(tenantId, auth, "ORDER.SETTLED", orderId, reason);
        log.info("settle success: order {} settled (tenant={} settler={})", orderId, tenantId, userId(auth));
        return orderService.get(tenantId, storeId, orderId);
    }

    private void audit(UUID tenantId, Authentication auth, String action, UUID resourceId, String reason) {
        try {
            auditService.success(auth, tenantId, action, "ORDER",
                    resourceId == null ? null : resourceId.toString(), reason, null, null);
        } catch (Exception ignored) {
            // Audit is cross-cutting and does not alter the transactional business invariant.
        }
    }

    record OrderRow(UUID id, UUID storeId, String status, String paymentStatus,
                    BigDecimal grandTotal, String currency) {}

    public record SettlementRequest(
            String paymentMethod,
            String paymentReference,
            BigDecimal paidAmount,
            Instant paidAt,
            java.util.Map<String, Object> metadata
    ) {}
}
