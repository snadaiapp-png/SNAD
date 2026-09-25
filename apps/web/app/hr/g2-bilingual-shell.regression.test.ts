import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";

const HR_ROOT = resolve(__dirname);

describe("G2 bilingual HR shell", () => {
  it("localizes the shared HR workspace chrome in English as well as Arabic", () => {
    const workspace = readFileSync(resolve(HR_ROOT, "components/hr-workspace.tsx"), "utf8");
    expect(workspace).toContain('useI18n');
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
});