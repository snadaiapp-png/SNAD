import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";

const HR_ROOT = resolve(__dirname);
function page(path: string): string { return readFileSync(resolve(HR_ROOT, path), "utf8"); }
function expectProductSurface(source: string, marker: string) {
  expect(source).toContain("HrProductHeader");
  expect(source).toContain("HrKpiGrid");
  expect(source).toContain("HrKpiCard");
  expect(source).toContain("HrActionBar");
  expect(source).toContain("HrOperationalPanel");
  expect(source).toContain("HrMobileRecordList");
  expect(source).toContain(`data-testid=\"${marker}\"`);
  expect(source).not.toContain("/executive");
  expect(source).not.toContain("CONTROL_PLANE");
}

describe("G2 HR Administration product surfaces", () => {
  it("renders Work Schedules as an administration surface with schedule metrics and create action", () => {
    const source = page("schedules/page.tsx");
    expectProductSurface(source, "schedules-product-surface");
    expect(source).toContain("schedules-total-count");
    expect(source).toContain("schedules-active-count");
    expect(source).toContain("schedules-overnight-count");
    expect(source).toContain("schedules-create-action");
    expect(source).toContain("schedules-records-panel");
    expect(source).toContain('data-testid="schedules-ready"');
  });

  it("renders Attendance Administration with operational exceptions and correction availability", () => {
    const source = page("attendance/admin/page.tsx");
    expectProductSurface(source, "attendance-admin-product-surface");
    expect(source).toContain("attendance-admin-open-count");
    expect(source).toContain("attendance-admin-completed-count");
    expect(source).toContain("attendance-admin-missing-punch-count");
    expect(source).toContain("attendance-admin-correction-state");
    expect(source).toContain("attendance-admin-records-panel");
    expect(source).toContain('data-testid="attendance-admin-ready"');
  });

  it("renders Leave Policies as a policy administration surface with policy characteristics", () => {
    const source = page("leave/policies/page.tsx");
    expectProductSurface(source, "leave-policies-product-surface");
    expect(source).toContain("leave-policies-total-count");
    expect(source).toContain("leave-policies-paid-count");
    expect(source).toContain("leave-policies-attachment-count");
    expect(source).toContain("leave-policies-admin-state");
    expect(source).toContain("leave-policies-records-panel");
    expect(source).toContain('data-testid="leave-policies-ready"');
  });

  it("keeps HR approval and monthly report product surfaces role-scoped", () => {
    const approvals = page("leave/approvals/page.tsx");
    const report = page("reports/attendance/page.tsx");
    expect(approvals).toContain("leave-approvals-product-surface");
    expect(approvals).toContain("canHrApprove");
    expect(approvals).toContain('r.state === "PENDING_HR" && canHrApprove');
    expect(report).toContain("attendance-report-product-surface");
    expect(report).toContain("canAdmin");
    expect(report).toContain("adminMonthlyAttendanceReport");
  });
});
