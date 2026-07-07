# SNAD Auth Performance

> **Custodian:** SNAD Executive Office · SDS Frontend Guild · SRE
> **Status:** ACTIVE — binding budget for every authentication surface
> **Scope:** `/` (login), `/auth/forgot-password`, `/reset-password`, and the auth entry state machine.

Authentication is the **first impression** of SNAD. A slow login screen
costs trust before the product has a chance to prove itself. This
document defines the performance budget for every auth surface, the
measurement strategy, the optimization techniques in use, and the
before/after evidence required for any PR that touches auth code.

---

## 1. Budget

All targets are **p75** on a Moto G Power (2022) over a simulated
Fast 3G connection (1.6 Mbps down, 750 Kbps up, 150 ms RTT), unless
otherwise noted. Targets apply to the **production build** (not dev).

| Metric | Target | Hard limit | Notes |
| --- | --- | --- | --- |
| First Contentful Paint (FCP) | ≤ 1.2s | 1.8s | Logo + headline visible. |
| Largest Contentful Paint (LCP) | ≤ 2.0s | 2.5s | Logo is the LCP element. |
| Time to Interactive (TTI) | ≤ 2.5s | 3.5s | Form is focusable. |
| Total Blocking Time (TBT) | ≤ 200ms | 400ms | Hydration + theme resolve. |
| Cumulative Layout Shift (CLS) | < 0.05 | 0.10 | Logo reservation + error alert. |
| JS bundle (auth route, gzipped) | ≤ 80 KB | 120 KB | Excludes Next.js runtime. |
| CSS bundle (auth route, gzipped) | ≤ 20 KB | 30 KB | Critical-path only. |
| Lighthouse Performance | ≥ 90 | 80 | Mobile, throttled. |
| Lighthouse Accessibility | ≥ 95 | 90 | WCAG 2.2 AA. |

### 1.1 Why these numbers?

- **FCP ≤ 1.2s**: the SNAD brand promise is "سند" — *support*. A
  login that takes longer than 1.2s to paint feels unsupportive.
- **LCP ≤ 2.0s with the logo as LCP**: the logo is the first thing
  the user looks for. If it's not painted by 2s, the user feels lost.
- **CLS < 0.05**: any layout shift on a login screen is interpreted
  as "is this phishing?" — a trust-destroying perception.
- **JS ≤ 80 KB gzipped**: the auth route ships no charting, no
  virtualization, no rich-text — there's no excuse for a heavy bundle.

---

## 2. Measurement strategy

### 2.1 Local measurement (developer machine)

```bash
# Production build + start
npm run build && npm run start

# Lighthouse CI — throttled Moto G Power profile
npx @lhci/cli autorun --collect.url=http://localhost:3000/ \
  --collect.numberOfRuns=5 \
  --collect.settings.preset=desktop \
  --assert.preset=lighthouse:recommended \
  --assert.assertions.categories:performance=0.9
```

### 2.2 CI measurement (GitHub Actions)

The `web-ci.yml` workflow runs Lighthouse CI on every PR that touches
`apps/web/components/auth/**` or `apps/web/app/(auth)/**`. The budget
above is enforced via Lighthouse CI assertions. A PR that regresses
any metric by more than 5% is blocked until remediated.

### 2.3 Production measurement (Vercel Web Vitals)

Every production page emits Real User Monitoring (RUM) metrics via
`next/web-vitals`. The metrics are forwarded to Vercel Analytics and
to the SNAD SRE dashboard. SRE alerts on p75 regressions of more than
10% sustained over 1 hour.

### 2.4 Synthetic measurement (k6)

A k6 load test (`tests/performance/k6/sanad-staging-load.js`) hits the
login endpoint at 50 VUs for 5 minutes. The test enforces:

- p95 response time ≤ 500ms for the login API.
- Error rate < 0.1%.
- Zero 5xx responses.

---

## 3. Optimization techniques

### 3.1 Logo: priority + dimensions

The SNAD logo is the LCP element on the auth screen. It uses:

- `priority` prop on `<SnadLogo />` → Next.js Image sets
  `fetchpriority="high"` and preloads the SVG.
- Inline `width` and `height` on the `<img>` (computed by SnadLogo
  from the size preset and variant aspect ratio) → the browser
  reserves the box before the SVG arrives, preventing CLS.
- `unoptimized` prop → the SVG is served as-is, no Next.js image
  optimizer hop (which would add 30–80ms for an SVG that's already
  vector-perfect).
- SVG format → infinitely scalable, sub-1KB gzipped.

### 3.2 Theme: SSR-safe + post-mount resolve

The `useTheme` hook returns `'light'` on SSR to avoid hydration
mismatch. After mount, it resolves the real theme (from
`localStorage` or `prefers-color-scheme`) and re-renders the logo
with the correct variant.

This costs one extra render cycle (~1ms) but avoids:

- A flash of the wrong-coloured logo (FOUC).
- A hydration mismatch warning (which would block interactive paint).

The `.loginBrandMark` container's `min-block-size: clamp(48px, 11vw,
103px)` absorbs the variant swap with zero CLS.

### 3.3 JS: route-level code splitting

The auth route is split from the workspace route via Next.js
dynamic imports. The auth bundle contains:

- `login-form.tsx` + `auth-error-alert.tsx` + `auth-intelligence-visual.tsx`
- `SnadLogo.tsx` (compact: < 2 KB)
- `useTheme` hook (< 1 KB)
- React + Next.js runtime (shared, ~45 KB gzipped)

It does NOT contain:

- The control-plane console (loaded on `/control-plane`).
- The CRM workspace (loaded on `/crm`).
- Chart libraries (loaded only on dashboard routes).
- The executive health panel (loaded only on `/control-plane`).

### 3.4 CSS: critical-path only

The auth route's CSS (`auth.module.css`) is ~6 KB gzipped. It uses
CSS custom properties (CSS variables) for all colours and fonts,
which means the theme tokens (~3 KB gzipped, shared) are loaded once
and reused. The auth-specific styles are pure layout + spacing.

### 3.5 Fonts: `font-display: swap`

Tajawal (Arabic) and Inter (Latin) are loaded via `next/font/google`
with `display: swap`. The fallback fonts (Tahoma, Arial) are sized
to match the metrics of the real fonts, so the swap does not cause
layout shift.

### 3.6 Intelligence visual: CSS-only

The pulsing brand core on the desktop left panel is pure CSS
(`@keyframes pulse` + `radial-gradient`). It costs zero JS and
renders on the compositor thread. Under `prefers-reduced-motion:
reduce`, the animation is disabled and the core is rendered as a
static 60% opacity disc.

---

## 4. Before / after evidence requirement

Every PR that modifies `apps/web/components/auth/**` or
`apps/web/app/(auth)/**` MUST include in its description:

1. **Before**: Lighthouse report URL (or screenshot) from `main`.
2. **After**: Lighthouse report URL (or screenshot) from the PR's
   preview deployment.
3. **Delta table**: for each metric in §1, the before/after numbers
   and the percentage change.
4. **Bundle size**: the auth route's JS and CSS bundle sizes before
   and after, from `next build`'s output.

A PR that regresses any metric beyond the §1 hard limits is blocked
until remediated. A PR that improves a metric is celebrated in the
#frontend channel.

### 4.1 Exemptions

The only acceptable reason to regress a metric is a deliberate
product change that requires it (e.g. adding a CAPTCHA, which adds
~30 KB JS). Such exemptions MUST be:

- Documented in the PR description.
- Approved by the SDS Frontend Guild lead.
- Tracked as tech debt with a remediation date.

---

## 5. Anti-patterns

1. ❌ **Client-side theme detection before first paint** — causes
   FOUC. Use `useTheme`'s SSR-safe default + post-mount resolve.
2. ❌ **Lazy-loading the logo** — the logo IS the LCP element. It
   must be `priority`.
3. ❌ **Using `<img src>` instead of `<SnadLogo />`** — loses
   dimension pre-computation, accessibility, and governance.
4. ❌ **Loading the workspace bundle on the auth route** — doubles
   the JS budget.
5. ❌ **Using a JS animation library for the intelligence visual** —
   CSS animations are 10× lighter.
6. ❌ **Inlining the SVG as a data URI** — defeats caching and
   preloading.

---

## 6. Cross-references

- `AUTH_UI_GUIDE.md` — visual layout and state machine.
- `WORKSPACE_BOOTSTRAP.md` — post-login bootstrap sequence.
- `DESIGN_TOKENS.md` — token system (drives CSS variable reuse).
- `VISUAL_TESTING.md` — visual regression testing strategy.
- `tests/performance/k6/sanad-staging-load.js` — k6 load test.
