import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";

const pageSource = readFileSync(resolve(__dirname, "page.tsx"), "utf8");
const productDictionary = readFileSync(
  resolve(__dirname, "../../lib/i18n/locales/hrm-g2-product-i18n.ts"),
  "utf8",
);

const LANDING_KEYS = [
  "hrm.g2.landing.summary",
  "hrm.g2.landing.activeEmployment",
  "hrm.g2.landing.onboarding",
  "hrm.g2.landing.onLeaveSuspended",
  "hrm.g2.landing.occupiedPosition",
  "hrm.g2.landing.vacantPosition",
  "hrm.g2.landing.pendingOverrides",
  "hrm.g2.landing.employeeRecords",
  "hrm.g2.landing.organizationStructure",
  "hrm.g2.landing.compliance",
] as const;

describe("G2 HR landing i18n contract", () => {
  it("uses route-scoped translation keys instead of hard-coded AR/EN branches", () => {
    expect(pageSource).not.toContain('locale === "ar"');
    for (const key of LANDING_KEYS) {
      expect(pageSource).toContain(`t("${key}")`);
    }
  });

  it("keeps every landing product key in both AR and EN dictionaries", () => {
    for (const key of LANDING_KEYS) {
      expect(productDictionary.split(`"${key}"`).length - 1).toBe(2);
    }
  });
});
