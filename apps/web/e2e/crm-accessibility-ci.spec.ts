/**
 * SNAD CRM Accessibility — CI-friendly Axe checks
 * ----------------------------------------------------------------------------
 * Runs @axe-core/playwright against the CRM login page (no authentication
 * required). This spec is designed for CI pipelines that don't have
 * CRM_TENANT_A_EMAIL/PASSWORD secrets configured.
 *
 * The login page is the first touchpoint of the CRM experience and must
 * meet WCAG 2.0/2.1 A/AA standards.
 *
 * Evidence is committed to evidence/crm-axe-audit.json for governance.
 *
 * Required devDependency: @axe-core/playwright
 */
import { test, expect, type Page } from "@playwright/test";
import AxeBuilder from "@axe-core/playwright";
import * as fs from "fs";
import * as path from "path";

const EVIDENCE_DIR = path.resolve(__dirname, "../../..", "evidence");
const EVIDENCE_FILE = path.join(EVIDENCE_DIR, "crm-axe-audit.json");

async function waitForLoginPage(page: Page): Promise<void> {
  await page.goto("/", { waitUntil: "domcontentloaded" });
  await page.locator('input[type="email"]').waitFor({ state: "visible", timeout: 15_000 });
}

test.describe("CRM Accessibility — CI Axe checks (login page)", () => {
  test.setTimeout(120_000);

  test("CRM login page has 0 critical and 0 serious Axe violations", async ({ page }) => {
    await waitForLoginPage(page);

    const results = await new AxeBuilder({ page })
      .withTags(["wcag2a", "wcag2aa", "wcag21a", "wcag21aa"])
      .analyze();

    const critical = results.violations.filter((v) => v.impact === "critical");
    const serious = results.violations.filter((v) => v.impact === "serious");

    // Log violations for debugging
    if (critical.length > 0 || serious.length > 0) {
      const summary = [...critical, ...serious]
        .map(
          (v) =>
            `  • [${v.impact}] ${v.id}: ${v.description}\n    help: ${v.helpUrl}\n    targets: ${v.nodes
              .slice(0, 3)
              .map((n) => n.target.join(","))
              .join(" | ")}`,
        )
        .join("\n");
      console.error(`Axe violations on /crm login:\n${summary}`);
    }

    // Write evidence file
    const evidence = {
      ticket: "CRM-034",
      timestamp: new Date().toISOString(),
      route: "/crm (login page)",
      wcagLevel: "wcag2a, wcag2aa, wcag21a, wcag21aa",
      totalViolations: results.violations.length,
      criticalViolations: critical.length,
      seriousViolations: serious.length,
      violations: results.violations.map((v) => ({
        id: v.id,
        impact: v.impact,
        description: v.description,
        helpUrl: v.helpUrl,
        nodes: v.nodes.length,
      })),
      passes: results.passes.length,
      inapplicable: results.inapplicable.length,
    };

    // Ensure evidence directory exists
    if (!fs.existsSync(EVIDENCE_DIR)) {
      fs.mkdirSync(EVIDENCE_DIR, { recursive: true });
    }
    fs.writeFileSync(EVIDENCE_FILE, JSON.stringify(evidence, null, 2));

    expect(critical, `/crm login: ${critical.length} critical Axe violations`).toEqual([]);
    expect(serious, `/crm login: ${serious.length} serious Axe violations`).toEqual([]);
  });

  test("CRM login page renders accessible form elements", async ({ page }) => {
    await waitForLoginPage(page);

    // Verify form has proper labels
    const emailInput = page.locator('input[type="email"]');
    const passwordInput = page.locator('input[type="password"]');
    const submitButton = page.locator('form button[type="submit"]');

    await expect(emailInput).toBeVisible();
    await expect(passwordInput).toBeVisible();
    await expect(submitButton).toBeVisible();

    // Check for associated labels
    const emailLabel = page.locator('label[for="login-email"]');
    const passwordLabel = page.locator('label[for="login-password"]');

    // At least one label should exist (either explicit or aria-label)
    const emailHasLabel = (await emailLabel.count()) > 0 ||
      (await emailInput.getAttribute("aria-label")) !== null ||
      (await emailInput.getAttribute("aria-labelledby")) !== null;
    const passwordHasLabel = (await passwordLabel.count()) > 0 ||
      (await passwordInput.getAttribute("aria-label")) !== null ||
      (await passwordInput.getAttribute("aria-labelledby")) !== null;

    expect(emailHasLabel, "Email input should have an associated label").toBe(true);
    expect(passwordHasLabel, "Password input should have an associated label").toBe(true);
  });
});
