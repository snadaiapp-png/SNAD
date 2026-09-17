import { test, expect } from "@playwright/test";
import { loginThroughUi } from "./crm-auth-session";

const ADMIN_EMAIL = process.env.SUBSCRIPTION_ADMIN_EMAIL ?? "";
const ADMIN_PASSWORD = process.env.SUBSCRIPTION_ADMIN_PASSWORD ?? "";
const SUBSCRIPTION_ID =
  process.env.SUBSCRIPTION_ACCEPTANCE_ID ?? "44444444-4444-4444-8444-444444444444";

test.describe("Subscription Control Plane — authenticated acceptance", () => {
  test.describe.configure({ mode: "serial" });

  test.beforeAll(() => {
    expect(ADMIN_EMAIL, "SUBSCRIPTION_ADMIN_EMAIL must be configured").toBeTruthy();
    expect(ADMIN_PASSWORD, "SUBSCRIPTION_ADMIN_PASSWORD must be configured").toBeTruthy();
  });

  test("list → detail → governed cancellation → durable readback", async ({ page }) => {
    const login = await loginThroughUi(page, ADMIN_EMAIL, ADMIN_PASSWORD);
    const authHeaders = { Authorization: `Bearer ${login.accessToken}` };

    await page.locator('a[href="/executive"]').first().click();
    await page.waitForURL(/\/executive(?:\?.*)?$/, { timeout: 30_000 });

    const subscriptionsLink = page.locator('a[href="/executive/subscriptions"]').first();
    await expect(subscriptionsLink).toBeVisible({ timeout: 15_000 });
    await subscriptionsLink.click();
    await page.waitForURL(/\/executive\/subscriptions(?:\?.*)?$/, { timeout: 30_000 });

    await expect(page.getByRole("heading", { name: "Subscriptions" })).toBeVisible();
    await expect(page.locator("body")).toContainText("Tenant A (Acceptance)");
    await expect(page.locator("body")).toContainText("Subscription Acceptance Plan");
    await expect(page.locator("body")).toContainText("ACTIVE");

    const genericCancel = await page.request.post(
      `/api/platform/api/v1/executive/subscriptions/${SUBSCRIPTION_ID}/lifecycle/CANCEL`,
      {
        headers: { ...authHeaders, "content-type": "application/json" },
        data: { reason: "acceptance generic-route guard" },
      },
    );
    expect(genericCancel.status(), "generic CANCEL endpoint must fail closed").toBe(409);

    const detailsLink = page.getByRole("link", { name: "Details" }).first();
    await expect(detailsLink).toBeVisible();
    await detailsLink.click();
    await page.waitForURL(
      new RegExp(`/executive/subscriptions/${SUBSCRIPTION_ID}(?:\\?.*)?$`),
      { timeout: 30_000 },
    );

    await expect(page.locator("body")).toContainText("Subscription detail");
    await expect(page.locator("body")).toContainText("ACTIVE");

    const lifecycle = page.locator('section[aria-labelledby="scp-lifecycle-heading"]');
    await expect(lifecycle).toBeVisible();
    await lifecycle.locator("input").fill("subscription acceptance cancellation");

    const cancelButton = lifecycle.getByRole("button", { name: "Cancel", exact: true });
    await expect(cancelButton).toBeVisible();
    await cancelButton.click();

    await expect(page.locator("body")).toContainText("CANCELLED", { timeout: 20_000 });

    const detailResponse = await page.request.get(
      `/api/platform/api/v1/executive/subscriptions/${SUBSCRIPTION_ID}/detail`,
      { headers: authHeaders },
    );
    expect(detailResponse.ok(), `detail readback failed: ${detailResponse.status()}`).toBe(true);
    const detail = await detailResponse.json();

    expect(detail.overview.status).toBe("CANCELLED");
    expect(
      (detail.changes ?? []).some(
        (entry: Record<string, unknown>) =>
          entry.action === "CANCEL" &&
          entry.fromStatus === "ACTIVE" &&
          entry.toStatus === "CANCELLED",
      ),
      "command ledger must contain ACTIVE → CANCELLED",
    ).toBe(true);
    expect(
      (detail.audit ?? []).some(
        (entry: Record<string, unknown>) => entry.action === "SUBSCRIPTION.CANCEL",
      ),
      "audit read model must contain SUBSCRIPTION.CANCEL",
    ).toBe(true);
  });
});
