# CRM-008 — Acceptance Plan

> 21 acceptance criteria (AC-01 → AC-15 + AC-DB-01, AC-DB-02, AC-DB-03, AC-CONC-01, AC-RR-01, AC-TEST-01). Each criterion specifies the test type, test class path (proposed), pass criterion, and evidence artifact.

---

## AC-01 — Tenant Isolation

**Requirement:** A user in tenant A cannot view or modify a team, queue, territory, assignment, or transfer owned by tenant B.

**Test type:** Integration + Testcontainers (PostgreSQL 16)

**Test classes (proposed):**
- `apps/sanad-platform/src/test/java/com/sanad/platform/crm/ownership/contract/SalesTeamTenantIsolationContractTest.java`
- `apps/sanad-platform/src/test/java/com/sanad/platform/crm/ownership/contract/QueueTenantIsolationContractTest.java`
- `apps/sanad-platform/src/test/java/com/sanad/platform/crm/ownership/contract/TerritoryTenantIsolationContractTest.java`
- `apps/sanad-platform/src/test/java/com/sanad/platform/crm/ownership/contract/AssignmentTenantIsolationContractTest.java`
- `apps/sanad-platform/src/test/java/com/sanad/platform/crm/ownership/contract/TransferRequestTenantIsolationContractTest.java`
- `apps/sanad-platform/src/test/java/com/sanad/platform/crm/ownership/contract/OwnershipHistoryTenantIsolationContractTest.java`

**Pass criterion:** For each scenario:
- Create record in tenant A
- Attempt read/update/delete from tenant B context (mocked JWT)
- Assert HTTP 403 Forbidden
- Assert no row returned (read paths return empty, never leak existence via 404)

**Evidence artifact:** Test run report with tenant A and tenant B UUIDs in the report metadata.

---

## AC-02 — Single Primary Owner

**Requirement:** Every ownable CRM record has exactly one ACTIVE assignment at any instant.

**Test type:** Testcontainers + concurrency test

**Test class (proposed):**
- `apps/sanad-platform/src/test/java/com/sanad/platform/crm/ownership/contract/SingleActiveAssignmentContractTest.java`

**Pass criterion:**
- Insert assignment #1 → succeeds
- Insert assignment #2 for the same (tenant, record_type, record_id) → succeeds AND automatically marks #1 as SUPERSEDED
- Query active assignments → returns exactly one (the newer)
- DB partial unique index `WHERE status='ACTIVE'` prevents a third concurrent insert from creating two ACTIVE rows

**Evidence artifact:** DB query showing `SELECT count(*) FROM crm_assignments WHERE tenant_id=? AND record_type=? AND record_id=? AND status='ACTIVE'` returns 1.

---

## AC-03 — Ownership History Immutability

**Requirement:** A change in ownership never deletes or modifies existing history rows.

**Test type:** Integration + DB role test

**Test class (proposed):**
- `apps/sanad-platform/src/test/java/com/sanad/platform/crm/ownership/contract/OwnershipHistoryImmutabilityContractTest.java`

**Pass criterion:**
- Reassign a record 5 times → 5 history rows inserted
- `UPDATE crm_ownership_history SET ... ` → fails with permission denied (DB role revocation of UPDATE)
- `DELETE FROM crm_ownership_history WHERE ...` → fails with permission denied
- After 5 reassignments, the history query returns all 5 rows ordered by `effective_at ASC`

**Evidence artifact:** DB role grant output showing only `INSERT` and `SELECT` on `crm_ownership_history`.

---

## AC-04 — Concurrent Queue Claim

**Requirement:** When two users attempt to claim the same queue item simultaneously, exactly one succeeds and the other receives a clear conflict.

**Test type:** Concurrency test (Testcontainers)

**Test class (proposed):**
- `apps/sanad-platform/src/test/java/com/sanad/platform/crm/ownership/contract/ConcurrentQueueClaimContractTest.java`

**Pass criterion:**
- Two threads call `claimQueueItem` for the same item simultaneously
- Exactly one returns `QueueClaimResult.success`
- The other returns `ConcurrentClaimConflictException` with HTTP 409 Conflict
- No row is left in an inconsistent state (no double-claim, no orphaned queue item)
- Idempotency key retry on the same item from the same user returns the same successful result

**Evidence artifact:** Test log showing thread A success + thread B 409 + final DB state showing single owner.

---

## AC-05 — Atomic Transfer

**Requirement:** A multi-record transfer completes entirely or fails entirely.

**Test type:** Integration test (Testcontainers)

**Test class (proposed):**
- `apps/sanad-platform/src/test/java/com/sanad/platform/crm/ownership/contract/AtomicTransferContractTest.java`

**Pass criterion:**
- Create a transfer request with 5 record ids
- Force one of the records to fail mid-execution (e.g. record belongs to a different tenant)
- Assert the entire transaction rolls back
- Assert no record's ownership changed
- Assert no `OwnershipHistory` row was inserted
- Assert the transfer request state transitions to `FAILED` with a `failureReason`

**Evidence artifact:** DB query showing all 5 records' ownership unchanged + transfer request state = FAILED.

---

## AC-06 — Separation of Duties

**Requirement:** The requester of a transfer cannot approve their own request when the policy requires independent approval.

**Test type:** Unit + integration test

**Test class (proposed):**
- `apps/sanad-platform/src/test/java/com/sanad/platform/crm/ownership/contract/TransferSeparationOfDutiesContractTest.java`

**Pass criterion:**
- Create transfer with `policy=SINGLE_APPROVER`
- Attempt to approve as the requester → HTTP 403 Forbidden with code `separation_of_duties_violation`
- Approve as a different user with `CRM.TRANSFER.APPROVE` → succeeds
- Create transfer with `policy=NO_APPROVAL_REQUIRED` → requester can execute directly

**Evidence artifact:** Audit log showing the rejected approval attempt + the successful one.

---

## AC-07 — Rule Explainability

**Requirement:** Every automatic assignment records the rule, the matched conditions, and the resulting owner.

**Test type:** Unit + integration test

**Test class (proposed):**
- `apps/sanad-platform/src/test/java/com/sanad/platform/crm/ownership/contract/AssignmentRuleExplainabilityContractTest.java`

**Pass criterion:**
- Create an assignment rule with `matchConditions = {"source":"web_form","country":"SA"}`
- Create a Lead matching the conditions
- Assert the Lead's assignment has `assigned_by_rule_id` set
- Assert `OwnershipHistory.changeType = RULE_MATCH`
- Assert `OwnershipHistory.triggerReferenceId = <rule_id>`
- Assert `Assignment.reason = "RULE_MATCH"`
- Assert a structured `RuleEvaluationTrace` is recorded (in `crm_assignment_rule_traces` table or as JSONB in the assignment)

**Evidence artifact:** Sample `OwnershipHistory` row + `Assignment` row showing all explainability fields.

---

## AC-08 — Workload Distribution Safety

**Requirement:** Least-loaded and round-robin never assign to inactive users, non-members, or users over capacity.

**Test type:** Unit + integration test

**Test class (proposed):**
- `apps/sanad-platform/src/test/java/com/sanad/platform/crm/ownership/contract/WorkloadDistributionSafetyContractTest.java`

**Pass criterion:**
- Team with 3 members: user A (active, capacity 5), user B (suspended), user C (active, capacity 5, currently 5/5)
- Round-robin assignment: skips B (suspended), skips C (at capacity), assigns to A
- Least-loaded assignment: same outcome
- When all members are at capacity → assignment goes to the queue (fallback) with `reason = TEAM_AT_CAPACITY`

**Evidence artifact:** Workload summary before/after + assignment trace showing skip reasons.

---

## AC-09 — Territory Hierarchy Integrity

**Requirement:** The territory hierarchy rejects cycles and invalid parent-child relationships.

**Test type:** Unit + integration test

**Test class (proposed):**
- `apps/sanad-platform/src/test/java/com/sanad/platform/crm/ownership/contract/TerritoryHierarchyIntegrityContractTest.java`

**Pass criterion:**
- Create territory A → B → C (linear)
- Attempt to set C as parent of A → rejected with code `territory_cycle_detected`
- Attempt to set A as parent of A → rejected with code `territory_self_parent`
- Attempt to set archived territory D as parent → rejected
- Closure table has correct entries for A → B → C

**Evidence artifact:** Closure table dump + rejected operation logs.

---

## AC-10 — Audit Completeness

**Requirement:** Every create/update/claim/release/transfer/approval produces an auditable event.

**Test type:** Integration test

**Test class (proposed):**
- `apps/sanad-platform/src/test/java/com/sanad/platform/crm/ownership/contract/AuditCompletenessContractTest.java`

**Pass criterion:** For each of the following operations, assert exactly one `platform_audit_logs` row with matching `correlation_id`:
- SalesTeam.create
- TeamMembership.start
- Queue.create
- Queue.claim
- Queue.release
- Territory.create
- AssignmentRule.activate
- Assignment.create (manual)
- Assignment.create (rule-driven)
- TransferRequest.submit
- TransferRequest.approve
- TransferRequest.execute
- Failed authorization (403)

**Evidence artifact:** Audit log table query with count grouped by action.

---

## AC-11 — Workflow Integration

**Requirement:** Approvals, escalations, and timers use the central Workflow Engine.

**Test type:** Contract test (mock WorkflowPort)

**Test class (proposed):**
- `apps/sanad-platform/src/test/java/com/sanad/platform/crm/ownership/contract/WorkflowIntegrationContractTest.java`

**Pass criterion:**
- `TransferRequestService.submit()` calls `WorkflowPort.startTransferApproval()`
- The returned `workflow_run_id` is stored on the transfer request
- `WorkflowPort.cancelApproval()` is called when the transfer is CANCELLED
- When `WorkflowPort.isStub() == true`, multi-step approvals are blocked with a clear error

**Evidence artifact:** Mock interaction log + transfer request row showing `workflow_run_id`.

---

## AC-12 — Localization (Arabic + English, RTL/LTR)

**Requirement:** All screens and messages are available in Arabic and English with proper RTL/LTR layout.

**Test type:** Playwright visual regression

**Test classes (proposed):**
- `apps/web/e2e/crm-ownership-ar-rtl.spec.ts`
- `apps/web/e2e/crm-ownership-en-ltr.spec.ts`

**Pass criterion:**
- Each new screen (My Work, Teams, Queues, Territories, Rules, Transfers) renders correctly in both locales
- All error messages have ar + en keys
- `scripts/ci/check_i18n_keys.py` passes (no missing keys)
- Direction switching via `LanguageSwitcher` re-renders layout correctly

**Evidence artifact:** Playwright screenshots in `apps/web/e2e/__screenshots__/crm-ownership-*`.

---

## AC-13 — Accessibility

**Requirement:** Core operations are keyboard-executable; fields and buttons have accessible names.

**Test type:** Playwright + axe-core accessibility scan

**Test class (proposed):**
- `apps/web/e2e/crm-ownership-accessibility.spec.ts`

**Pass criterion:**
- Tab navigation reaches all interactive elements on each new screen
- All buttons have `aria-label` or visible text
- All inputs have associated `<label>`
- axe-core scan reports 0 critical violations
- Color contrast ratio ≥ 4.5:1 for body text

**Evidence artifact:** axe-core report.

---

## AC-14 — Performance (Tenant-leading Indexes + Pagination)

**Requirement:** My Work, Queues, and Assignments lists never execute unbounded queries.

**Test type:** Performance baseline test

**Test class (proposed):**
- `apps/sanad-platform/src/test/java/com/sanad/platform/crm/ownership/performance/OwnershipListPerformanceTest.java`

**Pass criterion:**
- Seed 100,000 assignments across 100 tenants
- For a tenant with 1,000 assignments:
  - `GET /api/v2/crm/my-work` returns first page in < 200ms (p95)
  - `GET /api/v2/crm/queues/{id}/items?page=1` returns in < 150ms (p95)
  - `GET /api/v2/crm/ownership-history?recordType=&recordId=` returns in < 100ms (p95)
- EXPLAIN ANALYZE on each query shows `Index Scan` using a tenant-leading index (no `Seq Scan`)

**Evidence artifact:** EXPLAIN ANALYZE plans + p95 latency report.

---

## AC-15 — Production Proof

**Requirement:** A complete cycle runs in production PostgreSQL via `Vercel → BFF → backend → Supabase`, proving tenant isolation, RBAC, audit, and concurrency.

**Test type:** Production authenticated smoke (Post-merge only, with owner sign-off)

**Test class (proposed):**
- `apps/sanad-platform/src/test/java/com/sanad/platform/crm/ownership/e2e/OwnershipProductionE2ETest.java` (run via `crm-authenticated-acceptance.yml` workflow)

**Pass criterion:**
- Login as tenant A acceptance user → create team → create queue → claim item → ✓
- Login as tenant B acceptance user → attempt to read tenant A's queue → 403 → ✓
- Tenant A user requests transfer → tenant A manager approves → ✓
- Ownership history shows the full chain with correct correlation ids → ✓
- Audit log has matching entries → ✓
- Backend `/actuator/health` returns UP throughout → ✓
- No 5xx errors in Vercel runtime logs during the test window → ✓

**Evidence artifact:**
- Test run id (GitHub Actions)
- Vercel deployment SHA (matches main HEAD)
- Backend SHA (matches main HEAD)
- Audit log export (redacted)
- Ownership history export (redacted)
- Vercel runtime log export showing 0 unexplained 5xx

---

## AC-DB-01 — Database-Enforced Single Active Assignment (added per EXEC-PROMPT-CRM-008A-R2)

**Requirement:** PostgreSQL CANNOT retain two assignments with `status='ACTIVE'` for the same `(tenant_id, record_type, record_id)` — even if two concurrent requests bypass the application layer and reach the database simultaneously.

**Test type:** Testcontainers (PostgreSQL 16) + concurrency test

**Test class (proposed):**
- `apps/sanad-platform/src/test/java/com/sanad/platform/crm/ownership/contract/AcDb01DatabaseEnforcedSingleActiveAssignmentTest.java`

**Pass criterion:**
- The `crm_assignments` table has a partial unique index `uk_assignments_active_per_record ON (tenant_id, record_type, record_id) WHERE status='ACTIVE'`
- Two concurrent transactions attempt to INSERT a row with `status='ACTIVE'` for the same `(tenant_id, record_type, record_id)`
- Exactly one INSERT succeeds; the other receives a PostgreSQL unique violation (SQL state `23505`)
- After both transactions complete, `SELECT count(*) FROM crm_assignments WHERE tenant_id=? AND record_type=? AND record_id=? AND status='ACTIVE'` returns exactly 1
- The test does NOT rely on the application layer — it uses raw JDBC to bypass `OwnershipWritePort`

**Evidence artifact:** Test log showing the unique violation on the second INSERT + the final count of 1.

---

## AC-DB-02 — Migration Fails Closed on Partial Schema State (added per EXEC-PROMPT-CRM-008A-R2)

**Requirement:** If the schema is in a partial or unexpected state, the migration fails with `MIGRATION_ABORTED / SCHEMA_PARTIAL_OR_UNEXPECTED / NO_SILENT_REPAIR`. No silent repair, no `flyway repair`, no `IF NOT EXISTS` masking.

**Test type:** Testcontainers (PostgreSQL 16) + Flyway migration test

**Test class (proposed):**
- `apps/sanad-platform/src/test/java/com/sanad/platform/crm/ownership/contract/AcDb02MigrationFailsClosedOnPartialStateTest.java`

**Pass criterion:**
- Pre-condition: a partial state is simulated (e.g. one of the CRM-008 tables exists but others do not; or a table exists but is missing a required column; or a constraint exists with the wrong definition)
- The CRM-008B migration is executed against this partial state
- The migration MUST abort with a clear error message containing `SCHEMA_PARTIAL_OR_UNEXPECTED`
- The migration MUST NOT auto-repair the partial state
- The Flyway history MUST NOT contain a successful row for this migration
- The Flyway history MUST NOT contain a `flyway repair` artifact
- A separate reconciliation migration is required to repair the state

**Evidence artifact:** Flyway log showing the abort + Flyway history showing no successful row + DB query showing the partial state is unchanged.

---

## AC-DB-03 — Migration Postconditions Verified (added per EXEC-PROMPT-CRM-008A-R2)

**Requirement:** Each migration verifies its postconditions — not just that the table exists, but that all expected columns, primary keys, unique constraints, foreign keys (including same-tenant composite FKs), check constraints, tenant-leading indexes, and partial unique indexes match the design.

**Test type:** Testcontainers (PostgreSQL 16) + Flyway migration test

**Test class (proposed):**
- `apps/sanad-platform/src/test/java/com/sanad/platform/crm/ownership/contract/AcDb03MigrationPostconditionsVerifiedTest.java`

**Pass criterion:**
- After each CRM-008B migration executes successfully, a verification query against `information_schema` and `pg_catalog` confirms:
  - Expected tables exist with expected names
  - Expected columns exist with expected types and nullability
  - Primary keys match the design
  - Unique constraints match the design
  - Foreign keys (including same-tenant composite FKs) match the design
  - Check constraints match the design
  - Tenant-leading indexes match the design
  - Partial unique indexes match the design (e.g. `WHERE status='ACTIVE'`)
  - No unexpected additional objects (tables, columns, indexes, constraints) were created
- If any postcondition fails, the migration transaction rolls back

**Evidence artifact:** Postcondition verification report (structured JSON) showing all checks passed.

---

## AC-CONC-01 — Concurrent Assignment Conflict (added per EXEC-PROMPT-CRM-008A-R2)

**Requirement:** Two simultaneous assignment requests for the same record produce exactly one success and one controlled conflict, with exactly one ACTIVE assignment in the database afterward.

**Test type:** Concurrency test (Testcontainers PostgreSQL 16 + multi-threaded application test)

**Test class (proposed):**
- `apps/sanad-platform/src/test/java/com/sanad/platform/crm/ownership/contract/AcConc01ConcurrentAssignmentConflictTest.java`

**Pass criterion:**
- Two threads call `OwnershipWritePort.assign()` for the same `(tenant_id, record_type, record_id)` simultaneously
- Exactly one call returns a successful `Assignment` result
- The other call receives a controlled conflict (HTTP 409 or equivalent — `ConcurrentAssignmentConflictException` or PostgreSQL unique violation wrapped in a clear application error)
- After both calls complete, `SELECT count(*) FROM crm_assignments WHERE tenant_id=? AND record_type=? AND record_id=? AND status='ACTIVE'` returns exactly 1
- The `crm_ownership_history` table contains exactly one row for this correlation (no duplicate history entries)
- The test is repeated 10 times with different records to ensure determinism

**Evidence artifact:** Test log showing thread A success + thread B conflict + final DB state showing single ACTIVE assignment + history table showing single entry.

---

## AC-TEST-01 — Test Execution Distinction in Stage Report (added per EXEC-PROMPT-CRM-008A-R2)

**Requirement:** The stage report clearly differentiates between existing repository tests (which were executed) and CRM-008-specific tests (which were NOT executed). The success of existing repository tests MUST NOT be presented as evidence of CRM-008 completion.

**Test type:** Documentation review (manual + automated Markdown lint)

**Test class (proposed):**
- Manual review by QA Owner
- Automated check: `scripts/ci/check_crm_008_test_distinction.py` (to be added in CRM-008B)

**Pass criterion:**
- `STAGE-REPORT-CRM-008A.md` §2.5 contains the precise distinction with both lists:
  - Tests that WERE executed (existing repository suites — compilation, Maven, architecture, API contract, CI workflows, security baseline, backup validation, CRM-G1 schema isolation, business process E2E)
  - Tests that were NOT executed (CRM-008 business acceptance, migration, concurrency, tenant-isolation, RBAC, production smoke, Playwright)
- The stage report explicitly states: "The success of existing repository tests MUST NOT be presented as evidence of CRM-008 completion."
- No PR description, commit message, or comment claims CRM-008 completion based on existing repository test success
- The automated check (when implemented in CRM-008B) scans all CRM-008 documents for the required distinction language

**Evidence artifact:** QA Owner sign-off on §2.5 + automated check output (when implemented).

---

## AC-RR-01 — Round-Robin Counter Concurrency (added per Owner Review R2)

**Requirement:** Round-robin counter updates are atomic, concurrency-safe, and produce no lost updates. A repeated user assignment is allowed only after ring wrap or eligibility filtering — NOT as a result of counter corruption.

**Context:** AC-CONC-01 tests concurrent assignment of the same record (one success, one conflict). AC-RR-01 is a **different** test: it tests the round-robin counter itself when N concurrent evaluations target **different records** that share the same assignment rule.

**Test type:** Concurrency test (Testcontainers PostgreSQL 16 + multi-threaded application test)

**Test class (proposed):**
- `apps/sanad-platform/src/test/java/com/sanad/platform/crm/ownership/contract/AcRr01RoundRobinCounterConcurrencyTest.java`

**Pass criterion:**

```text
N concurrent evaluations
→ N atomic counter increments
→ no lost updates
→ each evaluation receives one deterministic sequence position
→ final counter = initial counter + N
→ distribution follows the eligible-member ring
→ repeated user is allowed only after ring wrap or eligibility filtering
```

Specifically:
- N threads (e.g. N=20) concurrently invoke the round-robin assignment rule for N **different** records (no two threads target the same record)
- All N assignments succeed (no conflicts — each record is independent)
- The `crm_assignment_rule_counters` row for this `(tenant_id, rule_id)` has `counter = initial_counter + N` (no lost updates)
- Each of the N assignments received a deterministic, distinct sequence position (1, 2, 3, …, N)
- The distribution of owners follows the eligible-member ring (e.g. with 5 eligible members and N=20, each member receives exactly 4 assignments — modulo ring wrap)
- A repeated user assignment (same user assigned twice) is allowed ONLY after the ring wraps (all eligible members received one assignment) or after eligibility filtering (e.g. a member reached capacity) — NOT as a result of counter corruption
- The test is repeated 10 times to ensure determinism
- The test is repeated with N=1, N=5, N=20, N=100 to verify scaling

**Accepted mechanisms** (per Q5 decision):
- `SELECT ... FOR UPDATE` pessimistic locking on the counter row, OR
- `UPDATE crm_assignment_rule_counters SET counter = counter + 1 WHERE tenant_id = ? AND rule_id = ? RETURNING counter` atomic increment, OR
- Equivalent PostgreSQL atomic mechanism (e.g. `SEQUENCE` with application-layer ring resolution)

**Forbidden mechanisms:**
- ❌ Read-modify-write without locking (lost updates possible)
- ❌ Application-layer counter without DB-level atomicity (race conditions)
- ❌ Counter that resets on conflict (loss of sequence integrity)

**Evidence artifact:** Test log showing N=20 concurrent evaluations → 20 atomic increments → final counter = initial + 20 → distribution follows the 5-member ring (4 each) + repeated runs with N=1/5/20/100 all pass.

---

## Acceptance Gate Summary (updated per EXEC-PROMPT-CRM-008A-R2)

> **Three-gate classification** (per P0-07 correction): criteria are classified against Implementation Merge, Formal Stage Closure, and Commercial Go-Live gates. See `STAGE-REPORT-CRM-008A.md` §5 for the full mapping.

| AC | Test type | Implementation Merge | Formal Stage Closure | Commercial Go-Live |
|---|---|---|---|---|
| AC-01 | Integration + Testcontainers | YES (P0) | YES (P0) | YES (P0) |
| AC-02 | Testcontainers + concurrency | YES (P0) | YES (P0) | YES (P0) |
| AC-03 | Integration + DB role | YES (P0) | YES (P0) | YES (P0) |
| AC-04 | Concurrency | YES (P0) | YES (P0) | YES (P0) |
| AC-05 | Integration | YES (P0) | YES (P0) | YES (P0) |
| AC-06 | Unit + integration | YES (P0) | YES (P0) | YES (P0) |
| AC-07 | Unit + integration | YES (P0) | YES (P0) | YES (P0) |
| AC-08 | Unit + integration | YES (P0) | YES (P0) | YES (P0) |
| AC-09 | Unit + integration | YES (P0) | YES (P0) | YES (P0) |
| AC-10 | Integration | YES (P0) | YES (P0) | YES (P0) |
| AC-11 | Contract (mock) | YES (P0) | YES (P0) | YES (P0) |
| AC-12 | Playwright | NO | YES (P0) | YES (P0) |
| AC-13 | Playwright + axe-core | NO | YES (P0) | YES (P0) |
| AC-14 | Performance | NO | YES (P0) | YES (P0) |
| AC-15 | Production smoke (post-merge) | **NO** | YES (P0) | YES (P0) |
| **AC-DB-01** | **Testcontainers + concurrency (raw JDBC)** | **YES (P0)** | **YES (P0)** | **YES (P0)** |
| **AC-DB-02** | **Testcontainers + Flyway migration** | **YES (P0)** | **YES (P0)** | **YES (P0)** |
| **AC-DB-03** | **Testcontainers + Flyway migration** | **YES (P0)** | **YES (P0)** | **YES (P0)** |
| **AC-CONC-01** | **Concurrency (Testcontainers + multi-threaded app)** | **YES (P0)** | **YES (P0)** | **YES (P0)** |
| **AC-RR-01** | **Concurrency (round-robin counter, N=1/5/20/100)** | **YES (P0)** | **YES (P0)** | **YES (P0)** |
| **AC-TEST-01** | **Documentation review (manual + automated)** | **YES (P0)** | **YES (P0)** | **YES (P0)** |

**Total acceptance criteria: 21** (AC-01 → AC-15 + AC-DB-01/02/03 + AC-CONC-01 + AC-RR-01 + AC-TEST-01)

**Implementation Merge Gate:** 17 P0 criteria must pass (AC-01 → AC-11 + AC-DB-01/02/03 + AC-CONC-01 + AC-RR-01 + AC-TEST-01)
**Formal Stage Closure Gate:** 21 criteria must pass (all 17 above + AC-12 + AC-13 + AC-14 + AC-15)
**Commercial Go-Live Gate:** 21 criteria + owner sign-off + REM-P0-006 independent security assurance
