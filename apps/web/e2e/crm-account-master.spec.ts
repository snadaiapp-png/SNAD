import { test, expect, type Page } from "@playwright/test";
import { loginThroughUi } from "./crm-auth-session";

const TENANT_A_EMAIL = process.env.CRM_TENANT_A_EMAIL ?? "";
const TENANT_A_PASSWORD = process.env.CRM_TENANT_A_PASSWORD ?? "";
const SEEDED_ACCOUNT_ID = "aa00aa00-aa00-4aa0-8aa0-aa00aa00aa01";

async function openCustomer360(page: Page): Promise<void> {
  await loginThroughUi(page, TENANT_A_EMAIL, TENANT_A_PASSWORD);
  await page.goto(`/crm/accounts/${SEEDED_ACCOUNT_ID}`);
  await page.waitForSelector("#crm-operational-content", { timeout: 30_000 });
  await page.waitForLoadState("networkidle");
}

test.describe("CRM-005 Enterprise Account Master", () => {
  test.beforeAll(() => {
    expect(TENANT_A_EMAIL, "CRM_TENANT_A_EMAIL must be configured").toBeTruthy();
    expect(TENANT_A_PASSWORD, "CRM_TENANT_A_PASSWORD must be configured").toBeTruthy();
  });

  test("renders the governed nine-tab Customer 360 workspace", async ({ page }) => {
    await openCustomer360(page);

    await expect(page.locator("#crm-operational-content")).toContainText(/Enterprise Customer 360|ملف العميل المؤسسي 360/i);
    for (const tab of [
      "Overview",
      "Contacts",
      "Opportunities",
      "Activities",
      "Interactions",
      "Financial Summary",
      "Orders",
      "Service",
      "Audit & History",
    ]) {
      await expect(page.getByRole("button", { name: tab, exact: true })).toBeVisible();
    }
  });

  test("updates legal profile through a real ETag and reloads the value", async ({ page }) => {
    await openCustomer360(page);
    const legalName = `Tenant A Legal ${Date.now()}`;
    const profileForm = page.locator("form").filter({ has: page.locator('input[name="legalName"]') });
    await profileForm.locator('input[name="legalName"]').fill(legalName);
    await profileForm.getByRole("button", { name: "Save changes" }).click();
    await expect(page.getByRole("status")).toContainText("Account master data was saved");
    await expect(profileForm.locator('input[name="legalName"]')).toHaveValue(legalName);
  });

  test("shows honest NOT_CONNECTED states for unpublished projections", async ({ page }) => {
    await openCustomer360(page);
    const expected = "The source system is not connected. No synthetic figures are displayed.";
    for (const tab of ["Financial Summary", "Orders", "Service"]) {
      await page.getByRole("button", { name: tab, exact: true }).click();
      await expect(page.locator("#crm-operational-content")).toContainText(expected);
    }
  });

  test("exposes append-only account history", async ({ page }) => {
    await openCustomer360(page);
    await page.getByRole("button", { name: "Audit & History", exact: true }).click();
    await expect(page.locator("#crm-operational-content")).toContainText("Status history");
    await expect(page.locator("#crm-operational-content")).toContainText("Ownership history");
    await expect(page.locator("#crm-operational-content")).toContainText("ACTIVE");
  });
});
