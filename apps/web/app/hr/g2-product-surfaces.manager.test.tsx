import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";

const HR_ROOT = resolve(__dirname);

function page(path: string): string {
  return readFileSync(resolve(HR_ROOT, path), "utf8");
}

function expectProductSurface(source: string, marker: string) {
  expect(source).toContain("HrProductHeader");
  expect(source).toContain("HrKpiGrid");
  expect(source).toContain("HrKpiCard");
  expect(source).toContain("HrOperationalPanel");
  expect(source).toContain("HrMobileRecordList");
  expect(source).toContain(`data-testid=\"${marker}\"`);
}

describe("G2 Manager / Team product surfaces", () => {
  it("renders Team Attendance as a team-status dashboard", () => {
    const source = page("team-attendance/page.tsx");
    expectProductSurface(source, "team-attendance-product-surface");
    expect(source).toContain("team-attendance-open-count");
    expect(source).toContain("team-attendance-completed-count");
    expect(source).toContain("team-attendance-worked-summary");
    expect(source).toContain("team-attendance-records-panel");
  });

  it("renders Team Timesheets as an approval queue with explicit actions", () => {
    const source = page("team-timesheets/page.tsx");
    expectProductSurface(source, "team-timesheets-product-surface");
    expect(source).toContain("team-timesheets-pending-count");
    expect(source).toContain("team-timesheets-queue-panel");
    expect(source).toContain("team-timesheets-approve-action");
    expect(source).toContain("team-timesheets-reject-action");
  });

  it("renders Leave Approvals with lifecycle queue summaries and role-scoped authority", () => {
    const source = page("leave/approvals/page.tsx");
    expectProductSurface(source, "leave-approvals-product-surface");
    expect(source).toContain("leave-approvals-manager-count");
    expect(source).toContain("leave-approvals-hr-count");
    expect(source).toContain("leave-approvals-queue-panel");
    expect(source).toContain('r.state === "PENDING_MANAGER" && canManagerApprove');
    expect(source).toContain('r.state === "PENDING_HR" && canHrApprove');
  });

  it("renders Attendance Report with period/team metrics and filters", () => {
    const source = page("reports/attendance/page.tsx");
    expectProductSurface(source, "attendance-report-product-surface");
    expect(source).toContain("attendance-report-people-count");
    expect(source).toContain("attendance-report-absent-count");
    expect(source).toContain("attendance-report-late-count");
    expect(source).toContain("attendance-report-filter-bar");
    expect(source).toContain("attendance-report-results-panel");
  });
});
