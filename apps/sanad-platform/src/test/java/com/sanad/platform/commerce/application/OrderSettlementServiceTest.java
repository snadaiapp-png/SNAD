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

        when(jdbc.queryForObject(anyString(), any(RowMapper.class), eq(tenantId), eq(orderId)))
                .thenReturn(new OrderRow(orderId, storeId, "PENDING", "PENDING",
                        new BigDecimal("250.00"), "SAR"));
        when(orderService.getItems(eq(tenantId), eq(storeId), eq(orderId)))
                .thenReturn(List.of(new CommerceDtos.OrderItemResponse(
                        UUID.randomUUID(), orderId, productId, null,
                        "Test Product", "SKU-001", null,
                        2, new BigDecimal("100.00"), BigDecimal.ZERO,
                        new BigDecimal("20.00"), new BigDecimal("220.00"), Instant.now())));
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
        when(jdbc.update(anyString(), any(), eq(tenantId), eq(orderId))).thenReturn(1);
        when(jdbc.update(anyString(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(1);
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
        verify(inventory, times(1)).confirm(eq(tenantId), eq(productId), eq(null), eq(2));
        verify(financePort, times(1)).markOrderSettled(eq(tenantId), eq(orderId), eq(new BigDecimal("250.00")));
        verify(jdbc, atLeast(1)).update(anyString(), any(), eq(tenantId), eq(orderId));
        verify(auditService, times(1)).success(any(), any(), eq("ORDER.SETTLED"), anyString(), anyString(), any(), any(), any());
    }

    @Test
    void settle_sequentialReplay_confirmedPaid_returnsExisting_noSideEffects() {
        when(jdbc.queryForObject(anyString(), any(RowMapper.class), eq(tenantId), eq(orderId)))
                .thenReturn(new OrderRow(orderId, storeId, "CONFIRMED", "PAID",
                        new BigDecimal("250.00"), "SAR"));

        SettlementRequest req = new SettlementRequest(
                "CASH", "RECEIPT-001", new BigDecimal("250.00"), Instant.now(), null);

        var result = service.settle(tenantId, storeId, orderId, req, mockAuth());

        assertThat(result.status()).isEqualTo(CommerceDomain.OrderStatus.CONFIRMED);
        verify(inventory, never()).confirm(any(), any(), any(), anyInt());
        verify(financePort, never()).markOrderSettled(any(), any(), any());
        verify(jdbc, never()).update(contains("SET status = 'CONFIRMED'"), any(), eq(tenantId), eq(orderId));
        verify(auditService, times(1)).success(any(), any(), eq("ORDER.SETTLEMENT_REPLAY"), anyString(), anyString(), any(), any(), any());
    }

    @Test
    void settle_concurrentReplay_lostRace_returnsExisting() {
        when(jdbc.update(anyString(), any(), eq(tenantId), eq(orderId))).thenReturn(0);
        SettlementRequest req = new SettlementRequest(
                "CASH", "RECEIPT-001", new BigDecimal("250.00"), Instant.now(), null);
        var result = service.settle(tenantId, storeId, orderId, req, mockAuth());
        assertThat(result).isNotNull();
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
                    assertThat(rse.getMessage()).contains("paidAmount").contains("does not match");
                });
        verify(inventory, never()).confirm(any(), any(), any(), anyInt());
        verify(financePort, never()).markOrderSettled(any(), any(), any());
    }

    @Test
    void settle_missingPaymentMethod_returns400() {
        SettlementRequest req = new SettlementRequest(
                null, "RECEIPT-001", new BigDecimal("250.00"), Instant.now(), null);
        assertThatThrownBy(() -> service.settle(tenantId, storeId, orderId, req, mockAuth()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(t -> assertThat(((ResponseStatusException) t).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void settle_nonPositiveAmount_returns400() {
        SettlementRequest req = new SettlementRequest(
                "CASH", "RECEIPT-001", BigDecimal.ZERO, Instant.now(), null);
        assertThatThrownBy(() -> service.settle(tenantId, storeId, orderId, req, mockAuth()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(t -> assertThat(((ResponseStatusException) t).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void settle_cancelledOrder_returns409() {
        when(jdbc.queryForObject(anyString(), any(RowMapper.class), eq(tenantId), eq(orderId)))
                .thenReturn(new OrderRow(orderId, storeId, "CANCELLED", "PENDING",
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
                .satisfies(t -> assertThat(((ResponseStatusException) t).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void settle_inventoryFailure_throws_rollsBack_noFinanceCall() {
        doThrow(new RuntimeException("inventory DB down")).when(inventory).confirm(any(), any(), any(), anyInt());
        SettlementRequest req = new SettlementRequest(
                "CASH", "RECEIPT-001", new BigDecimal("250.00"), Instant.now(), null);
        assertThatThrownBy(() -> service.settle(tenantId, storeId, orderId, req, mockAuth()))
                .isInstanceOf(RuntimeException.class).hasMessageContaining("inventory DB down");
        verify(inventory, times(1)).confirm(any(), any(), any(), anyInt());
        verify(financePort, never()).markOrderSettled(any(), any(), any());
        verify(jdbc, never()).update(contains("SET status = 'CONFIRMED'"), any(), eq(tenantId), eq(orderId));
        verify(auditService, never()).success(any(), any(), eq("ORDER.SETTLED"), anyString(), anyString(), any(), any(), any());
    }

    @Test
    void settle_financeFailure_throws_rollsBack_noUpdateToConfirmed() {
        doThrow(new RuntimeException("finance DB down"))
                .when(financePort).markOrderSettled(any(), any(), any());
        SettlementRequest req = new SettlementRequest(
                "CASH", "RECEIPT-001", new BigDecimal("250.00"), Instant.now(), null);
        assertThatThrownBy(() -> service.settle(tenantId, storeId, orderId, req, mockAuth()))
                .isInstanceOf(RuntimeException.class).hasMessageContaining("finance DB down");
        verify(inventory, times(1)).confirm(any(), any(), any(), anyInt());
        verify(financePort, times(1)).markOrderSettled(eq(tenantId), eq(orderId), eq(new BigDecimal("250.00")));
        verify(jdbc, never()).update(contains("SET status = 'CONFIRMED'"), any(), eq(tenantId), eq(orderId));
        verify(auditService, never()).success(any(), any(), eq("ORDER.SETTLED"), anyString(), anyString(), any(), any(), any());
    }

    @Test
    void settle_storeMismatch_returns404() {
        UUID differentStore = UUID.randomUUID();
        when(jdbc.queryForObject(anyString(), any(RowMapper.class), eq(tenantId), eq(orderId)))
                .thenReturn(new OrderRow(orderId, differentStore, "PENDING", "PENDING",
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
        verify(inventory, never()).confirm(any(), any(), any(), anyInt());
        verify(financePort, never()).markOrderSettled(any(), any(), any());
    }

    @Test
    void settle_amountWithin0_01Tolerance_succeeds() {
        SettlementRequest req = new SettlementRequest(
                "CASH", "RECEIPT-001", new BigDecimal("250.005"), Instant.now(), null);
        var result = service.settle(tenantId, storeId, orderId, req, mockAuth());
        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(CommerceDomain.OrderStatus.CONFIRMED);
    }

    private Authentication mockAuth() {
        Authentication auth = mock(Authentication.class);
        UUID settlerId = UUID.randomUUID();
        when(auth.getDetails()).thenReturn(Map.of("user_id", settlerId.toString()));
        return auth;
    }
}
