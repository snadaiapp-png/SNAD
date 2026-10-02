import { expect, type Page } from "@playwright/test";

/**
 * G3 authenticated acceptance — login/session helper.
 *
 * Authenticates through the real SNAD login form and waits for the canonical
 * authenticated workspace before returning. This prevents a race where the
 * login HTTP 200 is observed before AuthProvider/session hydration finishes.
 *
 * FAIL-CLOSED: missing E2E_<ROLE>_EMAIL/PASSWORD throws. No test.skip,
 * no storage injection, no raw-login shortcut, no soft-success fallback.
 */

export interface G3LoginResponse {
  accessToken: string;
  expiresAt?: string;
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
  expect(
    response.ok(),
    `Login failed for ${email}: ${response.status()} ${response.statusText()}`
  ).toBe(true);

  const body = (await response.json()) as G3LoginResponse;
  expect(body.accessToken, `Login response for ${email} is missing accessToken`).toBeTruthy();
  expect(body.user?.tenantId, `Login response for ${email} is missing tenantId`).toBeTruthy();
  expect(
    body.credentialRotationRequired,
    `Login for ${email} unexpectedly requires credential rotation`
  ).not.toBe(true);
  expect(body.user.status, `Login response for ${email} has status ${body.user.status}, expected ACTIVE`).toBe("ACTIVE");

  await page.waitForURL(
    (url) =>
      url.pathname === NORMAL_LOGIN_DESTINATION ||
      url.pathname.startsWith(`${NORMAL_LOGIN_DESTINATION}/`),
    { timeout: 30_000 }
  );

  const expectedIdentity = body.user.displayName || body.user.email;
  await expect(page.getByTestId("workspace-identity")).toContainText(expectedIdentity, {
    timeout: 10_000,
  });

  return body;
}

export async function logoutThroughUi(page: Page): Promise<void> {
  await page.goto(`${process.env.PLAYWRIGHT_BASE_URL ?? "http://127.0.0.1:3001"}/workspace`);
  const logoutBtn = page.getByTestId("logout");
  await expect(logoutBtn).toBeVisible({ timeout: 5_000 });
  await logoutBtn.click();
  await page.waitForURL(
    (url) => url.pathname === "/" || url.pathname.startsWith("/auth"),
    { timeout: 10_000 }
  );
}
