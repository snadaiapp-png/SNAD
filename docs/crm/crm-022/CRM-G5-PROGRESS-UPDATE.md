# CRM-G5 — Progress Update (CRM-022 delivered)

| Field | Value |
|-------|-------|
| Milestone | CRM-G5 — Tasks, transfers, employees, and assignments |
| Date | 2026-07-30 |
| Updated by | CRM-022 execution |

## 1. G5 work-item status

| Prompt | Title | Status | Notes |
|--------|-------|--------|-------|
| CRM-021 | Wire tasks tab | 🔴 NOT_STARTED | Unchanged; not in scope of CRM-022. |
| **CRM-022** | **Add CRM CI job** | 🟡 **IMPLEMENTED (pending CI green + required-check registration)** | `crm` job added to `ci.yml`; awaits PR CI result + branch-protection setting. |
| CRM-023 | Wire transfers/employees tabs | 🔴 BLOCKED | Depends on CRM-021 (not CRM-022). |

## 2. CRM-022 acceptance — completion checklist

- [x] `ci.yml` contains named `crm` job
- [x] `crm` runs CRM integration test classes (package-scoped, 16 classes)
- [x] Job fails on any CRM test failure
- [ ] `crm` registered as **required** status check on `main` ← **governance follow-up (repo admin)**
- [ ] CI green on the merged change

## 3. Definition of done (CRM-022) — not yet fully met

CRM-022 reaches DONE when CI is green **and** the `crm` check is added to
branch protection. The code deliverable is complete; the two remaining items
are operational/governance actions outside a workflow-file change.

## 4. Dependency note

CRM-022's only official dependency is CRM-001 (DONE). It does **not** depend on
CRM-021, and it does **not** unblock CRM-021/023/025/026 (those depend on
CRM-021). CRM-022 is a CI/quality enablement item.
