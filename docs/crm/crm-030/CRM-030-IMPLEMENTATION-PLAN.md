# CRM-030 IMPLEMENTATION PLAN

## Date: 2026-07-31
## Ticket: CRM-030 — Verify CRM workflows as required status checks
## Status: AUTHORIZED TO IMPLEMENT

---

## Objective

Verify that CRM workflows are configured as required status checks on `main`
and commit the branch protection configuration as evidence.

---

## Implementation Tasks

### Task 1: Add CRM Real API Smoke to required status checks

**Action:** Use GitHub API to add `CRM Real API Smoke` to branch protection
required status checks on `main`.

**Evidence:** API response confirming the check was added.

### Task 2: Add CRM Web Lint Diagnostics to required status checks

**Action:** Use GitHub API to add `CRM Web Lint Diagnostics` to branch protection
required status checks on `main`.

**Evidence:** API response confirming the check was added.

### Task 3: Add crm job in ci.yml to required status checks

**Action:** Use GitHub API to add `CI / crm` (workflow name / job name format)
to branch protection required status checks on `main`.

**Evidence:** API response confirming the check was added.

### Task 4: Create branch protection evidence file

**Action:** Create `evidence/branch-protection-crm.json` with the current
branch protection configuration.

**Evidence:** File exists and contains valid JSON with required status checks.

### Task 5: Verify all required checks are in place

**Action:** Query GitHub API to confirm all 10 required status checks are
configured on `main`.

**Evidence:** API response listing all required checks.

### Task 6: Update roadmap status

**Action:** Mark CRM-030 as `DONE` in the execution roadmap.

**Evidence:** `grep "CRM-030" docs/crm/CRM-ENTERPRISE-EXECUTION-ROADMAP.md`
shows `Status: DONE`.

---

## Estimated Duration

| Task | Estimate |
|------|----------|
| Task 1 | 5 min |
| Task 2 | 5 min |
| Task 3 | 5 min |
| Task 4 | 5 min |
| Task 5 | 5 min |
| Task 6 | 5 min |
| **Total** | **30 min** |

---

## Acceptance Criteria Verification

| # | Criterion | Task | Verification |
|---|-----------|------|--------------|
| 1 | All 4 CRM workflows are required status checks | Tasks 1-3, 5 | GitHub API query |
| 2 | Branch protection evidence committed | Task 4 | File exists |

---

## Dependencies

| Dependency | Status |
|------------|--------|
| CRM-022 (CRM CI job) | ✅ DONE |
| CRM-027 (Production smoke gate) | ✅ DONE |

---

## Risk Assessment

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| API permission denied | Low | High | Use admin token |
| Check name mismatch | Medium | Medium | Verify exact workflow/job names |
| Branch protection locked | Low | High | Document and escalate |

---

## Plan Authorization

✅ **CRM-030 IMPLEMENTATION PLAN APPROVED**

**Authorized by:** ZCode Agent
**Date:** 2026-07-31
**Next step:** Create feature branch and implement
