import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";

const HR_ROOT = resolve(__dirname);

function page(path: string): string {
  return readFileSync(resolve(HR_ROOT, path), "utf8");
}

function expectProductSurface(source: string, routeMarker: string) {
  expect(source).toContain("HrProductHeader");
  expect(source).toContain("HrKpiGrid");
  expect(source).toContain("HrKpiCard");
  expect(source).toContain("HrActionBar");
  expect(source).toContain("HrOperationalPanel");
  expect(source).toContain("HrMobileRecordList");
  expect(source).toContain(`data-testid=\"${routeMarker}\"`);
}

describe("G2 Employee product surfaces", () => {
  it("renders Attendance as an operational dashboard rather than heading + table only", () => {
    const source = page("attendance/page.tsx");
    expectProductSurface(source, "attendance-product-surface");
    expect(source).toContain("attendance-current-state");
    expect(source).toContain("attendance-today-worked");
    expect(source).toContain("attendance-history-panel");
    expect(source).toContain("attendance-clock-in");
    expect(source).toContain("attendance-clock-out");
  });

  it("renders My Timesheets with period/status KPIs, submit actions, and recent-period mobile records", () => {
    const source = page("timesheets/page.tsx");
    expectProductSurface(source, "timesheets-product-surface");
    expect(source).toContain("timesheets-current-period");
    expect(source).toContain("timesheets-current-state");
    expect(source).toContain("timesheets-submit-action");
    expect(source).toContain("timesheets-history-panel");
  });

  it("keeps Timesheets state and KPI copy inside route-scoped i18n", () => {
    const source = page("timesheets/page.tsx");
    expect(source).not.toContain("function stateLabel");
    expect(source).toContain('t("hrm.timesheets.state." +');
    expect(source).toContain('t("hrm.timesheets.kpi.awaitingApproval")');
    expect(source).toContain('t("hrm.timesheets.kpi.approved")');
    expect(source).not.toContain('locale === "ar" ? "بانتظار الاعتماد"');
  });

  it("renders My Leave with balances, lifecycle summary, request action, and recent requests", () => {
    const source = page("leave/page.tsx");
    expectProductSurface(source, "leave-product-surface");
    expect(source).toContain("leave-balance-summary");
    expect(source).toContain("leave-pending-summary");
    expect(source).toContain("leave-approved-summary");
    expect(source).toContain("request-leave-toggle");
    expect(source).toContain("leave-requests-panel");
  });
});
