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

---

## 5. Cross-Reference

> Governance cross-reference added 2026-07-30. This section is informational
> only and appends to (does not modify) the CRM-022 record. It is recorded on
> the CRM-022 branch only and does **not** alter any frozen `crm-v2.0.0`
> release artifact (Release Notes / Release Certificate remain sealed per
> `docs/crm/release/CRM-V2.0.0-FREEZE.md` §2.1/§3.1).

CRM-022 implementation has completed successfully.

An independent Maven Test Suite failure is tracked separately under **Issue #822**.

Independent investigation classified the failure as:

* Pre-existing Failure
* Regression Status: NO REGRESSION

No reproduced evidence currently attributes the failure to CRM-022.

CRM-022 shall remain closed unless future verified evidence demonstrates direct causation.

### Evidence anchors

| # | Finding | Evidence |
|---|---------|----------|
| 1 | Failure reproduced on `main` before CRM-022 branch | `main` CI `failure` at `9534a4bf` (2026-07-29 22:40 UTC) and `4480e107` (2026-07-30 00:12 UTC); CRM-022 branch created ~2026-07-30 10:06 UTC |
| 2 | CRM-022 modified only CI workflow | Commit `9b1c75f6` touched only `.github/workflows/ci.yml` |
| 3 | Maven `test` job byte-identical | `git show --numstat` → `89 0` (`+89/−0`); zero lines changed in the `test` block |
| 4 | CRM-specific CI passed | Run `30533427707`, job `CRM Integration Tests` → `completed/success` |
| 5 | No causal evidence | Adding a parallel job removes no lines; CRM subset green corroborates |

### Linked items

- Pull Request: https://github.com/snadaiapp-png/SNAD/pull/821
- Defect issue: https://github.com/snadaiapp-png/SNAD/issues/822
- Regression classification comment: https://github.com/snadaiapp-png/SNAD/issues/822#issuecomment-5130236800
