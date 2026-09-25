import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";

const HR_ROOT = resolve(__dirname);

describe("G2 bilingual HR shell", () => {
  it("localizes the shared HR workspace chrome while preserving provider-free legacy callers", () => {
    const workspace = readFileSync(resolve(HR_ROOT, "components/hr-workspace.tsx"), "utf8");
    expect(workspace).not.toContain('useI18n');
    expect(workspace).toContain('if (!translate) return "ar"');
    expect(workspace).toContain('translate("hrm.g2.landing.myWorkday") === "My Workday"');
    expect(workspace).toContain('Human Resources Workspace');
    expect(workspace).toContain('Home');
    expect(workspace).toContain('Execution Board');
    expect(workspace).toContain('aria-label={locale === "ar" ? "أقسام الموارد البشرية" : "Human resources sections"}');
  });

  it("does not leave the HR landing summary and foundation links Arabic-only", () => {
    const page = readFileSync(resolve(HR_ROOT, "page.tsx"), "utf8");
    expect(page).toContain('useI18n');
    expect(page).toContain('Active employment');
    expect(page).toContain('Onboarding');
    expect(page).toContain('On leave / suspended');
    expect(page).toContain('Employee records');
    expect(page).toContain('Organization structure');
    expect(page).toContain('Compliance');
    expect(page).toContain('translate={t}');
  });

  it("propagates the route translator into every HR-admin G2 workspace shell", () => {
    for (const relative of [
      "schedules/page.tsx",
      "attendance/admin/page.tsx",
      "leave/policies/page.tsx",
    ] as const) {
      const source = readFileSync(resolve(HR_ROOT, relative), "utf8");
      expect(source, relative).toContain('translate={t}');
    }
  });
});