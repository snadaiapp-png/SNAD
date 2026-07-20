# CRM-008A — Stage Report (Discovery and Contract)

> **Stage:** CRM-008A — Discovery and Contract
> **Branch:** `feature/crm-008-design`
> **Base SHA:** `d2f07a8b44905ef91e88aec77afb079b0a9ab79b`
> **Status:** DESIGN COMPLETE — ready for owner review
> **Implementation merge:** BLOCKED (pending CRM-007 closure gate)

---

## 1. Stage Objective

Transform CRM from a record-keeping system into a system that institutionally manages: who owns each record, who is currently working on it, how leads are auto-distributed, how ownership transfers between employees and teams, sales team management, queue management, territory organization, and immutable ownership history.

---

## 2. Stage Deliverables (this stage only)

### 2.1 Design documents

| Document | Path | Status |
|---|---|---|
| Discovery Report | `docs/crm/crm-008/00-discovery-report.md` | ✅ Written |
| Domain Model (10 aggregates) | `docs/crm/crm-008/domain/01-domain-model.md` | ✅ Written |
| OpenAPI Contract Draft (38 endpoints) | `docs/crm/crm-008/contracts/01-openapi-draft.md` | ✅ Written |
| RBAC Matrix (17 capabilities + 2 roles) | `docs/crm/crm-008/rbac/01-rbac-matrix.md` | ✅ Written |
| Migration Plan (8 migrations) | `docs/crm/crm-008/migrations/01-migration-plan.md` | ✅ Written |
| Acceptance Plan (AC-01 → AC-15) | `docs/crm/crm-008/tests/01-acceptance-plan.md` | ✅ Written |
| Stage Report (this document) | `docs/crm/crm-008/STAGE-REPORT-CRM-008A.md` | ✅ Written |

### 2.2 Domain port interfaces (Java — no implementations)

| Port | Path | Purpose |
|---|---|---|
| `OwnershipReadPort` | `apps/sanad-platform/src/main/java/com/sanad/platform/crm/ownership/domain/OwnershipReadPort.java` | Read-side port for ownership queries |
| `OwnershipWritePort` | `apps/sanad-platform/src/main/java/com/sanad/platform/crm/ownership/domain/OwnershipWritePort.java` | Write-side port for ownership mutations |
| `WorkflowPort` | `apps/sanad-platform/src/main/java/com/sanad/platform/crm/ownership/domain/WorkflowPort.java` | Port for central Workflow Engine (stub until engine built) |
| `HrmPort` | `apps/sanad-platform/src/main/java/com/sanad/platform/crm/ownership/domain/HrmPort.java` | Port for HRM (stub until HRM built) |

**All ports are pure Java interfaces — no Spring annotations, no JDBC, no implementations.** They compile but do not affect runtime behavior (no bean wired until adapters exist).

### 2.3 Flyway migration files (REVIEW ONLY — NOT EXECUTED)

| File | Purpose |
|---|---|
| `apps/sanad-platform/src/main/resources/db/migration/V20260720_1__create_crm_sales_teams.sql` | Teams + memberships |
| `apps/sanad-platform/src/main/resources/db/migration/V20260720_2__create_crm_queues.sql` | Queues + memberships |
| `apps/sanad-platform/src/main/resources/db/migration/V20260720_3__create_crm_territories.sql` | Territories + closure + assignments |
| `apps/sanad-platform/src/main/resources/db/migration/V20260720_4__create_crm_assignment_rules.sql` | Rules + versions |
| `apps/sanad-platform/src/main/resources/db/migration/V20260720_5__create_crm_assignments.sql` | Assignments + ownership history |
| `apps/sanad-platform/src/main/resources/db/migration/V20260720_6__create_crm_transfer_requests.sql` | Transfers + steps |
| `apps/sanad-platform/src/main/resources/db/migration/V20260720_7__add_owner_team_queue_columns.sql` | Add columns to existing CRM tables |
| `apps/sanad-platform/src/main/resources/db/migration/V20260720_8__seed_crm_ownership_capabilities.sql` | Seed 17 capabilities + audit marker |

**None of these migrations have been executed.** They are committed for review only.

### 2.4 What was NOT done in this stage (intentionally)

- ❌ No JDBC adapters
- ❌ No Spring `@Service` classes
- ❌ No `@RestController` classes
- ❌ No use cases / application services
- ❌ No frontend changes
- ❌ No test execution
- ❌ No migration execution
- ❌ No changes to existing CRM v1 or v2 endpoints
- ❌ No interaction with the active Windows backend or Supabase

---

## 3. Architectural Decisions

### 3.1 Boundary preservation (CRM-008 §5)
- HRM remains the source of truth for employee data → CRM-008 uses `HrmPort` (stub for now)
- SaaS Core/IAM remains the source of truth for user identity → CRM-008 references `users.id` by UUID, never copies identity fields
- Workflow Engine remains central → CRM-008 uses `WorkflowPort` (stub for now)
- CRM owns only the **operational ownership ledger** and team/queue/territory/assignment-rule definitions

### 3.2 Backward compatibility with existing `owner_user_id`
- Existing CRM tables have nullable `owner_user_id UUID` column (no FK)
- CRM-008 adds `owner_team_id` and `owner_queue_id` columns (V20260720_7) — both nullable
- The `Assignment` aggregate becomes the source of truth; the columns on CRM tables become a **denormalized fast-path** for read queries
- Write path: `OwnershipWritePort.assign()` updates both the `crm_assignments` table AND the appropriate column on the CRM record table (in one transaction)
- Read path: existing queries continue to use `owner_user_id`; new queries use `crm_assignments`

### 3.3 OwnerValidationPort preserved
- Existing `OwnerValidationPort` (single method `isValidOwner`) is NOT modified
- CRM-008 introduces a richer `OwnershipReadPort` and `OwnershipWritePort` that delegate to `OwnerValidationPort` for the user-existence check
- All existing callers of `OwnerValidationPort` continue to work unchanged

### 3.4 Ownership history immutability
- `crm_ownership_history` table is created (V20260720_5)
- App layer never issues UPDATE or DELETE on this table
- DB role revocation (separate admin script, NOT a Flyway migration) revokes UPDATE/DELETE from the application DB role
- This enforces immutability even if a future bug tries to mutate history

### 3.5 Concurrency strategy
- Single active assignment per record: partial unique index `WHERE status='ACTIVE'`
- Concurrent queue claims: `SELECT ... FOR UPDATE` pessimistic lock + ETag (`If-Match`) optimistic concurrency
- Bulk transfer atomicity: single DB transaction wrapping all record ownership changes

### 3.6 Workflow integration
- `WorkflowPort` is the only entry point for approval workflows
- Stub returns `isStub()=true` — health checks flag this clearly
- Multi-step approvals are blocked when stub is active (single-approver only until real engine arrives)

---

## 4. Acceptance Criteria Status

All 15 acceptance criteria (AC-01 → AC-15) have:
- A test class path proposed
- A pass criterion defined
- An evidence artifact specified

**None have been executed yet** — that happens in CRM-008F (Verification and Closure) after implementation.

P0 criteria (block implementation merge): AC-01, AC-02, AC-03, AC-04, AC-05, AC-06, AC-07, AC-08, AC-09, AC-10, AC-11
P1 criteria (block commercial go-live, not implementation merge): AC-12, AC-13, AC-14
P0 post-merge: AC-15

---

## 5. Open Questions for Owner Review (5 questions)

1. **`HrmPort` stub behavior** — accept "active, no absence" placeholder until HRM built? (Recommended: YES)
2. **`WorkflowPort` stub behavior** — single-approver inline only until engine built? (Recommended: YES, defer multi-step to CRM-008D)
3. **Shared ownership scope** — defer to CRM-008C? (Recommended: YES)
4. **Territory overlap policy** — overlap allowed with explicit priority? (Recommended: YES)
5. **Round-robin persistence** — per-(rule, tenant) counter? (Recommended: YES)

---

## 6. Implementation Phase Plan (CRM-008B → CRM-008F)

| Sub-phase | Scope | Estimated migrations | Estimated endpoints |
|---|---|---|---|
| CRM-008B (Foundation) | Teams, Memberships, Queues, Territories + DB + indexes | V20260720_1, _2, _3, _7, _8 | 22 endpoints |
| CRM-008C (Assignment Engine) | Rules, Assignments, Ownership History, Manual + auto-assign, Round-robin, Least-loaded, Queue claim | V20260720_4, _5 | 11 endpoints |
| CRM-008D (Transfers) | Transfer lifecycle, Approval integration, Atomic execution, Rollback, Notifications | V20260720_6 | 5 endpoints |
| CRM-008E (Operational UI) | My Work, Teams, Queues, Territories, Rules, Transfers, Record ownership panel | (none — frontend only) | (none) |
| CRM-008F (Verification and Closure) | Full test matrix, Production migration, Two-tenant acceptance, Performance, Security, Stage report, Formal closure | (none — execution only) | (none) |

---

## 7. Branch Strategy

- **Design branch:** `feature/crm-008-design` (this branch)
- **Implementation branches (future):** `feature/crm-008b-foundation`, `feature/crm-008c-assignment-engine`, `feature/crm-008d-transfers`, `feature/crm-008e-operational-ui`, `feature/crm-008f-verification`
- **Target:** `main`
- **Merge gate:** Issue #563 closed, PR #567 merged, CRM G1 Production Closure workflow green, formal authorization issued
- **No merge to main in this design phase**

---

## 8. Risks and Mitigations

| Risk | Mitigation |
|---|---|
| `HrmPort` stub returns wrong absence data in production | Stub is marked `isStub()=true`; health check fails loudly if stub is active in `prod` profile; absence-driven reassignment is disabled until HRM is real |
| `WorkflowPort` stub allows multi-step approvals in production | Stub blocks multi-step explicitly; only single-approver allowed; attempts to create multi-step transfer return 400 with code `multi_step_workflow_not_available` |
| Ownership history mutation by future bug | DB role revocation of UPDATE/DELETE on `crm_ownership_history`; app layer never issues these statements |
| Concurrent claim race condition | `SELECT FOR UPDATE` + ETag + idempotency key — three-layer defense |
| Bulk transfer partial completion | Single DB transaction; failure rolls back all changes including ownership history inserts |
| Cross-tenant leakage via assignment | Composite FKs `(tenant_id, parent_id)` at DB level + tenant-scoping at app layer + 6 tenant isolation tests |

---

## 9. Sign-off

| Role | Status |
|---|---|
| Principal Engineer (designer) | ✅ Self-approved for design quality |
| Product Owner | ⏳ Pending review of this document |
| QA Owner | ⏳ Pending review of acceptance plan |
| Security Owner | ⏳ Pending review of RBAC matrix + tenant isolation tests |
| Implementation merge authorization | ⏳ BLOCKED — pending CRM-007 closure gate |

---

## 10. Next Action

```text
1. Owner reviews this stage report + the 6 design documents.
2. Owner answers the 5 open questions in §5.
3. Owner issues formal authorization to start CRM-008B (Foundation) implementation.
4. CRM-007 closure gate is satisfied (Issue #563 closed, PR #567 merged, CRM G1 Production Closure green).
5. New branch `feature/crm-008b-foundation` is cut from latest `main` HEAD.
6. CRM-008B implementation begins — ports get JDBC adapters, controllers, use cases, and tests.
```

No implementation will start until items 1–4 above are complete.
