# 03 — SIX-TABLE RLS SECURITY ACCEPTANCE (Phase 2)

**Exact SHA:** `f95a30b64e75815d571e582e1b555c01bcd53abe`
**Environment:** isolated host-native **PostgreSQL 17.6.0** (same major as the production Supabase target) at `127.0.0.1:5434`, **Flyway CLI 11.7.2** (project version), database `rehearsal_final_a` migrated **exclusively by the real migration chain** (`filesystem:wt-final/.../db/migration` + `db/vendor/postgresql`) — 250 migrations applied, `flyway validate` clean.
**Driver:** `scripts/finalclosure_sixtable_matrix_v2.py` (57 strict tests, `ON_ERROR_STOP=1`, `VERBOSITY=verbose` for SQLSTATE capture).
**Runtime identity under test:** `sanad` — `LOGIN, NOSUPERUSER, NOCREATEDB, NOCREATEROLE, NOBYPASSRLS, NOREPLICATION` (verified live: `rolsuper=false, rolbypassrls=false`).
**Result:** `RESULT: PASS=57 FAIL=0 → MATRIX=GREEN` (log: `finalclosure-sixtable-matrix-v2.log`).

## 2.0 The critical proof: privileges come from the migration chain, not from test setup

The database was migrated by the chain alone; no out-of-band GRANT was executed before probing. Post-migration catalog probe (raw evidence `final-A-sanad-grants-from-chain.txt`):

```
auth_token_tenant_index:DELETE,INSERT,SELECT,UPDATE
login_tenant_index:DELETE,INSERT,SELECT
password_reset_tokens:DELETE,INSERT,SELECT,UPDATE
provisioning_job_steps:INSERT,SELECT,UPDATE
provisioning_jobs:INSERT,SELECT,UPDATE
refresh_tokens:DELETE,INSERT,SELECT,UPDATE
subscription_commands:INSERT,SELECT
workflow_external_action_token_index:DELETE,INSERT,SELECT,UPDATE
```

- `refresh_tokens` / `password_reset_tokens` runtime DML is enabled **by `V20261102_1` (PR #1465)** — exactly SELECT/INSERT/UPDATE/DELETE, nothing more.
- `provisioning_jobs` / `provisioning_job_steps` / `subscription_commands` runtime DML is enabled **by `V20261102_2` (PR #1465)** — least-privilege per audited call sites: no DELETE on jobs/steps, and subscription_commands is **append-only (INSERT,SELECT only — no UPDATE, no DELETE)**.
- No `TRUNCATE/REFERENCES/TRIGGER` privilege exists anywhere for `sanad` (DA03 = 0 rows).
- No grant relies on Flyway-chain-external setup: the rehearsal is the counter-example infrastructure — it has none.

## 2.1 Real table names & control state (live catalog, not legacy report labels)

| Table (verified name) | relrowsecurity | relforcerowsecurity | Owner | Policy (pg_policies) |
|---|---|---|---|---|
| `public.refresh_tokens` | true | **true (FORCE)** | postgres | `tenant_isolation`, ALL, **TO public**, fail-closed on `app.tenant_id` |
| `public.password_reset_tokens` | true | **true (FORCE)** | postgres | same |
| `public.login_tenant_index` | false | false | postgres | none by design — Pre-Auth discovery plane, guarded by ACL + SECURITY DEFINER sync trigger |
| `public.provisioning_jobs` | true | **true (FORCE)** | postgres | same |
| `public.provisioning_job_steps` | true | **true (FORCE)** | postgres | same |
| `public.subscription_commands` | true | **true (FORCE)** | postgres | same |

Notes mandated by the command:
- `login_tenant_index` was **deliberately NOT FORCE-RLS'd**: the pre-auth tenant-discovery contract requires identifier→tenant resolution with *no* tenant GUC present. Its data plane is protected by: ACL limited to `DELETE,INSERT,SELECT` for `sanad` (no UPDATE — PA03 proves `42501`), writes synchronized only through the SECURITY DEFINER trigger function `sync_login_tenant_index` (DA05: every `%token%`/index SECURITY DEFINER function has a pinned `search_path` — count without pin = 0; DA06: `sanad` cannot call `sync_auth_token_tenant_index()` directly → `42501`).
- FORCE RLS does NOT protect against `superuser` or `BYPASSRLS` — verified roles here: `sanad` has neither (WRK12/E2E, DA01). Supabase-plane roles (`anon`/`authenticated`/`service_role`) do not exist in the rehearsal cluster and their deployment-plane behavior is covered in 08 (topology evidence).

## 2.2 The 57-test matrix — sections and results (all PASS)

| Section | Tests | What they prove |
|---|---|---|
| 1 refresh_tokens (RT01–RT12) | 12 | own-read resolves exactly 1; foreign-tenant read/update/delete return empty; cross-tenant INSERT → `42501` (WITH CHECK); missing GUC → 0 rows; empty GUC → 0 rows; invalid GUC → 0 rows; revoked/expired tokens unresolvable; `token_hash` identity immutability → `23514`; index `auth_token_tenant_index` synced on insert |
| 2 password_reset_tokens (PW01–PW07) | 7 | same isolation envelope for the reset plane; expired reset tokens unresolvable |
| 3 pre-auth discovery planes (PA01–PA06) | 6 | email/token resolution works **without GUC** (documented design); index tampering by migrator cannot cross tenant boundary (forged index to tenant B still yields 0 refresh-token rows under B's GUC); index restored; no username-leak scope |
| 4 provisioning_jobs / _steps (PJ01–PJ12) | 13 | parent-child tenant isolation (join through parent under GUC), direct foreign step read = 0, foreign-step insert → `42501`, missing-GUC fail-closed, own-job INSERT allowed and verified, foreign DELETE → `42501` (ACL deny — DELETE not granted) |
| 5 subscription_commands (SC01–SC08) | 9 | own-read 1, foreign-read 0, cross-tenant insert → `42501`, **UPDATE → `42501` (append-only by ACL)**, **DELETE → `42501`**, missing/empty GUC fail-closed, own-ledger INSERT allowed and verified |
| 6 connection-pool recycling (CP01–CP02) | 2 | transaction-local GUC leaves **zero** residual rows after commit (pool reuse cannot leak tenant scope); explicit session reset returns to 0 |
| 7 Data-API & privileged surfaces (DA01–DA06) | 6 | no Supabase data-API roles in the isolated model; PUBLIC has no CREATE on schema public; no TRUNCATE/REFERENCES/TRIGGER for `sanad`; `sanad` cannot `ALTER TABLE … DISABLE ROW LEVEL SECURITY` (`42501`); all SECURITY DEFINER token/index functions have pinned `search_path`; `sanad` cannot invoke the sync trigger function directly (`42501`) |
| 8 concurrent sessions (CC01–CC02) | 2 | simultaneous tenant A and tenant B sessions each see exactly their own rows (2 vs 1) — no cross-session GUC bleed |

## 2.5 Migration review (the five mandated migrations)

| Migration | Introduced by | Content reviewed at f95a30b6 | Verdict |
|---|---|---|---|
| `V20261028_2` | PR #1458 (`12f92d7cf`) | pre-auth token index + FORCE RLS + fail-closed policies for the two auth-token tables; precondition gate requires restricted `sanad` | PASS |
| `V20261031_1` | PR #1463 (`2e88419cb`) | T2 batch-2 closure: ENABLE+FORCE RLS + fail-closed policies on provisioning/subscription-command tables; `login_tenant_index` ACL | PASS |
| `V20261101_1` | prior batch | index ACL + identity plane (authorization_version typing etc.) | PASS |
| `V20261102_1` | PR #1465 (`177e27c2`) | fail-closed preconditions; GRANT USAGE ON SCHEMA + full DML on the two token tables to `sanad` only; NO REVOKE/policy/role/BYPASSRLS changes; exit guard verifies the grant landed | PASS |
| `V20261102_2` | PR #1465 (`177e27c2`) | fail-closed preconditions; least-privilege grants per audited call sites (no DELETE on jobs; append-only commands); exit guard | PASS |

Least-privilege deltas are minimal and justified: `V20261102_1` grants exactly what the CI contract harness previously had to inject out-of-band (the masked-gap it closed), and `V20261102_2` grants exactly the audited call-site surface. Neither weakens RLS; both are forward-only and idempotent.

## 2.6 Verdict

**SIX_TABLE_RLS_ACCEPTANCE = PASS** at `f95a30b64e75815d571e582e1b555c01bcd53abe`, subject only to the standing independent-review gate (07). No unauthorized access path and no ACL/RLS-caused functional failure exists within the six-table scope. (The subscription *aggregate table* ACL finding from the E2E phase is recorded as F-01 in 04 — it is outside the six-table scope, is fail-closed, and does not affect this verdict.)
