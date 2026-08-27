import { describe, expect, it } from "vitest";
import { erpApi } from "./erp-api";

describe("erpApi operational contract", () => {
  it("exposes ERP mutation and workflow operations required by the human UI", () => {
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

    for (const method of required) {
      expect(api[method], `erpApi.${method} must exist`).toBeTypeOf("function");
    }
  });
});
