/**
 * G3 Authenticated Acceptance — stateful Employee/Manager/Unauthorized journey.
 *
 * Runs only in playwright-g3.config.ts with workers=1/retries=0 against the
 * dedicated PostgreSQL Direct stack. Required business mutations are driven
 * through product controls. The manager TEAM-goals assertion is an explicit
 * authenticated API-contract check because Task 5 exposes SELF goals only.
 */

import { test, expect } from "@playwright/test";
import { loginThroughUi, logoutThroughUi, roleEmail } from "./g3-auth-session";

const BASE_URL = process.env.PLAYWRIGHT_BASE_URL ?? "http://127.0.0.1:3001";
const EMPLOYEE_EMPLOYMENT_ID = "44444444-4444-4444-8444-444444444481";
const SEEDED_GOAL_ID = "44444444-4444-4444-8444-444444444521";
const TARGET_PROGRESS = 42;

let createdSelfReviewId = "";

test.describe.configure({ mode: "serial" });

function authorizedG3NetworkAudit(page: import("@playwright/test").Page) {
  const unexpected: string[] = [];
  const listener = (response: import("@playwright/test").Response) => {
    const path = new URL(response.url()).pathname;
    if (!path.startsWith("/api/v2/hr/performance/")) return;
    if ([401, 403, 500].includes(response.status())) {
      unexpected.push(`${response.request().method()} ${path} -> ${response.status()}`);
    }
  };
  page.on("response", listener);
  return () => {
    page.off("response", listener);
    expect(unexpected, "authorized G3 journeys must have zero hidden 401/403/500 responses").toEqual([]);
  };
}

test("A. employee views and persists a real goal progress update", async ({ browser }) => {
  const context = await browser.newContext();
  const page = await context.newPage();
  const finishAudit = authorizedG3NetworkAudit(page);

  const login = await loginThroughUi(page, "employee");
  expect(login.user.email).toBe(roleEmail("employee"));

  const initialList = page.waitForResponse(
    (r) => r.request().method() === "GET" && new URL(r.url()).pathname === "/api/v2/hr/performance/goals",
  );
  await page.goto(`${BASE_URL}/hr/performance/goals`);
  await expect(page.getByTestId("goals-ready")).toBeVisible({ timeout: 20_000 });

  const listRes = await initialList;
  expect(listRes.status()).toBe(200);
  const goals = (await listRes.json()) as Array<{ id: string; progress: number; title: string }>;
  expect(goals.some((g) => g.id === SEEDED_GOAL_ID)).toBe(true);

  await page.getByTestId("goals-update-select").selectOption(SEEDED_GOAL_ID);
  await page.getByTestId(`progress-input-${SEEDED_GOAL_ID}`).fill(String(TARGET_PROGRESS));

  const patch = page.waitForResponse(
    (r) =>
      r.request().method() === "PATCH" &&
      new URL(r.url()).pathname === `/api/v2/hr/performance/goals/${SEEDED_GOAL_ID}/progress`,
  );
  await page.getByTestId(`progress-save-${SEEDED_GOAL_ID}`).click();

  const patchRes = await patch;
  expect(patchRes.status()).toBe(200);
  const patchBody = (await patchRes.json()) as { id: string; progress: number };
  expect(patchBody.id).toBe(SEEDED_GOAL_ID);
  expect(patchBody.progress).toBe(TARGET_PROGRESS);
  await expect(page.getByTestId("goals-notice")).toBeVisible({ timeout: 15_000 });

  const persisted = await context.request.get(`${BASE_URL}/api/v2/hr/performance/goals/${SEEDED_GOAL_ID}`);
  expect(persisted.status()).toBe(200);
  const persistedBody = (await persisted.json()) as { progress: number };
  expect(persistedBody.progress).toBe(TARGET_PROGRESS);

  finishAudit();
  await logoutThroughUi(page);
  await context.close();
});

test("B. employee creates, submits, and acknowledges a self review", async ({ browser }) => {
  const context = await browser.newContext();
  const page = await context.newPage();
  const finishAudit = authorizedG3NetworkAudit(page);

  const login = await loginThroughUi(page, "employee");
  expect(login.user.email).toBe(roleEmail("employee"));

  await page.goto(`${BASE_URL}/hr/performance/reviews`);
  await expect(page.getByTestId("reviews-ready")).toBeVisible({ timeout: 20_000 });

  await page.getByTestId("reviews-cycle-input").fill("G3 Acceptance Cycle");
  await page.getByTestId("reviews-period-start-input").fill("2026-10-01");
  await page.getByTestId("reviews-period-end-input").fill("2026-12-31");
  await page.getByTestId("reviews-rating-select").selectOption("4");
  await page.getByTestId("reviews-comments-input").fill("Authenticated G3 acceptance self review");

  const create = page.waitForResponse(
    (r) => r.request().method() === "POST" && new URL(r.url()).pathname === "/api/v2/hr/performance/reviews",
  );
  await page.getByTestId("reviews-create-save").click();
  const createRes = await create;
  expect(createRes.status()).toBe(201);
  const created = (await createRes.json()) as { id: string; source: string; status: string; subjectEmploymentId: string };
  createdSelfReviewId = created.id;
  expect(created.source).toBe("SELF");
  expect(created.status).toBe("DRAFT");
  expect(created.subjectEmploymentId).toBe(EMPLOYEE_EMPLOYMENT_ID);

  await expect(page.getByTestId(`reviews-submit-${createdSelfReviewId}`)).toBeVisible({ timeout: 15_000 });
  const submit = page.waitForResponse(
    (r) => r.request().method() === "POST" && new URL(r.url()).pathname === `/api/v2/hr/performance/reviews/${createdSelfReviewId}/submit`,
  );
  await page.getByTestId(`reviews-submit-${createdSelfReviewId}`).click();
  const submitRes = await submit;
  expect(submitRes.status()).toBe(200);
  expect(((await submitRes.json()) as { status: string }).status).toBe("SUBMITTED");

  await expect(page.getByTestId(`reviews-acknowledge-${createdSelfReviewId}`)).toBeVisible({ timeout: 15_000 });
  const acknowledge = page.waitForResponse(
    (r) => r.request().method() === "POST" && new URL(r.url()).pathname === `/api/v2/hr/performance/reviews/${createdSelfReviewId}/acknowledge`,
  );
  await page.getByTestId(`reviews-acknowledge-${createdSelfReviewId}`).click();
  const acknowledgeRes = await acknowledge;
  expect(acknowledgeRes.status()).toBe(200);
  expect(((await acknowledgeRes.json()) as { status: string }).status).toBe("ACKNOWLEDGED");

  const persisted = await context.request.get(`${BASE_URL}/api/v2/hr/performance/reviews/${createdSelfReviewId}`);
  expect(persisted.status()).toBe(200);
  expect(((await persisted.json()) as { status: string }).status).toBe("ACKNOWLEDGED");

  finishAudit();
  await logoutThroughUi(page);
  await context.close();
});

test("C. manager sees canonical direct-report team goals and reviews", async ({ browser }) => {
  expect(createdSelfReviewId, "serial self-review journey must create a review before TEAM verification").not.toBe("");

  const context = await browser.newContext();
  const page = await context.newPage();
  const finishAudit = authorizedG3NetworkAudit(page);

  const login = await loginThroughUi(page, "manager");
  expect(login.user.email).toBe(roleEmail("manager"));

  // Explicit TEAM-goals API contract: there is no manager TEAM-goals UI in Task 5.
  const teamGoals = await context.request.get(
    `${BASE_URL}/api/v2/hr/performance/goals/team/${EMPLOYEE_EMPLOYMENT_ID}`,
  );
  expect(teamGoals.status()).toBe(200);
  const teamGoalsBody = (await teamGoals.json()) as Array<{ id: string; employmentId: string; progress: number }>;
  expect(teamGoalsBody.some((g) => g.id === SEEDED_GOAL_ID && g.employmentId === EMPLOYEE_EMPLOYMENT_ID)).toBe(true);
  expect(teamGoalsBody.find((g) => g.id === SEEDED_GOAL_ID)?.progress).toBe(TARGET_PROGRESS);

  const teamReviewsResponse = page.waitForResponse(
    (r) => r.request().method() === "GET" && new URL(r.url()).pathname === "/api/v2/hr/performance/reviews/team",
  );
  await page.goto(`${BASE_URL}/hr/performance/reviews`);
  await expect(page.getByTestId("reviews-ready")).toBeVisible({ timeout: 20_000 });
  await expect(page.getByTestId("reviews-team-panel")).toBeVisible();

  const teamReviewsRes = await teamReviewsResponse;
  expect(teamReviewsRes.status()).toBe(200);
  const teamReviews = (await teamReviewsRes.json()) as Array<{ id: string; subjectEmploymentId: string; status: string }>;
  expect(
    teamReviews.some(
      (review) =>
        review.id === createdSelfReviewId &&
        review.subjectEmploymentId === EMPLOYEE_EMPLOYMENT_ID &&
        review.status === "ACKNOWLEDGED",
    ),
  ).toBe(true);

  finishAudit();
  await logoutThroughUi(page);
  await context.close();
});

test("D. unauthorized principal is denied by backend with explicit 403", async ({ browser }) => {
  const context = await browser.newContext();
  const page = await context.newPage();

  const login = await loginThroughUi(page, "unauthorized");
  expect(login.user.email).toBe(roleEmail("unauthorized"));

  // Backend authorization proof using the authenticated browser context cookies.
  const forbidden = await context.request.get(`${BASE_URL}/api/v2/hr/performance/goals`);
  expect(forbidden.status(), "missing GOAL.SELF_VIEW must be a real backend 403").toBe(403);

  await page.goto(`${BASE_URL}/hr/performance/goals`);
  await expect(page.getByTestId("goals-capability-denied")).toBeVisible({ timeout: 10_000 });
  await expect(page.getByTestId("goals-ready")).not.toBeVisible();

  await logoutThroughUi(page);
  await context.close();
});
