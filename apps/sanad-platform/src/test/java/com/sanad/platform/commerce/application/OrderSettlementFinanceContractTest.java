package com.sanad.platform.commerce.application;

import com.sanad.platform.admin.service.PlatformAuditService;
import com.sanad.platform.commerce.api.CommerceDtos;
import com.sanad.platform.commerce.domain.CommerceDomain;
import com.sanad.platform.commerce.domain.CommerceFinancePort;
import com.sanad.platform.commerce.domain.InventoryAvailabilityPort;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.core.Authentication;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OrderSettlementFinanceContractTest {

    @Test
    void manualSettlement_marksLinkedFinanceInvoicePaidWithActualAmount() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        OrderService orderService = mock(OrderService.class);
        InventoryAvailabilityPort inventory = mock(InventoryAvailabilityPort.class);
        CommerceFinancePort finance = mock(CommerceFinancePort.class);
        PlatformAuditService audit = mock(PlatformAuditService.class);
        Authentication auth = mock(Authentication.class);

        UUID tenantId = UUID.randomUUID();
        UUID storeId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        BigDecimal paidAmount = new BigDecimal("250.00");

        when(auth.getName()).thenReturn(userId.toString());
        when(auth.getDetails()).thenReturn(Map.of("user_id", userId.toString()));
        when(jdbc.queryForObject(anyString(), any(RowMapper.class), eq(tenantId), eq(orderId)))
                .thenReturn(new OrderSettlementService.OrderRow(
                        orderId, storeId, "PENDING", "PENDING", paidAmount, "SAR"));
        when(orderService.getItems(tenantId, storeId, orderId)).thenReturn(List.of(
                new CommerceDtos.OrderItemResponse(
                        UUID.randomUUID(), orderId, productId, null,
                        "Physical Product", "SKU-1", null,
                        1, new BigDecimal("217.39"), BigDecimal.ZERO,
                        new BigDecimal("32.61"), paidAmount, Instant.now())
        ));
        when(jdbc.update(contains("UPDATE commerce_orders"), any(), eq(tenantId), eq(orderId))).thenReturn(1);
        when(jdbc.update(startsWith("INSERT INTO commerce_order_status_history"),
                any(), any(), any(), any(), any(), any())).thenReturn(1);
        when(orderService.get(tenantId, storeId, orderId)).thenReturn(new CommerceDtos.OrderResponse(
                orderId, tenantId, storeId, "ORD-1", UUID.randomUUID(), "customer", null,
                "SAR", new BigDecimal("217.39"), BigDecimal.ZERO, new BigDecimal("32.61"),
                BigDecimal.ZERO, paidAmount, CommerceDomain.PaymentStatus.PAID,
                CommerceDomain.FulfillmentStatus.UNFULFILLED, CommerceDomain.OrderStatus.CONFIRMED,
                "idem-1", 1L, Instant.now(), Instant.now()));

        OrderSettlementService service = new OrderSettlementService(
                jdbc, orderService, inventory, finance, audit);

        service.settle(tenantId, storeId, orderId,
                new OrderSettlementService.SettlementRequest(
                        "CASH", "R-1", paidAmount, Instant.now(), null), auth);

        verify(finance, times(1)).markOrderSettled(tenantId, orderId, paidAmount);
        verify(finance, never()).recordOrder(tenantId, orderId);
    }
}
