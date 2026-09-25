/**
 * G2 Authenticated E2E — Employee/Manager/HR Playwright stateful journey.
 *
 * MANDATORY for G2 certification. FAILS (not skips) if credentials absent.
 * No swallowed errors. No test.skip. No soft-success fallback.
 * No if(isVisible) for mandatory business actions.
 *
 * Real stateful journey (directive §6):
 *   1. Employee authenticates → opens /hr/leave → creates + submits a leave request.
 *   2. Manager authenticates (clean session) → opens /hr/leave/approvals →
 *      finds THE SAME leave request → clicks "Manager Approve" → request escalates to PENDING_HR.
 *   3. HR authenticates (clean session) → opens /hr/leave/approvals →
 *      finds the same request (now PENDING_HR) → clicks "HR Approve" →
 *      request transitions to APPROVED.
 *
 * Employee self-service table (/hr/leave) does NOT render a Reason column.
 * The test uses leaveRequestId (captured from create response) as the
 * canonical identity for cross-role verification. On the Employee page,
 * it verifies via API response (GET /leave/requests) + [data-status]
 * badge. On the Manager/HR approval pages (which DO render Reason),
 * it uses LEAVE_REASON text matching + data-testid for action buttons.
 */

import { test, expect, type Page } from "@playwright/test";
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

async function expectLeaveApprovalsReady(page: Page): Promise<void> {
  await expect(page.getByTestId("g2-page-title")).toBeVisible({ timeout: 15_000 });
  await expect(page.getByTestId("leave-approvals-ready")).toBeVisible({ timeout: 15_000 });
}

// =====================================================================
// G2 DESKTOP JOURNEY — single stateful test across 3 roles
// =====================================================================
test.describe("G2 Desktop Journey @desktop", () => {
  test("employee submits → manager approves → HR approves → APPROVED", async ({ browser }) => {
    // ---------- EMPLOYEE ----------
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

    const selfRequestsResponse = employeePage.waitForResponse(
      (r) => r.request().method() === "GET" && new URL(r.url()).pathname.endsWith("/api/v2/hr/leave/requests"),
      { timeout: 30_000 },
    );

    await employeePage.getByTestId("leave-submit").click();

    const createRes = await createResponse;
    expect(createRes.ok(), `Leave create failed: ${createRes.status()}`).toBe(true);
    const createBody = (await createRes.json()) as { id?: string; requestId?: string };
    const leaveRequestId = createBody.requestId ?? createBody.id;
    expect(leaveRequestId, "Leave create response must return request id").toBeTruthy();

    const submitRes = await submitResponse;
    expect(submitRes.ok(), `Leave submit failed: ${submitRes.status()}`).toBe(true);
    expect(submitRes.url()).toContain(`/leave/requests/${leaveRequestId}/submit`);

    const selfRes = await selfRequestsResponse;
    expect(selfRes.ok(), `Self leave requests GET failed: ${selfRes.status()}`).toBe(true);
    const selfBody = (await selfRes.json()) as Array<{ id: string; state: string }>;
    expect(
      selfBody.some((r) => r.id === leaveRequestId && r.state === "PENDING_MANAGER"),
      `Self leave requests must contain the submitted request (id=${leaveRequestId}, state=PENDING_MANAGER). Response: ${JSON.stringify(selfBody).slice(0, 300)}`,
    ).toBe(true);

    await expect(employeePage.locator('[data-status="PENDING_MANAGER"]')).toBeVisible({ timeout: 15_000 });

    await logoutThroughUi(employeePage);
    await employeeContext.close();

    // ---------- MANAGER ----------
    const managerContext = await browser.newContext();
    const managerPage = await managerContext.newPage();
    const managerLogin = await loginThroughUi(managerPage, "manager");
    expect(managerLogin.user.email).toBe(roleEmail("manager"));

    const teamApiResponse = managerPage.waitForResponse(
      (r) => r.request().method() === "GET" && r.url().includes("/leave/requests/team"),
      { timeout: 30_000 },
    );
    await managerPage.goto(`${BASE_URL}/hr/leave/approvals`);
    await expectLeaveApprovalsReady(managerPage);
    const teamRes = await teamApiResponse;
    expect(teamRes.ok(), `Manager team leave requests API failed: ${teamRes.status()}`).toBe(true);
    const teamBody = (await teamRes.json()) as Array<{ id: string; reason: string }>;
    expect(
      teamBody.some((r) => r.reason === LEAVE_REASON),
      `Manager team leave requests must contain the Employee's request with reason="${LEAVE_REASON}". Response: ${JSON.stringify(teamBody).slice(0, 300)}`,
    ).toBe(true);

    await expect(managerPage.locator("table")).toBeVisible({ timeout: 15_000 });

    const managerRow = managerPage.locator("tr", { hasText: LEAVE_REASON }).first();
    await expect(managerRow).toBeVisible({ timeout: 15_000 });

    const managerApproveBtn = managerPage.getByTestId(`manager-approve-${leaveRequestId}`);
    await expect(managerApproveBtn).toBeVisible();
    expect(await managerPage.getByTestId(`hr-approve-${leaveRequestId}`).count()).toBe(0);

    const managerApproveResponse = managerPage.waitForResponse(
      (r) => r.request().method() === "POST" && r.url().includes(`/leave/requests/${leaveRequestId}/manager-approve`),
      { timeout: 30_000 },
    );
    await managerApproveBtn.click();
    const mgrApproveRes = await managerApproveResponse;
    expect(mgrApproveRes.ok(), `Manager approve failed: ${mgrApproveRes.status()}`).toBe(true);
    await expect(managerPage.getByRole("status").filter({ hasText: /Manager approved/i })).toBeVisible({ timeout: 10_000 });

    await logoutThroughUi(managerPage);
    await managerContext.close();

    // ---------- HR ----------
    const hrContext = await browser.newContext();
    const hrPage = await hrContext.newPage();
    const hrLogin = await loginThroughUi(hrPage, "hr");
    expect(hrLogin.user.email).toBe(roleEmail("hr"));

    await hrPage.goto(`${BASE_URL}/hr/leave/approvals`);
    await expectLeaveApprovalsReady(hrPage);
    await expect(hrPage.locator("table")).toBeVisible({ timeout: 15_000 });

    const hrRow = hrPage.locator("tr", { hasText: LEAVE_REASON }).first();
    await expect(hrRow).toBeVisible({ timeout: 15_000 });

    const hrApproveBtn = hrPage.getByTestId(`hr-approve-${leaveRequestId}`);
    await expect(hrApproveBtn).toBeVisible();

    const hrApproveResponse = hrPage.waitForResponse(
      (r) => r.request().method() === "POST" && r.url().includes(`/leave/requests/${leaveRequestId}/hr-approve`),
      { timeout: 30_000 },
    );
    await hrApproveBtn.click();
    const hrApproveRes = await hrApproveResponse;
    expect(hrApproveRes.ok(), `HR approve failed: ${hrApproveRes.status()}`).toBe(true);
    await expect(hrPage.getByRole("status").filter({ hasText: /HR approved/i })).toBeVisible({ timeout: 10_000 });

    await logoutThroughUi(hrPage);
    await hrContext.close();

    // ---------- FINAL EMPLOYEE VERIFICATION ----------
    const verificationContext = await browser.newContext();
    const verificationPage = await verificationContext.newPage();
    const verificationLogin = await loginThroughUi(verificationPage, "employee");
    expect(verificationLogin.user.email).toBe(roleEmail("employee"));

    const verifySelfResponse = verificationPage.waitForResponse(
      (r) => r.request().method() === "GET" && new URL(r.url()).pathname.endsWith("/api/v2/hr/leave/requests"),
      { timeout: 30_000 },
    );
    await verificationPage.goto(`${BASE_URL}/hr/leave`);
    await expect(verificationPage.getByTestId("leave-ready")).toBeVisible({ timeout: 15_000 });
    const verifySelfRes = await verifySelfResponse;
    expect(verifySelfRes.ok(), `Verification self leave requests GET failed: ${verifySelfRes.status()}`).toBe(true);
    const verifyBody = (await verifySelfRes.json()) as Array<{ id: string; state: string }>;
    expect(
      verifyBody.some((r) => r.id === leaveRequestId && r.state === "APPROVED"),
      `Final verification: self leave requests must contain the approved request (id=${leaveRequestId}, state=APPROVED). Response: ${JSON.stringify(verifyBody).slice(0, 300)}`,
    ).toBe(true);

    await expect(verificationPage.locator('[data-status="APPROVED"]')).toBeVisible({ timeout: 15_000 });

    await logoutThroughUi(verificationPage);
    await verificationContext.close();
  });
});

// =====================================================================
// G2 MANAGER NAV JOURNEY — verifies Manager UI surfaces render
// =====================================================================
test.describe("G2 Manager Nav @desktop", () => {
  test("manager: login → team attendance → team timesheets → leave approvals", async ({ page }) => {
    await loginThroughUi(page, "manager");

    await page.goto(`${BASE_URL}/hr/team-attendance`);
    await expect(page.getByRole("heading", { name: "Team Attendance", exact: true })).toBeVisible();

    await page.goto(`${BASE_URL}/hr/team-timesheets`);
    await expect(page.getByRole("heading", { name: "Team Timesheets", exact: true })).toBeVisible();

    await page.goto(`${BASE_URL}/hr/leave/approvals`);
    await expectLeaveApprovalsReady(page);
  });
});

// =====================================================================
// G2 HR NAV JOURNEY — verifies HR admin UI surfaces render
// =====================================================================
test.describe("G2 HR Nav @desktop", () => {
  test("HR: login → schedules → attendance admin → leave policies → monthly report", async ({ page }) => {
    await loginThroughUi(page, "hr");

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

// =====================================================================
// G2 EMPLOYEE MOBILE JOURNEY — real attendance mutation (clock in/out)
// =====================================================================
test.describe("G2 Employee Journey @mobile", () => {
  test.use({ viewport: { width: 375, height: 667 } });
  test("employee mobile: login → attendance → perform real clock mutation → verify persisted state", async ({ page }) => {
    await loginThroughUi(page, "employee");
    await page.goto(`${BASE_URL}/hr/attendance`);
    await expect(page.locator("h1").first()).toBeVisible();
    await page.waitForLoadState("networkidle");

    const clockInBtn = page.getByTestId("attendance-clock-in");
    const clockOutBtn = page.getByTestId("attendance-clock-out");

    await expect(clockInBtn.or(clockOutBtn)).toBeVisible({ timeout: 10_000 });

    const inBtnVisible = await clockInBtn.isVisible();
    const outBtnVisible = await clockOutBtn.isVisible();
    expect(inBtnVisible && outBtnVisible, "Both clock buttons cannot be visible simultaneously (state invariant)").toBe(false);

    if (inBtnVisible) {
      const clockInResponse = page.waitForResponse(
        (r) => r.request().method() === "POST" && r.url().includes("/api/v2/hr/time/attendance/clock-in"),
        { timeout: 30_000 },
      );
      await clockInBtn.click();
      const res = await clockInResponse;
      expect(res.ok(), `Clock-in API failed: ${res.status()} ${res.statusText()}`).toBe(true);
      await expect(clockOutBtn).toBeVisible({ timeout: 15_000 });
    } else {
      const clockOutResponse = page.waitForResponse(
        (r) => r.request().method() === "POST" && r.url().includes("/api/v2/hr/time/attendance/") && r.url().includes("clock-out"),
        { timeout: 30_000 },
      );
      await clockOutBtn.click();
      const res = await clockOutResponse;
      expect(res.ok(), `Clock-out API failed: ${res.status()} ${res.statusText()}`).toBe(true);
      await expect(clockInBtn).toBeVisible({ timeout: 15_000 });
    }
  });
});
