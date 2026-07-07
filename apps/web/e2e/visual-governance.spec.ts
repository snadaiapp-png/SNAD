import { expect, test, type Page } from "@playwright/test";

const authProfile = {
  accessToken: "visual-test-access-token",
  expiresAt: "2099-01-01T00:00:00Z",
  user: {
    id: "00000000-0000-0000-0000-000000000001",
    tenantId: "00000000-0000-0000-0000-000000000002",
    email: "executive@example.invalid",
    displayName: "مدير سند",
    status: "ACTIVE",
  },
  profile: {
    id: "00000000-0000-0000-0000-000000000001",
    tenantId: "00000000-0000-0000-0000-000000000002",
    email: "executive@example.invalid",
    displayName: "مدير سند",
    status: "ACTIVE",
    lastLoginAt: "2026-07-07T00:00:00Z",
    credentialRotationRequired: false,
    memberships: [{
      id: "00000000-0000-0000-0000-000000000003",
      organizationId: "00000000-0000-0000-0000-000000000004",
      status: "ACTIVE",
    }],
    roleGrants: [{
      id: "00000000-0000-0000-0000-000000000005",
      roleId: "00000000-0000-0000-0000-000000000006",
      roleCode: "ADMIN",
      organizationId: null,
      status: "ACTIVE",
    }],
  },
};

async function mockAnonymous(page: Page) {
  await page.route("**/api/v1/auth/refresh", async (route) => {
    await route.fulfill({
      status: 401,
      contentType: "application/json",
      body: JSON.stringify({ status: 401, message: "unauthenticated" }),
    });
  });
  await page.route("**/api/telemetry/auth", async (route) => {
    await route.fulfill({ status: 204, body: "" });
  });
}

async function mockAuthenticated(page: Page) {
  let meRequests = 0;
  page.on("request", (request) => {
    if (request.url().includes("/api/v1/auth/me")) meRequests += 1;
  });
  await page.route("**/api/v1/auth/refresh", async (route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify(authProfile),
    });
  });
  await page.route("**/api/telemetry/auth", async (route) => {
    await route.fulfill({ status: 204, body: "" });
  });
  return () => meRequests;
}

test("login desktop light RTL", async ({ page }) => {
  await mockAnonymous(page);
  await page.goto("/");
  await expect(page.getByRole("heading", { name: "مرحبًا بعودتك" })).toBeVisible();
  const logo = page.locator('img[src*="snad-logo-official-primary"]');
  await expect(logo).toBeVisible();
  const box = await logo.boundingBox();
  expect(box?.width ?? 0).toBeGreaterThan(160);
  await expect(page).toHaveScreenshot("login-desktop-light-rtl.png", { fullPage: true });
});

test("login mobile dark RTL", async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await page.addInitScript(() => localStorage.setItem("snad-theme", "dark"));
  await mockAnonymous(page);
  await page.goto("/");
  await expect(page.getByRole("heading", { name: "مرحبًا بعودتك" })).toBeVisible();
  await expect(page).toHaveScreenshot("login-mobile-dark-rtl.png", { fullPage: true });
});

test("authenticated workspace uses one bootstrap request and global shell", async ({ page }) => {
  const meRequests = await mockAuthenticated(page);
  await page.goto("/");
  await page.waitForURL("**/workspace");
  await expect(page.getByRole("banner")).toBeVisible();
  await expect(page.getByRole("heading", { name: /مرحبًا، مدير سند/ })).toBeVisible();
  await expect(page.locator('img[src*="snad-logo-official-wordmark"]')).toBeVisible();
  expect(meRequests()).toBe(0);
  await expect(page).toHaveScreenshot("workspace-desktop-rtl.png", { fullPage: true });
});
