# CRM-008B — Foundation Implementation Backlog

> **Stage:** CRM-008B Foundation  
> **Status:** READY FOR AUTHORIZED IMPLEMENTATION  
> **Parent gate:** Issue #597  
> **Prepared base:** `08b69885f7ff17dd059596782489c47e6148442f`  
> **Functional prerequisite:** CRM-007 release `4cedf631a3e61f39039615d93cd03c3111213eb9` — CLOSED/PASS

## 1. Objective

Implement the approved CRM-008A ownership, assignment and distribution architecture without reopening design decisions or weakening tenant, RBAC, audit, concurrency, migration or evidence controls.

Approved baseline:

- 10 domain aggregates.
- 9 forward-only Flyway migrations.
- 14 new tenant-owned tables.
- 40 tenant-leading indexes.
- 21 tenant/composite foreign keys.
- 17 capabilities and 2 roles.
- 38 API operations.
- 21 acceptance criteria.
- 17 criteria required before implementation merge.

## 2. Authorization Boundary

This package is documentation only. Executable implementation begins only after:

1. this refreshed package is merged into `main`;
2. Issue #597 records explicit Project Owner authorization;
3. the resulting exact `main` SHA is recorded as `AUTHORIZED_BASE_SHA`;
4. the implementation branch is created from that SHA.

```text
CRM_008A_DESIGN_CLOSURE: COMPLETED
CRM_007_PRODUCTION_CLOSURE: COMPLETED
ISSUE_563: CLOSED_COMPLETED
ISSUE_571: CLOSED_COMPLETED
FOUNDATION_PACKAGE: READY_FOR_MERGE
CRM_008B_IMPLEMENTATION: PENDING_EXACT_SHA_AUTHORIZATION_RECORD
REM_P0_006: INDEPENDENT_AND_UNAFFECTED
```

## 3. Reserved Migration Range

Repository search on the prepared base found no use of `V20260722_*`.

```text
AUTHORIZED_PLANNED_RANGE: V20260722_1 through V20260722_9
FRACTIONAL_VERSIONS: PROHIBITED
OUT_OF_ORDER_EXECUTION: PROHIBITED
FLYWAY_REPAIR: PROHIBITED
MANUAL_PRODUCTION_SQL: PROHIBITED
```

The range is revoked if another merged change occupies any reserved version before the implementation branch is created or merged.

## 4. Delivery Waves

| Wave | Package | Owner | Estimate | Exit condition |
|---|---|---|---:|---|
| W0 | Exact-base authorization | Product + Architecture + QA | 2 SP | Issue #597 records exact authorized SHA and branch |
| W1 | Migration foundation | Database Engineering | 21 SP | Nine migrations pass PostgreSQL 16 validation |
| W2 | Domain and persistence | Backend Engineering | 21 SP | Tenant-first ports, repositories and transactions pass |
| W3 | Teams, queues and territories | Backend Engineering | 24 SP | Resource APIs and invariants pass |
| W4 | Assignment engine and ownership ledger | Backend Engineering | 26 SP | Manual/rule/round-robin/least-loaded assignment safe |
| W5 | Transfer workflow boundary | Backend + Workflow | 16 SP | Atomic transfer and separation-of-duties pass |
| W6 | RBAC, audit and OpenAPI | Security + Backend | 13 SP | 17 capabilities, audit and 38 operations validated |
| W7 | Ownership workspace | Web + UX | 24 SP | Arabic/English, RTL/LTR and accessibility complete |
| W8 | Acceptance and performance | QA | 24 SP | 17 implementation-merge criteria pass |
| W9 | Protected merge and production proof | Release + QA + Security | 13 SP | All 21 criteria and immutable evidence pass |

**Planning estimate:** 184 story points. This is not a calendar commitment.

## 5. Work Packages

### WP-00 — Gate and Baseline Verification

- Confirm `main` equals the authorized base at branch creation.
- Confirm Issue #563 and #571 remain closed with evidence.
- Confirm PR #567 and stale CRM-008/CRM-G1 evidence branches are not authoritative.
- Confirm `V20260722_1` through `_9` remain unused.
- Confirm CRM-008A baseline documents are unchanged or explicitly superseded.
- Record owner authorization, scope, migration range and branch in Issue #597.

DoD:

```text
GATE_B_DECISION: AUTHORIZED
AUTHORIZED_BASE_SHA: RECORDED
AUTHORIZED_BRANCH: RECORDED
OPEN_BLOCKERS: 0
```

### WP-01 — Migration Foundation

Planned sequence:

1. `V20260722_1` — sales teams and memberships.
2. `V20260722_2` — queues and queue memberships.
3. `V20260722_3` — territories, closure hierarchy and assignments.
4. `V20260722_4` — assignment rules and immutable rule versions.
5. `V20260722_5` — assignments and append-only ownership history.
6. `V20260722_6` — transfer requests and approval steps.
7. `V20260722_7` — owner-team and owner-queue columns on existing CRM records.
8. `V20260722_8` — 17 capabilities and 2 roles.
9. `V20260722_9` — per-tenant/per-rule round-robin counters.

Mandatory controls:

- Exact-state preconditions and postconditions.
- Fail closed on partial or conflicting schema state.
- No `IF NOT EXISTS` used to conceal drift.
- PostgreSQL 16 Testcontainers coverage for clean, baseline, partial and concurrent states.
- Tenant-leading indexes and same-tenant database relations.
- Flyway validate success and zero failed history rows.

### WP-02 — Domain and Persistence Foundation

Aggregates:

- SalesTeam and TeamMembership.
- Queue and QueueMembership.
- Territory and TerritoryAssignment.
- AssignmentRule and immutable versions.
- Assignment.
- TransferRequest and approval steps.
- OwnershipHistory.

Controls:

- Tenant identity is mandatory in every repository contract.
- Same-tenant validation is enforced at application and database boundaries.
- Assignment supersede, new assignment and history append occur atomically.
- Exactly one ACTIVE assignment remains after a successful transaction.
- Failed transactions leave no assignment, history or audit fragments.

### WP-03 — Sales Teams

- CRUD teams.
- Add, update and end memberships.
- Same-tenant active-manager validation.
- One primary membership per user per tenant.
- Archive blocked while active memberships remain.

### WP-04 — Queues and Claims

- Queue and membership lifecycle.
- Tenant-scoped bounded item lists.
- Concurrent-safe claim and release.
- Capacity, DRAINING and ARCHIVED behavior.
- Idempotency replay for successful claim operations.

### WP-05 — Territories

- Territory lifecycle and hierarchy.
- Closure-table maintenance.
- Cycle and self-parent rejection.
- Priority-based overlap resolution.
- Equal-priority ambiguity fails closed.

### WP-06 — Assignment Rules and Distribution

- Versioned deterministic rules.
- One ACTIVE version per tenant/rule code.
- Direct, team, queue, territory, least-loaded and round-robin strategies.
- Non-mutating simulation and explainability trace.
- Atomic per-tenant/per-rule round-robin counter.

### WP-07 — Assignments and Ownership Ledger

- Read current assignment.
- Manual and bulk reassignment.
- Cursor-paginated ownership history.
- Database-enforced single-active invariant.
- Backward-compatible CRM owner fast-path updates.
- Append-only history with request/correlation traceability.

### WP-08 — Transfers and Workflow Boundary

- Draft, submit, approve/reject and cancel transfers.
- Atomic multi-record transfer.
- Separation of duties and self-approval rejection.
- Persist workflow run reference.
- Block multi-step approval while only a stub is active.
- Keep HRM absence reassignment disabled until its real integration is approved.

### WP-09 — RBAC, Audit and OpenAPI

- Seed and validate 17 ownership capabilities.
- Seed SALES_MANAGER and SALES_REPRESENTATIVE roles.
- Keep `CRM.TRANSFER.EXECUTE` internal-only.
- Deny by default on all 38 operations.
- Pair authorization with tenant scoping.
- RFC 7807 errors, request IDs, correlation IDs, idempotency and strong concurrency validators.
- Never accept `tenantId` from request payload or query as authority.

### WP-10 — Frontend Ownership Workspace

Required surfaces:

1. My Work.
2. Sales Teams.
3. Queues and Queue Detail.
4. Territory Hierarchy.
5. Assignment Rules and Simulation.
6. Transfers and Approvals.
7. Ownership panel and history on CRM record details.

Controls:

- Arabic and English.
- Correct RTL/LTR.
- Keyboard-complete core workflows.
- No critical accessibility violations.
- Capability-aware UI without treating the client as an authorization boundary.

### WP-11 — Acceptance, Evidence and Release

Implementation merge requires:

```text
AC-01 through AC-11
AC-DB-01
AC-DB-02
AC-DB-03
AC-CONC-01
AC-RR-01
AC-TEST-01
TOTAL: 17
```

Formal closure additionally requires:

```text
AC-12 Localization
AC-13 Accessibility
AC-14 Performance
AC-15 Production Proof
TOTAL: 21
```

## 6. Global Definition of Ready

A work item may start only when:

- Issue #597 contains exact authorization.
- The branch descends from the authorized SHA.
- Migration versions remain unoccupied.
- Dependencies and evidence owners are named.
- Tenant, RBAC, audit, concurrency and rollback implications are reviewed.

## 7. Global Definition of Done

- Changes are traceable to a work package and acceptance criterion.
- Tests prove invariants, not only code coverage.
- Tenant isolation is proven at application and database boundaries.
- Expected 4xx outcomes remain strict; HTTP 500 is never accepted.
- Evidence is immutable and tied to one exact SHA.
- No timeout inflation, retry masking, skipped gate, `continue-on-error`, admin bypass, Flyway repair, history edit or manual production SQL.

## 8. Readiness Decision

```text
IMPLEMENTATION_BACKLOG: COMPLETE
OWNERS_AND_ESTIMATES: RECORDED
DEPENDENCIES: RECORDED
AC_TRACEABILITY: RECORDED
MIGRATION_RANGE: V20260722_1..V20260722_9 RESERVED_PENDING_MERGE
CRM_008B_FOUNDATION_PACKAGE: READY
NEXT_ACTION: MERGE_PACKAGE_THEN_RECORD_EXACT_OWNER_AUTHORIZATION
```