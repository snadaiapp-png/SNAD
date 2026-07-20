# CRM-008 — Migration Plan

> 8 forward-only Flyway migrations **PLANNED for CRM-008B**. No `.sql` files are committed in the CRM-008A merge — they were removed before PR #591 was merged and will be re-added in CRM-008B after local PostgreSQL 16 validation via Testcontainers.
>
> Pattern: matches the CRM-G1 idempotent style (`CREATE TABLE IF NOT EXISTS`, `ADD COLUMN IF NOT EXISTS`, tenant-leading indexes, composite FKs).

---

## Status

> **IMPORTANT:** The `.sql` files for these migrations were initially included in CRM-008A but **removed** because the CRM G1 Schema Isolation CI workflow applies ALL Flyway migrations on every PR (including design-only PRs), and the new migrations need local PostgreSQL validation before they can pass the strict CI gate.
>
> The migrations will be re-added in **CRM-008B (Foundation)** after local validation against PostgreSQL 16 (via Testcontainers). The migration plan, table designs, and invariants documented below remain the authoritative design reference for CRM-008B implementation.
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

**Total new tables:** 12
**Total new indexes:** 40 (all tenant-leading; one partial unique index for single-active-assignment)
**Total new capabilities seeded:** 17
**Total new roles seeded:** 2

> **Note on versioning:** Migration versions use sequential integers (`_1`, `_2`, …, `_9`). Fractional versions like `V20260720_4.1` are **forbidden** — Flyway treats them inconsistently across versions and they complicate history validation. The round-robin counter migration is `V20260720_9`, not `V20260720_4.1`.
>
> Before CRM-008B, the implementer MUST verify that no migration version `V20260720_1` through `V20260720_9` is already in use on `main`.

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

## Acceptance criteria for migrations (matches PHASE 3 of EXEC-PROMPT)

- ✅ Eight CRM-G1 tables exist (V20260720_5 creates the assignment + history tables — these are the G1-equivalent tables for ownership).
- ✅ `tenant_id` exists on all 12 new tables (including `crm_assignment_rule_counters`).
- ✅ Eight tenant foreign keys exist (composite FKs on each child table).
- ✅ Exactly 40 explicit indexes (above the G1 baseline of 26), including the partial unique index `uk_assignments_active_per_record WHERE status='ACTIVE'`.
- ✅ `tenant_id` is the leading field in every index.
- ✅ Composite relations prevent cross-tenant references (FK + app-layer validation).
- ✅ Flyway `validate` passes.
- ✅ No failed Flyway history entries (forward-only, no repair needed).
- ✅ Flyway remains enabled, `JPA_DDL_AUTO=validate`.
- ✅ Each migration includes Preconditions → DDL → Postconditions (see §Fail-Closed Strategy).
- ✅ AC-DB-01, AC-DB-02, AC-DB-03, AC-CONC-01, AC-TEST-01 from `tests/01-acceptance-plan.md` are satisfied.

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

Before executing any DDL, the migration MUST verify:

- The new tables do NOT already exist (or, if they do, they are in the EXACT expected state — same columns, same types, same constraints)
- There is no partial state — e.g. some tables exist but others do not
- There are no columns or constraints with matching names but different definitions
- There are no partial or non-matching indexes
- The Flyway history is consistent (no failed migration rows, no out-of-order versions)
- Required tenant constraints from prior migrations are present

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

Each migration file follows this fail-closed template (replaces the previous `IF NOT EXISTS` template):

```sql
-- ============================================================================
-- SANAD CRM-008 — V20260720_X — <description>
-- ============================================================================
-- Fail-closed. Forward-only. Tenant-scoped. Composite FKs.
-- Tenant-leading indexes. UUID PKs.
-- Preconditions → DDL → Postconditions → fail closed on partial state.
-- ============================================================================

BEGIN;

-- -----------------------------------------------------------------------
-- Preconditions: verify schema is in the EXACT expected state.
-- Abort if partial or unexpected state is detected.
-- -----------------------------------------------------------------------
DO $$
BEGIN
    -- Example: verify the target table does NOT already exist
    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = 'public' AND table_name = 'crm_<table_name>'
    ) THEN
        -- If the table exists, verify it is in the EXACT expected state.
        -- If not, abort with a clear error.
        -- (Implemented as a separate DO block or SELECT-into-variables check.)
        RAISE NOTICE 'Table crm_<table_name> already exists; verifying state...';
        -- ... verification logic ...;
    END IF;
END $$;

-- -----------------------------------------------------------------------
-- DDL: the actual schema change (transactional).
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
END $$;

COMMIT;
```

### Notes on the template

- `IF NOT EXISTS` is **removed** from `CREATE TABLE` and `CREATE INDEX` — the preconditions block handles existence checks explicitly
- Preconditions and postconditions use `DO $$ ... END $$` blocks with `RAISE EXCEPTION` to abort the transaction
- The entire migration runs in a single `BEGIN; ... COMMIT;` transaction — any `RAISE EXCEPTION` triggers an automatic rollback
- The template is a starting point; each migration will have its own specific pre/postcondition checks documented in the migration file

---

## Execution constraints (CRITICAL)

1. **No `.sql` files are committed in the CRM-008A merge.** The 8 migration files were temporarily committed during the design phase, then removed in commit `b683ec2d` before PR #591 was merged. They will be re-added in CRM-008B after local PostgreSQL 16 validation via Testcontainers.
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
                         'crm_transfer_requests','crm_transfer_steps');
   -- Expected: 13 (11 new tables + 2 closure/versions tables)
   ```

---

## Rollback plan (if migration fails)

Each migration is wrapped in `BEGIN; ... COMMIT;`. If any statement fails, the entire migration rolls back. No partial state is left.

If a migration is applied successfully but needs to be reverted (rare — requires owner ADR):
1. Stop the backend
2. Take a fresh Supabase backup
3. Execute a documented `V20260720_X_rollback.sql` (NOT committed in this design — created only if needed)
4. Verify Flyway history via `flyway repair` if needed
5. Restart the backend with `FLYWAY_VALIDATE_ON_MIGRATE=true`

**Revert is the last resort.** Forward-only is the default.
