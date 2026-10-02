import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";

const HR_ROOT = resolve(__dirname);
const productDictionary = readFileSync(
  resolve(HR_ROOT, "../../lib/i18n/locales/hrm-g2-product-i18n.ts"),
  "utf8",
);

describe("G2 bilingual HR shell", () => {
  it("renders through the shared SNAD module shell while preserving Arabic-first legacy callers", () => {
    const workspace = readFileSync(resolve(HR_ROOT, "components/hr-workspace.tsx"), "utf8");

    // G3 Task 6 unification: the workspace renders through the shared module
    // shell primitive and consumes the i18n provider for direction + language
    // toggle (shared header contract) — while label resolution keeps the
    // translator prop first and Arabic defaults for provider-free callers.
    expect(workspace).toContain("@/components/sds/module");
    expect(workspace).toContain("I18nContext");
    expect(workspace).toContain("translate?: (messageId: string) => string");
    expect(workspace).toContain('translate ? translate(messageId) : ARABIC_DEFAULTS[messageId] ?? messageId');
    expect(workspace).toContain('generic("hrm.g2.workspace.title")');
    expect(workspace).toContain('generic("hrm.g2.workspace.subtitle")');
    expect(workspace).toContain('generic("hrm.g2.workspace.navLabel")');
    expect(workspace).toContain('translate ? translate(item.labelMessageId) : item.label');
    expect(workspace).not.toContain("labelEn");
    expect(workspace).not.toContain("workspaceLocale");
    // The only permitted locale comparison is the canonical language-toggle
    // flip (runtime behavior, not content branching).
    expect(workspace.split('locale === "ar"').length - 1).toBe(1);
    expect(workspace).toContain('setLocale(locale === "ar" ? "en" : "ar")');

    expect(productDictionary).toContain('"hrm.g2.workspace.title": "Human Resources Workspace"');
    expect(productDictionary).toContain('"hrm.g2.workspace.nav.home": "Home"');
    expect(productDictionary).toContain('"hrm.g2.workspace.nav.execution": "Execution Board"');
  });

  it("localizes the HR landing summary and foundation links through the product dictionary", () => {
    const page = readFileSync(resolve(HR_ROOT, "page.tsx"), "utf8");

    expect(page).toContain("useI18n");
    expect(page).toContain('t("hrm.g2.landing.summary")');
    expect(page).toContain('t("hrm.g2.landing.activeEmployment")');
    expect(page).toContain('t("hrm.g2.landing.onboarding")');
    expect(page).toContain('t("hrm.g2.landing.onLeaveSuspended")');
    expect(page).toContain('labelMessageId: "hrm.g2.landing.employeeRecords"');
    expect(page).toContain('labelMessageId: "hrm.g2.landing.organizationStructure"');
    expect(page).toContain('labelMessageId: "hrm.g2.landing.compliance"');
    expect(page).toContain("t(link.labelMessageId)");
    expect(page).toContain("translate={t}");

    expect(productDictionary).toContain('"hrm.g2.landing.activeEmployment": "Active employment"');
    expect(productDictionary).toContain('"hrm.g2.landing.employeeRecords": "Employee records"');
    expect(productDictionary).toContain('"hrm.g2.landing.organizationStructure": "Organization structure"');
    expect(productDictionary).toContain('"hrm.g2.landing.compliance": "Compliance"');
  });

  it("propagates the route translator into every HR-admin G2 workspace shell", () => {
    for (const relative of [
      "schedules/page.tsx",
      "attendance/admin/page.tsx",
      "leave/policies/page.tsx",
    ] as const) {
      const source = readFileSync(resolve(HR_ROOT, relative), "utf8");
      expect(source, relative).toContain("translate={t}");
    }
  });
});
