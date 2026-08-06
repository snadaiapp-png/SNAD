# ROOT-CAUSE ANALYSIS

**Date:** 2026-08-03
**Repository:** snadaiapp-png/SNAD
**Issue:** G1 and G2 show Status = معتمدة but Progress = 0%

---

## Executive Summary

G1 and G2 had `status: "APPROVED"` in `EXECUTION_GROUPS` but **zero tasks** defined in `CRM_TASKS`. The progress calculation function `getGroupProgress()` derives progress from task completion — when there are no tasks, progress is 0% regardless of group status.

**Root Cause:** Missing task definitions for completed groups.
**Fix:** Generated 12 tasks for G1 and 10 tasks for G2 from certified execution evidence.

---

## Detailed Root Cause

### Data Flow (Before Fix)

```
EXECUTION_GROUPS (source of status)
    ↓
    G1: status = "APPROVED"
    G2: status = "APPROVED"
    
CRM_TASKS (source of progress)
    ↓
    G0: 15 tasks (all DONE) → progress = 100%
    G1: 0 tasks → progress = 0%
    G2: 0 tasks → progress = 0%

getGroupProgress("G1")
    ↓
    tasks = CRM_TASKS.filter(t => t.groupCode === "G1")  // → []
    total = 0
    percentage = 0  // (total > 0) is false → 0
```

### The Disconnect

| Concept | Source | Value |
|---------|--------|-------|
| Status | `EXECUTION_GROUPS[i].status` | `"APPROVED"` |
| Progress | `getGroupProgress(code).percentage` | `0%` |

Status and progress had **different authoritative sources**:
- Status came from `EXECUTION_GROUPS` (manually set)
- Progress came from `CRM_TASKS` (calculated from task completion)

When tasks were not defined for a group, progress defaulted to 0% — even if the group was certified.

### Why Tasks Were Missing

G1 and G2 were completed and certified (tag `CRM-G1G2-CERTIFIED` exists, final certification score 108/110), but their task definitions were never added to `CRM_TASKS`. The original `CRM_TASKS` array only contained G0 tasks (15 tasks for the execution control dashboard).

---

## Evidence Sources Used for Task Generation

| Evidence | Location | Content |
|----------|----------|---------|
| G1-G2-FINAL-CERTIFICATION.md | Repository root | G1: 8/8 criteria. G2: 7/7 criteria. Score: 108/110 |
| CRM-G1-FINAL-STAGE-REPORT.md | docs/crm/stage-reports/ | 12 deliverables, 100% complete |
| CRM-G2-STAGE-REPORT.md | docs/crm/stage-reports/ | Keyset pagination, i18n closure |
| V20260716_1__create_crm_tasks.sql | db/migration/ | crm_tasks table creation |
| V20260717_6__create_crm_g1_extension_tables.sql | db/migration/ | 6 G1 tables, 20 indexes |
| V20260718_1__reconcile_crm_g1_after_baseline_gap.sql | db/vendor/ | Reconciliation migration |
| CRM-G1-FINAL-PRODUCTION-CLOSURE.md | docs/crm/evidence/ | Production closure proof |
| crm-i18n.tsx | apps/web/app/crm/ | 304 translation keys, RTL/LTR |

---

## Generated Tasks

### G1: Database & Multi-Tenant Foundation (12 tasks)

| Task | Description | Evidence |
|------|-------------|----------|
| G1-T01 | Create 8 CRM extension tables | V20260716_1, V20260716_2, V20260717_6 |
| G1-T02 | Create 26 performance indexes | V20260717_6, V20260718_1 |
| G1-T03 | Implement tenant isolation | CrmG1TenantIsolationPostgresTest |
| G1-T04 | Add CHECK and UNIQUE constraints | V20260717_6 |
| G1-T05 | Create Flyway migrations | 4 migration files + reconciliation |
| G1-T06 | Write Testcontainers tests | 4 files, 22 methods |
| G1-T07 | Cross-tenant isolation test | PostgreSQL write rejection |
| G1-T08 | Create CI schema gate | crm-g1-schema-isolation.yml |
| G1-T09 | Create production closure gate | crm-g1-production-closure.yml |
| G1-T10 | Document production closure evidence | CRM-G1-FINAL-PRODUCTION-CLOSURE.md |
| G1-T11 | Create 8 ownership controllers | 41 ownership endpoints |
| G1-T12 | Create G1 stage report | CRM-G1-FINAL-STAGE-REPORT.md |

### G2: i18n, RTL/LTR & UI Shell (10 tasks)

| Task | Description | Evidence |
|------|-------------|----------|
| G2-T01 | Create CrmI18nProvider | crm-i18n.tsx |
| G2-T02 | Create useCrmI18n hook | crm-i18n.tsx line 352 |
| G2-T03 | Create 304 bilingual translation keys | crm-i18n.tsx |
| G2-T04 | Implement RTL/LTR with localStorage | crm-i18n.tsx line 348 |
| G2-T05 | Apply brand tokens | snad-tokens.css, theme.css |
| G2-T06 | Integrate useCrmI18n in 16 files | 16 consumer files |
| G2-T07 | Write Vitest tests | 4 test files |
| G2-T08 | Write Playwright RTL test | 1 Playwright test |
| G2-T09 | Implement CRM-003R keyset pagination | 9 CRM v2 operations |
| G2-T10 | Create G2 stage report | CRM-G2-STAGE-REPORT.md |

---

## Result

| Metric | Before | After |
|--------|--------|-------|
| G0 tasks | 15 | 15 |
| G1 tasks | 0 | 12 |
| G2 tasks | 0 | 10 |
| G0 progress | 100% | 100% |
| G1 progress | **0%** | **100%** |
| G2 progress | **0%** | **100%** |
| Total tasks | 15 | 37 |
| Status inconsistency | YES | NO |

---

## Prevention

Progress is now **automatically calculated** from task completion. No manual percentages. No hardcoded values. Single source of truth enforced:

- Status = from `EXECUTION_GROUPS[i].status`
- Progress = from `getGroupProgress(code).percentage` (derived from `CRM_TASKS`)
- Both are consistent because tasks are defined for every completed group
