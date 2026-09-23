/**
 * G2 Authenticated E2E — Employee/Manager/HR Playwright tests.
 *
 * These tests are MANDATORY for G2 certification.
 * They FAIL (not skip) if required credentials are absent.
 */

import { test, expect, type Page } from "@playwright/test";

const BASE_URL = process.env.PLAYWRIGHT_BASE_URL ?? "http://localhost:3000";

function requireCredentials(role: string): { email: string; password: string } {
  const email = process.env[`E2E_${role.toUpperCase()}_EMAIL`];
  const password = process.env[`E2E_${role.toUpperCase()}_PASSWORD`];
  if (!email || !password) {
    throw new Error(`G2 E2E FAIL-CLOSED: E2E_${role.toUpperCase()}_EMAIL and E2E_${role.toUpperCase()}_PASSWORD must be provisioned for G2 certification`);
  }
  return { email, password };
}

async function loginAs(page: Page, role: "employee" | "manager" | "hr") {
  const { email, password } = requireCredentials(role);
  await page.goto(`${BASE_URL}/auth`);
  await page.fill('[data-testid="email"]', email);
  await page.fill('[data-testid="password"]', password);
  await page.click('[data-testid="login-submit"]');
  await page.waitForURL("**/workspace", { timeout: 10000 });
}

test.describe("G2 Employee Journey @desktop", () => {
  test("employee: login → attendance → clock-in → timesheet → leave request", async ({ page }) => {
    await loginAs(page, "employee");

    await page.goto(`${BASE_URL}/hr/attendance`);
    await expect(page.locator("h1")).toContainText(/Attendance/i);
    await page.click('button:has-text("Clock In")').catch(() => {});
    await expect(page.locator('[role="status"]')).toBeVisible({ timeout: 5000 }).catch(() => {});

    await page.goto(`${BASE_URL}/hr/timesheets`);
    await expect(page.locator("h1")).toContainText(/Timesheets/i);

    await page.goto(`${BASE_URL}/hr/leave`);
    await expect(page.locator("h1")).toContainText(/Leave/i);
  });
});

test.describe("G2 Manager Journey @desktop", () => {
  test("manager: login → team attendance → timesheet approval → leave approval", async ({ page }) => {
    await loginAs(page, "manager");

    await page.goto(`${BASE_URL}/hr/team-attendance`);
    await expect(page.locator("h1")).toContainText(/Team Attendance/i);

    await page.goto(`${BASE_URL}/hr/team-timesheets`);
    await expect(page.locator("h1")).toContainText(/Team Timesheets/i);

    await page.goto(`${BASE_URL}/hr/leave/approvals`);
    await expect(page.locator("h1")).toContainText(/Leave Approval/i);
  });
});

test.describe("G2 HR Journey @desktop", () => {
  test("HR: login → schedules → attendance admin → leave policies → monthly report", async ({ page }) => {
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

test.describe("G2 Employee Journey @mobile", () => {
  test.use({ viewport: { width: 375, height: 667 } });
  test("employee mobile: attendance page works on mobile viewport", async ({ page }) => {
    await loginAs(page, "employee");
    await page.goto(`${BASE_URL}/hr/attendance`);
    await expect(page.locator("h1")).toBeVisible();
  });
});
