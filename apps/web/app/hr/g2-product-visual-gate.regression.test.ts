import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";

const WEB_ROOT = resolve(__dirname, "../../..");
const visualSpec = readFileSync(resolve(WEB_ROOT, "e2e/g2-visual.spec.ts"), "utf8");
const visualHelpers = readFileSync(resolve(WEB_ROOT, "e2e/g2-visual-helpers.ts"), "utf8");

const REQUIRED_MARKERS = [
  "g2-product-areas",
  "g2-launcher-self-summary",
  "g2-launcher-team-summary",
  "g2-launcher-hr-summary",
  "attendance-product-surface",
  "attendance-current-state",
  "attendance-history-panel",
  "timesheets-product-surface",
  "timesheets-current-period",
  "timesheets-current-state",
  "timesheets-history-panel",
  "leave-product-surface",
  "leave-balance-summary",
  "leave-pending-summary",
  "leave-requests-panel",
  "team-attendance-product-surface",
  "team-attendance-open-count",
  "team-attendance-records-panel",
  "team-timesheets-product-surface",
  "team-timesheets-pending-count",
  "team-timesheets-queue-panel",
  "leave-approvals-product-surface",
  "leave-approvals-manager-count",
  "leave-approvals-hr-count",
  "leave-approvals-queue-panel",
  "attendance-report-product-surface",
  "attendance-report-people-count",
  "attendance-report-filter-bar",
  "attendance-report-results-panel",
  "schedules-product-surface",
  "schedules-total-count",
  "schedules-create-action",
  "schedules-records-panel",
  "attendance-admin-product-surface",
  "attendance-admin-open-count",
  "attendance-admin-correction-state",
  "attendance-admin-records-panel",
  "leave-policies-product-surface",
  "leave-policies-total-count",
  "leave-policies-admin-state",
  "leave-policies-records-panel",
] as const;

describe("G2 visual product gate", () => {
  it("extends every visual surface with explicit product and route-specific requirements", () => {
    expect(visualHelpers).toContain("productSurfaceTestId: string");
    expect(visualHelpers).toContain("requiredProductTestIds: readonly string[]");
    expect(visualHelpers).toContain("page.getByTestId(surface.productSurfaceTestId)");
    expect(visualHelpers).toContain("for (const testId of surface.requiredProductTestIds)");
  });

  it("binds the visual matrix to product markers instead of ready selectors alone", () => {
    for (const marker of REQUIRED_MARKERS) expect(visualSpec).toContain(marker);
    expect(visualSpec).toContain("productSurfaceTestId");
    expect(visualSpec).toContain("requiredProductTestIds");
  });

  it("keeps the real-data-row check as supplemental evidence rather than the product definition", () => {
    expect(visualSpec).toContain("G2_VISUAL_REQUIRE_PRODUCT_DATA");
    expect(visualSpec).toContain('tr[data-testid="hr-data-row"]');
    expect(visualHelpers).toContain("productSurfaceTestId");
  });
});
