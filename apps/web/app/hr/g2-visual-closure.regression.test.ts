import { existsSync, readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";
import { HRM_G2_I18N_AR, HRM_G2_I18N_EN } from "@/lib/i18n/locales/hrm-g2-i18n";

const HR_ROOT = resolve(__dirname);
const WEB_ROOT = resolve(HR_ROOT, "../..");
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
    for (const legacy of ['header: "Period"','header: "State"','header: "Comment"','header: "Actions"','>Submit<','>My Timesheets<','caption="My Timesheets"','emptyTitle="No timesheets"','setNotice("Timesheet submitted")']) {
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
    const files = ["team-attendance/page.tsx","team-timesheets/page.tsx","leave/approvals/page.tsx","reports/attendance/page.tsx"] as const;
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

describe("G2 HR administration product surfaces", () => {
  it("provides deterministic ready-state selectors for all HR administration routes", () => {
    const schedules = readFileSync(resolve(HR_ROOT, "schedules/page.tsx"), "utf8");
    const attendanceAdmin = readFileSync(resolve(HR_ROOT, "attendance/admin/page.tsx"), "utf8");
    const leavePolicies = readFileSync(resolve(HR_ROOT, "leave/policies/page.tsx"), "utf8");
    expect(schedules).toContain('data-testid="schedules-ready"');
    expect(attendanceAdmin).toContain('data-testid="attendance-admin-ready"');
    expect(leavePolicies).toContain('data-testid="leave-policies-ready"');
  });

  it("removes legacy HR administration hard-coded copy in favor of G2 i18n", () => {
    const checks = [
      ["schedules/page.tsx", /(?:>Work Schedules<|>Create Schedule<|header: "Code"|header: "Name"|header: "Shift"|header: "Break"|header: "Expected")/],
      ["attendance/admin/page.tsx", /(?:>Attendance Administration<|Attendance correction is unavailable|caption="Attendance Records \(Admin\)"|emptyTitle="No attendance records")/],
      ["leave/policies/page.tsx", /(?:>Leave Policies<|header: "Code"|header: "Name \(AR\)"|header: "Name \(EN\)"|header: "Paid"|header: "Attachment"|header: "Default Days"|configure via policy)/],
    ] as const;
    for (const [relative, forbidden] of checks) {
      const source = readFileSync(resolve(HR_ROOT, relative), "utf8");
      expect(source, relative).not.toMatch(forbidden);
    }
    expect(readFileSync(resolve(HR_ROOT, "schedules/page.tsx"), "utf8")).toContain('t("hrm.schedules.title")');
    expect(readFileSync(resolve(HR_ROOT, "attendance/admin/page.tsx"), "utf8")).toContain('t("hrm.attendanceAdmin.title")');
    expect(readFileSync(resolve(HR_ROOT, "leave/policies/page.tsx"), "utf8")).toContain('t("hrm.leavePolicies.title")');
  });

  it("keeps HR administration contracts fail-closed and country-neutral", () => {
    const admin = readFileSync(resolve(HR_ROOT, "attendance/admin/page.tsx"), "utf8");
    const policies = readFileSync(resolve(HR_ROOT, "leave/policies/page.tsx"), "utf8");
    expect(admin).not.toContain("clockIn(");
    expect(admin).not.toContain("clockOut(");
    expect(policies).not.toMatch(/\b(?:21|30|70)\b/);
  });
});

describe("G2 role-device visual evidence harness", () => {
  it("defines a dedicated fail-closed visual suite for desktop and mobile", () => {
    const configPath = resolve(WEB_ROOT, "playwright-g2-visual.config.ts");
    const specPath = resolve(WEB_ROOT, "e2e/g2-visual.spec.ts");
    const helperPath = resolve(WEB_ROOT, "e2e/g2-visual-helpers.ts");
    expect(existsSync(configPath)).toBe(true);
    expect(existsSync(specPath)).toBe(true);
    expect(existsSync(helperPath)).toBe(true);
    if (!existsSync(configPath) || !existsSync(specPath) || !existsSync(helperPath)) return;
    const config = readFileSync(configPath, "utf8");
    const spec = readFileSync(specPath, "utf8");
    const helper = readFileSync(helperPath, "utf8");
    expect(config).toContain('name: "g2-visual-desktop"');
    expect(config).toContain('name: "g2-visual-mobile"');
    expect(config).toContain('screenshot: "on"');
    expect(config).toContain("375");
    for (const role of ['"employee"','"manager"','"hr"']) expect(spec).toContain(role);
    expect(helper).toContain("process.env.GITHUB_HEAD_SHA ??");
    expect(helper).toContain("manifest.ndjson");
    expect(helper).toContain("document.documentElement.scrollWidth");
  });
});