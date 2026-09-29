import { expect, test } from "@playwright/test";
import { loginThroughUi } from "./crm-auth-session";
import { captureUsersEvidence } from "./users-module-visual-helpers";

const TENANT_EMAIL = process.env.USERS_TENANT_EMAIL ?? "";
const TENANT_PASSWORD = process.env.USERS_TENANT_PASSWORD ?? "";
const TENANT_ID = process.env.USERS_TENANT_ID ?? "";
const CONTROL_EMAIL = process.env.USERS_CONTROL_EMAIL ?? "";
const CONTROL_PASSWORD = process.env.USERS_CONTROL_PASSWORD ?? "";
const CONTROL_TENANT_ID = process.env.USERS_CONTROL_TENANT_ID ?? "00000000-0000-0000-0000-000000000001";

function requireEnv(name: string, value: string) {
  expect(value, `${name} must be configured by the users-module closure workflow`).toBeTruthy();
}

test("capture authenticated users module evidence", async ({ page }, testInfo) => {
  requireEnv("USERS_TENANT_EMAIL", TENANT_EMAIL);
  requireEnv("USERS_TENANT_PASSWORD", TENANT_PASSWORD);
  requireEnv("USERS_TENANT_ID", TENANT_ID);
  requireEnv("USERS_CONTROL_EMAIL", CONTROL_EMAIL);
  requireEnv("USERS_CONTROL_PASSWORD", CONTROL_PASSWORD);

  const tenantSession = await loginThroughUi(page, TENANT_EMAIL, TENANT_PASSWORD);

  await page.goto("/management/users");
  await captureUsersEvidence(page, testInfo, "management-users", '[data-testid="management-users-ready"]', TENANT_ID, tenantSession.accessToken);
  const tenantRows = page.locator('[data-testid^="tenant-user-"]');
  await expect(tenantRows.first()).toBeVisible();
  const tenantDetailHref = await tenantRows.first().locator('a[href^="/management/users/"]').getAttribute("href");
  expect(tenantDetailHref).toBeTruthy();

  await page.goto(tenantDetailHref!);
  await captureUsersEvidence(page, testInfo, "management-user-detail", '[data-testid="management-user-detail-ready"]', TENANT_ID, tenantSession.accessToken);

  await page.goto("/management/access");
  await captureUsersEvidence(page, testInfo, "management-access", '[data-testid="management-access-ready"]', TENANT_ID, tenantSession.accessToken);

  await page.context().clearCookies();
  await page.goto("/");
  const controlSession = await loginThroughUi(page, CONTROL_EMAIL, CONTROL_PASSWORD);

  await page.goto("/executive/users");
  await captureUsersEvidence(page, testInfo, "executive-users", '[data-testid="executive-users-ready"]', CONTROL_TENANT_ID, controlSession.accessToken);
  const ownerCard = page.getByText(CONTROL_EMAIL, { exact: true }).locator("xpath=ancestor::article[1]");
  const ownerDetailHref = await ownerCard.locator('a[href^="/executive/users/"]').getAttribute("href");
  expect(ownerDetailHref).toBeTruthy();

  await page.goto(ownerDetailHref!);
  await captureUsersEvidence(page, testInfo, "executive-user-detail", '[data-testid="executive-user-detail-ready"]', CONTROL_TENANT_ID, controlSession.accessToken);

  await page.goto("/executive/access");
  await expect(page.getByText("PLATFORM_OWNER", { exact: true })).toBeVisible();
  await captureUsersEvidence(page, testInfo, "executive-access", 'text="PLATFORM_OWNER"', CONTROL_TENANT_ID, controlSession.accessToken);
});
