import { expect, type Page, type TestInfo } from "@playwright/test";
import { appendFile, mkdir, readFile } from "node:fs/promises";
import path from "node:path";

const ROOT = path.resolve("test-results/users-module-visual-evidence");
const DIAGNOSTICS_ROOT = path.resolve("test-results/users-module-runtime-diagnostics");
const ACCESS_CHECK_PATH = "/api/platform/api/v1/executive/access-check/v2";
const PLATFORM_USERS_PATH = "/api/platform/api/v1/executive/users";

export const EXECUTIVE_USERS_TERMINAL_TEST_IDS = [
  "access-check-failed",
  "access-denied",
  "users-load-failed",
  "executive-users-ready",
] as const;

type ExecutiveUsersTerminalState = (typeof EXECUTIVE_USERS_TERMINAL_TEST_IDS)[number] | "terminal-timeout";

type SafeDiagnosticBody = {
  error?: string;
  code?: string;
  message?: string;
  correlationId?: string;
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

function sanitizeDiagnosticBody(value: unknown): SafeDiagnosticBody | null {
  if (!value || typeof value !== "object" || Array.isArray(value)) return null;
  const body = value as Record<string, unknown>;
  const safe: SafeDiagnosticBody = {
    error: safeText(body.error),
    code: safeText(body.code),
    message: safeText(body.message),
    correlationId: safeText(body.correlationId),
  };
  return Object.values(safe).some(Boolean) ? safe : null;
}

function redactDiagnosticLine(line: string) {
  return line
    .replace(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/gi, "[redacted-email]")
    .replace(/(authorization|password|token|secret)(\s*[=:]\s*)\S+/gi, "$1$2[redacted]")
    .slice(0, 2000);
}

async function persistCorrelatedBackendException(correlationId: string, testInfo?: TestInfo) {
  const runnerTemp = process.env.RUNNER_TEMP;
  if (!runnerTemp) return;

  const backendLog = path.join(runnerTemp, "users-module-backend.log");
  try {
    const text = await readFile(backendLog, "utf8");
    const lines = text.split(/\r?\n/);
    const start = lines.findIndex((line) => line.includes(`correlationId=${correlationId}`));
    if (start < 0) {
      await appendDiagnostic("backend-5xx.ndjson", {
        sha: process.env.USERS_CANDIDATE_SHA ?? process.env.GITHUB_SHA ?? "local",
        project: testInfo?.project.name ?? "unknown-project",
        correlationId,
        matched: false,
      });
      return;
    }

    const excerpt = lines
      .slice(start, Math.min(lines.length, start + 80))
      .filter((line, index) =>
        index === 0
        || line.startsWith("Caused by:")
        || /^\s+at com\.sanad\./.test(line)
        || /^\s+at org\.springframework\./.test(line)
        || /^\s+at org\.hibernate\./.test(line)
        || /^\s*org\.(postgresql|hibernate)\..*(Exception|Error)/.test(line)
        || /^\s*(java|jakarta)\..*(Exception|Error)/.test(line))
      .slice(0, 40)
      .map(redactDiagnosticLine);

    await appendDiagnostic("backend-5xx.ndjson", {
      sha: process.env.USERS_CANDIDATE_SHA ?? process.env.GITHUB_SHA ?? "local",
      project: testInfo?.project.name ?? "unknown-project",
      correlationId,
      matched: true,
      excerpt,
    });
  } catch (reason) {
    await appendDiagnostic("backend-5xx.ndjson", {
      sha: process.env.USERS_CANDIDATE_SHA ?? process.env.GITHUB_SHA ?? "local",
      project: testInfo?.project.name ?? "unknown-project",
      correlationId,
      matched: false,
      diagnosticReadError: safeText(reason instanceof Error ? reason.message : String(reason)),
    });
  }
}

async function probeAuthenticatedEndpoint(
  page: Page,
  accessToken: string,
  testInfo: TestInfo | undefined,
  pathName: string,
  diagnosticFileName: string,
) {
  const base = {
    sha: process.env.USERS_CANDIDATE_SHA ?? process.env.GITHUB_SHA ?? "local",
    project: testInfo?.project.name ?? "unknown-project",
    path: pathName,
  };

  try {
    const response = await page.request.get(pathName, {
      headers: {
        Authorization: `Bearer ${accessToken}`,
        Accept: "application/json",
      },
    });

    const contentType = response.headers()["content-type"] ?? null;
    let safeBody: SafeDiagnosticBody | null = null;
    if (contentType?.includes("application/json")) {
      try {
        safeBody = sanitizeDiagnosticBody(await response.json());
      } catch {
        safeBody = null;
      }
    }

    await appendDiagnostic(diagnosticFileName, {
      ...base,
      phase: "direct-authenticated-probe",
      status: response.status(),
      statusText: response.statusText(),
      contentType,
      body: safeBody,
    });

    if (response.status() >= 500 && safeBody?.correlationId) {
      await persistCorrelatedBackendException(safeBody.correlationId, testInfo);
    }

    return response.status();
  } catch (reason) {
    await appendDiagnostic(diagnosticFileName, {
      ...base,
      phase: "direct-authenticated-probe",
      requestError: safeText(reason instanceof Error ? reason.message : String(reason)),
    });
    throw reason;
  }
}

export async function probeAccessCheckV2(page: Page, accessToken: string, testInfo?: TestInfo) {
  return probeAuthenticatedEndpoint(page, accessToken, testInfo, ACCESS_CHECK_PATH, "access-check-v2.ndjson");
}

export async function probePlatformUsers(page: Page, accessToken: string, testInfo?: TestInfo) {
  return probeAuthenticatedEndpoint(page, accessToken, testInfo, PLATFORM_USERS_PATH, "platform-users.ndjson");
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
