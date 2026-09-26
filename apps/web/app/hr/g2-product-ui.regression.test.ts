import { existsSync, readFileSync } from "node:fs";
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

describe("G2 product UI surfaces", () => {
  it("uses a dedicated product-surface primitive instead of a heading-plus-table shell", () => {
    const componentPath = resolve(HR_ROOT, "components/hr-g2-product-surface.tsx");
    expect(existsSync(componentPath)).toBe(true);
    if (!existsSync(componentPath)) return;

    const component = readFileSync(componentPath, "utf8");
    expect(component).toContain('data-testid="g2-product-surface"');
    expect(component).toContain('data-testid="g2-kpi-grid"');
    expect(component).toContain('data-testid="g2-primary-action-region"');
    expect(component).toContain('data-testid="g2-product-content"');

    for (const relative of PRODUCT_ROUTES) {
      const source = readFileSync(resolve(HR_ROOT, relative), "utf8");
      expect(source, relative).toContain("HrG2ProductSurface");
      expect(source, relative).toContain("metrics={");
    }
  });

  it("makes employee self-service screens visibly product-specific", () => {
    const attendance = readFileSync(resolve(HR_ROOT, "attendance/page.tsx"), "utf8");
    const timesheets = readFileSync(resolve(HR_ROOT, "timesheets/page.tsx"), "utf8");
    const leave = readFileSync(resolve(HR_ROOT, "leave/page.tsx"), "utf8");

    expect(attendance).toContain('data-testid="attendance-status-card"');
    expect(attendance).toContain('data-testid="attendance-today-card"');
    expect(attendance).toContain('data-testid="attendance-history-panel"');

    expect(timesheets).toContain('data-testid="timesheet-draft-card"');
    expect(timesheets).toContain('data-testid="timesheet-submitted-card"');
    expect(timesheets).toContain('data-testid="timesheet-period-panel"');

    expect(leave).toContain('data-testid="leave-balance-card"');
    expect(leave).toContain('data-testid="leave-pending-card"');
    expect(leave).toContain('data-testid="leave-lifecycle-panel"');
  });

  it("makes manager and HR screens dashboards and queues rather than naked tables", () => {
    const required = [
      ["team-attendance/page.tsx", "team-attendance-dashboard"],
      ["team-timesheets/page.tsx", "team-timesheet-queue"],
      ["leave/approvals/page.tsx", "leave-approval-queue"],
      ["schedules/page.tsx", "schedule-operations-panel"],
      ["attendance/admin/page.tsx", "attendance-admin-dashboard"],
      ["leave/policies/page.tsx", "leave-policy-operations"],
      ["reports/attendance/page.tsx", "attendance-report-dashboard"],
    ] as const;

    for (const [relative, testId] of required) {
      const source = readFileSync(resolve(HR_ROOT, relative), "utf8");
      expect(source, relative).toContain(`data-testid="${testId}"`);
    }
  });

  it("renders dense HR data as responsive mobile cards instead of relying on horizontal table scrolling", () => {
    const table = readFileSync(resolve(HR_ROOT, "components/hr-data-table.tsx"), "utf8");
    const styles = readFileSync(resolve(HR_ROOT, "components/hr-g2-visual.module.css"), "utf8");
    expect(table).toContain('data-testid="hr-mobile-records"');
    expect(table).toContain('data-testid="hr-mobile-record"');
    expect(styles).toContain(".mobileRecordList");
    expect(styles).toContain("@media (max-width: 47.99rem)");
  });
});
