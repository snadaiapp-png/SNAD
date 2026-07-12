#!/usr/bin/env python3
from __future__ import annotations

from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[2]
E2E = ROOT / "apps/web/e2e"


def read(name: str) -> str:
    return (E2E / name).read_text(encoding="utf-8")


def write(name: str, value: str) -> None:
    (E2E / name).write_text(value, encoding="utf-8")


write(
    "crm-auth-session.ts",
    '''import { expect, type Page } from "@playwright/test";

export interface CrmLoginResponse {
  accessToken: string;
  expiresAt: string;
  user: {
    id: string;
    tenantId: string;
    email: string;
    displayName: string | null;
    status: string;
  };
}

/**
 * Authenticate through the real SNAD login form so AuthProvider owns the
 * in-memory access token and the BFF refresh cookie is stored in this exact
 * browser context. Direct API login alone cannot authenticate the SPA because
 * access tokens are intentionally never persisted in browser storage.
 */
export async function loginThroughUi(
  page: Page,
  email: string,
  password: string,
): Promise<CrmLoginResponse> {
  expect(email, "CRM login email must be configured").toBeTruthy();
  expect(password, "CRM login password must be configured").toBeTruthy();

  await page.goto("/");
  await page.locator("#login-email").fill(email);
  await page.locator("#login-password").fill(password);

  const responsePromise = page.waitForResponse(
    (response) =>
      response.request().method() === "POST" &&
      response.url().includes("/api/platform/api/v1/auth/login"),
    { timeout: 30_000 },
  );

  await page.locator('form button[type="submit"]').click();
  const response = await responsePromise;
  expect(response.ok(), `Login failed for ${email}: ${response.status()} ${response.statusText()}`).toBe(true);

  const body = (await response.json()) as CrmLoginResponse;
  expect(body.accessToken, `Login response for ${email} is missing accessToken`).toBeTruthy();
  expect(body.user?.tenantId, `Login response for ${email} is missing tenantId`).toBeTruthy();

  await page.waitForURL(/\/workspace(?:[/?#]|$)/, { timeout: 30_000 });
  const refreshCookie = (await page.context().cookies()).find((cookie) => cookie.name === "sanad_refresh");
  expect(refreshCookie, `Refresh cookie was not created for ${email}`).toBeTruthy();

  return body;
}
''',
)

# Happy-path acceptance.
name = "crm-authenticated-acceptance.spec.ts"
text = read(name)
text = text.replace(
    'import { test, expect, type APIResponse, type Page } from "@playwright/test";',
    'import { test, expect, type Page } from "@playwright/test";\nimport { loginThroughUi as loginViaBFF } from "./crm-auth-session";',
)
text = re.sub(r"\ninterface LoginResponse \{.*?\n\}\n", "\n", text, flags=re.S)
text = re.sub(
    r"\n/\*\*\n \* Login via the BFF proxy.*?\nasync function loginViaBFF\(.*?\n\}\n",
    "\n",
    text,
    flags=re.S,
)
text = text.replace(
    "    const login = await loginViaBFF(page, TENANT_A_EMAIL, TENANT_A_PASSWORD);\n    accessToken = login.accessToken;\n    // Smoke-check the token by hitting /me via the BFF.",
    "    expect(accessToken, \"beforeEach must establish the Tenant A browser session\").toBeTruthy();\n    // Smoke-check the token by hitting /me via the BFF.",
    1,
)
write(name, text)

# Accessibility suite.
name = "crm-accessibility.spec.ts"
text = read(name)
text = text.replace(
    'import { test, expect, type APIResponse, type Page } from "@playwright/test";',
    'import { test, expect, type Page } from "@playwright/test";\nimport { loginThroughUi } from "./crm-auth-session";',
)
text = re.sub(r"\ninterface LoginResponse \{.*?\n\}\n", "\n", text, flags=re.S)
text = re.sub(r"\nasync function loginAsAdmin\(.*?\n\}\n", "\n", text, flags=re.S)
text = re.sub(
    r'''\n  test\("login as Tenant A admin \(shared setup\)", async \(\{ page \}\) => \{.*?\n  \}\);\n''',
    "\n  test.beforeEach(async ({ page }) => {\n    await loginThroughUi(page, TENANT_A_EMAIL, TENANT_A_PASSWORD);\n  });\n",
    text,
    flags=re.S,
)
write(name, text)

# Strict route smoke.
name = "crm-route-smoke.spec.ts"
text = read(name)
text = text.replace(
    'import { test, expect, type APIResponse, type Page } from "@playwright/test";',
    'import { test, expect, type Page } from "@playwright/test";\nimport { loginThroughUi } from "./crm-auth-session";',
)
text = re.sub(r"\ninterface LoginResponse \{.*?\n\}\n", "\n", text, flags=re.S)
text = re.sub(r"\nasync function loginAsAdmin\(.*?\n\}\n", "\n", text, flags=re.S)
text = text.replace(
    "    await loginAsAdmin(page);",
    "    await loginThroughUi(page, TENANT_A_EMAIL, TENANT_A_PASSWORD);",
)
write(name, text)

# Tenant isolation.
name = "crm-tenant-isolation.spec.ts"
text = read(name)
text = text.replace(
    'import { test, expect, type APIResponse, type Page } from "@playwright/test";',
    'import { test, expect, type Page } from "@playwright/test";\nimport { loginThroughUi } from "./crm-auth-session";',
)
text = re.sub(r"\ninterface LoginResponse \{.*?\n\}\n", "\n", text, flags=re.S)
text = re.sub(r"\nasync function loginTenantB\(.*?\n\}\n", "\n", text, flags=re.S)
text = re.sub(
    r'''\n  test\.beforeAll\(async \(\{ browser \}\) => \{.*?\n  \}\);\n''',
    '''\n  test.beforeEach(async ({ page }) => {\n    const login = await loginThroughUi(page, TENANT_B_EMAIL, TENANT_B_PASSWORD);\n    expect(login.user.tenantId, "Tenant B must not resolve to Tenant A").not.toBe(\n      "11111111-1111-4111-8111-111111111111",\n    );\n    accessToken = login.accessToken;\n  });\n''',
    text,
    flags=re.S,
)
write(name, text)

# RBAC suite: authenticate every fresh browser context for its role.
name = "crm-rbac-acceptance.spec.ts"
text = read(name)
text = text.replace(
    'import { test, expect, type APIResponse, type Page } from "@playwright/test";',
    'import { test, expect, type Page } from "@playwright/test";\nimport { loginThroughUi } from "./crm-auth-session";',
)
text = re.sub(r"\ninterface LoginResponse \{.*?\n\}\n", "\n", text, flags=re.S)
text = re.sub(
    r"async function login\(page: Page, email: string, password: string\): Promise<string> \{.*?\n\}",
    '''async function login(page: Page, email: string, password: string): Promise<string> {\n  return (await loginThroughUi(page, email, password)).accessToken;\n}''',
    text,
    flags=re.S,
)
text = text.replace(
    "    let accessToken: string;\n\n    test(\"login as read-only user\"",
    "    let accessToken: string;\n\n    test.beforeEach(async ({ page }) => {\n      accessToken = await login(page, READONLY_EMAIL, READONLY_PASSWORD);\n    });\n\n    test(\"login as read-only user\"",
)
text = text.replace(
    "      accessToken = await login(page, READONLY_EMAIL, READONLY_PASSWORD);\n      expect(accessToken).toBeTruthy();",
    "      expect(accessToken).toBeTruthy();",
    1,
)
text = text.replace(
    "    let accessToken: string;\n    let leadId: string;\n\n    test(\"login as lead-writer user\"",
    "    let accessToken: string;\n    let leadId: string;\n\n    test.beforeEach(async ({ page }) => {\n      accessToken = await login(page, LEAD_WRITER_EMAIL, LEAD_WRITER_PASSWORD);\n    });\n\n    test(\"login as lead-writer user\"",
)
text = text.replace(
    "      accessToken = await login(page, LEAD_WRITER_EMAIL, LEAD_WRITER_PASSWORD);\n      expect(accessToken).toBeTruthy();",
    "      expect(accessToken).toBeTruthy();",
    1,
)
text = text.replace(
    "    let accessToken: string;\n\n    test(\"login as import-reader user\"",
    "    let accessToken: string;\n\n    test.beforeEach(async ({ page }) => {\n      accessToken = await login(page, IMPORT_READER_EMAIL, IMPORT_READER_PASSWORD);\n    });\n\n    test(\"login as import-reader user\"",
)
text = text.replace(
    "      accessToken = await login(page, IMPORT_READER_EMAIL, IMPORT_READER_PASSWORD);\n      expect(accessToken).toBeTruthy();",
    "      expect(accessToken).toBeTruthy();",
    1,
)
write(name, text)

# Remove this patcher after it is executed by the governed operator.
Path(__file__).unlink()
