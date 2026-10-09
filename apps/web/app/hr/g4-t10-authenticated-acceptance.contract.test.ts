import { readFileSync, existsSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";

/** G4-T10 RED: authenticated acceptance must be a real dedicated Playwright
 * suite rather than a mocked API/unit test or evidence from G4-T9. */
const spec = resolve(__dirname, "../../e2e/g4-payroll-authenticated.spec.ts");
const config = resolve(__dirname, "../../playwright-g4-payroll.config.ts");

describe("G4-T10 independent payroll authenticated acceptance contract", () => {
  it("requires a dedicated executable Playwright suite and stack configuration", () => {
    expect(existsSync(spec), "G4-T10 Playwright suite must exist").toBe(true);
    expect(existsSync(config), "G4-T10 Playwright configuration must exist").toBe(true);
  });

  it("requires authenticated end-to-end lifecycle and denied foreign-tenant access", () => {
    const source = readFileSync(spec, "utf8");
    for (const contract of [
      "create", "calculate", "review", "approve", "export",
      "cross-tenant", "Idempotency-Key", "403", "500",
    ]) {
      expect(source, `missing G4-T10 acceptance assertion: ${contract}`).toContain(contract);
    }
    expect(source).not.toContain("test.skip");
    expect(source).not.toContain("page.route(");
  });
});
