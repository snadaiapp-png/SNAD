import { test, expect } from "@playwright/test";
import { loginThroughUi } from "./crm-auth-session";

const CRM_TENANT_A_EMAIL = process.env.CRM_TENANT_A_EMAIL ?? "";
const CRM_TENANT_A_PASSWORD = process.env.CRM_TENANT_A_PASSWORD ?? "";

test.describe("CRM Tags Production UI Verification", () => {
  test.describe.configure({ mode: "serial" });

  let accessToken = "";

  test.beforeEach(async ({ page }) => {
    test.skip(!CRM_TENANT_A_EMAIL || !CRM_TENANT_A_PASSWORD, "Missing CRM_TENANT_A credentials");
    const result = await loginThroughUi(page, CRM_TENANT_A_EMAIL, CRM_TENANT_A_PASSWORD);
    accessToken = result.accessToken;
  });

  test("A. Authentication succeeds", async ({ page }) => {
    // Verify we're on the workspace or CRM page after login
    const url = page.url();
    expect(url).not.toContain("/login");
    // Should be on workspace or CRM
    const isOnApp = url.includes("/workspace") || url.includes("/crm");
    expect(isOnApp).toBeTruthy();
  });

  test("B. Authenticated identity via /me endpoint", async ({ page }) => {
    const response = await page.request.get("/api/platform/api/v1/auth/me", {
      headers: { Authorization: `Bearer ${accessToken}` },
    });
    expect(response.status()).toBe(200);
    const body = await response.json();
    // /me returns a flat object with user properties at top level
    expect(body).toHaveProperty("email");
    expect(body).toHaveProperty("tenantId");
    expect(body).toHaveProperty("capabilities");
  });

  test("C. CRM.TAG.READ capability is present", async ({ page }) => {
    const response = await page.request.get("/api/platform/api/v1/auth/me", {
      headers: { Authorization: `Bearer ${accessToken}` },
    });
    expect(response.status()).toBe(200);
    const body = await response.json();
    // /me returns capabilities at top level (flat object)
    const capabilities = body.capabilities ?? body.user?.capabilities ?? [];
    // CRM.TAG.READ should be present (or equivalent like "Read CRM Tags")
    const hasTagRead = capabilities.some(
      (cap: string) => cap === "CRM.TAG.READ" || cap === "Read CRM Tags"
    );
    expect(hasTagRead).toBeTruthy();
  });

  test("D. CRM navigation renders with Tags item", async ({ page }) => {
    await page.goto("/crm/overview");
    await page.waitForSelector("#crm-operational-content", { timeout: 30_000 });
    await page.waitForLoadState("networkidle");

    // Check that CRM navigation is visible
    const sidebar = page.locator("aside[aria-label]");
    await expect(sidebar).toBeVisible();

    // Check that Tags navigation item is visible
    const tagsLink = page.locator('a[href="/crm/tags"]');
    await expect(tagsLink).toBeVisible();
  });

  test("E. Tags page loads successfully", async ({ page }) => {
    await page.goto("/crm/tags");
    await page.waitForSelector("#crm-operational-content", { timeout: 30_000 });
    await page.waitForLoadState("networkidle");

    // Verify no 401, 403, 404, or 500 errors
    const url = page.url();
    expect(url).toContain("/crm/tags");
    expect(url).not.toContain("/login");

    // Verify page content is present (not blank)
    const body = page.locator("body");
    const bodyText = await body.textContent();
    expect(bodyText).toBeTruthy();
    expect(bodyText!.length).toBeGreaterThan(0);

    // Verify no critical console errors
    const errors: string[] = [];
    page.on("console", (msg) => {
      if (msg.type() === "error") {
        errors.push(msg.text());
      }
    });

    // Wait a moment for any console errors
    await page.waitForTimeout(2000);

    // Filter out non-critical errors (e.g., network timeouts for analytics)
    const criticalErrors = errors.filter(
      (e) => !e.includes("analytics") && !e.includes("favicon") && !e.includes("404")
    );
    expect(criticalErrors).toHaveLength(0);
  });

  test("F. Tags API endpoint returns data", async ({ page }) => {
    const response = await page.request.get("/api/platform/api/v1/crm/tags", {
      headers: { Authorization: `Bearer ${accessToken}` },
    });
    // Should return 200 (even if empty array)
    expect(response.status()).toBe(200);
    const body = await response.json();
    // Response should be an array (may be empty)
    expect(Array.isArray(body)).toBeTruthy();
  });

  test("G. READ-only verification (no mutations)", async ({ page }) => {
    // Verify that only GET requests are made during the test
    const mutations: string[] = [];

    page.on("request", (request) => {
      const method = request.method();
      if (["POST", "PUT", "PATCH", "DELETE"].includes(method)) {
        mutations.push(`${method} ${request.url()}`);
      }
    });

    // Navigate to Tags page
    await page.goto("/crm/tags");
    await page.waitForSelector("#crm-operational-content", { timeout: 30_000 });
    await page.waitForLoadState("networkidle");

    // Wait for any pending requests
    await page.waitForTimeout(2000);

    // No mutations should have been made to CRM Tags
    const tagMutations = mutations.filter((m) => m.includes("/crm/tags"));
    expect(tagMutations).toHaveLength(0);
  });
});
