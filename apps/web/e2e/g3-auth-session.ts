import { expect, type Page } from "@playwright/test";

/**
 * G3 authenticated acceptance — login helper.
 *
 * Authenticates through the real SNAD login form so AuthProvider owns
 * the in-memory access token and the BFF refresh cookie is stored in this
 * exact browser context. Direct API login alone cannot authenticate the SPA
 * because access tokens are intentionally never persisted in browser storage.
 *
 * Mirrors the canonical G3 `g2-auth-session.ts` pattern.
 *
 * FAIL-CLOSED contract: throws when E2E_<ROLE>_EMAIL / E2E_<ROLE>_PASSWORD
 * env vars are missing. NO test.skip, NO Assumptions, NO soft-success
 * fallback. The dedicated G3 CI workflow MUST provision these env vars;
 * missing credentials fail the G3 job (not skip).
 */

export interface G3LoginResponse {
  accessToken: string;
  user: {
    id: string;
    tenantId: string;
    email: string;
    displayName: string | null;
    status: string;
  };
}

function requireCredentials(role: "employee" | "manager" | "unauthorized"): {
  email: string;
  password: string;
} {
  const email = process.env[`E2E_${role.toUpperCase()}_EMAIL`];
  const password = process.env[`E2E_${role.toUpperCase()}_PASSWORD`];
  if (!email || !password) {
    throw new Error(
      `G3 E2E FAIL-CLOSED: E2E_${role.toUpperCase()}_EMAIL and E2E_${role.toUpperCase()}_PASSWORD must be provisioned`
    );
  }
  return { email, password };
}

export function roleEmail(role: "employee" | "manager" | "unauthorized"): string {
  return requireCredentials(role).email;
}

export async function loginThroughUi(
  page: Page,
  role: "employee" | "manager" | "unauthorized"
): Promise<G3LoginResponse> {
  const { email, password } = requireCredentials(role);

  await page.addInitScript(() => {
    if (!localStorage.getItem("snad.locale")) {
      localStorage.setItem("snad.locale", "en");
    }
  });

  await page.goto("/");
  await page.locator("#login-email").fill(email);
  await page.locator("#login-password").fill(password);

  const responsePromise = page.waitForResponse(
    (response) =>
      response.request().method() === "POST" &&
      response.url().includes("/api/platform/api/v1/auth/login"),
    { timeout: 30_000 }
  );

  await page.locator('form button[type="submit"]').click();
  const response = await responsePromise;
  expect(response.ok(), `Login failed for ${email}: ${response.status()} ${response.statusText()}`).toBe(true);

  const body = (await response.json()) as G3LoginResponse;
  expect(body.accessToken, `Login response for ${email} is missing accessToken`).toBeTruthy();
  expect(body.user.tenantId, `Login response for ${email} is missing tenantId`).toBeTruthy();
  expect(body.user.status, `Login response for ${email} has status ${body.user.status}, expected ACTIVE`).toBe("ACTIVE");

  return body;
}

export async function logoutThroughUi(page: Page): Promise<void> {
  await page.goto("/");
  await page.locator("#login-email").fill("");
  await page.locator("#login-password").fill("");
}
