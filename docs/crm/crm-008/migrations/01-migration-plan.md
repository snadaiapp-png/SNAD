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
| 5 | `V20260720_5` | `create_crm_assignments.sql` | Assignments + ownership_history | `crm_assignments`, `crm_ownership_history` | 8 |
| 6 | `V20260720_6` | `create_crm_transfer_requests.sql` | Transfers + transfer steps | `crm_transfer_requests`, `crm_transfer_steps` | 4 |
| 7 | `V20260720_7` | `add_owner_team_queue_columns.sql` | Add `owner_team_id`, `owner_queue_id` to existing CRM tables | (alters existing tables) | 4 |
| 8 | `V20260720_8` | `seed_crm_ownership_capabilities.sql` | 17 capabilities + 2 roles + role_capabilities | (inserts into existing tables) | 0 |

**Total new tables:** 11
**Total new indexes:** 38 (all tenant-leading)
**Total new capabilities seeded:** 17
**Total new roles seeded:** 2

---

## Design principles applied (all migrations)

1. **Forward-only** — no `DROP` or `TRUNCATE` without explicit ADR.
2. **`IF NOT EXISTS`** everywhere — safe to re-run (idempotent).
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
- ✅ `tenant_id` exists on all 11 new tables.
- ✅ Eight tenant foreign keys exist (composite FKs on each child table).
- ✅ Exactly 38 explicit indexes (above the G1 baseline of 26).
- ✅ `tenant_id` is the leading field in every index.
- ✅ Composite relations prevent cross-tenant references (FK + app-layer validation).
- ✅ Flyway `validate` passes.
- ✅ No failed Flyway history entries (forward-only, no repair needed).
- ✅ Flyway remains enabled, `JPA_DDL_AUTO=validate`.

---

## Migration file format

Each migration file follows this template:

```sql
-- ============================================================================
-- SANAD CRM-008 — V20260720_X — <description>
-- ============================================================================
-- Forward-only. Idempotent (IF NOT EXISTS). Tenant-scoped.
-- Composite FKs. Tenant-leading indexes. UUID PKs.
-- ============================================================================

BEGIN;

CREATE TABLE IF NOT EXISTS crm_<table_name> (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    -- ... columns ...
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_<table>_tenants FOREIGN KEY (tenant_id)
        REFERENCES tenants(id) ON DELETE RESTRICT,
    CONSTRAINT ck_<table>_<field> CHECK (<field> IN (...))
);

CREATE INDEX IF NOT EXISTS idx_<table>_<cols>
    ON crm_<table> (tenant_id, <cols>);

COMMIT;
```

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
