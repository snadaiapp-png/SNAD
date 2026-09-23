# SNAD Login v2 — Official Brand + Authentication Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship SNAD Login v2 Phase A with the user-approved official wordmark, corrected responsive/accessibility behavior, safe nested executive return URLs, and regression evidence that preserves the existing multi-tenant/session security model.

**Architecture:** Keep the existing `AuthEntry → LoginScreen → LoginForm` flow and the existing backend authentication/session model. Introduce the approved logo only through the centralized `SnadLogo` SDS component, fix the concrete client-side destination normalization gap for nested `/executive/**` routes, and strengthen UI/accessibility behavior without duplicating auth logic. Backend production code changes are not part of Phase A unless a newly added regression test proves an existing control is broken; any such failure is a release blocker and must be fixed minimally before continuing.

**Tech Stack:** Next.js 16.3, React 19.2, TypeScript 5.9, CSS Modules/SNAD SDS tokens, Vitest 4.1, Testing Library, Playwright 1.61, Axe, Python 3 CI lint, Java 17 bytecode on JDK 21 CI, Spring Boot 3.5.6, Maven, PostgreSQL Direct.

**Spec:** `docs/superpowers/specs/2026-09-23-snad-auth-login-v2-official-brand-design.md`

## Global Constraints

- Baseline for this design cycle: `7a8399e25fe97758ee1e0ff6df512d18f8654da1` on `main`; implementation must first rebase/refresh against current `origin/main` and record the exact implementation base SHA before product-code edits.
- PostgreSQL Direct only. Docker and Testcontainers are out of scope.
- Approved auth wordmark source: `/mnt/data/snad-logo-official-web.png`, PNG RGBA `1162×337`, SHA-256 `98d0b84b0675b53f60837f803cf0a4bc85209eea650b213adba92a661bfde251`.
- Do not redraw, recolor, trace, vectorize, stretch, crop further, add effects to, or synthesize light/dark/compact variants of the approved wordmark.
- All application use of brand artwork must go through `apps/web/components/sds/SnadLogo.tsx`; no raw brand-asset path in auth components.
- Authentication does not grant authorization. Tenant, role, capability, executive status, and post-login destinations remain server-derived.
- `returnUrl` is navigation intent only; it must be both syntactically internal and present in the authenticated user’s effective destination roots.
- No refresh/access secrets may be migrated to `localStorage` or `sessionStorage`.
- Preserve current multi-tab refresh serialization behavior and existing BFF/token transport.
- RTL-first UI; email/password remain `dir="ltr"`; new layout CSS uses logical properties.
- WCAG 2.2 AA; interactive controls target at least `44×44px`; primary form controls are at least `48px` high.
- No raw backend errors, stack traces, internal URLs, or account-enumeration messages are exposed to users.
- MFA/passkeys/SSO are Phase B and are not to be simulated or represented as active controls in Phase A.

## Review Focus

1. **Nested privileged destination:** `/executive/tenants` must survive login only when `/executive` is in server-provided `availableDestinations`; otherwise it must fall back to `/workspace`.
2. **Encoded redirect input:** protocol-relative, external, backslash, control-character, and encoded unsafe destination inputs must never become a redirect target.
3. **Short/small viewport:** at `360×640`, `1280×720`, and 200% zoom the logo, fields, forgot link, submit button, and errors must remain reachable without horizontal scrolling.
4. **Brand source integrity:** the shipped PNG bytes must match SHA-256 `98d0b84b...de251`; no generated white/SVG variant may appear in the change.
5. **Credential correction flow:** validation errors must clear as the user edits the affected field; Caps Lock warning must be advisory only and must not block paste, password-manager fill, or form submission.

---

## File Structure

### Brand/SDS ownership
- Create `apps/web/public/assets/brand/snad-logo-official-wordmark.png` — exact approved PNG bytes.
- Modify `apps/web/components/sds/SnadLogo.tsx` — add `official-wordmark` variant and intrinsic dimensions.
- Modify `apps/web/components/sds/__tests__/SnadLogo.test.tsx` — pin variant path/aspect behavior.
- Modify `scripts/ci/check-logo-governance.py` — govern PNG brand paths as well as SVG.
- Create `scripts/ci/test_check_logo_governance.py` — regression test that a direct PNG reference outside SDS is rejected.

### Auth UI
- Modify `apps/web/components/auth/login-form.tsx` — use official wordmark, clear field errors on edit, add Caps Lock advisory state.
- Modify `apps/web/components/auth/login-form.test.tsx` — component behavior coverage.
- Modify `apps/web/lib/i18n/locales/ar.ts` — Arabic Caps Lock message.
- Modify `apps/web/lib/i18n/locales/en.ts` — English Caps Lock message.
- Modify `apps/web/components/auth/auth.module.css` — 48px inputs, 44px password toggle, `dvh` fallback, logical positioning, Caps Lock styling.

### Destination safety
- Modify `apps/web/lib/auth/destination.ts` — normalize nested `/executive/**` to `/executive` root.
- Modify `apps/web/lib/auth/destination.test.ts` — privileged nested route + encoded unsafe cases.
- Modify `apps/web/components/auth/auth-entry.test.tsx` — authenticated nested executive redirect and unauthorized fallback.

### Browser/security evidence
- Create `apps/web/e2e/auth-login-v2.spec.ts` — login page brand, accessibility, viewport, safe redirect checks.
- Reuse `apps/web/e2e/crm-auth-session.ts` — no duplicate login helper.
- Reuse existing tenant/executive specs for cross-tenant/control-plane acceptance.

### Documentation/governance
- Modify `apps/web/public/assets/brand/README.md`.
- Modify `apps/web/design-system/documentation/LOGO_USAGE.md`.
- Modify `apps/web/design-system/documentation/AUTH_UI_GUIDE.md`.
- Create `apps/web/design-system/documentation/CHANGELOG.md` because the process references it but it is absent on the baseline branch.
- Create `evidence/auth-login-v2/README.md` — exact SHA, commands, pass/fail matrix, and screenshot artifact names only after tests actually run.

---

### Task 1: Govern and render the approved official wordmark

**Files:**
- Create: `apps/web/public/assets/brand/snad-logo-official-wordmark.png`
- Modify: `apps/web/components/sds/SnadLogo.tsx`
- Modify: `apps/web/components/sds/__tests__/SnadLogo.test.tsx`
- Modify: `scripts/ci/check-logo-governance.py`
- Create: `scripts/ci/test_check_logo_governance.py`

**Interfaces:**
- Consumes: approved binary at `/mnt/data/snad-logo-official-web.png`.
- Produces: `SnadLogoVariant` value `"official-wordmark"`; governed public path `/assets/brand/snad-logo-official-wordmark.png`.

- [ ] **Step 1: Write the failing `SnadLogo` variant test**

Add to the variant matrix in `SnadLogo.test.tsx`:

```tsx
["official-wordmark", "/assets/brand/snad-logo-official-wordmark.png"],
```

Add a dedicated intrinsic-ratio assertion:

```tsx
it("preserves the approved official wordmark aspect ratio", () => {
  const { container } = render(<SnadLogo variant="official-wordmark" />);
  const img = container.querySelector("img");
  expect(img?.getAttribute("style") ?? "").toMatch(/aspect-ratio:\s*3\.44/);
});
```

- [ ] **Step 2: Run the focused component test and verify RED**

Run from `apps/web`:

```bash
npm test -- components/sds/__tests__/SnadLogo.test.tsx
```

Expected: FAIL because `official-wordmark` is not yet a valid `SnadLogoVariant`/mapping.

- [ ] **Step 3: Copy the exact approved PNG and verify the bytes before code changes**

```bash
cp /mnt/data/snad-logo-official-web.png public/assets/brand/snad-logo-official-wordmark.png
sha256sum public/assets/brand/snad-logo-official-wordmark.png
file public/assets/brand/snad-logo-official-wordmark.png
```

Expected SHA-256:

```text
98d0b84b0675b53f60837f803cf0a4bc85209eea650b213adba92a661bfde251
```

Expected image metadata includes:

```text
PNG image data, 1162 x 337, 8-bit/color RGBA
```

If either value differs, stop; do not substitute or regenerate artwork.

- [ ] **Step 4: Implement the minimal SDS variant**

In `SnadLogo.tsx`, extend the union and maps:

```tsx
export type SnadLogoVariant =
  | 'primary'
  | 'horizontal'
  | 'compact'
  | 'white'
  | 'monochrome'
  | 'app-icon'
  | 'official-wordmark';

const VARIANT_SRC: Record<SnadLogoVariant, string> = {
  primary: '/assets/brand/snad-logo-primary.svg',
  horizontal: '/assets/brand/snad-logo-primary.svg',
  compact: '/assets/brand/snad-favicon.svg',
  white: '/assets/brand/snad-logo-white.svg',
  monochrome: '/assets/brand/snad-logo-mono.svg',
  'app-icon': '/assets/brand/snad-app-icon.svg',
  'official-wordmark': '/assets/brand/snad-logo-official-wordmark.png',
};

const VARIANT_ASPECT: Record<SnadLogoVariant, number> = {
  primary: 280 / 80,
  horizontal: 280 / 80,
  compact: 32 / 32,
  white: 280 / 80,
  monochrome: 280 / 80,
  'app-icon': 512 / 512,
  'official-wordmark': 1162 / 337,
};
```

Update the intrinsic-dimension branch so `official-wordmark` uses `1162×337` rather than the default `280×80`:

```tsx
const intrinsicWidth =
  variantName === 'compact'
    ? 32
    : variantName === 'app-icon'
      ? 512
      : variantName === 'official-wordmark'
        ? 1162
        : 280;
const intrinsicHeight =
  variantName === 'compact'
    ? 32
    : variantName === 'app-icon'
      ? 512
      : variantName === 'official-wordmark'
        ? 337
        : 80;
```

Do not add this raster variant to `theme="auto"`; it is selected explicitly by auth surfaces only.

- [ ] **Step 5: Write the failing governance regression test for direct PNG usage**

Create `scripts/ci/test_check_logo_governance.py` using `unittest` and import the hyphenated script with `importlib.util`:

```python
import importlib.util
import tempfile
import unittest
from pathlib import Path

SCRIPT = Path(__file__).with_name("check-logo-governance.py")
spec = importlib.util.spec_from_file_location("logo_governance", SCRIPT)
module = importlib.util.module_from_spec(spec)
assert spec and spec.loader
spec.loader.exec_module(module)

class LogoGovernanceTest(unittest.TestCase):
    def test_direct_official_png_reference_is_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            file = root / "login.tsx"
            file.write_text(
                'export const x = <img src="/assets/brand/snad-logo-official-wordmark.png" />;',
                encoding="utf-8",
            )
            violations = module.check_direct_brand_references(file, "apps/web/login.tsx")
            self.assertTrue(violations)

if __name__ == "__main__":
    unittest.main()
```

Run:

```bash
python3 scripts/ci/test_check_logo_governance.py
```

Expected: FAIL before the governance regex is generalized beyond SVG.

- [ ] **Step 6: Generalize logo governance to SVG and PNG brand assets**

Replace the SVG-only path regex family with an asset regex that accepts both extensions:

```python
BRAND_ASSET_RE = re.compile(
    r"[/\\]assets[/\\]brand[/\\]snad-(?:logo-[a-z0-9-]+|favicon|app-icon)\.(?:svg|png)",
    re.IGNORECASE,
)
```

Rename the direct-reference helper messages from `*_BRAND_SVG` to `*_BRAND_ASSET`, and update import/img/string regexes to `\.(?:svg|png)`. Keep `SnadLogo.tsx` and its test as the only direct-path allowlist entries.

- [ ] **Step 7: Run brand unit/governance checks GREEN**

```bash
python3 scripts/ci/test_check_logo_governance.py
python3 scripts/ci/check-logo-governance.py apps/web
cd apps/web && npm test -- components/sds/__tests__/SnadLogo.test.tsx
```

Expected: all PASS; governance reports zero violations.

- [ ] **Step 8: Commit Task 1**

```bash
git add apps/web/public/assets/brand/snad-logo-official-wordmark.png \
  apps/web/components/sds/SnadLogo.tsx \
  apps/web/components/sds/__tests__/SnadLogo.test.tsx \
  scripts/ci/check-logo-governance.py \
  scripts/ci/test_check_logo_governance.py
git commit -m "feat(auth): add governed official SNAD wordmark"
```

---

### Task 2: Put the official wordmark in the canonical login form and fix correction UX

**Files:**
- Modify: `apps/web/components/auth/login-form.tsx`
- Modify: `apps/web/components/auth/login-form.test.tsx`
- Modify: `apps/web/lib/i18n/locales/ar.ts`
- Modify: `apps/web/lib/i18n/locales/en.ts`

**Interfaces:**
- Consumes: `SnadLogo variant="official-wordmark"` from Task 1.
- Produces: canonical login behavior with field-error clearing and advisory `capsLockOn` state.

- [ ] **Step 1: Add failing component tests**

Add these cases to `login-form.test.tsx`:

```tsx
it("renders the approved official wordmark on the login surface", () => {
  const { container } = renderLoginForm();
  expect(container.querySelector('img[src="/assets/brand/snad-logo-official-wordmark.png"]')).not.toBeNull();
});

it("clears the email validation error on the first corrective keystroke", async () => {
  const user = userEvent.setup();
  renderLoginForm();
  await user.click(screen.getByRole("button", { name: "تسجيل الدخول" }));
  expect(screen.getByText("البريد الإلكتروني مطلوب.")).toBeInTheDocument();
  await user.type(screen.getByPlaceholderText("name@company.com"), "a");
  expect(screen.queryByText("البريد الإلكتروني مطلوب.")).not.toBeInTheDocument();
});

it("clears the password validation error on the first corrective keystroke", async () => {
  const user = userEvent.setup();
  renderLoginForm();
  await user.type(screen.getByPlaceholderText("name@company.com"), "user@snad.app");
  await user.click(screen.getByRole("button", { name: "تسجيل الدخول" }));
  expect(screen.getByText("كلمة المرور مطلوبة.")).toBeInTheDocument();
  await user.type(screen.getByPlaceholderText("••••••••"), "x");
  expect(screen.queryByText("كلمة المرور مطلوبة.")).not.toBeInTheDocument();
});

it("shows and clears the Caps Lock advisory without disabling submit", () => {
  renderLoginForm();
  const password = screen.getByPlaceholderText("••••••••");
  fireEvent.keyDown(password, { key: "A", getModifierState: (key: string) => key === "CapsLock" });
  expect(screen.getByText("مفتاح الأحرف الكبيرة Caps Lock مفعّل.")).toBeInTheDocument();
  expect(screen.getByRole("button", { name: "تسجيل الدخول" })).not.toBeDisabled();
  fireEvent.keyUp(password, { key: "a", getModifierState: () => false });
  expect(screen.queryByText("مفتاح الأحرف الكبيرة Caps Lock مفعّل.")).not.toBeInTheDocument();
});
```

Also import `fireEvent` from Testing Library.

- [ ] **Step 2: Run the focused test and verify RED**

```bash
cd apps/web
npm test -- components/auth/login-form.test.tsx
```

Expected: failures for official asset, error-clearing, and Caps Lock behavior.

- [ ] **Step 3: Add the bilingual Caps Lock keys**

In `ar.ts`:

```ts
"auth.login.capsLockOn": "مفتاح الأحرف الكبيرة Caps Lock مفعّل.",
```

In `en.ts`:

```ts
"auth.login.capsLockOn": "Caps Lock is on.",
```

- [ ] **Step 4: Implement official logo + corrective error clearing + Caps Lock advisory**

In `login-form.tsx`, remove theme-dependent logo selection for this specific approved raster and render:

```tsx
<SnadLogo
  variant="official-wordmark"
  size="responsive"
  href="/"
  alt={t("auth.login.logoAlt")}
  priority
/>
```

Add state:

```tsx
const [capsLockOn, setCapsLockOn] = useState(false);
```

Change field handlers:

```tsx
onChange={(event) => {
  setEmail(event.target.value);
  if (emailError) setEmailError(null);
}}
```

```tsx
onChange={(event) => {
  setPassword(event.target.value);
  if (passwordError) setPasswordError(null);
}}
onKeyDown={(event) => setCapsLockOn(event.getModifierState("CapsLock"))}
onKeyUp={(event) => setCapsLockOn(event.getModifierState("CapsLock"))}
onBlur={() => setCapsLockOn(false)}
```

Render below the password wrapper and before the validation error:

```tsx
{capsLockOn && (
  <span className={styles.authAdvisory} role="status" aria-live="polite">
    {t("auth.login.capsLockOn")}
  </span>
)}
```

Do not add paste prevention or password-content logging.

- [ ] **Step 5: Run the focused test GREEN**

```bash
cd apps/web
npm test -- components/auth/login-form.test.tsx
```

Expected: PASS.

- [ ] **Step 6: Commit Task 2**

```bash
git add apps/web/components/auth/login-form.tsx \
  apps/web/components/auth/login-form.test.tsx \
  apps/web/lib/i18n/locales/ar.ts \
  apps/web/lib/i18n/locales/en.ts
git commit -m "feat(auth): apply official login brand and correction states"
```

---

### Task 3: Lock responsive/accessibility dimensions to the approved Login v2 contract

**Files:**
- Modify: `apps/web/components/auth/auth.module.css`
- Create: `apps/web/e2e/auth-login-v2.spec.ts` (first half: anonymous visual/accessibility checks)

**Interfaces:**
- Consumes: DOM ids/classes already emitted by `LoginForm`.
- Produces: viewport-safe auth layout; Playwright checks that later security cases extend.

- [ ] **Step 1: Write the failing browser checks for geometry and accessibility**

Create `apps/web/e2e/auth-login-v2.spec.ts`:

```ts
import { test, expect } from "@playwright/test";
import AxeBuilder from "@axe-core/playwright";

const BASE_URL = process.env.WEB_BASE_URL ?? "http://localhost:3000";

test.describe("SNAD Login v2", () => {
  test("shows the approved wordmark and keeps primary controls reachable on a short mobile viewport", async ({ page }) => {
    await page.setViewportSize({ width: 360, height: 640 });
    await page.goto(`${BASE_URL}/`, { waitUntil: "domcontentloaded" });

    const logo = page.locator('img[src="/assets/brand/snad-logo-official-wordmark.png"]');
    await expect(logo).toBeVisible();
    await expect(page.locator("#login-email")).toBeVisible();
    await expect(page.locator("#login-password")).toBeVisible();
    await expect(page.getByRole("button", { name: "تسجيل الدخول" })).toBeVisible();

    const overflow = await page.evaluate(() => document.documentElement.scrollWidth > document.documentElement.clientWidth);
    expect(overflow).toBe(false);
  });

  test("has no serious or critical axe violations on the anonymous login surface", async ({ page }) => {
    await page.goto(`${BASE_URL}/`, { waitUntil: "domcontentloaded" });
    const results = await new AxeBuilder({ page }).analyze();
    expect(results.violations.filter(v => ["serious", "critical"].includes(v.impact ?? ""))).toEqual([]);
  });
});
```

- [ ] **Step 2: Run the browser spec against the local app and record RED only for real failures**

Terminal A:

```bash
cd apps/web
npm run dev
```

Terminal B:

```bash
cd apps/web
npx playwright test e2e/auth-login-v2.spec.ts --project=chromium
```

The asset test should pass after Tasks 1–2. Any geometry/a11y failure is the RED signal for this task.

- [ ] **Step 3: Make the minimal CSS corrections**

In `auth.module.css`:

```css
.authShell {
  min-height: 100svh;
  min-height: 100dvh;
}

.loginPanel {
  min-height: 100svh;
  min-height: 100dvh;
}

.authInput {
  min-block-size: 48px;
}

.passwordToggle {
  min-inline-size: 44px;
  min-block-size: 44px;
}

.authAdvisory {
  font-size: 0.8125rem;
  line-height: 1.5;
  color: var(--snad-text-secondary);
}
```

Replace the physical positioning on the decorative intelligence core:

```css
.intelligenceCore {
  inset-inline-start: 50%;
}
```

and remove `left: 50%` from that rule. Keep the transform unchanged.

Keep `.loginCard` max width within `420–460px`; the existing `440px` value is valid and should remain unless a browser test proves a regression.

- [ ] **Step 4: Run viewport matrix and focused unit tests**

```bash
cd apps/web
npx playwright test e2e/auth-login-v2.spec.ts --project=chromium
npm test -- components/auth/login-form.test.tsx components/sds/__tests__/SnadLogo.test.tsx
```

Additionally execute Chromium viewport probes:

```bash
npx playwright test e2e/auth-login-v2.spec.ts --project=chromium --headed
```

Manually verify `360×640`, `1024×768`, and `1280×720` using the spec/devtools; no primary control may be unreachable.

- [ ] **Step 5: Commit Task 3**

```bash
git add apps/web/components/auth/auth.module.css apps/web/e2e/auth-login-v2.spec.ts
git commit -m "fix(auth): harden responsive login accessibility"
```

---

### Task 4: Fix nested executive return URL normalization without weakening authorization

**Files:**
- Modify: `apps/web/lib/auth/destination.ts`
- Modify: `apps/web/lib/auth/destination.test.ts`
- Modify: `apps/web/components/auth/auth-entry.test.tsx`

**Interfaces:**
- Consumes: server-provided `availableDestinations: readonly string[]`.
- Produces: `safeReturnUrl("/executive/tenants", ["/workspace", "/executive"]) === "/executive/tenants"`, while the same route is rejected when `/executive` is absent.

- [ ] **Step 1: Add failing destination tests for the production screenshot route**

In `destination.test.ts`:

```ts
it("accepts a nested executive route only when executive is granted", () => {
  expect(safeReturnUrl("/executive/tenants", ["/workspace", "/executive"]))
    .toBe("/executive/tenants");
  expect(safeReturnUrl("/executive/tenants", ["/workspace", "/crm"]))
    .toBeNull();
});

it.each([
  "%2F%2Fevil.example",
  "/%2F%2Fevil.example",
  "/%5Cevil.example",
  "\u0000/executive/tenants",
])("rejects encoded or control-character destination %s", (candidate) => {
  expect(safeReturnUrl(candidate, ["/workspace", "/executive"])) .toBeNull();
});
```

- [ ] **Step 2: Run destination tests and verify nested executive case is RED**

```bash
cd apps/web
npm test -- lib/auth/destination.test.ts
```

Expected: the nested executive route test FAILS on the baseline implementation because `destinationRoot()` only maps exact `/executive`.

- [ ] **Step 3: Add the minimal executive root normalization**

In `destinationRoot()` add:

```ts
if (pathname.startsWith("/executive/")) return "/executive";
```

Do not add a generic prefix match that could turn arbitrary strings such as `/executive-malicious` into `/executive`.

- [ ] **Step 4: Add AuthEntry integration tests for authorized and unauthorized nested executive redirects**

Extend `auth-entry.test.tsx` with a privileged bootstrap:

```ts
const executiveBootstrap = {
  ...bootstrap,
  defaultDestination: "/executive",
  availableDestinations: ["/workspace", "/executive"],
};
```

Add:

```tsx
it("restores an authorized nested executive returnUrl", async () => {
  document.cookie = "sanad_session_hint=1; Path=/";
  window.history.replaceState({}, "", "/?returnUrl=%2Fexecutive%2Ftenants");
  authApiMock.refresh.mockResolvedValue(executiveBootstrap);
  renderEntry();
  await waitFor(() => expect(replaceMock).toHaveBeenCalledWith("/executive/tenants"));
});

it("falls back to workspace when nested executive returnUrl is not granted", async () => {
  document.cookie = "sanad_session_hint=1; Path=/";
  window.history.replaceState({}, "", "/?returnUrl=%2Fexecutive%2Ftenants");
  authApiMock.refresh.mockResolvedValue(bootstrap);
  renderEntry();
  await waitFor(() => expect(replaceMock).toHaveBeenCalledWith("/workspace"));
});
```

- [ ] **Step 5: Run destination and AuthEntry tests GREEN**

```bash
cd apps/web
npm test -- lib/auth/destination.test.ts components/auth/auth-entry.test.tsx
```

Expected: PASS.

- [ ] **Step 6: Commit Task 4**

```bash
git add apps/web/lib/auth/destination.ts \
  apps/web/lib/auth/destination.test.ts \
  apps/web/components/auth/auth-entry.test.tsx
git commit -m "fix(auth): authorize nested executive return destinations safely"
```

---

### Task 5: Extend browser acceptance to safe redirects, error states, and RTL behavior

**Files:**
- Modify: `apps/web/e2e/auth-login-v2.spec.ts`
- Reuse: `apps/web/e2e/crm-auth-session.ts`

**Interfaces:**
- Consumes: deployed/local auth API and existing `loginThroughUi(page, email, password)` helper.
- Produces: browser-level evidence for anonymous layout, invalid credentials, authorized return URL behavior, and no raw error leakage.

- [ ] **Step 1: Add invalid-login browser coverage without embedding production credentials**

Append:

```ts
test("invalid credentials show a generic user-facing error without raw internals", async ({ page }) => {
  await page.goto(`${BASE_URL}/`, { waitUntil: "domcontentloaded" });
  await page.locator("#login-email").fill("not-a-user@example.invalid");
  await page.locator("#login-password").fill("DefinitelyWrong-1!");
  await page.getByRole("button", { name: "تسجيل الدخول" }).click();

  const alert = page.getByRole("alert");
  await expect(alert).toBeVisible();
  await expect(alert).not.toContainText(/stack|trace|http:\/\/|https:\/\/|SQLException|NullPointerException/i);
});
```

- [ ] **Step 2: Add optional credentialed executive returnUrl coverage guarded by environment variables**

At file top:

```ts
const EXEC_EMAIL = process.env.EXECUTIVE_E2E_EMAIL;
const EXEC_PASSWORD = process.env.EXECUTIVE_E2E_PASSWORD;
```

Add:

```ts
test("authorized executive principal returns to /executive/tenants", async ({ page }) => {
  test.skip(!EXEC_EMAIL || !EXEC_PASSWORD, "executive E2E credentials are required");
  await page.goto(`${BASE_URL}/?returnUrl=%2Fexecutive%2Ftenants`, { waitUntil: "domcontentloaded" });
  await page.locator("#login-email").fill(EXEC_EMAIL!);
  await page.locator("#login-password").fill(EXEC_PASSWORD!);
  await page.getByRole("button", { name: "تسجيل الدخول" }).click();
  await expect(page).toHaveURL(/\/executive\/tenants(?:[?#].*)?$/);
});
```

Do not hardcode a real executive email/password in the spec.

- [ ] **Step 3: Add RTL/LTR attribute assertions**

```ts
test("keeps the Arabic shell RTL while technical credential inputs are LTR", async ({ page }) => {
  await page.goto(`${BASE_URL}/`, { waitUntil: "domcontentloaded" });
  await expect(page.locator("#login-email")).toHaveAttribute("dir", "ltr");
  await expect(page.locator("#login-password")).toHaveAttribute("dir", "ltr");
  const rootDir = await page.locator("html").getAttribute("dir");
  expect(rootDir ?? "rtl").toBe("rtl");
});
```

If the app intentionally sets RTL on a provider wrapper rather than `<html>`, change the locator to that existing canonical locale root; do not add duplicate direction state.

- [ ] **Step 4: Run browser acceptance**

```bash
cd apps/web
npx playwright test e2e/auth-login-v2.spec.ts --project=chromium
```

Expected: anonymous and invalid-login tests PASS. The credentialed executive case is either PASS with supplied secret environment variables or explicitly SKIPPED; it must not be reported as PASS when skipped.

- [ ] **Step 5: Commit Task 5**

```bash
git add apps/web/e2e/auth-login-v2.spec.ts
git commit -m "test(auth): add Login v2 browser acceptance"
```

---

### Task 6: Verify session, tenant isolation, and backend authentication controls under PostgreSQL Direct

**Files:**
- Do not change backend production files unless a regression test fails and identifies a concrete defect.
- Modify/create only the smallest existing backend test file associated with the failing control if a defect is found.
- Create later: `evidence/auth-login-v2/README.md` in Task 8 with the actual command results.

**Interfaces:**
- Consumes: current `AuthController`, `AuthService`, `LoginRateLimitKeys`, `LoginDestinationResolver`, refresh/session implementation, PostgreSQL Direct database.
- Produces: evidence that UI/brand changes did not regress authentication/security boundaries.

- [ ] **Step 1: Run focused web auth regression suite**

```bash
cd apps/web
npm test -- \
  components/auth/login-form.test.tsx \
  components/auth/auth-entry.test.tsx \
  lib/auth/destination.test.ts \
  lib/api/auth-flow.test.ts \
  components/sds/__tests__/SnadLogo.test.tsx
```

Expected: zero failures.

- [ ] **Step 2: Run web static gates**

```bash
cd apps/web
npm run typecheck
npm run lint
npm run build
```

Expected: all exit `0`.

- [ ] **Step 3: Run logo/design governance gates from repository root**

```bash
python3 scripts/ci/test_check_logo_governance.py
python3 scripts/ci/check-logo-governance.py apps/web
python3 scripts/ci/check-design-system-compliance.py
python3 scripts/ci/check-brand-name-governance.py
```

Expected: zero violations.

- [ ] **Step 4: Run PostgreSQL Direct backend auth/security tests**

First confirm PostgreSQL is reachable using the project’s configured direct connection; do not start Docker/Testcontainers.

From `apps/sanad-platform` run the focused security classes already present in the repository. Discover exact class names before execution with:

```bash
find src/test -type f \( -name '*Auth*Test.java' -o -name '*Login*Test.java' -o -name '*Tenant*Isolation*Test.java' -o -name '*Destination*Test.java' \) -print | sort
```

Then run the discovered auth/destination/rate-limit/session/tenant-isolation classes together, for example through Surefire’s class list:

```bash
mvn -Dtest='*Auth*,*Login*,*Destination*,*TenantIsolation*' test
```

Expected: `FAILURES=0`, `ERRORS=0`. Assumption-skipped tests caused by an unavailable/misconfigured PostgreSQL Direct database do **not** qualify as PASS; fix the test environment before certification.

- [ ] **Step 5: Verify no browser token persistence regression**

Run the existing browser auth/session tests that assert access tokens are not persisted in browser storage:

```bash
cd apps/web
npm test -- lib/api/auth-flow.test.ts
npx playwright test e2e/crm-operational.spec.ts --project=chromium
```

Then inspect in a credentialed browser test:

```ts
const storage = await page.evaluate(() => ({
  local: Object.keys(localStorage),
  session: Object.keys(sessionStorage),
}));
expect(storage.local.filter(k => /token|refresh/i.test(k))).toEqual([]);
expect(storage.session.filter(k => /token|refresh/i.test(k))).toEqual([]);
```

Add this assertion to `auth-login-v2.spec.ts` only in a credentialed test that has completed a real login; do not make a no-login storage assertion masquerade as session evidence.

- [ ] **Step 6: Stop on any security regression before making speculative backend changes**

If a focused test proves a defect, record the exact failing test, request/response, and file path, then implement the smallest fix in the owner component and add a regression test in the same commit. Do not redesign the auth/token architecture inside Login v2 Phase A.

- [ ] **Step 7: Commit only if Task 6 added a real regression test/fix**

Use a defect-specific commit message, for example:

```bash
git commit -m "fix(auth): prevent <exact proven regression>"
```

If all existing backend/security controls pass unchanged, Task 6 produces no code commit; its verified command outputs are recorded in Task 8 evidence.

---

### Task 7: Update brand/auth governance documentation to match the approved limited-scope asset

**Files:**
- Modify: `apps/web/public/assets/brand/README.md`
- Modify: `apps/web/design-system/documentation/LOGO_USAGE.md`
- Modify: `apps/web/design-system/documentation/AUTH_UI_GUIDE.md`
- Create: `apps/web/design-system/documentation/CHANGELOG.md`

**Interfaces:**
- Consumes: actual implementation from Tasks 1–6.
- Produces: truthful governance that distinguishes the auth-approved raster wordmark from the legacy/global SVG family.

- [ ] **Step 1: Document the asset without declaring an unapproved global rebrand**

Add to brand README asset table:

```md
| snad-logo-official-wordmark.png | PNG RGBA, 1162×337 | User-approved official wordmark for authentication surfaces; exact bytes are governed and must not be recolored/vectorized |
```

Add the SHA-256 under a new integrity subsection:

```md
`snad-logo-official-wordmark.png` SHA-256: `98d0b84b0675b53f60837f803cf0a4bc85209eea650b213adba92a661bfde251`
```

Explicitly state that the existing SVG family remains in use on non-auth surfaces until a separate global migration is approved.

- [ ] **Step 2: Update `LOGO_USAGE.md` authentication rule**

Add a section:

```md
## Authentication wordmark

Authentication surfaces use `snad-logo-official-wordmark.png` through `<SnadLogo variant="official-wordmark" />` on a light/neutral surface. The file must be served at its locked aspect ratio (1162:337) and may not be recolored, traced to SVG, or used to synthesize reverse/compact variants. Existing SVG variants remain the governed legacy/global set outside the authentication scope until a separate migration is approved.
```

- [ ] **Step 3: Update `AUTH_UI_GUIDE.md` logo rule**

Replace the auth-specific theme-switching instruction with:

```md
Authentication uses `variant="official-wordmark"` on the light auth panel. Do not switch this asset to a generated white variant in dark mode. The intelligence panel is not a second logo canvas.
```

Keep the existing CLS reservation and responsive width rules.

- [ ] **Step 4: Create the missing design-system changelog with a concrete record**

Create `CHANGELOG.md`:

```md
# SNAD Design System Changelog

## 2026-09-23 — Authentication official wordmark adoption

- Scope: authentication surfaces only.
- Approved source: `snad-logo-official-wordmark.png`, 1162×337 RGBA.
- SHA-256: `98d0b84b0675b53f60837f803cf0a4bc85209eea650b213adba92a661bfde251`.
- No color/token change.
- No generated SVG/white/compact derivative.
- Existing non-auth logo variants remain unchanged pending a separate global migration decision.
```

- [ ] **Step 5: Run documentation-linked governance checks**

```bash
python3 scripts/ci/check-logo-governance.py apps/web
python3 scripts/ci/check-brand-name-governance.py
```

Expected: PASS.

- [ ] **Step 6: Commit Task 7**

```bash
git add apps/web/public/assets/brand/README.md \
  apps/web/design-system/documentation/LOGO_USAGE.md \
  apps/web/design-system/documentation/AUTH_UI_GUIDE.md \
  apps/web/design-system/documentation/CHANGELOG.md
git commit -m "docs(auth): govern official Login v2 wordmark"
```

---

### Task 8: Exact-head certification and evidence package

**Files:**
- Create: `evidence/auth-login-v2/README.md`
- Add: screenshot/artifact files produced by the browser run only if the repository’s evidence policy permits checked-in binaries; otherwise list the CI artifact names and URLs in the README.

**Interfaces:**
- Consumes: exact implementation HEAD and all test outputs.
- Produces: auditable release gate for Login v2 Phase A.

- [ ] **Step 1: Record exact implementation HEAD before final verification**

```bash
git rev-parse HEAD
git status --short
```

Expected: clean working tree. Save the SHA for every command/evidence row.

- [ ] **Step 2: Run the complete web gate on exact HEAD**

```bash
cd apps/web
npm run typecheck
npm run lint
npm test
npm run build
npx playwright test e2e/auth-login-v2.spec.ts --project=chromium
```

Expected: all required, non-credential-optional checks PASS.

- [ ] **Step 3: Run exact-head governance and backend/PostgreSQL Direct gate**

From repo root:

```bash
python3 scripts/ci/test_check_logo_governance.py
python3 scripts/ci/check-logo-governance.py apps/web
python3 scripts/ci/check-design-system-compliance.py
python3 scripts/ci/check-brand-name-governance.py
cd apps/sanad-platform
mvn test
```

Expected: governance exit `0`; Maven `FAILURES=0`, `ERRORS=0`. Skips must be reviewed and none may hide the required authentication/tenant/security checks.

- [ ] **Step 4: Capture desktop and mobile evidence from the same tested SHA**

Capture at minimum:

```text
auth-login-v2-desktop-1440x900.png
auth-login-v2-mobile-390x844.png
auth-login-v2-invalid-credentials.png
auth-login-v2-session-expired.png
```

When environment data allows, also capture:

```text
auth-login-v2-authorized-executive-return.png
auth-login-v2-unauthorized-executive-fallback.png
```

Do not fabricate screenshots for states that were not exercised.

- [ ] **Step 5: Write the evidence README from actual results**

Use this structure and replace each row only with measured data from this run:

```md
# SNAD Login v2 Phase A Evidence

- Exact tested SHA: `<git rev-parse HEAD output>`
- PostgreSQL mode: Direct
- Docker/Testcontainers: Not used
- Official wordmark SHA-256: `98d0b84b0675b53f60837f803cf0a4bc85209eea650b213adba92a661bfde251`

| Gate | Result | Evidence |
| --- | --- | --- |
| Web typecheck | PASS/FAIL | command output / CI job |
| Web lint | PASS/FAIL | command output / CI job |
| Vitest | PASS/FAIL | test counts |
| Playwright Login v2 | PASS/FAIL/SKIPPED | per-test counts; credentialed skips listed explicitly |
| Logo governance | PASS/FAIL | violation count |
| Backend Maven | PASS/FAIL | tests/failures/errors/skips |
| Tenant isolation | PASS/FAIL | exact test/spec name |
| Executive isolation | PASS/FAIL | exact test/spec name |
| Desktop evidence | PRESENT/MISSING | artifact filename |
| Mobile evidence | PRESENT/MISSING | artifact filename |
```

Do not write `PASS` for a gate that was skipped, assumed, or inferred from an older SHA.

- [ ] **Step 6: Final diff and scope audit**

```bash
git diff --stat <implementation-base-sha>...HEAD
git diff <implementation-base-sha>...HEAD -- \
  apps/web/components/auth \
  apps/web/components/sds \
  apps/web/lib/auth \
  scripts/ci \
  apps/web/design-system/documentation \
  evidence/auth-login-v2
```

Confirm there are no unrelated HRM, subscription, accounting, database-migration, or token-architecture changes.

- [ ] **Step 7: Commit the truthful evidence record**

```bash
git add evidence/auth-login-v2
git commit -m "docs(auth): record Login v2 Phase A verification evidence"
```

- [ ] **Step 8: Re-run the lightweight exact-final-head checks after the evidence commit**

Because the evidence commit changes HEAD, run at minimum:

```bash
git rev-parse HEAD
python3 scripts/ci/check-logo-governance.py apps/web
cd apps/web && npm run typecheck && npm run lint
```

The final reported SHA is this post-evidence SHA, not the pre-evidence implementation SHA.

---

## Self-Review Result

### Spec coverage
- Official approved logo with no synthesized variants: Tasks 1, 2, 7, 8.
- Existing auth architecture preserved: Tasks 2–6.
- Responsive/RTL/WCAG behavior: Tasks 2, 3, 5.
- Return URL authorization including `/executive/tenants`: Task 4 plus Task 5 browser evidence.
- Session/refresh/token-storage preservation: Task 6.
- PostgreSQL Direct security verification: Tasks 6 and 8.
- Brand/governance reconciliation: Tasks 1 and 7.
- Exact deployed/tested evidence requirement: Task 8; deployment itself must use the exact verified implementation SHA in the existing release pipeline.
- MFA/passkeys remain a separate Phase B: explicitly excluded by Global Constraints; no task adds fake controls.

### Placeholder scan
No `TBD`, `TODO`, “implement later”, or unspecified validation/error-handling steps are present. Optional credentialed browser tests are explicitly classified as `SKIPPED`, never PASS, when secrets are unavailable.

### Type/interface consistency
- The single new public UI variant name is consistently `official-wordmark`.
- The public asset path is consistently `/assets/brand/snad-logo-official-wordmark.png`.
- The privileged destination root remains `/executive`; nested routes are only normalized to that root for authorization comparison.

### Review Focus mapping
- Nested privileged destination: Task 4 tests.
- Encoded redirect input: Task 4 tests.
- Small/short viewport and accessibility: Task 3 Playwright/Axe checks.
- Brand source integrity: Tasks 1 and 8 SHA checks.
- Credential correction/Caps Lock behavior: Task 2 component tests.
