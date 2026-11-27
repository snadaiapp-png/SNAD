# G1 / C4 — PR-2 technical risk adjudication (exact merged main `4cee76ad7`)

**Date:** 2026-11-18 · **Branch:** `security/g1-c4-pr2-risk-adjudication` (from official `main` @ `4cee76ad71667b00b60570db25358df60c99cbb1`, PR-1 #1538 MERGED)
**Adjudicated tree:** identical content to PR #1538 head `7693c4806d164768c297d6169a4bff333bddb626` (verified: merge commit imports source tree `11c998cdebf0d24ad7ed97c53750c8e446680d23` of verified local candidate `498c5acfd87720b3446cecbdd3df0082365f1e1a` + a two-file CI fixture alignment).
**Disposition:** `C4_TECHNICAL_ADJUDICATION = COMPLETE (this document)` · `C4_RESIDUAL_RISK_DECISIONS = PENDING-AUTHORIZED-OWNER` · `G1=HOLD` · `PRODUCTION_RELEASE=NO-GO` · `PAYMENTS=OFF`.
This is an evidence-grounded engineering risk adjudication under [Continuous Security & Release Governance](../../governance/SANAD-CONTINUOUS-SECURITY-RELEASE-GOVERNANCE-2026-11-14.md) (PR #1531). It is **not** a production authorization, **not** a G1 certificate, and **accepts no residual risk** — every residual risk below remains PENDING a documented decision by the authorized project owner with compensating control and time-bound remediation plan.

## 0. Evidence base (three independent classes)

1. **Official CI on/near the exact SHA (2026-11-18):**
   - Post-Merge Main Verification [run 44294090764](https://github.com/snadaiapp-png/SNAD/actions/runs/44294090764) on `4cee76ad7`: JOB A/B/C/D/E/F all SUCCESS (JOB C = full Maven suite on host-native PostgreSQL, 11:22:22→12:31:00 ≈ 69 min; JOB F = fail-closed evidence gate).
   - G3 Authenticated Acceptance [run 44294090547](https://github.com/snadaiapp-png/SNAD/actions/runs/44294090547): SUCCESS. HRM G4 T1 RED [run 44294090839](https://github.com/snadaiapp-png/SNAD/actions/runs/44294090839): SUCCESS. Production Workflow closure and R0C-12 gate: SKIPPED (production execution remains gated; correctly **not** counted as passing).
   - Migrator/Runtime Separation [run 44283363382](https://github.com/snadaiapp-png/SNAD/actions/runs/44283363382): SUCCESS on PR head `7693c480` (content-identical tree).
   - G1 Flyway Auditor Acceptance [run 44280887930](https://github.com/snadaiapp-png/SNAD/actions/runs/44280887930): FAILURE (pre-fix, PR head `2cf1728`) — root-caused to synthetic-ledger fixture versions; fixed by the merge commit's fixture alignment; locally re-validated GREEN for this adjudication (see doc 19 §3).
2. **Live isolated re-certification executed for this adjudication** on a fresh disposable separated-topology PostgreSQL 17.12 cluster (deviation from the CI 17.6 pin recorded; CI remains authoritative for the pin): separation S0–S9 **75/75 PASS**; G1 release catalog gate **14/14 PASS** (head `20261115.1`); C4 catalog probes PASS (partial contract; full approval correctly HOLD); HR definer negative matrix **20/20**; positive synthetic scope **2/2**; token-rotation regression **7/7**; S10 protected-deployment gate **7/7**; G5/G6 all scenarios PASS; Callback Replay RLS **37/37**; Red Team battery **37/37** (doc 18). Raw logs in the PR-2 delivery package (`evidence-pr2/`).
3. **Historical production read-only catalog facts** (doc 15, 2026-11-13): production Supabase project head `20261111.1` at the time; 12 definer functions owned by `sanad_migrator`; `sanad` EXECUTE 12/12; `service_role` EXECUTE 11/12; no PUBLIC/anon/authenticated EXECUTE. Production re-verification on the current chain remains BLOCKED (no authorized production access in this engagement).

## 1. Correction to doc 16 — the live RLS-OFF set

The live catalog on the adjudicated chain (fresh Flyway apply, head `20261115.1`) reports **321** base/partitioned public tables, **295** ENABLE+FORCE RLS, **26** RLS-OFF — matching the production read-only facts of doc 15 gate 4 exactly. The **composition** differs from doc 16 §2's static replay in two places:

- **`tenant_quota` is RLS ENABLED + FORCE** with the fail-closed `tenant_isolation` policy (`tenant_id::text = NULLIF(current_setting('app.tenant_id', true),'')`, USING and WITH CHECK). Doc 16 listed it as RLS-OFF (static-replay error; the prior session's own live forensic inventory already showed `rls=t force=t`). Live proof: own-tenant quota INSERT PASS, foreign-tenant quota INSERT denied **42501** (RT-10/RT-10B).
- **`flyway_schema_history` is in the live 26** (it is created by Flyway, not by a versioned migration, so the static replay never counted it). The runtime role holds **zero** privileges on it: SELECT/INSERT/UPDATE/DELETE all denied 42501 (S3, re-confirmed RT-29/RT-30).

Authoritative live 26 (sorted): `access_capabilities`, `application_iam_contracts`, `applications`, `auth_token_tenant_index`, `country_currencies`, `flyway_schema_history`, `hr_compliance_rules`, `hr_country_packs`, `login_tenant_index`, `module_capabilities`, `modules`, `partners`, `plan_module_entitlements`, `plan_versions`, `platform_audit_logs`, `platform_countries`, `prices`, `product_entitlements`, `products`, `protected_system_roles`, `saas_plan_entitlements`, `saas_plans`, `system_services`, `tenants`, `usage_metrics`, `workflow_external_action_token_index`.

## 2. Per-table adjudication — all 26 RLS-OFF tables

Severity scale: CRITICAL (immediate production blocker) / HIGH / MEDIUM / LOW. "Runtime ACL" = effective grants for `sanad` after `V20261103_2` + `V20261115_1` (live-verified).

### 2.1 Pre-auth credential plane (3 tables, tenant-bearing, RLS deliberately deferred)

| # | Table | Runtime ACL | Severity | Evidence & controls | Disposition | Residual risk |
|---|---|---|---|---|---|---|
| 1 | `auth_token_tenant_index` | SELECT, INSERT, UPDATE, DELETE (V20261101_1) | MEDIUM | Pre-auth discovery by design (V20261028_2 contract); `anon`/`authenticated` effective SELECT denied (doc 15 gate 4); cross-tenant visibility confirmed live (RT-05: readable w/o GUC; 0 rows at probe time) | DOCUMENTED-EXCEPTION | Pre-RLS characterization contract OPEN (doc 16 §2 A); token hashes only, no plaintext secrets |
| 2 | `login_tenant_index` | SELECT, INSERT, DELETE (V20261031_1; UPDATE revoked) | MEDIUM | Trigger-maintained (S5 live: trigger fires, resolver works no-GUC); cross-tenant identifier visibility confirmed live (RT-04: 2 distinct tenants readable w/o GUC) | DOCUMENTED-EXCEPTION | (a) pre-RLS characterization OPEN; (b) `identifier` stored plaintext — hashing follow-up recommended (T2 doc §3) |
| 3 | `workflow_external_action_token_index` | SELECT only (V20261105_2) | LOW-MEDIUM | Read-only confirmed live (RT-06 INSERT denied 42501); token-rotation stale-pointer regression GREEN 7/7 (fa12c64eb fix + this run); external-portal proof path unchanged | DOCUMENTED-EXCEPTION (hardened) | Rotation semantics regression-tested; hostile trigger-path matrix beyond rotation remains listed in doc 15 C4-F12 |

### 2.2 Tenant control-plane (1 table)

| # | Table | Runtime ACL | Severity | Evidence & controls | Disposition | Residual risk |
|---|---|---|---|---|---|---|
| 4 | `tenants` | SELECT, INSERT, UPDATE, DELETE (general set) | **HIGH (pre-mitigation)** | Live-confirmed FULL CRUD by runtime with NO RLS (RT-07 INSERT, RT-08 UPDATE incl. `status`/`suspension_reason`, RT-09 DELETE all succeeded on a sacrificial row). Controls: control-plane write path is the application's own provisioning flow; runtime identity is not exposed to tenants; no PUBLIC access; `anon`/`authenticated` denied | **REVIEW REQUIRED — REMEDIATION RECOMMENDED** | A compromised runtime SQL session can enumerate, suspend or delete ANY tenant row with no DB-level guardrail. Recommend dedicated control-plane role (or RLS with control-plane context) before production release. **NOT ACCEPTED** |

(`tenant_quota` is removed from this category by the live catalog — RLS ENABLE+FORCE, own-tenant DML only, foreign-tenant INSERT denied 42501 (RT-10/RT-10B); CHECK whitelist on `dimension`; the documented `tenant_id VARCHAR(64)` type drift is handled by the policy's text cast. Residual: type unification still recommended.)

### 2.3 Audit ledger (1 table)

| # | Table | Runtime ACL | Severity | Evidence & controls | Disposition | Residual risk |
|---|---|---|---|---|---|---|
| 5 | `platform_audit_logs` | SELECT, INSERT only — append-only (V20261115_1) | MEDIUM | UPDATE/DELETE/TRUNCATE denied 42501 live (S9, RT-22/23/24); trigger creation denied 42501 (RT-25); member-role bypass census 0 (S9.4); cleanup role cannot SELECT/UPDATE (RT-27/28); enforced fail-closed by S10 deployment gate (7/7 self-test) | HARDENED (append-only). RLS decision PENDING (global audit plane; indiscriminate FORCE RLS explicitly not applied — doc 15 gate 4) | **Forged-row insertion is possible by design** (RT-26: runtime INSERT of `RT_FORGED_PROBE` accepted, admin-verified persisted). Append-only guarantees integrity-against-modification, NOT per-row authenticity. Recommend correlation/monitoring controls on actor + correlation_id. **NOT ACCEPTED** |

### 2.4 Global platform registries / catalogs (20 tables incl. `flyway_schema_history`; no `tenant_id` by design)

| # | Table | Created by | Runtime ACL | Severity | Disposition |
|---|---|---|---|---|---|
| 6 | `modules` | V20260814_1 | SELECT (+general-set DML where granted) | LOW | DOCUMENTED-EXCEPTION (global reference; per-table necessity review PENDING) |
| 7 | `module_capabilities` | V20260814_2 | SELECT | LOW | DOCUMENTED-EXCEPTION |
| 8 | `applications` | V20260829_1 | SELECT | LOW | DOCUMENTED-EXCEPTION |
| 9 | `products` | V20260829_2 | SELECT | LOW | DOCUMENTED-EXCEPTION |
| 10 | `plan_versions` | V20260829_2 | SELECT | LOW | DOCUMENTED-EXCEPTION |
| 11 | `prices` | V20260829_4 | SELECT | LOW | DOCUMENTED-EXCEPTION |
| 12 | `country_currencies` | V20260829_4 | SELECT | LOW | DOCUMENTED-EXCEPTION (static reference data) |
| 13 | `product_entitlements` | V20260829_4 | SELECT | LOW | DOCUMENTED-EXCEPTION |
| 14 | `plan_module_entitlements` | V20260814_2 | SELECT | LOW | DOCUMENTED-EXCEPTION |
| 15 | `saas_plans` | V19 | SELECT | LOW | DOCUMENTED-EXCEPTION |
| 16 | `saas_plan_entitlements` | V19 | SELECT | LOW | DOCUMENTED-EXCEPTION |
| 17 | `system_services` | V18 | SELECT | LOW | DOCUMENTED-EXCEPTION |
| 18 | `platform_countries` | V20260904_2 | SELECT | LOW | DOCUMENTED-EXCEPTION |
| 19 | `hr_country_packs` | V20260905_12 | SELECT | LOW | DOCUMENTED-EXCEPTION |
| 20 | `hr_compliance_rules` | V20260905_12 | SELECT | LOW-MEDIUM (compliance content) | DOCUMENTED-EXCEPTION |
| 21 | `protected_system_roles` | V20261001_9 | SELECT | LOW | DOCUMENTED-EXCEPTION |
| 22 | `application_iam_contracts` | V20261005_1 | SELECT | LOW | DOCUMENTED-EXCEPTION |
| 23 | `partners` | V20261003_2 | SELECT | LOW | DOCUMENTED-EXCEPTION |
| 24 | `access_capabilities` | V7 | SELECT, INSERT, UPDATE, DELETE | **MEDIUM-HIGH** | See finding R-C4-04 below |
| 25 | `usage_metrics` | V20260830_2 | SELECT | LOW (metric-definition registry, not tenant usage data — doc 16 §2 D) | DOCUMENTED-EXCEPTION |
| 26 | `flyway_schema_history` | Flyway (system) | **NONE** (all DML denied 42501 — S3, RT-29/30) | LOW | MIGRATOR-ONLY (by privilege, not by RLS) |

**R-C4-04 (`access_capabilities`):** live-confirmed runtime INSERT of a synthetic capability code, UPDATE of an existing code's `status`, and DELETE (RT-12/13/14, sacrificial row cleaned up). The capability registry gates authorization checks (e.g. RBAC `rb12_inactiveCapability_denied`, Y2 sentinel capability). A compromised runtime session can therefore **mint, deactivate or delete capability definitions**. Controls: application control-plane is the only intended writer; `anon`/`authenticated` denied. Recommendation: restrict DML to a dedicated control-plane role before production release. **NOT ACCEPTED.**

All 20 registry tables remain part of the pending per-table necessity sweep demanded by doc 15 gate 4 / doc 16 §2 D; none is accepted by this document.

## 3. Per-function adjudication — all 12 SECURITY DEFINER signatures

Common live facts (this run + doc 14/15): owner `sanad_migrator`; `proacl={sanad_migrator=X/sanad_migrator,sanad=X/sanad_migrator}` (sanad EXECUTE 12/12, PUBLIC 0/12); production facts additionally show `service_role` EXECUTE on 11/12 (doc 15; local role absent — RT-35).

| ID | Signature | search_path (live) | Dynamic negative evidence (live) | Remaining adjudication |
|---|---|---|---|---|
| C4-F01 | `hr_assert_migration_tenant_scope(uuid)` | `pg_catalog, public, pg_temp` | missing-GUC + foreign-tenant 42501 (20/20 matrix; RT-32/33 sample re-confirm) | guard semantics GREEN for synthetic input; runtime/service execution necessity REVIEW REQUIRED |
| C4-F02 | `hr_default_migration_as_of_date(uuid)` | same | two 42501 + pg_temp shadow negative PASS (CI parity: RT-21, NOT 2099-12-31) | reporting/no-leak semantics review OPEN |
| C4-F03 | `hr_precheck_tenant(uuid)` | same | two 42501 | privileged-mutation coverage within own tenant OPEN |
| C4-F04 | `hr_precheck_tenant(uuid,date)` | same | two 42501 | as-of input review OPEN |
| C4-F05 | `hr_reconcile_tenant_report(uuid)` | same | two 42501 (RT-32 live re-confirm) | report disclosure review OPEN |
| C4-F06 | `hr_reconcile_tenant_report(uuid,date)` | same | two 42501 | report disclosure review OPEN |
| C4-F07 | `hr_reconcile_tenant(uuid)` | same | two 42501 | state-changing invocation/rollback review OPEN |
| C4-F08 | `hr_reconcile_tenant(uuid,date)` | same | two 42501 | same |
| C4-F09 | `hr_backfill_tenant(uuid)` | same | two 42501 | privileged backfill/idempotency review OPEN |
| C4-F10 | `hr_backfill_tenant(uuid,date)` | same | two 42501 | same |
| C4-F11 | `sync_login_tenant_index()` (trigger) | `pg_catalog, public, pg_temp` (source-explained by V20261104_1) | trigger fires correctly under S5 user lifecycle; no full hostile trigger-path matrix | transfer/duplicate/orphan invariant review OPEN |
| C4-F12 | `sync_workflow_external_action_token_index()` (trigger) | `pg_catalog, public` | token-rotation regression 7/7 GREEN (C4-TOKEN-01 fixed by fa12c64eb) | revoked/expired pointer & external-portal proof review OPEN |

**Aggregate residual (R-C4-07):** `sanad` EXECUTE on all 12 remains a deliberate-but-unreviewed executable surface; production `service_role` EXECUTE on 11/12 likewise (doc 15 gate 1–2). No blanket REVOKE recommended from this document (migration/trigger dependencies unproven); a per-function necessity decision with a targeted migration is required. **NOT ACCEPTED.**

## 4. The six T2-deferred cases — live status

| # | Table (T2 doc) | Live status on this chain | Evidence |
|---|---|---|---|
| 1 | `refresh_tokens` | CLOSED — RLS ENABLE+FORCE, tenant policy | S7 6/6; G6 D4 A/B isolation; S5 lifecycle |
| 2 | `password_reset_tokens` | CLOSED — RLS ENABLE+FORCE | S7; S5 |
| 3 | `login_tenant_index` | ACL CLOSURE BY DESIGN (documented exception; pre-RLS characterization + identifier-hashing follow-up OPEN) | §2.1 row 2 |
| 4 | `provisioning_jobs` | CLOSED — RLS ENABLE+FORCE fail-closed TO PUBLIC | S7; catalog gate |
| 5 | `provisioning_job_steps` | CLOSED — RLS ENABLE+FORCE via parent-job subquery, WITH CHECK | S7; G6 |
| 6 | `subscription_commands` | CLOSED — RLS ENABLE+FORCE + append-only runtime ACL (INSERT-only) | S6 live INSERT ok / UPDATE/DELETE 42501; S9 |

## 5. ACL / Default ACL / ownership / inheritance / escalation

- **Default ACL (R-C4-06):** `pg_default_acl` for `sanad_migrator` in `public`: future functions `f:{sanad=X}`, future tables `r:{sanad=arwd}`, future sequences `S:{sanad=rU}`. Least-privilege growth concern — REVIEW REQUIRED; any narrowing must be compatibility-tested on a disposable replica first (doc 15 gate 3). **NOT ACCEPTED.**
- **Ownership:** `sanad_migrator` owns all 324 public objects (incl. the 12 definers, both append-only ledgers and `flyway_schema_history`); `public` schema owner `pg_database_owner`; DB owner migrator; runtime owns 0 objects; runtime `CREATE` on `public` denied 42501 (S7, RT-20).
- **Role contract:** `sanad` = LOGIN, NOSUPERUSER, NOBYPASSRLS, NOCREATEDB, NOCREATEROLE, NOINHERIT; membership exactly `{sanad_callback_replay_cleanup (INHERIT FALSE)}`; `pg_has_role('sanad','sanad_migrator','MEMBER')=false` (S0/S9, RT-34).
- **Escalation attempts all denied live:** `SET ROLE sanad_migrator` 42501 (S3, RT-18); `CREATE ROLE` 42501 (RT-16); `GRANT sanad_migrator TO sanad` 42501 (RT-17); `ALTER ROLE … BYPASSRLS` 42501 (S3); `ALTER DATABASE` 42501 (RT-19); runtime `CREATE FUNCTION` in public 42501 (RT-20); session-level topology override possible but non-authoritative — S10 reads the catalog (S10 case 6; RT-31).
- **Inherited-privilege bypass census on both append-only ledgers: 0** (S9.4).

## 6. Tenant isolation boundary — including the structural trust boundary

- 295/321 tables ENABLE+FORCE with fail-closed `NULLIF(current_setting('app.tenant_id',true),'')` policies; live matrix: A sees A / B sees B / cross INSERT 42501 / no-GUC and empty-GUC zero-visibility (S4); cross-tenant UPDATE zero-effect (RT-03); tenant_id swap on own row denied 42501 (RT-02); G6 D4 restored-database A/B equality.
- **R-C4-02 (structural, live-confirmed RT-01):** `app.tenant_id` is set by the trusted application transaction boundary and is **not authenticated at the database layer**. A hostile session holding runtime SQL access can `SET app.tenant_id` to any tenant and read/act within that tenant's RLS scope (RT-01: foreign-tenant `users` count=1 returned under spoofed GUC). Compensating controls: runtime credentials are never distributed to tenants; the application sets the GUC per-transaction from the authenticated JWT (`TenantRlsTransactionContext`); pooler exposure is part of the pending production verification. This is the inherent limitation of GUC-based RLS already recorded in doc 15 gate 5 — now confirmed by execution. Remediation evaluation (e.g. role-per-tenant, signed session context, or pooler-level enforcement) is RECOMMENDED; **NOT ACCEPTED**.

## 7. service_role

Absent in the local separated topology (RT-35) — local verification impossible. Production read-only facts (doc 15, 2026-11-13): EXECUTE on 11/12 definer functions. Per-function necessity review and production re-verification remain REQUIRED and BLOCKED (no authorized production access in this engagement).

## 8. S9 / S10 — actual results (this adjudication, live)

- **S0–S9 separation certification: PASSED 75/75**, exit 0 (roles/contract, Flyway chain head `20261115.1`, runtime DML, DDL/ledger/escalation denials, tenant isolation, auth plane, append-only subscription ledger, ownership/ACL snapshot, runtime append-only enforcement with live DML denials). Artifacts: `evidence-pr2/separation-cert/` (matrix.tsv, S1 logs, summary.md).
- **S10 protected-deployment gate self-test: PASSED 7/7** (separated PASS; fixture/missing/empty/unknown topology refusals; session-override refusal; inherited-bypass detection + restoration). Artifacts: `evidence-pr2/s10-gate/`.
- CI counterpart: separation workflow SUCCESS on content-identical PR head (run 44283363382); PMV JOB D SUCCESS on the merge SHA.

## 9. Residual-risk register (summary — ALL PENDING authorized owner decision)

| ID | Risk | Severity | Remediation path | Status |
|---|---|---|---|---|
| R-C4-01 | `tenants` full CRUD by runtime, no RLS | HIGH | dedicated control-plane role / RLS with control-plane context; fail-closed tests first | PENDING-OWNER |
| R-C4-02 | GUC tenant context not DB-authenticated (RT-01) | HIGH (inherent) | evaluate pooler/session-level enforcement or per-tenant roles; keep JWT→GUC transaction discipline | PENDING-OWNER |
| R-C4-03 | Audit ledger accepts forged runtime INSERTs (RT-26) | MEDIUM | actor/correlation monitoring; consider write-role separation | PENDING-OWNER |
| R-C4-04 | `access_capabilities` DML by runtime (RT-12/13/14) | MEDIUM-HIGH | restrict registry DML to control-plane role | PENDING-OWNER |
| R-C4-05 | Pre-auth plane RLS characterization open (2 tables) + plaintext `identifier` | MEDIUM | characterization contract; identifier hashing | PENDING-OWNER |
| R-C4-06 | Default ACL future-grant growth | MEDIUM | narrow default ACLs after replica compatibility test | PENDING-OWNER |
| R-C4-07 | `sanad` EXECUTE 12/12; production `service_role` 11/12 | MEDIUM | per-function necessity decision + targeted migration | PENDING-OWNER |
| R-C4-08 | Global audit-plane RLS decision pending | LOW-MEDIUM | owner decision on audit read path | PENDING-OWNER |
| R-C4-09 | Registry per-table necessity sweep (20 tables) | LOW-MEDIUM | owner review sweep | PENDING-OWNER |

**No entry in this register is accepted, waived or closed by this document.** Per the Owner Risk Acceptance Register rules, acceptance requires a documented owner decision with expiry/review date and compensating controls; remediation supersedes acceptance.

## 10. What this document is NOT

- Not a G1 certificate; not a production, migration or payment authorization; no production SQL was executed.
- The Issue #1462 C4 checkbox should remain **unchecked** until the authorized owner records dispositions on this register and the production-verification items (service_role, production catalog recheck) close — the *technical adjudication* itself is hereby delivered, but the checkbox wording ("risk-based evidence-backed technical adjudication") also subsumes the owner's risk disposition of the residual register above.
- Local runs used PostgreSQL 17.12 (CI pin is 17.6); CI on the merge SHA remains the authoritative pin certification (PMV JOB C SUCCESS).

`P0_SECURITY_HOLD=ACTIVE` · `G1=HOLD` · `PRODUCTION_RELEASE=NO-GO` · `PAYMENTS=OFF`.
