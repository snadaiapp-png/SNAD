# CRM-029 IMPLEMENTATION PLAN

## Date: 2026-07-31
## Ticket: CRM-029 — Reference Issue #189 in workflows and docs
## Status: AUTHORIZED TO IMPLEMENT

---

## Objective

Reference Issue #189 (CI-PLATFORM-01 — Restore GitHub Actions execution) in
at least one workflow and in the baseline documentation, and implement a
drift check to prevent silent regression.

---

## Implementation Tasks

### Task 1: Add Issue #189 reference to workflow

**File:** `.github/workflows/crm-deployment-readiness.yml`

**Change:** Add `run-name` or step summary reference to Issue #189.

**Evidence:** The workflow `run-name` or a step `run` summary must mention
`#189` or `Issue #189`.

### Task 2: Add Issue #189 reference to baseline doc

**File:** `docs/crm/CRM-CURRENT-BASELINE.md`

**Change:** Add a section or note referencing Issue #189 and its status.

**Evidence:** `grep -n "#189" docs/crm/CRM-CURRENT-BASELINE.md` returns a match.

### Task 3: Add Issue #189 drift check to governance script

**File:** `scripts/crm/governance-drift-check.sh`

**Change:** Add a check that fails if a commit message references `#189` but
no workflow file contains a reference to `#189`.

**Evidence:** The drift check script includes a new validation rule for
Issue #189 references.

### Task 4: Update roadmap status

**File:** `docs/crm/CRM-ENTERPRISE-EXECUTION-ROADMAP.md`

**Change:** Mark CRM-029 status as `DONE` with closing commit SHA.

**Evidence:** `grep "CRM-029" docs/crm/CRM-ENTERPRISE-EXECUTION-ROADMAP.md`
shows `Status: DONE`.

---

## Estimated Duration

| Task | Estimate |
|------|----------|
| Task 1 | 5 min |
| Task 2 | 5 min |
| Task 3 | 15 min |
| Task 4 | 5 min |
| **Total** | **30 min** |

---

## Acceptance Criteria Verification

| # | Criterion | Task | Verification |
|---|-----------|------|--------------|
| 1 | Issue #189 referenced in workflow `run-name` or step summary | Task 1 | `grep "#189" .github/workflows/*.yml` |
| 2 | Issue #189 referenced in `CRM-CURRENT-BASELINE.md` | Task 2 | `grep "#189" docs/crm/CRM-CURRENT-BASELINE.md` |
| 3 | Drift check fails if #189 in commit but not in workflow | Task 3 | Run `governance-drift-check.sh` |

---

## Dependencies

| Dependency | Status |
|------------|--------|
| CRM-001 (Baseline reconciliation) | ✅ DONE |
| Issue #189 (CI-PLATFORM-01) | OPEN — reference only, no code change |

---

## Risk Assessment

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| Drift check too strict | Low | Medium | Test with sample commits |
| Workflow change breaks CI | Low | Low | YAML lint validation |

---

## Plan Authorization

✅ **CRM-029 IMPLEMENTATION PLAN APPROVED**

**Authorized by:** ZCode Agent
**Date:** 2026-07-31
**Next step:** Create feature branch and implement
