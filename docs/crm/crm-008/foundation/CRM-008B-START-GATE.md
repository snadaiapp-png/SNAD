# CRM-008B — Foundation Start Gate

> **Gate:** CRM-008B implementation start authorization  
> **Prepared:** 2026-07-22  
> **Parent:** Issue #597  
> **Package base:** `08b69885f7ff17dd059596782489c47e6148442f`

## 1. Purpose

Provide one authoritative transition from CRM-008A design closure and CRM-007 Production closure into CRM-008B implementation while preserving exact-SHA, migration, tenant, security and evidence controls.

## 2. Completed Prerequisites

```text
CRM_008A_DESIGN_CLOSURE: COMPLETED
CRM_008A_DESIGN_BASELINE: APPROVED
CRM_007_PRODUCTION_CLOSURE: COMPLETED
CRM_007_FUNCTIONAL_RELEASE: 4cedf631a3e61f39039615d93cd03c3111213eb9
CRM_007_EVIDENCE_MAIN: 08b69885f7ff17dd059596782489c47e6148442f
ISSUE_563: CLOSED_COMPLETED
ISSUE_571: CLOSED_COMPLETED
CRM_G1_RUN: 29917230857 / SUCCESS
CRM_007_RUN: 29917314330 / SUCCESS
UNEXPECTED_CRM_HTTP_5XX: 0
IMPLEMENTATION_BACKLOG: COMPLETE
MIGRATION_RUNBOOK: COMPLETE
TEST_EVIDENCE_RUNBOOK: COMPLETE
AC_TRACEABILITY: 21/21
REM_P0_006: INDEPENDENT_AND_UNAFFECTED
```

## 3. Authorized Scope

CRM-008B implementation scope is limited to:

- Sales teams and memberships.
- Queues, memberships, claims and releases.
- Territories and hierarchy.
- Assignment rules and simulations.
- Assignments and append-only ownership history.
- Transfer requests and approval steps.
- Owner-team and owner-queue compatibility fields.
- 17 ownership capabilities and 2 roles.
- Per-tenant/per-rule round-robin counters.
- 38 governed API operations.
- Ownership workspace frontend surfaces.
- All 21 acceptance criteria and immutable evidence.

Excluded without separate authorization:

- HRM absence reassignment.
- Multi-step approval while the WorkflowPort remains a stub.
- Commercial go-live approval.
- REM-P0-006 closure.
- Unrelated CRM, ERP, Accounting or platform refactors.

## 4. Migration Authorization

```text
RESERVED_RANGE: V20260722_1 through V20260722_9
SEQUENTIAL_INTEGER_VERSIONS_ONLY: TRUE
FRACTIONAL_VERSIONS: PROHIBITED
FORWARD_ONLY: TRUE
FAIL_CLOSED: TRUE
FLYWAY_REPAIR: PROHIBITED
SCHEMA_HISTORY_EDIT: PROHIBITED
MANUAL_PRODUCTION_SQL: PROHIBITED
```

This range must remain unused when the implementation branch is created and when the implementation PR is merged. A collision revokes authorization until a new range is reviewed and recorded.

## 5. Mandatory Start Conditions

- [x] CRM-008A design closure complete.
- [x] CRM-007 exact-SHA Production closure complete.
- [x] CRM-G1 and CRM-007 evidence merged.
- [x] Issues #563 and #571 closed.
- [x] Foundation backlog, migration and test runbooks prepared.
- [x] Candidate migration range has no observed repository collision.
- [ ] This refreshed Foundation package merged into `main`.
- [ ] Resulting exact `main` SHA recorded in Issue #597.
- [ ] Project Owner authorization recorded against that exact SHA.
- [ ] Implementation branch created after authorization.

## 6. Authorization Record Requirements

Issue #597 must record:

```text
GATE_B_DECISION: AUTHORIZED
AUTHORIZED_BASE_SHA: <exact post-package main SHA>
AUTHORIZED_BRANCH: feature/crm-008b-foundation-<date-or-id>
AUTHORIZED_SCOPE: CRM-008B ONLY
AUTHORIZED_MIGRATIONS: V20260722_1..V20260722_9
AUTHORIZATION_SOURCE: PROJECT_OWNER
OPEN_BLOCKERS: 0
REM_P0_006: INDEPENDENT_AND_UNAFFECTED
```

No executable CRM-008B commit may predate this record.

## 7. Revocation Conditions

Authorization is revoked before implementation merge if:

- `main` receives a conflicting CRM/database migration.
- Any reserved version becomes occupied.
- Issue #563 reopens because of a CRM-007 regression.
- Tenant isolation, security baseline or migration validation regresses.
- The implementation branch is not descended from the authorized SHA.
- Scope expands beyond this document without owner approval.

Revocation blocks merge but does not authorize history rewriting or migration renumbering without review.

## 8. Implementation Merge Gate

Required before protected merge:

```text
AC-01 through AC-11: PASS
AC-DB-01: PASS
AC-DB-02: PASS
AC-DB-03: PASS
AC-CONC-01: PASS
AC-RR-01: PASS
AC-TEST-01: PASS
REQUIRED_CHECKS: SUCCESS
UNEXPLAINED_HTTP_5XX: 0
EXPECTED_HEAD_SHA: UNCHANGED
```

Formal closure additionally requires AC-12 through AC-15.

## 9. Current Decision

```text
CRM_008B_FOUNDATION_PREPARATION: COMPLETE
CRM_007_BLOCKER: REMOVED
FOUNDATION_PACKAGE: PENDING_REQUIRED_CHECKS_AND_MERGE
OWNER_INTENT: CRM_008B_START_REQUESTED_2026-07-22
CRM_008B_IMPLEMENTATION: PENDING_POST_MERGE_EXACT_SHA_RECORD
CURRENT_BLOCKER: FOUNDATION_PACKAGE_MERGE_AND_EXACT_SHA_AUTHORIZATION_RECORD
```

After this package is merged, Issue #597 becomes the authoritative executable start record.