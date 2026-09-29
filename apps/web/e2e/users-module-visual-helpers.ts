import { expect, type Page, type TestInfo } from "@playwright/test";
import { appendFile, mkdir } from "node:fs/promises";
import path from "node:path";

const ROOT = path.resolve("test-results/users-module-visual-evidence");
const DIAGNOSTICS_ROOT = path.resolve("test-results/users-module-runtime-diagnostics");

export const EXECUTIVE_USERS_TERMINAL_TEST_IDS = [
  "access-check-failed",
  "access-denied",
  "users-load-failed",
  "executive-users-ready",
] as const;

type ExecutiveUsersTerminalState = (typeof EXECUTIVE_USERS_TERMINAL_TEST_IDS)[number] | "terminal-timeout";

async function persistExecutiveUsersDiagnostic(
  page: Page,
  state: ExecutiveUsersTerminalState,
  testInfo?: TestInfo,
) {
  await mkdir(DIAGNOSTICS_ROOT, { recursive: true });
  const record = {
    sha: process.env.USERS_CANDIDATE_SHA ?? process.env.GITHUB_SHA ?? "local",
    project: testInfo?.project.name ?? "unknown-project",
    route: new URL(page.url()).pathname,
    state,
  };
  await appendFile(
    path.join(DIAGNOSTICS_ROOT, "terminal-state.ndjson"),
    `${JSON.stringify(record)}\n`,
    "utf8",
  );
}

export async function expectExecutiveUsersReady(page: Page, testInfo?: TestInfo) {
  const terminal = page.locator(
    EXECUTIVE_USERS_TERMINAL_TEST_IDS.map((testId) => `[data-testid=\"${testId}\"]`).join(", "),
  );

  try {
    await expect(
      terminal.first(),
      `executive users must reach one deterministic terminal state: ${EXECUTIVE_USERS_TERMINAL_TEST_IDS.join(", ")}`,
    ).toBeVisible();
  } catch (reason) {
    await persistExecutiveUsersDiagnostic(page, "terminal-timeout", testInfo);
    throw reason;
  }

  const visibleState = await terminal.evaluateAll((elements) => {
    const visible = elements.find((element) => {
      const html = element as HTMLElement;
      const style = window.getComputedStyle(html);
      return style.visibility !== "hidden" && style.display !== "none" && html.getClientRects().length > 0;
    });
    return visible?.getAttribute("data-testid") ?? null;
  });

  if (visibleState && EXECUTIVE_USERS_TERMINAL_TEST_IDS.includes(visibleState as (typeof EXECUTIVE_USERS_TERMINAL_TEST_IDS)[number])) {
    await persistExecutiveUsersDiagnostic(
      page,
      visibleState as (typeof EXECUTIVE_USERS_TERMINAL_TEST_IDS)[number],
      testInfo,
    );
  }

  expect(
    visibleState,
    `executive users terminal runtime state was ${visibleState ?? "unknown"}`,
  ).toBe("executive-users-ready");
}

export async function captureUsersEvidence(
  page: Page,
  testInfo: TestInfo,
  name: string,
  readySelector: string,
  expectedTenantId: string,
  accessToken: string,
) {
  if (name === "executive-users") {
    await expectExecutiveUsersReady(page, testInfo);
  } else {
    await expect(page.locator(readySelector).first()).toBeVisible();
  }

  const auth = await page.request.get("/api/platform/api/v1/auth/me", {
    headers: { Authorization: `Bearer ${accessToken}` },
  });
  expect(auth.ok(), `auth/me failed before ${name} evidence capture`).toBe(true);
  const authBody = await auth.json() as { tenantId?: string; user?: { tenantId?: string; email?: string }; email?: string };
  const tenantId = authBody.tenantId ?? authBody.user?.tenantId;
  expect(tenantId).toBe(expectedTenantId);

  const device = testInfo.project.name.includes("mobile") ? "mobile" : "desktop";
  const dir = path.join(ROOT, device);
  await mkdir(dir, { recursive: true });
  const relative = `${device}/${name}.png`;
  await page.screenshot({ path: path.join(ROOT, relative), fullPage: true });

  const record = {
    sha: process.env.USERS_CANDIDATE_SHA ?? process.env.GITHUB_SHA ?? "local",
    project: testInfo.project.name,
    device,
    name,
    route: new URL(page.url()).pathname,
    tenantId,
    identity: authBody.email ?? authBody.user?.email ?? null,
    file: relative,
  };
  await appendFile(path.join(ROOT, "manifest.ndjson"), `${JSON.stringify(record)}\n`, "utf8");
}
