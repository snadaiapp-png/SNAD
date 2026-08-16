package com.sanad.platform.erp;

import com.sanad.platform.erp.api.ErpDtos.CreateGoodsReceiptItem;
import com.sanad.platform.erp.api.ErpDtos.CreateGoodsReceiptRequest;
import com.sanad.platform.erp.api.ErpDtos.CreateItemRequest;
import com.sanad.platform.erp.api.ErpDtos.CreatePurchaseOrderItem;
import com.sanad.platform.erp.api.ErpDtos.CreatePurchaseOrderRequest;
import com.sanad.platform.erp.api.ErpDtos.CreateSupplierRequest;
import com.sanad.platform.erp.api.ErpDtos.CreateWarehouseRequest;
import com.sanad.platform.erp.application.ErpGoodsReceiptService;
import com.sanad.platform.erp.application.ErpInventoryService;
import com.sanad.platform.erp.application.ErpItemService;
import com.sanad.platform.erp.application.ErpPurchaseOrderService;
import com.sanad.platform.erp.application.ErpSupplierService;
import com.sanad.platform.erp.application.ErpWarehouseService;
import com.sanad.platform.erp.domain.ErpDomain;
import com.sanad.platform.security.SecurityPermitAllTestConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("local")
@Import(SecurityPermitAllTestConfig.class)
class ErpGoodsReceiptStateIntegrationTest {

    @Autowired private ErpItemService itemService;
    @Autowired private ErpSupplierService supplierService;
    @Autowired private ErpWarehouseService warehouseService;
    @Autowired private ErpInventoryService inventoryService;
    @Autowired private ErpGoodsReceiptService goodsReceiptService;
    @Autowired private ErpPurchaseOrderService poService;
    @Autowired private JdbcTemplate jdbc;

    private UUID tenantId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        var now = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO tenants (id,name,subdomain,status,created_at,updated_at) VALUES (?, 'ERP Receipt State Test', ?, 'ACTIVE', ?, ?)",
                tenantId, "erp-gr-state-" + tenantId.toString().substring(0, 8), now, now);
    }

    @Test
    void createLinkedReceipt_rejectsDraftPurchaseOrder() {
        var warehouse = createWarehouse("DRAFT");
        var item = createActiveItem("DRAFT");
        var po = createPurchaseOrder("DRAFT", item.id(), new BigDecimal("10"));

        assertThat(po.status()).isEqualTo(ErpDomain.PurchaseOrderStatus.DRAFT);
        assertThatThrownBy(() -> goodsReceiptService.create(tenantId,
                new CreateGoodsReceiptRequest(po.id(), warehouse.id(), List.of(
                        new CreateGoodsReceiptItem(po.items().get(0).id(), item.id(), new BigDecimal("1")))), null))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode").isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void postLinkedReceipt_rejectsPurchaseOrderCancelledAfterReceiptDraft() {
        var warehouse = createWarehouse("CANCEL");
        var item = createActiveItem("CANCEL");
        var po = createPurchaseOrder("CANCEL", item.id(), new BigDecimal("10"));
        poService.submit(tenantId, po.id(), null);
        poService.approve(tenantId, po.id(), null);

        var receipt = goodsReceiptService.create(tenantId,
                new CreateGoodsReceiptRequest(po.id(), warehouse.id(), List.of(
                        new CreateGoodsReceiptItem(po.items().get(0).id(), item.id(), new BigDecimal("4")))), null);
        poService.cancel(tenantId, po.id(), null);

        assertThatThrownBy(() -> goodsReceiptService.post(tenantId, receipt.id(), null))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode").isEqualTo(HttpStatus.CONFLICT);

        var balance = inventoryService.getOrCreateBalance(tenantId, warehouse.id(), item.id());
        assertThat(balance.onHand()).isEqualByComparingTo(BigDecimal.ZERO);
        var unchangedPo = poService.get(tenantId, po.id());
        assertThat(unchangedPo.status()).isEqualTo(ErpDomain.PurchaseOrderStatus.CANCELLED);
        assertThat(unchangedPo.items().get(0).receivedQuantity()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    private com.sanad.platform.erp.api.ErpDtos.ItemResponse createActiveItem(String code) {
        var item = itemService.create(tenantId,
                new CreateItemRequest("GR-" + code, "SKU-GR-" + code, "Item " + code, "Receipt state test",
                        ErpDomain.ItemType.GOODS, ErpDomain.UnitOfMeasure.EACH, true,
                        BigDecimal.ZERO, BigDecimal.ZERO), null);
        return itemService.activate(tenantId, item.id(), null);
    }

    private com.sanad.platform.erp.api.ErpDtos.WarehouseResponse createWarehouse(String code) {
        return warehouseService.create(tenantId,
                new CreateWarehouseRequest("GR-" + code, "Warehouse " + code, "Riyadh", false), null);
    }

    private com.sanad.platform.erp.api.ErpDtos.PurchaseOrderResponse createPurchaseOrder(
            String code, UUID itemId, BigDecimal quantity) {
        var supplier = supplierService.create(tenantId,
                new CreateSupplierRequest("GR-" + code, "Supplier " + code, "gr-state@test.com", "123",
                        null, null, null, "SAR"), null);
        supplierService.activate(tenantId, supplier.id(), null);
        return poService.create(tenantId,
                new CreatePurchaseOrderRequest(supplier.id(), "SAR", null, null, List.of(
                        new CreatePurchaseOrderItem(itemId, quantity, new BigDecimal("10.00")))), null);
    }
}
