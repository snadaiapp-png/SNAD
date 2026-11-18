# G1 / C4 — PR-2 Red Team attack-surface review (live, isolated, evidence-bound)

**Date:** 2026-11-18 · **Target tree:** official `main` @ `4cee76ad71667b00b60570db25358df60c99cbb1` (PR-2 branch base) · **Environment:** disposable separated-topology PostgreSQL 17.12 cluster (127.0.0.1:55440), full 260-migration Flyway chain as `sanad_migrator`, head `20261115.1`, certified by S0–S9 immediately before the battery.
**Method:** every probe is a **real SQL statement executed as the restricted runtime role `sanad`** (or `sanad_callback_replay_cleanup` where noted) on the certified database; SQLSTATE, exit code and row counts recorded per probe; fail-closed expectations. No simulation, no mocks, no production access. Reproducible via `tests/security/g1-c4/redteam-attack-surface-battery.sh` (added in this PR; isolation-guarded).
**Result: 37/37 probes executed with expected outcomes; 0 probe-construction failures.** Raw matrix: `redteam-matrix.tsv`; full console: `redteam-run.log` (PR-2 delivery package `evidence-pr2/redteam/`).

## Execution honesty record

Three attempts were executed; all three are preserved (`*-attempt1/2` + final). Attempt 1 lost its console to a logging-pipe failure (side effects detected and disclosed: one forged audit row `66666666…`, removed by the cluster admin before the clean run) and had 6 expectation deviations, of which: one was an inconclusive-by-construction temp-shadow probe (no data row), two were wrong column/type fixtures, one was a wrong expected SQLSTATE (43101 vs actual 42501 — denial itself correct), one was a unique-key collision with attempt-1 residue, and one (quota INSERT denied) led to the **live discovery that `tenant_quota` is RLS-protected**, correcting doc 16 (see doc 17 §1). Attempt 2 fixed the fixtures; remaining deviations were CHECK-whitelist (`23514`) and residue-collision issues. The final attempt is the record of truth.

## A. RLS trust boundary and bypass attempts

| Probe | Statement (as `sanad`) | Expected | Actual |
|---|---|---|---|
| RT-01 | `SET app.tenant_id='<tenant B>'; SELECT count(*) FROM users WHERE tenant_id='<B>'` | DESIGN-TRUST visibility | **CONFIRMED: foreign tenant row visible** — GUC is application-trusted, not DB-authenticated (finding F-RT-01) |
| RT-02 | `UPDATE users SET tenant_id='<B>' WHERE tenant_id='<A>'` (GUC=A) | DENY 42501 | **DENIED 42501** (RLS WITH CHECK) |
| RT-03 | `WITH changed AS (UPDATE users … WHERE tenant_id='<B>' RETURNING 1) SELECT count(*)` (GUC=A) | 0 rows | **0 rows** (invisible) |
| RT-04 | `SELECT count(DISTINCT tenant_id) FROM login_tenant_index` (no GUC) | documented exposure | **CONFIRMED: 2 tenants visible** (pre-auth plane, finding F-RT-03) |
| RT-05 | `SELECT count(*) FROM auth_token_tenant_index` (no GUC) | documented exposure | **CONFIRMED readable** (0 rows at probe time) |
| RT-06 | `INSERT INTO workflow_external_action_token_index …` | DENY 42501 | **DENIED 42501** (read-only grant holds) |

## B. Control-plane write surface (RLS-off tables)

| Probe | Statement | Expected | Actual |
|---|---|---|---|
| RT-07/08/09 | sacrificial tenant: INSERT → UPDATE (`status`,`suspension_reason`) → DELETE on `tenants` | full CRUD available | **CONFIRMED ×3** (finding F-RT-02) |
| RT-10 | `INSERT INTO tenant_quota …` own tenant (GUC=A) | allowed | **CONFIRMED** |
| RT-10B | same for tenant B (GUC=A) | DENY 42501 | **DENIED 42501** (`tenant_quota` is RLS ENABLE+FORCE — doc 16 correction) |
| RT-11 | own-tenant quota DELETE (count CTE) | 1 | **1** (tenant-scoped) |
| RT-12/13/14 | `access_capabilities`: INSERT synthetic code → UPDATE status → DELETE | DML available | **CONFIRMED ×3** (finding F-RT-04; sacrificial row cleaned up) |
| RT-15 | `SELECT count(*) FROM modules` (no GUC) | global by design | **CONFIRMED: 12 rows readable** |

## C. Privilege escalation

| Probe | Statement | Expected | Actual |
|---|---|---|---|
| RT-16 | `CREATE ROLE rt_escalate` | DENY 42501 | **DENIED 42501** |
| RT-17 | `GRANT sanad_migrator TO sanad` | DENY 42501 | **DENIED 42501** |
| RT-18 | `SET ROLE sanad_migrator` | DENY 42501 | **DENIED 42501** |
| RT-19 | `ALTER DATABASE separation_cert SET sanad.deployment_topology='fixture'` | DENY 42501 | **DENIED 42501** (non-owner) |
| RT-20 | `CREATE FUNCTION public.rt_f() …` | DENY 42501 | **DENIED 42501** (no schema CREATE) |
| RT-21 | pg_temp shadow, CI-parity (temp `hr_employees` row `2099-12-31` for tenant A; call `hr_default_migration_as_of_date(A)`) | ≠ 2099-12-31 | **1970-01-01 — shadow NOT sighted** (public precedes pg_temp in definer search_path) |

## D. Audit-ledger tampering

| Probe | Statement | Expected | Actual |
|---|---|---|---|
| RT-22/23/24 | UPDATE / DELETE / TRUNCATE `platform_audit_logs` (GUC=A) | DENY 42501 ×3 | **DENIED 42501 ×3** |
| RT-25 | `CREATE TRIGGER … ON platform_audit_logs` | DENY 42501 | **DENIED 42501** |
| RT-26 | forged INSERT (`RT_FORGED_PROBE`) | accepted by append-only design | **CONFIRMED** (admin-verified persisted; left in place as evidence) — finding F-RT-05 |
| RT-27/28 | as cleanup role: SELECT / UPDATE audit ledger | DENY 42501 ×2 | **DENIED 42501 ×2** (no grants on the ledger) |

## E. Fake-success / migration-gate forgery

| Probe | Statement | Expected | Actual |
|---|---|---|---|
| RT-29 | `INSERT INTO flyway_schema_history …` (fake successful migration) | DENY 42501 | **DENIED 42501** |
| RT-30 | `UPDATE flyway_schema_history SET success=true` | DENY 42501 | **DENIED 42501** |
| RT-31 | `SET sanad.deployment_topology='fixture'` (session) | settable but non-authoritative | **CONFIRMED**; S10 case 6 proves the gate reads the catalog, not the session |

## F/G. Definer invocation & separation invariants

RT-32/33: `hr_reconcile_tenant_report(B)` and `hr_precheck_tenant(B)` under GUC=A — **DENIED 42501 ×2** (guards hold). RT-34: `pg_has_role('sanad','sanad_migrator','MEMBER')` = **false**. RT-35: `service_role` count = 0 locally (production-only; production facts stand in doc 15 — local UNVERIFIED).

## Findings (each bound to live evidence above)

- **F-RT-01 (HIGH, inherent design trust):** tenant GUC is not DB-authenticated; a hostile runtime SQL session can impersonate any tenant inside RLS scope (RT-01). Compensating control: JWT→GUC transaction discipline; remediation evaluation recommended (doc 17 R-C4-02).
- **F-RT-02 (HIGH):** `tenants` control-plane is fully writable (CRUD) by the runtime role with no RLS (RT-07/08/09). Remediation recommended (R-C4-01).
- **F-RT-03 (MEDIUM, documented exception):** pre-auth indexes are cross-tenant readable (RT-04/05); pre-RLS characterization contract open (R-C4-05).
- **F-RT-04 (MEDIUM-HIGH):** capability registry DML by runtime (RT-12/13/14) can mint/deactivate authorization-relevant codes (R-C4-04).
- **F-RT-05 (MEDIUM, design trade-off):** audit ledger accepts forged runtime INSERTs; append-only ≠ per-row authenticity (RT-26) (R-C4-03).
- **F-RT-06 (verification):** all escalation, ledger-tamper, forgery and separation-bypass attempts were denied with real SQLSTATEs (no bypass found); `tenant_quota` misclassification in doc 16 corrected from live behavior.

## Explicitly NOT tested here

Production catalog/pooler (no authorized access), application-layer auth (JWT issuance), network perimeter, availability/DoS, and the Supabase `service_role` path (role absent locally). No production system was touched.

`P0_SECURITY_HOLD=ACTIVE` · `G1=HOLD` · `PRODUCTION_RELEASE=NO-GO` · `PAYMENTS=OFF`.
