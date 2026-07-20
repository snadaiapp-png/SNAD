# CRM-008A — Discovery Report

> **EXEC-PROMPT:** CRM-008
> **Stage:** CRM-008A — Discovery and Contract
> **Branch:** `feature/crm-008-design`
> **Base SHA:** `d2f07a8b44905ef91e88aec77afb079b0a9ab79b` (current local main; main HEAD is `700f247340dbb2c8b7591f6507d9b36ec30fd2cc` but cannot be fast-forwarded because user has uncommitted Render-removal changes to workflow files that conflict — these are preserved untouched)
> **Status:** DESIGN ONLY — no implementation, no migration execution
> **Implementation Merge:** BLOCKED per EXEC-PROMPT-CRM-008 §1, pending CRM-007 reconciliation

---

## 1. Inventory of Existing Ownership/Assignment/Transfer Code

### 1.1 Existing `OwnerValidationPort` (port + adapter)

**Port:** `apps/sanad-platform/src/main/java/com/sanad/platform/crm/party/domain/OwnerValidationPort.java`
```java
public interface OwnerValidationPort {
    boolean isValidOwner(UUID tenantId, UUID ownerUserId);
}
```

**Adapter:** `JdbcOwnerValidationAdapter` — single-method check against `users WHERE tenant_id = ? AND id = ? AND status = 'ACTIVE'`.

**Gap vs CRM-008 §4.1:** This port only validates *user existence* in the tenant. It does not model teams, queues, territories, assignment rules, transfer lifecycles, ownership history, or shared/contributor ownership. CRM-008 will require a new `OwnershipPort` (richer contract) — but `OwnerValidationPort` should remain as-is and be delegated to by the new port to avoid breaking existing callers (Account, Contact, Lead, Opportunity, Activity, Task use cases).

### 1.2 Existing `owner_user_id` columns on CRM tables

Migration `V20260702_1__create_unified_crm_core.sql` adds `owner_user_id UUID` (nullable, no FK) to:
- `crm_accounts` — index `idx_crm_accounts_tenant_owner (tenant_id, owner_user_id, lifecycle_status)`
- `crm_contacts` — index `idx_crm_contacts_owner (tenant_id, owner_user_id, lifecycle_status)`
- `crm_leads` — index `idx_crm_leads_tenant_status_owner (tenant_id, status, owner_user_id, updated_at DESC)`
- `crm_opportunities` — index `idx_crm_opportunities_owner (tenant_id, owner_user_id, status, expected_close_date)`
- `crm_activities` — index `idx_crm_activities_owner_due (tenant_id, owner_user_id, status, due_at)`

**Task table** (`V20260716_1__create_crm_tasks.sql`) also has `owner_user_id UUID`.

**Gap vs CRM-008 §4.1:**
- `owner_user_id` is a single-user FK-style column with no FK constraint, no history, no team/queue support, no transfer lifecycle.
- CRM-008 §4.1 requires: "individual user, team, queue, temporary ownership, shared ownership with limited permissions" → cannot fit in a single nullable UUID column.
- CRM-008 must introduce an `Ownership` aggregate that *wraps* `owner_user_id` semantically without removing it: the column becomes the **fast-path primary owner** (read-optimized), and the `Ownership` aggregate becomes the source of truth for assignment history, team linkage, and queue/territory context.

### 1.3 Existing Flyway migrations touching assignment/transfer

Only one migration matches: `V9__create_user_role_assignments.sql` — but this is the **RBAC** user-role assignment table, NOT the CRM ownership assignment table. There is **no** existing `crm_assignments`, `crm_transfers`, `crm_ownership_history`, `crm_teams`, `crm_queues`, or `crm_territories` table in the current Flyway set.

**Confirmed:** CRM-008 starts from a clean slate for the ownership/assignment/transfer/territory/queue/team schema. No existing migrations need to be reconciled.

### 1.4 Existing CRM domain packages

The CRM bounded context under `apps/sanad-platform/src/main/java/com/sanad/platform/crm/` has 23 sub-packages:

```
activity/        configuration/   dto/           export/    idempotency/   integration/
lead/            legacy/           mapper/        note/      opportunity/   pagination/
party/           query/            reports/       search/    tag/           task/
web/             concurrency/      error/
```

**CRM-008 will introduce a new `ownership/` sub-package** with the same hexagonal layout as the existing packages:
```
crm/ownership/
├── domain/            # ports + aggregates + value objects (pure Java)
├── application/       # use cases + module configuration
├── infrastructure/    # JDBC adapters
└── web/               # REST controllers + DTOs
```

### 1.5 Existing RBAC capability catalog

`AccessCapability` table is seeded via V7 + V15 (Java) + V20260717_5 + V20260717_101 migrations. Existing CRM capabilities use the `CRM.<MODULE>.<ACTION>` pattern (e.g. `CRM.ACCOUNT.READ`, `CRM.LEAD.WRITE`). The 17 new capabilities required by CRM-008 §8 follow this pattern.

### 1.6 Existing audit infrastructure

- `platform_audit_logs` table (V17) — generic audit, `result IN ('SUCCESS', 'FAILURE')`.
- `crm_audit_logs` — CRM-specific audit, present in V20260702_1.
- `timeline_events` — CRM timeline projection (`JdbcTimelineEventAdapter`).

**Gap vs CRM-008 §4.2:** The `Assignment` aggregate requires structured fields (record_id, prev_owner, new_owner, reason, rule_id, actor, effective_at, expires_at, correlation_id, workflow_result). The generic `platform_audit_logs` row is not enough — it captures *that* something happened but not the structured *what*. CRM-008 will use:
- The new `crm_ownership_history` table as the **structured** immutable ownership ledger
- The existing `platform_audit_logs` for **correlated** audit events (with `correlation_id` linking the two)
- The existing `timeline_events` projection for customer-facing timeline UI

### 1.7 Existing concurrent-claim infrastructure

- `crm_idempotency_records` (V20260713_1) — for write idempotency
- `ETagService` + `If-Match` header — for optimistic concurrency on CRM v2 endpoints

**Use for CRM-008 §4.5 (Queues Claim/Release):** Concurrent claim on the same queue item will use a `SELECT ... FOR UPDATE` pessimistic lock + the existing `If-Match` ETag flow. Idempotency key on the claim POST ensures safe retry.

---

## 2. Boundary Definitions: HRM / IAM / Workflow / CRM

| Concern | Source of truth | CRM-008 references it as |
|---|---|---|
| User identity & authentication | SaaS Core / IAM (`users` table, JWT) | Foreign-key reference (UUID), never copies identity fields |
| Tenant context | SaaS Core (`tenants` table, JWT claim) | Read from `SecurityContext` via `TenantContextPort` (already exists in `crm/integration/`) |
| Employee organizational data (manager, department, employment status, absence) | HRM (not yet built) | CRM-008 introduces a `HrmPort` port that HRM will eventually implement; until HRM exists, a stub adapter returns "active, no absence" for any user — explicitly marked as a temporary placeholder |
| Workflow approvals, escalations, timers | Central Workflow Engine (not yet built) | CRM-008 introduces a `WorkflowPort` port that the engine will implement; until then, transfer approvals are inline synchronous (manager approves via direct API call) — explicitly marked as a temporary placeholder |
| Notifications (assignment, transfer, SLA breach) | Notification service (Resend integration exists) | CRM-008 publishes structured `AssignmentEvent` / `TransferEvent` records to the existing notification sink; the notification service is the sole delivery channel |
| Analytics | Analytics module (event sink exists) | CRM-008 emits domain events on each assignment/transfer/claim/release; analytics consumes them without CRM-008 owning any analytics queries |

**Critical principle preserved:** CRM-008 does **not** become the source of truth for employees or approvals. It only owns the **CRM operational ownership ledger** and references the other systems via ports.

---

## 3. Domain Model (10 entities — see `domain/01-domain-model.md` for details)

| # | Aggregate | Key invariant |
|---|---|---|
| 1 | `SalesTeam` | One ACTIVE manager per team; manager must be ACTIVE user in same tenant |
| 2 | `TeamMembership` | A user has at most one PRIMARY team per tenant; temporary memberships have expiry |
| 3 | `Queue` | Queue has exactly one record_type; members are explicit (not inferred from team) |
| 4 | `QueueMembership` | A user can be a member of multiple queues; claim capacity is per-queue |
| 5 | `Territory` | Hierarchy is acyclic (enforced at write time via closure-table cycle check) |
| 6 | `TerritoryAssignment` | A team or user can be assigned to multiple territories; coverage rules resolve overlaps |
| 7 | `AssignmentRule` | Versioned; only one ACTIVE version per (tenant, record_type, priority) at a time |
| 8 | `Assignment` | Exactly one ACTIVE assignment per (tenant, record_id) at any instant — enforced by partial unique index |
| 9 | `TransferRequest` | State machine: DRAFT→SUBMITTED→UNDER_REVIEW→{APPROVED\|REJECTED\|CANCELLED}→COMPLETED; only COMPLETED mutates ownership |
| 10 | `OwnershipHistory` | Append-only; no UPDATE or DELETE ever (enforced by revoke of UPDATE/DELETE grants at DB role level) |

---

## 4. OpenAPI Contract Draft (see `contracts/01-openapi-draft.md`)

The contract draft covers 38 endpoints across 7 resource groups:
- `/api/v2/crm/teams` (CRUD + members)
- `/api/v2/crm/queues` (CRUD + members + claim/release)
- `/api/v2/crm/territories` (CRUD + hierarchy + assignments)
- `/api/v2/crm/assignment-rules` (CRUD + simulate + versions)
- `/api/v2/crm/assignments` (current + history + reassign)
- `/api/v2/crm/transfers` (full lifecycle)
- `/api/v2/crm/my-work` (aggregate view for the authenticated user)

All endpoints use the v2 CRM contract pattern (already established by `CrmContractController` and `CrmV2AtomicMutationInfrastructureService`).

---

## 5. RBAC Matrix (see `rbac/01-rbac-matrix.md`)

17 capabilities, mapped to the existing `access_capabilities` catalog pattern. Two new special roles seeded:
- `SALES_MANAGER` — grants `CRM.TEAM.ADMIN`, `CRM.TRANSFER.APPROVE`, `CRM.ASSIGNMENT.ADMIN`, `CRM.ASSIGNMENT_RULE.ADMIN`, `CRM.TERRITORY.ADMIN`, `CRM.QUEUE.ADMIN`
- `SALES_REPRESENTATIVE` — grants `CRM.ASSIGNMENT.WRITE`, `CRM.QUEUE.CLAIM`, `CRM.TRANSFER.REQUEST`, `CRM.OWNERSHIP_HISTORY.READ`

The existing `ADMIN` role auto-receives all 17 capabilities (via the `ensureAdminAllCapabilities` flow already in `CredentialBootstrapService`).

---

## 6. Acceptance Plan (see `tests/01-acceptance-plan.md`)

15 acceptance criteria (AC-01 → AC-15), each with:
- Test name
- Test type (unit / integration / Testcontainers / Playwright / production smoke)
- Test class path (proposed)
- Pass criterion
- Evidence artifact

---

## 7. Migration Plan (see `migrations/01-migration-plan.md`)

**Forward-only Flyway migrations, planned sequence:**

| Version | File | Purpose |
|---|---|---|
| `V20260720_1` | `create_crm_sales_teams.sql` | Teams + team_memberships |
| `V20260720_2` | `create_crm_queues.sql` | Queues + queue_memberships |
| `V20260720_3` | `create_crm_territories.sql` | Territories + closure table + territory_assignments |
| `V20260720_4` | `create_crm_assignment_rules.sql` | Rules + rule_versions |
| `V20260720_5` | `create_crm_assignments.sql` | Assignments (with partial unique index for "one active per record") |
| `V20260720_6` | `create_crm_transfer_requests.sql` | Transfers + transfer_steps |
| `V20260720_7` | `create_crm_ownership_history.sql` | Append-only ledger |
| `V20260720_8` | `seed_crm_ownership_capabilities.sql` | 17 capabilities + 2 new roles + role_capabilities grants |

All migrations:
- Use `IF NOT EXISTS` (forward-only, idempotent re-run safety per CRM-G1 pattern)
- Every table has `tenant_id UUID NOT NULL` as first column
- Every FK is `(tenant_id, parent_id)` composite
- Every index leads with `tenant_id`
- No `DROP` or `TRUNCATE` without explicit ADR

**No migration will be executed in this design phase.** They are committed as `.sql` files for review only.

---

## 8. Branch Strategy

- **Branch:** `feature/crm-008-design` (created)
- **Base SHA:** `d2f07a8b44905ef91e88aec77afb079b0a9ab79b`
- **Target:** `main` (after CRM-007 closure gate is satisfied)
- **No merge to main in this phase**
- **PR will be opened as `WIP: CRM-008A design` for review only**
- **Implementation merge** (separate PR, separate branch `feature/crm-008-implementation`) is blocked until:
  - Issue #563 closed
  - PR #567 merged
  - CRM G1 Production Closure workflow green
  - Formal closure record issued authorizing CRM-008 implementation start

---

## 9. What This Discovery Does NOT Include

- No Java implementation files (only port interfaces as `domain/*.java` — no JDBC adapters, no controllers, no use cases)
- No SQL execution against any database
- No test execution (only test plan documents)
- No frontend changes (frontend design is a separate sub-phase CRM-008E)
- No changes to existing CRM v1 or v2 endpoints
- No changes to existing migrations (only new V20260720_* files)
- No interaction with the active Windows backend or Supabase

---

## 10. Open Questions for Owner Review

1. **`HrmPort` stub behavior:** Until HRM is built, the stub returns "active, no absence" for all users. Is this acceptable for the initial CRM-008B foundation release, or should absence-driven reassignment (CRM-008 §9.4) be deferred entirely to a later phase?

2. **`WorkflowPort` stub behavior:** Until the central Workflow Engine is built, transfer approvals are inline synchronous. Should multi-step approval (chain of managers) be deferred to CRM-008D, with CRM-008B/C supporting only single-approver transfers?

3. **Shared ownership:** §4.1 mentions "shared ownership with limited permissions." Is this in scope for CRM-008B (foundation) or deferred to CRM-008C (assignment engine)? The draft recommends deferring shared ownership to CRM-008C.

4. **Territory overlap policy:** §4.6 requires "uncontrolled overlap" prevention. Should the policy be (a) mutually exclusive territories (DB-level exclusion), or (b) overlap allowed with explicit priority resolution at assignment time? The draft recommends (b) with documented priority.

5. **Round-robin persistence:** Should round-robin state be per-rule (one counter per rule) or per-(rule, tenant) (one counter per rule per tenant)? The draft recommends per-(rule, tenant).

---

## 11. Next Steps (within CRM-008A design phase)

1. ✅ Create design directory structure
2. ✅ Write this discovery report
3. ⏳ Write `domain/01-domain-model.md` (10 aggregates detailed)
4. ⏳ Write `domain/*.java` port interfaces (no impls)
5. ⏳ Write `contracts/01-openapi-draft.md` (38 endpoints)
6. ⏳ Write `rbac/01-rbac-matrix.md` (17 capabilities)
7. ⏳ Write `migrations/01-migration-plan.md` + 8 `.sql` files (review-only, no execution)
8. ⏳ Write `tests/01-acceptance-plan.md` (AC-01 → AC-15)
9. ⏳ Commit + push branch + open WIP PR for review
10. ⏳ Stage report (CRM-008A closure document)
