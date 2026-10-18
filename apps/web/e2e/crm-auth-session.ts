import { expect, type Page, type Response } from "@playwright/test";

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

const NORMAL_LOGIN_DESTINATION = "/workspace";

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
  // Authenticated Users closure fixtures may reuse an email across tenants.
  // Pass the fixture's verified tenant explicitly; do not weaken backend
  // ambiguity checks or accept a tenant selected by an untrusted response.
  const crmTenantAFixtures = [
    "CRM_READONLY_EMAIL", "CRM_LEAD_WRITER_EMAIL", "CRM_IMPORT_READER_EMAIL",
  ] as const;
  for (const key of crmTenantAFixtures) {
    const fixtureEmail = (process.env[key] ?? "").trim().toLowerCase();
    if (fixtureEmail && normalized === fixtureEmail) {
      return process.env.CRM_TENANT_A_ID || undefined;
    }
  }
  const usersFixtures = [
    ["USERS_CONTROL_EMAIL", "USERS_CONTROL_TENANT_ID"],
    ["USERS_PHASE8_ADMIN_EMAIL", "USERS_TENANT_ID"],
    ["USERS_TENANT_EMAIL", "USERS_TENANT_ID"],
  ] as const;
  for (const [emailKey, tenantKey] of usersFixtures) {
    const fixtureEmail = (process.env[emailKey] ?? "").trim().toLowerCase();
    if (fixtureEmail && normalized === fixtureEmail) {
      return process.env[tenantKey] || undefined;
    }
  }
  return undefined;
}

/**
 * Authenticate through the real SNAD login form so AuthProvider owns the
 * in-memory access token and the BFF refresh cookie is stored in this exact
 * browser context. Direct API login alone cannot authenticate the SPA because
 * access tokens are intentionally never persisted in browser storage.
 *
 * Normal login lands on /workspace. CRM specs navigate explicitly to their
 * target route after authentication, while dedicated returnUrl tests cover
 * authorized deep-link behavior separately.
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
  options?: { sessionCookieEcho?: boolean },
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

  // Runner resilience (PR 1414 CI, runs 39069551098 / 39078138284): Chrome
  // 155 on the ubuntu runner intermittently aborts the login fetch with
  // net::ERR_ABORTED (trace 0-trace.network shows the POST with status -1)
  // while the SPA's own post-submit router prefetch/replace traffic races the
  // request. The app stays on the login form after a failed fetch, so the
  // submit is retried until the login POST actually answers. Every downstream
  // contract assertion below is unchanged.
  const loginResponsePredicate = (response: { request: () => { method: () => string }; url: () => string }) =>
    response.request().method() === "POST" &&
    response.url().includes("/api/platform/api/v1/auth/login");

  let response: Response | null = null;
  for (let attempt = 0; attempt < 3 && !response; attempt++) {
    // Re-fill both fields each attempt: a runner-side navigation race can
    // leave the form freshly rendered (and empty) between submits.
    await page.locator("#login-email").fill(email);
    await page.locator("#login-password").fill(password);
    const responsePromise = page.waitForResponse(loginResponsePredicate, { timeout: 15_000 });
    await page.locator('form button[type="submit"]').click();
    response = await responsePromise.catch(() => null);
  }
  expect(
    response,
    `Login POST for ${email} never received a response (all 3 submit attempts were aborted)`,
  ).toBeTruthy();
  expect(response!.ok(), `Login failed for ${email}: ${response!.status()} ${response!.statusText()}`).toBe(true);

  const body = (await response!.json()) as CrmLoginResponse;
  expect(body.accessToken, `Login response for ${email} is missing accessToken`).toBeTruthy();
  expect(body.user?.tenantId, `Login response for ${email} is missing tenantId`).toBeTruthy();
  if (tenantId) {
    expect(body.user.tenantId, `Login for ${email} resolved to an unexpected tenant`).toBe(tenantId);
  }
  expect(body.credentialRotationRequired, `Login for ${email} unexpectedly requires credential rotation`).not.toBe(true);

  const destination = NORMAL_LOGIN_DESTINATION;
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
    'main, [data-auth-state="authenticated"], #workspace-content, #crm-operational-content',
    { timeout: 30_000 },
  ).catch(() => {
    // The authenticated shell may still be settling; the second network-idle
    // gate below remains authoritative.
  });
  await page.waitForLoadState("networkidle", { timeout: 15_000 });

  // Session-cookie contract check. Two evidence paths:
  //
  // 1. REAL-BFF flows (default): the sanad_refresh cookie must be present in
  //    the browser cookie jar after login. The real backend delivers
  //    Set-Cookie with the login response (AuthController authResponse) and
  //    the jar read is reliable there.
  //
  // 2. MOCKED-BFF flows (options.sessionCookieEcho): the jar read via
  //    browserContext.cookies() is UNRELIABLE on the runner's Chrome
  //    155.0.8059.39 — CI run 39081882390 traces prove the cookie IS in the
  //    jar and IS sent on every request (including the login POST) while the
  //    CDP read returns an empty jar, and one attempt hung until the 60s
  //    test timeout. The contract is therefore verified through the REQUEST
  //    path instead: a mocked echo endpoint returns whether the browser
  //    attached sanad_refresh to an actual credentialed request — the exact
  //    surface the real BFF consumes. Playwright strips set-cookie from
  //    response headers, so no response-side read exists.
  if (options?.sessionCookieEcho) {
    const echo = await page.evaluate(async () => {
      const res = await fetch("/api/platform/api/v1/auth/session-cookie-echo", {
        method: "POST",
        credentials: "include",
      });
      return (await res.json()) as { hasSessionCookie: boolean };
    });
    expect(
      echo.hasSessionCookie,
      `Browser requests for ${email} do not carry the sanad_refresh session cookie`,
    ).toBe(true);
  } else {
    const refreshCookie = (await page.context().cookies()).find((cookie) => cookie.name === "sanad_refresh");
    expect(refreshCookie, `Refresh cookie was not created for ${email}`).toBeTruthy();
  }

  return body;
}
