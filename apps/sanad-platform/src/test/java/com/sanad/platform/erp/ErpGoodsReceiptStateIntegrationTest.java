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

    // ===== v20260816.10 — exhaustive receivable/non-receivable state matrix =====
    //
    // The two tests above prove the two RED paths exposed by the original
    // red-gate run. The tests below complete the regression matrix required
    // by the v20260816.10 fix:
    //
    //   D) APPROVED               => receipt allowed
    //   E) SENT                    => receipt allowed
    //   F) PARTIALLY_RECEIVED     => additional receipt allowed
    //   G) RECEIVED/CLOSED/CANCELLED => additional receipt rejected (3 sub-cases)

    @Test
    void createLinkedReceipt_allowsApprovedPurchaseOrder() {
        var warehouse = createWarehouse("APPR");
        var item = createActiveItem("APPR");
        var po = createPurchaseOrder("APPR", item.id(), new BigDecimal("10"));
        poService.submit(tenantId, po.id(), null);
        poService.approve(tenantId, po.id(), null);
        assertThat(poService.get(tenantId, po.id()).status())
                .isEqualTo(ErpDomain.PurchaseOrderStatus.APPROVED);

        var receipt = goodsReceiptService.create(tenantId,
                new CreateGoodsReceiptRequest(po.id(), warehouse.id(), List.of(
                        new CreateGoodsReceiptItem(po.items().get(0).id(), item.id(), new BigDecimal("4")))), null);

        assertThat(receipt.poId()).isEqualTo(po.id());
        assertThat(receipt.status()).isEqualTo(ErpDomain.GoodsReceiptStatus.DRAFT);
        var posted = goodsReceiptService.post(tenantId, receipt.id(), null);
        assertThat(posted.status()).isEqualTo(ErpDomain.GoodsReceiptStatus.POSTED);
        var balance = inventoryService.getOrCreateBalance(tenantId, warehouse.id(), item.id());
        assertThat(balance.onHand()).isEqualByComparingTo(new BigDecimal("4"));
        var updatedPo = poService.get(tenantId, po.id());
        assertThat(updatedPo.status()).isEqualTo(ErpDomain.PurchaseOrderStatus.PARTIALLY_RECEIVED);
        assertThat(updatedPo.items().get(0).receivedQuantity()).isEqualByComparingTo(new BigDecimal("4"));
    }

    @Test
    void createLinkedReceipt_allowsSentPurchaseOrder() {
        var warehouse = createWarehouse("SENT");
        var item = createActiveItem("SENT");
        var po = createPurchaseOrder("SENT", item.id(), new BigDecimal("10"));
        poService.submit(tenantId, po.id(), null);
        poService.approve(tenantId, po.id(), null);
        // The PO service has no send() transition; SENT is set externally
        // (e.g. by an outbound integration) — emulate that here so we can
        // verify the SENT branch of the receivable-state matrix.
        setPoStatusViaSql(po.id(), "SENT");
        assertThat(poService.get(tenantId, po.id()).status())
                .isEqualTo(ErpDomain.PurchaseOrderStatus.SENT);

        var receipt = goodsReceiptService.create(tenantId,
                new CreateGoodsReceiptRequest(po.id(), warehouse.id(), List.of(
                        new CreateGoodsReceiptItem(po.items().get(0).id(), item.id(), new BigDecimal("3")))), null);
        assertThat(receipt.poId()).isEqualTo(po.id());

        var posted = goodsReceiptService.post(tenantId, receipt.id(), null);
        assertThat(posted.status()).isEqualTo(ErpDomain.GoodsReceiptStatus.POSTED);
        assertThat(inventoryService.getOrCreateBalance(tenantId, warehouse.id(), item.id()).onHand())
                .isEqualByComparingTo(new BigDecimal("3"));
    }

    @Test
    void createLinkedReceipt_allowsPartiallyReceivedPurchaseOrder() {
        var warehouse = createWarehouse("PART");
        var item = createActiveItem("PART");
        var po = createPurchaseOrder("PART", item.id(), new BigDecimal("10"));
        poService.submit(tenantId, po.id(), null);
        poService.approve(tenantId, po.id(), null);

        // First receipt — partial quantity
        var first = goodsReceiptService.create(tenantId,
                new CreateGoodsReceiptRequest(po.id(), warehouse.id(), List.of(
                        new CreateGoodsReceiptItem(po.items().get(0).id(), item.id(), new BigDecimal("4")))), null);
        goodsReceiptService.post(tenantId, first.id(), null);
        var poAfterFirst = poService.get(tenantId, po.id());
        assertThat(poAfterFirst.status()).isEqualTo(ErpDomain.PurchaseOrderStatus.PARTIALLY_RECEIVED);
        assertThat(poAfterFirst.items().get(0).receivedQuantity()).isEqualByComparingTo(new BigDecimal("4"));

        // Second receipt — additional receipt on the now-PARTIALLY_RECEIVED PO
        var second = goodsReceiptService.create(tenantId,
                new CreateGoodsReceiptRequest(po.id(), warehouse.id(), List.of(
                        new CreateGoodsReceiptItem(po.items().get(0).id(), item.id(), new BigDecimal("3")))), null);
        var posted = goodsReceiptService.post(tenantId, second.id(), null);
        assertThat(posted.status()).isEqualTo(ErpDomain.GoodsReceiptStatus.POSTED);

        var balance = inventoryService.getOrCreateBalance(tenantId, warehouse.id(), item.id());
        assertThat(balance.onHand()).isEqualByComparingTo(new BigDecimal("7"));
        var updatedPo = poService.get(tenantId, po.id());
        assertThat(updatedPo.status()).isEqualTo(ErpDomain.PurchaseOrderStatus.PARTIALLY_RECEIVED);
        assertThat(updatedPo.items().get(0).receivedQuantity()).isEqualByComparingTo(new BigDecimal("7"));
    }

    @Test
    void createLinkedReceipt_rejectsReceivedPurchaseOrder() {
        var warehouse = createWarehouse("RECV");
        var item = createActiveItem("RECV");
        var po = createPurchaseOrder("RECV", item.id(), new BigDecimal("10"));
        poService.submit(tenantId, po.id(), null);
        poService.approve(tenantId, po.id(), null);

        // Fully receive the PO first.
        var full = goodsReceiptService.create(tenantId,
                new CreateGoodsReceiptRequest(po.id(), warehouse.id(), List.of(
                        new CreateGoodsReceiptItem(po.items().get(0).id(), item.id(), new BigDecimal("10")))), null);
        goodsReceiptService.post(tenantId, full.id(), null);
        assertThat(poService.get(tenantId, po.id()).status())
                .isEqualTo(ErpDomain.PurchaseOrderStatus.RECEIVED);

        // Second receipt on a fully-received PO must be rejected.
        assertThatThrownBy(() -> goodsReceiptService.create(tenantId,
                new CreateGoodsReceiptRequest(po.id(), warehouse.id(), List.of(
                        new CreateGoodsReceiptItem(po.items().get(0).id(), item.id(), new BigDecimal("1")))), null))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode").isEqualTo(HttpStatus.CONFLICT);

        // No additional inventory movement occurred (balance stays at 10).
        assertThat(inventoryService.getOrCreateBalance(tenantId, warehouse.id(), item.id()).onHand())
                .isEqualByComparingTo(new BigDecimal("10"));
    }

    @Test
    void createLinkedReceipt_rejectsClosedPurchaseOrder() {
        var warehouse = createWarehouse("CLOS");
        var item = createActiveItem("CLOS");
        var po = createPurchaseOrder("CLOS", item.id(), new BigDecimal("10"));
        poService.submit(tenantId, po.id(), null);
        poService.approve(tenantId, po.id(), null);

        var receipt = goodsReceiptService.create(tenantId,
                new CreateGoodsReceiptRequest(po.id(), warehouse.id(), List.of(
                        new CreateGoodsReceiptItem(po.items().get(0).id(), item.id(), new BigDecimal("4")))), null);
        goodsReceiptService.post(tenantId, receipt.id(), null);
        poService.close(tenantId, po.id(), null);
        assertThat(poService.get(tenantId, po.id()).status())
                .isEqualTo(ErpDomain.PurchaseOrderStatus.CLOSED);

        assertThatThrownBy(() -> goodsReceiptService.create(tenantId,
                new CreateGoodsReceiptRequest(po.id(), warehouse.id(), List.of(
                        new CreateGoodsReceiptItem(po.items().get(0).id(), item.id(), new BigDecimal("1")))), null))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode").isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void createLinkedReceipt_rejectsCancelledPurchaseOrder() {
        var warehouse = createWarehouse("CANC");
        var item = createActiveItem("CANC");
        var po = createPurchaseOrder("CANC", item.id(), new BigDecimal("10"));
        poService.submit(tenantId, po.id(), null);
        poService.approve(tenantId, po.id(), null);
        poService.cancel(tenantId, po.id(), null);
        assertThat(poService.get(tenantId, po.id()).status())
                .isEqualTo(ErpDomain.PurchaseOrderStatus.CANCELLED);

        assertThatThrownBy(() -> goodsReceiptService.create(tenantId,
                new CreateGoodsReceiptRequest(po.id(), warehouse.id(), List.of(
                        new CreateGoodsReceiptItem(po.items().get(0).id(), item.id(), new BigDecimal("1")))), null))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode").isEqualTo(HttpStatus.CONFLICT);

        // No inventory movement occurred.
        assertThat(inventoryService.getOrCreateBalance(tenantId, warehouse.id(), item.id()).onHand())
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void createLinkedReceipt_rejectsSubmittedPurchaseOrder() {
        var warehouse = createWarehouse("SUBM");
        var item = createActiveItem("SUBM");
        var po = createPurchaseOrder("SUBM", item.id(), new BigDecimal("10"));
        poService.submit(tenantId, po.id(), null);
        assertThat(poService.get(tenantId, po.id()).status())
                .isEqualTo(ErpDomain.PurchaseOrderStatus.SUBMITTED);

        assertThatThrownBy(() -> goodsReceiptService.create(tenantId,
                new CreateGoodsReceiptRequest(po.id(), warehouse.id(), List.of(
                        new CreateGoodsReceiptItem(po.items().get(0).id(), item.id(), new BigDecimal("1")))), null))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode").isEqualTo(HttpStatus.CONFLICT);

        assertThat(inventoryService.getOrCreateBalance(tenantId, warehouse.id(), item.id()).onHand())
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    private void setPoStatusViaSql(UUID poId, String newStatus) {
        // Used to emulate external PO state transitions (e.g. SENT) that are
        // not exposed by ErpPurchaseOrderService but are valid enum values.
        jdbc.update("UPDATE erp_purchase_orders SET status = ?, updated_at = ?, version = version + 1 "
                        + "WHERE tenant_id = ? AND id = ?",
                newStatus, Timestamp.from(Instant.now()), tenantId, poId);
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
