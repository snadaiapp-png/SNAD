import { expect, test, type Page } from "@playwright/test";

const CRM_ROUTES = [
  "/crm/overview",
  "/crm/accounts",
  "/crm/contacts",
  "/crm/leads",
  "/crm/pipelines",
  "/crm/opportunities",
  "/crm/activities",
  "/crm/imports",
  "/crm/settings/custom-fields",
  "/crm/command-center",
] as const;

function collectHydrationErrors(page: Page): string[] {
  const errors: string[] = [];
  page.on("pageerror", (error) => {
    if (/hydrat/i.test(error.message)) errors.push(error.message);
  });
  page.on("console", (message) => {
    if ((message.type() === "error" || message.type() === "warning") && /hydrat/i.test(message.text())) {
      errors.push(message.text());
    }
  });
  return errors;
}

async function expectProtectedLogin(page: Page, returnUrl: string): Promise<void> {
  await page.waitForURL(
    (url) => url.pathname === "/" && url.searchParams.get("returnUrl") === returnUrl,
    { timeout: 10_000 },
  );
  await expect(page.locator("#login-email")).toBeVisible();
}

test.describe("CRM unauthenticated route contract", () => {
  test("GET /crm remains non-error before authenticated landing resolution", async ({ request }) => {
    const response = await request.get("/crm", { maxRedirects: 0 });
    expect(response.status()).toBeLessThan(500);
    if ([307, 308].includes(response.status())) {
      expect(response.headers()["location"]).toBe("/crm/overview");
    } else {
      // Next.js may materialize the redirect through its RSC response contract.
      expect(response.status()).toBe(200);
    }
  });

  test("anonymous /crm navigation ends at login with its return URL preserved", async ({ page }) => {
    const hydrationErrors = collectHydrationErrors(page);
    const response = await page.goto("/crm");
    if (response) expect(response.status()).toBeLessThan(500);
    await expectProtectedLogin(page, "/crm");
    expect(hydrationErrors).toEqual([]);
  });

  for (const route of CRM_ROUTES) {
    test(`${route} is protected without a 5xx`, async ({ page }) => {
      const response = await page.goto(route);
      if (response) expect(response.status()).toBeLessThan(500);
      await expectProtectedLogin(page, route);

      const box = await page.locator("body").boundingBox();
      expect(box).toBeTruthy();
      expect(box!.height).toBeGreaterThan(80);
    });
  }
});
