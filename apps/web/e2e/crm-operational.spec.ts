import { expect, test, type Page } from "@playwright/test";
const BASE_URL = process.env.PLAYWRIGHT_BASE_URL ?? "http://127.0.0.1:3001";
const CRM_ROUTES = [
  "/crm/overview", "/crm/accounts", "/crm/contacts", "/crm/leads",
  "/crm/pipelines", "/crm/opportunities", "/crm/activities", "/crm/imports",
  "/crm/settings/custom-fields", "/crm/command-center",
] as const;
function collectHydrationErrors(page: Page): string[] {
  const errors: string[] = [];
  page.on("pageerror", (error) => { if (/hydrat/i.test(error.message)) errors.push(error.message); });
  page.on("console", (message) => {
    if ((message.type() === "error" || message.type() === "warning") && /hydrat/i.test(message.text())) {
      errors.push(message.text());
    }
  });
  return errors;
}
test.describe("CRM unauthenticated route contract", () => {
  test("GET /crm redirects to /crm/overview", async ({ request }) => {
    const response = await request.get("/crm", { maxRedirects: 0 });
    expect([307, 308]).toContain(response.status());
    expect(response.headers()["location"]).toBe("/crm/overview");
  });
  test("anonymous /crm navigation ends at login", async ({ page }) => {
    const hydrationErrors = collectHydrationErrors(page);
    await page.goto("/crm");
    await page.waitForURL(`${BASE_URL}/`, { timeout: 30_000 });
    await expect(page.locator("body")).toBeVisible();
    expect(hydrationErrors).toEqual([]);
  });
  for (const route of CRM_ROUTES) {
    test(`${route} is protected without a 5xx`, async ({ page }) => {
      const response = await page.goto(route);
      if (response) expect(response.status()).toBeLessThan(500);
      await page.waitForURL(`${BASE_URL}/`, { timeout: 30_000 });
      const box = await page.locator("body").boundingBox();
      expect(box).toBeTruthy();
      expect(box!.height).toBeGreaterThan(80);
    });
  }
});
