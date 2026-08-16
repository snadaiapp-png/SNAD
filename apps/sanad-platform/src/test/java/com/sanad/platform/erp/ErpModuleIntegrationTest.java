package com.sanad.platform.erp;

import com.sanad.platform.erp.api.ErpDtos.*;
import com.sanad.platform.erp.application.*;
import com.sanad.platform.erp.domain.ErpDomain;
import com.sanad.platform.security.SecurityPermitAllTestConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Import(SecurityPermitAllTestConfig.class)
class ErpModuleIntegrationTest {

    @Autowired private ErpItemService itemService;
    @Autowired private ErpSupplierService supplierService;
    @Autowired private ErpWarehouseService warehouseService;
    @Autowired private ErpInventoryService inventoryService;
    @Autowired private ErpInventoryReservationService reservationService;
    @Autowired private ErpGoodsReceiptService goodsReceiptService;
    @Autowired private ErpPurchaseOrderService poService;
    @Autowired private JdbcTemplate jdbc;
    private UUID tenantId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        var now = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO tenants (id,name,subdomain,status,created_at,updated_at) VALUES (?, 'Test', ?, 'ACTIVE', ?, ?)",
                tenantId, "erp-" + tenantId.toString().substring(0, 8), now, now);
    }

    @Test
    void createItem_persistsWithDraftStatus() {
        var item = createItem("C1");
        assertThat(item.status()).isEqualTo(ErpDomain.ItemStatus.DRAFT);
    }

    @Test
    void createItem_rejectsDuplicateCode() {
        createItem("DUP1");
        assertThatThrownBy(() -> createItem("DUP1"))
                .isInstanceOf(ResponseStatusException.class).extracting("statusCode").isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void activateItem_transitionsToActive() {
        var item = createItem("A1");
        assertThat(itemService.activate(tenantId, item.id(), null).status()).isEqualTo(ErpDomain.ItemStatus.ACTIVE);
    }

    @Test
    void createSupplier_persistsWithPendingStatus() {
        var s = createSupplier("S1");
        assertThat(s.status()).isEqualTo(ErpDomain.SupplierStatus.PENDING);
    }

    @Test
    void activateSupplier_transitionsToActive() {
        var s = createSupplier("SA1");
        assertThat(supplierService.activate(tenantId, s.id(), null).status()).isEqualTo(ErpDomain.SupplierStatus.ACTIVE);
    }

    @Test
    void createWarehouse_persistsWithActiveStatus() {
        var w = createWarehouse("W1");
        assertThat(w.status()).isEqualTo(ErpDomain.WarehouseStatus.ACTIVE);
    }

    @Test
    void inventoryBalance_startsAtZero() {
        var wh = createWarehouse("IB1");
        var item = createItem("IB1");
        itemService.activate(tenantId, item.id(), null);
        var b = inventoryService.getOrCreateBalance(tenantId, wh.id(), item.id());
        assertThat(b.onHand()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(b.available()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void adjustStock_increasesOnHand() {
        var wh = createWarehouse("AS1");
        var item = createItem("AS1");
        itemService.activate(tenantId, item.id(), null);
        inventoryService.adjustStock(tenantId, wh.id(), item.id(), new BigDecimal("100"), ErpDomain.MovementType.RECEIPT, "Initial", null, null);
        var b = inventoryService.getBalance(tenantId, wh.id(), item.id());
        assertThat(b.onHand()).isEqualByComparingTo(new BigDecimal("100"));
    }

    @Test
    void reserve_decreasesAvailable() {
        var wh = createWarehouse("RS1");
        var item = createItem("RS1");
        itemService.activate(tenantId, item.id(), null);
        inventoryService.adjustStock(tenantId, wh.id(), item.id(), new BigDecimal("50"), ErpDomain.MovementType.RECEIPT, null, null, null);
        var r = reservationService.reserve(tenantId, wh.id(), item.id(), new BigDecimal("20"), "TEST", "ext-rs1", null);
        assertThat(r.status()).isEqualTo(ErpDomain.ReservationStatus.RESERVED);
        var b = inventoryService.getBalance(tenantId, wh.id(), item.id());
        assertThat(b.available()).isEqualByComparingTo(new BigDecimal("30"));
    }

    @Test
    void releaseReservation_restoresAvailable() {
        var wh = createWarehouse("RL1");
        var item = createItem("RL1");
        itemService.activate(tenantId, item.id(), null);
        inventoryService.adjustStock(tenantId, wh.id(), item.id(), new BigDecimal("50"), ErpDomain.MovementType.RECEIPT, null, null, null);
        var r = reservationService.reserve(tenantId, wh.id(), item.id(), new BigDecimal("20"), "TEST", "ext-rl1", null);
        reservationService.release(tenantId, r.id(), null);
        var b = inventoryService.getBalance(tenantId, wh.id(), item.id());
        assertThat(b.available()).isEqualByComparingTo(new BigDecimal("50"));
    }

    @Test
    void confirmReservation_decreasesOnHand() {
        var wh = createWarehouse("CF1");
        var item = createItem("CF1");
        itemService.activate(tenantId, item.id(), null);
        inventoryService.adjustStock(tenantId, wh.id(), item.id(), new BigDecimal("50"), ErpDomain.MovementType.RECEIPT, null, null, null);
        var r = reservationService.reserve(tenantId, wh.id(), item.id(), new BigDecimal("20"), "TEST", "ext-cf1", null);
        reservationService.confirm(tenantId, r.id(), null);
        var b = inventoryService.getBalance(tenantId, wh.id(), item.id());
        assertThat(b.onHand()).isEqualByComparingTo(new BigDecimal("30"));
    }

    @Test
    void insufficientStock_rejectsReservation() {
        var wh = createWarehouse("IS1");
        var item = createItem("IS1");
        itemService.activate(tenantId, item.id(), null);
        inventoryService.adjustStock(tenantId, wh.id(), item.id(), new BigDecimal("10"), ErpDomain.MovementType.RECEIPT, null, null, null);
        assertThatThrownBy(() -> reservationService.reserve(tenantId, wh.id(), item.id(), new BigDecimal("100"), "TEST", "ext-is1", null))
                .isInstanceOf(ResponseStatusException.class).extracting("statusCode").isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void createPo_calculatesTotals() {
        var sup = createSupplier("PO1");
        supplierService.activate(tenantId, sup.id(), null);
        var item = createItem("PI1");
        itemService.activate(tenantId, item.id(), null);
        var po = poService.create(tenantId, new CreatePurchaseOrderRequest(sup.id(), "SAR", null, null, List.of(
                new CreatePurchaseOrderItem(item.id(), new BigDecimal("10"), new BigDecimal("50.00")),
                new CreatePurchaseOrderItem(item.id(), new BigDecimal("5"), new BigDecimal("100.00"))
        )), null);
        assertThat(po.subtotal()).isEqualByComparingTo(new BigDecimal("1000.00"));
    }

    @Test
    void postGoodsReceipt_updatesInventoryAndPo() {
        var wh = createWarehouse("GR1");
        var sup = createSupplier("GR1");
        supplierService.activate(tenantId, sup.id(), null);
        var item = createItem("GI1");
        itemService.activate(tenantId, item.id(), null);
        var po = poService.create(tenantId, new CreatePurchaseOrderRequest(sup.id(), "SAR", null, null, List.of(
                new CreatePurchaseOrderItem(item.id(), new BigDecimal("100"), new BigDecimal("10.00"))
        )), null);
        poService.submit(tenantId, po.id(), null);
        poService.approve(tenantId, po.id(), null);
        var receipt = goodsReceiptService.create(tenantId, new CreateGoodsReceiptRequest(po.id(), wh.id(), List.of(
                new CreateGoodsReceiptItem(null, item.id(), new BigDecimal("40"))
        )), null);
        var posted = goodsReceiptService.post(tenantId, receipt.id(), null);
        assertThat(posted.status()).isEqualTo(ErpDomain.GoodsReceiptStatus.POSTED);
        var b = inventoryService.getBalance(tenantId, wh.id(), item.id());
        assertThat(b.onHand()).isEqualByComparingTo(new BigDecimal("40"));
        var updatedPo = poService.get(tenantId, po.id());
        assertThat(updatedPo.status()).isEqualTo(ErpDomain.PurchaseOrderStatus.PARTIALLY_RECEIVED);
    }

    @Test
    void crossTenantItemAccess_denied() {
        var item = createItem("XT1");
        UUID other = createOtherTenant();
        assertThatThrownBy(() -> itemService.get(other, item.id()))
                .isInstanceOf(ResponseStatusException.class).extracting("statusCode").isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void noPosImplementation() {
        Integer c = jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='public' AND table_name LIKE 'pos_%'", Integer.class);
        assertThat(c).as("POS_BUSINESS_IMPLEMENTATION_ADDED").isEqualTo(0);
    }

    @Test
    void noContractImplementation() {
        Integer c = jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='public' AND table_name LIKE 'contracts_%'", Integer.class);
        assertThat(c).as("CONTRACT_BUSINESS_IMPLEMENTATION_ADDED").isEqualTo(0);
    }

    private ItemResponse createItem(String code) {
        return itemService.create(tenantId, new CreateItemRequest(code, "SKU-" + code, "Item " + code, "Desc",
                ErpDomain.ItemType.GOODS, ErpDomain.UnitOfMeasure.EACH, true, BigDecimal.ZERO, BigDecimal.ZERO), null);
    }

    private SupplierResponse createSupplier(String code) {
        return supplierService.create(tenantId, new CreateSupplierRequest(code, "Supplier " + code, "s@test.com", "123", null, null, null, "SAR"), null);
    }

    private WarehouseResponse createWarehouse(String code) {
        return warehouseService.create(tenantId, new CreateWarehouseRequest(code, "Warehouse " + code, "Riyadh", false), null);
    }

    private UUID createOtherTenant() {
        UUID ot = UUID.randomUUID();
        var now = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO tenants (id,name,subdomain,status,created_at,updated_at) VALUES (?, 'Other', ?, 'ACTIVE', ?, ?)", ot, "erp-ot-" + ot.toString().substring(0, 8), now, now);
        return ot;
    }
}
