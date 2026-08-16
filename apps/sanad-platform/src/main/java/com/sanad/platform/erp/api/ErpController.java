package com.sanad.platform.erp.api;

import com.sanad.platform.erp.api.ErpDtos.*;
import com.sanad.platform.erp.application.ErpGoodsReceiptService;
import com.sanad.platform.erp.application.ErpInventoryAdjustmentService;
import com.sanad.platform.erp.application.ErpInventoryReservationService;
import com.sanad.platform.erp.application.ErpInventoryService;
import com.sanad.platform.erp.application.ErpInventoryTransferService;
import com.sanad.platform.erp.application.ErpItemService;
import com.sanad.platform.erp.application.ErpPurchaseOrderService;
import com.sanad.platform.erp.application.ErpPurchaseRequisitionService;
import com.sanad.platform.erp.application.ErpSupplierService;
import com.sanad.platform.erp.application.ErpWarehouseService;
import com.sanad.platform.security.authorization.RequireCapability;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static com.sanad.platform.security.SecurityContextUtils.tenantId;

/**
 * ERP Core Platform Management API (v20260816.7).
 *
 * <p>All management endpoints are tenant-scoped (resolved via
 * {@link com.sanad.platform.security.SecurityContextUtils#tenantId(Authentication)})
 * and protected by {@code @RequireCapability}
 * ({@code ERP.VIEW / WRITE / ADMIN / APPROVE / INVENTORY / PROCUREMENT}).
 * Mirrors the {@code StoreController} conventions.
 */
@RestController
@RequestMapping("/api/v1/erp")
public class ErpController {

    private final ErpItemService itemService;
    private final ErpSupplierService supplierService;
    private final ErpWarehouseService warehouseService;
    private final ErpInventoryService inventoryService;
    private final ErpInventoryReservationService reservationService;
    private final ErpInventoryTransferService transferService;
    private final ErpInventoryAdjustmentService adjustmentService;
    private final ErpPurchaseRequisitionService requisitionService;
    private final ErpPurchaseOrderService purchaseOrderService;
    private final ErpGoodsReceiptService goodsReceiptService;
    private final JdbcTemplate jdbc;

    public ErpController(ErpItemService itemService, ErpSupplierService supplierService,
                          ErpWarehouseService warehouseService, ErpInventoryService inventoryService,
                          ErpInventoryReservationService reservationService,
                          ErpInventoryTransferService transferService,
                          ErpInventoryAdjustmentService adjustmentService,
                          ErpPurchaseRequisitionService requisitionService,
                          ErpPurchaseOrderService purchaseOrderService,
                          ErpGoodsReceiptService goodsReceiptService,
                          JdbcTemplate jdbc) {
        this.itemService = itemService;
        this.supplierService = supplierService;
        this.warehouseService = warehouseService;
        this.inventoryService = inventoryService;
        this.reservationService = reservationService;
        this.transferService = transferService;
        this.adjustmentService = adjustmentService;
        this.requisitionService = requisitionService;
        this.purchaseOrderService = purchaseOrderService;
        this.goodsReceiptService = goodsReceiptService;
        this.jdbc = jdbc;
    }

    // ===== Items =====
    @GetMapping("/items")
    @RequireCapability("ERP.VIEW")
    public ResponseEntity<List<ItemResponse>> listItems(Authentication auth) {
        return ResponseEntity.ok(itemService.list(tenantId(auth)));
    }

    @PostMapping("/items")
    @RequireCapability("ERP.WRITE")
    public ResponseEntity<ItemResponse> createItem(Authentication auth, @RequestBody CreateItemRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(itemService.create(tenantId(auth), request, auth));
    }

    @PutMapping("/items/{id}")
    @RequireCapability("ERP.WRITE")
    public ResponseEntity<ItemResponse> updateItem(Authentication auth, @PathVariable UUID id,
                                                     @RequestBody UpdateItemRequest request) {
        return ResponseEntity.ok(itemService.update(tenantId(auth), id, request, auth));
    }

    @GetMapping("/items/{id}")
    @RequireCapability("ERP.VIEW")
    public ResponseEntity<ItemResponse> getItem(Authentication auth, @PathVariable UUID id) {
        return ResponseEntity.ok(itemService.get(tenantId(auth), id));
    }

    @PostMapping("/items/{id}/activate")
    @RequireCapability("ERP.ADMIN")
    public ResponseEntity<ItemResponse> activateItem(Authentication auth, @PathVariable UUID id) {
        return ResponseEntity.ok(itemService.activate(tenantId(auth), id, auth));
    }

    @PostMapping("/items/{id}/inactivate")
    @RequireCapability("ERP.ADMIN")
    public ResponseEntity<ItemResponse> inactivateItem(Authentication auth, @PathVariable UUID id) {
        return ResponseEntity.ok(itemService.inactivate(tenantId(auth), id, auth));
    }

    @PostMapping("/items/{id}/archive")
    @RequireCapability("ERP.ADMIN")
    public ResponseEntity<ItemResponse> archiveItem(Authentication auth, @PathVariable UUID id) {
        return ResponseEntity.ok(itemService.archive(tenantId(auth), id, auth));
    }

    @GetMapping("/items/low-stock")
    @RequireCapability("ERP.VIEW")
    public ResponseEntity<List<ItemResponse>> getLowStockItems(Authentication auth) {
        return ResponseEntity.ok(itemService.getLowStockItems(tenantId(auth)));
    }

    // ===== Suppliers =====
    @GetMapping("/suppliers")
    @RequireCapability("ERP.VIEW")
    public ResponseEntity<List<SupplierResponse>> listSuppliers(Authentication auth) {
        return ResponseEntity.ok(supplierService.list(tenantId(auth)));
    }

    @PostMapping("/suppliers")
    @RequireCapability("ERP.PROCUREMENT")
    public ResponseEntity<SupplierResponse> createSupplier(Authentication auth,
                                                             @RequestBody CreateSupplierRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(supplierService.create(tenantId(auth), request, auth));
    }

    @PutMapping("/suppliers/{id}")
    @RequireCapability("ERP.PROCUREMENT")
    public ResponseEntity<SupplierResponse> updateSupplier(Authentication auth, @PathVariable UUID id,
                                                             @RequestBody UpdateSupplierRequest request) {
        return ResponseEntity.ok(supplierService.update(tenantId(auth), id, request, auth));
    }

    @GetMapping("/suppliers/{id}")
    @RequireCapability("ERP.VIEW")
    public ResponseEntity<SupplierResponse> getSupplier(Authentication auth, @PathVariable UUID id) {
        return ResponseEntity.ok(supplierService.get(tenantId(auth), id));
    }

    @PostMapping("/suppliers/{id}/activate")
    @RequireCapability("ERP.PROCUREMENT")
    public ResponseEntity<SupplierResponse> activateSupplier(Authentication auth, @PathVariable UUID id) {
        return ResponseEntity.ok(supplierService.activate(tenantId(auth), id, auth));
    }

    @PostMapping("/suppliers/{id}/block")
    @RequireCapability("ERP.ADMIN")
    public ResponseEntity<SupplierResponse> blockSupplier(Authentication auth, @PathVariable UUID id) {
        return ResponseEntity.ok(supplierService.block(tenantId(auth), id, auth));
    }

    // ===== Warehouses =====
    @GetMapping("/warehouses")
    @RequireCapability("ERP.VIEW")
    public ResponseEntity<List<WarehouseResponse>> listWarehouses(Authentication auth) {
        return ResponseEntity.ok(warehouseService.list(tenantId(auth)));
    }

    @PostMapping("/warehouses")
    @RequireCapability("ERP.ADMIN")
    public ResponseEntity<WarehouseResponse> createWarehouse(Authentication auth,
                                                                @RequestBody CreateWarehouseRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(warehouseService.create(tenantId(auth), request, auth));
    }

    @PutMapping("/warehouses/{id}")
    @RequireCapability("ERP.ADMIN")
    public ResponseEntity<WarehouseResponse> updateWarehouse(Authentication auth, @PathVariable UUID id,
                                                                @RequestBody UpdateWarehouseRequest request) {
        return ResponseEntity.ok(warehouseService.update(tenantId(auth), id, request, auth));
    }

    @GetMapping("/warehouses/{id}")
    @RequireCapability("ERP.VIEW")
    public ResponseEntity<WarehouseResponse> getWarehouse(Authentication auth, @PathVariable UUID id) {
        return ResponseEntity.ok(warehouseService.get(tenantId(auth), id));
    }

    @PostMapping("/warehouses/{id}/activate")
    @RequireCapability("ERP.ADMIN")
    public ResponseEntity<WarehouseResponse> activateWarehouse(Authentication auth, @PathVariable UUID id) {
        return ResponseEntity.ok(warehouseService.activate(tenantId(auth), id, auth));
    }

    @PostMapping("/warehouses/{id}/archive")
    @RequireCapability("ERP.ADMIN")
    public ResponseEntity<WarehouseResponse> archiveWarehouse(Authentication auth, @PathVariable UUID id) {
        return ResponseEntity.ok(warehouseService.archive(tenantId(auth), id, auth));
    }

    // ===== Inventory Balances =====
    @GetMapping("/inventory/balances")
    @RequireCapability("ERP.VIEW")
    public ResponseEntity<List<InventoryBalanceResponse>> listBalances(
            Authentication auth,
            @RequestParam(value = "warehouseId", required = false) UUID warehouseId) {
        return ResponseEntity.ok(inventoryService.listBalances(tenantId(auth), warehouseId));
    }

    @GetMapping("/inventory/balances/{warehouseId}/{itemId}")
    @RequireCapability("ERP.VIEW")
    public ResponseEntity<InventoryBalanceResponse> getBalance(
            Authentication auth, @PathVariable UUID warehouseId, @PathVariable UUID itemId) {
        return ResponseEntity.ok(inventoryService.getBalance(tenantId(auth), warehouseId, itemId));
    }

    @GetMapping("/inventory/summary")
    @RequireCapability("ERP.VIEW")
    public ResponseEntity<InventorySummary> getInventorySummary(Authentication auth) {
        return ResponseEntity.ok(inventoryService.getInventorySummary(tenantId(auth)));
    }

    // ===== Reservations =====
    @PostMapping("/inventory/reservations")
    @RequireCapability("ERP.INVENTORY")
    public ResponseEntity<ReservationResponse> createReservation(Authentication auth,
                                                                    @RequestBody CreateReservationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(reservationService.reserve(tenantId(auth),
                        request.warehouseId(), request.itemId(),
                        request.quantity(), request.source(),
                        request.externalReference(), auth));
    }

    @PostMapping("/inventory/reservations/{id}/release")
    @RequireCapability("ERP.INVENTORY")
    public ResponseEntity<ReservationResponse> releaseReservation(Authentication auth,
                                                                     @PathVariable UUID id) {
        return ResponseEntity.ok(reservationService.release(tenantId(auth), id, auth));
    }

    @PostMapping("/inventory/reservations/{id}/confirm")
    @RequireCapability("ERP.INVENTORY")
    public ResponseEntity<ReservationResponse> confirmReservation(Authentication auth,
                                                                     @PathVariable UUID id) {
        return ResponseEntity.ok(reservationService.confirm(tenantId(auth), id, auth));
    }

    // ===== Transfers =====
    @GetMapping("/inventory/transfers")
    @RequireCapability("ERP.VIEW")
    public ResponseEntity<List<TransferResponse>> listTransfers(Authentication auth) {
        return ResponseEntity.ok(transferService.list(tenantId(auth)));
    }

    @PostMapping("/inventory/transfers")
    @RequireCapability("ERP.INVENTORY")
    public ResponseEntity<TransferResponse> createTransfer(Authentication auth,
                                                              @RequestBody CreateTransferRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(transferService.create(tenantId(auth), request.fromWarehouseId(),
                        request.toWarehouseId(), request.items(), auth));
    }

    @GetMapping("/inventory/transfers/{id}")
    @RequireCapability("ERP.VIEW")
    public ResponseEntity<TransferResponse> getTransfer(Authentication auth, @PathVariable UUID id) {
        return ResponseEntity.ok(transferService.get(tenantId(auth), id));
    }

    @PostMapping("/inventory/transfers/{id}/submit")
    @RequireCapability("ERP.INVENTORY")
    public ResponseEntity<TransferResponse> submitTransfer(Authentication auth, @PathVariable UUID id) {
        return ResponseEntity.ok(transferService.submit(tenantId(auth), id, auth));
    }

    @PostMapping("/inventory/transfers/{id}/receive")
    @RequireCapability("ERP.INVENTORY")
    public ResponseEntity<TransferResponse> receiveTransfer(Authentication auth, @PathVariable UUID id) {
        return ResponseEntity.ok(transferService.receive(tenantId(auth), id, auth));
    }

    // ===== Adjustments =====
    @GetMapping("/inventory/adjustments")
    @RequireCapability("ERP.VIEW")
    public ResponseEntity<List<AdjustmentResponse>> listAdjustments(Authentication auth) {
        return ResponseEntity.ok(adjustmentService.list(tenantId(auth)));
    }

    @PostMapping("/inventory/adjustments")
    @RequireCapability("ERP.INVENTORY")
    public ResponseEntity<AdjustmentResponse> createAdjustment(Authentication auth,
                                                                  @RequestBody CreateAdjustmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(adjustmentService.create(tenantId(auth), request.warehouseId(),
                        request.itemId(), request.quantityDelta(),
                        request.reasonCode(), request.notes(), auth));
    }

    @PostMapping("/inventory/adjustments/{id}/approve")
    @RequireCapability("ERP.APPROVE")
    public ResponseEntity<AdjustmentResponse> approveAdjustment(Authentication auth, @PathVariable UUID id) {
        return ResponseEntity.ok(adjustmentService.approve(tenantId(auth), id, auth));
    }

    // ===== Requisitions =====
    @GetMapping("/purchase-requisitions")
    @RequireCapability("ERP.VIEW")
    public ResponseEntity<List<PurchaseRequisitionResponse>> listRequisitions(Authentication auth) {
        return ResponseEntity.ok(requisitionService.list(tenantId(auth)));
    }

    @PostMapping("/purchase-requisitions")
    @RequireCapability("ERP.PROCUREMENT")
    public ResponseEntity<PurchaseRequisitionResponse> createRequisition(
            Authentication auth, @RequestBody CreateRequisitionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(requisitionService.create(tenantId(auth), request, auth));
    }

    @GetMapping("/purchase-requisitions/{id}")
    @RequireCapability("ERP.VIEW")
    public ResponseEntity<PurchaseRequisitionResponse> getRequisition(
            Authentication auth, @PathVariable UUID id) {
        return ResponseEntity.ok(requisitionService.get(tenantId(auth), id));
    }

    @PostMapping("/purchase-requisitions/{id}/submit")
    @RequireCapability("ERP.PROCUREMENT")
    public ResponseEntity<PurchaseRequisitionResponse> submitRequisition(
            Authentication auth, @PathVariable UUID id) {
        return ResponseEntity.ok(requisitionService.submit(tenantId(auth), id, auth));
    }

    @PostMapping("/purchase-requisitions/{id}/approve")
    @RequireCapability("ERP.APPROVE")
    public ResponseEntity<PurchaseRequisitionResponse> approveRequisition(
            Authentication auth, @PathVariable UUID id) {
        return ResponseEntity.ok(requisitionService.approve(tenantId(auth), id, auth));
    }

    @PostMapping("/purchase-requisitions/{id}/reject")
    @RequireCapability("ERP.APPROVE")
    public ResponseEntity<PurchaseRequisitionResponse> rejectRequisition(
            Authentication auth, @PathVariable UUID id) {
        return ResponseEntity.ok(requisitionService.reject(tenantId(auth), id, auth));
    }

    // ===== Purchase Orders =====
    @GetMapping("/purchase-orders")
    @RequireCapability("ERP.VIEW")
    public ResponseEntity<List<PurchaseOrderResponse>> listPurchaseOrders(Authentication auth) {
        return ResponseEntity.ok(purchaseOrderService.list(tenantId(auth)));
    }

    @PostMapping("/purchase-orders")
    @RequireCapability("ERP.PROCUREMENT")
    public ResponseEntity<PurchaseOrderResponse> createPurchaseOrder(
            Authentication auth, @RequestBody CreatePurchaseOrderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(purchaseOrderService.create(tenantId(auth), request, auth));
    }

    @GetMapping("/purchase-orders/{id}")
    @RequireCapability("ERP.VIEW")
    public ResponseEntity<PurchaseOrderResponse> getPurchaseOrder(
            Authentication auth, @PathVariable UUID id) {
        return ResponseEntity.ok(purchaseOrderService.get(tenantId(auth), id));
    }

    @PostMapping("/purchase-orders/{id}/submit")
    @RequireCapability("ERP.PROCUREMENT")
    public ResponseEntity<PurchaseOrderResponse> submitPurchaseOrder(
            Authentication auth, @PathVariable UUID id) {
        return ResponseEntity.ok(purchaseOrderService.submit(tenantId(auth), id, auth));
    }

    @PostMapping("/purchase-orders/{id}/approve")
    @RequireCapability("ERP.APPROVE")
    public ResponseEntity<PurchaseOrderResponse> approvePurchaseOrder(
            Authentication auth, @PathVariable UUID id) {
        return ResponseEntity.ok(purchaseOrderService.approve(tenantId(auth), id, auth));
    }

    @PostMapping("/purchase-orders/{id}/cancel")
    @RequireCapability("ERP.PROCUREMENT")
    public ResponseEntity<PurchaseOrderResponse> cancelPurchaseOrder(
            Authentication auth, @PathVariable UUID id) {
        return ResponseEntity.ok(purchaseOrderService.cancel(tenantId(auth), id, auth));
    }

    // ===== Goods Receipts =====
    @GetMapping("/goods-receipts")
    @RequireCapability("ERP.VIEW")
    public ResponseEntity<List<GoodsReceiptResponse>> listGoodsReceipts(Authentication auth) {
        return ResponseEntity.ok(goodsReceiptService.list(tenantId(auth)));
    }

    @PostMapping("/goods-receipts")
    @RequireCapability("ERP.INVENTORY")
    public ResponseEntity<GoodsReceiptResponse> createGoodsReceipt(
            Authentication auth, @RequestBody CreateGoodsReceiptRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(goodsReceiptService.create(tenantId(auth), request, auth));
    }

    @GetMapping("/goods-receipts/{id}")
    @RequireCapability("ERP.VIEW")
    public ResponseEntity<GoodsReceiptResponse> getGoodsReceipt(
            Authentication auth, @PathVariable UUID id) {
        return ResponseEntity.ok(goodsReceiptService.get(tenantId(auth), id));
    }

    @PostMapping("/goods-receipts/{id}/post")
    @RequireCapability("ERP.INVENTORY")
    public ResponseEntity<GoodsReceiptResponse> postGoodsReceipt(
            Authentication auth, @PathVariable UUID id) {
        return ResponseEntity.ok(goodsReceiptService.post(tenantId(auth), id, auth));
    }

    // ===== Dashboard =====
    @GetMapping("/dashboard")
    @RequireCapability("ERP.VIEW")
    public ResponseEntity<ErpDashboardSummary> dashboard(Authentication auth) {
        UUID tenantId = tenantId(auth);
        Integer totalItems = countFor(tenantId, "SELECT COUNT(*) FROM erp_items WHERE tenant_id = ?");
        Integer activeItems = countFor(tenantId,
                "SELECT COUNT(*) FROM erp_items WHERE tenant_id = ? AND status = 'ACTIVE'");
        Integer totalSuppliers = countFor(tenantId, "SELECT COUNT(*) FROM erp_suppliers WHERE tenant_id = ?");
        Integer totalWarehouses = countFor(tenantId, "SELECT COUNT(*) FROM erp_warehouses WHERE tenant_id = ?");
        Integer lowStock = countFor(tenantId,
                "SELECT COUNT(DISTINCT i.id) FROM erp_items i "
                        + "JOIN erp_inventory_balances b ON b.tenant_id = i.tenant_id AND b.item_id = i.id "
                        + "WHERE i.tenant_id = ? AND i.track_inventory = TRUE AND i.reorder_level > 0 "
                        + "AND (b.on_hand - b.reserved) <= i.reorder_level");
        Integer pendingRequisitions = countFor(tenantId,
                "SELECT COUNT(*) FROM erp_purchase_requisitions WHERE tenant_id = ? AND status IN ('DRAFT','SUBMITTED')");
        Integer pendingPurchaseOrders = countFor(tenantId,
                "SELECT COUNT(*) FROM erp_purchase_orders WHERE tenant_id = ? AND status IN ('DRAFT','SUBMITTED','APPROVED','SENT')");
        BigDecimal totalInvValue = BigDecimal.ZERO;
        try {
            InventorySummary summary = inventoryService.getInventorySummary(tenantId);
            totalInvValue = summary.totalInventoryValue() != null ? summary.totalInventoryValue() : BigDecimal.ZERO;
        } catch (Exception ignored) {}
        return ResponseEntity.ok(new ErpDashboardSummary(
                totalItems, activeItems, totalSuppliers, totalWarehouses, lowStock,
                pendingRequisitions, pendingPurchaseOrders, totalInvValue));
    }

    private Integer countFor(UUID tenantId, String sql) {
        try {
            Integer v = jdbc.queryForObject(sql, Integer.class, tenantId);
            return v != null ? v : 0;
        } catch (Exception e) { return 0; }
    }
}
