# CRM-008B — Foundation Implementation Backlog

> **Stage:** CRM-008B Foundation Readiness  
> **Status:** PREPARED / IMPLEMENTATION NOT AUTHORIZED  
> **Parent gate:** Issue #597  
> **Dependency:** CRM-007 production closure, Issue #563 closure, and explicit Project Owner authorization  
> **Scope of this document:** executable backlog only; no SQL, Java, controller, adapter, workflow, feature-flag, or production change is authorized by this document.

---

## 1. Objective

Translate the approved CRM-008A design baseline into an assigned, estimated, traceable implementation backlog that can start immediately after Gate B authorization without reopening design decisions.

The baseline contains:

- 10 domain aggregates.
- 9 planned sequential-integer Flyway migrations.
- 14 new tables.
- 40 new tenant-leading indexes.
- 21 tenant/composite foreign-key constraints.
- 17 capabilities and 2 roles.
- 38 API operations.
- 21 acceptance criteria.
- 17 criteria required before implementation merge.

---

## 2. Gate Boundary

```text
CRM_008A_DESIGN_CLOSURE: COMPLETED
CRM_008B_GATE_PREPARATION: COMPLETED_BY_THIS_PACKAGE
CRM_008B_IMPLEMENTATION: NOT_AUTHORIZED
CRM_007_PRODUCTION_CLOSURE: REQUIRED
ISSUE_563: MUST_BE_CLOSED
PR_567: MUST_BE_FORMALLY_RESOLVED
PROJECT_OWNER_AUTHORIZATION: REQUIRED
REM_P0_006: INDEPENDENT_AND_UNAFFECTED
```

Until authorization, every work item below remains `READY / BLOCKED_BY_GATE`.

---

## 3. Delivery Sequence

| Wave | Work package | Primary owner | Estimate | Entry condition | Exit condition |
|---|---|---|---:|---|---|
| W0 | Gate and baseline verification | Project Owner + Architecture + QA | 2 SP | Documentation PR merged | Gate evidence current and exact |
| W1 | Database foundation | Database Engineering | 21 SP | Gate B authorized | 9 migrations validated on PostgreSQL 16 |
| W2 | Domain and persistence foundation | Backend Engineering | 21 SP | W1 schema contract stable | Ports, aggregates, repositories and transactions verified |
| W3 | Team, queue and territory capabilities | Backend Engineering | 24 SP | W2 complete | Team/queue/territory use cases and APIs pass contracts |
| W4 | Assignment engine and ownership ledger | Backend Engineering | 26 SP | W2 complete | Manual/rule/round-robin/least-loaded assignment safe |
| W5 | Transfer workflow boundary | Backend + Workflow Integration | 16 SP | W4 assignment transaction stable | Single-approver flow and fail-closed workflow boundary pass |
| W6 | RBAC, audit and OpenAPI | Security + Backend | 13 SP | W3-W5 API inventory stable | 17 capabilities, deny-by-default, audit and contract checks pass |
| W7 | Frontend ownership workspace | Web Engineering + UX | 24 SP | API contract frozen | Six screens, Arabic/English and accessibility ready |
| W8 | Acceptance and performance | QA Engineering | 24 SP | W1-W7 integrated | 17 implementation-merge AC pass |
| W9 | Merge and production evidence | Release + QA + Security | 13 SP | W8 green | Protected merge and AC-15 production proof complete |

**Total planning estimate:** 184 story points. This is an execution estimate, not a calendar commitment.

---

## 4. Work Packages

### WP-00 — Gate and Exact-Baseline Verification

**Priority:** P0  
**Owner:** Project Owner / Architecture / QA  
**Estimate:** 2 SP

Tasks:

- Re-read Issue #563 and confirm `CLOSED_WITH_EVIDENCE`.
- Confirm obsolete PR #567 is closed or formally superseded.
- Record exact authorized `main` SHA.
- Confirm no migration version reserved for the planned sequence is already used.
- Confirm CRM-008A authoritative documents are unchanged or explicitly superseded.
- Record explicit Project Owner authorization in Issue #597.
- Create the implementation branch only after all checks pass.

Acceptance:

```text
GATE_B_DECISION: AUTHORIZED
AUTHORIZED_MAIN_SHA: RECORDED
OPEN_BLOCKERS: 0
```

Definition of Done:

- Evidence is linked in Issue #597.
- Authorization names the exact scope and exact base SHA.
- No implementation commit predates authorization.

---

### WP-01 — Migration Foundation

**Priority:** P0  
**Owner:** Database Engineering  
**Estimate:** 21 SP  
**Traceability:** AC-DB-01, AC-DB-02, AC-DB-03, AC-CONC-01, AC-RR-01

Stories:

1. Sales teams and memberships schema.
2. Queues and queue memberships schema.
3. Territories, closure hierarchy and territory assignments schema.
4. Assignment rules and immutable rule versions schema.
5. Assignments and append-only ownership history schema.
6. Transfer requests and approval steps schema.
7. Existing CRM owner-team and owner-queue column extension.
8. Capability and role seed migration.
9. Per-tenant/per-rule round-robin counter schema.

Mandatory controls:

- Sequential-integer migration versions only.
- Forward-only migration policy.
- Preconditions, transactional change and postconditions in every migration.
- Create migrations require complete target absence.
- Alter migrations require exact predecessor state.
- Seed migration uses controlled idempotency and fails on conflicting definitions.
- No `IF NOT EXISTS` as a mechanism for masking partial schema state.
- No Flyway repair, history editing or ad-hoc production SQL.
- PostgreSQL 16 Testcontainers validation before review.
- Tenant-leading index and same-tenant relation verification.

Definition of Done:

- Nine migrations execute cleanly on an empty supported baseline.
- Partial-state tests fail closed without silent repair.
- Double-application behavior is explicitly tested according to migration type.
- Schema inventory proves 14 tables, 40 indexes and 21 tenant/composite FKs.
- Flyway validate succeeds and no failed history rows exist.

---

### WP-02 — Domain and Persistence Foundation

**Priority:** P0  
**Owner:** Backend Engineering  
**Estimate:** 21 SP

Scope:

- SalesTeam.
- TeamMembership.
- Queue.
- QueueMembership.
- Territory.
- TerritoryAssignment.
- AssignmentRule and versions.
- Assignment.
- TransferRequest and steps.
- OwnershipHistory.

Tasks:

- Preserve pure-domain boundaries and hexagonal ports.
- Implement tenant-first repository contracts.
- Enforce same-tenant validation on every relation.
- Implement transactional supersede-then-insert assignment behavior.
- Append ownership history in the same transaction as assignment changes.
- Enforce immutable history through application behavior and database privileges.
- Preserve backward-compatible owner fast-path columns.
- Reject partial owner tuples and invalid polymorphic owner targets.

Definition of Done:

- Domain invariants are executable and independently tested.
- Repository calls cannot omit authenticated tenant context.
- Exactly one ACTIVE assignment remains after every successful assignment transaction.
- Failed transactions leave no history or audit fragments.

---

### WP-03 — Sales Teams

**Priority:** P0  
**Owner:** Backend Engineering  
**Estimate:** 8 SP  
**API scope:** 8 operations

Stories:

- List, create, read and update teams.
- List, add, update and end memberships.
- Validate active same-tenant manager.
- Enforce one primary membership per user per tenant.
- Prevent archive while active memberships remain.
- Emit auditable team and membership events.

Mapped acceptance:

- AC-01 Tenant Isolation.
- AC-10 Audit Completeness.
- AC-12 Localization.
- AC-13 Accessibility.

---

### WP-04 — Queues and Claims

**Priority:** P0  
**Owner:** Backend Engineering  
**Estimate:** 9 SP  
**API scope:** 8 operations

Stories:

- Queue lifecycle and membership reads.
- Paginated tenant-scoped item lists.
- Concurrent-safe claim and release.
- Queue capacity enforcement.
- DRAINING and ARCHIVED behavior.
- Idempotency-key replay for successful claims.
- SLA and escalation metadata reads.

Mapped acceptance:

- AC-01 Tenant Isolation.
- AC-04 Concurrent Queue Claim.
- AC-08 Workload Distribution Safety.
- AC-10 Audit Completeness.
- AC-14 Performance.

---

### WP-05 — Territories

**Priority:** P0  
**Owner:** Backend Engineering  
**Estimate:** 7 SP  
**API scope:** 6 operations

Stories:

- Territory create, read, update and hierarchy listing.
- Same-tenant parent validation.
- Closure-table maintenance.
- Cycle and self-parent rejection.
- Priority-based overlap resolution.
- Fail closed on equal-priority ambiguity.
- User/team territory assignment lifecycle.

Mapped acceptance:

- AC-01 Tenant Isolation.
- AC-09 Territory Hierarchy Integrity.
- AC-10 Audit Completeness.

---

### WP-06 — Assignment Rules and Distribution Engine

**Priority:** P0  
**Owner:** Backend Engineering  
**Estimate:** 13 SP  
**API scope:** 7 operations

Stories:

- Versioned assignment rules.
- Exactly one ACTIVE version per tenant and rule code.
- Deterministic evaluation order.
- Non-mutating simulation.
- Direct, team, queue, territory, least-loaded and round-robin strategies.
- Eligibility filtering for inactive, non-member and over-capacity users.
- Structured explainability trace.
- Transactional per-tenant/per-rule round-robin counter.

Mapped acceptance:

- AC-07 Rule Explainability.
- AC-08 Workload Distribution Safety.
- AC-RR-01 Round-Robin Counter Concurrency.
- AC-14 Performance.

---

### WP-07 — Assignments and Ownership Ledger

**Priority:** P0  
**Owner:** Backend Engineering  
**Estimate:** 13 SP  
**API scope:** 4 operations

Stories:

- Read current assignment.
- Manual reassign.
- Bulk reassign with reason and audit.
- Cursor-paginated ownership history.
- Transactional supersede and new active assignment.
- Database-enforced single-active invariant.
- Backward-compatible updates to CRM record owner columns.
- Append-only ownership history and correlation-id traceability.

Mapped acceptance:

- AC-02 Single Primary Owner.
- AC-03 Ownership History Immutability.
- AC-07 Rule Explainability.
- AC-DB-01 Database Single Active Assignment.
- AC-CONC-01 Concurrent Assignment Conflict.

---

### WP-08 — Transfers and Workflow Boundary

**Priority:** P0  
**Owner:** Backend Engineering / Workflow Integration  
**Estimate:** 16 SP  
**API scope:** 5 operations

Stories:

- Draft, submit, approve/reject and cancel transfer requests.
- Atomic multi-record transfer.
- Separation of duties.
- Single-approver temporary mode.
- Persist workflow run reference.
- Cancel workflow on transfer cancellation.
- Block multi-step approval while the WorkflowPort stub is active.
- Reject production use of the HRM stub and keep absence reassignment disabled.

Mapped acceptance:

- AC-05 Atomic Transfer.
- AC-06 Separation of Duties.
- AC-10 Audit Completeness.
- AC-11 Workflow Integration.

---

### WP-09 — RBAC, Audit and OpenAPI Contract

**Priority:** P0  
**Owner:** Security Engineering / Backend Engineering  
**Estimate:** 13 SP

Tasks:

- Seed and validate all 17 CRM ownership capabilities.
- Seed SALES_MANAGER and SALES_REPRESENTATIVE roles.
- Keep CRM.TRANSFER.EXECUTE internal-only.
- Enforce deny-by-default capability checks on all 38 operations.
- Pair every capability decision with tenant scoping.
- Audit successful and failed write authorization attempts.
- Generate the full OpenAPI contract from the approved summary.
- Enforce RFC 7807 error envelopes.
- Enforce request/correlation identifiers, idempotency and ETag behavior.
- Verify `tenantId` is never accepted from request payload or query.

Definition of Done:

- Unauthorized role/capability combinations fail predictably.
- Cross-tenant requests cannot reveal or mutate foreign records.
- OpenAPI lint, backward-compatibility and route-contract checks pass.

---

### WP-10 — Frontend Ownership Workspace

**Priority:** P1 for implementation merge; P0 for formal closure  
**Owner:** Web Engineering / UX  
**Estimate:** 24 SP

Screens:

1. My Work.
2. Sales Teams.
3. Queues and Queue Detail.
4. Territory Hierarchy.
5. Assignment Rules and Simulation.
6. Transfers and Approvals.
7. Ownership panel/history on CRM record detail.

Controls:

- Arabic and English localization.
- Correct RTL/LTR behavior.
- Keyboard-complete core operations.
- Accessible names and labels.
- No critical axe violations.
- Capability-aware actions without trusting the client for authorization.
- Pagination and bounded list requests.

Mapped acceptance:

- AC-12 Localization.
- AC-13 Accessibility.
- AC-14 Performance.

---

### WP-11 — Acceptance, Evidence and Release

**Priority:** P0  
**Owner:** QA Engineering / Release Engineering / Security  
**Estimate:** 24 SP before merge + 13 SP post-integration

Implementation-merge gate:

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

Formal closure adds:

```text
AC-12
AC-13
AC-14
AC-15
TOTAL: 21
```

Evidence requirements:

- Exact tested commit SHA.
- PostgreSQL 16 Testcontainers reports.
- Flyway schema inventory and postcondition report.
- Concurrency evidence.
- RBAC and two-tenant evidence.
- Playwright localization/accessibility reports.
- Performance query plans and p95 results.
- Production deployment and backend image SHA match.
- Production authenticated smoke, audit and history exports.
- Zero unexplained 5xx in the test window.

---

## 5. Dependency Graph

```text
WP-00 Gate Authorization
  → WP-01 Migration Foundation
  → WP-02 Domain/Persistence Foundation
      → WP-03 Teams
      → WP-04 Queues
      → WP-05 Territories
      → WP-06 Rules/Distribution
      → WP-07 Assignments/Ledger
          → WP-08 Transfers/Workflow
  → WP-09 RBAC/OpenAPI (integrates WP-03..WP-08)
  → WP-10 Frontend
  → WP-11 Acceptance and Release
```

Parallelization is allowed only where the authoritative contract is stable. Schema, transaction and tenant-isolation work cannot be bypassed by parallel API/UI delivery.

---

## 6. Global Definition of Ready

A work item may move from `READY / BLOCKED_BY_GATE` to `IN_PROGRESS` only when:

- Gate B is explicitly authorized.
- The exact base SHA is recorded.
- Dependencies are complete.
- Acceptance criteria and evidence artifact are named.
- Tenant, RBAC, audit and concurrency implications are reviewed.
- No unresolved design question remains.

---

## 7. Global Definition of Done

A work item is complete only when:

- Code and migration changes are traceable to this backlog.
- Tests demonstrate the stated invariant, not merely line coverage.
- Tenant isolation is validated at application and database boundaries.
- Expected 4xx outcomes are preserved; no test accepts HTTP 500.
- Evidence is immutable and tied to one exact SHA.
- No timeout inflation, retry masking, skip, `continue-on-error`, admin bypass, Flyway repair, history edit, or manual production SQL is used.
- Documentation distinguishes executed CRM-008 tests from unrelated repository regression tests.

---

## 8. Readiness Decision

```text
IMPLEMENTATION_BACKLOG: COMPLETE
OWNERSHIP_MODEL: ASSIGNED_BY_ROLE
ESTIMATES: RECORDED
DEPENDENCIES: RECORDED
AC_TRACEABILITY: RECORDED
EXECUTION_SEQUENCE: RECORDED
CRM_008B_IMPLEMENTATION: NOT_AUTHORIZED
NEXT_GATE: CRM_007_FINAL_CLOSURE + OWNER_AUTHORIZATION
```
