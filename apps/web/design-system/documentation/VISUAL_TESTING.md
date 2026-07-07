# SNAD Visual Testing Strategy

> **Goal:** catch unintended visual regressions before they ship.
> **Tools:** Playwright, `@playwright/test`, `pixelmatch`.
> **Cadence:** every PR, every theme (light + dark), every direction (LTR + RTL).

Visual regressions are the silent killer of design system adoption. A
single careless PR can shift a button 2px to the right and erode user
trust across the entire product. This document specifies SNAD's visual
testing strategy.

## 1. What we test

| Surface | Tested in light | Tested in dark | Tested in LTR | Tested in RTL |
| --- | --- | --- | --- | --- |
| Every SDS component | ✓ | ✓ | ✓ | ✓ |
| Every app route | ✓ | ✓ | ✓ | ✓ |
| Email templates | ✓ | n/a | ✓ | ✓ |
| Print stylesheet | ✓ | n/a | ✓ | ✓ |

That's 4 baseline snapshots per surface (light-LTR, light-RTL, dark-LTR,
dark-RTL). For 50 components and 30 routes, that's ~320 baseline images.

## 2. Tooling

### Playwright

`@playwright/test` is the primary test runner. It supports:

* Headless Chromium, Firefox, WebKit
* Device emulation (iPhone, iPad, Pixel, Galaxy)
* `prefers-color-scheme` emulation
* `direction` emulation
* Screenshot diffing via `expect(page).toHaveScreenshot()`

### Test structure

```
apps/web/__visual_tests__/
├── components/
│   ├── button.test.ts
│   ├── card.test.ts
│   └── ...
├── routes/
│   ├── login.test.ts
│   ├── dashboard.test.ts
│   └── ...
└── snapshots/
    ├── button-test/
    │   ├── button-primary-light-ltr-chromium.png
    │   ├── button-primary-light-rtl-chromium.png
    │   ├── button-primary-dark-ltr-chromium.png
    │   ├── button-primary-dark-rtl-chromium.png
    │   └── ... (one per variant × theme × direction)
    └── ...
```

### Example test

```ts
// __visual_tests__/components/button.test.ts
import { test, expect } from "@playwright/test";

test.describe("Button", () => {
  for (const theme of ["light", "dark"] as const) {
    for (const dir of ["ltr", "rtl"] as const) {
      test(`primary — ${theme} — ${dir}`, async ({ page }) => {
        await page.emulateMedia({
          colorScheme: theme,
        });
        await page.goto(`/__sandbox/button?variant=primary&dir=${dir}`);
        await page.waitForLoadState("networkidle");
        await expect(page).toHaveScreenshot(
          `button-primary-${theme}-${dir}.png`,
          { maxDiffPixelRatio: 0.01 }
        );
      });
    }
  }
});
```

## 3. The 1% diff threshold

`maxDiffPixelRatio: 0.01` allows up to 1% of pixels to differ between
the captured screenshot and the baseline. This is generous enough to
absorb sub-pixel anti-aliasing differences across CI runners, but strict
enough to catch real regressions.

If a change is intentional (e.g. a token value change), update the
baseline with `pnpm exec playwright test --update-snapshots`. The PR
must include the updated baseline and a justification in the commit
message.

## 4. Theme coverage

Every visual test runs in both light and dark themes. This catches:

* Dark-mode-only contrast failures (e.g. text that's invisible in dark
  mode because the foreground and background tokens happen to resolve to
  the same value)
* Shadow regressions (dark shadows use pure black; light shadows use
  petroleum-tinted black)
* Status color inversions (soft backgrounds flip between light and dark)

### How theme switching works in tests

The sandbox route sets `data-theme` on `<html>`:

```ts
// __sandbox/button/route.tsx
export default function ButtonSandbox({
  searchParams,
}: {
  searchParams: { variant: string; dir: string; theme?: string };
}) {
  const theme = searchParams.theme ?? "light";
  return (
    <html data-theme={theme} dir={searchParams.dir}>
      <body>
        <Button variant={searchParams.variant}>Click me</Button>
      </body>
    </html>
  );
}
```

## 5. Direction coverage

Every visual test runs in both LTR and RTL. This catches:

* Layout flips that didn't happen (forgot to use logical properties)
* Icons that didn't flip
* Mixed-direction numerals that got comma-flipped

## 6. Sandboxed routes

Visual tests render components and routes in a **sandbox** — a minimal
HTML shell that loads only the design system tokens, the component under
test, and the global typography. This isolates the visual test from app
chrome (nav bars, sidebars, toasts) that would shift between snapshots.

The sandbox routes live under `apps/web/app/__sandbox/` and are excluded
from production builds via `next.config.ts`:

```ts
// next.config.ts
export default {
  async redirects() {
    if (process.env.NODE_ENV === "production") {
      return [
        { source: "/__sandbox/:path*", destination: "/404", permanent: false },
      ];
    }
    return [];
  },
};
```

## 7. CI integration

```yaml
# .github/workflows/visual-regression.yml
name: Visual Regression
on: [pull_request]
jobs:
  visual:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with:
          node-version: 20
      - run: pnpm install --frozen-lockfile
      - run: pnpm exec playwright install --with-deps chromium
      - run: pnpm --filter web test:visual
      - uses: actions/upload-artifact@v4
        if: failure()
        with:
          name: visual-diff
          path: apps/web/__visual_tests__/diff-output/
```

If a test fails, the diff image is uploaded as an artifact for review.

## 8. Handling flaky tests

Visual tests can be flaky due to:

* Font loading race conditions (web fonts not yet applied when screenshot
  is taken)
* Animation mid-frame capture
* Sub-pixel anti-aliasing differences across CI runners

Mitigations:

1. `await page.waitForLoadState("networkidle")` before screenshotting.
2. Disable animations: `page.addStyleTag({ content: "* { animation: none !important; transition: none !important; }" })`.
3. Use `expect(page).toHaveScreenshot(..., { animations: "disabled" })`.
4. If a test is genuinely flaky across platforms, mark it
   `test.fixme(...)` with a comment and file an issue.

## 9. Baseline updates

Baselines are stored in the repo under
`apps/web/__visual_tests__/snapshots/`. To update baselines after an
intentional change:

```bash
pnpm --filter web test:visual -- --update-snapshots
git add apps/web/__visual_tests__/snapshots/
git commit -m "test(visual): update baselines for [reason]"
```

The PR description must include:

* Before / after screenshots
* The token change that drove the visual change
* Sign-off from a designer

## 10. What we don't test

* **Performance regressions** — covered by Lighthouse CI, not visual
  tests.
* **Accessibility regressions** — covered by `@axe-core/playwright`, not
  visual tests.
* **Functional regressions** — covered by Playwright UI tests, not
  visual tests.

Visual tests catch only **pixel-level** regressions. They are a
complement to, not a replacement for, the other test layers.
