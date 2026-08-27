import { describe, expect, it } from "vitest";
import { existsSync } from "node:fs";
import { resolve } from "node:path";

describe("ERP operational route surface", () => {
  it("contains the shared workspace and every human-operable ERP route", () => {
    const root = process.cwd();
    const required = [
      "app/erp/components/erp-workspace.tsx",
      "app/erp/erp.module.css",
      "app/erp/items/page.tsx",
      "app/erp/suppliers/page.tsx",
      "app/erp/warehouses/page.tsx",
      "app/erp/inventory/page.tsx",
      "app/erp/requisitions/page.tsx",
      "app/erp/purchase-orders/page.tsx",
      "app/erp/goods-receipts/page.tsx",
    ];

    for (const path of required) {
      expect(existsSync(resolve(root, path)), `${path} must exist`).toBe(true);
    }
  });
});
