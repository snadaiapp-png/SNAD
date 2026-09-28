import { expect, test, type Page } from "@playwright/test";
import { loginThroughUi } from "./crm-auth-session";

const TENANT_EMAIL = process.env.USERS_TENANT_EMAIL ?? "";
const TENANT_PASSWORD = process.env.USERS_TENANT_PASSWORD ?? "";
const TENANT_ID = process.env.USERS_TENANT_ID ?? "";
const CROSS_TENANT_ID = process.env.USERS_CROSS_TENANT_ID ?? "";
const CONTROL_EMAIL = process.env.USERS_CONTROL_EMAIL ?? "";
const CONTROL_PASSWORD = process.env.USERS_CONTROL_PASSWORD ?? "";
const CONTROL_TENANT_ID = process.env.USERS_CONTROL_TENANT_ID ?? "00000000-0000-0000-0000-000000000001";

function requireEnv(name: string, value: string) {
  expect(value, `${name} must be configured by the users-module closure workflow`).toBeTruthy();
}

async function assertAuthenticatedIdentity(page: Page, expectedTenantId: string, accessToken: string) {
  const response = await page.request.get("/api/platform/api/v1/auth/me", {
    headers: { Authorization: `Bearer ${accessToken}` },
  });
  expect(response.ok(), `auth/me failed: ${response.status()} ${response.statusText()}`).toBe(true);
  const body = await response.json() as { tenantId?: string; user?: { tenantId?: string } };
  const tenantId = body.tenantId ?? body.user?.tenantId;
  expect(tenantId).toBe(expectedTenantId);
}

test.describe.configure({ mode: "serial" });

test("tenant users/access renders real data and stays fail-closed", async ({ page }) => {
  requireEnv("USERS_TENANT_EMAIL", TENANT_EMAIL);
  requireEnv("USERS_TENANT_PASSWORD", TENANT_PASSWORD);
  requireEnv("USERS_TENANT_ID", TENANT_ID);
  requireEnv("USERS_CROSS_TENANT_ID", CROSS_TENANT_ID);

  const session = await loginThroughUi(page, TENANT_EMAIL, TENANT_PASSWORD);
  expect(session.user.tenantId).toBe(TENANT_ID);

  await page.goto("/management/users");
  await expect(page.getByTestId("management-users-ready")).toBeVisible();
  const rows = page.locator('[data-testid^="tenant-user-"]');
  await expect(rows.first()).toBeVisible();
  expect(await rows.count()).toBeGreaterThan(0);

  const firstUserLink = rows.first().locator('a[href^="/management/users/"]');
  const detailHref = await firstUserLink.getAttribute("href");
  expect(detailHref).toMatch(/^\/management\/users\/[0-9a-f-]{36}$/i);
  await firstUserLink.click();
  await expect(page.getByTestId("management-user-detail-ready")).toBeVisible();
  await expect(page.locator("#user-memberships-heading")).toBeVisible();
  await expect(page.locator("#user-roles-heading")).toBeVisible();

  await page.goto("/management/access");
  await expect(page.getByTestId("management-access-ready")).toBeVisible();
  await expect(page.locator("#tenant-roles-heading")).toBeVisible();
  await expect(page.locator("#capability-registry-heading")).toBeVisible();
  await expect(page.locator("#tenant-roles-heading").locator("xpath=following::table[1] tbody tr").first()).toBeVisible();
  await expect(page.locator("#capability-registry-heading").locator("xpath=following::ul[1] li").first()).toBeVisible();

  const forbiddenMutation = await page.request.post(
    `/api/platform/api/v1/access/roles?tenantId=${encodeURIComponent(TENANT_ID)}`,
    {
      headers: { Authorization: `Bearer ${session.accessToken}` },
      data: { code: "E2E_FORBIDDEN_ROLE", name: "E2E forbidden role", description: null },
    },
  );
  expect([403, 404]).toContain(forbiddenMutation.status());

  const crossTenantRead = await page.request.get(
    `/api/platform/api/v1/users?tenantId=${encodeURIComponent(CROSS_TENANT_ID)}`,
    { headers: { Authorization: `Bearer ${session.accessToken}` } },
  );
  expect([403, 404]).toContain(crossTenantRead.status());

  await page.goto("/management/users");
  await expect(page.getByTestId("management-users-ready")).toBeVisible();
  await assertAuthenticatedIdentity(page, TENANT_ID, session.accessToken);
});

test("control-plane users/access renders only platform identities", async ({ page }) => {
  requireEnv("USERS_CONTROL_EMAIL", CONTROL_EMAIL);
  requireEnv("USERS_CONTROL_PASSWORD", CONTROL_PASSWORD);
  requireEnv("USERS_TENANT_EMAIL", TENANT_EMAIL);

  const session = await loginThroughUi(page, CONTROL_EMAIL, CONTROL_PASSWORD);
  expect(session.user.tenantId).toBe(CONTROL_TENANT_ID);

  await page.goto("/executive/users");
  await expect(page.getByTestId("executive-users-ready")).toBeVisible();
  await expect(page.getByText(CONTROL_EMAIL, { exact: true })).toBeVisible();
  await expect(page.getByText(TENANT_EMAIL, { exact: true })).toHaveCount(0);

  const ownerCard = page.getByText(CONTROL_EMAIL, { exact: true }).locator("xpath=ancestor::article[1]");
  const ownerDetailLink = ownerCard.locator('a[href^="/executive/users/"]');
  await expect(ownerDetailLink).toBeVisible();
  await ownerDetailLink.click();
  await expect(page.getByTestId("executive-user-detail-ready")).toBeVisible();

  await page.goto("/executive/access");
  await expect(page.getByText("PLATFORM_OWNER", { exact: true })).toBeVisible();
  await expect(page.getByText("PLATFORM.USER.READ", { exact: true })).toBeVisible();

  await assertAuthenticatedIdentity(page, CONTROL_TENANT_ID, session.accessToken);
});
