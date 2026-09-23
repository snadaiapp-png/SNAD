# SNAD Login v2 — Official Brand + Authentication Hardening Design

**Date:** 2026-09-23  
**Status:** DESIGN APPROVED IN CHAT — implementation not started  
**Base SHA:** `7a8399e25fe97758ee1e0ff6df512d18f8654da1` (`main`)  
**Design branch:** `design/auth-login-v2-official-brand`  
**Scope:** Web authentication entry experience, official logo integration, post-login destination safety, session/auth hardening verification, and future MFA/passkey extension points.  
**Out of scope:** Global rebrand of every SNAD surface, HRM G2 functional work, Docker/Testcontainers, replacing PostgreSQL Direct governance, or granting authorization based on UI state.

---

## 1. Intent and success criteria

The goal is not to cosmetically restyle one login form. The goal is to make the SNAD authentication entry point a production-grade identity gateway that:

1. uses the user-approved official SNAD logo without redrawing, recoloring, stretching, tracing, or inventing missing variants;
2. presents a modern, readable, RTL-first login experience on desktop, tablet, and mobile;
3. preserves and strengthens the existing multi-tenant and executive authorization model;
4. validates post-login navigation against effective server-derived access;
5. provides deterministic, accessible states for loading, errors, session expiry, tenant ambiguity, and credential rotation;
6. creates clean extension points for MFA, passkeys/WebAuthn, trusted-device step-up, and enterprise SSO without mixing those future controls into the initial UI refactor;
7. ships only after security, tenant-isolation, accessibility, visual, and production evidence gates pass.

The user-approved visual reference is the supplied official SNAD PNG wordmark. The original supplied image is `1536×1024` RGBA. A web derivative was prepared only by cropping surrounding transparent/unused area; the approved web derivative is `1162×337` RGBA. No artwork geometry or color is to be changed.

---

## 2. Current baseline findings

### 2.1 Existing web architecture

The current web app already has a structured authentication subsystem rather than a single page:

- `apps/web/components/auth/login-screen.tsx` composes the authentication shell.
- `apps/web/components/auth/login-form.tsx` already includes email validation, password visibility toggle, forgot-password navigation, error handling, loading state, `aria-busy`, LTR technical inputs, and password-manager compatible autocomplete attributes.
- `apps/web/components/auth/auth-entry.tsx` already routes between login, tenant picker, session refresh/loading, credential rotation, and authenticated redirect states.
- `apps/web/lib/auth/destination.ts` already normalizes internal paths, rejects protocol-relative/external/backslash destinations, and checks destinations against the authenticated user’s available destination roots.
- `apps/web/app/providers.tsx` treats `/executive` as a protected root.
- `apps/web/components/sds/SnadLogo.tsx` is the centralized logo renderer and CI governance prevents raw brand-asset references outside that component.

Therefore Login v2 must improve the existing subsystem rather than replace it with a parallel login implementation.

### 2.2 Existing backend protections

The current backend already contains important controls that must be preserved:

- `/api/v1/auth/login`, `/refresh`, `/logout`, `/me`, forgot/reset password, and credential rotation are centralized in `AuthController`.
- Login rate-limit keys are derived server-side through `LoginRateLimitKeys`.
- Forgot-password returns a generic success response to avoid account enumeration.
- Login bootstrap is enriched from server-side memberships, role grants, capabilities, organization memberships, and `LoginDestinationResolver`.
- Available destinations are capability-derived rather than trusted from the browser.
- Refresh token transport is intentionally separated from normal access-token persistence and is consumed by the trusted BFF flow.
- Current history includes a multi-tab refresh-rotation race fix; Login v2 must not regress it.

### 2.3 Brand inconsistency discovered

The repository currently describes `apps/web/public/assets/brand/snad-logo-primary.svg` as the canonical official logo. That SVG is a different lockup: a rounded-square glyph plus `SNAD` and Arabic `سند`. The user has now explicitly approved a different stylized SNAD wordmark as the official logo for this work.

This creates a real governance conflict. The implementation must not silently overwrite every global logo reference in the same change. Login v2 will introduce the approved wordmark through the centralized `SnadLogo` component for authentication surfaces first. A separate global brand-migration decision can later promote it across the entire product after all required light/dark/compact/favicon variants are approved.

### 2.4 Screenshot/deployment discrepancy

The supplied production screenshot shows the text lockup `SNAD • سند` in the intelligence panel and does not visibly show the official wordmark above the login form. Current `main`, however, already renders `<SnadLogo />` in `login-form.tsx`.

This mismatch must be treated as a deployment/evidence problem until proven otherwise. Implementation must verify the exact deployed SHA and asset loading rather than assuming the screenshot represents current `main`.

---

## 3. Design decisions

### 3.1 One auth system, no duplicate page

Keep `AuthEntry → LoginScreen → LoginForm` as the canonical path. Do not create a second `/login-v2` implementation that duplicates authentication logic. The visual and behavior changes land in the existing auth components.

### 3.2 Official logo integration

Add the approved PNG as a new governed asset under:

`apps/web/public/assets/brand/snad-logo-official-wordmark.png`

The asset must be stored losslessly and checked into the repository exactly as approved.

Extend `SnadLogo` with a dedicated governed variant such as:

`official-wordmark`

This variant references the approved PNG only from `SnadLogo.tsx`, preserving the existing rule that raw brand paths are forbidden elsewhere.

Authentication surfaces use `variant="official-wordmark"` on a light/neutral surface.

Do **not** generate a white, monochrome, SVG, favicon, or compact derivative automatically. Those are separate artwork decisions. Until a reverse/light variant is explicitly approved, the dark intelligence panel must not display a recolored version of this mark.

### 3.3 Intelligence panel branding

The dark intelligence panel remains a product/value panel, not a second logo canvas. The current text `SNAD • سند` may remain as normal product copy or be replaced by a neutral product kicker, but it must not visually pretend to be the official logo.

The official wordmark is shown once, prominently, above the authentication form on the light panel. This prevents duplication and avoids contrast hacks on the dark panel.

### 3.4 Responsive composition

Desktop (`≥900px`): retain two-panel layout, approximately 55/45 visual-to-form balance.  
Tablet: maintain a centered auth card with reduced visual dominance.  
Mobile (`<900px`): collapse the intelligence panel and prioritize the authentication flow.

The auth card target width is `420–460px`, with a hard maximum that prevents long-line forms. Controls have a minimum interactive height of `48px` and touch targets of at least `44×44px`.

Use `100dvh` where supported with safe fallbacks (`100svh`) and preserve vertical scrolling for short viewports and 200% text zoom.

### 3.5 RTL/LTR

The page remains RTL-first. Labels, help text, alerts, and narrative copy follow the active locale. Email and password inputs remain `dir="ltr"`. All layout styles use logical CSS properties.

### 3.6 Error and loading behavior

Preserve generic server-facing authentication errors. Do not expose whether an account exists. Field validation remains local and specific (empty/invalid email, missing password).

The form must preserve:

- disabled submit while authenticating;
- `aria-busy`;
- a delayed “still processing” label for slow requests;
- screen-reader announced form-level errors;
- no raw backend errors;
- no double submit;
- retry only for recoverable session-restore failures.

Add/verify Caps Lock indication as a non-blocking enhancement. Do not disable paste in password fields.

---

## 4. Authentication and authorization model

### 4.1 Authentication does not grant route access

Successful credential verification yields identity. Route access remains derived from server-side user context:

`identity → tenant → memberships → role grants → capabilities → available destinations → requested route policy`

The browser must not be authoritative for `tenantId`, role, capability, executive status, or destination authorization.

### 4.2 Tenant selection

The existing ambiguous-tenant flow remains canonical. If an email maps to more than one permitted tenant context, the user selects from server-provided tenant candidates. Arbitrary client-supplied tenant IDs are never trusted without server verification.

### 4.3 Executive/control-plane separation

`Tenant Admin` must never imply `Executive Operator` access. `/executive/**` remains protected by explicit server authorization/capability policy. UI visibility is only a convenience layer and must never be the security boundary.

### 4.4 Return URL

The current `safeReturnUrl` behavior is retained and strengthened by tests. A `returnUrl` is a navigation request, not an entitlement.

Required rejection cases include:

- `https://evil.example`
- `//evil.example`
- encoded protocol-relative paths
- backslash based paths
- control-character payloads
- internal paths not present in the authenticated user’s effective destination set
- executive paths for a non-executive principal

If validation fails, the user lands at the canonical safe fallback (`/workspace`).

---

## 5. Session model

Login v2 does not replace the current token/session architecture without evidence of a defect. The existing BFF-oriented design is preserved.

Implementation must verify and lock with tests:

1. browser-accessible storage does not contain long-lived refresh secrets;
2. refresh rotation remains serialized across tabs;
3. logout revokes server-side refresh sessions as intended;
4. session restore cannot cross tenant boundaries;
5. expired/rotated credentials force the documented state transition;
6. authentication responses and sensitive routes use `Cache-Control: no-store`;
7. production cookies/headers use the required Secure/HttpOnly/SameSite semantics at the BFF boundary.

No migration to localStorage/sessionStorage token persistence is permitted.

---

## 6. Advanced authentication roadmap

MFA and passkeys are deliberately staged so the visual redesign does not pretend those controls already exist.

### Phase A — Login v2 foundation

- official approved logo on auth surfaces;
- responsive/accessibility correction;
- deployed-SHA parity check;
- destination/security regression tests;
- session and tenant-isolation verification;
- observability and security-event correlation where already supported.

### Phase B — Administrative MFA

Introduce a real step-up state in the auth state machine for privileged accounts. Preferred order:

1. WebAuthn/passkey;
2. TOTP authenticator;
3. recovery codes.

SMS is not a preferred high-assurance factor. Enterprise SSO may delegate MFA to the identity provider when policy guarantees are available.

### Phase C — Adaptive authentication

A risk engine may recommend deterministic actions such as `ALLOW`, `STEP_UP_MFA`, `REAUTHENTICATE`, `THROTTLE`, or `BLOCK`. AI/ML may contribute signals, but it must never grant a capability, tenant membership, or executive role. Authorization remains deterministic and policy-driven.

---

## 7. Files expected to change during implementation

Primary web files:

- `apps/web/public/assets/brand/snad-logo-official-wordmark.png`
- `apps/web/components/sds/SnadLogo.tsx`
- `apps/web/components/sds/SnadLogo.module.css` (only if required for intrinsic ratio/responsive sizing)
- `apps/web/components/sds/__tests__/SnadLogo.test.tsx`
- `apps/web/components/auth/login-form.tsx`
- `apps/web/components/auth/auth-intelligence-visual.tsx`
- `apps/web/components/auth/auth.module.css`
- `apps/web/components/auth/__tests__/*` as applicable
- `apps/web/lib/auth/destination.ts` only if tests expose a concrete gap
- `apps/web/e2e/*auth*` and executive/tenant isolation acceptance coverage

Documentation/governance files:

- `apps/web/public/assets/brand/README.md`
- `apps/web/design-system/documentation/LOGO_USAGE.md`
- `apps/web/design-system/documentation/AUTH_UI_GUIDE.md`
- brand changelog/process evidence required by repository governance

Backend files should change only if a failing security test demonstrates a gap. Existing controls must not be rewritten speculatively.

---

## 8. Test strategy and release gates

### 8.1 Unit/component

- official logo variant renders the approved asset path;
- no raw brand path appears outside `SnadLogo`;
- correct intrinsic ratio / no CLS regression;
- email/password validation;
- show/hide password;
- no double-submit;
- screen-reader error wiring;
- session-expired/retry behavior;
- tenant picker transition;
- credential-rotation transition;
- malicious `returnUrl` cases;
- unauthorized destination fallback.

### 8.2 Backend/security

- valid/invalid login;
- account enumeration resistance;
- login rate limiting;
- refresh rotation and replay handling;
- logout revocation;
- password reset token lifecycle;
- tenant A → tenant B denial;
- tenant admin → executive denial;
- anonymous → executive denial;
- disabled tenant/account behavior;
- capability-derived destinations;
- no unsafe external redirect.

Backend integration remains **PostgreSQL Direct only**. Docker/Testcontainers are out of scope.

### 8.3 Accessibility and UX

- WCAG 2.2 AA automated + manual checks;
- keyboard-only flow;
- visible focus;
- 200% text zoom;
- reduced motion;
- RTL Arabic;
- LTR technical inputs;
- desktop/tablet/mobile viewport matrix;
- Chrome, Edge, Firefox, Safari where CI/environment permits.

### 8.4 Production evidence

Before closure capture authenticated evidence for:

- desktop login;
- mobile login;
- failed credentials;
- session expiry;
- tenant ambiguity when applicable;
- successful safe return URL;
- rejected unauthorized return URL;
- executive authorization boundary.

The deployed SHA must match the tested SHA.

---

## 9. Definition of done

Login v2 is not complete until all of the following are true:

- official approved wordmark is visible on the authentication surface;
- no unapproved recolor/vectorization/derivative was introduced;
- exact deployed SHA is known and matches evidence;
- auth functional suite passes;
- security suite passes;
- tenant isolation passes;
- executive isolation passes;
- return URL security passes;
- session/refresh regression suite passes;
- accessibility/RTL/responsive gates pass;
- logo governance CI passes;
- visual regression evidence exists for desktop and mobile;
- zero Critical/High authentication findings remain;
- zero known tenant escape, privilege escalation, MFA bypass (when Phase B exists), or open redirect remains.

---

## 10. Implementation sequencing

1. Establish exact-head baseline and current deployment SHA.
2. Add the approved wordmark as a governed asset and extend `SnadLogo`.
3. Update login composition and responsive sizing without changing auth business logic.
4. Update auth/brand documentation to describe the approved auth wordmark and its intentionally limited scope.
5. Add/repair unit and visual tests.
6. Add/repair return URL, tenant, executive, refresh, and session regression tests.
7. Run web checks and PostgreSQL Direct backend checks.
8. Deploy the exact tested SHA.
9. Capture desktop/mobile authenticated evidence.
10. Only then close Login v2 Phase A; MFA/passkeys proceed as a separate Phase B implementation plan.

This sequencing minimizes risk by separating brand/UI change from identity-policy expansion while preserving the existing production authentication core.