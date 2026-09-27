import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";

const pageSource = readFileSync(resolve(__dirname, "page.tsx"), "utf8");
const workspaceSource = readFileSync(resolve(__dirname, "components/hr-workspace.tsx"), "utf8");
const productDictionary = readFileSync(
  resolve(__dirname, "../../lib/i18n/locales/hrm-g2-product-i18n.ts"),
  "utf8",
);

const DIRECT_LANDING_KEYS = [
  "hrm.g2.landing.summary",
  "hrm.g2.landing.activeEmployment",
  "hrm.g2.landing.onboarding",
  "hrm.g2.landing.onLeaveSuspended",
  "hrm.g2.landing.occupiedPosition",
  "hrm.g2.landing.vacantPosition",
  "hrm.g2.landing.pendingOverrides",
] as const;

const FOUNDATION_LANDING_KEYS = [
  "hrm.g2.landing.employeeRecords",
  "hrm.g2.landing.organizationStructure",
  "hrm.g2.landing.compliance",
] as const;

const LANDING_KEYS = [...DIRECT_LANDING_KEYS, ...FOUNDATION_LANDING_KEYS] as const;

const WORKSPACE_KEYS = [
  "hrm.g2.workspace.title",
  "hrm.g2.workspace.subtitle",
  "hrm.g2.workspace.navLabel",
  "hrm.g2.workspace.nav.home",
  "hrm.g2.workspace.nav.employees",
  "hrm.g2.workspace.nav.organizationStructure",
  "hrm.g2.workspace.nav.jobs",
  "hrm.g2.workspace.nav.positions",
  "hrm.g2.workspace.nav.assignments",
  "hrm.g2.workspace.nav.compliance",
  "hrm.g2.workspace.nav.recruitment",
  "hrm.g2.workspace.nav.onboarding",
  "hrm.g2.workspace.nav.execution",
] as const;

const REQUIRED_KEYS = [...LANDING_KEYS, ...WORKSPACE_KEYS] as const;

describe("G2 HR landing and workspace i18n contract", () => {
  it("uses route-scoped translation keys instead of hard-coded AR/EN branches", () => {
    expect(pageSource).not.toContain('locale === "ar"');
    for (const key of DIRECT_LANDING_KEYS) {
      expect(pageSource).toContain(`t("${key}")`);
    }
    for (const key of FOUNDATION_LANDING_KEYS) {
      expect(pageSource).toContain(`labelMessageId: "${key}"`);
    }
    expect(pageSource).toContain("t(link.labelMessageId)");

    expect(workspaceSource).not.toContain("labelEn");
    expect(workspaceSource).not.toContain("workspaceLocale");
    expect(workspaceSource).not.toContain('locale === "ar"');
    for (const key of WORKSPACE_KEYS) {
      expect(workspaceSource).toContain(`"${key}"`);
    }
  });

  it("keeps every landing and workspace product key in both AR and EN dictionaries", () => {
    for (const key of REQUIRED_KEYS) {
      expect(productDictionary.split(`"${key}"`).length - 1).toBe(2);
    }
  });
});
