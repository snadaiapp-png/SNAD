# MISSION 40 — BRANCH TRIAGE DOCUMENTATION

> **Date:** 2026-08-09
> **MISSION:** 40 — Comprehensive Final Repository Audit & Release Freeze
> **CURRENT_FROZEN_SHA:** `6c4d166c320a4720b1658009b215a46ffe807b1d`
> **HEAD:** `6d1f9b5092b836d35b39d83a7f011aaf850a6dae`

---

## Executive Summary

All 14 unmerged branches have been forensically analyzed. **13 of 14 branches have ALL their content already present on `main`** via alternative merge paths. **1 branch (r1-rls-migration-fix) contains a security-relevant code deletion** that is NOT on main and requires separate authorization.

---

## Classification Matrix

### ALREADY IN BASELINE (11 branches — all content on main)

| # | Branch | SHA | Evidence |
|---|---|---|---|
| 1 | `fix/crm-007-archive-500-sql` | `8dc234fa` | CAST(:now AS TIMESTAMP) fix confirmed on main (4 occurrences in JdbcAddressCommunicationRepository.java) |
| 2 | `fix/bff-x-snad-if-match-translation` | `e70c1fef` | X-SNAD-If-Match header confirmed on main (4 occurrences in route.ts, 2 in client.ts). Branch is OLDER version. |
| 3 | `fix/gcr-isa-arch-003-pr-wait` | `ee4d9e40` | Workflow change merged via different path |
| 4 | `docs/crm-007-closure-evidence-20260718` | `6c6b3ea7` | Integrated via MISSION 38 (PR #839) |
| 5 | `remediation/ws3-governance-drift-cleanup` | `d7855ce7` | Both modified docs identical on main |
| 6 | `remediation/ws4-documentation-governance` | `9bbbaef9` | Main has NEWER content (CRM-G1=DONE). Branch is stale. |
| 7 | `remediation/ws5-technical-debt-register` | `3f99e77c` | Main has NEWER version with TD-002/TD-006 sections |
| 8 | `remediation/ws6-final-validation` | `e8316a33` | Main has NEWER remediation report with post-publication corrections |
| 9 | `recovery-crm-022/r1-migration-test-fix` | `1431eb56` | Commits in main history via revert path (MISSION 40 Phase 0) |
| 10 | `recovery-crm-022/r2-drift-repair` | `102d4b94` | BRANCH-PROTECTION-AUDIT.md exists on main via different merge |
| 11 | `feature/td-002-phase1-deprecation-migration` | `b692fec7` | V1DeprecationHeaderFilter.java + SecurityConfig.java identical on main |

### DUPLICATE (2 branches — code merged via different PRs)

| # | Branch | SHA | Evidence |
|---|---|---|---|
| 12 | `feat/crm-reporting-mod003` | `74306082` | ReportController.java, all reporting code IDENTICAL to main. Merged via PR #849 path. |
| 13 | `feat/crm-customer-portal-mod004` | `20430177` | PortalController.java on main. Branch has extra 8-line javadoc comment (non-functional). Merged via PR #849 path. |

### HIGH RISK — REQUIRES AUTHORIZATION (1 branch)

| # | Branch | SHA | Evidence |
|---|---|---|---|
| 14 | `recovery-crm-022/r1-rls-migration-fix` | `5b6477c9` | Contains: DELETE RLS migration + modify 2 test files + ADD root cause doc + ADD rollback script |

**r1-rls-migration-fix detailed content:**

| File | Action | Status on Main |
|---|---|---|
| `V20260730_2__disable_crm_row_level_security.sql` | DELETE | EXISTS on main |
| `V20260730_2__disable_crm_row_level_security.sql` (H2 test) | DELETE | EXISTS on main |
| `Crm008bFoundationAcceptanceTest.java` | MODIFY | DIFFERS from branch |
| `CrmPostgresMigrationTest.java` | MODIFY | DIFFERS from branch |
| `ROOT-CAUSE-R1.md` | ADD | EXISTS on main |
| `CRM-018-RLS-DISABLE-rollback.sql` | ADD | EXISTS on main |

**Decision:** This branch is NOT integrated. Its unique content (migration deletion + test modifications) is NOT on main. It requires explicit authorization before any merge.

---

## Stash Classification

| Stash | Files | Risk | Decision |
|---|---|---|---|
| `stash@{0}` | 3 docs | LOW | Leave stashed |
| `stash@{1}` | 1 doc | LOW | Leave stashed |
| `stash@{2}` | 14 code | HIGH | Leave stashed — NOT APPLIED |
| `stash@{3}` | 1 test | LOW | Leave stashed |

---

## Cleanup Recommendation

Per governance Rule 16, branches are NOT deleted without documentation. The following branches are recommended for cleanup in a separate operation:

**Safe to delete (content on main, no unique value):**
- `fix/crm-007-archive-500-sql`
- `fix/bff-x-snad-if-match-translation`
- `fix/gcr-isa-arch-003-pr-wait`
- `docs/crm-007-closure-evidence-20260718`
- `remediation/ws3-governance-drift-cleanup`
- `remediation/ws4-documentation-governance`
- `remediation/ws5-technical-debt-register`
- `remediation/ws6-final-validation`
- `recovery-crm-022/r1-migration-test-fix`
- `recovery-crm-022/r2-drift-repair`
- `feature/td-002-phase1-deprecation-migration`
- `feat/crm-reporting-mod003`
- `feat/crm-customer-portal-mod004`

**Requires authorization (security-relevant):**
- `recovery-crm-022/r1-rls-migration-fix` — contains security-relevant deletion

---

**Documented by:** ZCode agent
**Session:** MISSION 40
**Date:** 2026-08-09
