import { apiClient } from "./client";
export interface ErpDashboardSummary {
  totalItems: number; activeItems: number; totalSuppliers: number;
  totalWarehouses: number; lowStockItems: number; pendingRequisitions: number;
  pendingPurchaseOrders: number; totalInventoryValue: number;
}
export interface ItemResponse {
  id: string; code: string; sku?: string; name: string; status: string;
  itemType: string; unitOfMeasure: string; trackInventory: boolean;
  reorderLevel: number; reorderQuantity: number;
}
export interface SupplierResponse { id: string; supplierCode: string; name: string; status: string; }
export interface WarehouseResponse { id: string; code: string; name: string; status: string; isPrimary: boolean; }
const BASE = "/api/v1/erp";
export const erpApi = {
  dashboard: () => apiClient.get<ErpDashboardSummary>(`${BASE}/dashboard`),
  listItems: () => apiClient.get<ItemResponse[]>(`${BASE}/items`),
  listSuppliers: () => apiClient.get<SupplierResponse[]>(`${BASE}/suppliers`),
  listWarehouses: () => apiClient.get<WarehouseResponse[]>(`${BASE}/warehouses`),
};
