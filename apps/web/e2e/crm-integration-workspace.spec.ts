import { expect, test, type Page, type Route } from "@playwright/test";
import { loginThroughUi, type CrmLoginResponse } from "./crm-auth-session";

const EMAIL = "crm-integration-ui@example.test";
const PASSWORD = "crm-integration-ui-password";
const ENTITY_ID = "11111111-1111-4111-8111-111111111111";
const TENANT_ID = "33333333-3333-4333-8333-333333333333";
const USER_ID = "44444444-4444-4444-8444-444444444444";

interface IntegrationState {
  id: string;
  tenantId: string;
  actorId: string;
  integrationType: "AI" | "WORKFLOW";
  status: string;
  externalReference: string | null;
  correlationId: string;
  idempotencyKey: string;
  sourceEntityType: string;
  sourceEntityId: string;
  sourceEntityVersion: number;
  payload: Record<string, unknown>;
  resultPayload: Record<string, unknown> | null;
  requestedAt: string;
  expiresAt: string;
  errorCode: string | null;
  version: number;
}

async function fulfillJson(route: Route, body: unknown, status = 200, headers?: Record<string, string>): Promise<void> {
  await route.fulfill({
    status,
    contentType: "application/json",
    headers,
    body: JSON.stringify(body),
  });
}

async function mockAuthApi(page: Page): Promise<void> {
  let loggedIn = false;
  const loginResponse: CrmLoginResponse = {
    accessToken: "crm-integration-ui-access-token",
    expiresAt: new Date(Date.now() + 15 * 60_000).toISOString(),
    credentialRotationRequired: false,
    defaultDestination: "/crm/overview",
    availableDestinations: ["/crm", "/workspace"],
    user: {
      id: USER_ID,
      tenantId: TENANT_ID,
      email: EMAIL,
      displayName: "CRM Integration UI",
      status: "ACTIVE",
    },
  };
  const cookie = "sanad_refresh=crm-integration-ui-refresh; Path=/; HttpOnly; SameSite=Lax";

  await page.route("**/api/platform/api/v1/auth/**", async (route) => {
    const request = route.request();
    const path = new URL(request.url()).pathname;
    if (path.endsWith("/auth/login") && request.method() === "POST") {
      loggedIn = true;
      await fulfillJson(route, loginResponse, 200, { "set-cookie": cookie });
      return;
    }
    if (path.endsWith("/auth/refresh") && request.method() === "POST") {
      if (!loggedIn) {
        await fulfillJson(route, { errorCode: "UNAUTHENTICATED" }, 401);
        return;
      }
      await fulfillJson(route, loginResponse, 200, { "set-cookie": cookie });
      return;
    }
    if (path.endsWith("/auth/logout")) {
      loggedIn = false;
      await fulfillJson(route, {}, 204);
      return;
    }
    await fulfillJson(route, loggedIn ? loginResponse.user : { errorCode: "UNAUTHENTICATED" }, loggedIn ? 200 : 401);
  });
}

async function mockIntegrationApi(page: Page): Promise<{ current: IntegrationState }> {
  const now = new Date();
  const state: { current: IntegrationState } = {
    current: {
      id: "22222222-2222-4222-8222-222222222222",
      tenantId: TENANT_ID,
      actorId: USER_ID,
      integrationType: "AI",
      status: "RECOMMENDATION_AVAILABLE",
      externalReference: null,
      correlationId: "corr-workspace",
      idempotencyKey: "idem-workspace",
      sourceEntityType: "ACCOUNT",
      sourceEntityId: ENTITY_ID,
      sourceEntityVersion: 0,
      payload: { capability: "CUSTOMER_SUMMARY" },
      resultPayload: {
        generatedText: "Healthy customer with an expansion opportunity.",
        actionCode: "CREATE_FOLLOW_UP_ACTIVITY",
        confidence: 0.91,
        explanation: "Recent engagement and open opportunity signals.",
        sourceReferences: ["crm://account/111", "crm://opportunity/222"],
      },
      requestedAt: now.toISOString(),
      expiresAt: new Date(now.getTime() + 60_000).toISOString(),
      errorCode: null,
      version: 1,
    },
  };

  await page.route("**/api/platform/api/v2/crm/integrations**", async (route) => {
    const request = route.request();
    const path = new URL(request.url()).pathname;

    if (request.method() === "POST" && path.endsWith("/integrations/ai")) {
      await fulfillJson(route, state.current, 202);
      return;
    }
    if (request.method() === "POST" && path.endsWith("/confirm")) {
      expect(request.headers()["if-match"]).toBe('"1"');
      state.current = { ...state.current, status: "EXECUTING", version: 2 };
      await fulfillJson(route, state.current);
      return;
    }
    if (request.method() === "POST" && path.endsWith("/reject")) {
      expect(request.headers()["if-match"]).toBe('"1"');
      state.current = { ...state.current, status: "REJECTED", version: 2 };
      await fulfillJson(route, state.current);
      return;
    }
    if (request.method() === "POST" && path.endsWith("/integrations/workflows")) {
      state.current = {
        ...state.current,
        id: "55555555-5555-4555-8555-555555555555",
        integrationType: "WORKFLOW",
        status: "ACCEPTED",
        externalReference: "66666666-6666-4666-8666-666666666666",
        payload: { workflowType: "ASSIGNMENT" },
        resultPayload: null,
        version: 1,
      };
      await fulfillJson(route, state.current, 202);
      return;
    }
    if (request.method() === "POST" && path.endsWith("/cancel")) {
      expect(request.headers()["if-match"]).toBe('"1"');
      state.current = { ...state.current, status: "CANCELLED", version: 2 };
      await fulfillJson(route, state.current);
      return;
    }
    if (request.method() === "GET") {
      await fulfillJson(route, state.current);
      return;
    }
    await fulfillJson(route, { errorCode: "UNEXPECTED_TEST_REQUEST" }, 500);
  });
  return state;
}

async function openWorkspace(page: Page, locale: "ar" | "en"): Promise<void> {
  await mockAuthApi(page);
  await loginThroughUi(page, EMAIL, PASSWORD);
  await page.evaluate((nextLocale) => window.localStorage.setItem("snad.locale", nextLocale), locale);
  await page.goto("/crm/integrations");
  await page.waitForSelector("#crm-operational-content", { timeout: 30_000 });
}

test.describe("CRM Workflow & AI integration workspace", () => {
  test("renders all governed panels in Arabic RTL", async ({ page }) => {
    await mockIntegrationApi(page);
    await openWorkspace(page, "ar");

    await expect(page.locator("html")).toHaveAttribute("dir", "rtl");
    await expect(page.getByRole("heading", { name: "تكاملات سير العمل والذكاء الاصطناعي" })).toBeVisible();
    for (const heading of [
      "ملخص العميل",
      "أفضل إجراء تالٍ",
      "تفسير التقييم",
      "حالة التنفيذ",
      "حالة سير العمل",
      "الخط الزمني للموافقة",
      "حالة التذكير / التصعيد",
      "مراجع الأدلة",
    ]) {
      await expect(page.getByRole("heading", { name: heading })).toBeVisible();
    }
  });

  test("requests AI and confirms through If-Match dialog in English", async ({ page }) => {
    const state = await mockIntegrationApi(page);
    await openWorkspace(page, "en");

    await expect(page.locator("html")).toHaveAttribute("dir", "ltr");
    await page.getByLabel("Entity ID").fill(ENTITY_ID);
    await page.getByLabel("Entity version").fill("0");
    await page.getByRole("button", { name: "Request AI insight" }).click();

    await expect(page.getByText("Healthy customer with an expansion opportunity.")).toBeVisible();
    await expect(page.getByText("CREATE_FOLLOW_UP_ACTIVITY")).toBeVisible();
    await page.getByRole("button", { name: "Confirm recommendation" }).click();
    const dialog = page.getByRole("dialog");
    await expect(dialog).toBeVisible();
    await expect(dialog.getByRole("heading", { name: "Execute this recommendation?" })).toBeFocused();
    await dialog.getByRole("button", { name: "Confirm recommendation" }).click();
    await expect(page.getByText("EXECUTING", { exact: true }).first()).toBeVisible();
    expect(state.current.status).toBe("EXECUTING");
  });

  test("dispatches and cancels a workflow using only the same-origin BFF", async ({ page }) => {
    const directBackendRequests: string[] = [];
    page.on("request", (request) => {
      const url = new URL(request.url());
      if (url.pathname.includes("/api/v2/crm/") && !url.pathname.startsWith("/api/platform/")) {
        directBackendRequests.push(request.url());
      }
    });
    const state = await mockIntegrationApi(page);
    await openWorkspace(page, "en");

    await page.getByLabel("Entity ID").fill(ENTITY_ID);
    await page.getByRole("button", { name: "Dispatch workflow" }).click();
    await expect(page.getByText("ACCEPTED", { exact: true }).first()).toBeVisible();
    await page.getByRole("button", { name: "Cancel workflow" }).click();
    await expect(page.getByText("CANCELLED", { exact: true }).first()).toBeVisible();
    expect(state.current.status).toBe("CANCELLED");
    expect(directBackendRequests).toEqual([]);
  });

  test("decision dialog closes with Escape and retains keyboard focusability", async ({ page }) => {
    await mockIntegrationApi(page);
    await openWorkspace(page, "en");
    await page.getByLabel("Entity ID").fill(ENTITY_ID);
    await page.getByRole("button", { name: "Request AI insight" }).click();
    await page.getByRole("button", { name: "Reject recommendation" }).click();
    await expect(page.getByRole("dialog")).toBeVisible();
    await page.keyboard.press("Escape");
    await expect(page.getByRole("dialog")).toHaveCount(0);
  });
});
