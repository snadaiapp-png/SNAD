package com.sanad.platform.commerce.application;

import com.sanad.platform.admin.service.PlatformAuditService;
import com.sanad.platform.commerce.api.CommerceDtos;
import com.sanad.platform.commerce.application.OrderSettlementService.OrderRow;
import com.sanad.platform.commerce.application.OrderSettlementService.SettlementRequest;
import com.sanad.platform.commerce.domain.CommerceDomain;
import com.sanad.platform.commerce.domain.CommerceFinancePort;
import com.sanad.platform.commerce.domain.InventoryAvailabilityPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link OrderSettlementService}.
 *
 * <p>Coverage per the v12.1 brief:
 * <ul>
 *   <li>settle success (PENDING → CONFIRMED+PAID)</li>
 *   <li>sequential replay (CONFIRMED+PAID → return existing, no side effects)</li>
 *   <li>concurrent replay (lost race → return existing)</li>
 *   <li>invalid amount (paidAmount ≠ grandTotal → 400)</li>
 *   <li>STORE_MANAGER denied (handled at controller via @RequireCapability)</li>
 *   <li>FINANCE_APPROVER allowed (capability check at controller layer)</li>
 *   <li>Inventory failure rollback (inventory.confirm throws → transaction rolls back; verify side effects not duplicated)</li>
 *   <li>Finance failure rollback (financePort.recordOrder throws → same)</li>
 *   <li>order not found → 404</li>
 *   <li>order in non-PENDING non-CONFIRMED state (e.g. CANCELLED) → 409</li>
 * </ul>
 *
 * <p>Tests mock JdbcTemplate, OrderService, InventoryAvailabilityPort,
 * CommerceFinancePort, and PlatformAuditService. No Spring context —
 * hermetic and fast.
 */
class OrderSettlementServiceTest {

    private JdbcTemplate jdbc;
    private OrderService orderService;
    private InventoryAvailabilityPort inventory;
    private CommerceFinancePort financePort;
    private PlatformAuditService auditService;
    private OrderSettlementService service;

    private UUID tenantId;
    private UUID storeId;
    private UUID orderId;
    private UUID productId;
    private UUID cartId;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        orderService = mock(OrderService.class);
        inventory = mock(InventoryAvailabilityPort.class);
        financePort = mock(CommerceFinancePort.class);
        auditService = mock(PlatformAuditService.class);
        service = new OrderSettlementService(jdbc, orderService, inventory, financePort, auditService);

        tenantId = UUID.randomUUID();
        storeId = UUID.randomUUID();
        orderId = UUID.randomUUID();
        productId = UUID.randomUUID();
        cartId = UUID.randomUUID();

        // Default: SELECT FOR UPDATE returns a PENDING order row
        when(jdbc.queryForObject(anyString(), any(RowMapper.class), eq(tenantId), eq(orderId)))
                .thenReturn(new OrderRow(
                        orderId, storeId, "PENDING", "PENDING",
                        new BigDecimal("250.00"), "SAR"));
        // Default: order items = 1
        when(orderService.getItems(eq(tenantId), eq(storeId), eq(orderId)))
                .thenReturn(List.of(new CommerceDtos.OrderItemResponse(
                        UUID.randomUUID(), orderId, productId, null,
                        "Test Product", "SKU-001", null,
                        2, new BigDecimal("100.00"), BigDecimal.ZERO,
                        new BigDecimal("20.00"), new BigDecimal("220.00"),
                        Instant.now())));
        // Default: orderService.get returns the CONFIRMED+PAID order
        when(orderService.get(eq(tenantId), eq(storeId), eq(orderId)))
                .thenReturn(new CommerceDtos.OrderResponse(
                        orderId, tenantId, storeId, "ORD-202608-00001", cartId,
                        "guest-001", null, "SAR",
                        new BigDecimal("220.00"), BigDecimal.ZERO,
                        new BigDecimal("20.00"), new BigDecimal("10.00"),
                        new BigDecimal("250.00"),
                        CommerceDomain.PaymentStatus.PAID,
                        CommerceDomain.FulfillmentStatus.UNFULFILLED,
                        CommerceDomain.OrderStatus.CONFIRMED,
                        "idem-key-1", 1L, Instant.now(), Instant.now()));
        // Default: UPDATE affects 1 row
        when(jdbc.update(anyString(), any(), eq(tenantId), eq(orderId))).thenReturn(1);
        // status history insert
        when(jdbc.update(anyString(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(1);
        // audit success no-op
        doNothing().when(auditService).success(any(), any(), anyString(), anyString(), anyString(), any(), any(), any());
    }

    @Test
    void settle_success_transitionsToConfirmedPaid() {
        SettlementRequest req = new SettlementRequest(
                "CASH", "RECEIPT-001", new BigDecimal("250.00"), Instant.now(), null);

        var result = service.settle(tenantId, storeId, orderId, req, mockAuth());

        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(CommerceDomain.OrderStatus.CONFIRMED);
        assertThat(result.paymentStatus()).isEqualTo(CommerceDomain.PaymentStatus.PAID);
        // Verify inventory confirmed once per item
        verify(inventory, times(1)).confirm(eq(tenantId), eq(productId), eq(null), eq(2));
        // Verify finance recorded once
        verify(financePort, times(1)).recordOrder(eq(tenantId), eq(orderId));
        // Verify UPDATE was called (transition)
        verify(jdbc, atLeast(1)).update(anyString(), any(), eq(tenantId), eq(orderId));
        // Verify audit fired
        verify(auditService, times(1)).success(any(), any(), eq("ORDER.SETTLED"), anyString(), anyString(), any(), any(), any());
    }

    @Test
    void settle_sequentialReplay_confirmedPaid_returnsExisting_noSideEffects() {
        // Override: SELECT FOR UPDATE returns a CONFIRMED+PAID order (already settled)
        when(jdbc.queryForObject(anyString(), any(RowMapper.class), eq(tenantId), eq(orderId)))
                .thenReturn(new OrderRow(
                        orderId, storeId, "CONFIRMED", "PAID",
                        new BigDecimal("250.00"), "SAR"));

        SettlementRequest req = new SettlementRequest(
                "CASH", "RECEIPT-001", new BigDecimal("250.00"), Instant.now(), null);

        var result = service.settle(tenantId, storeId, orderId, req, mockAuth());

        // Idempotent replay — returns existing order
        assertThat(result.status()).isEqualTo(CommerceDomain.OrderStatus.CONFIRMED);
        // NO inventory confirmation replay
        verify(inventory, never()).confirm(any(), any(), any(), anyInt());
        // NO finance recording replay
        verify(financePort, never()).recordOrder(any(), any());
        // NO UPDATE to CONFIRMED+PAID (already there)
        verify(jdbc, never()).update(contains("SET status = 'CONFIRMED'"), any(), eq(tenantId), eq(orderId));
        // Audit ORDER.SETTLEMENT_REPLAY fired
        verify(auditService, times(1)).success(any(), any(), eq("ORDER.SETTLEMENT_REPLAY"), anyString(), anyString(), any(), any(), any());
    }

    @Test
    void settle_concurrentReplay_lostRace_returnsExisting() {
        // SELECT FOR UPDATE returns PENDING (we acquire the lock first)
        // (default mock already returns PENDING)
        // But the conditional UPDATE affects 0 rows — concurrent settler won
        when(jdbc.update(anyString(), any(), eq(tenantId), eq(orderId))).thenReturn(0);

        SettlementRequest req = new SettlementRequest(
                "CASH", "RECEIPT-001", new BigDecimal("250.00"), Instant.now(), null);

        var result = service.settle(tenantId, storeId, orderId, req, mockAuth());

        // Returns existing order (the now-settled one read via orderService.get)
        assertThat(result).isNotNull();
        // Audit fired as REPLAY (concurrent settler won)
        verify(auditService, times(1)).success(any(), any(), eq("ORDER.SETTLEMENT_REPLAY"), anyString(), anyString(), any(), any(), any());
    }

    @Test
    void settle_invalidAmount_returns400() {
        SettlementRequest req = new SettlementRequest(
                "CASH", "RECEIPT-001", new BigDecimal("999.99"), Instant.now(), null);

        assertThatThrownBy(() -> service.settle(tenantId, storeId, orderId, req, mockAuth()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(t -> {
                    var rse = (ResponseStatusException) t;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(rse.getMessage()).contains("paidAmount");
                    assertThat(rse.getMessage()).contains("does not match");
                });
        // No inventory/finance side effects when amount is invalid
        verify(inventory, never()).confirm(any(), any(), any(), anyInt());
        verify(financePort, never()).recordOrder(any(), any());
    }

    @Test
    void settle_missingPaymentMethod_returns400() {
        SettlementRequest req = new SettlementRequest(
                null, "RECEIPT-001", new BigDecimal("250.00"), Instant.now(), null);

        assertThatThrownBy(() -> service.settle(tenantId, storeId, orderId, req, mockAuth()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(t -> {
                    var rse = (ResponseStatusException) t;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                });
    }

    @Test
    void settle_nonPositiveAmount_returns400() {
        SettlementRequest req = new SettlementRequest(
                "CASH", "RECEIPT-001", BigDecimal.ZERO, Instant.now(), null);

        assertThatThrownBy(() -> service.settle(tenantId, storeId, orderId, req, mockAuth()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(t -> {
                    var rse = (ResponseStatusException) t;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                });
    }

    @Test
    void settle_cancelledOrder_returns409() {
        // Order is CANCELLED — not PENDING, not CONFIRMED+PAID
        when(jdbc.queryForObject(anyString(), any(RowMapper.class), eq(tenantId), eq(orderId)))
                .thenReturn(new OrderRow(
                        orderId, storeId, "CANCELLED", "PENDING",
                        new BigDecimal("250.00"), "SAR"));

        SettlementRequest req = new SettlementRequest(
                "CASH", "RECEIPT-001", new BigDecimal("250.00"), Instant.now(), null);

        assertThatThrownBy(() -> service.settle(tenantId, storeId, orderId, req, mockAuth()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(t -> {
                    var rse = (ResponseStatusException) t;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(rse.getMessage()).contains("not in PENDING state");
                });
    }

    @Test
    void settle_orderNotFound_returns404() {
        when(jdbc.queryForObject(anyString(), any(RowMapper.class), eq(tenantId), eq(orderId)))
                .thenThrow(new org.springframework.dao.EmptyResultDataAccessException(1));

        SettlementRequest req = new SettlementRequest(
                "CASH", "RECEIPT-001", new BigDecimal("250.00"), Instant.now(), null);

        assertThatThrownBy(() -> service.settle(tenantId, storeId, orderId, req, mockAuth()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(t -> {
                    var rse = (ResponseStatusException) t;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                });
    }

    @Test
    void settle_inventoryFailure_throws_rollsBack_noFinanceCall() {
        // inventory.confirm throws
        doThrow(new RuntimeException("inventory DB down"))
                .when(inventory).confirm(any(), any(), any(), anyInt());

        SettlementRequest req = new SettlementRequest(
                "CASH", "RECEIPT-001", new BigDecimal("250.00"), Instant.now(), null);

        assertThatThrownBy(() -> service.settle(tenantId, storeId, orderId, req, mockAuth()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("inventory DB down");

        // Inventory was attempted
        verify(inventory, times(1)).confirm(any(), any(), any(), anyInt());
        // Finance was NEVER called (rolled back before reaching it)
        verify(financePort, never()).recordOrder(any(), any());
        // NO UPDATE to CONFIRMED+PAID (transaction rolled back)
        verify(jdbc, never()).update(contains("SET status = 'CONFIRMED'"), any(), eq(tenantId), eq(orderId));
        // NO audit ORDER.SETTLED (failed before)
        verify(auditService, never()).success(any(), any(), eq("ORDER.SETTLED"), anyString(), anyString(), any(), any(), any());
    }

    @Test
    void settle_financeFailure_throws_rollsBack_noUpdateToConfirmed() {
        // financePort.recordOrder throws
        doThrow(new RuntimeException("finance DB down"))
                .when(financePort).recordOrder(any(), any());

        SettlementRequest req = new SettlementRequest(
                "CASH", "RECEIPT-001", new BigDecimal("250.00"), Instant.now(), null);

        assertThatThrownBy(() -> service.settle(tenantId, storeId, orderId, req, mockAuth()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("finance DB down");

        // Inventory was called (executed before finance)
        verify(inventory, times(1)).confirm(any(), any(), any(), anyInt());
        // Finance was attempted
        verify(financePort, times(1)).recordOrder(any(), any());
        // NO UPDATE to CONFIRMED+PAID (transaction rolled back)
        verify(jdbc, never()).update(contains("SET status = 'CONFIRMED'"), any(), eq(tenantId), eq(orderId));
        // NO audit ORDER.SETTLED
        verify(auditService, never()).success(any(), any(), eq("ORDER.SETTLED"), anyString(), anyString(), any(), any(), any());
    }

    @Test
    void settle_storeMismatch_returns404() {
        // Order exists but belongs to a different store
        UUID differentStore = UUID.randomUUID();
        when(jdbc.queryForObject(anyString(), any(RowMapper.class), eq(tenantId), eq(orderId)))
                .thenReturn(new OrderRow(
                        orderId, differentStore, "PENDING", "PENDING",
                        new BigDecimal("250.00"), "SAR"));

        SettlementRequest req = new SettlementRequest(
                "CASH", "RECEIPT-001", new BigDecimal("250.00"), Instant.now(), null);

        assertThatThrownBy(() -> service.settle(tenantId, storeId, orderId, req, mockAuth()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(t -> {
                    var rse = (ResponseStatusException) t;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(rse.getMessage()).contains("not found in this store");
                });
        // No side effects
        verify(inventory, never()).confirm(any(), any(), any(), anyInt());
        verify(financePort, never()).recordOrder(any(), any());
    }

    @Test
    void settle_amountWithin0_01Tolerance_succeeds() {
        // grandTotal=250.00, paidAmount=250.005 (within 0.01 tolerance)
        SettlementRequest req = new SettlementRequest(
                "CASH", "RECEIPT-001", new BigDecimal("250.005"), Instant.now(), null);

        var result = service.settle(tenantId, storeId, orderId, req, mockAuth());

        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(CommerceDomain.OrderStatus.CONFIRMED);
    }

    /**
     * Construct a mock Authentication whose details Map contains a user_id.
     * The production SecurityContextUtils.userId(auth) reads
     * auth.getDetails() as a Map and extracts "user_id".
     */
    private Authentication mockAuth() {
        Authentication auth = mock(Authentication.class);
        UUID settlerId = UUID.randomUUID();
        when(auth.getDetails()).thenReturn(Map.of("user_id", settlerId.toString()));
        return auth;
    }
}
