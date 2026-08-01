# CRM-034 Implementation Plan

| Field | Value |
|-------|-------|
| Ticket | CRM-034 — Accessibility audit for CRM Command Center |
| Date | 2026-08-02 |
| Owner | Frontend squad |
| Status | BLOCKED — awaiting repository synchronization |

---

## 1. Objective

Add an automated axe-core accessibility audit to the Playwright CI pipeline
that validates the CRM Command Center at `/crm` with zero Critical or Serious
violations.

---

## 2. Prerequisites

| # | Prerequisite | Status |
|---|--------------|--------|
| 1 | Repository synchronized (local == origin) | ❌ BLOCKED |
| 2 | Working tree clean | ❌ BLOCKED |
| 3 | CRM-017 DONE | ✅ READY |
| 4 | CRM-020 DONE | ✅ READY |
| 5 | Playwright CI workflow exists | ✅ READY |

---

## 3. Implementation Steps

### Phase 1: Setup (estimated: 30 min)

| # | Task | File |
|---|------|------|
| 1 | Add `@axe-core/playwright` dependency | `apps/web/package.json` |
| 2 | Run `npm install` to update lock file | `apps/web/package-lock.json` |

### Phase 2: Create Accessibility Spec (estimated: 60 min)

| # | Task | File |
|---|------|------|
| 3 | Create accessibility test spec | `apps/web/e2e/crm-accessibility.spec.ts` |
| 4 | Implement axe-core integration | Same file |
| 5 | Add evidence collection logic | Same file |

### Phase 3: CI Integration (estimated: 30 min)

| # | Task | File |
|---|------|------|
| 6 | Update Playwright CI workflow | `.github/workflows/playwright-ci.yml` |
| 7 | Add accessibility test step | Same file |

### Phase 4: Validation (estimated: 60 min)

| # | Task | File |
|---|------|------|
| 8 | Run accessibility tests locally | — |
| 9 | Fix any Critical/Serious violations | `apps/web/app/crm/**/*.tsx` |
| 10 | Commit audit evidence | `evidence/crm-axe-audit.json` |

---

## 4. File Changes

| File | Action | Description |
|------|--------|-------------|
| `apps/web/package.json` | MODIFY | Add `@axe-core/playwright` dependency |
| `apps/web/package-lock.json` | MODIFY | Updated lock file |
| `apps/web/e2e/crm-accessibility.spec.ts` | CREATE | Accessibility test spec |
| `.github/workflows/playwright-ci.yml` | MODIFY | Add accessibility test step |
| `evidence/crm-axe-audit.json` | CREATE | Audit evidence (auto-generated) |

---

## 5. Acceptance Criteria

| # | Criterion | Verification |
|---|-----------|--------------|
| 1 | axe-core audit runs in `playwright-ci.yml` | CI workflow includes accessibility step |
| 2 | Zero Critical violations | Evidence shows 0 Critical |
| 3 | Zero Serious violations | Evidence shows 0 Serious |
| 4 | Audit evidence committed | `evidence/crm-axe-audit.json` exists |

---

## 6. Estimated Effort

| Phase | Effort |
|-------|--------|
| Setup | 30 min |
| Create spec | 60 min |
| CI integration | 30 min |
| Validation | 60 min |
| **Total** | **3 hours** |

---

## 7. Risk Mitigation

| Risk | Mitigation |
|------|------------|
| axe-core reports violations | Fix violations before merge |
| Playwright CI fails | Test locally first |
| Evidence file missing | Auto-generate in test |

---

## 8. Conclusion

CRM-034 is a straightforward accessibility audit implementation. The main
blocker is repository synchronization, not technical complexity.
