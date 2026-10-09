import { readFileSync, existsSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";

/**
 * G4-T9 RED contract. No payroll UI has been introduced at the time of this
 * baseline; implementation must satisfy these checks without weakening them.
 */
const payrollRoot = resolve(__dirname, "payroll");
const page = resolve(payrollRoot, "page.tsx");
const client = resolve(payrollRoot, "payroll-client.tsx");
const workspace = resolve(__dirname, "components/hr-workspace.tsx");

describe("HRM G4-T9 Payroll review UI contract", () => {
  it("exposes an HR payroll route and dedicated review client", () => {
    expect(existsSync(page)).toBe(true);
    expect(existsSync(client)).toBe(true);
  });

  it("exposes governed review navigation", () => {
    const source = readFileSync(workspace, "utf8");
    expect(source).toContain('href: "/hr/payroll"');
    expect(source).toContain("HRM.PAYROLL.VIEW");
  });

  it("presents payroll runs, items, exceptions and guarded actions", () => {
    const source = readFileSync(client, "utf8");
    for (const action of ["calculate", "recalculate", "review", "approve", "export"]) {
      expect(source).toContain(action);
    }
    for (const state of ["loading", "empty", "error", "forbidden", "conflict"]) {
      expect(source.toLowerCase()).toContain(state);
    }
    expect(source).toContain("Idempotency-Key");
    expect(source).toContain("HRM.PAYROLL.VIEW");
  });
});
