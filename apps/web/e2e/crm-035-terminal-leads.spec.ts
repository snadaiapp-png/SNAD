/**
 * CRM-035 — Terminal Lead Status E2E Test
 * ----------------------------------------------------------------------------
 * Verifies that terminal leads (CONVERTED, ARCHIVED, DISQUALIFIED) cannot
 * have their status modified via the UI. This prevents HTTP 409 conflicts
 * caused by the backend rejecting invalid state transitions.
 *
 * Acceptance Criteria:
 * - Terminal leads display a read-only status badge
 * - No action buttons (Qualify / Disqualify / Convert) are rendered for
 *   terminal leads
 * - No PATCH request is sent for terminal leads
 * - No HTTP 409 appears in network responses
 *
 * Production reference: apps/web/app/crm/(operational)/leads/page.tsx
 *   - Status badge: <span className={styles.badge}>{lead.status}</span>
 *   - Action buttons (NOT a <select> dropdown):
 *       lead.status === "NEW"             → Qualify button
 *       !terminalStates.includes(status)  → Disqualify button
 *       !terminalStates.includes(status)  → Convert button
 *   - terminalStates = ["CONVERTED", "ARCHIVED", "DISQUALIFIED"]
 *
 * Required: Authentication with a CRM tenant that has terminal leads.
 */
import { test, expect, type Page } from "@playwright/test";
import { loginThroughUi } from "./crm-auth-session";
import { TENANT_A_EMAIL, TENANT_A_PASSWORD } from "./crm-helpers";

/* ============================================================================
 *  Helpers
 * ============================================================================ */

// Mirrors production terminalStates in apps/web/app/crm/(operational)/leads/page.tsx.
const TERMINAL_STATUSES = ["CONVERTED", "ARCHIVED", "DISQUALIFIED"];

// Action buttons rendered by the leads page for non-terminal leads.
// Production uses buttons (Qualify / Disqualify / Convert), NOT a <select>.
const ACTION_BUTTON_TEXTS = ["Qualify", "Disqualify", "Convert", "تأهيل", "استبعاد", "تحويل"];

async function waitForLeadsPage(page: Page): Promise<void> {
  // /crm redirects to /crm/overview; this spec must exercise the leads table.
  await page.goto("/crm/leads", { waitUntil: "domcontentloaded" });
  await page.waitForSelector("table", { timeout: 15_000 });
}

/**
 * Read the status text from a row's badge. Returns null if the row has no
 * badge element (e.g., a non-lead row that happens to match `table tbody tr`,
 * such as a skeleton/pagination/empty-state row). The previous test version
 * called `await statusBadge.textContent()` directly, which auto-waits for
 * the element to appear — if the row had no badge, the call would wait the
 * full 60s test timeout and mask the actual test logic.
 */
async function readStatusText(row: ReturnType<Page["locator"]>): Promise<string | null> {
  const statusBadge = row.locator('[class*="badge"]');
  const count = await statusBadge.count();
  if (count === 0) return null;
  return (await statusBadge.first().textContent()) ?? null;
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
    let foundTerminalLead = false;
    for (let i = 0; i < rowCount; i++) {
      const row = rows.nth(i);

      /* Read the status text. Skip rows that don't have a status badge
         (e.g., non-lead rows like loading skeletons or pagination rows). */
      const statusText = await readStatusText(row);
      if (statusText === null) continue;

      if (TERMINAL_STATUSES.some((ts) => statusText.includes(ts))) {
        foundTerminalLead = true;

        /* Terminal lead: should NOT have any action buttons (Qualify /
           Disqualify / Convert). Production uses buttons for state
           transitions, NOT a <select> dropdown. The previous test version
           asserted `selectDropdown.toHaveCount(0)` — which trivially
           passes because production never renders <select> at all. The
           meaningful invariant is "no action buttons for terminal leads". */
        for (const buttonText of ACTION_BUTTON_TEXTS) {
          const button = row.locator(`button`, { hasText: buttonText });
          await expect(button, `Terminal lead row ${i} should not have action button "${buttonText}"`).toHaveCount(0);
        }
      }
    }

    /* Verify no PATCH requests were sent */
    expect(patchRequests).toHaveLength(0);

    /* Verify no 409 responses occurred */
    expect(conflictResponses).toHaveLength(0);

    if (!foundTerminalLead) {
      // Don't fail the test — the absence of terminal leads in the seed data
      // is an environment concern, not a regression. The patch/409 invariants
      // above remain authoritative.
      test.skip(true, "No terminal leads found in the table — only verified no PATCH / no 409 invariants");
    }
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

      /* Read the status text. Skip rows that don't have a status badge. */
      const statusText = await readStatusText(row);
      if (statusText === null) continue;

      if (!TERMINAL_STATUSES.some((ts) => statusText.includes(ts))) {
        /* Non-terminal lead: should have at least one action button
           (Qualify, Disqualify, or Convert). Production uses buttons for
           state transitions, NOT a <select> dropdown. */
        let foundAnyActionButton = false;
        for (const buttonText of ACTION_BUTTON_TEXTS) {
          const button = row.locator(`button`, { hasText: buttonText });
          const count = await button.count();
          if (count > 0) {
            foundAnyActionButton = true;
            break;
          }
        }

        expect(foundAnyActionButton, `Non-terminal lead row ${i} (status="${statusText}") should have at least one action button`).toBe(true);

        foundNonTerminal = true;
        break;
      }
    }

    if (!foundNonTerminal) {
      test.skip(true, "No non-terminal leads found — cannot verify editable action buttons");
    }
  });
});
