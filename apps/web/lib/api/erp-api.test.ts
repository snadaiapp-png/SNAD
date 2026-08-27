import { afterEach, describe, expect, it, vi } from "vitest";

vi.mock("./client", () => ({
  apiClient: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
  },
}));

import { erpApi } from "./erp-api";
import { apiClient } from "./client";

afterEach(() => vi.clearAllMocks());

describe("erpApi operational contract", () => {
  it("exposes every ERP operation required by the human workspace", () => {
    const api = erpApi as unknown as Record<string, unknown>;
    const required = [
      "createItem", "updateItem", "activateItem", "inactivateItem", "archiveItem",
      "createSupplier", "updateSupplier", "activateSupplier", "blockSupplier",
      "createWarehouse", "updateWarehouse", "activateWarehouse", "archiveWarehouse",
      "listBalances", "inventorySummary", "listReservations", "createReservation",
      "releaseReservation", "confirmReservation", "listMovements",
      "listTransfers", "createTransfer", "submitTransfer", "receiveTransfer",
      "listAdjustments", "createAdjustment", "approveAdjustment",
      "listRequisitions", "createRequisition", "submitRequisition", "approveRequisition", "rejectRequisition",
      "listPurchaseOrders", "createPurchaseOrder", "submitPurchaseOrder", "approvePurchaseOrder", "cancelPurchaseOrder",
      "listGoodsReceipts", "createGoodsReceipt", "postGoodsReceipt",
    ];
    for (const method of required) expect(api[method], `erpApi.${method} must exist`).toBeTypeOf("function");
  });

  it("creates an item through the canonical ERP endpoint", async () => {
    vi.mocked(apiClient.post).mockResolvedValue({ id: "item-1" });
    const request = {
      code: "SKU-1", sku: "SKU-1", name: "صنف 1", description: "",
      itemType: "GOODS" as const, unitOfMeasure: "EACH" as const,
      trackInventory: true, reorderLevel: 5, reorderQuantity: 10,
    };
    await erpApi.createItem(request);
    expect(apiClient.post).toHaveBeenCalledWith("/api/v1/erp/items", request);
  });

  it("creates a warehouse and purchase order with backend DTO payloads", async () => {
    vi.mocked(apiClient.post).mockResolvedValue({ id: "created" });
    const warehouse = { code: "WH-1", name: "المستودع الرئيسي", location: "جدة", isPrimary: true };
    await erpApi.createWarehouse(warehouse);
    expect(apiClient.post).toHaveBeenCalledWith("/api/v1/erp/warehouses", warehouse);

    const po = {
      supplierId: "supplier-1", currency: "SAR", expectedDate: "2026-09-01", requisitionId: null,
      items: [{ itemId: "item-1", quantity: 4, unitCost: 25 }],
    };
    await erpApi.createPurchaseOrder(po);
    expect(apiClient.post).toHaveBeenCalledWith("/api/v1/erp/purchase-orders", po);
  });

  it("reads the operational inventory surfaces", async () => {
    vi.mocked(apiClient.get).mockResolvedValue([]);
    await erpApi.listBalances("warehouse-1");
    await erpApi.listReservations();
    await erpApi.listMovements();
    expect(apiClient.get).toHaveBeenNthCalledWith(1, "/api/v1/erp/inventory/balances?warehouseId=warehouse-1");
    expect(apiClient.get).toHaveBeenNthCalledWith(2, "/api/v1/erp/inventory/reservations");
    expect(apiClient.get).toHaveBeenNthCalledWith(3, "/api/v1/erp/inventory/movements");
  });

  it("uses lifecycle workflow endpoints without fabricating local state", async () => {
    vi.mocked(apiClient.post).mockResolvedValue({ id: "x" });
    await erpApi.activateItem("item-1");
    await erpApi.approveRequisition("req-1");
    await erpApi.approvePurchaseOrder("po-1");
    await erpApi.postGoodsReceipt("gr-1");
    expect(apiClient.post).toHaveBeenNthCalledWith(1, "/api/v1/erp/items/item-1/activate");
    expect(apiClient.post).toHaveBeenNthCalledWith(2, "/api/v1/erp/purchase-requisitions/req-1/approve");
    expect(apiClient.post).toHaveBeenNthCalledWith(3, "/api/v1/erp/purchase-orders/po-1/approve");
    expect(apiClient.post).toHaveBeenNthCalledWith(4, "/api/v1/erp/goods-receipts/gr-1/post");
  });
});
