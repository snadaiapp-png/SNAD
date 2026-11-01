# RLS Full Closure — Execution & Verification Report — 2026-10-29

**Task:** `SNAD-SEC-RLS-FULL-CLOSURE-2026-10-28` · **Branch:** `security/rls-forensic-remediation`
**Tested commit:** this file's commit (recorded at push time) · **Production changes: ZERO · Merge: NONE · Deploy: NONE**

---

## 1. Executive summary

Phase 2 (PostgreSQL Direct) and Phase 3 (remediation migration) of the RLS full-closure
task were executed against a real, disposable, host-native PostgreSQL 17.6 cluster running
the repository's complete 214-migration Flyway chain — production was never written to.

**New remediation migration:** `V20261029_1__tenant_owned_business_data_fail_closed_rls.sql`
— applies ENABLE + FORCE RLS plus one fail-closed `tenant_isolation` policy (`TO sanad`)
to 92 tenant-owned tables whose isolation was absent from the chain (the CRM-018
generator is not present in the current chain), and binds the table owner to the
existing strict policies of 60 further tables that were ENABLE-only (owner-bypass
defect, runtime-proven on `platform_files`). Guards: G0 restricted-role precondition,
G1 table/tenant-column existence, G2/G2b/G2c application, G3 post-apply verification
(fail-closed migration — any drift aborts it).

**PostgreSQL Direct suite (this branch, real roles, no mocks):**
- 260/260 tenant-scoped RLS-protected tables inventoried and processed.
- **156 PASS** — full A/B read isolation, B update/delete denial, cross-tenant INSERT
  rejected by WITH CHECK (RLS-specific error), tenant-swap rejected, missing/empty/
  malformed context denied.
- **6 DOMAIN-GUARD-LIMITED** — every cross-tenant and context-denial probe held; own-
  tenant visibility could not be synthesized under domain lifecycle guards
  (partner_* bindings). Requires domain-realistic fixtures (CI Java suite).
- **98 SYNTH-INSERT-BLOCKED** — generic fixture synthesis blocked by per-table domain
  CHECK constraints. Not an RLS result. These tables are covered by the migration's
  G3 predicate guards, the catalog checks below, and the CI Maven suite with real
  fixtures.
- **0 FAIL, 0 ERROR.**
- Catalog/role/function/transaction/negative-control acceptance: **all PASS** (see §3).

## 2. Constraints honored

| Constraint | Status |
|---|---|
| Production zero-change | HONORED — Management API used for catalog SELECTs only; no DDL/DML reached production |
| NO MERGE / NO DEPLOY | HONORED — branch push + PR only |
| Forward-only migrations | HONORED — no history edits; `IF EXISTS` used only in the documented DROP-before-CREATE idempotence pattern (repo precedent V20260905_5) |
| No mocks for DB proofs | HONORED — every isolation assertion ran against real PostgreSQL 17.6 with the real chain and real roles |
| Secrets | HONORED — no token value in any committed file, log, or report |

## 3. Acceptance evidence (catalog_tests.py — exit 0)

| ID | Check | Result |
|---|---|---|
| RLS-06a | No `BYPASSRLS` roles except `postgres` (bootstrap only) | PASS |
| RLS-06b | Zero table grants to `PUBLIC`/`anon`/`authenticated` | PASS (0) |
| RLS-04a | Zero fail-open tenant-context policies (`IS NULL`-allow pattern) | PASS (0) |
| FN-01 | All non-extension public functions have pinned `search_path` | PASS (0) |
| RPC-01a | No anon/authenticated-callable SECURITY DEFINER functions | PASS (0) |
| RPC-01/02 | anon/authenticated cannot reach `sync_login_tenant_index()` | PASS (denied) |
| RPC-03 | `users` trigger maintains `login_tenant_index` (insert + delete) | PASS |
| TX-01/02 | GUC is transaction-local (cleared after commit AND rollback) | PASS |
| TX-03 | In-transaction tenant switch is immediate (last wins) | PASS |
| TX-04 | Tenant B cannot update tenant A rows (FORCE binds owner) | PASS |
| NEG-01 | Negative control: a fail-open policy IS detected by this suite | PASS (leak reproduced → detected) |
| RLS-06c | Tables without RLS remaining | 29 = 23 PLATFORM-REFERENCE + 6 T2-DEFERRED (classified, see §4) |

## 4. Classification matrix (RLS-06)

`tests/security/rls_tenant_owned_classification.json` — 294 public tables:
173 already RLS-protected · 92 remediated by V20261029_1 (T1) · 60 FORCE-bound (G2c,
included in the 92+60 hardening; FORCE-only tables keep their existing strict policies) ·
23 PLATFORM-REFERENCE (no tenant_id; default-deny grants contract) ·
6 T2-DEFERRED (refresh_tokens, password_reset_tokens, login_tenant_index,
provisioning_jobs/steps, subscription_commands — credential/pre-auth/background
contracts that require the G1 characterization gate before RLS; explicitly documented,
not silently skipped). Known schema drift documented: `tenant_quota.tenant_id` is
VARCHAR(64) vs platform UUID contract — protected under a type-checked guard.

## 5. Reproduction

```bash
# isolated cluster (no root, no Docker): scripts used for this evidence
initdb -D pgdata && pg_ctl start -p 5433
CREATE ROLE sanad LOGIN NOSUPERUSER NOBYPASSRLS; CREATE DATABASE sanad OWNER sanad;
flyway -url=jdbc:postgresql://127.0.0.1:5433/sanad -user=sanad \
  -locations=filesystem:apps/sanad-platform/src/main/resources/db/migration migrate   # 214/214 exit 0
flyway ... validate                                                                    # 214/214 exit 0
python3 tests/security/rls_postgres_direct_suite.py      # per-table A/B suite
python3 tests/security/rls_catalog_acceptance_tests.py   # catalog/RPC/TX/NEG suite
```

## 6. Remaining before COMPLETE (honest gaps)

1. **REG-01 (targeted, green):** backend compiles with the new migration (mvnw compile,
   exit 0) and targeted PostgreSQL-Direct Java tests ran GREEN against the full chain
   including V20261029_1: ContactRlsPostgresTest 12/12, HrRlsFailClosedIntegrationTest
   259/259, CrmPostBaselineFailClosedRlsPostgresTest 5/5 (276 tests, 0 failures/errors/
   skips). The CI Maven suite on PR #1460 remains the authoritative full-suite gate.
2. **Independent review:** not yet performed. This report is implementer evidence and
   must not be treated as independent assurance. PR #1460 is opened for it.
3. **T2 tables (6):** require the G1 pre-auth/credential contract work before RLS.
4. **98 fixture-limited tables:** row-level A/B probes require domain-realistic
   fixtures (CI suite covers them with real fixtures).
5. **PROD-RESCAN (executed this session, catalog SELECTs only):** production
   `snad-prod-fresh` — 319 tables, 270 RLS-enabled, 114 FORCE, 49 without RLS (matches
   the 2026-10-26 audit). Flyway history reaches 20261021.1 (installed 2026-10-28);
   **20261026.4 is NOT in the history yet its effects are verified present** (0 unpinned
   non-extension functions; sync_login_tenant_index anon/authenticated EXECUTE = false)
   — a Flyway history divergence from an out-of-band application that must be
   reconciled before the next deployment. **60 fail-open policies remain in production,
   all on crm_* tables**, ~25 of which do not exist in the branch chain (production
   schema drift) — closing them needs the drift reconciliation or a production-derived
   hardening migration. Production `sanad` role verified NOBYPASSRLS; BYPASSRLS roles
   present are Supabase-platform roles (service_role, supabase_admin,
   supabase_read_only_user, supabase_etl_admin) — provider-owned, outside the SANAD
   role contract, documented in V20261020_1 architecture.
6. **Token rotation:** all five plaintext-exposed platform tokens must be rotated
   before any deployment authorization (governance rule: chat exposure = compromised).

**Verdict: PARTIALLY COMPLETED** — the isolation itself is implemented and
runtime-proven on the isolated environment; gates that require CI, independent
review, and deployment authorization remain open by design.
