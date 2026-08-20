/**
 * CRM-035 — Terminal Lead Status E2E Test
 * ----------------------------------------------------------------------------
 * Verifies that terminal leads (CONVERTED, ARCHIVED) cannot have their
 * status modified via the UI. This prevents HTTP 409 conflicts caused
 * by the backend rejecting invalid state transitions.
 *
 * Acceptance Criteria:
 * - Terminal leads display a read-only status badge
 * - Status selector is not rendered for terminal leads
 * - No PATCH request is sent for terminal leads
 * - No HTTP 409 appears in network responses
 *
 * Required: Authentication with a CRM tenant that has terminal leads.
 */
import { test, expect, type Page } from "@playwright/test";
import { loginThroughUi } from "./crm-auth-session";
import { TENANT_A_EMAIL, TENANT_A_PASSWORD } from "./crm-helpers";

/* ============================================================================
 *  Helpers
 * ============================================================================ */

// Production code (apps/web/app/crm/(operational)/leads/page.tsx) treats
// CONVERTED, ARCHIVED, and DISQUALIFIED as terminal — none of these get an
// editable <select>. The E2E test must mirror that exact set so the assertion
// "terminal lead has no select dropdown" matches the page behavior.
const TERMINAL_STATUSES = ["CONVERTED", "ARCHIVED", "DISQUALIFIED"];

async function waitForLeadsPage(page: Page): Promise<void> {
  // /crm redirects to /crm/overview; this spec must exercise the leads table.
  await page.goto("/crm/leads", { waitUntil: "domcontentloaded" });
  await page.waitForSelector("table", { timeout: 15_000 });
}

/* ============================================================================
 *  Tests
 * ============================================================================ */

test.describe("CRM-035: Terminal Lead Status Protection", () => {
  test.setTimeout(60_000);

  test.beforeAll(async () => {
    expect(TENANT_A_EMAIL, "CRM_TENANT_A_EMAIL env var must be set").toBeTruthy();
    expect(TENANT_A_PASSWORD, "CRM_TENANT_A_PASSWORD env var must be set").toBeTruthy();
  });

  test.beforeEach(async ({ page }) => {
    await loginThroughUi(page, TENANT_A_EMAIL, TENANT_A_PASSWORD);
  });

  test("Terminal leads show read-only status badge, not editable selector", async ({ page }) => {
    /* Intercept PATCH requests to detect any status change attempts */
    const patchRequests: string[] = [];
    page.on("request", (request) => {
      if (request.method() === "PATCH" && request.url().includes("/leads/")) {
        patchRequests.push(request.url());
      }
    });

    /* Intercept 409 responses */
    const conflictResponses: number[] = [];
    page.on("response", (response) => {
      if (response.status() === 409) {
        conflictResponses.push(response.status());
      }
    });

    await waitForLeadsPage(page);

    /* Find all rows in the leads table */
    const rows = page.locator("table tbody tr");
    const rowCount = await rows.count();

    if (rowCount === 0) {
      test.skip(true, "No leads found in the table — cannot verify terminal state behavior");
      return;
    }

    /* Check each row for terminal state behavior */
    for (let i = 0; i < rowCount; i++) {
      const row = rows.nth(i);

      /* Get the status badge text. Production code uses CSS module class
         `badge` (apps/web/app/crm/(operational)/leads/page.tsx line ~163:
         `<span className={styles.badge}>{lead.status}</span>`). The previous
         selector `[class*="statusBadge"]` never matched because the class
         name doesn't contain the substring 'statusBadge' — this caused
         textContent() to auto-wait until the test's 60s timeout, masking
         the actual test logic. Use `[class*="badge"]` which matches the
         current production CSS module class. */
      const statusBadge = row.locator('[class*="badge"]');
      const statusText = await statusBadge.textContent();

      if (TERMINAL_STATUSES.some((ts) => statusText?.includes(ts))) {
        /* Terminal lead: should NOT have a status select dropdown */
        const selectDropdown = row.locator("select");
        await expect(selectDropdown).toHaveCount(0);

        /* Terminal lead: should NOT have a convert button */
        const convertButton = row.locator('button:has-text("Convert"), button:has-text("تحويل")');
        await expect(convertButton).toHaveCount(0);
      }
    }

    /* Verify no PATCH requests were sent */
    expect(patchRequests).toHaveLength(0);

    /* Verify no 409 responses occurred */
    expect(conflictResponses).toHaveLength(0);
  });

  test("Non-terminal leads have editable status selector", async ({ page }) => {
    await waitForLeadsPage(page);

    /* Find all rows in the leads table */
    const rows = page.locator("table tbody tr");
    const rowCount = await rows.count();

    if (rowCount === 0) {
      test.skip(true, "No leads found in the table — cannot verify non-terminal state behavior");
      return;
    }

    /* Check each row for non-terminal state behavior */
    let foundNonTerminal = false;
    for (let i = 0; i < rowCount; i++) {
      const row = rows.nth(i);

      /* Get the status badge text (see comment in the first test about the
         CSS module class name — production uses `badge`, not `statusBadge`). */
      const statusBadge = row.locator('[class*="badge"]');
      const statusText = await statusBadge.textContent();

      if (!TERMINAL_STATUSES.some((ts) => statusText?.includes(ts))) {
        /* Non-terminal lead: should have a status select dropdown */
        const selectDropdown = row.locator("select");
        await expect(selectDropdown).toHaveCount(1);

        /* Non-terminal lead: select should be enabled */
        await expect(selectDropdown).toBeEnabled();

        foundNonTerminal = true;
        break;
      }
    }

    if (!foundNonTerminal) {
      test.skip(true, "No non-terminal leads found — cannot verify editable selector");
    }
  });
});
