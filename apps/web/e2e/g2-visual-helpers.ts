import { appendFile, mkdir } from "node:fs/promises";
import { dirname, join } from "node:path";
import { expect, type Page } from "@playwright/test";

export type G2Role = "employee" | "manager" | "hr";

export interface VisualSurface {
  route: string;
  readyTestId: string;
}

interface VisualDiagnostics {
  pageErrors: string[];
  failedHrResponses: string[];
}

const diagnostics = new WeakMap<Page, VisualDiagnostics>();

export function initializeVisualDiagnostics(page: Page): void {
  if (diagnostics.has(page)) return;
  const state: VisualDiagnostics = { pageErrors: [], failedHrResponses: [] };
  diagnostics.set(page, state);
  page.on("pageerror", (error) => state.pageErrors.push(error.message));
  page.on("response", (response) => {
    const url = response.url();
    if (url.includes("/api/v2/hr/") && response.status() >= 400) {
      state.failedHrResponses.push(`${response.status()} ${response.request().method()} ${url}`);
    }
  });
}

export async function assertVisualSurface(page: Page, surface: VisualSurface): Promise<void> {
  const state = diagnostics.get(page);
  expect(state, "initializeVisualDiagnostics(page) must run before route navigation").toBeDefined();
  await expect(page.getByTestId(surface.readyTestId)).toBeVisible({ timeout: 20_000 });
  await expect(page.locator("main").first()).toBeVisible();
  expect(state!.pageErrors, `page errors on ${surface.route}: ${state!.pageErrors.join(" | ")}`).toEqual([]);
  expect(state!.failedHrResponses, `failed HR API responses on ${surface.route}: ${state!.failedHrResponses.join(" | ")}`).toEqual([]);
  const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
  expect(overflow, `page-level horizontal overflow on ${surface.route}`).toBeLessThanOrEqual(1);
  state!.pageErrors.length = 0;
  state!.failedHrResponses.length = 0;
}

function safeRoute(route: string): string {
  return route === "/hr" ? "hr" : route.replace(/^\//, "").replaceAll("/", "-");
}

export async function captureVisualEvidence(
  page: Page,
  role: G2Role,
  route: string,
  projectName: string,
): Promise<void> {
  const candidateSha = process.env.GITHUB_HEAD_SHA ?? process.env.GITHUB_SHA ?? "local";
  const evidenceRoot = join("test-results", "g2-visual-evidence");
  const screenshot = join(evidenceRoot, `${candidateSha}-${role}-${projectName}-${safeRoute(route)}.png`);
  const manifest = join(evidenceRoot, "manifest.ndjson");
  await mkdir(dirname(screenshot), { recursive: true });
  await page.screenshot({ path: screenshot, fullPage: true });
  await appendFile(
    manifest,
    `${JSON.stringify({ sha: candidateSha, role, viewport: projectName, route, screenshot, result: "PASS" })}\n`,
    "utf8",
  );
}
