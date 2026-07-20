# CRM-008 — Migration Plan

> Nine migrations are planned for CRM-008B (V20260720_1 through V20260720_9).
>
> Eight executable draft files (V20260720_1 through V20260720_8) were temporarily committed during CRM-008A and removed before PR #591 merged.
>
> V20260720_9 was introduced later as a design-only planned migration during Owner Review R2 and has never been committed as an executable SQL file.
>
> No `.sql` files are committed in the CRM-008A merge. All nine migrations will be re-added in CRM-008B after local PostgreSQL 16 validation via Testcontainers.
>
> Pattern:
> Fail-closed PostgreSQL migrations with explicit preconditions,
> transactional DDL, and verified postconditions.
> (The previous CRM-G1 `IF NOT EXISTS` idempotent pattern is deprecated for CRM-008B — see §Fail-Closed Strategy.)

---

## Status

> **IMPORTANT:** Eight executable draft files (V20260720_1 through V20260720_8) were temporarily committed during CRM-008A and removed in commit `b683ec2d` before PR #591 merged. V20260720_9 was introduced later as a design-only planned migration during Owner Review R2 and has **never** been committed as an executable SQL file. The removal was necessary because the CRM G1 Schema Isolation CI workflow applies ALL Flyway migrations on every PR (including design-only PRs), and the new migrations need local PostgreSQL validation before they can pass the strict CI gate.
>
> All nine migrations will be re-added in **CRM-008B (Foundation)** after local validation against PostgreSQL 16 (via Testcontainers). The migration plan, table designs, and invariants documented below remain the authoritative design reference for CRM-008B implementation.
>
> The CRM-008A PR is intentionally design-only: documents + pure-domain port interfaces. No SQL migrations are executed.

## Migration Sequence

| # | Version | File | Purpose | Tables added | Indexes added |
|---|---|---|---|---|---|
| 1 | `V20260720_1` | `create_crm_sales_teams.sql` | Sales teams + memberships | `crm_sales_teams`, `crm_team_memberships` | 6 |
| 2 | `V20260720_2` | `create_crm_queues.sql` | Queues + queue memberships | `crm_queues`, `crm_queue_memberships` | 4 |
| 3 | `V20260720_3` | `create_crm_territories.sql` | Territories + closure table + assignments | `crm_territories`, `crm_territory_closure`, `crm_territory_assignments` | 7 |
| 4 | `V20260720_4` | `create_crm_assignment_rules.sql` | Rules + rule versions | `crm_assignment_rules`, `crm_assignment_rule_versions` | 5 |
| 5 | `V20260720_5` | `create_crm_assignments.sql` | Assignments + ownership_history | `crm_assignments`, `crm_ownership_history` | 8 (including **partial unique index** `WHERE status='ACTIVE'` for single-active-assignment invariant) |
| 6 | `V20260720_6` | `create_crm_transfer_requests.sql` | Transfers + transfer steps | `crm_transfer_requests`, `crm_transfer_steps` | 4 |
| 7 | `V20260720_7` | `add_owner_team_queue_columns.sql` | Add `owner_team_id`, `owner_queue_id` to existing CRM tables | (alters existing tables) | 4 |
| 8 | `V20260720_8` | `seed_crm_ownership_capabilities.sql` | 17 capabilities + 2 roles + role_capabilities | (inserts into existing tables) | 0 |
| 9 | `V20260720_9` | `create_crm_assignment_rule_counters.sql` | Round-robin counter table (per `tenant_id + assignment_rule_id`) | `crm_assignment_rule_counters` | 2 |

**Total new tables:** 14
  - V20260720_1: 2 (`crm_sales_teams`, `crm_team_memberships`)
  - V20260720_2: 2 (`crm_queues`, `crm_queue_memberships`)
  - V20260720_3: 3 (`crm_territories`, `crm_territory_closure`, `crm_territory_assignments`)
  - V20260720_4: 2 (`crm_assignment_rules`, `crm_assignment_rule_versions`)
  - V20260720_5: 2 (`crm_assignments`, `crm_ownership_history`)
  - V20260720_6: 2 (`crm_transfer_requests`, `crm_transfer_steps`)
  - V20260720_7: 0 (alters existing CRM tables — adds columns only)
  - V20260720_8: 0 (inserts into existing tables — no new tables)
  - V20260720_9: 1 (`crm_assignment_rule_counters`)
  - **Total: 2+2+3+2+2+2+0+0+1 = 14**

**Total new indexes:** 40 (all tenant-leading; one partial unique index `uk_assignments_active_per_record WHERE status='ACTIVE'` for single-active-assignment)
**Total new capabilities seeded:** 17
**Total new roles seeded:** 2

> **Note on versioning:** Migration versions use sequential integers (`_1`, `_2`, …, `_9`). Fractional versions like `V20260720_4.1` are **prohibited by SANAD migration governance** — see §Versioning Policy below. The round-robin counter migration is `V20260720_9`, not `V20260720_4.1`.
>
> Before CRM-008B, the implementer MUST verify that no migration version `V20260720_1` through `V20260720_9` is already in use on `main`.

---

## Versioning Policy (P1-01 correction per Owner Review R2)

```text
SANAD migration governance requires sequential integer versions
for deterministic ordering, audit clarity, and simplified
flyway_schema_history reconciliation.

Fractional migration versions are prohibited by project policy.
```

**Rationale:** Sequential integer versions provide deterministic ordering, audit clarity, and simplified `flyway_schema_history` reconciliation. Fractional versions are prohibited by project policy regardless of any Flyway engine behavior.

**Enforcement:** Any migration file with a fractional version (e.g. `V20260720_4.1`) MUST be rejected at code review. The migration version MUST be the next available sequential integer after the latest applied migration on `main`.

---

## Design principles applied (all migrations)

1. **Forward-only** — no `DROP` or `TRUNCATE` without explicit ADR.
2. **Fail-closed strategy** (replaces `IF NOT EXISTS`) — see §Fail-Closed Strategy below. `IF NOT EXISTS` is **deprecated** for CRM-008B because it can silently mask partial schema states.
3. **`tenant_id UUID NOT NULL`** as first column on every tenant-owned table.
4. **Composite FKs** `(tenant_id, parent_id)` — prevents cross-tenant references at DB level.
5. **Named FK constraints** `fk_<child>_<parent>` (CONSTITUTION §3.2).
6. **UUID PKs** (CONSTITUTION §3.2 — no BIGSERIAL).
7. **Tenant-leading indexes** — `tenant_id` is always the first index column.
8. **Partial unique indexes** for status-only-uniqueness (e.g. one ACTIVE assignment per record).
9. **CHECK constraints** for enums (e.g. `status IN ('ACTIVE','SUSPENDED','ARCHIVED')`).
10. **No FK to `users`** for owner columns — validated in app (polymorphic: user OR team OR queue).

---

## Planned CRM-008B Migration Acceptance Requirements

> **IMPORTANT (per Owner Review R2 P0-03):** The requirements below are **PLANNED** for CRM-008B. They have **NOT** been executed or verified in CRM-008A. No CRM-008 migration files exist on `main`. The ✅ markers below indicate **design completeness** (the requirement is documented and ready for implementation), NOT execution success.

### Existing baseline (already verified on `main` — NOT a CRM-008 deliverable)

- ✅ Eight CRM-G1 tables exist on `main` from prior migrations (verified by the `Verify 8 tables, 26 indexes, and tenant isolation` CI check). This is the **existing CRM-G1 baseline** and is NOT evidence of CRM-008 completion.

### Planned CRM-008B requirements (NOT YET EXECUTED)

- ⏳ `tenant_id` exists on all 14 new tables — **PLANNED / NOT_EXECUTED**
- ⏳ Tenant foreign keys (composite FKs `(tenant_id, parent_id)`) — explicit inventory of 21 constraints — **PLANNED / NOT_EXECUTED**:
  1. `fk_team_memberships_tenants` — `crm_team_memberships.tenant_id` → `tenants(id)`
  2. `fk_team_memberships_sales_teams` — `crm_team_memberships.(tenant_id, team_id)` → `crm_sales_teams.(tenant_id, id)`
  3. `fk_queue_memberships_tenants` — `crm_queue_memberships.tenant_id` → `tenants(id)`
  4. `fk_queue_memberships_queues` — `crm_queue_memberships.(tenant_id, queue_id)` → `crm_queues.(tenant_id, id)`
  5. `fk_territories_tenants` — `crm_territories.tenant_id` → `tenants(id)`
  6. `fk_territories_parent` — `crm_territories.(tenant_id, parent_id)` → `crm_territories.(tenant_id, id)`
  7. `fk_territory_closure_ancestor` — `crm_territory_closure.(tenant_id, ancestor_id)` → `crm_territories.(tenant_id, id)`
  8. `fk_territory_closure_descendant` — `crm_territory_closure.(tenant_id, descendant_id)` → `crm_territories.(tenant_id, id)`
  9. `fk_territory_assignments_tenants` — `crm_territory_assignments.tenant_id` → `tenants(id)`
  10. `fk_territory_assignments_territories` — `crm_territory_assignments.(tenant_id, territory_id)` → `crm_territories.(tenant_id, id)`
  11. `fk_assignment_rules_tenants` — `crm_assignment_rules.tenant_id` → `tenants(id)`
  12. `fk_rule_versions_tenants` — `crm_assignment_rule_versions.tenant_id` → `tenants(id)`
  13. `fk_rule_versions_rules` — `crm_assignment_rule_versions.(tenant_id, rule_id)` → `crm_assignment_rules.(tenant_id, id)`
  14. `fk_assignments_tenants` — `crm_assignments.tenant_id` → `tenants(id)`
  15. `fk_assignments_rules` — `crm_assignments.(tenant_id, assigned_by_rule_id)` → `crm_assignment_rules.(tenant_id, id)`
  16. `fk_ownership_history_tenants` — `crm_ownership_history.tenant_id` → `tenants(id)`
  17. `fk_transfer_requests_tenants` — `crm_transfer_requests.tenant_id` → `tenants(id)`
  18. `fk_transfer_steps_tenants` — `crm_transfer_steps.tenant_id` → `tenants(id)`
  19. `fk_transfer_steps_requests` — `crm_transfer_steps.(tenant_id, transfer_request_id)` → `crm_transfer_requests.(tenant_id, id)`
  20. `fk_assignment_rule_counters_tenants` — `crm_assignment_rule_counters.tenant_id` → `tenants(id)` (V20260720_9)
  21. `fk_assignment_rule_counters_rules` — `crm_assignment_rule_counters.(tenant_id, rule_id)` → `crm_assignment_rules.(tenant_id, id)` (V20260720_9)
  - **Total: 21 composite/tenant FK constraints planned** (covering both `tenant_id → tenants(id)` single-column FKs and `(tenant_id, parent_id)` composite FKs)
- ⏳ Exactly 40 explicit indexes (above the G1 baseline of 26), including the partial unique index `uk_assignments_active_per_record WHERE status='ACTIVE'` — **PLANNED / NOT_EXECUTED**
- ⏳ `tenant_id` is the leading field in every index — **PLANNED / NOT_EXECUTED**
- ⏳ Composite relations prevent cross-tenant references (FK + app-layer validation) — **PLANNED / NOT_EXECUTED**
- ⏳ Flyway `validate` passes — **PLANNED / NOT_EXECUTED** (will be verified in CRM-008B)
- ⏳ No failed Flyway history entries (forward-only, no repair needed) — **PLANNED / NOT_EXECUTED**
- ⏳ Flyway remains enabled, `JPA_DDL_AUTO=validate` — **PLANNED / NOT_EXECUTED** (already the production setting; CRM-008B must not change this)
- ⏳ Each migration includes Preconditions → DDL → Postconditions (see §Fail-Closed Strategy) — **PLANNED / NOT_EXECUTED**
- ⏳ AC-DB-01, AC-DB-02, AC-DB-03, AC-CONC-01, AC-RR-01, AC-TEST-01 from `tests/01-acceptance-plan.md` are satisfied — **PLANNED / NOT_EXECUTED**

---

## Fail-Closed Strategy (added per EXEC-PROMPT-CRM-008A-R2)

CRM-008B migrations MUST NOT rely on `IF NOT EXISTS` to hide partial or unexpected schema state. The previous CRM-008A draft used `IF NOT EXISTS` extensively for idempotency; this is **insufficient** for production migrations because it can silently mask incomplete schema states (e.g. a table that exists but is missing columns, or a constraint that exists but has the wrong definition).

### Pattern

Each CRM-008B migration MUST follow this pattern:

```text
Preconditions
  → Exact expected state validation
  → Transactional schema change
  → Postconditions
  → Fail closed on partial or unexpected state
```

### Preconditions (before any DDL)

Preconditions are **split by migration type** because Create, Alter, and Seed migrations have fundamentally different state requirements. A single generic "if the table exists and is exact, continue to CREATE TABLE" rule is contradictory (the unconditional `CREATE TABLE` would still fail) and is **forbidden**.

#### Create Migration (V20260720_1, _2, _3, _4, _5, _6, _9)

```text
All target objects MUST be absent.
If any target object already exists, abort with MIGRATION_ABORTED / SCHEMA_PARTIAL_OR_UNEXPECTED.
A separate reconciliation migration is required to repair the state.
```

Precondition checks:
- None of the target tables exist in `information_schema.tables`
- None of the target indexes exist in `pg_indexes`
- None of the target constraints exist in `information_schema.table_constraints`
- There is no partial state — e.g. some target tables exist but others do not
- There are no columns or constraints with matching names but different definitions on any pre-existing object
- The Flyway history is consistent (no failed migration rows, no out-of-order versions)
- Required tenant constraints from prior migrations are present (e.g. `tenants(id)` must exist)

#### Alter Migration (V20260720_7)

```text
The exact predecessor schema MUST exist.
Any missing or different predecessor object aborts the migration.
```

Precondition checks:
- The target table (e.g. `crm_accounts`) exists with the EXACT expected predecessor schema (existing columns, types, constraints)
- The columns being added (`owner_team_id`, `owner_queue_id`) do NOT already exist on the target table
- The indexes being created do NOT already exist
- If the columns already exist with a different type, abort — do NOT silently alter them
- The Flyway history is consistent

#### Seed Migration (V20260720_8)

```text
The required capability/role baseline MUST exist.
Duplicate or conflicting semantic records fail closed.
Approved idempotent UPSERT behavior must be explicitly documented.
```

Precondition checks:
- The `access_capabilities` table exists with the expected schema (from V7 + V15 + V20260717_5 + V20260717_101)
- The `platform_audit_logs` table exists (from V17)
- None of the 17 new capability codes (`CRM.ASSIGNMENT.READ`, etc.) already exist with a different definition
- If a capability code already exists with the same definition, the seed MUST skip it (idempotent UPSERT, explicitly documented in the migration)
- If a capability code already exists with a DIFFERENT definition, abort — do NOT silently overwrite

#### Common precondition rules (all migration types)

- There is no partial state across multiple objects in the same migration
- There are no columns or constraints with matching names but different definitions
- The Flyway history is consistent (no failed migration rows, no out-of-order versions)
- Required tenant constraints from prior migrations are present

#### Failure handling

If any precondition fails, the migration MUST abort with:

```text
MIGRATION_ABORTED
SCHEMA_PARTIAL_OR_UNEXPECTED
NO_SILENT_REPAIR
```

A separate, approved reconciliation migration is required to repair the state — the migration itself never auto-repairs. **No `flyway repair`, no manual edits to `flyway_schema_history`, no out-of-order execution.**

### Transaction requirements

- Use a single PostgreSQL transaction per migration whenever PostgreSQL allows it (DDL is transactional in PostgreSQL)
- Any failure MUST trigger a full rollback — no partial state is left
- No destructive operations (`DROP TABLE`, `DROP COLUMN`, `TRUNCATE`) without an explicit ADR
- No `flyway repair`
- No manual edits to `flyway_schema_history`
- No out-of-order Flyway execution
- No `CREATE INDEX CONCURRENTLY` inside a transactional migration (it cannot run in a transaction; use a separate non-transactional migration if concurrency is required)

### Postconditions (after DDL, before commit)

After the DDL, the migration MUST verify (via `SELECT` against `information_schema` / `pg_catalog`):

- Expected tables exist with expected names
- Expected columns exist with expected types and nullability
- Primary keys match the design
- Unique constraints match the design
- Foreign keys (including same-tenant composite FKs) match the design
- Check constraints match the design
- Tenant-leading indexes match the design
- Partial unique indexes match the design (e.g. `WHERE status = 'ACTIVE'`)
- No unexpected additional objects (tables, columns, indexes, constraints) were created

If any postcondition fails, the transaction MUST roll back.

### Single Active Assignment — partial unique index

The `crm_assignments` table MUST have a partial unique index:

```sql
CREATE UNIQUE INDEX uk_assignments_active_per_record
    ON crm_assignments (tenant_id, record_type, record_id)
    WHERE status = 'ACTIVE';
```

This index is the **database-level enforcement** of the single-active-assignment invariant (see STAGE-REPORT-CRM-008A.md §4.7). The previous failure that removed this index is classified as `MIGRATION_DESIGN_OR_SCHEMA_STATE_DEFECT` — NOT a PostgreSQL or Flyway limitation. CRM-008B MUST root-cause the previous failure and re-introduce the index with the fail-closed pattern above.

---

## Migration file format (fail-closed template)

Each migration file follows this fail-closed template (replaces the previous `IF NOT EXISTS` template). The template below is for a **Create Migration** — Alter and Seed migrations have different precondition rules (see §Preconditions above).

```sql
-- ============================================================================
-- SANAD CRM-008 — V20260720_X — <description>
-- ============================================================================
-- Fail-closed. Forward-only. Tenant-scoped. Composite FKs.
-- Tenant-leading indexes. UUID PKs.
-- Preconditions → DDL → Postconditions → fail closed on partial state.
-- Migration type: CREATE (target objects MUST be absent)
-- ============================================================================

BEGIN;

-- -----------------------------------------------------------------------
-- Preconditions (CREATE migration): verify all target objects are ABSENT.
-- If any target object already exists, abort with SCHEMA_PARTIAL_OR_UNEXPECTED.
-- A separate reconciliation migration is required to repair the state.
-- -----------------------------------------------------------------------
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = 'public' AND table_name = 'crm_<table_name>'
    ) THEN
        RAISE EXCEPTION 'MIGRATION_ABORTED / SCHEMA_PARTIAL_OR_UNEXPECTED: table crm_<table_name> already exists. Use a reconciliation migration to repair state.';
    END IF;
    -- Repeat for each target table, index, and constraint in this migration.
    -- Verify no partial state: if any sibling target exists, abort.
END $$;

-- -----------------------------------------------------------------------
-- DDL: the actual schema change (transactional).
-- No IF NOT EXISTS — preconditions already guaranteed absence.
-- -----------------------------------------------------------------------
CREATE TABLE crm_<table_name> (
    id              UUID NOT NULL DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    -- ... columns ...
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_<table> PRIMARY KEY (id),
    CONSTRAINT uk_<table>_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_<table>_tenants FOREIGN KEY (tenant_id)
        REFERENCES tenants(id) ON DELETE RESTRICT,
    CONSTRAINT ck_<table>_<field> CHECK (<field> IN (...))
);

CREATE INDEX idx_<table>_<cols>
    ON crm_<table> (tenant_id, <cols>);

-- -----------------------------------------------------------------------
-- Postconditions: verify the DDL produced the EXACT expected schema.
-- Abort (rollback) if any postcondition fails.
-- -----------------------------------------------------------------------
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = 'public' AND table_name = 'crm_<table_name>'
    ) THEN
        RAISE EXCEPTION 'Postcondition failed: crm_<table_name> not created';
    END IF;
    -- ... additional postcondition checks for columns, constraints, indexes ...
    -- Verify expected columns, types, nullability, PK, UK, FK, CHECK, indexes.
END $$;

COMMIT;
```

### Notes on the template

- `IF NOT EXISTS` is **removed** from `CREATE TABLE` and `CREATE INDEX` — the preconditions block handles absence checks explicitly and aborts if the object already exists
- For **Create Migrations**: target objects MUST be absent. If any exists, abort.
- For **Alter Migrations**: the exact predecessor schema MUST exist. Verify predecessor columns/types/constraints before altering.
- For **Seed Migrations**: the required baseline tables MUST exist. Use explicitly documented idempotent UPSERT for same-definition records; abort on different-definition conflicts.
- Preconditions and postconditions use `DO $$ ... END $$` blocks with `RAISE EXCEPTION` to abort the transaction
- The entire migration runs in a single `BEGIN; ... COMMIT;` transaction — any `RAISE EXCEPTION` triggers an automatic rollback
- The template is a starting point; each migration will have its own specific pre/postcondition checks documented in the migration file

---

## Execution constraints (CRITICAL)

1. **No `.sql` files are committed in the CRM-008A merge.** Eight migration files (`V20260720_1` through `V20260720_8`) were temporarily committed during CRM-008A and removed in commit `b683ec2d` before PR #591 was merged. `V20260720_9` was introduced later as a planned migration during Owner Review R2 and has **never** been committed as an executable SQL file. All nine migrations will be re-added in CRM-008B after local PostgreSQL 16 validation via Testcontainers.
2. **Execution happens in CRM-008B** (Foundation phase) — after:
   - CRM-007 closure gate satisfied
   - Issue #563 closed
   - PR #567 merged
   - Formal authorization to start CRM-008 implementation
3. **Execution against production Supabase** requires:
   - Verified backup (Supabase dashboard → scheduled backup)
   - Owner sign-off
   - Runbook execution on Windows machine (the only machine with backend + DB access)
4. **Post-execution validation** (matches PHASE 3 acceptance):
   ```sql
   SELECT count(*) FROM information_schema.tables
    WHERE table_name IN ('crm_sales_teams','crm_team_memberships',
                         'crm_queues','crm_queue_memberships',
                         'crm_territories','crm_territory_closure','crm_territory_assignments',
                         'crm_assignment_rules','crm_assignment_rule_versions',
                         'crm_assignments','crm_ownership_history',
                         'crm_transfer_requests','crm_transfer_steps',
                         'crm_assignment_rule_counters');
   -- Expected: 14 (the 14 new tables listed in the Migration Sequence above)
   ```

---

## Rollback policy (per Owner Review R2 P0-04)

**This section is a forward-only rollback POLICY, not a procedure.** Ad-hoc rollback migrations are prohibited by default.

### Failed-before-commit (transaction failure)

```text
Transaction rollback only.
No flyway repair.
No manual flyway_schema_history edit.
No ad-hoc rollback migration.
```

Each migration is wrapped in `BEGIN; ... COMMIT;`. If any statement fails (including any precondition or postcondition `RAISE EXCEPTION`), the entire transaction rolls back automatically. No partial state is left. The Flyway history will contain a single `success` row only if the migration committed; a failed migration leaves no Flyway history row.

### Successfully-applied migration (forward-only)

```text
Forward-only corrective migration or approved reconciliation migration.
NO_FLYWAY_REPAIR.
NO_MANUAL_FLYWAY_HISTORY_EDIT.
NO AD-HOC ROLLBACK MIGRATION NAMING.
```

If a migration was applied successfully but later needs correction (e.g. a column has the wrong type, a constraint is missing), the remedy is a **new forward-only corrective migration** with the next sequential version number (e.g. `V20260720_10__correct_<description>.sql`). This corrective migration follows the same fail-closed pattern (Preconditions → DDL → Postconditions).

### Exceptional reverse operations (rare — requires ADR + independent runbook)

Any operation that reverses a successfully-applied migration (e.g. `DROP TABLE`, `DROP COLUMN`, `TRUNCATE`, manual `flyway_schema_history` edit) requires:

1. An explicit Architecture Decision Record (ADR) approved by the Project Owner
2. An independent rollback runbook (separate from this migration plan)
3. A verified fresh backup taken immediately before the operation
4. Owner sign-off on the specific operation

**Ad-hoc rollback migration naming like `V20260720_X_rollback.sql` is prohibited.** Reverse operations are never the default; they are exceptional and require their own governance.

### Summary

```text
FAILED_BEFORE_COMMIT:
  Transaction rollback only.

SUCCESSFULLY_APPLIED_MIGRATION:
  Forward-only corrective migration or approved reconciliation migration.

NO_FLYWAY_REPAIR.
NO_MANUAL_FLYWAY_HISTORY_EDIT.
FORWARD_ONLY.
NO AD-HOC ROLLBACK MIGRATION NAMING.
```
