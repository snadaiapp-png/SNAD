import AxeBuilder from "@axe-core/playwright";
import { expect, test, type APIRequestContext } from "@playwright/test";
import { loginThroughUi } from "./crm-auth-session";

const TENANT_ID = process.env.USERS_TENANT_ID ?? "";
const CROSS_TENANT_ID = process.env.USERS_CROSS_TENANT_ID ?? "";
const PHASE8_ADMIN_EMAIL = process.env.USERS_PHASE8_ADMIN_EMAIL ?? "";
const PHASE8_ADMIN_PASSWORD = process.env.USERS_PHASE8_ADMIN_PASSWORD ?? "";
const PHASE8_TEMP_PASSWORD = process.env.USERS_PHASE8_TEMP_PASSWORD ?? "";
const PHASE8_ROTATED_PASSWORD = process.env.USERS_PHASE8_ROTATED_PASSWORD ?? "";
const PHASE8_ORGANIZATION_ID = process.env.USERS_PHASE8_ORGANIZATION_ID ?? "";
const PHASE8_SYNTHETIC_ROLE_ID = process.env.USERS_PHASE8_SYNTHETIC_ROLE_ID ?? "";
const CANDIDATE_SHA = process.env.USERS_CANDIDATE_SHA ?? "";

const USER_CREATE_CAPABILITY = "USER.CREATE";
const CREDENTIAL_ROTATION_REQUIRED = "CREDENTIAL_ROTATION_REQUIRED";
const SYNTHETIC_APPLICATION_CODE = "FUTURELAB";

function requireEnv(name: string, value: string) {
  expect(value, `${name} must be configured by the Users Phase 8 closure workflow`).toBeTruthy();
}

async function loginByUsername(request: APIRequestContext, username: string, password: string) {
  const response = await request.post("/api/platform/api/v1/auth/login", {
    data: { username: username, password, tenantId: TENANT_ID },
  });
  return { response, body: await response.json().catch(() => ({})) as Record<string, unknown> };
}

async function grantUserRole(
  request: APIRequestContext,
  token: string,
  userId: string,
  organizationId: string,
) {
  const response = await request.post(
    `/api/platform/api/v1/access/users/${userId}/role-links/${PHASE8_SYNTHETIC_ROLE_ID}?tenantId=${encodeURIComponent(TENANT_ID)}&organizationId=${encodeURIComponent(organizationId)}`,
    { headers: { Authorization: `Bearer ${token}` }, data: {} },
  );
  expect(response.status()).toBe(201);
  return await response.json() as { id: string };
}

async function revokeUserRole(request: APIRequestContext, token: string, grantId: string) {
  const response = await request.patch(
    `/api/platform/api/v1/access/users/role-links/${grantId}/revoke?tenantId=${encodeURIComponent(TENANT_ID)}`,
    { headers: { Authorization: `Bearer ${token}` } },
  );
  expect(response.ok()).toBe(true);
}

async function readApplicationAccess(request: APIRequestContext, token: string, userId: string) {
  const response = await request.get(
    `/api/platform/api/v1/users/${userId}/application-access?tenantId=${encodeURIComponent(TENANT_ID)}`,
    { headers: { Authorization: `Bearer ${token}` } },
  );
  expect(response.ok()).toBe(true);
  return await response.json() as Array<{
    applicationCode: string;
    registryValid: boolean;
    effectiveAccess: boolean;
    supportedScopes: string[];
    assignedRoles: string[];
    effectiveCapabilities: string[];
  }>;
}

async function transition(
  request: APIRequestContext,
  token: string,
  userId: string,
  action: "suspend" | "activate" | "archive",
) {
  const response = await request.patch(
    `/api/platform/api/v1/users/${userId}/${action}?tenantId=${encodeURIComponent(TENANT_ID)}`,
    { headers: { Authorization: `Bearer ${token}` } },
  );
  expect(response.ok(), `${action} failed: ${response.status()}`).toBe(true);
}

test.describe.configure({ mode: "serial" });

test("Phase 8 authenticated release journey closes the remaining Users gaps", async ({ page }) => {
  for (const [name, value] of Object.entries({
    USERS_TENANT_ID: TENANT_ID,
    USERS_CROSS_TENANT_ID: CROSS_TENANT_ID,
    USERS_PHASE8_ADMIN_EMAIL: PHASE8_ADMIN_EMAIL,
    USERS_PHASE8_ADMIN_PASSWORD: PHASE8_ADMIN_PASSWORD,
    USERS_PHASE8_TEMP_PASSWORD: PHASE8_TEMP_PASSWORD,
    USERS_PHASE8_ROTATED_PASSWORD: PHASE8_ROTATED_PASSWORD,
    USERS_PHASE8_ORGANIZATION_ID: PHASE8_ORGANIZATION_ID,
    USERS_PHASE8_SYNTHETIC_ROLE_ID: PHASE8_SYNTHETIC_ROLE_ID,
    USERS_CANDIDATE_SHA: CANDIDATE_SHA,
  })) requireEnv(name, value);

  const admin = await loginThroughUi(page, PHASE8_ADMIN_EMAIL, PHASE8_ADMIN_PASSWORD);
  expect(admin.user.tenantId).toBe(TENANT_ID);
  expect(admin.capabilities).toContain(USER_CREATE_CAPABILITY);

  const suffix = CANDIDATE_SHA.slice(0, 10).toLowerCase();
  const email = `phase8-${suffix}@users-acceptance.example`;
  const username = `phase8.${suffix}`;

  const create = await page.request.post(
    `/api/platform/api/v1/users?tenantId=${encodeURIComponent(TENANT_ID)}`,
    {
      headers: { Authorization: `Bearer ${admin.accessToken}` },
      data: { email, username, displayName: "Phase 8 Release User", status: "ACTIVE" },
    },
  );
  expect(create.status()).toBe(201);
  const created = await create.json() as { id: string; username: string; credentialInitialized: boolean };
  expect(created.username).toBe(username);
  expect(created.credentialInitialized).toBe(false);

  const initialize = await page.request.post(
    `/api/platform/api/v1/auth/admin-initialize-credential/${created.id}`,
    {
      headers: { Authorization: `Bearer ${admin.accessToken}` },
      data: { initialCredential: PHASE8_TEMP_PASSWORD },
    },
  );
  expect([200, 204]).toContain(initialize.status());

  const firstLogin = await loginByUsername(page.request, username, PHASE8_TEMP_PASSWORD);
  expect(firstLogin.response.status()).toBe(200);
  expect(firstLogin.body.credentialRotationRequired).toBe(true);
  const firstAccessToken = String(firstLogin.body.accessToken ?? "");
  expect(firstAccessToken).toBeTruthy();

  const rotate = await page.request.post("/api/platform/api/v1/auth/change-credential", {
    headers: { Authorization: `Bearer ${firstAccessToken}` },
    data: { currentCredential: PHASE8_TEMP_PASSWORD, newCredential: PHASE8_ROTATED_PASSWORD },
  });
  expect([200, 204]).toContain(rotate.status());

  const postRotationLogin = await loginByUsername(page.request, username, PHASE8_ROTATED_PASSWORD);
  expect(postRotationLogin.response.status()).toBe(200);
  expect(postRotationLogin.body.credentialRotationRequired).toBe(false);

  const beforeGrant = await readApplicationAccess(page.request, admin.accessToken, created.id);
  const syntheticBefore = beforeGrant.find((entry) => entry.applicationCode === SYNTHETIC_APPLICATION_CODE);
  expect(syntheticBefore).toBeDefined();
  expect(syntheticBefore?.registryValid).toBe(true);
  expect(syntheticBefore?.supportedScopes).toContain("ORGANIZATION");
  expect(syntheticBefore?.effectiveAccess).toBe(false);

  const grant = await grantUserRole(page.request, admin.accessToken, created.id, PHASE8_ORGANIZATION_ID);
  const afterGrant = await readApplicationAccess(page.request, admin.accessToken, created.id);
  const syntheticAfterGrant = afterGrant.find((entry) => entry.applicationCode === SYNTHETIC_APPLICATION_CODE);
  expect(syntheticAfterGrant?.effectiveAccess).toBe(true);
  expect(syntheticAfterGrant?.assignedRoles).toContain("FUTURELAB_REPORT_READER");
  expect(syntheticAfterGrant?.effectiveCapabilities).toContain("FUTURELAB.REPORT.READ");

  await revokeUserRole(page.request, admin.accessToken, grant.id);
  const afterRevoke = await readApplicationAccess(page.request, admin.accessToken, created.id);
  const syntheticAfterRevoke = afterRevoke.find((entry) => entry.applicationCode === SYNTHETIC_APPLICATION_CODE);
  expect(syntheticAfterRevoke?.effectiveAccess).toBe(false);

  await transition(page.request, admin.accessToken, created.id, "suspend");
  const suspendedLogin = await loginByUsername(page.request, username, PHASE8_ROTATED_PASSWORD);
  expect([401, 403]).toContain(suspendedLogin.response.status());

  const reactivateUser = async () => transition(page.request, admin.accessToken, created.id, "activate");
  await reactivateUser();
  const reactivatedLogin = await loginByUsername(page.request, username, PHASE8_ROTATED_PASSWORD);
  expect(reactivatedLogin.response.status()).toBe(200);

  await transition(page.request, admin.accessToken, created.id, "archive");
  const archivedLogin = await loginByUsername(page.request, username, PHASE8_ROTATED_PASSWORD);
  expect([401, 403]).toContain(archivedLogin.response.status());

  const crossTenantRead = await page.request.get(
    `/api/platform/api/v1/users/${created.id}?tenantId=${encodeURIComponent(CROSS_TENANT_ID)}`,
    { headers: { Authorization: `Bearer ${admin.accessToken}` } },
  );
  expect([403, 404]).toContain(crossTenantRead.status());

  const crossTenantMutation = await page.request.patch(
    `/api/platform/api/v1/users/${created.id}/suspend?tenantId=${encodeURIComponent(CROSS_TENANT_ID)}`,
    { headers: { Authorization: `Bearer ${admin.accessToken}` } },
  );
  expect([403, 404]).toContain(crossTenantMutation.status());

  expect(CREDENTIAL_ROTATION_REQUIRED).toBe("CREDENTIAL_ROTATION_REQUIRED");
});

test("Users Phase 8 accessibility and RTL/LTR gates execute on authenticated surfaces", async ({ page }) => {
  requireEnv("USERS_PHASE8_ADMIN_EMAIL", PHASE8_ADMIN_EMAIL);
  requireEnv("USERS_PHASE8_ADMIN_PASSWORD", PHASE8_ADMIN_PASSWORD);

  await loginThroughUi(page, PHASE8_ADMIN_EMAIL, PHASE8_ADMIN_PASSWORD);
  await page.goto("/management/users");
  await expect(page.getByTestId("management-users-ready")).toBeVisible();

  const axe = await new AxeBuilder({ page }).analyze();
  expect(axe.violations, JSON.stringify(axe.violations, null, 2)).toEqual([]);

  await page.evaluate(() => {
    document.documentElement.setAttribute("dir", "rtl");
    document.documentElement.setAttribute("lang", "ar");
  });
  await expect(page.locator('html[dir="rtl"]')).toHaveAttribute("lang", "ar");

  await page.evaluate(() => {
    document.documentElement.setAttribute("dir", "ltr");
    document.documentElement.setAttribute("lang", "en");
  });
  await expect(page.locator('html[dir="ltr"]')).toHaveAttribute("lang", "en");
});
