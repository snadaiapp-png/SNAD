import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";

const HR_ROOT = resolve(__dirname);

const PRODUCT_ROUTES = [
  "attendance/page.tsx",
  "timesheets/page.tsx",
  "leave/page.tsx",
  "team-attendance/page.tsx",
  "team-timesheets/page.tsx",
  "leave/approvals/page.tsx",
  "schedules/page.tsx",
  "attendance/admin/page.tsx",
  "leave/policies/page.tsx",
  "reports/attendance/page.tsx",
] as const;

describe("G2 product surfaces", () => {
  it.each(PRODUCT_ROUTES)("renders a product header and structured surface: %s", (route) => {
    const source = readFileSync(resolve(HR_ROOT, route), "utf8");
    expect(source).toContain("G2ProductHeader");
    expect(source).toContain("G2SurfaceSection");
    expect(source).toContain('data-testid="g2-product-surface"');
  });

  it("requires operational metrics on the employee attendance surface", () => {
    const source = readFileSync(resolve(HR_ROOT, "attendance/page.tsx"), "utf8");
    expect(source).toContain("G2MetricGrid");
    expect(source).toContain("G2MetricCard");
    expect(source).toContain('data-testid="attendance-current-state"');
  });

  it("requires balance metrics and request lifecycle presentation on leave", () => {
    const source = readFileSync(resolve(HR_ROOT, "leave/page.tsx"), "utf8");
    expect(source).toContain("G2MetricGrid");
    expect(source).toContain('data-testid="leave-balance-summary"');
    expect(source).toContain('data-testid="leave-request-lifecycle"');
  });
});
