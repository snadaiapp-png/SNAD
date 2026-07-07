# SNAD Auth UI Guide

> **Custodian:** SNAD Executive Office · SDS Frontend Guild
> **Status:** ACTIVE — binding for every authentication surface
> **Scope:** Login, forgot-password, reset-password, tenant-picker, credential-rotation, MFA challenge, session-expired banner.

This document is the **single source of truth** for the visual layout,
state machine, accessibility, RTL/LTR behaviour, dark-mode behaviour, and
performance budget of every SNAD authentication screen. Any PR that
modifies `apps/web/components/auth/*` or `apps/web/app/(auth)/*` MUST
comply with the rules below. The CI lint at
`scripts/ci/check-design-system-compliance.py` enforces the token-only
colour rule; this guide enforces everything else via code review.

---

## 1. Layout requirements

### 1.1 Two-panel shell (desktop ≥ 900px)

```
┌────────────────────────┬──────────────────────────┐
│                        │                          │
│  Intelligence Visual   │      Login Card          │
│  (petroleum panel      │   ┌──────────────────┐   │
│   with brand pulse,    │   │   SNAD Logo      │   │
│   domain signals,      │   │   (responsive)   │   │
│   headline, subtext)   │   ├──────────────────┤   │
│                        │   │   Welcome title  │   │
│                        │   │   Email field    │   │
│                        │   │   Password field │   │
│                        │   │   Forgot link    │   │
│                        │   │   Submit button  │   │
│                        │   │   Help link      │   │
│                        │   └──────────────────┘   │
└────────────────────────┴──────────────────────────┘
```

### 1.2 Single-panel (mobile < 900px)

The intelligence panel collapses to `display: none`. The login card
takes the full viewport width with `padding: 2rem 1.5rem`.

### 1.3 Logo placement (CLS-safe)

The SNAD logo is rendered by `<SnadLogo />` (the ONLY permitted brand
renderer — see `LOGO_USAGE.md`). It must be:

1. **Above the form** — never beside it, never below it.
2. **Centred horizontally** inside `.loginBrandMark`.
3. **Theme-aware** — `variant="white"` on dark backgrounds, `variant="primary"` on light. The `useTheme` hook at `@/lib/hooks/useTheme` resolves the active theme.
4. **Linked to `/`** — clicking the logo returns the user to the marketing home.
5. **CLS-safe** — the `.loginBrandMark` container reserves a
   `min-block-size: clamp(48px, 11vw, 103px)` so validation errors,
   MFA prompts, and theme switches NEVER shift the logo vertically.

#### 1.3.1 Width clamp (per breakpoint)

| Breakpoint | Min width | Preferred | Max width |
| --- | --- | --- | --- |
| Desktop (≥ 900px) | 260px | 30vw | 360px |
| Tablet (600–899px) | 220px | 30vw | 300px |
| Mobile (< 600px) | 170px | 50vw | 230px |

These clamps are implemented in `auth.module.css` via the
`--snad-logo-width` custom property on `.loginBrandMark`.

---

## 2. Field states

Every input must implement all eight states below. The default
`<input>` element provides `:hover`, `:focus`, `:disabled`, and
`:autofill` natively; the SDS auth styles layer `aria-invalid`,
`aria-busy`, and `data-success` on top.

| State | Trigger | Visual | Token |
| --- | --- | --- | --- |
| **default** | page load | 1px border, ivory background | `--snad-border-default`, `--snad-surface-primary` |
| **hover** | pointer over field | border brightens | `--snad-color-border-strong` |
| **focus** | keyboard or pointer focus | 3px focus ring, brand tint shadow | `--snad-color-focus-ring` |
| **filled** | non-empty value | no visual change (default keeps border) | — |
| **invalid** | `aria-invalid="true"` | error border + soft error shadow | `--snad-color-error`, `--snad-color-error-soft` |
| **disabled** | `disabled` attribute | 50% opacity, `not-allowed` cursor | inherited |
| **submitting** | `aria-busy="true"` on submit button | spinner + "جارٍ تسجيل الدخول…" label + button disabled | `--snad-brand-primary` |
| **success** | `data-success="true"` | green check icon, success border | `--snad-color-success` |

### 2.1 Password visibility toggle

The toggle is a `<button type="button">` positioned at
`inset-inline-end: 0.5rem`. It MUST:

- Toggle `type="password"` ↔ `type="text"`.
- Update `aria-label` to "إظهار كلمة المرور" / "إخفاء كلمة المرور".
- Have a 36×36 minimum touch target.
- Not submit the form (`type="button"`, not `type="submit"`).

---

## 3. Error presentation

### 3.1 Inline field errors

- Rendered as `<span role="alert">` immediately below the field.
- Connected to the input via `aria-describedby`.
- The input carries `aria-invalid="true"` while the error is visible.
- Errors clear on first keystroke (no lingering red border after the
  user starts correcting).

### 3.2 Form-level errors (auth failures)

- Rendered by `<AuthErrorAlert />` ABOVE the form, BELOW the logo.
- The alert has `role="alert"` so screen readers announce it on mount.
- The alert MUST NOT shift the logo. The `.loginBrandMark` min-height
  reservation (see §1.3) guarantees this.

### 3.3 Never leak raw backend errors

- No stack traces, no HTTP status codes, no URLs, no internal hostnames.
- Map every backend error to a `UserFacingError` (see
  `lib/api/user-facing-errors.ts`).
- Tests at `login-form.test.tsx` enforce this invariant.

---

## 4. Accessibility (WCAG 2.2 AA)

### 4.1 Required attributes

| Element | Attribute | Value |
| --- | --- | --- |
| Email input | `autocomplete` | `email` |
| Email input | `inputmode` | `email` |
| Email input | `dir` | `ltr` (even in RTL UI) |
| Password input | `autocomplete` | `current-password` |
| Password input | `dir` | `ltr` |
| Submit button | `aria-busy` | `true` while submitting |
| Forgot-password link | `aria-label` | "نسيت كلمة المرور؟ استعادة كلمة المرور" |
| SnadLogo | `alt` | "شعار سند — SNAD Business Operating System" |
| SnadLogo (with `href`) | inner `<img>` | `alt=""` + `aria-hidden="true"` (decorative) |

### 4.2 Focus management

- The email field receives autofocus on mount ONLY on desktop
  (`@media (min-width: 900px)`). On mobile, autofocus is suppressed to
  avoid popping the on-screen keyboard over the logo.
- Tab order: email → password → forgot link → submit → help link.
- `:focus-visible` rings use `--snad-color-focus-ring` at 3px with
  2px offset (WCAG 2.2 SC 2.4.11 Focus Not Obscured).

### 4.3 Reduced motion

- The intelligence-core pulse animation is disabled under
  `prefers-reduced-motion: reduce`.
- The auth spinner falls back to a static 50% opacity disc.

---

## 5. Dark mode

### 5.1 Detection order

1. `<html data-theme="...">` — explicit user toggle (none today, but
   the `useTheme` hook supports it for future profile pages).
2. `localStorage['snad-theme']` — returning users.
3. `prefers-color-scheme: dark` — OS-level.
4. `'light'` — safe default.

### 5.2 Visual deltas

| Element | Light | Dark |
| --- | --- | --- |
| Canvas | `--snad-color-background-default` (ivory) | `--snad-color-background-default` (charcoal, token overrides in `themes/dark.css`) |
| Logo | `variant="primary"` (petroleum + gold) | `variant="white"` (white + gold) |
| Submit button | `--snad-brand-primary` (petroleum) | `--snad-brand-primary` (lighter petroleum per dark theme) |
| Error alert | `--snad-color-error-soft` (light pink) | `--snad-color-error-soft` (muted dark red) |

### 5.3 No-flash guarantee

The `useTheme` hook returns `'light'` on SSR to avoid hydration
mismatch. After mount, it resolves the real theme and the
`.loginBrandMark` min-height reservation absorbs the logo swap with
zero CLS.

---

## 6. RTL / LTR

The entire auth UI is RTL-first (Arabic is the primary language).
All CSS uses **logical properties** (`margin-inline-*`,
`padding-inline-*`, `inset-inline-*`, `border-inline-*`). Physical
`left`/`right` are FORBIDDEN.

The email and password inputs are pinned to `dir="ltr"` because
emails and passwords are always Latin strings — but their labels,
placeholders, and error messages remain RTL.

---

## 7. Performance budget

See `AUTH_PERFORMANCE.md` for the full budget. Summary:

- First Contentful Paint: ≤ 1.2s on 4G.
- Time to Interactive: ≤ 2.5s on 4G.
- Layout shift (CLS): < 0.05 — the `.loginBrandMark` min-height is
  the primary CLS defence.
- Bundle: the auth screen must ship ≤ 80 KB JS (gzipped) excluding
  Next.js runtime.

---

## 8. Prohibited patterns

1. ❌ **Raw `<img src="/assets/brand/...">`** — use `<SnadLogo />`.
2. ❌ **`<div>SNAD</div>` as a brand mark** — use `<SnadLogo />`.
3. ❌ **Hardcoded hex/rgb colours** — use `var(--snad-*)` tokens.
4. ❌ **Physical `left`/`right` CSS** — use logical properties.
5. ❌ **`autofocus` on mobile** — suppress under 900px.
6. ❌ **Raw error strings from the backend** — map to `UserFacingError`.
7. ❌ **Disabling the form on submit without `aria-busy`** — always
   pair the two so screen readers announce the busy state.
8. ❌ **Cropping the logo** — see `LOGO_USAGE.md` §6.

---

## 9. Cross-references

- `LOGO_USAGE.md` — logo artwork, clear space, minimum sizes.
- `AUTH_PERFORMANCE.md` — performance budget and measurement strategy.
- `EXECUTIVE_SHELL_GUIDE.md` — post-login shell (header, sidebar).
- `WORKSPACE_BOOTSTRAP.md` — workspace bootstrap sequence.
- `ACCESSIBILITY.md` — SDS-wide WCAG 2.2 AA rules.
- `RTL_LTR_GUIDE.md` — SDS-wide RTL/LTR conventions.
