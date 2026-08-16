package com.sanad.platform.erp.api;

import com.sanad.platform.erp.domain.ErpDomain.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * DTOs for the ERP Core Platform (v20260816.7).
 *
 * <p>Records are immutable and used as the request/response payloads for
 * {@link com.sanad.platform.erp.api.ErpController}.
 */
public final class ErpDtos {

    private ErpDtos() {}

    // ===== Item =====
    public record CreateItemRequest(String code, String sku, String name, String description,
                                      ItemType itemType, UnitOfMeasure unitOfMeasure,
                                      Boolean trackInventory, BigDecimal reorderLevel,
                                      BigDecimal reorderQuantity) {}
    public record UpdateItemRequest(String sku, String name, String description,
                                      ItemType itemType, UnitOfMeasure unitOfMeasure,
                                      Boolean trackInventory, BigDecimal reorderLevel,
                                      BigDecimal reorderQuantity, Long expectedVersion) {}
    public record ItemResponse(UUID id, UUID tenantId, String code, String sku, String name,
                                 String description, ItemType itemType, UnitOfMeasure unitOfMeasure,
                                 ItemStatus status, boolean trackInventory, BigDecimal reorderLevel,
                                 BigDecimal reorderQuantity, long version, Instant createdAt,
                                 Instant updatedAt) {}

    // ===== Supplier =====
    public record CreateSupplierRequest(String supplierCode, String name, String contactEmail,
                                          String contactPhone, String address, String taxNumber,
                                          String paymentTerms, String currency) {}
    public record UpdateSupplierRequest(String name, String contactEmail, String contactPhone,
                                          String address, String taxNumber, String paymentTerms,
                                          String currency, Long expectedVersion) {}
    public record SupplierResponse(UUID id, UUID tenantId, String supplierCode, String name,
                                     SupplierStatus status, String contactEmail, String contactPhone,
                                     String address, String taxNumber, String paymentTerms,
                                     String currency, long version, Instant createdAt,
                                     Instant updatedAt) {}

    // ===== Warehouse =====
    public record CreateWarehouseRequest(String code, String name, String location,
                                           Boolean isPrimary) {}
    public record UpdateWarehouseRequest(String name, String location, Long expectedVersion) {}
    public record WarehouseResponse(UUID id, UUID tenantId, String code, String name,
                                      WarehouseStatus status, String location, boolean isPrimary,
                                      long version, Instant createdAt, Instant updatedAt) {}

    // ===== Inventory Balance =====
    public record InventoryBalanceResponse(UUID id, UUID tenantId, UUID warehouseId, UUID itemId,
                                              String itemCode, String itemName, String warehouseCode,
                                              BigDecimal onHand, BigDecimal reserved, BigDecimal incoming,
                                              BigDecimal available, long version, Instant updatedAt) {}
    public record InventorySummary(int totalItems, int activeItems, int totalWarehouses,
                                     int totalSuppliers, int lowStockItems, BigDecimal totalInventoryValue) {}

    // ===== Reservation =====
    public record CreateReservationRequest(UUID warehouseId, UUID itemId, BigDecimal quantity,
                                              String source, String externalReference,
                                              Instant expiresAt) {}
    public record ReservationResponse(UUID id, UUID tenantId, UUID warehouseId, UUID itemId,
                                        BigDecimal quantity, String source, String externalReference,
                                        ReservationStatus status, Instant expiresAt, long version,
                                        Instant createdAt, Instant updatedAt) {}

    // ===== Movement =====
    public record MovementResponse(UUID id, UUID tenantId, UUID warehouseId, UUID itemId,
                                     BigDecimal quantity, MovementType movementType, String referenceType,
                                     UUID referenceId, String reason, UUID performedBy,
                                     Instant createdAt) {}

    // ===== Purchase Requisition =====
    public record CreateRequisitionRequest(String reason, RequisitionPriority priority,
                                              UUID requesterId,
                                              List<CreateRequisitionItem> items) {}
    public record CreateRequisitionItem(UUID itemId, BigDecimal quantity, LocalDate requiredDate,
                                          BigDecimal estimatedUnitCost, String notes) {}
    public record RequisitionItemResponse(UUID id, UUID requisitionId, UUID itemId, String itemCode,
                                            String itemName, BigDecimal quantity, LocalDate requiredDate,
                                            BigDecimal estimatedUnitCost, String notes, Instant createdAt) {}
    public record PurchaseRequisitionResponse(UUID id, UUID tenantId, String requisitionNumber,
                                                 UUID requesterId, String reason, RequisitionPriority priority,
                                                 RequisitionStatus status, List<RequisitionItemResponse> items,
                                                 long version, Instant createdAt, Instant updatedAt) {}

    // ===== Purchase Order =====
    public record CreatePurchaseOrderRequest(UUID supplierId, String currency, LocalDate expectedDate,
                                                 UUID requisitionId, List<CreatePurchaseOrderItem> items) {}
    public record CreatePurchaseOrderItem(UUID itemId, BigDecimal quantity, BigDecimal unitCost) {}
    public record PurchaseOrderItemResponse(UUID id, UUID poId, UUID itemId, String itemCode,
                                              String itemName, BigDecimal quantity, BigDecimal unitCost,
                                              BigDecimal receivedQuantity, BigDecimal lineTotal,
                                              Instant createdAt) {}
    public record PurchaseOrderResponse(UUID id, UUID tenantId, String poNumber, UUID supplierId,
                                          String supplierName, String currency, PurchaseOrderStatus status,
                                          BigDecimal subtotal, BigDecimal taxTotal, BigDecimal total,
                                          LocalDate expectedDate, UUID createdBy, UUID approvedBy,
                                          List<PurchaseOrderItemResponse> items, long version,
                                          Instant createdAt, Instant updatedAt) {}

    // ===== Goods Receipt =====
    public record CreateGoodsReceiptRequest(UUID poId, UUID warehouseId,
                                                List<CreateGoodsReceiptItem> items) {}
    public record CreateGoodsReceiptItem(UUID poItemId, UUID itemId, BigDecimal quantity) {}
    public record GoodsReceiptItemResponse(UUID id, UUID receiptId, UUID poItemId, UUID itemId,
                                             String itemCode, String itemName, BigDecimal quantity,
                                             Instant createdAt) {}
    public record GoodsReceiptResponse(UUID id, UUID tenantId, String receiptNumber, UUID poId,
                                         UUID warehouseId, String warehouseCode, GoodsReceiptStatus status,
                                         UUID receivedBy, Instant postedAt, List<GoodsReceiptItemResponse> items,
                                         long version, Instant createdAt, Instant updatedAt) {}

    // ===== Transfer =====
    public record CreateTransferRequest(UUID fromWarehouseId, UUID toWarehouseId,
                                          List<CreateTransferItem> items) {}
    public record CreateTransferItem(UUID itemId, BigDecimal quantity) {}
    public record TransferItemResponse(UUID id, UUID transferId, UUID itemId, String itemCode,
                                         String itemName, BigDecimal quantity, Instant createdAt) {}
    public record TransferResponse(UUID id, UUID tenantId, String transferNumber,
                                     UUID fromWarehouseId, UUID toWarehouseId,
                                     String fromWarehouseCode, String toWarehouseCode,
                                     TransferStatus status, UUID requestedBy,
                                     List<TransferItemResponse> items, long version,
                                     Instant createdAt, Instant updatedAt) {}

    // ===== Adjustment =====
    public record CreateAdjustmentRequest(UUID warehouseId, UUID itemId, BigDecimal quantityDelta,
                                             String reasonCode, String notes) {}
    public record AdjustmentResponse(UUID id, UUID tenantId, String adjustmentNumber, UUID warehouseId,
                                       UUID itemId, String itemCode, String itemName, String warehouseCode,
                                       BigDecimal quantityDelta, String reasonCode, String notes,
                                       UUID requestedBy, UUID approvedBy, AdjustmentStatus status,
                                       long version, Instant createdAt, Instant updatedAt) {}

    // ===== Dashboard =====
    public record ErpDashboardSummary(int totalItems, int activeItems, int totalSuppliers,
                                        int totalWarehouses, int lowStockItems, int pendingRequisitions,
                                        int pendingPurchaseOrders, BigDecimal totalInventoryValue) {}
}
