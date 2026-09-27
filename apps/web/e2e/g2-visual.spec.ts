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
const REQUIRE_PRODUCT_DATA = process.env.G2_VISUAL_REQUIRE_PRODUCT_DATA === "true";

const SURFACES = {
  employee: [
    {
      route: "/hr",
      readyTestId: "hr-landing-ready",
      productSurfaceTestId: "g2-product-areas",
      requiredProductTestIds: ["g2-launcher-self-summary"],
      forbiddenTestIds: ["g2-launcher-team", "g2-launcher-hr"],
    },
    {
      route: "/hr/attendance",
      readyTestId: "attendance-ready",
      productSurfaceTestId: "attendance-product-surface",
      requiredProductTestIds: ["attendance-current-state", "attendance-history-panel"],
    },
    {
      route: "/hr/timesheets",
      readyTestId: "timesheets-ready",
      productSurfaceTestId: "timesheets-product-surface",
      requiredProductTestIds: ["timesheets-current-period", "timesheets-current-state", "timesheets-history-panel"],
    },
    {
      route: "/hr/leave",
      readyTestId: "leave-ready",
      productSurfaceTestId: "leave-product-surface",
      requiredProductTestIds: ["leave-balance-summary", "leave-pending-summary", "leave-requests-panel"],
    },
  ],
  manager: [
    {
      route: "/hr",
      readyTestId: "hr-landing-ready",
      productSurfaceTestId: "g2-product-areas",
      requiredProductTestIds: ["g2-launcher-team-summary"],
      forbiddenTestIds: ["g2-launcher-hr"],
    },
    {
      route: "/hr/team-attendance",
      readyTestId: "team-attendance-ready",
      productSurfaceTestId: "team-attendance-product-surface",
      requiredProductTestIds: ["team-attendance-open-count", "team-attendance-records-panel"],
    },
    {
      route: "/hr/team-timesheets",
      readyTestId: "team-timesheets-ready",
      productSurfaceTestId: "team-timesheets-product-surface",
      requiredProductTestIds: ["team-timesheets-pending-count", "team-timesheets-queue-panel"],
    },
    {
      route: "/hr/leave/approvals",
      readyTestId: "leave-approvals-ready",
      productSurfaceTestId: "leave-approvals-product-surface",
      requiredProductTestIds: ["leave-approvals-manager-count", "leave-approvals-queue-panel"],
    },
    {
      route: "/hr/reports/attendance",
      readyTestId: "attendance-report-ready",
      productSurfaceTestId: "attendance-report-product-surface",
      requiredProductTestIds: ["attendance-report-people-count", "attendance-report-filter-bar", "attendance-report-results-panel"],
    },
  ],
  hr: [
    {
      route: "/hr",
      readyTestId: "hr-landing-ready",
      productSurfaceTestId: "g2-product-areas",
      requiredProductTestIds: ["g2-launcher-hr-summary"],
    },
    {
      route: "/hr/schedules",
      readyTestId: "schedules-ready",
      productSurfaceTestId: "schedules-product-surface",
      requiredProductTestIds: ["schedules-total-count", "schedules-create-action", "schedules-records-panel"],
    },
    {
      route: "/hr/attendance/admin",
      readyTestId: "attendance-admin-ready",
      productSurfaceTestId: "attendance-admin-product-surface",
      requiredProductTestIds: ["attendance-admin-open-count", "attendance-admin-correction-state", "attendance-admin-records-panel"],
    },
    {
      route: "/hr/leave/policies",
      readyTestId: "leave-policies-ready",
      productSurfaceTestId: "leave-policies-product-surface",
      requiredProductTestIds: ["leave-policies-total-count", "leave-policies-admin-state", "leave-policies-records-panel"],
    },
    {
      route: "/hr/leave/approvals",
      readyTestId: "leave-approvals-ready",
      productSurfaceTestId: "leave-approvals-product-surface",
      requiredProductTestIds: ["leave-approvals-hr-count", "leave-approvals-queue-panel"],
    },
    {
      route: "/hr/reports/attendance",
      readyTestId: "attendance-report-ready",
      productSurfaceTestId: "attendance-report-product-surface",
      requiredProductTestIds: ["attendance-report-people-count", "attendance-report-filter-bar", "attendance-report-results-panel"],
    },
  ],
} satisfies Record<G2Role, readonly VisualSurface[]>;

const LANDING_GROUP = {
  employee: "g2-launcher-self",
  manager: "g2-launcher-team",
  hr: "g2-launcher-hr",
} satisfies Record<G2Role, string>;

for (const role of ["employee", "manager", "hr"] as const) {
  test.describe(`G2 visual ${role}`, () => {
    for (const surface of SURFACES[role]) {
      test(`${role} ${surface.route} is visually certifiable`, async ({ page }, testInfo) => {
        const locale = testInfo.project.use.locale === "en" ? "en" : "ar";
        await page.addInitScript((selectedLocale) => {
          localStorage.setItem("snad.locale", selectedLocale);
          localStorage.setItem("snad.theme", "light");
        }, locale);
        initializeVisualDiagnostics(page);

        const login = await loginThroughUi(page, role);
        expect(login.user.email).toBe(roleEmail(role));

        await page.goto(`${BASE_URL}${surface.route}`);
        await page.waitForLoadState("domcontentloaded");
        await assertVisualSurface(page, surface);

        await expect(page.locator("html")).toHaveAttribute("lang", locale);
        await expect(page.locator("html")).toHaveAttribute("dir", locale === "ar" ? "rtl" : "ltr");
        await expect(
          page.locator("body"),
          `${role} ${surface.route} must not expose unresolved HRM translation keys`,
        ).not.toContainText("hrm.");

        if (surface.route === "/hr") {
          await expect(page.getByTestId(LANDING_GROUP[role])).toBeVisible();
        } else {
          await expect(page.locator(`nav a[href="${surface.route}"]`).first()).toBeVisible();
          if (REQUIRE_PRODUCT_DATA) {
            await expect(
              page.locator('main tbody tr[data-testid="hr-data-row"]').first(),
              `${role} ${surface.route} must render a real product-data row; an empty-state row is not visual closure`,
            ).toBeVisible({ timeout: 20_000 });
          }
        }

        await captureVisualEvidence(page, role, surface.route, testInfo.project.name);
      });
    }
  });
}
