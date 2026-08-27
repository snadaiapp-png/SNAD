import { describe, expect, it } from "vitest";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";

function source(path: string) {
  return readFileSync(resolve(process.cwd(), path), "utf8");
}

function expectCalls(path: string, calls: string[]) {
  const content = source(path);
  for (const call of calls) expect(content, `${path} must wire ${call}`).toContain(`erpApi.${call}`);
}

describe("ERP operational page wiring", () => {
  it("wires inventory write workflows", () => {
    expectCalls("app/erp/inventory/page.tsx", [
      "listBalances", "listReservations", "listMovements", "createReservation",
      "releaseReservation", "confirmReservation", "createTransfer", "submitTransfer",
      "receiveTransfer", "createAdjustment", "approveAdjustment",
    ]);
  });

  it("wires requisition workflow", () => {
    expectCalls("app/erp/requisitions/page.tsx", [
      "createRequisition", "submitRequisition", "approveRequisition", "rejectRequisition",
    ]);
  });

  it("wires purchase order workflow", () => {
    expectCalls("app/erp/purchase-orders/page.tsx", [
      "createPurchaseOrder", "submitPurchaseOrder", "approvePurchaseOrder", "cancelPurchaseOrder",
    ]);
  });

  it("wires goods receipt workflow", () => {
    expectCalls("app/erp/goods-receipts/page.tsx", ["createGoodsReceipt", "postGoodsReceipt"]);
  });
});
