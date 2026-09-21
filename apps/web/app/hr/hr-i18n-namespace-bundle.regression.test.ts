// @vitest-environment jsdom

/**
 * HR I18n Namespace Bundle Regression Test
 * -----------------------------------------
 * Prevents the HRM G1 dictionary from being re-introduced into the global
 * ar.ts/en.ts dictionaries (which would push login/executive/control-plane
 * routes over the performance budget).
 *
 * Background: the G1-T11 initial implementation spread HRM_G1_I18N_AR/EN
 * into ar.ts/en.ts, causing the entire HRM G1 dictionary (105+ keys × 2
 * locales) to be bundled into EVERY route's initial JS. The fix moved
 * HRM keys to a route-scoped namespace loaded by HrI18nAugmenter only
 * within /hr/* routes (see app/hr/layout.tsx + app/hr/_client/hr-i18n-augmenter.tsx).
 *
 * This test fails if any contributor re-introduces the spread pattern.
 */

import { readFileSync, existsSync } from "fs";
import { resolve } from "path";
import { describe, expect, it } from "vitest";

const REPO_ROOT = resolve(__dirname, "../../../../");
const AR_PATH = resolve(REPO_ROOT, "apps/web/lib/i18n/locales/ar.ts");
const EN_PATH = resolve(REPO_ROOT, "apps/web/lib/i18n/locales/en.ts");
const HRM_G1_PATH = resolve(REPO_ROOT, "apps/web/lib/i18n/locales/hrm-g1-i18n.ts");
const AUGMENTER_PATH = resolve(REPO_ROOT, "apps/web/app/hr/_client/hr-i18n-augmenter.tsx");
const HR_LAYOUT_PATH = resolve(REPO_ROOT, "apps/web/app/hr/layout.tsx");

function readFile(path: string): string {
  expect(existsSync(path), `Expected file to exist: ${path}`).toBe(true);
  return readFileSync(path, "utf8");
}

describe("HR I18n Namespace Bundle Regression", () => {
  it("ar.ts does NOT spread HRM_G1_I18N_AR (route-scoped pattern only)", () => {
    const ar = readFile(AR_PATH);
    // The spread pattern would be: ...HRM_G1_I18N_AR
    expect(ar).not.toContain("...HRM_G1_I18N_AR");
    // Also reject the import line (no static import of HRM into ar.ts)
    expect(ar).not.toMatch(/import.*HRM_G1_I18N_AR/);
  });

  it("en.ts does NOT spread HRM_G1_I18N_EN (route-scoped pattern only)", () => {
    const en = readFile(EN_PATH);
    expect(en).not.toContain("...HRM_G1_I18N_EN");
    expect(en).not.toMatch(/import.*HRM_G1_I18N_EN/);
  });

  it("hrm-g1-i18n.ts still exists as a standalone module (HRM namespace source of truth)", () => {
    const hrm = readFile(HRM_G1_PATH);
    expect(hrm).toContain("HRM_G1_I18N_AR");
    expect(hrm).toContain("HRM_G1_I18N_EN");
  });

  it("HrI18nAugmenter statically imports HRM namespace + overrides I18nContext", () => {
    const augmenter = readFile(AUGMENTER_PATH);
    expect(augmenter).toContain("import { HRM_G1_I18N_AR, HRM_G1_I18N_EN }");
    expect(augmenter).toContain("I18nContext.Provider");
    expect(augmenter).toContain("useI18n");
  });

  it("app/hr/layout.tsx wraps all HR routes with HrI18nAugmenter", () => {
    const layout = readFile(HR_LAYOUT_PATH);
    expect(layout).toContain("HrI18nAugmenter");
  });

  it("HRM keys are namespaced under hrm.* (no collision with global keys)", () => {
    const hrm = readFile(HRM_G1_PATH);
    // Extract all keys from the HRM module — should all start with hrm.
    const keyMatches = hrm.matchAll(/^\s*"([^"]+)":/gm);
    const keys = Array.from(keyMatches, (m) => m[1]);
    expect(keys.length).toBeGreaterThan(0);
    for (const key of keys) {
      expect(
        key.startsWith("hrm."),
        `HRM key "${key}" is not namespaced under hrm.* — would collide with global keys`,
      ).toBe(true);
    }
  });
});
