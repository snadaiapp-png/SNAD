# SNAD Design System — Definition of Done

**Version:** 1.0.0
**Date:** 2026-07-07
**Status:** ACTIVE — mandatory for all SNAD frontend work

---

## 1. Purpose

This document defines the mandatory criteria that must be met before any frontend PR can be merged. No PR is considered "done" until ALL applicable criteria are verified PASS.

---

## 2. Implementation Checklist

### 2.1 Brand Identity

- [ ] Official name **SNAD** (English) / **سند** (Arabic) used everywhere
- [ ] No usage of `SANAD`, `Sanad`, `Snad App`, or any variant
- [ ] `SnadLogo` component used for ALL logo rendering — no direct SVG imports
- [ ] Official colors only: `#0E3D38` (petroleum green) + `#D4AF37` (royal gold)
- [ ] No hardcoded hex/rgb/hsl values — ALL colors via SDS tokens

### 2.2 Design Tokens

- [ ] All visual values use SDS tokens (`var(--snad-color-*)`, `var(--snad-space-*)`, etc.)
- [ ] No hardcoded spacing, radius, shadow, or z-index values
- [ ] No hardcoded font-family declarations
- [ ] `python3 scripts/ci/check-design-system-compliance.py` PASSES

### 2.3 Components

- [ ] SDS components used from `apps/web/components/sds/` where available
- [ ] No duplicate local components when SDS component exists
- [ ] New components follow SDS patterns (tokens, RTL, WCAG 2.2 AA)
- [ ] Forward refs on all interactive components

### 2.4 Internationalization

- [ ] Tested with **Arabic (RTL)** layout
- [ ] Tested with **English (LTR)** layout
- [ ] Logical CSS properties used (`margin-inline-*`, `padding-inline-*`)
- [ ] No physical-direction CSS (`margin-left`, `padding-right`)

### 2.5 Themes

- [ ] Tested in **Light Mode**
- [ ] Tested in **Dark Mode**
- [ ] Text contrast ≥ 4.5:1 in both modes
- [ ] No flash of incorrect theme on load (FOUC prevention)

### 2.6 Accessibility (WCAG 2.2 AA)

- [ ] `:focus-visible` styles using `var(--snad-color-focus-ring)`
- [ ] Touch targets ≥ 44×44px
- [ ] Not relying on color alone
- [ ] Form inputs have `<label>` elements
- [ ] Error states use `aria-invalid` and `aria-describedby`
- [ ] Modals use `role="dialog"` and `aria-modal="true"`
- [ ] `prefers-reduced-motion` respected

### 2.7 Performance

- [ ] No heavy libraries loaded on auth route
- [ ] No business modules loaded before authentication
- [ ] Route-based code splitting used
- [ ] `python3 scripts/ci/check-performance-budget.py` PASSES
- [ ] No N+1 queries in auth/workspace bootstrap path

### 2.8 Security

- [ ] No tokens/passwords in localStorage, logs, or error messages
- [ ] HttpOnly cookies for refresh tokens
- [ ] CSRF protection maintained
- [ ] Tenant isolation verified
- [ ] Rate limiting not bypassed

### 2.9 Testing

- [ ] Unit tests for new components
- [ ] All existing tests pass (`npm test`)
- [ ] No `continue-on-error` or `allow-failure` on critical checks
- [ ] Visual regression test matrix documented

### 2.10 CI/CD

- [ ] Build Next.js Web PASSES
- [ ] Lint PASSES
- [ ] SDS Compliance Check PASSES
- [ ] Logo Governance Check PASSES
- [ ] Brand Name Governance Check PASSES
- [ ] Performance Budget Check PASSES
- [ ] provenance PASSES

---

## 3. Final Status Declaration

Work is considered **CLOSED** only when ALL of the following are PASS:

```text
Implementation:           PASS
Integration:              PASS
Authentication:           PASS
Workspace Bootstrap:      PASS
Visual Identity:          PASS
Logo Governance:          PASS
RTL/LTR:                  PASS
Responsive Design:        PASS
Accessibility:            PASS
Security:                 PASS
Performance:              PASS
Automated Tests:          PASS
Visual Regression:        PASS
Production Build:         PASS
CI/CD:                    PASS
Documentation:            PASS
Repository Integration:   PASS
Post-Merge Verification:  PASS
```

**Final Status:** `CLOSED`
**Release Decision:** `GO`
**Brand Status:** `GOVERNED`
**Production Status:** `VERIFIED`

If ANY item is not PASS:
```text
FINAL STATUS:     OPEN
RELEASE DECISION: NO-GO
```

---

## 4. Change Log

| Date       | Change                                | Author             |
|------------|---------------------------------------|--------------------|
| 2026-07-07 | Initial Definition of Done created    | SANAD Platform (Z) |
