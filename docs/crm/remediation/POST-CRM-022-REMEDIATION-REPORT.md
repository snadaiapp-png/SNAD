# POST-CRM-022 REMEDIATION REPORT

| Field | Value |
|-------|-------|
| Date | 2026-07-30 (originally); corrected 2026-07-31 under RECOVERY-CRM-022 |
| Author | ZCode Agent |
| Scope | Post-CRM-022 remediation (6 workstreams) + RECOVERY-CRM-022 corrections |
| PRs Created | #825, #826, #827, #828, #829, #830 (this report) |
| Status | ** superseded by RECOVERY-CRM-022 — see corrections below ** |

> **Correction notice (RECOVERY-CRM-022, 2026-07-31):** The original version
> of this report marked Workstreams 2 and 3 as ✅ COMPLETE and the
> Maven/drift risks as RESOLVED. Post-merge CI on `main` proved those claims
> were **unsupported**: the Maven migration test still failed (WS2 did not
> resolve it) and the governance drift check was still RED (WS3 did not clear
> it; this very report introduced new drift violations). The inaccurate
> "delivered / fully implemented" wording also tripped drift Rule 4.
> This corrected version removes the exaggeration. Supporting recovery PRs:
> R1 = #831 (migration constant), R2 = this file's correction.

---

## Executive Summary

Six workstreams were attempted to remediate the engineering and governance
issues from the CRM-022 forensic audit. After the PRs merged, post-merge
validation on `main` showed that **two workstreams did not achieve their
objectives** and one (WS6) introduced fresh governance drift. RECOVERY-CRM-022
was opened to return the repository to green; this report is corrected as
part of that recovery (R2).

| Workstream | Original claim | Verified outcome |
|------------|----------------|------------------|
| WS1 Branch protection | COMPLETE | Delivered as scoped ✅ |
| WS2 Maven stability | COMPLETE ("11 failures fixed") | **Not achieved** — `CrmPostgresMigrationTest` still RED on `main`; #826 changed the wrong version constant. Fixed by R1 (#831). |
| WS3 Governance drift | COMPLETE ("3 violations resolved") | **Not achieved** — drift check RED on `main`; 3 new violations in this report. Fixed by R2 (this correction). |
| WS4 Documentation | COMPLETE | Delivered as scoped ✅ |
| WS5 Tech-debt register | COMPLETE | Delivered as scoped ✅ |
| WS6 This report | COMPLETE | **Introduced drift** — corrected here (R2). |

---

## Work Completed

### Workstream 1: Branch Protection (PR #825)

| Item | Before | After |
|------|--------|-------|
| Required checks | `Build Next.js Web`, `provenance` | `Build Next.js Web`, `provenance`, `CRM Integration Tests` |
| CRM tests blocking | No | **Yes** |

**Status:** ✅ COMPLETE — Branch protection updated via GitHub API.
(Note: the required-checks set was later found too narrow; RECOVERY-CRM-022 R3
hardens it further — see BRANCH-PROTECTION-AUDIT.md.)

### Workstream 2: Docker/Maven Stability (PR #826)

| File | Change made by #826 | Verified effect |
|------|---------------------|-----------------|
| `Crm008bFoundationAcceptanceTest.java` | version constant → `20260730.2` | **Regression** — pointed at the wrong migration |
| `CrmPostgresMigrationTest.java` | version constant → `20260730.2` | **Regression** — same; 3 of 4 tests fail |
| `CrmRlsTenantIsolationPostgresTest.java` | added vendor migration path | OK |

**Status:** ❌ NOT ACHIEVED. The "11 test failures fixed" claim was unsupported.
The scoring-models seed lives at Flyway version `20260729.2`
(`V20260729_2__seed_default_scoring_models.sql`); version `20260730.2` is the
unrelated `disable crm row level security` migration. #826 moved the constant
onto the wrong migration. Corrected by RECOVERY-CRM-022 R1 (PR #831). See
`ROOT-CAUSE-R1.md`.

### Workstream 3: Governance Drift Cleanup (PR #827)

PR #827 edited two files to soften prior wording. Verified effect after merge:
the drift check was still RED on `main`, with 3 violations located in this
report file (WS6), not the files #827 touched.

**Status:** ❌ NOT ACHIEVED at the gate. Corrected by RECOVERY-CRM-022 R2
(this rewrite, which removes the same-line delivered-phrase + tab
co-occurrences that drift Rule 4 flags).

### Workstream 4: Documentation Governance (PR #828)

| File | Issue | Fix |
|------|-------|-----|
| `CRM-CURRENT-BASELINE.md` | CRM-008R status inconsistency | Reconciled Section 1 vs Section 5 |
| `CRM-ENTERPRISE-EXECUTION-ROADMAP.md` | Wrong status counts | Updated: 18 DONE, 5 closed milestones |
| `README.md` | Stale tab/test counts | Updated: 8 empty-state tabs, 80+ tests, 16+ classes |

**Status:** ✅ COMPLETE — 3 documentation issues addressed.

### Workstream 5: Technical Debt Register (PR #829)

| Category | Total | Resolved | Open |
|----------|-------|----------|------|
| Governance Debt | 3 | 3 | 0 |
| CI Debt | 2 | 2 | 0 |
| Documentation Debt | 4 | 3 | 1 |
| Workflow Debt | 1 | 0 | 1 |
| Legacy Suppressions | 1 | 0 | 1 |
| Build Debt | 1 | 0 | 1 |
| **Total** | **12** | **8** | **4** |

**Status:** ✅ COMPLETE — Register created. (Note: the "CI Debt: 2 resolved"
entry is inconsistent with WS2's verified failure and is reconciled under
RECOVERY-CRM-022; the migration-test debt is tracked as TD-CRM022-1.)

### Workstream 6: Final Validation (This Report)

**Status:** ❌ Originally introduced governance drift (3 Rule-4 violations from
its own "delivered / fully implemented" wording). Corrected in RECOVERY-CRM-022 R2.

---

## CI Status (corrected)

| Check | Verified status on `main` @ `61cf9a5b` | Required (at the time) |
|-------|------------------------------------------|------------------------|
| Build Next.js Web | ✅ GREEN | Yes |
| provenance | ✅ GREEN | Yes |
| CRM Integration Tests | ✅ GREEN | Yes |
| Maven Test Suite | ❌ RED (CrmPostgresMigrationTest) | No — governance gap |
| CRM Deployment Readiness | ❌ RED (drift violations) | No — governance gap |
| Post-Merge Verification | ❌ RED (cascaded) | No — governance gap |
| CRM G1 Schema Isolation | ❌ RED (same migration test) | No — governance gap |

**Note:** The original report stated these were "⏳ Pending (PR #826 will
trigger)" and asserted #826 "should resolve the Maven Test Suite failures."
That assertion was unsupported; the failures persisted.

---

## Governance Status (corrected)

| Check | Verified status |
|-------|-----------------|
| Governance drift violations | ❌ 3 violations on `main` @ `61cf9a5b` (in this report) — corrected by R2 |
| Branch protection enforced | ⚠️ Partial — only 3 contexts required; R3 hardens to 7 |
| Documentation accurate | ⚠️ This report over-claimed; corrected by R2 |

---

## Branch Protection Status (as of base SHA)

```json
{
  "strict": true,
  "contexts": ["Build Next.js Web", "provenance", "CRM Integration Tests"]
}
```

| Setting | Value |
|---------|-------|
| Required approvals | 0 |
| Enforce admins | false |
| Dismiss stale reviews | true |

This set was too narrow to prevent the red merges (the four RED workflows were
not required). RECOVERY-CRM-022 R3 adds `Maven Test Suite`, `CRM Deployment
Readiness`, `Post-Merge Verification`, and `CRM G1 Schema Isolation` to the
required contexts. See `BRANCH-PROTECTION-AUDIT.md`.

---

## Risk Assessment (corrected)

| Risk | Severity | Actual status at `61cf9a5b` | Recovery action |
|------|----------|------------------------------|-----------------|
| CRM tests not required | High | Partially open — required set too narrow | R3 (BRANCH-PROTECTION-AUDIT.md) |
| Maven Test Suite failures | High | **OPEN** — #826 did not fix | R1 (#831) |
| Governance drift | Medium | **OPEN** — this report introduced violations | R2 (this correction) |
| Stale documentation | Medium | Addressed by WS4 | — |
| Remaining debt items | Low | Tracked in register | Open |

---

## Remaining Debt

| ID | Description | Target |
|----|-------------|--------|
| TD-CRM022-1 | CrmPostgresMigrationTest version constant (fixed by R1) | RECOVERY-CRM-022 |
| TD-CRM022-2 | Report drift violations (fixed by R2) | RECOVERY-CRM-022 |
| TD-CRM022-3 | Branch-protection required checks too narrow (fixed by R3) | RECOVERY-CRM-022 |
| DOC-004 | Add 9 missing migrations to baseline inventory | CRM-023 |
| WF-001 | Audit and archive 29 unused CRM workflow files | CRM-023 |
| LS-001 | Refactor CRM components to remove ESLint suppression | CRM-023 |
| BD-001 | Add vendor migration path to 6 test files | CRM-023 |

---

## Success Criteria (corrected)

| Criterion | Verified status |
|-----------|-----------------|
| CRM required status check enforced | Partial (WS1); hardened by R3 |
| Maven Test Suite stable | ❌ Not achieved by WS2; fixed by R1 |
| Docker-related CI failures resolved | ❌ Not achieved by WS2; fixed by R1 |
| Governance drift eliminated | ❌ Not achieved by WS3; fixed by R2 |
| Documentation validated | Addressed by WS4 |
| Technical debt recorded | ✅ WS5 |
| Full CI green | ❌ RED on `main` @ `61cf9a5b`; recovered by R1+R2+R3 |

---

## PR Summary

| PR | Title | Workstream | Files Changed |
|----|-------|------------|---------------|
| #825 | Branch Protection for CRM Integration Tests | WS1 | 1 (documentation) |
| #826 | Fix Maven Test Suite failures | WS2 | 3 (test files) — **did not achieve objective** |
| #827 | Resolve governance drift violations | WS3 | 2 (doc files) — **did not achieve objective** |
| #828 | Fix stale claims and inconsistent status | WS4 | 3 (doc files) |
| #829 | Create Technical Debt Register | WS5 | 1 (new file) |
| #830 | This report | WS6 | 1 (this file) |

Recovery PRs: #831 (R1).
