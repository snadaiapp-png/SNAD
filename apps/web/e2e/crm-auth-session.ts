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

function configuredTenantId(email: string): string | undefined {
  const normalized = email.trim().toLowerCase();
  const tenantAEmail = (process.env.CRM_TENANT_A_EMAIL ?? "").trim().toLowerCase();
  const tenantBEmail = (process.env.CRM_TENANT_B_EMAIL ?? "").trim().toLowerCase();

  if (tenantAEmail && normalized === tenantAEmail) {
    return process.env.CRM_TENANT_A_ID || undefined;
  }
  if (tenantBEmail && normalized === tenantBEmail) {
    return process.env.CRM_TENANT_B_ID || undefined;
  }
  return undefined;
}

/**
 * Authenticate through the real SNAD login form so AuthProvider owns the
 * in-memory access token and the BFF refresh cookie is stored in this exact
 * browser context. Direct API login alone cannot authenticate the SPA because
 * access tokens are intentionally never persisted in browser storage.
 *
 * Production closure may provide CRM_TENANT_A_ID / CRM_TENANT_B_ID. When an
 * email exists in more than one tenant, the helper injects the proven tenantId
 * into the real form request without persisting the identifier in browser
 * storage or changing the production login UI.
 */
export async function loginThroughUi(
  page: Page,
  email: string,
  password: string,
): Promise<CrmLoginResponse> {
  expect(email, "CRM login email must be configured").toBeTruthy();
  expect(password, "CRM login password must be configured").toBeTruthy();

  const tenantId = configuredTenantId(email);
  if (tenantId) {
    await page.route(
      "**/api/platform/api/v1/auth/login",
      async (route) => {
        const request = route.request();
        if (request.method() !== "POST") {
          await route.continue();
          return;
        }

        const requestBody = request.postDataJSON() as Record<string, unknown> | null;
        await route.continue({
          headers: {
            ...request.headers(),
            "content-type": "application/json",
          },
          postData: JSON.stringify({
            ...(requestBody ?? {}),
            tenantId,
          }),
        });
      },
      { times: 1 },
    );
  }

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
  if (tenantId) {
    expect(body.user.tenantId, `Login for ${email} resolved to an unexpected tenant`).toBe(tenantId);
  }
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

  await page.waitForLoadState("networkidle", { timeout: 30_000 });
  await page.waitForSelector(
    'main, [data-auth-state="authenticated"], #workspace-content, #crm-operational-content, #crm-command-center-content',
    { timeout: 30_000 },
  ).catch(() => {
    // The authenticated shell may still be settling; the second network-idle
    // gate below remains authoritative.
  });
  await page.waitForLoadState("networkidle", { timeout: 15_000 });

  const refreshCookie = (await page.context().cookies()).find((cookie) => cookie.name === "sanad_refresh");
  expect(refreshCookie, `Refresh cookie was not created for ${email}`).toBeTruthy();

  return body;
}
