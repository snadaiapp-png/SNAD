/**
 * G2 Authenticated E2E — Employee/Manager/HR stateful journey.
 * Required for G2 certification. No skips, retries, raw authenticated fetch,
 * network-idle heuristics, or soft-success fallbacks.
 */

import { test, expect } from "@playwright/test";
import { loginThroughUi, logoutThroughUi, roleEmail } from "./g2-auth-session";

const BASE_URL = process.env.PLAYWRIGHT_BASE_URL ?? "http://localhost:3000";
const RUN_ID = process.env.GITHUB_RUN_ID || `local-${Date.now()}`;
const LEAVE_REASON = `G2 E2E Journey ${RUN_ID}`;

function futureDate(offsetDays: number): string {
  const d = new Date();
  d.setDate(d.getDate() + offsetDays);
  return d.toISOString().slice(0, 10);
}

const LEAVE_START = futureDate(7);
const LEAVE_END = futureDate(8);

test.describe("G2 Desktop Journey @desktop", () => {
  test("employee submits → manager approves → HR approves → APPROVED", async ({ browser }) => {
    const employeeContext = await browser.newContext();
    const employeePage = await employeeContext.newPage();

    const employeeLogin = await loginThroughUi(employeePage, "employee");
    expect(employeeLogin.user.email).toBe(roleEmail("employee"));

    await employeePage.goto(`${BASE_URL}/hr/leave`);
    await expect(employeePage.getByTestId("g2-page-title")).toBeVisible();
    await expect(employeePage.getByTestId("leave-ready")).toBeVisible({ timeout: 15_000 });

    await employeePage.getByTestId("request-leave-toggle").click();
    await expect(employeePage.getByTestId("leave-request-form")).toBeVisible();
    await employeePage.getByTestId("leave-type").selectOption({ index: 1 });
    await employeePage.getByTestId("leave-start").fill(LEAVE_START);
    await employeePage.getByTestId("leave-end").fill(LEAVE_END);
    await employeePage.getByTestId("leave-reason").fill(LEAVE_REASON);

    const createResponse = employeePage.waitForResponse(
      (r) => r.request().method() === "POST" && new URL(r.url()).pathname.endsWith("/api/v2/hr/leave/requests"),
      { timeout: 30_000 },
    );
    const submitResponse = employeePage.waitForResponse(
      (r) => r.request().method() === "POST" && /\/api\/v2\/hr\/leave\/requests\/[^/]+\/submit$/.test(new URL(r.url()).pathname),
      { timeout: 30_000 },
    );

    await employeePage.getByTestId("leave-submit").click();

    const createRes = await createResponse;
    expect(createRes.ok(), `Leave create failed: ${createRes.status()}`).toBe(true);
    const createBody = await createRes.json() as { id?: string; requestId?: string };
    const leaveRequestId = createBody.requestId ?? createBody.id;
    expect(leaveRequestId, "Leave create response must return request id").toBeTruthy();

    const submitRes = await submitResponse;
    expect(submitRes.ok(), `Leave submit failed: ${submitRes.status()}`).toBe(true);
    expect(submitRes.url()).toContain(`/leave/requests/${leaveRequestId}/submit`);

    await expect(
      employeePage.getByRole("status").filter({ hasText: "تم إرسال طلب الإجازة" }),
    ).toBeVisible({ timeout: 10_000 });
    await expect(employeePage.locator("tr", { hasText: LEAVE_REASON })).toBeVisible({ timeout: 15_000 });

    await logoutThroughUi(employeePage);
    await employeeContext.close();

    const managerContext = await browser.newContext();
    const managerPage = await managerContext.newPage();
    const managerLogin = await loginThroughUi(managerPage, "manager");
    expect(managerLogin.user.email).toBe(roleEmail("manager"));

    // Wait for the team leave requests API response before looking for the row.
    // This surfaces HTTP errors (401/403/500) and timing issues that cause
    // the approval queue to render empty.
    const teamApiResponse = managerPage.waitForResponse(
      (r) => r.request().method() === "GET" && r.url().includes("/leave/requests/team"),
      { timeout: 30_000 },
    );
    await managerPage.goto(`${BASE_URL}/hr/leave/approvals`);
    await expect(managerPage.getByRole("heading", { name: "Leave Approval Queue", exact: true })).toBeVisible();
    const teamRes = await teamApiResponse;
    expect(teamRes.ok(), `Manager team leave requests API failed: ${teamRes.status()} ${teamRes.statusText()}`).toBe(true);
    const teamBody = await teamRes.json();
    expect(teamBody.length, `Manager team leave requests returned 0 results — expected at least 1 (the Employee's leave request). Response: ${JSON.stringify(teamBody)}`).toBeGreaterThan(0);

    await expect(managerPage.locator("table")).toBeVisible({ timeout: 15_000 });

    const managerRow = managerPage.locator("tr", { hasText: LEAVE_REASON }).first();
    await expect(managerRow).toBeVisible({ timeout: 15_000 });
    const managerApproveBtn = managerRow.locator('[data-testid^="manager-approve-"]').first();
    await expect(managerApproveBtn).toBeVisible();
    await expect(managerRow.locator('[data-testid^="hr-approve-"]')).toHaveCount(0);

    const managerApproveResponse = managerPage.waitForResponse(
      (r) => r.request().method() === "POST" && r.url().includes(`/leave/requests/${leaveRequestId}/manager-approve`),
      { timeout: 30_000 },
    );
    await managerApproveBtn.click();
    const mgrApproveRes = await managerApproveResponse;
    expect(mgrApproveRes.ok(), `Manager approve failed: ${mgrApproveRes.status()}`).toBe(true);
    await expect(
      managerPage.getByRole("status").filter({ hasText: /Manager approved/i }),
    ).toBeVisible({ timeout: 10_000 });
    await expect(managerPage.locator("tr", { hasText: LEAVE_REASON })).toHaveCount(0, { timeout: 15_000 });

    await logoutThroughUi(managerPage);
    await managerContext.close();

    const hrContext = await browser.newContext();
    const hrPage = await hrContext.newPage();
    const hrLogin = await loginThroughUi(hrPage, "hr");
    expect(hrLogin.user.email).toBe(roleEmail("hr"));

    await hrPage.goto(`${BASE_URL}/hr/leave/approvals`);
    await expect(hrPage.getByRole("heading", { name: "Leave Approval Queue", exact: true })).toBeVisible();
    await expect(hrPage.locator("table")).toBeVisible({ timeout: 15_000 });

    const hrRow = hrPage.locator("tr", { hasText: LEAVE_REASON }).first();
    await expect(hrRow).toBeVisible({ timeout: 15_000 });
    const hrApproveBtn = hrRow.locator('[data-testid^="hr-approve-"]').first();
    await expect(hrApproveBtn).toBeVisible();

    const hrApproveResponse = hrPage.waitForResponse(
      (r) => r.request().method() === "POST" && r.url().includes(`/leave/requests/${leaveRequestId}/hr-approve`),
      { timeout: 30_000 },
    );
    await hrApproveBtn.click();
    const hrApproveRes = await hrApproveResponse;
    expect(hrApproveRes.ok(), `HR approve failed: ${hrApproveRes.status()}`).toBe(true);
    await expect(
      hrPage.getByRole("status").filter({ hasText: /HR approved/i }),
    ).toBeVisible({ timeout: 10_000 });
    await expect(hrPage.locator("tr", { hasText: LEAVE_REASON })).toHaveCount(0, { timeout: 15_000 });

    await logoutThroughUi(hrPage);
    await hrContext.close();

    const verificationContext = await browser.newContext();
    const verificationPage = await verificationContext.newPage();
    const verificationLogin = await loginThroughUi(verificationPage, "employee");
    expect(verificationLogin.user.email).toBe(roleEmail("employee"));

    await verificationPage.goto(`${BASE_URL}/hr/leave`);
    await expect(verificationPage.getByTestId("leave-ready")).toBeVisible({ timeout: 15_000 });
    const approvedRow = verificationPage.locator("tr", { hasText: LEAVE_REASON }).first();
    await expect(approvedRow).toBeVisible({ timeout: 15_000 });
    await expect(approvedRow.locator('[data-status="APPROVED"]')).toBeVisible();

    await logoutThroughUi(verificationPage);
    await verificationContext.close();
  });
});

test.describe("G2 Manager Nav @desktop", () => {
  test("manager: login → team attendance → team timesheets → leave approvals queue", async ({ page }) => {
    const login = await loginThroughUi(page, "manager");
    expect(login.user.email).toBe(roleEmail("manager"));

    await page.goto(`${BASE_URL}/hr/team-attendance`);
    await expect(page.getByRole("heading", { name: "Team Attendance", exact: true })).toBeVisible();

    await page.goto(`${BASE_URL}/hr/team-timesheets`);
    await expect(page.getByRole("heading", { name: "Team Timesheets", exact: true })).toBeVisible();

    await page.goto(`${BASE_URL}/hr/leave/approvals`);
    await expect(page.getByRole("heading", { name: "Leave Approval Queue", exact: true })).toBeVisible();
  });
});

test.describe("G2 HR Nav @desktop", () => {
  test("HR: login → schedules → attendance admin → leave policies → monthly report", async ({ page }) => {
    const login = await loginThroughUi(page, "hr");
    expect(login.user.email).toBe(roleEmail("hr"));

    await page.goto(`${BASE_URL}/hr/schedules`);
    await expect(page.getByRole("heading", { name: "Work Schedules", exact: true })).toBeVisible();

    await page.goto(`${BASE_URL}/hr/attendance/admin`);
    await expect(page.getByRole("heading", { name: "Attendance Administration", exact: true })).toBeVisible();

    await page.goto(`${BASE_URL}/hr/leave/policies`);
    await expect(page.getByRole("heading", { name: "Leave Policies", exact: true })).toBeVisible();

    await page.goto(`${BASE_URL}/hr/reports/attendance`);
    await expect(page.getByRole("heading", { name: "Monthly Attendance Report", exact: true })).toBeVisible();
  });
});

test.describe("G2 Employee Journey @mobile", () => {
  test.use({ viewport: { width: 375, height: 667 } });

  test("employee mobile: login → attendance → perform real clock mutation → verify persisted state", async ({ page }) => {
    const login = await loginThroughUi(page, "employee");
    expect(login.user.email).toBe(roleEmail("employee"));

    await page.goto(`${BASE_URL}/hr/attendance`);
    await expect(page.getByTestId("g2-page-title")).toBeVisible();
    await expect(page.getByTestId("attendance-ready")).toBeVisible({ timeout: 15_000 });

    const clockInBtn = page.getByTestId("attendance-clock-in");
    const clockOutBtn = page.getByTestId("attendance-clock-out");
    const inCount = await clockInBtn.count();
    const outCount = await clockOutBtn.count();
    expect(inCount + outCount, "Exactly one canonical attendance action must be rendered").toBe(1);

    if (inCount === 1) {
      const response = page.waitForResponse(
        (r) => r.request().method() === "POST" && r.url().includes("/api/v2/hr/time/attendance/clock-in"),
        { timeout: 30_000 },
      );
      await clockInBtn.click();
      const res = await response;
      expect(res.ok(), `Clock-in API failed: ${res.status()} ${res.statusText()}`).toBe(true);
      await expect(clockOutBtn).toBeVisible({ timeout: 15_000 });
      await expect(clockInBtn).toHaveCount(0);

      await page.reload();
      await expect(page.getByTestId("attendance-ready")).toBeVisible({ timeout: 15_000 });
      await expect(clockOutBtn).toBeVisible({ timeout: 15_000 });
      await expect(clockInBtn).toHaveCount(0);
    } else {
      const response = page.waitForResponse(
        (r) => r.request().method() === "POST" && /\/api\/v2\/hr\/time\/attendance\/[^/]+\/clock-out$/.test(new URL(r.url()).pathname),
        { timeout: 30_000 },
      );
      await clockOutBtn.click();
      const res = await response;
      expect(res.ok(), `Clock-out API failed: ${res.status()} ${res.statusText()}`).toBe(true);
      await expect(clockInBtn).toBeVisible({ timeout: 15_000 });
      await expect(clockOutBtn).toHaveCount(0);

      await page.reload();
      await expect(page.getByTestId("attendance-ready")).toBeVisible({ timeout: 15_000 });
      await expect(clockInBtn).toBeVisible({ timeout: 15_000 });
      await expect(clockOutBtn).toHaveCount(0);
    }
  });
});
