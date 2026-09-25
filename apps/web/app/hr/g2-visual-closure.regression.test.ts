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
