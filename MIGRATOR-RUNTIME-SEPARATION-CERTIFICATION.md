# Migrator/Runtime Separation Certification

**Mandate:** SANAD P0 FINAL REMEDIATION executive order, Articles 3–5 (Issue #1462)
**Certified change:** V20261103_1 (`sanad_migrator` role contract) + V20261103_2 (topology-gated ownership transfer + evidence-derived runtime ACL matrix, closes F-01)
**Instrument:** `tests/security/migrator-separation/run-separation-cert.sh` (this tree) — real connections as the intended roles; SQLSTATE + exit codes recorded per probe; hard-fail gates.

## 1. Environment

| Component | Value |
|---|---|
| PostgreSQL | **17.6** exact pin (`17.6-1.pgdg13+1`, Debian trixie debs, user-space cluster) — same version class as the certified G5 toolchain (pgdg 17.6-1) |
| Flyway | **11.7.2** CLI (project-pinned; bundles `flyway-database-postgresql-11.7.2.jar` + `postgresql-42.7.2.jar`) |
| Chain | 252 migrations applied (main `db/migration` + `db/vendor/postgresql`), head **`20261103.2`** |
| Roles | `sanad_migrator` LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS · `sanad` LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOBYPASSRLS (ephemeral passwords, never logged) |
| Topology | database `separation_cert`, owner `sanad_migrator`, `sanad.deployment_topology='separated'` |

## 2. Article 4 acceptance matrix — result: **35/35 PASS, 0 FAIL**

| # | Executive-order test | Required | Actual (recorded) |
|---|---|---|---|
| 1 | Flyway using `sanad_migrator` | PASS | **PASS** — migrate 252 migrations, validate OK, head `20261103.2` |
| 2 | Runtime SELECT (allowed) | PASS | **PASS** — `SELECT count(*) FROM saas_plans` → 3 |
| 3 | Runtime INSERT/UPDATE/DELETE (allowed) | PASS | **PASS** — tenants row lifecycle INSERT/UPDATE/DELETE rc=0 |
| 4 | Runtime ALTER TABLE | DENIED | **DENIED 42501** — `must be owner of table tenants` |
| 5 | Runtime DROP TABLE | DENIED | **DENIED 42501** — `must be owner of table saas_plans` |
| 6 | Runtime TRUNCATE | DENIED | **DENIED 42501** — `permission denied for table subscription_commands` |
| 7a | Runtime reads Flyway history | DENIED | **DENIED 42501** (SELECT) |
| 7b | Runtime changes Flyway history | DENIED | **DENIED 42501** (UPDATE, DELETE, INSERT forged row) |
| 8 | Runtime SET ROLE migrator | DENIED | **DENIED 42501** — `permission denied to set role "sanad_migrator"` |
| 9 | Runtime attempts BYPASSRLS | DENIED | **DENIED 42501** — `permission denied to alter role` |
| 10 | Tenant A reaches Tenant B data | DENIED | **DENIED** — A sees 1 own row / 0 of B; cross-tenant UPDATE affects 0 rows (RLS invisibility); cross-tenant INSERT **42501** `new row violates row-level security policy` |
| 11 | Missing tenant context | FAIL-CLOSED | **FAIL-CLOSED** — no GUC → 0 rows; empty-string GUC → 0 rows |
| 12 | User creation (runtime, under GUC) | PASS | **PASS** |
| 13 | Refresh + password reset | PASS | **PASS** — INSERT/UPDATE/DELETE on both token tables; **trigger `sync_auth_token_tenant_index` fired as runtime and maintained the pre-auth index** (1 row verified) |
| 14 | Subscription commands | PASS | **PASS** — INSERT ok; ledger append-only enforced: UPDATE/DELETE **DENIED 42501**; `tenant_subscriptions` INSERT/UPDATE ok (**F-01 closed**) |
| 15 | Provisioning jobs | PASS | **PASS** (ACL surface SELECT/INSERT/UPDATE per V20261102_2 call-site audit; enforced-by-ownership removed) |
| 16 | Background workers | PASS | **PASS** — worker-owned planes (`provisioning_*`, outbox tables) in the general DML set; catalog sweep asserts the full surface on **every** public table |

**Catalog snapshot (S7):** all 324 public relations owned by `sanad_migrator`;
`flyway_schema_history` owner `sanad_migrator`; runtime CREATE on `public` = false;
FORCE RLS 6/6 on the guarded tables; sequences USAGE/SELECT complete.

## 3. Article 5 evidence — result: **29/29 PASS**

| Scenario | Checks | Result |
|---|---|---|
| **A. Production-history-equivalent upgrade** | chain to watermark `20261021.1` as `postgres` (shared model) → admin ownership transfer (`scripts/ops/migrator-separation/transfer-ownership-to-migrator.sql`) → topology declaration → **pending 21 migrations as `sanad_migrator`** → head `20261103.2`; post-upgrade ownership = migrator only; denials as runtime hold | **PASS (10/10)** incl. **checksum join vs the read-only extracted production ledger: 100% match ≤ watermark** |
| **B. Fixture topology (CI sandboxes)** | full chain as `sanad` (owner-as-runner) → completes, head `20261103.2`, WARNING emitted, validate OK | **PASS (4/4)** — the existing CI suite path stays green |
| **C. Failure injection** | checksum tamper on V20261103_2 → `validate` **FAILED** with checksum mismatch (fail-closed; no repair, no baseline) | **PASS** |
| **D. Backup/Restore** | `pg_dump -Fc` → restore into fresh DB as migrator role → 252 history rows, ownership = migrator, FORCE RLS 6/6, tenant A sees 1 / B 0, no-GUC 0, TRUNCATE + history-tamper still denied | **PASS (10/10)** |

## 4. Problems found and fixed during certification (engineering record)

1. **Flyway-history in-migration deadlock** (live reproduction): any ACCESS
   EXCLUSIVE lock on `flyway_schema_history` inside a migration blocks forever
   against Flyway's own history lock connection → the ledger is excluded from
   in-migration DDL and enforced by a dedicated ownership guard.
2. **Owned-sequence transfer restriction**: `ALTER SEQUENCE … OWNER TO` is
   forbidden for column-owned sequences (`pg_depend deptype='a'`) → excluded;
   they follow their table.
3. **Planner predicate reordering hazard**: `has_table_privilege('…','public.'||name,…)`
   evaluated before the schema filter probed non-existent relations (42P01 live) →
   guards now qualify schemas from the row's own columns.
4. **Pending-set ownership obstacles**: pending migrations ALTER objects owned
   by the previous shared identity; running them as the migrator without the
   admin transfer fails closed (`must be owner of function …`, live 42501) →
   runbook step + transfer script added; upgrade scenario proves the full path.

## 5. Known scope boundary (honest disclosure)

The existing CI scenario databases keep the documented `migration-fixture`
topology because the migration-mechanics tests (`flyway.clean()/migrate()`
cycles; ~47 test classes run their own Flyway against the scenario database)
structurally require the runner to own the schema. Under the fixture topology
V20261103_2 applies the grant matrix and invariant guards, emits a WARNING,
and does **not** claim separation. The separated topology is certified by the
dedicated 35-probe matrix (this document) and enforced for production by the
runbook + protected migrator workflow. Converting the full CI scenario fleet
to the separated topology is follow-up engineering (tracked as open work),
not a claim of this certification.

## 6. Artifacts

* `tests/security/migrator-separation/run-separation-cert.sh` (instrument)
* Workflow: `.github/workflows/migrator-runtime-separation.yml` (PR + dispatch; uploads matrix.tsv + logs)
* Local-run artifacts: `matrix.tsv` (35 rows), `S1-migrate.log`, `S1-validate.log`, `summary.md`
* Upgrade suite evidence: U1–U4 logs incl. checksum-join output and backup.dump digest

```text
SEPARATION_ACCEPTANCE_MATRIX = PASS (35/35)
UPGRADE_FIXTURE_INJECTION_BACKUP = PASS (29/29)
PROBLEM_PROOF_PRE_FIX = confirmed (ALTER/TRUNCATE/history/CREATE allowed as runtime under owner-as-runtime)
F01_TENANT_SUBSCRIPTIONS_ACL = CLOSED (V20261103_2)
```
