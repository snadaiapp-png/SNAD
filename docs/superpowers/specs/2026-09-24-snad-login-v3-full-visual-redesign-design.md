# SNAD Login v3 — Full Visual Redesign Design Specification

**Date:** 2026-09-24  
**Status:** APPROVED — implementation authorized  
**Repository:** `snadaiapp-png/SNAD`  
**Base branch:** `main`  
**Base SHA:** `3adf7ccef1e56de5ed586c2e9f255ddefc7f24ec`  
**Predecessor:** PR `#1142` — Login v2 official brand + authentication hardening  
**Scope:** Full visual redesign of the SNAD web login experience while preserving the existing authentication, session, tenant-isolation, executive-access, and destination-security contracts.  
**Out of scope:** Replacing the authentication backend, changing RBAC semantics, changing tenant membership rules, introducing new MFA/passkey backend behavior, global rebranding of every SNAD surface, or modifying the approved official wordmark artwork.

---

## 1. Purpose

Login v3 exists to solve a specific remaining gap after Login v2:

- Login v2 hardened authentication behavior, integrated the approved official SNAD wordmark, fixed responsive/accessibility defects, and reached production.
- The production screen still uses the legacy visual shell and legacy `AuthIntelligenceVisual` composition.
- Login v3 replaces that visual system with a purpose-built, modern authentication experience without changing the proven authentication core.

The target outcome is a production-grade enterprise login surface that feels deliberate, modern, calm, and distinctly SNAD rather than a restyled legacy two-panel screen.

---

## 2. Non-negotiable invariants

The following behavior is frozen unless a failing security test proves a defect:

1. `AuthEntry` remains the authentication state orchestrator.
2. `LoginForm` continues to call the existing `onLogin(email, password)` contract.
3. Tenant selection remains server-derived.
4. Tenant Admin does not imply Executive Operator.
5. `/executive/**` authorization remains server/policy controlled.
6. `returnUrl` remains treated as a request, never an entitlement.
7. Browser state must not become authoritative for tenant, role, capability, or executive access.
8. Refresh/session architecture remains BFF-oriented; no long-lived auth secret may be moved into `localStorage` or `sessionStorage`.
9. PostgreSQL Direct governance remains unchanged.
10. The official authentication wordmark remains the approved PNG asset:
   - `apps/web/public/assets/brand/snad-logo-official-wordmark.png`
   - intrinsic size `1162×337`
   - approved SHA-256 `98d0b84b0675b53f60837f803cf0a4bc85209eea650b213adba92a661bfde251`
11. The approved logo must not be recolored, redrawn, traced, vectorized, stretched, cropped, filtered, or synthesized into dark/light derivatives.

---

## 3. Current architecture baseline

The current login composition is:

`AuthEntry → LoginScreen → AuthIntelligenceVisual + LoginForm`

Current responsibilities:

- `auth-entry.tsx`: authentication state and post-login routing.
- `login-screen.tsx`: current shell composition.
- `login-form.tsx`: credentials, field validation, Caps Lock status, submit/loading/error behavior.
- `auth-intelligence-visual.tsx`: legacy dark narrative panel.
- `auth.module.css`: legacy shell, visual panel, form layout, shared auth styling.
- `login-v2.module.css`: small Login v2 responsive/touch-target corrections.
- `SnadLogo`: centralized governed logo renderer.

Login v3 must not create a parallel authentication path such as `/login-v3`. It replaces the visual composition inside the existing canonical flow.

---

## 4. Design direction

### 4.1 Visual character

The new experience uses an **asymmetric editorial enterprise composition**, not the current rigid dark/light split.

Visual principles:

- calm, high-trust enterprise tone;
- strong typography and spacing hierarchy;
- official SNAD wordmark as the primary identity anchor;
- deep SNAD green used selectively for structure and interaction, not as a full-screen slab;
- gold used as a restrained accent only;
- subtle depth through surface layering, borders, and ambient gradients;
- no decorative “AI grid”, glowing core, fake dashboard chrome, particle field, or heavy sci-fi treatment;
- motion is light, functional, and optional under `prefers-reduced-motion`.

The screen should look intentionally designed for SNAD rather than generated from a generic SaaS login template.

### 4.2 Desktop composition

For desktop (`≥ 1024px`):

- full-canvas neutral background with subtle SNAD ambient treatment;
- a bounded authentication composition centered within a max-width layout;
- form region remains the primary interaction area;
- supporting brand/narrative region is secondary and quieter than the form;
- no 50/50 wall split;
- no duplicate logo;
- no large dark rectangle consuming half the viewport;
- visual hierarchy must remain stable at 1280×720 and larger.

### 4.3 Tablet composition

For tablet (`768–1023px`):

- collapse decorative/narrative content;
- keep a single dominant auth container;
- preserve enough brand context to avoid feeling like a generic white form;
- maintain all controls without horizontal overflow.

### 4.4 Mobile composition

For mobile (`<768px`):

- single-column layout;
- logo, title, fields, primary CTA, recovery/help links all reachable without clipped top content;
- form starts near the top with safe spacing rather than forced vertical centering;
- no decorative region that competes with authentication;
- touch targets minimum `44×44px`, form controls target `48px+`;
- tested at `360×640`, `390×844`, and `430×932`.

---

## 5. Component architecture

Login v3 introduces an isolated visual layer:

```text
apps/web/components/auth/
  auth-entry.tsx                 # unchanged orchestration
  login-screen.tsx               # composition only
  login-v3-shell.tsx             # new
  login-v3-brand-panel.tsx       # new
  login-form.tsx                 # auth behavior preserved
  login-v3.module.css            # new isolated visual system
  auth.module.css                # legacy/shared auth styles retained
```

### 5.1 `LoginV3Shell`

Responsibilities:

- page-level layout;
- responsive composition;
- neutral/ambient background;
- form region placement;
- reduced-motion handling;
- no authentication business logic.

### 5.2 `LoginV3BrandPanel`

Responsibilities:

- concise product/brand narrative;
- optional domain/value indicators;
- decorative visual layer only;
- `aria-hidden` where content is purely decorative;
- no duplicate official logo;
- no hardcoded authentication state.

### 5.3 `LoginForm`

Preserved responsibilities:

- email/password entry;
- local field validation;
- Caps Lock status;
- show/hide password;
- submit state;
- session-expired/error rendering;
- forgot-password/help actions;
- calling the existing login callback.

Changes to `LoginForm` are limited to visual hooks/classes needed by V3 and accessibility fixes proven necessary by tests.

---

## 6. Content hierarchy

Primary hierarchy:

1. Official SNAD wordmark.
2. Clear welcome/authentication heading.
3. One concise supporting line.
4. Email.
5. Password.
6. Forgot-password action.
7. Primary login CTA.
8. Contextual help.
9. Errors/session-expired state inserted without moving the primary identity anchor unpredictably.

Narrative content must remain short. The login screen is an identity gateway, not a marketing landing page.

No unverified performance, security, customer-count, AI-capability, or compliance claims may be introduced.

---

## 7. Interaction states

Login v3 must explicitly design and test:

- idle;
- email validation error;
- password required error;
- invalid credentials;
- authenticating;
- slow authentication;
- session expired;
- session retry;
- Caps Lock on;
- password visible/hidden;
- keyboard focus;
- help expanded;
- 200% zoom;
- Arabic RTL;
- English/LTR copy where supported;
- reduced motion.

The UI must not leak whether a user account exists.

---

## 8. Motion model

Allowed motion:

- subtle shell entrance;
- focus/hover transitions;
- small status transitions;
- restrained ambient background drift only if performance-safe.

Forbidden:

- continuous distracting pulses;
- mandatory parallax;
- animation that shifts form controls during entry;
- motion required to understand state;
- animation under `prefers-reduced-motion: reduce`.

All transitions must remain short and functional.

---

## 9. Accessibility requirements

Target: WCAG 2.2 AA.

Required verification:

- keyboard-only login;
- visible focus states;
- logical tab order;
- no keyboard traps;
- correct `aria-invalid` / error association;
- `aria-busy` while authenticating;
- live announcement for status/error changes where appropriate;
- 200% text zoom without clipping or loss of controls;
- contrast AA for text, controls, and focus indicators;
- reduced-motion compliance;
- no horizontal scrolling on target mobile widths;
- password reveal control accessible by name;
- official logo accessible exactly once.

---

## 10. RTL and localization

Arabic remains first-class.

Rules:

- layout uses logical CSS properties;
- page copy follows current locale direction;
- email/password technical inputs remain `dir="ltr"`;
- icon placement must not assume physical left/right;
- no visual order reversal that changes semantic or tab order;
- design must remain balanced in Arabic and English.

---

## 11. Security boundary

Login v3 is presentation work. It must not weaken any existing security control.

Regression gates must prove:

- external/protocol-relative `returnUrl` rejected;
- nested executive return URL allowed only with effective executive destination access;
- tenant A cannot cross to tenant B;
- tenant admin cannot gain executive capability;
- disabled/expired sessions remain blocked;
- authentication errors remain generic at the server boundary;
- no new tokens stored in browser-accessible persistent storage;
- no raw backend exception text exposed.

---

## 12. Styling isolation strategy

Do not rewrite the full legacy `auth.module.css` during the first Login v3 implementation.

Use:

`login-v3.module.css`

for V3 layout and visual composition.

Reasons:

- limits regression blast radius;
- allows tenant picker / credential rotation / legacy auth states to remain stable;
- makes V3 visual regression evidence attributable;
- enables later migration of other auth surfaces deliberately.

Shared field primitives may remain in `auth.module.css` initially if their behavior is already correct.

Unrelated refactoring is prohibited.

---

## 13. Testing strategy

### 13.1 Component/unit

Add or update tests for:

- V3 shell composition;
- official wordmark remains `official-wordmark`;
- no `AuthIntelligenceVisual` in Login v3;
- form callback contract unchanged;
- field validation unchanged;
- Caps Lock state;
- password visibility;
- session-expired state;
- generic error behavior;
- reduced-motion class/media behavior where testable.

### 13.2 Visual regression

Playwright screenshots at minimum:

- Arabic desktop light/system rendering;
- Arabic mobile `360×640`;
- English desktop if locale route/state is available;
- error state;
- authenticating state;
- 200% zoom reachability;
- reduced motion.

Visual regression must fail on:

- missing/zero-size logo;
- horizontal overflow;
- clipped submit button;
- legacy split-panel returning unintentionally.

### 13.3 Accessibility

Run Axe on target states with zero `serious` or `critical` violations.

Manual checks:

- keyboard-only;
- focus visibility;
- 200% zoom;
- Arabic RTL;
- reduced motion.

### 13.4 Existing security regression

Existing Login v2 destination, session, tenant, executive, and auth regression tests remain required and must pass unchanged unless a test itself is proven incorrect.

Backend verification remains PostgreSQL Direct only.

---

## 14. Preview and release model

Login v3 must ship through a new branch and new PR after this specification and its implementation plan are approved.

Recommended implementation branch:

`design/auth-login-v3-full-visual-redesign`

Recommended PR title:

`feat(auth): SNAD Login v3 full visual redesign`

Release order:

1. exact-head baseline;
2. branch implementation;
3. unit/lint/type/build;
4. Playwright + Axe;
5. security/auth regression;
6. preview deployment;
7. human desktop review;
8. human mobile review;
9. current-head approval;
10. merge;
11. post-merge verification;
12. production promotion only after explicit production review.

No production promotion is permitted merely because CI is green.

---

## 15. Expected file changes

Primary:

- `apps/web/components/auth/login-screen.tsx`
- `apps/web/components/auth/login-v3-shell.tsx` — new
- `apps/web/components/auth/login-v3-brand-panel.tsx` — new
- `apps/web/components/auth/login-v3.module.css` — new
- `apps/web/components/auth/login-form.tsx` — minimal visual hooks only
- auth component tests
- `apps/web/e2e/auth-login-v3.spec.ts` or equivalent visual coverage

Potentially:

- auth i18n resources if approved copy requires new keys;
- auth UI documentation;
- visual baseline artifacts.

Do not modify backend auth code unless a failing regression test exposes a concrete defect.

---

## 16. Definition of done

Login v3 is complete only when:

- legacy `AuthIntelligenceVisual` is no longer part of the login screen;
- official approved PNG wordmark is visible and unchanged;
- desktop, tablet, and mobile compositions are intentionally redesigned;
- Arabic RTL is visually correct;
- target mobile sizes have no horizontal overflow;
- short-height desktop remains fully reachable;
- keyboard/focus behavior passes;
- 200% zoom passes;
- reduced motion passes;
- Axe has zero serious/critical violations;
- Login v2 security/session/destination regressions still pass;
- PostgreSQL Direct backend gates pass where required;
- exact-head CI is green;
- preview is reviewed visually before merge;
- merged `main` passes post-merge verification;
- production domain serves the exact approved merged SHA;
- production visual verification confirms the V3 shell, not only a successful deployment.

---

## 17. Explicit non-goals for this PR

The Login v3 PR must not:

- introduce functional MFA or passkeys;
- redesign tenant authorization semantics;
- change tenant memberships;
- change executive capability policy;
- replace BFF session transport;
- perform a global logo migration;
- redesign the entire authenticated application shell;
- redesign HRM/CRM/ERP pages;
- invent backend claims or security badges;
- silently alter production domains.

---

## 18. Follow-up path

After Login v3 is stable, the V3 shell may be reused deliberately for:

- forgot-password;
- reset-password;
- credential rotation;
- tenant picker;
- future MFA/passkey challenge.

Those migrations should be individually tested. They are not automatically included in the first Login v3 PR unless explicitly added to scope before implementation.

---

## 19. Approval gate

Implementation was explicitly approved by the user on 2026-09-24 with execution method `Native`.

After approval:

1. create the implementation plan;
2. create the dedicated branch;
3. implement with TDD;
4. open a new PR;
5. verify exact-head CI and visual preview before merge.
