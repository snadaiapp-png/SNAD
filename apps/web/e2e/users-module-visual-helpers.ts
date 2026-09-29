import { expect, type Page, type TestInfo } from "@playwright/test";
import { appendFile, mkdir } from "node:fs/promises";
import path from "node:path";

const ROOT = path.resolve("test-results/users-module-visual-evidence");
const DIAGNOSTICS_ROOT = path.resolve("test-results/users-module-runtime-diagnostics");
const ACCESS_CHECK_PATH = "/api/platform/api/v1/control-plane/access-check/v2";

export const EXECUTIVE_USERS_TERMINAL_TEST_IDS = [
  "access-check-failed",
  "access-denied",
  "users-load-failed",
  "executive-users-ready",
] as const;

type ExecutiveUsersTerminalState = (typeof EXECUTIVE_USERS_TERMINAL_TEST_IDS)[number] | "terminal-timeout";

type SafeAccessCheckBody = {
  error?: string;
  code?: string;
  message?: string;
};

async function appendDiagnostic(fileName: string, record: Record<string, unknown>) {
  await mkdir(DIAGNOSTICS_ROOT, { recursive: true });
  await appendFile(
    path.join(DIAGNOSTICS_ROOT, fileName),
    `${JSON.stringify(record)}\n`,
    "utf8",
  );
}

async function persistExecutiveUsersDiagnostic(
  page: Page,
  state: ExecutiveUsersTerminalState,
  testInfo?: TestInfo,
) {
  await appendDiagnostic("terminal-state.ndjson", {
    sha: process.env.USERS_CANDIDATE_SHA ?? process.env.GITHUB_SHA ?? "local",
    project: testInfo?.project.name ?? "unknown-project",
    route: new URL(page.url()).pathname,
    state,
  });
}

function safeText(value: unknown) {
  return typeof value === "string" ? value.slice(0, 500) : undefined;
}

function sanitizeAccessCheckBody(value: unknown): SafeAccessCheckBody | null {
  if (!value || typeof value !== "object" || Array.isArray(value)) return null;
  const body = value as Record<string, unknown>;
  const safe: SafeAccessCheckBody = {
    error: safeText(body.error),
    code: safeText(body.code),
    message: safeText(body.message),
  };
  return Object.values(safe).some(Boolean) ? safe : null;
}

export async function probeAccessCheckV2(page: Page, accessToken: string, testInfo?: TestInfo) {
  const base = {
    sha: process.env.USERS_CANDIDATE_SHA ?? process.env.GITHUB_SHA ?? "local",
    project: testInfo?.project.name ?? "unknown-project",
    path: ACCESS_CHECK_PATH,
  };

  try {
    const response = await page.request.get(ACCESS_CHECK_PATH, {
      headers: {
        Authorization: `Bearer ${accessToken}`,
        Accept: "application/json",
      },
    });

    const contentType = response.headers()["content-type"] ?? null;
    let safeBody: SafeAccessCheckBody | null = null;
    if (contentType?.includes("application/json")) {
      try {
        safeBody = sanitizeAccessCheckBody(await response.json());
      } catch {
        safeBody = null;
      }
    }

    await appendDiagnostic("access-check-v2.ndjson", {
      ...base,
      phase: "direct-authenticated-probe",
      status: response.status(),
      statusText: response.statusText(),
      contentType,
      body: safeBody,
    });

    return response.status();
  } catch (reason) {
    await appendDiagnostic("access-check-v2.ndjson", {
      ...base,
      phase: "direct-authenticated-probe",
      requestError: safeText(reason instanceof Error ? reason.message : String(reason)),
    });
    throw reason;
  }
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
