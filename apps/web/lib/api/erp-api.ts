import { apiClient } from "./client";

export type ItemStatus = "DRAFT" | "ACTIVE" | "INACTIVE" | "ARCHIVED";
export type ItemType = "GOODS" | "SERVICE" | "DIGITAL" | "RAW_MATERIAL" | "FINISHED_GOOD";
export type UnitOfMeasure = "EACH" | "KG" | "G" | "L" | "M" | "CM" | "BOX" | "PACK" | "UNIT";
export type SupplierStatus = "PENDING" | "ACTIVE" | "INACTIVE" | "BLOCKED" | "ARCHIVED";
export type WarehouseStatus = "ACTIVE" | "INACTIVE" | "ARCHIVED";
export type MovementType = "RECEIPT" | "ISSUE" | "TRANSFER_OUT" | "TRANSFER_IN" | "ADJUSTMENT_IN" | "ADJUSTMENT_OUT" | "RESERVATION" | "RELEASE" | "FULFILLMENT" | "RETURN";
export type ReservationStatus = "PENDING" | "RESERVED" | "CONFIRMED" | "RELEASED" | "EXPIRED" | "CANCELLED";
export type RequisitionStatus = "DRAFT" | "SUBMITTED" | "APPROVED" | "REJECTED" | "CONVERTED" | "CANCELLED";
export type RequisitionPriority = "LOW" | "NORMAL" | "HIGH" | "URGENT";
export type PurchaseOrderStatus = "DRAFT" | "SUBMITTED" | "APPROVED" | "SENT" | "PARTIALLY_RECEIVED" | "RECEIVED" | "CLOSED" | "CANCELLED";
export type GoodsReceiptStatus = "DRAFT" | "POSTED" | "CANCELLED";
export type TransferStatus = "DRAFT" | "SUBMITTED" | "IN_TRANSIT" | "RECEIVED" | "CANCELLED";
export type AdjustmentStatus = "PENDING" | "APPROVED" | "POSTED" | "REJECTED";

export interface ErpDashboardSummary {
  totalItems: number;
  activeItems: number;
  totalSuppliers: number;
  totalWarehouses: number;
  lowStockItems: number;
  pendingRequisitions: number;
  pendingPurchaseOrders: number;
  totalInventoryValue: number;
}

export interface CreateItemRequest {
  code: string;
  sku: string | null;
  name: string;
  description: string | null;
  itemType: ItemType;
  unitOfMeasure: UnitOfMeasure;
  trackInventory: boolean;
  reorderLevel: number;
  reorderQuantity: number;
}
export interface UpdateItemRequest extends Omit<CreateItemRequest, "code"> { expectedVersion: number; }
export interface ItemResponse extends CreateItemRequest {
  id: string;
  tenantId: string;
  status: ItemStatus;
  version: number;
  createdAt: string;
  updatedAt: string;
}

export interface CreateSupplierRequest {
  supplierCode: string;
  name: string;
  contactEmail: string | null;
  contactPhone: string | null;
  address: string | null;
  taxNumber: string | null;
  paymentTerms: string | null;
  currency: string;
}
export interface UpdateSupplierRequest extends Omit<CreateSupplierRequest, "supplierCode"> { expectedVersion: number; }
export interface SupplierResponse extends CreateSupplierRequest {
  id: string;
  tenantId: string;
  status: SupplierStatus;
  version: number;
  createdAt: string;
  updatedAt: string;
}

export interface CreateWarehouseRequest { code: string; name: string; location: string | null; isPrimary: boolean; }
export interface UpdateWarehouseRequest { name: string; location: string | null; expectedVersion: number; }
export interface WarehouseResponse extends CreateWarehouseRequest {
  id: string;
  tenantId: string;
  status: WarehouseStatus;
  version: number;
  createdAt: string;
  updatedAt: string;
}

export interface InventoryBalanceResponse {
  id: string;
  tenantId: string;
  warehouseId: string;
  itemId: string;
  itemCode: string | null;
  itemName: string | null;
  warehouseCode: string | null;
  onHand: number;
  reserved: number;
  incoming: number;
  available: number;
  version: number;
  updatedAt: string;
}
export interface InventorySummary {
  totalItems: number;
  activeItems: number;
  totalWarehouses: number;
  totalSuppliers: number;
  lowStockItems: number;
  totalInventoryValue: number;
}

export interface CreateReservationRequest {
  warehouseId: string;
  itemId: string;
  quantity: number;
  source: string | null;
  externalReference: string | null;
  expiresAt: string | null;
}
export interface ReservationResponse extends CreateReservationRequest {
  id: string;
  tenantId: string;
  status: ReservationStatus;
  version: number;
  createdAt: string;
  updatedAt: string;
}

export interface MovementResponse {
  id: string;
  tenantId: string;
  warehouseId: string;
  itemId: string;
  quantity: number;
  movementType: MovementType;
  referenceType: string | null;
  referenceId: string | null;
  reason: string | null;
  performedBy: string | null;
  createdAt: string;
}

export interface CreateTransferItem { itemId: string; quantity: number; }
export interface CreateTransferRequest { fromWarehouseId: string; toWarehouseId: string; items: CreateTransferItem[]; }
export interface TransferItemResponse extends CreateTransferItem {
  id: string;
  transferId: string;
  itemCode: string | null;
  itemName: string | null;
  createdAt: string;
}
export interface TransferResponse extends CreateTransferRequest {
  id: string;
  tenantId: string;
  transferNumber: string;
  fromWarehouseCode: string | null;
  toWarehouseCode: string | null;
  status: TransferStatus;
  requestedBy: string | null;
  items: TransferItemResponse[];
  version: number;
  createdAt: string;
  updatedAt: string;
}

export interface CreateAdjustmentRequest {
  warehouseId: string;
  itemId: string;
  quantityDelta: number;
  reasonCode: string;
  notes: string | null;
}
export interface AdjustmentResponse extends CreateAdjustmentRequest {
  id: string;
  tenantId: string;
  adjustmentNumber: string;
  itemCode: string | null;
  itemName: string | null;
  warehouseCode: string | null;
  requestedBy: string | null;
  approvedBy: string | null;
  status: AdjustmentStatus;
  version: number;
  createdAt: string;
  updatedAt: string;
}

export interface CreateRequisitionItem {
  itemId: string;
  quantity: number;
  requiredDate: string | null;
  estimatedUnitCost: number | null;
  notes: string | null;
}
export interface CreateRequisitionRequest {
  reason: string | null;
  priority: RequisitionPriority;
  requesterId: string | null;
  items: CreateRequisitionItem[];
}
export interface RequisitionItemResponse extends CreateRequisitionItem {
  id: string;
  requisitionId: string;
  itemCode: string | null;
  itemName: string | null;
  createdAt: string;
}
export interface PurchaseRequisitionResponse extends Omit<CreateRequisitionRequest, "items"> {
  id: string;
  tenantId: string;
  requisitionNumber: string;
  status: RequisitionStatus;
  items: RequisitionItemResponse[];
  version: number;
  createdAt: string;
  updatedAt: string;
}

export interface CreatePurchaseOrderItem { itemId: string; quantity: number; unitCost: number; }
export interface CreatePurchaseOrderRequest {
  supplierId: string;
  currency: string;
  expectedDate: string | null;
  requisitionId: string | null;
  items: CreatePurchaseOrderItem[];
}
export interface PurchaseOrderItemResponse extends CreatePurchaseOrderItem {
  id: string;
  poId: string;
  itemCode: string | null;
  itemName: string | null;
  receivedQuantity: number;
  lineTotal: number;
  createdAt: string;
}
export interface PurchaseOrderResponse extends Omit<CreatePurchaseOrderRequest, "items"> {
  id: string;
  tenantId: string;
  poNumber: string;
  supplierName: string | null;
  status: PurchaseOrderStatus;
  subtotal: number;
  taxTotal: number;
  total: number;
  createdBy: string | null;
  approvedBy: string | null;
  items: PurchaseOrderItemResponse[];
  version: number;
  createdAt: string;
  updatedAt: string;
}

export interface CreateGoodsReceiptItem { poItemId: string | null; itemId: string; quantity: number; }
export interface CreateGoodsReceiptRequest { poId: string | null; warehouseId: string; items: CreateGoodsReceiptItem[]; }
export interface GoodsReceiptItemResponse extends CreateGoodsReceiptItem {
  id: string;
  receiptId: string;
  itemCode: string | null;
  itemName: string | null;
  createdAt: string;
}
export interface GoodsReceiptResponse extends Omit<CreateGoodsReceiptRequest, "items"> {
  id: string;
  tenantId: string;
  receiptNumber: string;
  warehouseCode: string | null;
  status: GoodsReceiptStatus;
  receivedBy: string | null;
  postedAt: string | null;
  items: GoodsReceiptItemResponse[];
  version: number;
  createdAt: string;
  updatedAt: string;
}

const BASE = "/api/v1/erp";
const encode = (value: string) => encodeURIComponent(value);

export const erpApi = {
  dashboard: () => apiClient.get<ErpDashboardSummary>(`${BASE}/dashboard`),

  listItems: () => apiClient.get<ItemResponse[]>(`${BASE}/items`),
  getItem: (id: string) => apiClient.get<ItemResponse>(`${BASE}/items/${encode(id)}`),
  lowStockItems: () => apiClient.get<ItemResponse[]>(`${BASE}/items/low-stock`),
  createItem: (request: CreateItemRequest) => apiClient.post<ItemResponse, CreateItemRequest>(`${BASE}/items`, request),
  updateItem: (id: string, request: UpdateItemRequest) => apiClient.put<ItemResponse, UpdateItemRequest>(`${BASE}/items/${encode(id)}`, request),
  activateItem: (id: string) => apiClient.post<ItemResponse>(`${BASE}/items/${encode(id)}/activate`),
  inactivateItem: (id: string) => apiClient.post<ItemResponse>(`${BASE}/items/${encode(id)}/inactivate`),
  archiveItem: (id: string) => apiClient.post<ItemResponse>(`${BASE}/items/${encode(id)}/archive`),

  listSuppliers: () => apiClient.get<SupplierResponse[]>(`${BASE}/suppliers`),
  getSupplier: (id: string) => apiClient.get<SupplierResponse>(`${BASE}/suppliers/${encode(id)}`),
  createSupplier: (request: CreateSupplierRequest) => apiClient.post<SupplierResponse, CreateSupplierRequest>(`${BASE}/suppliers`, request),
  updateSupplier: (id: string, request: UpdateSupplierRequest) => apiClient.put<SupplierResponse, UpdateSupplierRequest>(`${BASE}/suppliers/${encode(id)}`, request),
  activateSupplier: (id: string) => apiClient.post<SupplierResponse>(`${BASE}/suppliers/${encode(id)}/activate`),
  blockSupplier: (id: string) => apiClient.post<SupplierResponse>(`${BASE}/suppliers/${encode(id)}/block`),

  listWarehouses: () => apiClient.get<WarehouseResponse[]>(`${BASE}/warehouses`),
  getWarehouse: (id: string) => apiClient.get<WarehouseResponse>(`${BASE}/warehouses/${encode(id)}`),
  createWarehouse: (request: CreateWarehouseRequest) => apiClient.post<WarehouseResponse, CreateWarehouseRequest>(`${BASE}/warehouses`, request),
  updateWarehouse: (id: string, request: UpdateWarehouseRequest) => apiClient.put<WarehouseResponse, UpdateWarehouseRequest>(`${BASE}/warehouses/${encode(id)}`, request),
  activateWarehouse: (id: string) => apiClient.post<WarehouseResponse>(`${BASE}/warehouses/${encode(id)}/activate`),
  archiveWarehouse: (id: string) => apiClient.post<WarehouseResponse>(`${BASE}/warehouses/${encode(id)}/archive`),

  listBalances: (warehouseId?: string) => apiClient.get<InventoryBalanceResponse[]>(
    warehouseId ? `${BASE}/inventory/balances?warehouseId=${encode(warehouseId)}` : `${BASE}/inventory/balances`,
  ),
  inventorySummary: () => apiClient.get<InventorySummary>(`${BASE}/inventory/summary`),
  listReservations: () => apiClient.get<ReservationResponse[]>(`${BASE}/inventory/reservations`),
  createReservation: (request: CreateReservationRequest) => apiClient.post<ReservationResponse, CreateReservationRequest>(`${BASE}/inventory/reservations`, request),
  releaseReservation: (id: string) => apiClient.post<ReservationResponse>(`${BASE}/inventory/reservations/${encode(id)}/release`),
  confirmReservation: (id: string) => apiClient.post<ReservationResponse>(`${BASE}/inventory/reservations/${encode(id)}/confirm`),
  listMovements: () => apiClient.get<MovementResponse[]>(`${BASE}/inventory/movements`),

  listTransfers: () => apiClient.get<TransferResponse[]>(`${BASE}/inventory/transfers`),
  getTransfer: (id: string) => apiClient.get<TransferResponse>(`${BASE}/inventory/transfers/${encode(id)}`),
  createTransfer: (request: CreateTransferRequest) => apiClient.post<TransferResponse, CreateTransferRequest>(`${BASE}/inventory/transfers`, request),
  submitTransfer: (id: string) => apiClient.post<TransferResponse>(`${BASE}/inventory/transfers/${encode(id)}/submit`),
  receiveTransfer: (id: string) => apiClient.post<TransferResponse>(`${BASE}/inventory/transfers/${encode(id)}/receive`),

  listAdjustments: () => apiClient.get<AdjustmentResponse[]>(`${BASE}/inventory/adjustments`),
  createAdjustment: (request: CreateAdjustmentRequest) => apiClient.post<AdjustmentResponse, CreateAdjustmentRequest>(`${BASE}/inventory/adjustments`, request),
  approveAdjustment: (id: string) => apiClient.post<AdjustmentResponse>(`${BASE}/inventory/adjustments/${encode(id)}/approve`),

  listRequisitions: () => apiClient.get<PurchaseRequisitionResponse[]>(`${BASE}/purchase-requisitions`),
  getRequisition: (id: string) => apiClient.get<PurchaseRequisitionResponse>(`${BASE}/purchase-requisitions/${encode(id)}`),
  createRequisition: (request: CreateRequisitionRequest) => apiClient.post<PurchaseRequisitionResponse, CreateRequisitionRequest>(`${BASE}/purchase-requisitions`, request),
  submitRequisition: (id: string) => apiClient.post<PurchaseRequisitionResponse>(`${BASE}/purchase-requisitions/${encode(id)}/submit`),
  approveRequisition: (id: string) => apiClient.post<PurchaseRequisitionResponse>(`${BASE}/purchase-requisitions/${encode(id)}/approve`),
  rejectRequisition: (id: string) => apiClient.post<PurchaseRequisitionResponse>(`${BASE}/purchase-requisitions/${encode(id)}/reject`),

  listPurchaseOrders: () => apiClient.get<PurchaseOrderResponse[]>(`${BASE}/purchase-orders`),
  getPurchaseOrder: (id: string) => apiClient.get<PurchaseOrderResponse>(`${BASE}/purchase-orders/${encode(id)}`),
  createPurchaseOrder: (request: CreatePurchaseOrderRequest) => apiClient.post<PurchaseOrderResponse, CreatePurchaseOrderRequest>(`${BASE}/purchase-orders`, request),
  submitPurchaseOrder: (id: string) => apiClient.post<PurchaseOrderResponse>(`${BASE}/purchase-orders/${encode(id)}/submit`),
  approvePurchaseOrder: (id: string) => apiClient.post<PurchaseOrderResponse>(`${BASE}/purchase-orders/${encode(id)}/approve`),
  cancelPurchaseOrder: (id: string) => apiClient.post<PurchaseOrderResponse>(`${BASE}/purchase-orders/${encode(id)}/cancel`),

  listGoodsReceipts: () => apiClient.get<GoodsReceiptResponse[]>(`${BASE}/goods-receipts`),
  getGoodsReceipt: (id: string) => apiClient.get<GoodsReceiptResponse>(`${BASE}/goods-receipts/${encode(id)}`),
  createGoodsReceipt: (request: CreateGoodsReceiptRequest) => apiClient.post<GoodsReceiptResponse, CreateGoodsReceiptRequest>(`${BASE}/goods-receipts`, request),
  postGoodsReceipt: (id: string) => apiClient.post<GoodsReceiptResponse>(`${BASE}/goods-receipts/${encode(id)}/post`),
};
