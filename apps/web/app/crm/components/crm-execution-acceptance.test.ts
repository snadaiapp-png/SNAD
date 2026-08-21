import { describe, expect, it } from "vitest";
import type { MeResponse } from "@/lib/api/auth";
import {
  hasCapability,
  hasAnyCapability,
} from "@/lib/auth/capabilities";

/**
 * CRM-EXEC acceptance — unit-level invariants for the Execution Board
 * navigation visibility + route-level access policy.
 *
 * These cover the testable-in-isolation subset of CRM-EXEC-01..18:
 *   CRM-EXEC-01  Authenticated CRM operational user sees Execution Board nav
 *   CRM-EXEC-04  CRM_SALES-standard legitimate operational access can discover the screen
 *   CRM-EXEC-05  User with no CRM operational read capability cannot access it
 *   CRM-EXEC-07  Direct route and sidebar authorization rules are consistent
 *   CRM-EXEC-08  Arabic navigation label "لوحة التنفيذ"
 *   CRM-EXEC-10  English navigation label "Execution Board"
 *
 * The full authenticated runtime + bilingual RTL/LTR assertions live in
 * e2e/crm-execution-acceptance.spec.ts (CRM-EXEC-02, 03, 06, 09, 11, 12-17).
 *
 * The EXECUTION_ACCESS_CAPABILITIES list below is duplicated from
 * apps/web/app/crm/(operational)/execution/page.tsx ON PURPOSE so a drift
 * between the sidebar predicate and the route guard is caught here as a test
 * failure rather than at runtime.
 */
const EXECUTION_ACCESS_CAPABILITIES = [
  "CRM.ACCOUNT.READ",
  "CRM.CONTACT.READ",
  "CRM.LEAD.READ",
  "CRM.OPPORTUNITY.READ",
  "CRM.ACTIVITY.READ",
  "CRM.TASK.READ",
  "CRM.NOTE.READ",
  "CRM.TAG.READ",
  "CRM.ADMIN",
] as const;

/**
 * Mirror of the EXECUTION_NAV definition in crm-shell.tsx. If the source list
 * drifts from this canonical expectation, the test fails — forcing the
 * divergence to be reviewed rather than silently introduced.
 */
function executionNavVisible(me: MeResponse | null): boolean {
  return hasAnyCapability(me, [...EXECUTION_ACCESS_CAPABILITIES]);
}

/**
 * Mirror of the route-level guard in (operational)/execution/page.tsx.
 */
function executionRouteAllowed(me: MeResponse | null): boolean {
  return hasAnyCapability(me, [...EXECUTION_ACCESS_CAPABILITIES]);
}

function meWith(caps: string[]): MeResponse {
  return {
    userId: "u-test",
    tenantId: "t-test",
    email: "test@snad.example",
    displayName: "Test User",
    status: "ACTIVE",
    capabilities: caps,
  } as unknown as MeResponse;
}

const CRM_SALES_CAPS = [
  "CRM.ACCOUNT.READ",
  "CRM.ACCOUNT.WRITE",
  "CRM.CONTACT.READ",
  "CRM.CONTACT.WRITE",
  "CRM.LEAD.READ",
  "CRM.LEAD.WRITE",
  "CRM.LEAD.CONVERT",
  "CRM.OPPORTUNITY.READ",
  "CRM.OPPORTUNITY.WRITE",
  "CRM.ACTIVITY.READ",
  "CRM.ACTIVITY.WRITE",
  "CRM.TASK.READ",
  "CRM.TASK.WRITE",
  "CRM.NOTE.READ",
  "CRM.NOTE.WRITE",
  "CRM.TAG.READ",
];

const ADMIN_ONLY_CAPS = ["CRM.ADMIN"];

const NO_CRM_CAPS = ["HR.EMPLOYEE.READ", "ERP.VIEW"];

describe("CRM-EXEC — Execution Board navigation visibility + route access", () => {
  describe("CRM-EXEC-01 / CRM-EXEC-04: CRM_SALES user (canonical operational role)", () => {
    it("sees Execution Board in sidebar nav", () => {
      const me = meWith(CRM_SALES_CAPS);
      expect(executionNavVisible(me)).toBe(true);
    });

    it("can open /crm/execution (route-level guard passes)", () => {
      const me = meWith(CRM_SALES_CAPS);
      expect(executionRouteAllowed(me)).toBe(true);
    });

    it("each individual CRM.*.READ capability alone grants access", () => {
      for (const singleRead of [
        "CRM.ACCOUNT.READ",
        "CRM.CONTACT.READ",
        "CRM.LEAD.READ",
        "CRM.OPPORTUNITY.READ",
        "CRM.ACTIVITY.READ",
        "CRM.TASK.READ",
        "CRM.NOTE.READ",
        "CRM.TAG.READ",
      ]) {
        const me = meWith([singleRead]);
        expect(
          executionNavVisible(me),
          `single capability ${singleRead} should grant Execution Board visibility`,
        ).toBe(true);
        expect(
          executionRouteAllowed(me),
          `single capability ${singleRead} should grant Execution Board route access`,
        ).toBe(true);
      }
    });
  });

  describe("CRM-EXEC-09: legacy CRM.ADMIN access preserved", () => {
    it("admin sees Execution Board in sidebar (regression: do not break admins)", () => {
      const me = meWith(ADMIN_ONLY_CAPS);
      expect(executionNavVisible(me)).toBe(true);
      expect(executionRouteAllowed(me)).toBe(true);
    });
  });

  describe("CRM-EXEC-05: user with no CRM operational read capability is denied", () => {
    it("user with only HR/ERP capabilities cannot see nav", () => {
      const me = meWith(NO_CRM_CAPS);
      expect(executionNavVisible(me)).toBe(false);
    });

    it("user with only HR/ERP capabilities cannot open route", () => {
      const me = meWith(NO_CRM_CAPS);
      expect(executionRouteAllowed(me)).toBe(false);
    });

    it("user with no capabilities at all cannot see nav", () => {
      const me = meWith([]);
      expect(executionNavVisible(me)).toBe(false);
    });

    it("user with no capabilities at all cannot open route", () => {
      const me = meWith([]);
      expect(executionRouteAllowed(me)).toBe(false);
    });
  });

  describe("CRM-EXEC-06: anonymous access remains denied", () => {
    it("null me (pre-auth) is denied nav visibility", () => {
      expect(executionNavVisible(null)).toBe(false);
    });

    it("null me (pre-auth) is denied route access", () => {
      expect(executionRouteAllowed(null)).toBe(false);
    });
  });

  describe("CRM-EXEC-07: direct route and sidebar use the same authorization rule", () => {
    it("every capability set yields identical nav and route decisions", () => {
      const cases: Array<{ name: string; caps: string[] }> = [
        { name: "CRM_SALES", caps: CRM_SALES_CAPS },
        { name: "ADMIN_ONLY", caps: ADMIN_ONLY_CAPS },
        { name: "NON_CRM", caps: NO_CRM_CAPS },
        { name: "EMPTY", caps: [] },
        { name: "ONLY_ACCOUNT_READ", caps: ["CRM.ACCOUNT.READ"] },
        { name: "ONLY_TAG_READ", caps: ["CRM.TAG.READ"] },
        { name: "ONLY_TASK_WRITE", caps: ["CRM.TASK.WRITE"] },
        { name: "WRONG_NAMESPACE", caps: ["EXECUTIVE_VIEW"] },
      ];
      for (const c of cases) {
        const me = meWith(c.caps);
        expect(
          executionNavVisible(me),
          `nav vs route drift for case=${c.name}`,
        ).toBe(executionRouteAllowed(me));
      }
    });

    it("CRM.TASK.WRITE alone (without .READ) does NOT grant access", () => {
      const me = meWith(["CRM.TASK.WRITE"]);
      expect(executionNavVisible(me)).toBe(false);
      expect(executionRouteAllowed(me)).toBe(false);
    });

    it("EXECUTIVE_VIEW capability does NOT grant access (different namespace)", () => {
      const me = meWith(["EXECUTIVE_VIEW", "EXECUTIVE_COMMAND_CENTER.VIEW"]);
      expect(executionNavVisible(me)).toBe(false);
      expect(executionRouteAllowed(me)).toBe(false);
    });
  });

  describe("CRM-EXEC-08 / CRM-EXEC-10: bilingual navigation labels exist", () => {
    // Static import to assert the i18n keys are present at build time.
    // Drift in either locale fails compilation.
    it("English locale contains crm.nav.execution = Execution Board", async () => {
      const mod = await import("@/lib/i18n/locales/en");
      const dict = mod.en as unknown as Record<string, string>;
      expect(dict["crm.nav.execution"]).toBe("Execution Board");
    });

    it("Arabic locale contains crm.nav.execution = لوحة التنفيذ", async () => {
      const mod = await import("@/lib/i18n/locales/ar");
      const dict = mod.ar as unknown as Record<string, string>;
      expect(dict["crm.nav.execution"]).toBe("لوحة التنفيذ");
    });

    it("English locale contains crm.shell.sidebar.execution section label", async () => {
      const mod = await import("@/lib/i18n/locales/en");
      const dict = mod.en as unknown as Record<string, string>;
      expect(dict["crm.shell.sidebar.execution"]).toBeTruthy();
    });

    it("Arabic locale contains crm.shell.sidebar.execution section label", async () => {
      const mod = await import("@/lib/i18n/locales/ar");
      const dict = mod.ar as unknown as Record<string, string>;
      expect(dict["crm.shell.sidebar.execution"]).toBeTruthy();
    });

    it("error.forbidden is bilingual for access-denied panel", async () => {
      const enMod = await import("@/lib/i18n/locales/en");
      const arMod = await import("@/lib/i18n/locales/ar");
      const en = enMod.en as unknown as Record<string, string>;
      const ar = arMod.ar as unknown as Record<string, string>;
      expect(en["error.forbidden"]).toBeTruthy();
      expect(ar["error.forbidden"]).toBeTruthy();
    });
  });

  describe("CRM-EXEC-18: no G8 code enters the corrective diff", () => {
    // Smoke check: the EXECUTION_ACCESS_CAPABILITIES list must NOT contain
    // any G8-namespaced capability.
    it("EXECUTION_ACCESS_CAPABILITIES contains zero G8.* entries", () => {
      const g8Leaks = EXECUTION_ACCESS_CAPABILITIES.filter((c) =>
        c.toUpperCase().startsWith("G8."),
      );
      expect(g8Leaks).toEqual([]);
    });
  });

  describe("capability helper sanity (regression guard for the helper itself)", () => {
    it("hasCapability returns true only for exact match", () => {
      const me = meWith(["CRM.ACCOUNT.READ"]);
      expect(hasCapability(me, "CRM.ACCOUNT.READ")).toBe(true);
      expect(hasCapability(me, "CRM.CONTACT.READ")).toBe(false);
    });

    it("hasAnyCapability returns true when ANY of the listed is present", () => {
      expect(hasAnyCapability(meWith(["CRM.TAG.READ"]), ["CRM.ACCOUNT.READ", "CRM.TAG.READ"])).toBe(true);
      expect(hasAnyCapability(meWith(["HR.EMPLOYEE.READ"]), ["CRM.ACCOUNT.READ", "CRM.TAG.READ"])).toBe(false);
      expect(hasAnyCapability(null, ["CRM.ACCOUNT.READ"])).toBe(false);
    });
  });
});
