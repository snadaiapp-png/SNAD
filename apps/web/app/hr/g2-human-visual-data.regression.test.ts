import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";
import { HRM_G2_LIFECYCLE_I18N_AR, HRM_G2_LIFECYCLE_I18N_EN } from "@/lib/i18n/locales/hrm-g2-lifecycle-i18n";
import { formatLocalizedDate } from "./hr-labels";

const HR_ROOT = resolve(__dirname);
const REPO_ROOT = resolve(HR_ROOT, "../../../..");
const WEB_ROOT = resolve(REPO_ROOT, "apps/web");
const VISUAL_FIXTURE_PATH = resolve(REPO_ROOT, "apps/sanad-platform/src/test/resources/sql/g2-human-visual-fixtures.sql");
const PREVIEW_WORKFLOW_PATH = resolve(REPO_ROOT, ".github/workflows/hrm-human-preview.yml");
const VISUAL_SPEC_PATH = resolve(WEB_ROOT, "e2e/g2-visual.spec.ts");
const HR_DATA_TABLE_PATH = resolve(HR_ROOT, "components/hr-data-table.tsx");
const BILINGUAL_G2_DATE_SURFACES = [
  "attendance/page.tsx",
  "team-attendance/page.tsx",
  "attendance/admin/page.tsx",
  "timesheets/page.tsx",
  "team-timesheets/page.tsx",
  "leave/page.tsx",
  "leave/approvals/page.tsx",
];
const LEAVE_LIFECYCLE_STATES = [
  "DRAFT",
  "SUBMITTED",
  "PENDING_MANAGER",
  "PENDING_HR",
  "APPROVED",
  "REJECTED",
  "WITHDRAWN",
  "CANCELLED",
] as const;

describe("G2 human visual acceptance data contract", () => {
  it("uses preview-only representative product data instead of certifying empty-only G2 screens", () => {
    const fixture = readFileSync(VISUAL_FIXTURE_PATH, "utf8");
    for (const table of [
      "hr_attendance_records",
      "hr_timesheets",
      "hr_leave_requests",
      "hr_work_schedules",
    ]) {
      expect(fixture, table).toContain(`INSERT INTO ${table}`);
    }
    expect(fixture).toContain("G2_VISUAL_FIXTURE");
  });

  it("requires representative product rows during HRM Human Preview only", () => {
    const workflow = readFileSync(PREVIEW_WORKFLOW_PATH, "utf8");
    const visualSpec = readFileSync(VISUAL_SPEC_PATH, "utf8");
    const dataTable = readFileSync(HR_DATA_TABLE_PATH, "utf8");

    expect(workflow).toContain("g2-human-visual-fixtures.sql");
    expect(workflow).toContain('G2_VISUAL_REQUIRE_PRODUCT_DATA: "true"');
    expect(visualSpec).toContain('process.env.G2_VISUAL_REQUIRE_PRODUCT_DATA === "true"');
    expect(visualSpec).toContain('tr[data-testid="hr-data-row"]');
    expect(dataTable).toContain('data-testid="hr-data-row"');
  });

  it("fails visual closure when a raw HRM translation key is visible", () => {
    const visualSpec = readFileSync(VISUAL_SPEC_PATH, "utf8");
    expect(visualSpec).toContain('not.toContainText("hrm.")');
  });

  it("localizes every canonical leave lifecycle state in Arabic and English", () => {
    for (const state of LEAVE_LIFECYCLE_STATES) {
      const key = `hrm.leave.state.${state}`;
      expect(HRM_G2_LIFECYCLE_I18N_AR[key], `Arabic ${key}`).toBeTruthy();
      expect(HRM_G2_LIFECYCLE_I18N_EN[key], `English ${key}`).toBeTruthy();
      expect(HRM_G2_LIFECYCLE_I18N_AR[key]).not.toBe(key);
      expect(HRM_G2_LIFECYCLE_I18N_EN[key]).not.toBe(key);
    }
  });

  it("formats G2 dates in the selected UI locale", () => {
    const english = formatLocalizedDate("2026-09-26", "en");
    const arabic = formatLocalizedDate("2026-09-26", "ar");
    expect(english).toMatch(/Sep/i);
    expect(english).not.toMatch(/[\u0600-\u06FF]/);
    expect(arabic).toMatch(/[\u0600-\u06FF]/);
  });

  it("wires every bilingual G2 date surface to the selected locale", () => {
    for (const relativePath of BILINGUAL_G2_DATE_SURFACES) {
      const source = readFileSync(resolve(HR_ROOT, relativePath), "utf8");
      expect(source, relativePath).toContain("formatLocalizedDate");
      expect(source, relativePath).not.toContain("formatArabicDate");
      expect(source, relativePath).toMatch(/useI18n\(\)[\s\S]*locale/);
    }
  });
});
