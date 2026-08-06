# G5 Readiness Report — CRM v2.0.0 Baseline

| Field | Value |
|-------|-------|
| Report Date | 2026-07-30 |
| Baseline Version | crm-v2.0.0 |
| Authority | Release Baseline Authority |

---

## 1. G5 Milestone Overview

| Property | Value |
|----------|-------|
| Milestone | **CRM-G5** — Tasks, transfers, employees, and assignments |
| Status | ⏳ NOT_STARTED |
| Gate evidence | `docs/crm/stage-reports/CRM-G5-STAGE-REPORT.md` |
| Prompts | CRM-021, CRM-022, CRM-023 |

---

## 2. Work Item Readiness

### CRM-021 — Wire tasks tab

| Property | Value |
|----------|-------|
| Status | 🔴 **READY TO START** |
| Dependencies | CRM-008, CRM-017 |
| Owner | Frontend squad |

**Dependency Verification:**

| Dependency | Required | Status | Evidence |
|------------|----------|--------|----------|
| CRM-008 — G1 extension tables on main | Code on `main` | ✅ SATISFIED | `V20260716_1__create_crm_tasks.sql` on `main` |
| CRM-017 — Customer-360 wired | DONE | ✅ SATISFIED | `crm-017` implemented in v2.0.0 |

**Verdict:** ✅ **CRM-021 IS READY TO START** — all prerequisites satisfied.

### CRM-022 — Add CRM CI job

| Property | Value |
|----------|-------|
| Status | 🔴 **READY TO START** |
| Dependencies | CRM-001 |
| Owner | Platform CI squad |

**Dependency Verification:**

| Dependency | Required | Status | Evidence |
|------------|----------|--------|----------|
| CRM-001 — Baseline reconciled | DONE | ✅ SATISFIED | CRM-001 status = `DONE` in roadmap |

**Verdict:** ✅ **CRM-022 IS READY TO START** — all prerequisites satisfied.

### CRM-023 — Wire transfers and employees tabs

| Property | Value |
|----------|-------|
| Status | 🔴 **BLOCKED** |
| Dependencies | CRM-021 |
| Owner | Frontend squad |

**Dependency Verification:**

| Dependency | Required | Status | Evidence |
|------------|----------|--------|----------|
| CRM-021 — Tasks tab wired | NOT_STARTED | ❌ BLOCKED | CRM-021 must complete first |

**Verdict:** ❌ **CRM-023 IS BLOCKED** — requires CRM-021 completion.

### CRM-025 — Wire reports tab (G6)

| Property | Value |
|----------|-------|
| Status | 🔴 **BLOCKED** |
| Dependencies | CRM-019, CRM-021 |
| Owner | Frontend squad |

**Dependency Verification:**

| Dependency | Required | Status | Evidence |
|------------|----------|--------|----------|
| CRM-019 — Opportunities wired | DONE | ✅ SATISFIED | Part of v2.0.0 |
| CRM-021 — Tasks tab wired | NOT_STARTED | ❌ BLOCKED | CRM-021 must complete first |

**Verdict:** ❌ **CRM-025 IS BLOCKED** — requires CRM-021 completion.

### CRM-026 — Add CRM E2E test (G6)

| Property | Value |
|----------|-------|
| Status | 🔴 **BLOCKED** |
| Dependencies | CRM-017, CRM-019, CRM-021 |
| Owner | Quality squad |

**Dependency Verification:**

| Dependency | Required | Status | Evidence |
|------------|----------|--------|----------|
| CRM-017 — Customer-360 wired | DONE | ✅ SATISFIED | Part of v2.0.0 |
| CRM-019 — Opportunities wired | DONE | ✅ SATISFIED | Part of v2.0.0 |
| CRM-021 — Tasks tab wired | NOT_STARTED | ❌ BLOCKED | CRM-021 must complete first |

**Verdict:** ❌ **CRM-026 IS BLOCKED** — requires CRM-021 completion.

---

## 3. Dependency Map

```
CRM-021 ────┬─── CRM-023
            ├─── CRM-025
            └─── CRM-026

CRM-022 ──── (independent)

CRM-023 ──── CRM-025 ──── ...
CRM-026
```

The critical path for G5 is:

```
START ──▶ CRM-021 (tasks tab) ──▶ CRM-023 (transfers/employees)
                                    │
                                    └──▶ CRM-025 (reports) — G6
                                    └──▶ CRM-026 (E2E test) — G6

CRM-022 (CI job) ──▶ can run in parallel
```

---

## 4. Readiness Summary

| Prompt | Title | Deps Satisfied | Ready? | Recommended Order |
|--------|-------|---------------|--------|-------------------|
| CRM-021 | Wire tasks tab | ✅ All | **READY** | **1st** |
| CRM-022 | Add CRM CI job | ✅ All | **READY** | **Parallel** |
| CRM-023 | Wire transfers/employees tabs | ❌ Blocked by CRM-021 | **BLOCKED** | After CRM-021 |
| CRM-025 | Wire reports tab | ❌ Blocked by CRM-021 | **BLOCKED** | G6 (after CRM-021) |
| CRM-026 | Add CRM E2E test | ❌ Blocked by CRM-021 | **BLOCKED** | G6 (after CRM-021) |

---

## 5. Recommendation

**Begin G5 with CRM-021 (Wire tasks tab) as the first work item.**

- CRM-021 is the gateway item — it unblocks CRM-023, CRM-025, and CRM-026
- CRM-022 can be executed in parallel
- CRM-021 depends on CRM-008 (code on main) and CRM-017 (✅ DONE)
- All prerequisites for G5 entry are satisfied

---

## 6. G5 Entry Checklist

| Criterion | Status |
|-----------|--------|
| Production baseline frozen | ✅ v2.0.0 frozen |
| CRM-021 deps (CRM-008, CRM-017) satisfied | ✅ |
| CRM-022 deps (CRM-001) satisfied | ✅ |
| No open release blockers | ✅ |
| Working tree clean | ✅ |
| All v2.0.0 artifacts committed | ✅ |
| Technical debt documented | ✅ |

**Final Verdict: ✅ G5 READY — May begin with CRM-021**

---

*Report compiled 2026-07-30 by Release Baseline Authority*
