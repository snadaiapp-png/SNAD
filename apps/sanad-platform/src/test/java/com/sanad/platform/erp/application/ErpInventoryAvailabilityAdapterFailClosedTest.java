package com.sanad.platform.erp.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ErpInventoryAvailabilityAdapterFailClosedTest {

    private JdbcTemplate jdbc;
    private ErpInventoryService inventoryService;
    private ErpInventoryReservationService reservationService;
    private ErpInventoryAvailabilityAdapter adapter;
    private UUID tenantId;
    private UUID productId;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        inventoryService = mock(ErpInventoryService.class);
        reservationService = mock(ErpInventoryReservationService.class);
        adapter = new ErpInventoryAvailabilityAdapter(jdbc, inventoryService, reservationService);
        tenantId = UUID.randomUUID();
        productId = UUID.randomUUID();
    }

    @Test
    void physicalProduct_missingErpItemMapping_failsClosed() {
        when(jdbc.queryForObject(contains("product_type"), eq(String.class), eq(tenantId), eq(productId)))
                .thenReturn("PHYSICAL");
        when(jdbc.queryForObject(contains("SELECT sku FROM commerce_products"), eq(String.class), eq(tenantId), eq(productId)))
                .thenThrow(new EmptyResultDataAccessException(1));

        assertThatThrownBy(() -> adapter.reserve(tenantId, productId, null, 1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ERP item mapping");
    }

    @Test
    void digitalProduct_bypassesStockWithoutErpMapping() {
        when(jdbc.queryForObject(contains("product_type"), eq(String.class), eq(tenantId), eq(productId)))
                .thenReturn("DIGITAL");

        assertThat(adapter.reserve(tenantId, productId, null, 1)).isTrue();
        adapter.confirm(tenantId, productId, null, 1);

        verifyNoInteractions(inventoryService, reservationService);
    }

    @Test
    void physicalDirectFulfillmentFailure_isPropagated() {
        UUID itemId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        when(jdbc.queryForObject(contains("product_type"), eq(String.class), eq(tenantId), eq(productId)))
                .thenReturn("PHYSICAL");
        when(jdbc.queryForObject(contains("SELECT sku FROM commerce_products"), eq(String.class), eq(tenantId), eq(productId)))
                .thenReturn("SKU-1");
        when(jdbc.queryForObject(contains("SELECT id FROM erp_items"), eq(UUID.class), eq(tenantId), eq("SKU-1")))
                .thenReturn(itemId);
        when(jdbc.queryForObject(contains("SELECT id FROM erp_warehouses"), eq(UUID.class), eq(tenantId)))
                .thenReturn(warehouseId);
        when(jdbc.queryForObject(contains("SELECT * FROM erp_inventory_reservations"), any(org.springframework.jdbc.core.RowMapper.class),
                eq(tenantId), anyString())).thenThrow(new EmptyResultDataAccessException(1));
        doThrow(new IllegalStateException("stock write failed"))
                .when(inventoryService).adjustStock(eq(tenantId), eq(warehouseId), eq(itemId),
                        eq(BigDecimal.ONE), any(), anyString(), isNull(), isNull());

        assertThatThrownBy(() -> adapter.confirm(tenantId, productId, null, 1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("stock write failed");
    }
}
