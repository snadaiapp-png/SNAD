# RLS Execution & Verification Report — 2026-10-26

**Task:** SNAD-SEC-RLS-EXEC-2026-10-26 · **Branch:** `security/rls-forensic-remediation`
**Repository:** `snadaiapp-png/SNAD` · **Overall status: PARTIALLY COMPLETED**

---

## 1. Executive summary

The function-boundary remediation defined by the RLS forensic audit
(`docs/security/RLS_FORENSIC_REMEDIATION_2026-10-26.md`) was fully executed on
this branch in a free, disposable, host-native PostgreSQL environment —
production was never connected to and remains unchanged. A new forward-only
Flyway migration (`V20261026_4__rls_forensic_function_search_path_and_acl_hardening.sql`)
pins `search_path` for the 16 audited functions and closes the public RPC
surface of the SECURITY DEFINER trigger function `public.sync_login_tenant_index()`
behind five fail-closed guard blocks (G0 existence, G1 schema-CREATE
precondition, G2 role-existence-conditioned revocation, G3 service_role grant
preservation, G4 post-apply verification).

Two independent scenarios were proven against the repository's **real**
212-migration chain (not stubs): chain integrity (Scenario A) and
production-parity unification (Scenario B), where the exact production
divergence was reproduced and then cleared by the migration. A 13-check
verification suite passed 13/13 on both databases, including the negative
tests: anon and authenticated RPC calls are denied (`permission denied for
function sync_login_tenant_index`), the trigger still maintains
`login_tenant_index` across INSERT/UPDATE/DELETE, `service_role` retains
EXECUTE, and the HR append-only guard still raises
`HRM_OFFER_VERSION_IMMUTABLE`.

The policy phase (81 NULL-allow tenant policies, the 49 RLS-disabled tables,
tenant A/B runtime matrix) was deliberately **not** executed: the audit
document explicitly requires application-context tests and owner decisions
before touching those policies, and no authorized migration for them exists on
this branch. The production read-only rescan is **BLOCKED** because the
Supabase access token supplied for this session is rejected by the Management
API (HTTP 401 Invalid access token). The full Maven/PostgreSQL Direct suite
was **NOT RUN** (no Maven in the execution sandbox). These gaps are recorded
with honest statuses below; none of them is silently claimed as passing.

Per the task's grading rule — all acceptance criteria must pass for COMPLETE —
this task is graded **PARTIALLY COMPLETED**.

## 2. Constraints honored (integrity declarations)

| Constraint | Status | Evidence |
|---|---|---|
| Production zero-change | HONORED | No connection to the production database was attempted or made this session (the Management API call that failed with 401 was an authentication attempt only; no query reached the database). All execution happened on localhost disposable fixtures |
| NO MERGE | HONORED | Only a branch push was performed (fast-forward); no pull request was created, no merge action taken |
| NO DEPLOY | HONORED | No Vercel/Render/Supabase deployment or project action was invoked |
| Zero additional cost | HONORED | Environment = portable PostgreSQL 17.6 binaries + Flyway 11.7.2 CLI on the local sandbox; no cloud resource created, no Supabase branch/project started |
| No Docker fallback | HONORED | Host-native PostgreSQL per the repository's PostgreSQL Direct contract (no Docker/Testcontainers) |
| Zero secrets in repo/reports/logs | HONORED | No token value appears in any committed file, report, or evidence log; the Supabase token was used only via a transient environment variable and never persisted; command outputs contain no credential material |
| Forward-only migrations | HONORED | `V20261026_4` contains no `IF EXISTS` masking, no history edits; double-run proven idempotent at the Flyway level ("up to date. No migration necessary.") |
| Honest status vocabulary | HONORED | PASS / FAIL / BLOCKED / NOT RUN / NOT APPLICABLE used throughout; this report is graded PARTIALLY COMPLETED |

## 3. Environment

| Component | Value |
|---|---|
| PostgreSQL | 17.6 portable binaries (`postgresql-binaries` project), localhost:127.0.0.1:5432, trust auth, disposable `$PGDATA` |
| Databases | `sanad` (Scenario A), `sanad_parity` (Scenario B) — both owned by role `sanad` |
| Roles | `sanad` LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS (repo CI contract, required by `V20261011_4` precondition); `anon`, `authenticated`, `service_role` NOLOGIN (Supabase-shaped surface) |
| Flyway | CLI 11.7.2 — the exact version pinned by `apps/sanad-platform/pom.xml`; PostgreSQL JDBC 42.7.2 |
| Migration chain | 211 versioned migrations (V2 → V20261021.1) + 1 repeatable = 212 total, applied from the repository's real `db/migration` directory |
| Fixture note | `btree_gist` was created in schema `public` by the local fixture (production keeps it in `extensions`), which adds extension-owned rows to the inventory's unpinned-functions listing; V20261020_1 and V20261026_4 intentionally exclude extension-owned functions, and the audited 16 are unaffected |

## 4. Baseline vs post-execution state

**Production (unchanged; evidenced from the 2026-10-26 audit snapshot, not
re-verified this session — see PROD-RESCAN):** 319 public tables, 49 without
RLS, 165 RLS-enabled-not-FORCED, 283 policies, 81 NULL-allow tenant-policy
candidates, 16 functions without pinned `search_path`,
`sync_login_tenant_index()` SECURITY DEFINER with EXECUTE inherited from
PUBLIC (anon/authenticated callable).

**Disposable fixture, prod-parity state (Scenario B baseline, reproducing the
production divergence on the repository chain):** 16/16 audited functions
unpinned; `sync_login_tenant_index()` `anon_execute = t`,
`authenticated_execute = t`.

**Disposable fixture after `V20261026_4` (Scenarios A and B):** 17/17 audited
functions pinned (`search_path = pg_catalog, public, pg_temp`; the SECURITY
DEFINER function keeps its shipped `pg_catalog, public`);
`anon_execute = f`, `authenticated_execute = f`; `service_role` EXECUTE
preserved; trigger `users_sync_login_tenant_index` attached and enabled;
SECURITY DEFINER flag unchanged.

## 5. Execution log — commands and exit codes

| # | Step | Command (abbreviated) | Exit | Result |
|---|---|---|---|---|
| 1 | Bootstrap fixture | `scripts/rls_exec/01_bootstrap_pg.sh` (initdb → pg_ctl → roles → createdb) | 0 | `sanad \| postgres \| pg170006` |
| 2 | A1 chain migrate (sans #4) | `flyway -url=jdbc:postgresql://localhost:5432/sanad -user=sanad migrate` | 0 | 212 migrations applied |
| 3 | A2 baseline inventory | `psql -f tests/security/rls_forensic_inventory.sql` (db `sanad`) | 0 | 16 audited functions already pinned by V20261020_1 in-chain; sync `anon_execute = f` (chain-correct state) |
| 4 | A3 validate | `flyway validate` | 0 | "Successfully validated 212 migrations" |
| 5 | A4 apply migration #4 | `flyway migrate` (chain + `V20261026_4`) | 0 | "Migrating schema 'public' to version '20261026.4 … Successfully applied 1 migration" — guards G0/G1/G4 all passed |
| 6 | A5 validate | `flyway validate` | 0 | "Successfully validated 213 migrations" |
| 7 | A6 double-run | `flyway migrate` (again) | 0 | "Schema 'public' is up to date. No migration necessary." |
| 8 | A7 history check | `SELECT … FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 6` | 0 | `213 \| 20261026.4 \| rls forensic function search path and acl hardening \| SQL \| success = t` |
| 9 | A8 post-fix inventory | `psql -f tests/security/rls_forensic_inventory.sql` | 0 | No audited function listed as unpinned; sync `anon_execute = f` |
| 10 | B1 chain on parity DB | `flyway migrate` (db `sanad_parity`) | 0 | 212 migrations applied |
| 11 | B2 reproduce prod divergence | `ALTER FUNCTION … RESET search_path` ×16 + `GRANT EXECUTE … TO PUBLIC` (emulation of the audited production state) | 0 | Prod-parity state established |
| 12 | B3 baseline scan (parity) | `psql -f tests/security/rls_forensic_inventory.sql` | 0 | 16/16 audited unpinned; sync `anon_execute = t`, `authenticated_execute = t` (matches production snapshot) |
| 13 | B4 apply #4 on parity | `flyway migrate` | 0 | Applied; guards passed |
| 14 | B5 post scan (parity) | `psql -f tests/security/rls_forensic_inventory.sql` | 0 | 0/16 unpinned; sync `anon_execute = f`, `authenticated_execute = f` |
| 15 | Verification suite (A) | `scripts/rls_exec/03_verify_suite.sh` (db `sanad`) | 0 | **PASS=13 FAIL=0** |
| 16 | Verification suite (B) | `DBNAME=sanad_parity … 03_verify_suite.sh` | 0 | **PASS=13 FAIL=0** |
| 17 | Production rescan | `scripts/rls_exec/05_prod_rescan.py` (Management API, read-only) | 1 | **BLOCKED** — HTTP 401 `Invalid access token`; no query executed against production |

## 6. Verification suite detail (13 checks, both databases)

| Check | Type | Expected | Observed |
|---|---|---|---|
| P1_fn01_pins | positive | zero audited functions without pinned `search_path` | `pins-verified` (exit 0) |
| P1b_proconfig_evidence | positive | 17 rows carry `pg_catalog, public, pg_temp` (16) + shipped pin (SECURITY DEFINER fn) | evidence file `proconfig_17.txt` written |
| P2_service_role_execute | positive | `service_role` retains EXECUTE | true (exit 0) |
| rpc01_anon | negative | denied | `ERROR: permission denied for function sync_login_tenant_index` (exit 1) |
| rpc02_authenticated | negative | denied | `ERROR: permission denied for function sync_login_tenant_index` (exit 1) |
| n3_anon_index_read | negative | denied | `ERROR: permission denied for table login_tenant_index` (exit 1) |
| P3a_insert_index_rows | regression | email + username index rows created by trigger | `rows=2 kinds=email,username` |
| P3b_update_email | regression | index follows email change, username kept | `old-gone=t new-present=t username-kept=t`, `update-sync-ok` |
| P3c_delete_user | regression | index rows removed on DELETE | `index-rows-after-delete=0`, `delete-sync-ok` |
| P4_partner_triggers | regression | updated_at advances; PARTNER_SUSPENDED audit row | `suspended-event=t updated_at_advanced=t` |
| P5_memberships_triggers | regression | membership audit row + updated_at advance | `membership-audit=t updated_at_advanced=t` |
| n4_append_only_guard | negative | HR append-only guard raises | `ERROR: HRM_OFFER_VERSION_IMMUTABLE: offer versions are immutable history` (exit 1, function-level probe — production trigger attachment untouched by `ALTER FUNCTION … SET`) |
| P6_probe_cleanup | cleanup | probe table dropped | exit 0 |

One test-harness defect occurred during development and was fixed before final
evidence: the first P3b assertion used `'old-gone=' || NOT EXISTS(…)` which is
a SQL precedence error in the assertion query itself (the UPDATE under test had
already succeeded). Re-run after the fix: 13/13. The defect was in test code,
not in the migration.

## 7. Acceptance criteria — final grading

| ID | Criterion | Status |
|---|---|---|
| RLS-01 | No-tenant-context SELECT denies | NOT RUN (policy phase out of scope of this migration) |
| RLS-02 | Tenant A sees only its rows | NOT RUN |
| RLS-03 | Cross-tenant A→B access denied | NOT RUN |
| RLS-04 | Missing/empty/malformed context deny-by-default | NOT RUN (81 candidate policies unchanged, per audit guidance) |
| RLS-05 | `tenant_id` change rejected by WITH CHECK | NOT RUN |
| RPC-01 | anon RPC denied | PASS (fixture, both scenarios) |
| RPC-02 | authenticated RPC denied | PASS (fixture, both scenarios) |
| RPC-03 | Trigger regression on `login_tenant_index` | PASS (fixture, both scenarios) |
| RLS-06 | 49 RLS-disabled tables classified | NOT RUN |
| FN-01 | 16 functions pinned | PASS in fixture (prod-parity 16/16 → 0/16; verified 17/17 pinned); production advisor re-check NOT PERFORMED (PROD-RESCAN blocked) |
| REG-01 | Full Maven/PostgreSQL Direct suite green | NOT RUN (no Maven in sandbox; representative trigger regressions PASS) |
| GOV-1 | Forward-only migration, checksum-stable, double-run safe | PASS (A5/A6) |
| GOV-2 | Production unchanged, no merge, no deploy, zero cost | PASS (§2 declarations) |
| GOV-3 | Zero secrets in repo/reports/logs | PASS |
| GOV-4 | Production rescan evidence | BLOCKED (Supabase token invalid — HTTP 401) |
| GOV-5 | This report + audit-doc appendix delivered on the branch | PASS |

**Grading: PARTIALLY COMPLETED** — the function-boundary remediation (the
scope explicitly ordered for execution) is complete and verified in a real
PostgreSQL environment; the policy phase, 49-table classification, full
Maven suite, and production re-verification remain open and are not claimed
as satisfied.

## 8. FAIL / BLOCKED / NOT RUN register

| Item | Status | Reason | Required to close |
|---|---|---|---|
| PROD-RESCAN | BLOCKED | Supabase access token rejected: HTTP 401 `Invalid access token` (likely rotated/revoked since the audit session) | Provision a valid read-scoped access token; re-run `scripts/rls_exec/05_prod_rescan.py` (read-only, 4 SELECT queries) |
| RLS-01…05 (policy phase) | NOT RUN | Requires application-context tests and owner decisions before editing 81 policies (audit §Priority 0 guidance); no authorized policy migration exists on this branch | Owner-approved policy migration + A/B tenant matrix in this same disposable environment |
| RLS-06 (49-table classification) | NOT RUN | Classification work not started this session | Table-by-table classification doc + per-class policy design |
| REG-01 (full Maven suite) | NOT RUN | Maven/JDK toolchain absent from execution sandbox | Run `mvn test` against a host-native PostgreSQL (repo CI contract) in a Maven-capable environment |
| Production deployment of the chain | NOT RUN | P0 SECURITY HOLD = ACTIVE; NO MERGE / NO DEPLOY | Explicit owner authorization after RLS-01…05, RLS-06, REG-01 pass |

## 9. Deviations and anomalies (recorded for transparency)

1. **Migration numbering gap:** `V20261026_1..3` were never persisted to any
   branch (the prior session's workspace was lost before commit) and were
   never applied to any database. The executed migration uses the
   owner-specified name `V20261026_4__…`; the migration header documents the
   gap. No drift risk exists: Flyway has no record of V20261026_1..3 anywhere.
2. **Fixture `btree_gist` placement:** the local fixture installs
   `btree_gist` into `public` (production uses `extensions`), inflating the
   fixture's unpinned-functions listing with extension-owned functions.
   Excluded by design from both hardening paths; audited 16 unaffected.
3. **Scenario B emulation detail:** the emulation restores PUBLIC EXECUTE on
   the RPC function and un-pins the 16 functions, but leaves the RPC
   function's own `search_path` at the chain value (`pg_catalog, public,
   pg_temp`) rather than production's shipped `pg_catalog, public` — the two
   audited findings are reproduced exactly; the remaining difference does not
   affect the finding semantics.
4. **Trigger regressions are representative, not exhaustive:** users-sync
   (full INSERT/UPDATE/DELETE), partners (2 triggers), partner memberships
   (audit + updated_at), and the HR append-only guard (function-level probe)
   were exercised on real tables; the remaining partner
   binding/delegation/recovery trigger families were not lifecycle-activated
   (covered structurally by G0/G4 and the shared migration batch, flagged for
   the REG-01 run).

## 10. Production-unchanged declaration

No statement in this report is based on a connection to the production
database made during this execution session. Production evidence is carried
forward from the original read-only audit snapshot of 2026-10-26 (same
branch, same document). The remediation reaches production only through the
authorized deploy path after the open items above are closed and the hold is
lifted by the owner.

## 11. Mandatory credential follow-up

All five platform tokens that were exposed in plaintext in the working
channel (GitHub fine-grained PAT, Vercel, Render, Supabase access token,
Resend) are treated as compromised per governance policy and **must be
rotated** immediately after this task, independent of task outcome. The
Supabase token's 401 rejection observed during PROD-RESCAN is consistent with
a prior rotation/revocation and does not change the rotation requirement for
the remaining tokens.
