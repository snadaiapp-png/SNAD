# SNAD Auth UI Guide

> **Custodian:** SNAD Executive Office · SDS Frontend Guild
> **Status:** ACTIVE — binding for every authentication surface
> **Scope:** Login, forgot-password, reset-password, tenant-picker, credential-rotation, MFA challenge, session-expired banner.

This document is the **single source of truth** for the visual layout, state behavior, accessibility, RTL/LTR behavior, brand rendering, and performance budget of SNAD authentication screens. Any PR that modifies `apps/web/components/auth/*` or `apps/web/app/(auth)/*` must comply with these rules.

---

## 1. Layout requirements

### 1.1 Two-panel shell (desktop ≥ 900px)

The authentication shell uses a narrative/intelligence panel and a focused login panel. The intelligence panel communicates product value; the light/neutral login panel owns the authentication brand mark and form.

### 1.2 Single-panel (mobile < 900px)

The intelligence panel collapses. The login card takes the available width with safe inline padding and vertical scrolling. `100svh` is the compatibility minimum and `100dvh` is the preferred dynamic viewport height where supported. The form must remain reachable at `360×640`, `1024×768`, `1280×720`, and 200% text zoom without horizontal scrolling.

### 1.3 Authentication wordmark (CLS-safe)

Authentication surfaces render the user-approved wordmark only through:

```tsx
<SnadLogo variant="official-wordmark" size="responsive" />
```

The approved asset is `snad-logo-official-wordmark.png`, PNG RGBA `1162×337`, SHA-256 `98d0b84b0675b53f60837f803cf0a4bc85209eea650b213adba92a661bfde251`.

Requirements:

1. It appears **above the form** on the light/neutral auth panel.
2. It is centered in `.loginBrandMark` and linked to `/`.
3. It keeps the exact `1162:337` aspect ratio and must never be recolored, traced/vectorized, cropped, stretched, filtered, or given visual effects.
4. Do **not** synthesize a white/dark/compact variant. Dark mode may change the surrounding surface tokens, but the approved artwork itself is unchanged and must remain on a suitable light/neutral brand-safe area.
5. The intelligence panel is not a second logo canvas. Product copy such as `SNAD • سند` is text, not a substitute logo.
6. `.loginBrandMark` retains a reserved block size to prevent validation, MFA, session, or locale states from shifting the logo.

#### 1.3.1 Width clamp

| Breakpoint | Min width | Preferred | Max width |
| --- | --- | --- | --- |
| Desktop (≥ 900px) | 260px | 30vw | 360px |
| Tablet (600–899px) | 220px | 30vw | 300px |
| Mobile (< 600px) | 170px | 50vw | 230px |

---

## 2. Field and interaction states

Every credential input supports default, hover, focus, filled, invalid, disabled, and submitting behavior. Primary credential controls have a minimum block size of `48px`; interactive targets, including password visibility, are at least `44×44px`.

### 2.1 Password visibility

The toggle is `type="button"`, uses logical positioning, toggles `password` ↔ `text`, and updates its accessible label. It never submits the form.

### 2.2 Caps Lock advisory

When the password field reports `getModifierState("CapsLock") === true`, show a non-blocking `role="status"` advisory. It must clear when Caps Lock is no longer active or the password field loses focus. It must not disable paste, password-manager fill, or submission.

---

## 3. Error presentation

### 3.1 Inline field errors

- Render immediately below the affected field with `role="alert"`.
- Connect the input through `aria-describedby`.
- Set `aria-invalid="true"` while present.
- Clear the validation error on the first corrective edit so stale red state does not remain while the user fixes input.

### 3.2 Form-level errors

`<AuthErrorAlert />` renders below the logo and above the form. The alert must be announced to assistive technology and must not cause brand-layout instability.

### 3.3 No information leakage

Never expose stack traces, HTTP status details, internal URLs/hostnames, SQL/Java exception names, raw backend messages, or account-existence distinctions. Map backend failures to `UserFacingError`.

---

## 4. Accessibility — WCAG 2.2 AA

| Element | Required behavior |
| --- | --- |
| Email input | `autocomplete="username"`, `inputmode="email"`, `dir="ltr"` |
| Password input | `autocomplete="current-password"`, `dir="ltr"` |
| Submit | disabled while authenticating and paired with `aria-busy` |
| Forgot password | keyboard-reachable link with accessible name |
| Logo link | anchor owns the accessible name; inner image is decorative |
| Caps Lock message | advisory `role="status"`, not an error |

Focus indicators must remain visible and unobscured. Keyboard order follows the visual workflow. Reduced-motion preferences disable non-essential animation.

---

## 5. Theme behavior

Theme affects surfaces, text, borders, controls, and the existing non-auth SVG family. **It does not transform the approved authentication PNG.** Login v2 selects `variant="official-wordmark"` explicitly and does not use `useTheme` to swap it to an invented reverse version.

If a future dark authentication composition requires a reverse logo, the brand authority must supply and approve that artwork as a distinct governed asset before implementation.

---

## 6. RTL / LTR

Authentication is RTL-first. Layout code should use logical CSS properties. Email and password content remain `dir="ltr"` while labels, help copy, alerts, and narrative copy follow the active locale. Locale state remains owned by `I18nProvider`; do not create a second direction source.

---

## 7. Security-bound navigation

Authentication success does not grant route access. `returnUrl` is navigation intent only. A return destination must be an internal path and its normalized root must exist in the server-provided `availableDestinations` for the authenticated principal.

In particular, `/executive/tenants` is valid only when `/executive` is granted. A tenant admin without executive capability must fall back to `/workspace`; UI visibility is not an authorization boundary.

---

## 8. Performance budget

See `AUTH_PERFORMANCE.md` for measurement details. Targets remain:

- First Contentful Paint ≤ 1.2s on the documented 4G profile.
- Time to Interactive ≤ 2.5s on the documented 4G profile.
- CLS < 0.05.
- Auth route JS budget ≤ 80 KB gzip excluding Next.js runtime.

The approved wordmark is above the fold and may use Next.js image priority, but must retain intrinsic dimensions to prevent layout shift.

---

## 9. Prohibited patterns

1. Raw `<img src="/assets/brand/...">` or direct brand-asset imports outside `SnadLogo`.
2. Plain-text `SNAD` used as a visual replacement for the approved login wordmark.
3. Recoloring, CSS filters, tracing/vectorizing, cropping, or synthesizing derivatives of the approved authentication PNG.
4. Hardcoded colors instead of SDS tokens.
5. New physical left/right assumptions in auth layout code; use logical properties.
6. Raw backend errors or account-enumeration copy.
7. Token/refresh-secret persistence in `localStorage` or `sessionStorage`.
8. Treating `returnUrl`, `tenantId`, role, capability, or executive state from the browser as authoritative.
9. Presenting MFA/passkeys/SSO controls as active before the corresponding Phase B security implementation exists.

---

## 10. Verification gates

Before an auth UI change is released:

- Vitest auth/SDS tests pass.
- Web typecheck, lint, and build pass.
- Logo/brand/design-system governance passes.
- Login v2 Playwright/Axe checks pass for required anonymous states; credential-dependent checks are reported as `SKIPPED`, never `PASS`, when credentials are absent.
- PostgreSQL Direct backend auth, session, rate-limit, tenant-isolation, and executive-boundary tests have `FAILURES=0`, `ERRORS=0`; environment skips do not count as certification.
- The deployed/tested SHA is exact and desktop/mobile evidence comes from that same SHA.

---

## 11. Cross-references

- `LOGO_USAGE.md` — artwork integrity and scope.
- `AUTH_PERFORMANCE.md` — auth performance budget.
- `WORKSPACE_BOOTSTRAP.md` — post-login bootstrap.
- `EXECUTIVE_SHELL_GUIDE.md` — post-login shell.
- `ACCESSIBILITY.md` — SDS WCAG requirements.
- `RTL_LTR_GUIDE.md` — direction conventions.
- `docs/superpowers/specs/2026-09-23-snad-auth-login-v2-official-brand-design.md` — Login v2 design authority.
