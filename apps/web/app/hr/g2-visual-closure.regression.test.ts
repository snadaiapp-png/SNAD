import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";
import { HRM_G2_I18N_AR, HRM_G2_I18N_EN } from "@/lib/i18n/locales/hrm-g2-i18n";

const HR_ROOT = resolve(__dirname);
const REQUIRED_FOUNDATION_KEYS = [
  "hrm.g2.landing.myWorkday",
  "hrm.g2.landing.myTeam",
  "hrm.g2.landing.hrOperations",
  "hrm.timesheets.title",
  "hrm.timesheets.team.title",
  "hrm.teamAttendance.title",
  "hrm.leaveApprovals.title",
  "hrm.schedules.title",
  "hrm.attendanceAdmin.title",
  "hrm.leavePolicies.title",
  "hrm.attendanceReport.title",
] as const;

describe("G2 visual closure foundation", () => {
  it("keeps route-scoped G2 dictionaries in exact parity", () => {
    expect(Object.keys(HRM_G2_I18N_AR).sort()).toEqual(Object.keys(HRM_G2_I18N_EN).sort());
    for (const key of REQUIRED_FOUNDATION_KEYS) {
      expect(HRM_G2_I18N_AR[key]).toBeTruthy();
      expect(HRM_G2_I18N_EN[key]).toBeTruthy();
    }
  });

  it("inherits direction instead of forcing all HR tables to RTL", () => {
    const table = readFileSync(resolve(HR_ROOT, "components/hr-data-table.tsx"), "utf8");
    expect(table).not.toContain('dir="rtl"');
  });
});

describe("G2 employee product surfaces", () => {
  it("keeps stable ready/action selectors used by authenticated acceptance", () => {
    const attendance = readFileSync(resolve(HR_ROOT, "attendance/page.tsx"), "utf8");
    const timesheets = readFileSync(resolve(HR_ROOT, "timesheets/page.tsx"), "utf8");
    const leave = readFileSync(resolve(HR_ROOT, "leave/page.tsx"), "utf8");

    expect(attendance).toContain('data-testid="attendance-ready"');
    expect(attendance).toContain('data-testid="attendance-clock-in"');
    expect(attendance).toContain('data-testid="attendance-clock-out"');
    expect(timesheets).toContain('data-testid="timesheets-ready"');
    expect(leave).toContain('data-testid="leave-ready"');
    expect(leave).toContain('data-testid="request-leave-toggle"');
    expect(leave).toContain('data-testid="leave-request-form"');
    expect(leave).toContain('data-testid="leave-submit"');
  });

  it("removes hard-coded employee Timesheet UI copy in favor of route-scoped i18n", () => {
    const timesheets = readFileSync(resolve(HR_ROOT, "timesheets/page.tsx"), "utf8");
    for (const legacy of [
      'header: "Period"',
      'header: "State"',
      'header: "Comment"',
      'header: "Actions"',
      '>Submit<',
      '>My Timesheets<',
      'caption="My Timesheets"',
      'emptyTitle="No timesheets"',
      'setNotice("Timesheet submitted")',
    ]) {
      expect(timesheets, legacy).not.toContain(legacy);
    }
    expect(timesheets).toContain('t("hrm.timesheets.title")');
    expect(timesheets).toContain('t("hrm.timesheets.submit")');
  });

  it("formats attendance clock times with the active locale rather than forcing Arabic", () => {
    const attendance = readFileSync(resolve(HR_ROOT, "attendance/page.tsx"), "utf8");
    expect(attendance).toContain('locale === "ar" ? "ar-SA" : "en-US"');
    expect(attendance).not.toContain('toLocaleTimeString("ar"');
  });
});

describe("G2 manager/team product surfaces", () => {
  it("provides deterministic ready-state selectors for every manager visual route", () => {
    const teamAttendance = readFileSync(resolve(HR_ROOT, "team-attendance/page.tsx"), "utf8");
    const teamTimesheets = readFileSync(resolve(HR_ROOT, "team-timesheets/page.tsx"), "utf8");
    const approvals = readFileSync(resolve(HR_ROOT, "leave/approvals/page.tsx"), "utf8");
    const report = readFileSync(resolve(HR_ROOT, "reports/attendance/page.tsx"), "utf8");

    expect(teamAttendance).toContain('data-testid="team-attendance-ready"');
    expect(teamTimesheets).toContain('data-testid="team-timesheets-ready"');
    expect(approvals).toContain('data-testid="leave-approvals-ready"');
    expect(report).toContain('data-testid="attendance-report-ready"');
  });

  it("removes legacy manager hard-coded page copy in favor of G2 i18n", () => {
    const files = [
      "team-attendance/page.tsx",
      "team-timesheets/page.tsx",
      "leave/approvals/page.tsx",
      "reports/attendance/page.tsx",
    ] as const;
    const forbidden = /(?:>Team Attendance<|>Team Timesheets<|>Leave Approval Queue<|>Monthly Attendance Report<|caption="Pending Timesheet Approvals"|caption="Pending Leave Requests"|emptyTitle="No pending timesheets"|emptyTitle="No pending leave requests")/;
    for (const relative of files) {
      const source = readFileSync(resolve(HR_ROOT, relative), "utf8");
      expect(source, relative).not.toMatch(forbidden);
      expect(source, relative).toContain("translate={t}");
    }
  });

  it("preserves Workflow Y2 role separation selectors on leave approvals", () => {
    const approvals = readFileSync(resolve(HR_ROOT, "leave/approvals/page.tsx"), "utf8");
    expect(approvals).toContain('data-testid={`manager-approve-${r.id}`}');
    expect(approvals).toContain('data-testid={`manager-reject-${r.id}`}');
    expect(approvals).toContain('data-testid={`hr-approve-${r.id}`}');
    expect(approvals).toContain('data-testid={`hr-reject-${r.id}`}');
    expect(approvals).toContain('r.state === "PENDING_MANAGER" && canManagerApprove');
    expect(approvals).toContain('r.state === "PENDING_HR" && canHrApprove');
  });

  it("keeps report API scope backend-authoritative by role capability", () => {
    const report = readFileSync(resolve(HR_ROOT, "reports/attendance/page.tsx"), "utf8");
    expect(report).toContain("canAdmin");
    expect(report).toContain("hrG2Api.adminMonthlyAttendanceReport(year, month)");
    expect(report).toContain("hrG2Api.teamMonthlyAttendanceReport(year, month)");
  });
});
