/**
 * G3 Production Certification — read-only authenticated live acceptance.
 *
 * This spec intentionally performs NO Goal/Review mutations in production.
 * Stateful write lifecycle remains covered by g3-authenticated.spec.ts against
 * PostgreSQL Direct. Production proves exact release identity, UI route health,
 * positive SELF/TEAM authorization, and a real negative 403 principal.
 */

import { test, expect } from "@playwright/test";
import { loginThroughUi, logoutThroughUi } from "./g3-auth-session";

const BASE_URL = process.env.PLAYWRIGHT_BASE_URL ?? "https://snad-app.vercel.app";
const EXPECTED_SHA = process.env.G3_EXPECTED_SHA ?? "";
const EMPLOYEE_EMPLOYMENT_ID = process.env.G3_PROD_EMPLOYEE_EMPLOYMENT_ID ?? "";

function requireRuntimeContract() {
  expect(EXPECTED_SHA, "G3_EXPECTED_SHA is required").toMatch(/^[0-9a-f]{40}$/);
  expect(EMPLOYEE_EMPLOYMENT_ID, "G3_PROD_EMPLOYEE_EMPLOYMENT_ID is required").toMatch(
    /^[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}$/i,
  );
}

test.describe.configure({ mode: "serial" });

test("A. production release identity is the exact requested SHA", async ({ request }) => {
  requireRuntimeContract();
  const response = await request.get(`${BASE_URL}/api/system/release`);
  expect(response.status()).toBe(200);
  const body = (await response.json()) as { commitSha?: string; environment?: string };
  expect(body.commitSha).toBe(EXPECTED_SHA);
  expect(["production", "prod"]).toContain(body.environment);
});

test("B. employee authenticates through UI and reads live SELF Goals and Reviews", async ({ browser }) => {
  requireRuntimeContract();
  const context = await browser.newContext();
  const page = await context.newPage();
  const login = await loginThroughUi(page, "employee");

  await page.goto(`${BASE_URL}/hr/performance/goals`);
  await expect(page.getByTestId("goals-ready")).toBeVisible({ timeout: 20_000 });
  const goals = await context.request.get(`${BASE_URL}/api/platform/api/v2/hr/performance/goals`, {
    headers: { Authorization: `Bearer ${login.accessToken}` },
  });
  expect(goals.status()).toBe(200);

  await page.goto(`${BASE_URL}/hr/performance/reviews`);
  await expect(page.getByTestId("reviews-ready")).toBeVisible({ timeout: 20_000 });
  await expect(page.getByTestId("reviews-self-panel")).toBeVisible();
  const reviews = await context.request.get(`${BASE_URL}/api/platform/api/v2/hr/performance/reviews`, {
    headers: { Authorization: `Bearer ${login.accessToken}` },
  });
  expect(reviews.status()).toBe(200);

  await logoutThroughUi(page);
  await context.close();
});

test("C. manager authenticates and reads canonical direct-report TEAM data", async ({ browser }) => {
  requireRuntimeContract();
  const context = await browser.newContext();
  const page = await context.newPage();
  const login = await loginThroughUi(page, "manager");

  const teamGoals = await context.request.get(
    `${BASE_URL}/api/platform/api/v2/hr/performance/goals/team/${EMPLOYEE_EMPLOYMENT_ID}`,
    { headers: { Authorization: `Bearer ${login.accessToken}` } },
  );
  expect(teamGoals.status()).toBe(200);

  const teamReviews = await context.request.get(`${BASE_URL}/api/platform/api/v2/hr/performance/reviews/team`, {
    headers: { Authorization: `Bearer ${login.accessToken}` },
  });
  expect(teamReviews.status()).toBe(200);

  await page.goto(`${BASE_URL}/hr/performance/reviews`);
  await expect(page.getByTestId("reviews-ready")).toBeVisible({ timeout: 20_000 });
  await expect(page.getByTestId("reviews-team-panel")).toBeVisible();

  await logoutThroughUi(page);
  await context.close();
});

test("D. negative QA principal is denied by backend and UI", async ({ browser }) => {
  requireRuntimeContract();
  const context = await browser.newContext();
  const page = await context.newPage();
  const login = await loginThroughUi(page, "unauthorized");

  const goals = await context.request.get(`${BASE_URL}/api/platform/api/v2/hr/performance/goals`, {
    headers: { Authorization: `Bearer ${login.accessToken}` },
  });
  expect(goals.status()).toBe(403);
  const reviews = await context.request.get(`${BASE_URL}/api/platform/api/v2/hr/performance/reviews`, {
    headers: { Authorization: `Bearer ${login.accessToken}` },
  });
  expect(reviews.status()).toBe(403);

  await page.goto(`${BASE_URL}/hr/performance/goals`);
  await expect(page.getByTestId("goals-capability-denied")).toBeVisible({ timeout: 15_000 });
  await page.goto(`${BASE_URL}/hr/performance/reviews`);
  await expect(page.getByTestId("reviews-capability-denied")).toBeVisible({ timeout: 15_000 });

  await logoutThroughUi(page);
  await context.close();
});
