# Stage 15 — Automated Quality Gates

**Date**: 2026-07-08

---

## Current Quality Gates (Active)

### CI Required Checks (Branch Protection)

```
1. Build Next.js Web
   - Workflow: web-ci.yml / Build Next.js Web
   - Status: REQUIRED
   - Checks: Next.js production build succeeds

2. provenance
   - Workflow: stage07-provenance.yml
   - Status: REQUIRED
   - Checks: Artifact provenance verified
```

### CI Non-Required Checks (Active)

```
3. Post-Merge Main Verification
   - Workflow: post-merge-verification.yml
   - Triggers: push to main
   - Checks: 17 critical checks (lint, tsc, tests, build, smoke, secret scan, evidence)
   - Fail-closed: YES (no continue-on-error)

4. Production Smoke
   - Workflow: production-smoke.yml
   - Triggers: push to main
   - Checks: Production URL HTTP 200, brand identity

5. Playwright E2E & Visual Regression
   - Workflow: playwright-ci.yml
   - Triggers: PR + push to main
   - Checks: 58 tests (48 E2E + 10 visual regression)
   - Fail-closed: YES

6. CI (Maven Test Suite)
   - Workflow: ci.yml
   - Triggers: PR + push to main
   - Checks: Backend Maven verify with Docker/Testcontainers

7. Current Tree Secret Scan
   - Workflow: security-scan.yml
   - Triggers: PR
   - Checks: gitleaks + SNAD policy supplement scanner

8. Workflow Security Policy
   - Workflow: (inline in security-scan.yml)
   - Checks: No continue-on-error, no || true on critical commands

9. Design System Compliance
   - Script: check-design-system-compliance.py
   - Checks: No hardcoded colors outside theme.css

10. Logo Governance
    - Script: check-logo-governance.py
    - Checks: SnadLogo used, no direct SVG imports

11. Brand Name Governance
    - Script: check-brand-name-governance.py
    - Checks: SNAD/سند used, no SANAD

12. i18n Key Parity
    - Script: check_i18n_keys.py
    - Checks: ar.ts and en.ts have same keys (168/168)

13. Performance Budget
    - Script: check-performance-budget.py
    - Checks: Build output exists, performance budget met
```

## Quality Gate Enforcement

### Fail-Closed Policy

```
All quality gates are fail-closed:
  - No continue-on-error on critical steps
  - No || true on critical commands
  - No set +e on critical commands
  - Artifacts uploaded with if-no-files-found: error (where critical)

Verification: check_workflow_security.py scans all 47 workflow files
```

### Gate Hierarchy

```
1. Code quality (lint, tsc) → must pass before build
2. Build → must pass before tests
3. Unit tests → must pass before E2E
4. E2E tests → must pass before merge
5. Secret scan → must pass before merge
6. Security baseline → must pass before merge
7. Post-Merge Verification → must pass after merge (on exact SHA)
8. Production Smoke → must pass after production deploy
```

## Recommended Additional Gates (Stage 15+)

### 1. Lighthouse CI (Performance)

```
Status: RECOMMENDED
Checks: Performance, Accessibility, Best Practices, SEO scores
Threshold: Performance > 80, Accessibility > 90
Integration: Add to playwright-ci.yml or separate workflow
```

### 2. Bundle Size Check

```
Status: RECOMMENDED
Checks: JavaScript bundle size does not exceed budget
Threshold: < 200KB initial JS
Integration: Add to web-ci.yml
```

### 3. Dependency Vulnerability Scan

```
Status: RECOMMENDED
Checks: npm audit + Maven OWASP dependency-check
Threshold: 0 Critical, 0 High (unaccepted)
Integration: Add to CI workflow
```

### 4. License Compliance Scan

```
Status: RECOMMENDED
Checks: All dependencies have compatible licenses
Integration: Add to CI workflow
```

### 5. Database Migration Check

```
Status: RECOMMENDED (when database migrations added)
Checks: Migrations are reversible, no data loss
Integration: Add to CI workflow before Maven tests
```

## Quality Gate Summary

```
Active gates: 13 (2 required, 11 non-required but active)
Fail-closed: YES (all critical gates)
Coverage: Code quality, build, tests, E2E, visual regression, security, governance

Recommended additions: 5 (Lighthouse, bundle size, dependency scan, license scan, migration check)

Automated Quality Gates: ACTIVE
  → Current gates provide comprehensive coverage
  → Recommended gates to be added in Stage 15-16
```
