# Migrator/Runtime Role Separation — Security Design

**Mandate:** SANAD P0 FINAL REMEDIATION executive order, Article 3 (Issue #1462)
**Status:** Implemented by V20261103_1 + V20261103_2 (this PR)
**Evidence:** `tests/security/migrator-separation/run-separation-cert.sh` (35-probe acceptance matrix)

## 1. The problem (proven, not inferred)

Under the previous deployment model the same database identity both ran
Flyway and served the application ("owner-as-runtime"):

* **CI / self-hosted fixture topology:** the restricted role `sanad` owned
  the database, so Flyway-created objects were owned by the runtime role.
  Live reproduction on the exact chain at head `20261102.2` (isolated
  PostgreSQL 17.6, all statements rolled back):

  | Operation as `sanad` (NOSUPERUSER NOBYPASSRLS) | Result |
  |---|---|
  | `ALTER TABLE public.tenants ADD COLUMN ...` | **ALLOWED** (rc=0) |
  | `TRUNCATE TABLE public.subscription_commands` | **ALLOWED** (rc=0) — TRUNCATE is NOT subject to RLS |
  | `UPDATE/DELETE public.flyway_schema_history` | **ALLOWED** (rc=0) — migration-ledger tampering |
  | `CREATE TABLE public.<any>` | **ALLOWED** (rc=0) |
  | Catalog probe | **all 324 public-schema objects owned by `sanad`** |

* **Production (Supabase `tkbrvupemreqabwzdpyq`):** the shared model couples
  Flyway to the bootstrap admin identity; V20261102_1/V20261102_2 documented
  the masked-gap class this produces for split topologies ("the migration
  owner bypasses ACLs via ownership, so CI fixtures running as the migrator
  never observed it").

## 2. Target model

| Property | `sanad_migrator` | `sanad` (runtime) |
|---|---|---|
| Purpose | Flyway migrations only | REST API, workers, background jobs |
| LOGIN | Enabled by the operator; password only in the secret store | Yes |
| SUPERUSER / BYPASSRLS | NO / NO | NO / NO |
| CREATEDB / CREATEROLE | NO / NO | NO / NO |
| Owns schema `public` objects | **YES** (after V20261103_2) | **NO** |
| Member of the other role | — | **NO** (enforced, incl. indirect paths) |
| DDL (ALTER/DROP/CREATE) | Yes (owner) | **DENIED** |
| TRUNCATE | Yes (owner) | **DENIED** (TRUNCATE bypasses RLS) |
| `flyway_schema_history` | Owner, full | **NO privilege at all** |
| DML | Yes (owner) | Exact matrix below, RLS-bound |
| Credentials held by app | — | **Never sees migrator credentials** |

### 2.1 Runtime ACL matrix (V20261103_2)

**Protected set** (exact privileges, preserved from the audited call-site
migrations; the separation makes them finally *enforceable* — under ownership
they were advisory):

| Table | Privileges for `sanad` | Source |
|---|---|---|
| `refresh_tokens` | SELECT, INSERT, UPDATE, DELETE | V20261102_1 |
| `password_reset_tokens` | SELECT, INSERT, UPDATE, DELETE | V20261102_1 |
| `auth_token_tenant_index` | SELECT, INSERT, UPDATE, DELETE | V20261101_1 |
| `login_tenant_index` | SELECT, INSERT, DELETE | V20261031_1 |
| `provisioning_jobs` | SELECT, INSERT, UPDATE | V20261102_2 |
| `provisioning_job_steps` | SELECT, INSERT, UPDATE | V20261102_2 |
| `subscription_commands` | SELECT, INSERT (append-only) | V20261102_2 |
| `tenant_subscriptions` | SELECT, INSERT, UPDATE | **F-01 closure** (DELETE has zero call sites) |

**Excluded entirely:** `flyway_schema_history` (zero privileges — SELECT
included — for the runtime role).

**General set** (every other public table): SELECT, INSERT, UPDATE, DELETE —
the surface the application previously exercised through ownership, minus
TRUNCATE / REFERENCES / TRIGGER / DDL / ownership.

**Sequences:** USAGE, SELECT. **Routines:** EXECUTE (triggers, pre-auth
resolvers, policy helpers — `sanad` is server-side only; the PostgREST RPC
surface is bound to `anon`/`authenticated`/`service_role`, which V20261026_4
already denied).

**Default privileges** (separated topology): future objects created by
`sanad_migrator` automatically grant the runtime matrix to `sanad`.

## 3. Topology model (fail-closed, per-database)

The database-level GUC `sanad.deployment_topology` (set by the provisioning
actor, read by V20261103_2) selects the behaviour:

| Value | Meaning | Ownership transfer | Negative guards |
|---|---|---|---|
| `separated` | Target model (production, separation certification, future CI) | YES — runner must act as `sanad_migrator` or the migration fails closed | Enforced (denials asserted) |
| `migration-fixture` / unset | Disposable migration-mechanics sandboxes (CI `test_migration`, local dev): the runner owns what it creates so `flyway.clean()/migrate()` cycles keep working | NO (loud WARNING emitted) | Positives only (negatives are structurally impossible under ownership — documented skip, same class as V20261102_2) |
| anything else | Unknown | — | **Migration fails closed** |

Declaring `separated` but running as a non-migrator identity **fails the
migration** — the declared intent must be matched by capability.

## 4. Engineering constraints discovered and honored

1. **Flyway history deadlock:** taking an ACCESS EXCLUSIVE lock on
   `flyway_schema_history` *inside* a Flyway migration deadlocks against
   Flyway's own schema-history lock connection (reproduced live: the
   migration blocked indefinitely). V20261103_2 therefore **excludes the
   ledger from all in-migration DDL** and enforces its ownership with a
   dedicated guard instead.
2. **Owned sequences:** PostgreSQL forbids `ALTER SEQUENCE ... OWNER TO` for
   sequences linked to a table column (`pg_depend deptype='a'`); they follow
   their table automatically and are excluded from the transfer loops.
3. **Planner predicate reordering:** privilege probes like
   `has_table_privilege('sanad', 'public.' || name, ...)` can be evaluated
   before the `schemaname='public'` filter and then fail on non-existent
   relations (`public.pg_statistic`, SQLSTATE 42P01 — reproduced live). All
   guards qualify the schema **from the row's own columns**.
4. **Pending-set ownership obstacles (upgrade path):** pending migrations
   ALTER objects created by the previous shared identity; running them as
   `sanad_migrator` without the admin ownership transfer fails closed with
   `must be owner of function ...` (reproduced live). The runbook therefore
   prescribes `scripts/ops/migrator-separation/transfer-ownership-to-migrator.sql`
   **before** the first migrator-run.

## 5. What is deliberately NOT changed

* No RLS policy, ENABLE/FORCE state, or SECURITY DEFINER definition is
  touched (guards assert the invariants instead).
* No test is deleted or weakened; the CI fixture topology keeps the existing
  3,507-test suite semantics, and `TestIsolation` routes cleanup TRUNCATEs
  through a maintenance identity with **behaviour-identical defaults**.
* The legacy `database-migrate-production.yml` workflow remains in the tree
  (superseded by `database-migrate-production-migrator.yml`); no history
  rewrite, no `flyway repair`, no baseline.
