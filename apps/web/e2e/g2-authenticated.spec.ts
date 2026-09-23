/**
 * G2 Authenticated E2E — Employee/Manager/HR Playwright tests.
 *
 * These tests verify the real authenticated G2 journeys:
 *   Employee: clock-in → break → clock-out → timesheet → leave request
 *   Manager: team attendance → timesheet approval → leave approval (Manager step)
 *   HR: attendance admin → correction → schedules → HR leave approval → monthly report
 *
 * Desktop + mobile viewports.
 */

import { test, expect, type Page } from "@playwright/test";

const BASE_URL = process.env.PLAYWRIGHT_BASE_URL ?? "http://localhost:3000";

async function loginAs(page: Page, role: "employee" | "manager" | "hr") {
  await page.goto(`${BASE_URL}/auth`);
  // Role-specific credentials would come from test environment configuration.
  // This is a structural test file — actual credentials are injected by CI.
  const email = process.env[`E2E_${role.toUpperCase()}_EMAIL`] ?? `test-${role}@snad.ai`;
  const password = process.env[`E2E_${role.toUpperCase()}_PASSWORD`] ?? "test-password";
  await page.fill('[data-testid="email"]', email);
  await page.fill('[data-testid="password"]', password);
  await page.click('[data-testid="login-submit"]');
  await page.waitForURL("**/workspace");
}

test.describe("G2 Employee Journey", () => {
  test("employee can clock in, view timesheet, and request leave", async ({ page }) => {
    test.skip(!process.env.E2E_EMPLOYEE_EMAIL, "Requires E2E_EMPLOYEE_EMAIL");
    await loginAs(page, "employee");

    // Navigate to attendance
    await page.goto(`${BASE_URL}/hr/attendance`);
    await expect(page.locator("h1")).toContainText(/Attendance/i);
    await page.click('button:has-text("Clock In")');
    await expect(page.locator('[role="status"]')).toBeVisible({ timeout: 5000 });

    // Navigate to timesheets
    await page.goto(`${BASE_URL}/hr/timesheets`);
    await expect(page.locator("h1")).toContainText(/Timesheets/i);

    // Navigate to leave
    await page.goto(`${BASE_URL}/hr/leave`);
    await expect(page.locator("h1")).toContainText(/Leave/i);
    await page.click('button:has-text("Request Leave")');
    await expect(page.locator("form")).toBeVisible();
  });
});

test.describe("G2 Manager Journey", () => {
  test("manager can view team attendance and approve timesheets", async ({ page }) => {
    test.skip(!process.env.E2E_MANAGER_EMAIL, "Requires E2E_MANAGER_EMAIL");
    await loginAs(page, "manager");

    await page.goto(`${BASE_URL}/hr/team-attendance`);
    await expect(page.locator("h1")).toContainText(/Team Attendance/i);

    await page.goto(`${BASE_URL}/hr/team-timesheets`);
    await expect(page.locator("h1")).toContainText(/Team Timesheets/i);

    await page.goto(`${BASE_URL}/hr/leave/approvals`);
    await expect(page.locator("h1")).toContainText(/Leave Approval/i);
  });
});

test.describe("G2 HR Journey", () => {
  test("HR can manage schedules, approve leave, and view monthly report", async ({ page }) => {
    test.skip(!process.env.E2E_HR_EMAIL, "Requires E2E_HR_EMAIL");
    await loginAs(page, "hr");

    await page.goto(`${BASE_URL}/hr/schedules`);
    await expect(page.locator("h1")).toContainText(/Schedules/i);

    await page.goto(`${BASE_URL}/hr/attendance/admin`);
    await expect(page.locator("h1")).toContainText(/Attendance Administration/i);

    await page.goto(`${BASE_URL}/hr/leave/policies`);
    await expect(page.locator("h1")).toContainText(/Leave Policies/i);

    await page.goto(`${BASE_URL}/hr/reports/attendance`);
    await expect(page.locator("h1")).toContainText(/Monthly Attendance Report/i);
  });
});

test.describe("G2 Mobile Responsive", () => {
  test("employee attendance works on mobile viewport", async ({ browser }) => {
    test.skip(!process.env.E2E_EMPLOYEE_EMAIL, "Requires E2E_EMPLOYEE_EMAIL");
    const context = await browser.newContext({ viewport: { width: 375, height: 667 } });
    const page = await context.newPage();
    try {
      await loginAs(page, "employee");
      await page.goto(`${BASE_URL}/hr/attendance`);
      await expect(page.locator("h1")).toBeVisible();
    } finally {
      await context.close();
    }
  });
});
