# SNAD Executive Shell Guide

> **Custodian:** SNAD Executive Office · SDS Frontend Guild
> **Status:** ACTIVE — binding for every authenticated surface
> **Scope:** `apps/web/components/shell/ExecutiveShell.tsx`, control-plane console, workspace dashboard, CRM workspace, and any future authenticated route.

The **Executive Shell** is the persistent chrome that surrounds every
authenticated SNAD surface. It consists of a sticky top bar (the
"executive header") and a main content area. This document specifies
its structure, layout, logo placement, sticky behaviour, sidebar
interaction, RTL/LTR conventions, and responsive breakpoints.

---

## 1. Anatomy

```
┌──────────────────────────────────────────────────────────────────┐
│  [≡]  [SNAD]    Organization Name    [🔍] [🔔] [✨] [👤]          │ ← sticky header (64px)
├──────────────────────────────────────────────────────────────────┤
│                                                                  │
│                       Main content area                          │
│                       (children of <ExecutiveShell>)             │
│                                                                  │
└──────────────────────────────────────────────────────────────────┘
```

### 1.1 Header zones (inline-axis, in order)

| Zone | Slot prop | Content | RTL position |
| --- | --- | --- | --- |
| **Brand zone** | `menuButton` + logo | Hamburger toggle (optional) + compact SNAD logo | inline-start (right in RTL) |
| **Context zone** | `organizationName` | Current org / tenant name, centred | centre |
| **Action zone** | `search` | Global search input (hidden on mobile) | inline-end (left in RTL) |
| **Action zone** | `notifications` | Bell with badge | inline-end |
| **Action zone** | `aiAssistant` | AI assistant trigger | inline-end |
| **Action zone** | `userProfile` | Avatar + dropdown | inline-end (outermost) |

All zones use **logical CSS properties** (`margin-inline-end`,
`inset-inline-start`, etc.) so the layout mirrors correctly in RTL
without per-direction overrides.

---

## 2. Logo placement

### 2.1 The compact variant

The executive shell uses `<SnadLogo variant="compact" size="md" />` —
the 32×32 favicon-style mark without the wordmark. This is deliberate:

- The wordmark is illegible at 32px (see `LOGO_USAGE.md` §3).
- The compact mark is the same artwork used for the PWA install icon
  and the browser favicon, creating visual continuity across surfaces.
- The compact SVG is < 1 KB, adding negligible weight to the
  header's critical-path bundle.

### 2.2 Link destination

The logo is wrapped in a Next.js `<Link>` whose destination is
configurable via the `logoHref` prop:

| Surface | `logoHref` | `logoAriaLabel` |
| --- | --- | --- |
| Workspace (`/workspace`) | `/workspace` | "الذهاب إلى مساحة العمل" |
| Control plane (`/control-plane/*`) | `/control-plane` | "الذهاب إلى مركز الإدارة العليا" |
| CRM (`/crm/*`) | `/crm` | "الذهاب إلى إدارة العلاقات" |
| Settings (`/settings/*`) | `/workspace` | "الذهاب إلى مساحة العمل" |

The default is `/workspace`. Layouts that wrap their pages in
`<ExecutiveShell>` MUST pass the correct `logoHref` so users always
have a one-click "go home" affordance for that surface.

### 2.3 Section brand mark (in-page header)

Some surfaces (notably `/control-plane`) render an additional compact
logo inside their page-level `<header>` element. This **section brand
mark** is distinct from the sticky shell logo:

- The sticky shell logo orients the user globally ("you are in SNAD").
- The section brand mark orients the user locally ("you are in the
  control plane").

Both are permitted. The section brand mark MUST use `size="sm"` (32px)
and `margin-inline-end: var(--snad-space-3)` so it sits cleanly beside
the page title without crowding.

### 2.4 Accessibility

- When `href` is provided, the inner `<img>` is decorative:
  `alt=""` + `aria-hidden="true"`. The wrapping `<Link>` carries the
  accessible name via `aria-label`.
- The link has a visible `:focus-visible` ring using
  `--snad-color-focus-ring` at 3px with `--snad-space-1` offset
  (WCAG 2.2 SC 2.4.11).
- The link is keyboard-focusable (no `tabindex="-1"`).

---

## 3. Sticky header behaviour

### 3.1 CSS

```css
.shell {
  position: sticky;
  inset-block-start: 0;
  z-index: var(--snad-z-sticky);  /* 1100 — above content, below modals */
  block-size: clamp(56px, 7vw, 64px);
  contain: layout paint;
}
```

### 3.2 Why `sticky`, not `fixed`?

- `sticky` preserves the header's space in the normal flow, so the
  main content area does not need `padding-block-start` to compensate.
- `sticky` also means the header scrolls away if the user scrolls
  back to the very top of the page above it — which never happens on
  authenticated surfaces because the shell is the topmost element.
- `fixed` would require compensating padding and would break
  `position: sticky` sub-elements inside the main content (e.g.
  in-page toolbar tabs).

### 3.3 Stacking context

The header's `z-index: var(--snad-z-sticky)` (1100) sits above
page content (0–1000) but below dropdowns (1000+), modals (1400),
popovers (1500), tooltips (1600), and toasts (1700). This guarantees
that opening a modal or dropdown always covers the header correctly.

### 3.4 `contain: layout paint`

The `contain` property isolates the header's layout and paint work
from the rest of the document. This has two benefits:

1. **Performance:** scrolling the main content does not invalidate
   the header's paint.
2. **Correctness:** the header's `box-shadow` (which extends below
   its border-box) is clipped to the header's paint layer, so it
   does not bleed into the main content's stacking context.

---

## 4. Sidebar (future)

The shell does not currently render a sidebar. When one is added
(planned for Stage 08 Sprint 1), it MUST:

- Be a separate component (`<ExecutiveSidebar />`) rendered as a
  sibling of `<ExecutiveShell>` inside the layout, not as a child of
  the header.
- Use `position: fixed; inset-block-start: 64px; inset-inline-start: 0`
  so it sits below the sticky header and against the inline-start edge.
- Animate open/closed with `transform: translateX(0)` ↔
  `translateX(-100%)` (RTL: `translateX(100%)`) over
  `var(--snad-motion-standard)` (220ms).
- Trap focus when open (focus-trap-react or equivalent).
- Close on `Escape` and on outside-click.

---

## 5. Responsive breakpoints

| Breakpoint | Header height | Search | Org name | Hamburger |
| --- | --- | --- | --- | --- |
| ≥ 1024px (lg) | 64px | visible | visible | optional |
| 768–1023px (md) | 64px | visible | visible | optional |
| 600–767px (sm) | 60px | hidden | hidden | shown |
| < 600px (xs) | 56px | hidden | hidden | shown |

The `clamp(56px, 7vw, 64px)` height function smoothly interpolates
between these targets without media queries. Search and org name are
hidden via `display: none` at their respective breakpoints; the
consumer is responsible for surfacing them via the menu button on
mobile (progressive disclosure).

---

## 6. RTL / LTR

The shell is RTL-first. All CSS uses logical properties. The only
physical-property escape hatch is the `transform: translateX` for
the future sidebar animation — and even there, the direction is
gated by `[dir="rtl"]` selectors.

The compact logo is a square mark with no inherent directionality,
so it renders identically in both writing modes. The wordmark
variants (`primary`, `horizontal`, `white`) are NOT used in the
shell — they appear only on the auth screen and marketing surfaces.

---

## 7. Slot contracts

Every slot prop (`menuButton`, `search`, `notifications`,
`aiAssistant`, `userProfile`) receives a styled wrapper:

```tsx
<div className={styles.iconSlot}>{notifications}</div>
```

The wrapper enforces:

- Minimum 44×44 touch target (WCAG 2.2 SC 2.5.8).
- `:focus-visible` ring on the wrapper AND on any child `<button>`
  or `<a>` (via `:global()` selector).
- Hover background using `--snad-color-surface-secondary`.
- Smooth colour transition over `--snad-motion-fast` (140ms).

Slot consumers MUST:

- Render a single interactive element (`<button>` or `<a>`) inside
  the slot.
- Provide an `aria-label` on that element.
- NOT render multiple interactive elements inside a single slot —
  use a `<menu>` or popover instead.

---

## 8. Skip link

Every authenticated layout MUST render a skip link as the first
focusable element, before `<ExecutiveShell>`:

```tsx
<a href="#main-content" className={styles.skipLink}>
  تخطَّ إلى المحتوى الرئيسي
</a>
<ExecutiveShell>...</ExecutiveShell>
```

The skip link is visually hidden until focused, then appears at
`inset-block-start: 0; inset-inline-start: 0`. The `#main-content`
target is the `<main>` element rendered by `<ExecutiveShell>`.

---

## 9. Prohibited patterns

1. ❌ **Rendering the logo outside `<SnadLogo />`** — use the component.
2. ❌ **Passing `logoHref` to a non-route URL** — internal routes only.
3. ❌ **Removing the sticky positioning** — every authenticated surface
   must keep the header visible during scroll.
4. ❌ **Adding a second sticky header inside `<main>`** — only the
   shell header is sticky. Page-level headers (like the control-plane
   console's `<header>`) must scroll naturally.
5. ❌ **Hardcoding colours, fonts, or z-index** — use `var(--snad-*)`.
6. ❌ **Physical `left`/`right` CSS** — use logical properties.

---

## 10. Cross-references

- `LOGO_USAGE.md` — logo artwork rules.
- `AUTH_UI_GUIDE.md` — pre-login shell (auth screens).
- `WORKSPACE_BOOTSTRAP.md` — post-login bootstrap sequence.
- `ACCESSIBILITY.md` — SDS-wide WCAG 2.2 AA rules.
- `RTL_LTR_GUIDE.md` — SDS-wide RTL/LTR conventions.
- `COMPONENT_USAGE.md` — SDS component catalogue.
