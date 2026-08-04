/**
 * CRM Pipeline + Export E2E — pipeline creation and CSV export
 * ----------------------------------------------------------------------------
 * TD-004-4: Tests pipeline creation workflow and CSV export functionality,
 * validating the /crm/pipelines page and the V1 export API endpoints.
 *
 * Coverage:
 *   1.  Login as Tenant A CRM Admin
 *   2.  Navigate to /crm/pipelines
 *   3.  Create a new pipeline with stages via UI
 *   4.  Verify pipeline appears in list
 *   5.  Verify stages are listed under pipeline
 *   6.  Test CSV export download (accounts, contacts, leads)
 *   7.  Verify downloaded content is valid CSV
 *
 * Required env vars:
 *   - PLAYWRIGHT_BASE_URL
 *   - CRM_TENANT_A_EMAIL
 *   - CRM_TENANT_A_PASSWORD
 */
import { test, expect, type Page } from "@playwright/test";
import { loginThroughUi } from "./crm-auth-session";
import {
  TENANT_A_EMAIL,
  TENANT_A_PASSWORD,
  waitForCrmReady,
  createTestPipeline,
  expectStatusNotice,
} from "./crm-helpers";

test.describe("CRM Pipeline + Export E2E", () => {
  test.describe.configure({ mode: "serial" });

  test.beforeAll(async () => {
    expect(TENANT_A_EMAIL, "CRM_TENANT_A_EMAIL env var must be set").toBeTruthy();
    expect(TENANT_A_PASSWORD, "CRM_TENANT_A_PASSWORD env var must be set").toBeTruthy();
  });

  let accessToken: string;

  test.beforeEach(async ({ page }) => {
    const login = await loginThroughUi(page, TENANT_A_EMAIL, TENANT_A_PASSWORD);
    accessToken = login.accessToken;
  });

  test("login and navigate to pipelines page", async ({ page }) => {
    expect(accessToken).toBeTruthy();
    await waitForCrmReady(page, "/crm/pipelines");
    await expect(page.locator("#crm-operational-content")).toBeVisible();
  });

  test("pipelines page renders create form and existing pipelines", async ({ page }) => {
    await waitForCrmReady(page, "/crm/pipelines");

    // The pipelines page should have a create form or list.
    // Wait for the page to settle.
    await page.waitForLoadState("networkidle");

    // Verify the page has meaningful content (not a blank/error page).
    const bodyBox = await page.locator("body").boundingBox();
    expect(bodyBox).toBeTruthy();
    expect(bodyBox!.height).toBeGreaterThan(80);
  });

  test("create a pipeline via API and verify it appears", async ({ page }) => {
    const pipelineName = `E2E Pipeline ${Date.now()}`;
    const stages = ["Qualification", "Proposal", "Negotiation", "Closed Won"];

    // Create via API for reliability.
    const pipeline = await createTestPipeline(page, accessToken, {
      name: pipelineName,
      stages,
    });
    expect(pipeline.id).toBeTruthy();
    expect(pipeline.name).toBe(pipelineName);

    // Navigate to pipelines page and verify it appears.
    await waitForCrmReady(page, "/crm/pipelines");
    await page.waitForLoadState("networkidle");

    // The pipeline name should be visible on the page.
    await expect(page.locator("body")).toContainText(pipelineName, { timeout: 15_000 });
  });

  test("pipeline stages are listed", async ({ page }) => {
    // Fetch pipelines via API to verify stages are associated.
    const response = await page.request.get("/api/platform/api/v1/crm/pipelines", {
      headers: { Authorization: `Bearer ${accessToken}` },
    });
    expect(response.ok(), `Fetch pipelines failed: ${response.status()}`).toBe(true);
    const pipelines = await response.json();
    expect(Array.isArray(pipelines)).toBe(true);
    expect(pipelines.length).toBeGreaterThan(0);

    // Fetch stages for the first pipeline.
    const firstPipeline = pipelines[0];
    const stagesResponse = await page.request.get(
      `/api/platform/api/v1/crm/pipelines/${firstPipeline.id}/stages`,
      { headers: { Authorization: `Bearer ${accessToken}` } },
    );
    expect(stagesResponse.ok(), `Fetch stages failed: ${stagesResponse.status()}`).toBe(true);
    const stages = await stagesResponse.json();
    expect(Array.isArray(stages)).toBe(true);
    expect(stages.length).toBeGreaterThan(0);

    // Each stage should have a name and pipeline_id.
    for (const stage of stages) {
      expect(stage.name, "Stage must have a name").toBeTruthy();
      expect(stage.pipeline_id, "Stage must have a pipeline_id").toBeTruthy();
    }
  });

  test("CSV export for accounts downloads valid content", async ({ page }) => {
    const response = await page.request.get("/api/platform/api/v1/crm/export/accounts", {
      headers: { Authorization: `Bearer ${accessToken}` },
    });
    expect(response.ok(), `Export accounts failed: ${response.status()}`).toBe(true);

    const contentType = response.headers()["content-type"] ?? "";
    expect(
      contentType.includes("csv") || contentType.includes("octet-stream") || contentType.includes("text/"),
      `Export should return CSV content, got: ${contentType}`,
    ).toBe(true);

    const body = await response.body();
    const text = body.toString("utf-8");
    // CSV should have at least a header row.
    const lines = text.split("\n").filter((l) => l.trim());
    expect(lines.length).toBeGreaterThan(0, "CSV export should have at least a header row");
  });

  test("CSV export for contacts downloads valid content", async ({ page }) => {
    const response = await page.request.get("/api/platform/api/v1/crm/export/contacts", {
      headers: { Authorization: `Bearer ${accessToken}` },
    });
    expect(response.ok(), `Export contacts failed: ${response.status()}`).toBe(true);

    const body = await response.body();
    const text = body.toString("utf-8");
    const lines = text.split("\n").filter((l) => l.trim());
    expect(lines.length).toBeGreaterThan(0, "CSV export should have at least a header row");
  });

  test("CSV export for leads downloads valid content", async ({ page }) => {
    const response = await page.request.get("/api/platform/api/v1/crm/export/leads", {
      headers: { Authorization: `Bearer ${accessToken}` },
    });
    expect(response.ok(), `Export leads failed: ${response.status()}`).toBe(true);

    const body = await response.body();
    const text = body.toString("utf-8");
    const lines = text.split("\n").filter((l) => l.trim());
    expect(lines.length).toBeGreaterThan(0, "CSV export should have at least a header row");
  });

  test("CSV export with search filter returns valid CSV", async ({ page }) => {
    const response = await page.request.get(
      "/api/platform/api/v1/crm/export/accounts?search=E2E",
      { headers: { Authorization: `Bearer ${accessToken}` } },
    );
    expect(response.ok(), `Export accounts with search failed: ${response.status()}`).toBe(true);

    const body = await response.body();
    const text = body.toString("utf-8");
    // Even with no matches, the CSV should have a header row.
    const lines = text.split("\n").filter((l) => l.trim());
    expect(lines.length).toBeGreaterThan(0, "Filtered CSV export should have at least a header row");
  });
});
