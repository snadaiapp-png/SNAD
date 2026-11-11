# Migrator/Runtime Role Separation — Operator Runbook

**Scope:** production (Supabase), CI, self-hosted deployments.
**References:** `DESIGN.md` (model), V20261103_1/V20261103_2 (chain enforcement).

## 1. Identities

| Role | Where provisioned | LOGIN |
|---|---|---|
| `sanad_migrator` | CI provisioning SQL (auto), docker init `02-create-migrator-role.sh`, Supabase operator (§2) | NOLOGIN until the operator sets a password from the secret store |
| `sanad` (runtime) | already exists (restricted contract enforced by V20261011_3 / V20261103_1) | yes |
| `sanad_callback_replay_cleanup` | CI provisioning SQL (auto), docker init `02-create-migrator-role.sh`, Supabase operator (§2c) — dedicated expired-row cleanup role for `service_callback_replay` (V20261111_1) | NOLOGIN (structural — never gets a password) |
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

-- (b-pre) Supabase-specific bootstrap order (empirically verified on the
--     isolated DR project 2026-11-11; the GitHub-runner rehearsal runs as
--     superuser and does not exercise these platform constraints):
--
--     The Supabase SQL-editor identity (`postgres`) is NOSUPERUSER (CREATEROLE,
--     database owner; schema public is owned by `pg_database_owner`), so BEFORE
--     any ALTER ... OWNER TO sanad_migrator can succeed it needs:
GRANT sanad_migrator TO postgres;                       -- membership for ALTER OWNER
GRANT USAGE, CREATE ON SCHEMA public TO sanad_migrator; -- new owner needs CREATE to receive tables
--     (CREATE becomes implicit once the schema ownership transfers to the
--      migrator; the grant is idempotent and only bridges the pre-transfer
--      ALTER statements inside the transfer script.)

-- (c) declare the target topology for the production database.
--     PLATFORM CONSTRAINT (verified 2026-11-11 on Supabase PG 17.6):
--     `ALTER DATABASE ... SET sanad.deployment_topology` and
--     `ALTER ROLE ... SET sanad.deployment_topology` are DENIED for
--     placeholder/custom GUCs (parameter privilege wall; GRANT SET ON
--     PARAMETER is likewise superuser-only and unavailable), and the
--     session pooler strips startup `options`. The topology declaration
--     therefore rides with the authorized migrator dispatch instead:
--     the `confirm_topology='separated'` typed input of the protected
--     workflow maps to a session-level SET (FLYWAY_INIT_SQL for every
--     Flyway connection + the pre/post-flight psql sessions). The
--     original ALTER DATABASE form below remains valid on self-hosted /
--     vanilla PostgreSQL where the migration chain is provisioned by a
--     superuser:
--     ALTER DATABASE postgres SET sanad.deployment_topology = 'separated';
```

Then store the migrator credentials **only** in the GitHub secret store:

* `PROD_FLYWAY_MIGRATOR_USER` = `sanad_migrator.tkbrvupemreqabwzdpyq`
  (Supabase session-mode pooler form; the pooler strips the `.tkbrvupemreqabwzdpyq`
  suffix and PostgreSQL sees exactly `sanad_migrator`, which the workflow's
  `current_user` assertion enforces. The bare form `sanad_migrator` is accepted
  by the workflow only for non-pooler connection paths, which the Supabase
  platform does not offer to IPv4-only GitHub runners — the direct
  `db.<ref>.supabase.co:5432` endpoint resolves IPv6-only.)
* `PROD_FLYWAY_MIGRATOR_PASSWORD` = a long random secret
* `PROD_JDBC_URL` = `jdbc:postgresql://aws-0-eu-central-1.pooler.supabase.com:5432/postgres?sslmode=require`

and enable LOGIN with that password:

```sql
-- Use the privileged psql provisioning session and its protected variable:
SELECT format('ALTER ROLE sanad_migrator LOGIN PASSWORD %L', :'migrator_secret') \gexec
```

### 2b. Read-only release audit identity (`sanad_release_auditor`) — required by the production release gate

The canonical production release workflow audits Flyway through a separately
governed READ-ONLY identity (CAB 2026-11-10 §C); the runtime role must never
regain `flyway_schema_history` access. Provision it in the same one-time admin
session, BEFORE the ownership transfer (run
`scripts/ops/migrator-separation/provision-release-auditor.sql`, which
creates the role, grants the exact least-privilege set and verifies it
fail-closed):

* role `sanad_release_auditor`: `NOLOGIN` until the operator sets the password;
  `NOSUPERUSER NOBYPASSRLS NOCREATEDB NOCREATEROLE`;
* exact privileges: `USAGE` on schema `public`, `SELECT` on
  `flyway_schema_history` and `access_capabilities` (Y2 sentinel rows);
* store in the GitHub **Production** environment secret store:
  `PROD_RELEASE_AUDIT_USER` = `sanad_release_auditor`,
  `PROD_RELEASE_AUDIT_PASSWORD` = a long random secret,
  `PROD_RELEASE_AUDIT_URL` = production database URL;
* enable LOGIN with that password via the operator's governed channel (the
  provisioning script documents the `\gexec` pattern);
* if the grants must be re-asserted AFTER the migrator ownership transfer,
  run the `GRANT` statements from the owner context (`SET ROLE
  sanad_migrator` as the bootstrap admin).

### 2c. Callback-replay cleanup role (`sanad_callback_replay_cleanup`) — required by V20261111_1

The P0 RLS closure of `public.service_callback_replay` requires a dedicated
least-privilege role so the hourly expired-row sweep
(`CallbackReplayStore.deleteExpired`) never needs standing cross-tenant
privileges for the runtime identity. Provision it in the same one-time admin
session (BEFORE running the migration that includes V20261111_1):

```sql
DO $do$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'sanad_callback_replay_cleanup') THEN
    CREATE ROLE sanad_callback_replay_cleanup NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS;
  END IF;
END $do$;
-- SET-only membership: the runtime identity may SET ROLE to the cleanup role
-- inside the sweep's own transaction, but must NEVER inherit its
-- cross-tenant expired-row visibility (policy role matching follows
-- inheritance rules, so a NON-INHERITING membership keeps the cleanup
-- policies inapplicable to `sanad` itself).
GRANT sanad_callback_replay_cleanup TO sanad WITH INHERIT FALSE;
```

The table grants (`SELECT`+`DELETE` only) and the expiry-bounded policies are
created by V20261111_1 itself and verified fail-closed by the migration's
own postconditions plus
`tests/security/callback-replay-rls/run-rls-cert.sh` (isolated PG17).
Verified contract (fail closed on any deviation):

```sql
SELECT pg_has_role('sanad','sanad_callback_replay_cleanup','MEMBER');  -- t
SELECT pg_has_role('sanad','sanad_callback_replay_cleanup','USAGE');  -- f (non-inheriting)
SELECT pg_has_role('sanad','sanad_callback_replay_cleanup','SET');     -- t (SET-only)
SELECT pg_has_role('sanad_callback_replay_cleanup','sanad','MEMBER'); -- f (no escalation)
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

* workflow_dispatch from the G8-approved commit → type
  `migrate-as-migrator`, provide `approved_sha` (must equal the
  dispatched ref HEAD) and `expected_head` → approve the
  **Production** environment review;
* runs Flyway **11.7.2** as `sanad_migrator` — exactly that role —
  with `validateOnMigrate=true` (no baseline, no repair, no
  outOfOrder); credentials reach Flyway only through `FLYWAY_*`
  environment variables, never CLI arguments;
* asserts BEFORE migrating: connected identity, role contracts,
  membership isolation and `sanad.deployment_topology='separated'`;
* asserts AFTER migrating: Flyway `validate`, the enforced expected
  chain head, migrator ownership (relations + routines), the
  append-only ledger, FORCE RLS on the guarded plane and the runtime
  denials (§6) — every condition fails closed.

The runtime service (`render.yaml`) has `FLYWAY_ENABLED=false`: the backend
never runs migrations and never needs DDL privileges. Deploy order:

1. run the migrator workflow (above);
2. deploy the backend image;
3. G10 verification confirms the live `flyway_schema_history` head and the
   acceptance probes.

## 4. CI

* All CI provisioning blocks now create `sanad_migrator` (NOLOGIN,
  cluster-wide) so V20261103_1 passes in every topology.
* The same holds for the P0 callback-replay cleanup role (§2c): every CI
  provisioning block — the 27 workflows that provision the chain identities
  and run the chain — creates `sanad_callback_replay_cleanup` and grants it
  to `sanad` NON-INHERITING (SET-only), so V20261111_1 passes in every
  topology (33 provisioning blocks verified executable on isolated PG 17.11,
  PR #1509 evidence).
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
SELECT has_table_privilege('sanad','public.service_callback_replay','UPDATE'); -- f
SELECT pg_has_role('sanad','sanad_migrator','MEMBER');   -- f
-- P0 callback-replay RLS closure (V20261111_1)
SELECT relrowsecurity, relforcerowsecurity FROM pg_class
 WHERE oid='public.service_callback_replay'::regclass;  -- t | t
SELECT policyname, cmd FROM pg_policies
 WHERE schemaname='public' AND tablename='service_callback_replay'
 ORDER BY policyname;  -- exactly 5 (3 tenant TO PUBLIC, 2 expired-bounded TO cleanup)
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
