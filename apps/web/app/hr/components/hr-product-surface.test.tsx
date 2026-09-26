import { existsSync, readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";

const COMPONENT_PATH = resolve(__dirname, "hr-product-surface.tsx");
const STYLE_PATH = resolve(__dirname, "hr-g2-visual.module.css");

function productSurfaceSource(): string {
  expect(existsSync(COMPONENT_PATH), "hr-product-surface.tsx must exist").toBe(true);
  if (!existsSync(COMPONENT_PATH)) return "";
  return readFileSync(COMPONENT_PATH, "utf8");
}

describe("HR G2 product-surface primitives", () => {
  it("exports the shared primitives required by every G2 product screen", () => {
    const source = productSurfaceSource();
    for (const component of [
      "HrProductHeader",
      "HrKpiGrid",
      "HrKpiCard",
      "HrActionBar",
      "HrOperationalPanel",
      "HrMobileRecordList",
    ]) {
      expect(source, component).toContain(`export function ${component}`);
    }
  });

  it("provides semantic product summary, action, operational, and mobile-record regions", () => {
    const source = productSurfaceSource();
    expect(source).toContain('data-testid="g2-product-header"');
    expect(source).toContain('data-testid="g2-kpi-grid"');
    expect(source).toContain('data-testid="g2-action-bar"');
    expect(source).toContain('data-testid="g2-operational-panel"');
    expect(source).toContain('data-testid="g2-mobile-record-list"');
    expect(source).toContain("aria-label={label}");
    expect(source).toContain("<h1");
  });

  it("keeps focus-visible and mobile behavior inside the existing G2 SDS stylesheet", () => {
    const css = readFileSync(STYLE_PATH, "utf8");
    expect(css).toContain(":focus-visible");
    expect(css).toContain("productSurface");
    expect(css).toContain("productKpiGrid");
    expect(css).toContain("productActionBar");
    expect(css).toContain("productOperationalPanel");
    expect(css).toContain("productMobileRecords");
    expect(css).toMatch(/@media[^\{]*\(max-width:\s*[^\)]+\)/);
  });
});
