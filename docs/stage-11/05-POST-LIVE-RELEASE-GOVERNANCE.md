# Stage 11 — Post-Live Release Governance

**Date**: 2026-07-08
**Issue**: #370

---

## Governing Principle

All post-production changes must follow this governance framework. The
Gate 8F closure decision is NOT reopened by Stage 11 work. The production
release decision remains:

```
Gate 8F: CLOSED BY GOVERNANCE WAIVER
Reference: SANAD-ST08-GOV-AMENDMENT-002
Final Platform Release: GO
```

---

## Post-Live Change Process

### 1. Create Independent PR

Every post-live change requires an independent PR with:
- Clear title prefixed with scope (e.g., `fix(stage11):`, `feat(stage11):`)
- Description of production impact
- Rollback note
- Reference to Issue #370 (or child Issue)

### 2. Describe Production Impact

PR description must include:

```
## Production Impact
- Affected routes: (list or "none")
- User-facing changes: (yes/no, description)
- Breaking changes: (yes/no)
- Rollback plan: (steps)
```

### 3. CI Checks Must Pass

Required checks (enforced by branch protection):
- Build Next.js Web: PASS
- provenance: PASS

Additional checks that should pass:
- Maven Test Suite (if backend changes)
- Playwright E2E & Visual Regression (if frontend changes)
- Secret Scan
- Workflow Security Policy

### 4. Vercel Preview Verification

Before merge:
- Vercel Preview deployment must reach READY state
- Preview URL must return HTTP 200
- Brand identity must be present (SNAD + سند)

### 5. Merge and Production Deploy

After merge to main:
- Vercel auto-deploys to production
- Verify production deployment reaches READY state
- Verify production commit SHA matches merge SHA
- Run production smoke test (all 6 routes)

### 6. Document SHA

Record in the PR or Issue:
```
Merge SHA: <actual-sha>
Production Deployment ID: <actual-id>
Production URL: https://snad-app.vercel.app/
Vercel State: success
```

---

## What Stage 11 Changes Must NOT Do

```
- Reopen Gate 8F
- Change Final Platform Release from GO to NO-GO
- Claim closure based on 5 independent GitHub accounts
- Merge old NO-GO language without governance reconciliation
- Mix Stage 11 work with the previous release closure
```

---

## Governance Amendment Process

If a change requires modifying the governance basis:

1. Create a formal amendment document (e.g., SANAD-ST08-GOV-AMENDMENT-003)
2. Describe what is being amended and why
3. Owner approval required (snadaiapp-png)
4. Independent reviewer approval recommended
5. Document in docs/stage-08/governance/
6. Reference in all subsequent PRs and Issues

The current governance basis remains SANAD-ST08-GOV-AMENDMENT-002.

---

## Release Governance Checklist (Per Post-Live PR)

```
[ ] PR created with clear scope
[ ] Production impact described
[ ] CI required checks pass (Build Next.js Web + provenance)
[ ] Vercel Preview reaches READY
[ ] Preview smoke test passes (HTTP 200, brand present)
[ ] Merge to main
[ ] Vercel Production deployment reaches READY
[ ] Production commit SHA matches merge SHA
[ ] Production smoke test passes (all 6 routes)
[ ] Rollback plan documented
[ ] Gate 8F closure basis preserved (no reopening)
```
