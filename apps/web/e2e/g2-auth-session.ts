import { expect, type Page } from "@playwright/test";

/**
 * G2 authenticated acceptance — login helper.
 *
 * Authenticates through the real SNAD login form so AuthProvider owns the
 * in-memory access token and the BFF refresh cookie is stored in this exact
 * browser context. Direct API login alone cannot authenticate the SPA
 * because access tokens are intentionally never persisted in browser storage.
 *
 * Mirrors the canonical CRM `crm-auth-session.ts` pattern.
 *
 * FAIL-CLOSED contract: throws when E2E_<ROLE>_EMAIL / E2E_<ROLE>_PASSWORD
 * env vars are missing. NO test.skip, NO Assumptions, NO soft-success
 * fallback. The dedicated G2 CI workflow MUST provision these env vars;
 * missing credentials fail the G2 job (not skip).
 */

export interface G2LoginResponse {
  accessToken: string;
  expiresAt: string;
  credentialRotationRequired?: boolean;
  defaultDestination?: string;
  availableDestinations?: string[];
  user: {
    id: string;
    tenantId: string;
    email: string;
    displayName: string | null;
    status: string;
  };
}

const NORMAL_LOGIN_DESTINATION = "/workspace";

function requireCredentials(role: "employee" | "manager" | "hr"): {
  email: string;
  password: string;
} {
  const email = process.env[`E2E_${role.toUpperCase()}_EMAIL`];
  const password = process.env[`E2E_${role.toUpperCase()}_PASSWORD`];
  if (!email || !password) {
    throw new Error(
      `G2 E2E FAIL-CLOSED: E2E_${role.toUpperCase()}_EMAIL and E2E_${role.toUpperCase()}_PASSWORD must be provisioned`
    );
  }
  return { email, password };
}

/**
 * Authenticate through the real SNAD login form. Verifies:
 *   - Login response is HTTP 200
 *   - accessToken is present
 *   - tenantId is present
 *   - credentialRotationRequired is NOT true
 *   - Browser URL lands on /workspace
 *
 * Returns the parsed login response for downstream assertions.
 */
export async function loginThroughUi(
  page: Page,
  role: "employee" | "manager" | "hr"
): Promise<G2LoginResponse> {
  const { email, password } = requireCredentials(role);

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
  expect(
    response.ok(),
    `Login failed for ${email}: ${response.status()} ${response.statusText()}`
  ).toBe(true);

  const body = (await response.json()) as G2LoginResponse;
  expect(
    body.accessToken,
    `Login response for ${email} is missing accessToken`
  ).toBeTruthy();
  expect(
    body.user?.tenantId,
    `Login response for ${email} is missing tenantId`
  ).toBeTruthy();
  expect(
    body.credentialRotationRequired,
    `Login for ${email} unexpectedly requires credential rotation`
  ).not.toBe(true);

  await page.waitForURL(
    (url) =>
      url.pathname === NORMAL_LOGIN_DESTINATION ||
      url.pathname.startsWith(`${NORMAL_LOGIN_DESTINATION}/`),
    { timeout: 30_000 }
  );

  return body;
}

/**
 * Log out the current session via the workspace UI logout button.
 *
 * Fail-closed contract: if the logout button cannot be located within a
 * short deterministic timeout, the helper fails the test rather than
 * silently falling back to clearing storage. The directive §6 forbids
 * swallowed errors / soft fallback in required G2 certification paths.
 *
 * The implementation uses an explicit locator assertion (not isVisible().catch())
 * so a missing logout button surfaces as a Playwright timeout failure
 * (deterministic), not a silent skip.
 */
export async function logoutThroughUi(page: Page): Promise<void> {
  // Deterministic locator — covers the canonical workspace logout affordance.
  // The 5s timeout is generous enough for slow CI runners but bounded so a
  // missing logout button fails the test rather than hanging indefinitely.
  const logoutBtn = page.locator(
    'button:has-text("Logout"), button:has-text("Sign out"), button:has-text("Log out"), [data-testid="logout"]'
  );
  await logoutBtn.first().click({ timeout: 5_000 });
  // Wait for navigation back to an auth surface (login or auth root).
  await page.waitForURL(/\/auth/, { timeout: 10_000 });
}

/**
 * Configuration access — exposes env-var-resolved emails for spec assertions
 * (e.g. verifying the logged-in user matches the expected identity).
 */
export function roleEmail(role: "employee" | "manager" | "hr"): string {
  return requireCredentials(role).email;
}
