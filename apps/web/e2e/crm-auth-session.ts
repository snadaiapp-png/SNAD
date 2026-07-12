import { expect, type Page } from "@playwright/test";

export interface CrmLoginResponse {
  accessToken: string;
  expiresAt: string;
  user: {
    id: string;
    tenantId: string;
    email: string;
    displayName: string | null;
    status: string;
  };
}

/**
 * Authenticate through the real SNAD login form so AuthProvider owns the
 * in-memory access token and the BFF refresh cookie is stored in this exact
 * browser context. Direct API login alone cannot authenticate the SPA because
 * access tokens are intentionally never persisted in browser storage.
 */
export async function loginThroughUi(
  page: Page,
  email: string,
  password: string,
): Promise<CrmLoginResponse> {
  expect(email, "CRM login email must be configured").toBeTruthy();
  expect(password, "CRM login password must be configured").toBeTruthy();

  await page.goto("/");
  await page.locator("#login-email").fill(email);
  await page.locator("#login-password").fill(password);

  const responsePromise = page.waitForResponse(
    (response) =>
      response.request().method() === "POST" &&
      response.url().includes("/api/platform/api/v1/auth/login"),
    { timeout: 30_000 },
  );

  await page.locator('form button[type="submit"]').click();
  const response = await responsePromise;
  expect(response.ok(), `Login failed for ${email}: ${response.status()} ${response.statusText()}`).toBe(true);

  const body = (await response.json()) as CrmLoginResponse;
  expect(body.accessToken, `Login response for ${email} is missing accessToken`).toBeTruthy();
  expect(body.user?.tenantId, `Login response for ${email} is missing tenantId`).toBeTruthy();

  await page.waitForURL(/\/workspace(?:[/?#]|$)/, { timeout: 30_000 });
  const refreshCookie = (await page.context().cookies()).find((cookie) => cookie.name === "sanad_refresh");
  expect(refreshCookie, `Refresh cookie was not created for ${email}`).toBeTruthy();

  return body;
}
