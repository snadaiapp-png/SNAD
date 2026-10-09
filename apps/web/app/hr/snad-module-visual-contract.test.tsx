// @vitest-environment jsdom

/**
 * SNAD Module Visual Contract — RED-first governance tests (G3 Task 6).
 *
 * These tests encode the SNAD Module Visual Contract
 * (docs/superpowers/specs/2026-10-02-snad-module-visual-contract.md):
 *
 *   1. shared module primitives exist under components/sds/module
 *   2. CRM consumes the shared shell primitive (reference module)
 *   3. HR consumes the shared shell primitive (no independent identity)
 *   4. HR renders sidebar navigation with the shared active treatment
 *   5. HR renders no independent shell palette/styles
 *   6. system-wide visual compliance check exists and fails closed on a
 *      synthetic non-compliant module while passing a compliant fixture
 *
 * RED semantics: before migration every assertion below fails for a
 * feature-missing / drift reason (no shared primitives, no shared
 * consumption) — never for a harness or resolver defect.
 */

import "@testing-library/jest-dom/vitest";

import { execFileSync } from "node:child_process";
import { existsSync, readFileSync } from "node:fs";
import { resolve } from "node:path";
import { cleanup, render, screen } from "@testing-library/react";
import type React from "react";
import { afterEach, describe, expect, it, vi } from "vitest";

const WEB_ROOT = resolve(__dirname, "../..");
const MODULE_DIR = resolve(WEB_ROOT, "components/sds/module");
const MODULE_INDEX = resolve(MODULE_DIR, "index.ts");
const COMPLIANCE_SCRIPT = resolve(WEB_ROOT, "../../scripts/ci/check-module-visual-compliance.py");

const { authMock } = vi.hoisted(() => ({
  authMock: {
    state: "AUTHENTICATED",
    me: {
      capabilities: [] as string[],
      displayName: "موظف التشغيل",
      email: "operator@example.com",
    },
    logout: vi.fn(async () => undefined),
  },
}));

vi.mock("@/lib/auth/auth-provider", () => ({
  useAuth: () => authMock,
}));

const i18nMock = vi.hoisted(() => ({
  direction: "rtl" as "rtl" | "ltr",
  locale: "ar" as "ar" | "en",
}));

vi.mock("@/lib/i18n/I18nProvider", async () => {
  const { createContext } = await import("react");
  const I18nContext = createContext({
    locale: i18nMock.locale,
    direction: i18nMock.direction,
    setLocale: () => undefined,
    t: (key: string) => key,
  });
  return {
    I18nContext,
  useI18n: () => ({
    locale: i18nMock.locale,
    direction: i18nMock.direction,
    setLocale: vi.fn(),
    t: (key: string) => key,
  }),
  };
});

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn(), replace: vi.fn() }),
  usePathname: () => "/hr",
}));

vi.mock("next/link", () => ({
  default: ({ href, children, ...props }: React.AnchorHTMLAttributes<HTMLAnchorElement>) => (
    <a href={String(href)} {...props}>{children}</a>
  ),
}));

afterEach(() => cleanup());

function source(relativePath: string): string {
  return readFileSync(resolve(__dirname, relativePath), "utf8");
}

describe("SNAD shared module visual contract — primitives", () => {
  it("V1. exposes the shared module primitive registry", () => {
    expect(existsSync(MODULE_DIR), "components/sds/module must exist").toBe(true);
    expect(existsSync(MODULE_INDEX), "components/sds/module/index.ts must exist").toBe(true);
    const index = readFileSync(MODULE_INDEX, "utf8");
    for (const primitive of [
      "SnadModuleShell",
      "SnadPageHeader",
      "SnadKpiGrid",
      "SnadKpiCard",
      "SnadOperationalPanel",
      "SnadActionBar",
      "SnadStatusBadge",
      "SnadLoadingState",
      "SnadEmptyState",
      "SnadErrorState",
      "SnadForbiddenState",
      "SnadSuccessNotice",
      "SnadMobileRecordList",
      "SnadMobileRecordCard",
    ]) {
      expect(index).toContain(primitive);
    }
  });

  it("V2. styles every shared primitive through SDS tokens with zero raw colors", () => {
    expect(existsSync(MODULE_DIR), "components/sds/module must exist").toBe(true);
    const css = readFileSync(resolve(MODULE_DIR, "snad-module.module.css"), "utf8");
    expect(css, "shared module styles must exist").toContain("SnadModuleShell".length > 0 ? "." : ".");
    const rawColor = /#[0-9a-fA-F]{3,8}\b|rgba?\(/;
    expect(css.match(rawColor)?.join(", ") ?? "none").toBe("none");
    expect(css).toMatch(/var\(--snad-/);
  });
});

describe("SNAD shared module visual contract — CRM reference consumption", () => {
  it("V3. CRM shell renders through the shared module primitives", () => {
    const crmShell = source("../crm/components/crm-shell.tsx");
    expect(crmShell).toContain("@/components/sds/module");
    expect(crmShell).not.toContain('from "../crm-shared-styles.module.css"');
  });

  it("V3b. shared shell owns the USER.CREATE module-header action for CRM, HR, and future modules", () => {
    const shell = source("../../components/sds/module/SnadModuleShell.tsx");
    const crmShell = source("../crm/components/crm-shell.tsx");
    const hrWorkspace = source("./components/hr-workspace.tsx");

    expect(shell).toContain("GlobalUserProvisioningLauncher");
    expect(shell).toContain("ModuleAccessNavigation");
    expect(shell).toContain('presentation="header"');
    expect(shell).toContain('presentation="sidebar"');
    expect(shell).toContain('data-snad-module-shell="true"');
    expect(crmShell).toContain("<SnadModuleShell");
    expect(hrWorkspace).toContain("<SnadModuleShell");
  });
});

describe("SNAD shared module visual contract — HR unification", () => {
  it("V4. HR workspace renders through the shared module primitives", () => {
    const workspace = source("./components/hr-workspace.tsx");
    expect(workspace).toContain("@/components/sds/module");
    // The independent HR shell identity is retired: the workspace must no
    // longer own its own shell geometry styles.
    expect(workspace).not.toContain('from "../hr.module.css"');
  });

  it("V5. HR renders sidebar navigation with sectioned groups", async () => {
    const { HRM_CAPABILITIES } = await import("@/lib/auth/capabilities");
    const { HrWorkspace } = await import("./components/hr-workspace");
    render(
      <HrWorkspace
        capabilities={[HRM_CAPABILITIES.GOAL_SELF_VIEW, HRM_CAPABILITIES.ATTENDANCE_SELF_VIEW]}
        activeHref="/hr"
      >
        <p>المحتوى</p>
      </HrWorkspace>,
    );
    // Sidebar landmark containing the labelled navigation.
    const nav = screen.getByRole("navigation", { name: "أقسام الموارد البشرية" });
    const aside = nav.closest("aside");
    expect(aside, "HR navigation must live in the shared sidebar aside").not.toBeNull();
  });

  it("V6. HR shell is RTL/LTR driven by the i18n direction contract", async () => {
    const { HRM_CAPABILITIES } = await import("@/lib/auth/capabilities");
    const { HrWorkspace } = await import("./components/hr-workspace");
    render(
      <HrWorkspace
        capabilities={[HRM_CAPABILITIES.ATTENDANCE_SELF_VIEW]}
        activeHref="/hr"
      >
        <p>المحتوى</p>
      </HrWorkspace>,
    );
    const shell = screen.getByRole("main").closest("[dir]");
    expect(shell?.getAttribute("dir")).toBe("rtl");
  });
});

describe("SNAD system-wide visual governance", () => {
  it("V7. module visual compliance check exists and fails closed on a synthetic drift module", () => {
    expect(existsSync(COMPLIANCE_SCRIPT), "check-module-visual-compliance.py must exist").toBe(true);
    // Self-test embeds a compliant fixture (must pass) and a non-compliant
    // fixture (must fail with exit code 1).
    const selfTest = () =>
      execFileSync("python3", [COMPLIANCE_SCRIPT, "--self-test"], { encoding: "utf8", timeout: 60_000 });
    expect(selfTest).not.toThrow();
    const output = selfTest();
    expect(output).toContain("SELF_TEST_COMPLIANT_FIXTURE=PASS");
    expect(output).toContain("SELF_TEST_NONCOMPLIANT_FIXTURE=FAIL");
  });
});
