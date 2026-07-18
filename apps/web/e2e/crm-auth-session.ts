import { expect, type Page } from "@playwright/test";

export interface CrmLoginResponse {
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

const AUTHENTICATED_DESTINATIONS = [
  "/workspace",
  "/crm",
  "/crm/command-center",
  "/control-plane",
] as const;

function expectedDestination(body: CrmLoginResponse): string {
  const destination = body.defaultDestination;
  if (
    typeof destination === "string"
    && AUTHENTICATED_DESTINATIONS.some(
      (root) => destination === root || destination.startsWith(`${root}/`),
    )
  ) {
    return destination;
  }
  return "/workspace";
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
  expect(body.credentialRotationRequired, `Login for ${email} unexpectedly requires credential rotation`).not.toBe(true);

  const destination = expectedDestination(body);
  if (Array.isArray(body.availableDestinations)) {
    expect(
      body.availableDestinations.some(
        (root) => destination === root || destination.startsWith(`${root}/`),
      ),
      `Bootstrap destination ${destination} is not included in availableDestinations`,
    ).toBe(true);
  }

  await page.waitForURL(
    (url) => url.pathname === destination || url.pathname.startsWith(`${destination}/`),
    { timeout: 30_000 },
  );

  const refreshCookie = (await page.context().cookies()).find((cookie) => cookie.name === "sanad_refresh");
  expect(refreshCookie, `Refresh cookie was not created for ${email}`).toBeTruthy();

  return body;
}
