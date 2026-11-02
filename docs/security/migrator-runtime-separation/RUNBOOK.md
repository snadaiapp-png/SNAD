# Migrator/Runtime Role Separation — Operator Runbook

**Scope:** production (Supabase), CI, self-hosted deployments.
**References:** `DESIGN.md` (model), V20261103_1/V20261103_2 (chain enforcement).

## 1. Identities

| Role | Where provisioned | LOGIN |
|---|---|---|
| `sanad_migrator` | CI provisioning SQL (auto), docker init `02-create-migrator-role.sh`, Supabase operator (§2) | NOLOGIN until the operator sets a password from the secret store |
| `sanad` (runtime) | already exists (restricted contract enforced by V20261011_3 / V20261103_1) | yes |
| bootstrap admin (`postgres`) | platform | yes — maintenance only |

## 2. Production (Supabase `tkbrvupemreqabwzdpyq`) — one-time separation setup

Execute ONCE, in the Supabase SQL editor (or any admin connection), while
`P0_SECURITY_HOLD=ACTIVE` (no deployment is running):

```sql
-- (a) dedicated migration role (cluster-wide)
DO $do$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'sanad_migrator') THEN
    CREATE ROLE sanad_migrator NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS;
  END IF;
END $do$;

-- (b) full ownership transfer (idempotent, fail-closed verification built in)
--     Run the file scripts/ops/migrator-separation/transfer-ownership-to-migrator.sql
--     (it also transfers schema public and flyway_schema_history).

-- (c) declare the target topology for the sanad database
ALTER DATABASE sanad SET sanad.deployment_topology = 'separated';
```

Then store the migrator credentials **only** in the GitHub secret store:

* `PROD_FLYWAY_MIGRATOR_USER` = `sanad_migrator`
* `PROD_FLYWAY_MIGRATOR_PASSWORD` = a long random secret

and enable LOGIN with that password:

```sql
ALTER ROLE sanad_migrator LOGIN PASSWORD '<the secret from the store>';
```

Finally verify the runtime contract (fail closed if violated):

```sql
SELECT rolname, rolsuper, rolbypassrls, rolcreatedb, rolcreaterole
  FROM pg_roles WHERE rolname IN ('sanad','sanad_migrator');
-- both rows must be f | f | f | f
SELECT pg_has_role('sanad','sanad_migrator','MEMBER');  -- must be f
```

## 3. Running migrations (production)

Use the protected workflow `Database Migration Production (Migrator Identity)`
(`.github/workflows/database-migrate-production-migrator.yml`):

* workflow_dispatch → type `migrate-as-migrator` → approve the
  **Production** environment review;
* runs Flyway **11.7.2** as `sanad_migrator` with
  `validateOnMigrate=true` (no baseline, no repair, no outOfOrder);
* asserts the identity is neither `postgres` nor the runtime role.

The runtime service (`render.yaml`) has `FLYWAY_ENABLED=false`: the backend
never runs migrations and never needs DDL privileges. Deploy order:

1. run the migrator workflow (above);
2. deploy the backend image;
3. G10 verification confirms the live `flyway_schema_history` head and the
   acceptance probes.

## 4. CI

* All CI provisioning blocks now create `sanad_migrator` (NOLOGIN,
  cluster-wide) so V20261103_1 passes in every topology.
* The CI scenario databases keep the documented `migration-fixture` topology
  (owner-as-runner) — required by the migration-mechanics tests
  (`flyway.clean()/migrate()` cycles in `MigrationTestSchemaSupport`
  consumers). V20261103_2 emits a WARNING on that path and it is explicitly
  **not** a separation certification path.
* The separated topology is certified on every PR touching the chain by
  `.github/workflows/migrator-runtime-separation.yml`
  (35-probe acceptance matrix, real connections as both roles).

## 5. Self-hosted (docker compose)

1. `docker compose --profile migrate run --rm flyway-migrate`
   (uses `SANAD_MIGRATOR_PASSWORD`; enable LOGIN inside the container
   entrypoint by setting the password in `.env`);
2. `docker compose up -d backend` — connects as `DATABASE_USERNAME=sanad`
   (restricted runtime identity) with `FLYWAY_ENABLED=false`.

## 6. Verification quick-set (any environment, read-only)

```sql
-- ownership
SELECT pg_get_userbyid(relowner) AS owner, count(*)
  FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
 WHERE n.nspname='public' AND c.relkind IN ('r','p','S','v','m')
 GROUP BY 1;                                    -- expect sanad_migrator only
SELECT pg_get_userbyid(relowner) FROM pg_class
 WHERE oid='public.flyway_schema_history'::regclass;   -- sanad_migrator
-- runtime denials
SELECT has_schema_privilege('sanad','public','CREATE');  -- f
SELECT has_table_privilege('sanad','public.flyway_schema_history','SELECT'); -- f
SELECT has_table_privilege('sanad','public.subscription_commands','UPDATE'); -- f
SELECT pg_has_role('sanad','sanad_migrator','MEMBER');   -- f
```

## 7. Rollback / recovery

* The separation migrations are forward-only; there is no automated
  downgrade (consistent with the chain policy). Recovery of the database
  state uses the certified backup/restore path (G6): `pg_dump` → restore →
  ownership/RLS/tenant-isolation re-verification (proven in the Article 5
  evidence: 29/29 checks).
* If a migrator-run fails mid-chain, PostgreSQL rolls back the migration
  transaction (Flyway 11 + PG transactional DDL — certified in the G5
  failure-injection scenarios); re-run after fixing the root cause. Never
  use `flyway repair` or `baseline` to mask a failure.
