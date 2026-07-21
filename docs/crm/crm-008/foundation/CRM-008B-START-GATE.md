# CRM-008B — Foundation Start Gate

> **Gate:** CRM-008B Start Authorization  
> **Prepared:** 2026-07-21  
> **Status:** PREPARATION COMPLETE / IMPLEMENTATION BLOCKED  
> **Parent:** Issue #597

---

## 1. Purpose

Provide one authoritative decision record for moving CRM-008B from design closure into implementation without bypassing CRM-007 production closure.

---

## 2. Completed Prerequisites

```text
CRM_008A_DESIGN_CLOSURE: COMPLETED
CRM_008A_DESIGN_BASELINE: APPROVED
PRODUCT_DESIGN_SIGN_OFF: RECORDED
QA_DESIGN_SIGN_OFF: RECORDED
SECURITY_DESIGN_SIGN_OFF: RECORDED
IMPLEMENTATION_BACKLOG: COMPLETE
OWNERS_AND_ESTIMATES: ASSIGNED_BY_ROLE
MIGRATION_RUNBOOK: COMPLETE
TEST_AND_EVIDENCE_RUNBOOK: COMPLETE
AC_TRACEABILITY: COMPLETE
```

Authoritative design scope:

- 10 domain aggregates.
- 9 planned migrations.
- 14 new tables.
- 40 new indexes.
- 21 tenant/composite FKs.
- 17 capabilities and 2 roles.
- 38 API operations.
- 21 acceptance criteria.

---

## 3. Current CRM-007 Dependency State

Current repository evidence at preparation time:

```text
CURRENT_MAIN_SHA: 098a6124c4274a8de65188d1622b336e9d3a2fb4
CRM_G1: PASSED_ON_PRIOR_EXACT_RELEASES
PRODUCTION_BACKEND: RENDER
LEGACY_NGROK_PATH: RETIRED
ISSUE_563: OPEN
PR_567: CLOSED_WITHOUT_MERGE / SUPERSEDED
PR_647: OPEN
CRM_007_FINAL_ACCEPTANCE: NOT_YET_GREEN_ON_FINAL_EXACT_SHA
```

PR #647 addresses a proven PostgreSQL nullable-parameter defect in CRM-007 Address Create and repairs the explicit CRM-G1-to-CRM-007 closure chain. Its checks and exact-SHA production acceptance must complete before Issue #563 can close.

---

## 4. Gate B Mandatory Conditions

Every condition must be true on one current, exact and unchanged release state:

- [x] CRM-008A design closure completed.
- [x] Implementation backlog assigned and estimated.
- [x] Migration execution runbook completed.
- [x] Test/evidence runbook and acceptance traceability completed.
- [x] Obsolete PR #567 formally superseded and closed without merge.
- [ ] PR #647 or its authoritative replacement merged after all required checks pass.
- [ ] Exact immutable Render backend image deployed from the merge SHA.
- [ ] Vercel production and Render backend identities match the exact release SHA.
- [ ] Flyway required production versions are `SQL / true`.
- [ ] CRM-G1 succeeds on the exact release SHA.
- [ ] CRM-007 authenticated lifecycle and two-tenant acceptance succeed on the same SHA.
- [ ] Immutable CRM-G1 and CRM-007 evidence PRs are reviewed and safely resolved.
- [ ] No unexplained 5xx in the final verification window.
- [ ] Issue #563 closed with final evidence.
- [ ] Explicit Project Owner authorization issued in Issue #597.

---

## 5. Authorization Decision Rules

### AUTHORIZE

Gate B may be authorized only when:

```text
ISSUE_563: CLOSED_WITH_EVIDENCE
CRM_007_FINAL_ACCEPTANCE: PASS
FINAL_RELEASE_SHA: ONE_UNCHANGED_SHA
EVIDENCE_PRS: MERGED_OR_FORMALLY_RESOLVED
UNEXPLAINED_5XX: 0
PROJECT_OWNER_AUTHORIZATION: EXPLICIT
```

Authorization must record:

- Exact base SHA.
- Exact implementation scope.
- Authorized migration version range after checking current main.
- Authorized branch naming and responsible owners.
- Explicit statement that REM-P0-006 remains independent.

### HOLD

Gate B remains blocked when any production or evidence gate is queued, running, failed, stale or tied to another SHA.

### REVOKE

Authorization is revoked before implementation merge if:

- The authorized base changes through a conflicting CRM/database migration.
- A new CRM-007 production defect reopens Issue #563.
- Migration version collision appears.
- Tenant isolation or security baseline regresses.

A revoked authorization requires a new exact-base review; existing implementation work must not be merged until reconciled.

---

## 6. Prohibited Actions While Blocked

```text
NO_CRM_008_EXECUTABLE_SQL
NO_CRM_008_JAVA_IMPLEMENTATION
NO_CRM_008_CONTROLLERS
NO_CRM_008_JDBC_ADAPTERS
NO_CRM_008_FRONTEND_FEATURES
NO_CRM_008_RUNTIME_FLAGS
NO_CRM_008_PRODUCTION_CHANGE
NO_PRE-AUTHORIZATION_IMPLEMENTATION_COMMIT
```

Permitted work is limited to documentation, planning, estimation, review preparation and non-executable acceptance design.

---

## 7. Prepared Delivery Package

| Artifact | Status |
|---|---|
| `CRM-008B-IMPLEMENTATION-BACKLOG.md` | COMPLETE |
| `CRM-008B-MIGRATION-RUNBOOK.md` | COMPLETE |
| `CRM-008B-TEST-EVIDENCE-RUNBOOK.md` | COMPLETE |
| `CRM-008B-START-GATE.md` | COMPLETE |

This package satisfies the CRM-008A Gate B requirement that the implementation backlog be assigned before start authorization. It does not satisfy the CRM-007 production closure requirement.

---

## 8. Final Preparation Decision

```text
CRM_008B_FOUNDATION_PREPARATION: COMPLETED
IMPLEMENTATION_BACKLOG: READY
MIGRATION_RUNBOOK: READY
TEST_EVIDENCE_RUNBOOK: READY
START_GATE: READY_FOR_FINAL_CRM_007_EVIDENCE
CRM_008B_IMPLEMENTATION: NOT_AUTHORIZED
BLOCKING_GATE: CRM_007_FINAL_PRODUCTION_CLOSURE
ISSUE_563: OPEN
REM_P0_006: INDEPENDENT_AND_UNAFFECTED
```
