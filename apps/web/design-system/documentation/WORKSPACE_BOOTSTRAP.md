# SNAD Workspace Bootstrap

> **Custodian:** SNAD Executive Office · SDS Frontend Guild
> **Status:** ACTIVE — binding for every authenticated route
> **Scope:** The bootstrap sequence that takes a user from "login successful" to "interactive workspace."

The workspace bootstrap is the most performance-sensitive moment in
the SNAD user journey. The user has just authenticated and expects
the workspace to appear instantly. Any delay beyond ~100ms feels
like the app is broken. This document defines the two-phase
bootstrap strategy, the code-splitting boundaries, the lazy-loading
rules, the skeleton screen contract, and the route-based loading
states.

---

## 1. Two-phase bootstrap

### Phase 1: Critical (0 → 100ms)

The **critical phase** ships the minimum viable interactive workspace:
the executive shell (header + logo), the page title, and the primary
navigation. Everything else is deferred.

Critical-phase assets:

- `ExecutiveShell` + `SnadLogo` (compact variant, < 3 KB combined).
- The route's page component shell (e.g. `<WorkspacePage />`'s
  outer `<div>` + page title).
- SDS token CSS (shared, ~3 KB gzipped).
- Auth provider (already loaded from the auth screen).

Critical-phase budget: **≤ 30 KB JS gzipped** on top of the auth
bundle, **≤ 5 KB CSS gzipped** on top of the auth bundle.

### Phase 2: Progressive (100ms → 2s)

The **progressive phase** hydrates the rest of the workspace: the
dashboard widgets, the data tables, the chart libraries, the sidebar
navigation (when present). Each progressive chunk is loaded via
`next/dynamic` with `ssr: false` and a skeleton placeholder.

Progressive-phase assets (examples):

- The CRM pipeline board (`/crm`).
- The executive health panel (`/control-plane`).
- The charting library (recharts / visx, loaded only when a chart
  is visible).
- The virtualized table (loaded only when a table is visible).

---

## 2. Code splitting boundaries

### 2.1 Route-level splits

Next.js's file-based router automatically code-splits at each
`app/*/page.tsx` boundary. This means:

- `/workspace` ships only the workspace page bundle.
- `/control-plane` ships only the control-plane bundle.
- `/crm` ships only the CRM bundle.

No route imports another route's page component. Cross-route shared
code lives in `components/`, `lib/`, or `design-system/` and is
tree-shaken per route.

### 2.2 Component-level splits (dynamic imports)

Heavy components are split via `next/dynamic`:

```tsx
const CrmPipelineBoard = dynamic(() => import("./crm-pipeline-board"), {
  loading: () => <CrmPipelineSkeleton />,
  ssr: false,
});
```

Rules:

- Any component > 10 KB gzipped MUST be dynamically imported.
- Any component that imports a heavy library (charts, virtualization,
  rich text) MUST be dynamically imported.
- The `loading` prop MUST return a skeleton component, not `null`.
- `ssr: false` is used ONLY for components that depend on browser
  APIs (e.g. `window`, `IntersectionObserver`). SSR-able components
  keep `ssr: true` (default) for better FCP.

### 2.3 Library-level splits

Third-party libraries are split by route:

- `recharts` is imported only by dashboard widgets on `/workspace`
  and `/control-plane`. It is NOT imported by `/crm` or `/settings`.
- `@tanstack/react-virtual` is imported only by virtualized tables
  on `/crm`. It is NOT imported by dashboards.
- `react-aria` is imported only by complex overlay components
  (combobox, date picker). Simple components use native HTML
  elements with ARIA attributes.

---

## 3. Lazy loading

### 3.1 Below-the-fold content

Any content below the fold (more than ~1 viewport height from the
top) is lazy-loaded via `IntersectionObserver`:

```tsx
const LazyWidget = lazy(() => import("./LazyWidget"));
<LazyWidget fallback={<WidgetSkeleton />} />
```

The observer triggers when the widget is within 200px of the
viewport, so it's loaded by the time the user scrolls to it.

### 3.2 Images

All images use Next.js `<Image>` with:

- `loading="lazy"` (default) for below-the-fold images.
- `priority` for above-the-fold images (only the logo on auth and
  the executive shell's compact logo on authenticated pages).
- `sizes` attribute for responsive images, so the browser downloads
  the correct resolution.

### 3.3 Fonts

Fonts are loaded via `next/font/google` with `display: swap` and
`preload: true` for the primary font (Tajawal for Arabic, Inter for
Latin). Secondary fonts (IBM Plex Sans Arabic, Noto Sans Arabic) are
not preloaded — they're swapped in only when needed.

---

## 4. Skeleton screens

### 4.1 Contract

Every dynamically-loaded component MUST provide a skeleton:

```tsx
<Crmskeleton />
```

A skeleton is:

- **Same shape** as the loaded content (same width, height, border
  radius, spacing).
- **Same colour** as the loaded content's container
  (`--snad-color-surface-secondary`).
- **Animated** with a shimmer (`@keyframes shimmer`) over
  `var(--snad-motion-slow)` (320ms), but ONLY when
  `prefers-reduced-motion: no-preference`.
- **Accessible**: `aria-busy="true"` on the skeleton's parent, and
  `aria-hidden="true"` on the skeleton itself.

### 4.2 Duration limits

- A skeleton may show for up to **200ms** without a progress
  indicator. Below 200ms, the skeleton is barely perceptible and
  feels like a render.
- From 200ms to 1s, the skeleton's shimmer animation is sufficient.
- Beyond 1s, the skeleton MUST be replaced by a progress bar with
  a percentage (if known) or an indeterminate spinner with a
  "جارٍ التحميل…" label.

### 4.3 Error fallback

If a lazy-loaded component fails to load (network error, chunk
invalidation), the fallback is an `<ErrorBoundary>` that renders:

```tsx
<div role="alert" className={styles.loadError}>
  تعذّر تحميل هذا القسم. <button onClick={retry}>إعادة المحاولة</button>
</div>
```

The retry button re-attempts the dynamic import.

---

## 5. Route-based loading states

### 5.1 Next.js `loading.tsx`

Every route segment that fetches data MUST have a `loading.tsx` file
that renders a route-level skeleton:

```
app/
  control-plane/
    page.tsx
    loading.tsx       ← renders <ControlPlaneSkeleton />
  crm/
    page.tsx
    loading.tsx       ← renders <CrmSkeleton />
  workspace/
    page.tsx
    loading.tsx       ← renders <WorkspaceSkeleton />
```

The `loading.tsx` file renders during route transitions and during
Suspense boundaries triggered by data fetching.

### 5.2 Auth-loading-state

The auth entry state machine has its own loading component
(`<AuthLoadingState />`) that renders a centered spinner with the
SNAD wordmark. This is used during INITIALIZING, REFRESHING, and
LOGGING_OUT states.

### 5.3 Prefetching

Next.js `<Link>` prefetches the destination route on hover and on
viewport entry. This means that by the time the user clicks a link,
the route's JS bundle is likely already cached. We do NOT disable
prefetching except for links to very heavy routes (e.g. a report
builder that loads a 500 KB charting library).

---

## 6. Hydration

### 6.1 Selective hydration

Next.js 14+ supports selective hydration: the parts of the page
that are interactive hydrate first, while the parts that are static
(sidebar, header decoration) hydrate later. This is automatic — no
developer action required.

### 6.2 Avoid hydration mismatches

Common causes of hydration mismatches:

- Using `Date.now()`, `Math.random()`, or `window.innerWidth` during
  render. Use `useEffect` for these.
- Using `useTheme` before mount (it returns `'light'` on SSR). The
  `useTheme` hook is SSR-safe; consumers must handle the post-mount
  theme change without causing layout shift.
- Using `localStorage` during render. Wrap in `useEffect`.

### 6.3 `useId` for SSR-stable IDs

Any component that generates a unique ID (e.g. for `aria-labelledby`)
MUST use React's `useId()` hook, NOT `Math.random()` or
`Date.now()`. This guarantees the server and client generate the
same ID.

---

## 7. Measurement

### 7.1 Per-route Lighthouse

Each authenticated route has its own Lighthouse budget (see
`AUTH_PERFORMANCE.md` for the auth budget; the workspace budget is
similar but allows 1.5× the JS bundle for dashboard widgets).

### 7.2 Bundle analyzer

Run `npm run build` with `ANALYZE=true` to generate a bundle size
report. Any route whose JS bundle exceeds its budget MUST be split
further before merging.

### 7.3 Long Animation Frames API

The Long Animation Frames (LoAF) API is used in development to
detect any frame that takes > 50ms. Each such frame is logged to
the console with the offending component stack.

---

## 8. Prohibited patterns

1. ❌ **Loading the entire workspace bundle on the auth route** —
   doubles the JS budget.
2. ❌ **Using `ssr: false` on SSR-able components** — hurts FCP.
3. ❌ **Rendering `null` as a loading fallback** — causes layout
   shift. Always use a skeleton.
4. ❌ **Disabling Next.js `<Link>` prefetching globally** — prefetching
   is what makes route transitions feel instant.
5. ❌ **Loading chart libraries on routes without charts** —
   recharts alone is ~90 KB gzipped.
6. ❌ **Using `Math.random()` for component IDs** — causes hydration
   mismatches. Use `useId()`.
7. ❌ **Hydrating the entire page in one pass** — let Next.js's
   selective hydration handle prioritization.

---

## 9. Cross-references

- `AUTH_PERFORMANCE.md` — auth-route performance budget.
- `EXECUTIVE_SHELL_GUIDE.md` — shell structure (the first thing the
  user sees after login).
- `AUTH_UI_GUIDE.md` — pre-login shell.
- `DESIGN_TOKENS.md` — token system (shared CSS, no per-route
  duplication).
- `VISUAL_TESTING.md` — visual regression testing for skeletons.
