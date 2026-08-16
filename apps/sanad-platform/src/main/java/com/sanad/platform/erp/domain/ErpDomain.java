package com.sanad.platform.erp.domain;

/**
 * ERP Core Platform domain types (v20260816.7).
 *
 * <p>Defines the canonical status / type enums used by the ERP tables
 * (erp_items, erp_suppliers, erp_warehouses, erp_inventory_balances,
 * erp_inventory_movements, erp_inventory_reservations,
 * erp_purchase_requisitions, erp_purchase_requisition_items,
 * erp_purchase_orders, erp_purchase_order_items,
 * erp_goods_receipts, erp_goods_receipt_items,
 * erp_inventory_transfers, erp_inventory_transfer_items,
 * erp_inventory_adjustments).
 *
 * <p>The values MUST match the CHECK constraints defined in
 * {@code V20260816_7__create_erp_tables.sql}.
 */
public final class ErpDomain {

    private ErpDomain() {}

    /** Lifecycle status of an {@code erp_items} row. */
    public enum ItemStatus { DRAFT, ACTIVE, INACTIVE, ARCHIVED }

    /** Type of an item (drives inventory / fulfillment model). */
    public enum ItemType { GOODS, SERVICE, DIGITAL, RAW_MATERIAL, FINISHED_GOOD }

    /** Unit of measure for an item. */
    public enum UnitOfMeasure { EACH, KG, G, L, M, CM, BOX, PACK, UNIT }

    /** Lifecycle status of an {@code erp_suppliers} row. */
    public enum SupplierStatus { PENDING, ACTIVE, INACTIVE, BLOCKED, ARCHIVED }

    /** Lifecycle status of an {@code erp_warehouses} row. */
    public enum WarehouseStatus { ACTIVE, INACTIVE, ARCHIVED }

    /**
     * Type of an {@code erp_inventory_movements} row. The movement ledger is
     * append-only — rows are never UPDATED or DELETED.
     */
    public enum MovementType {
        RECEIPT, ISSUE, TRANSFER_OUT, TRANSFER_IN,
        ADJUSTMENT_IN, ADJUSTMENT_OUT, RESERVATION, RELEASE,
        FULFILLMENT, RETURN
    }

    /** Lifecycle status of an {@code erp_inventory_reservations} row. */
    public enum ReservationStatus { PENDING, RESERVED, CONFIRMED, RELEASED, EXPIRED, CANCELLED }

    /** Lifecycle status of an {@code erp_purchase_requisitions} row. */
    public enum RequisitionStatus { DRAFT, SUBMITTED, APPROVED, REJECTED, CONVERTED, CANCELLED }

    /** Priority of an {@code erp_purchase_requisitions} row. */
    public enum RequisitionPriority { LOW, NORMAL, HIGH, URGENT }

    /** Lifecycle status of an {@code erp_purchase_orders} row. */
    public enum PurchaseOrderStatus {
        DRAFT, SUBMITTED, APPROVED, SENT,
        PARTIALLY_RECEIVED, RECEIVED, CLOSED, CANCELLED
    }

    /** Lifecycle status of an {@code erp_goods_receipts} row. */
    public enum GoodsReceiptStatus { DRAFT, POSTED, CANCELLED }

    /** Lifecycle status of an {@code erp_inventory_transfers} row. */
    public enum TransferStatus { DRAFT, SUBMITTED, IN_TRANSIT, RECEIVED, CANCELLED }

    /** Lifecycle status of an {@code erp_inventory_adjustments} row. */
    public enum AdjustmentStatus { PENDING, APPROVED, POSTED, REJECTED }
}
