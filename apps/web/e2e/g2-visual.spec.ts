import { test, expect } from "@playwright/test";
import { loginThroughUi, roleEmail } from "./g2-auth-session";
import {
  assertVisualSurface,
  captureVisualEvidence,
  initializeVisualDiagnostics,
  type G2Role,
  type VisualSurface,
} from "./g2-visual-helpers";

const BASE_URL = process.env.PLAYWRIGHT_BASE_URL ?? "http://127.0.0.1:3001";

const SURFACES = {
  employee: [
    { route: "/hr", readyTestId: "hr-landing-ready" },
    { route: "/hr/attendance", readyTestId: "attendance-ready" },
    { route: "/hr/timesheets", readyTestId: "timesheets-ready" },
    { route: "/hr/leave", readyTestId: "leave-ready" },
  ],
  manager: [
    { route: "/hr", readyTestId: "hr-landing-ready" },
    { route: "/hr/team-attendance", readyTestId: "team-attendance-ready" },
    { route: "/hr/team-timesheets", readyTestId: "team-timesheets-ready" },
    { route: "/hr/leave/approvals", readyTestId: "leave-approvals-ready" },
    { route: "/hr/reports/attendance", readyTestId: "attendance-report-ready" },
  ],
  hr: [
    { route: "/hr", readyTestId: "hr-landing-ready" },
    { route: "/hr/schedules", readyTestId: "schedules-ready" },
    { route: "/hr/attendance/admin", readyTestId: "attendance-admin-ready" },
    { route: "/hr/leave/policies", readyTestId: "leave-policies-ready" },
    { route: "/hr/leave/approvals", readyTestId: "leave-approvals-ready" },
    { route: "/hr/reports/attendance", readyTestId: "attendance-report-ready" },
  ],
} satisfies Record<G2Role, readonly VisualSurface[]>;

const LANDING_GROUP = {
  employee: "g2-launcher-self",
  manager: "g2-launcher-team",
  hr: "g2-launcher-hr",
} satisfies Record<G2Role, string>;

for (const role of ["employee", "manager", "hr"] as const) {
  test.describe(`G2 visual ${role}`, () => {
    test(`${role} role surfaces are visually certifiable`, async ({ page }, testInfo) => {
      const locale = testInfo.project.use.locale === "en" ? "en" : "ar";
      await page.addInitScript((selectedLocale) => {
        localStorage.setItem("snad.locale", selectedLocale);
        localStorage.setItem("snad.theme", "light");
      }, locale);
      initializeVisualDiagnostics(page);

      const login = await loginThroughUi(page, role);
      expect(login.user.email).toBe(roleEmail(role));

      for (const surface of SURFACES[role]) {
        await page.goto(`${BASE_URL}${surface.route}`);
        await page.waitForLoadState("domcontentloaded");
        await assertVisualSurface(page, surface);

        await expect(page.locator("html")).toHaveAttribute("lang", locale);
        await expect(page.locator("html")).toHaveAttribute("dir", locale === "ar" ? "rtl" : "ltr");

        if (surface.route === "/hr") {
          await expect(page.getByTestId(LANDING_GROUP[role])).toBeVisible();
        } else {
          await expect(page.locator(`nav a[href="${surface.route}"]`).first()).toBeVisible();
        }

        await captureVisualEvidence(page, role, surface.route, testInfo.project.name);
      }
    });
  });
}
