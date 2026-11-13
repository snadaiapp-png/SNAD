# SNAD G1/C4 — signed-review decision packet, exact main 354598c1

**State: TECHNICAL SOURCE/CATALOG ASSESSMENT ONLY — INDEPENDENT HUMAN SECURITY SIGNATURE PENDING.**  
Release: `354598c17bdbf66fe349e6a94bb9cf084be6ba7c` · Current result: `C4=HOLD`, `G1=HOLD`, `PRODUCTION_RELEASE=NO-GO`, `PAYMENTS=OFF`.  
Prepared 2026-11-13. No production mutations, release dispatch, signature, or payment changes.

## Primary evidence, separated by scope

- [Merge PR #1525](https://github.com/snadaiapp-png/SNAD/pull/1525) included a review approving **test implementation only**, *not* C4. [Post-Merge #43423404060](https://github.com/snadaiapp-png/SNAD/actions/runs/43423404060): all A–F jobs SUCCESS; JOB F actual log: `PMV_FINAL_GATE=PASS`, `result=PASS`, `criticalFailures=[]`. [Manifest artifact #14040480371](https://github.com/snadaiapp-png/SNAD/actions/runs/43423404060/artifacts/14040480371).
- [Isolated PG17.6 #43409260500](https://github.com/snadaiapp-png/SNAD/actions/runs/43409260500): 10 HR functions × 2 negative contexts = **20 actual 42501 denials PASS** as `sanad`; script `tests/security/g1-c4/hr-definer-tenant-negative-matrix.sql`, complete output in job artifacts. All tests roll back and are **not production acceptance**.
- Production Supabase project `tkbrvupemreqabwzdpyq`, **read-only SQL catalog examination only**, 2026-11-13: Flyway successful versioned head `20261111.1`; 12 security-definer functions owned by `sanad_migrator`. All 12 explicitly grant runtime `sanad` EXECUTE, 11 explicitly grant `service_role` EXECUTE; none grant `PUBLIC`, `anon`, or `authenticated` EXECUTE (effective checked). These are **catalog facts**, not authorization-level penetration tests.
- Original source `apps/sanad-platform/src/main/resources/db/migration/R__finalize_hr_backfill_closure.sql` holds the 10 HR functions and an explicit tenant-scope checker. `V20261104_1__login_tenant_index_transfer_consistency.sql` explicitly **redefines** `sync_login_tenant_index()` to `search_path=pg_catalog, public, pg_temp`; thus the historical V20261011_4 shorter setting's discrepancy is **SOURCE EXPLAINED**, not unresolved. `V20261029_3__workflow_external_action_preauth_token_index.sql` defines the second trigger with `search_path=pg_catalog, public`.

## Function-by-function disposition

**Common audited actual production facts:** Owner=`sanad_migrator`, `PUBLIC=false`, `anon=false`, `authenticated=false`, runtime `sanad EXECUTE=true`; all paths explicit. For rows 1–11, path=`pg_catalog, public, pg_temp`; for row 12 path=`pg_catalog, public`.

| ID | Exact function signature | service_role EXECUTE | Exercised dynamic negative evidence | Security disposition / remaining adjudication |
|---|---|---|---|---|
| C4-F01 | `hr_assert_migration_tenant_scope(uuid)` | YES | missing-GUC and foreign tenant 42501 PASS | Guard behavior PASS for synthetic input; justify runtime/service execution and GUC trust boundary |
| C4-F02 | `hr_default_migration_as_of_date(uuid)` | YES | same two 42501 PASS; separate single `pg_temp` shadow probe PASS | Check authorized tenant reporting/no-leak semantics |
| C4-F03 | `hr_precheck_tenant(uuid)` | YES | two 42501 PASS | Privileged mutation and authorized-input tests/rollback coverage needed |
| C4-F04 | `hr_precheck_tenant(uuid,date)` | YES | two 42501 PASS | As-of input and privileged mutation behavior review needed |
| C4-F05 | `hr_reconcile_tenant_report(uuid)` | YES | two 42501 PASS | Verify reporting disclosure, real tenant A/B data and authorization |
| C4-F06 | `hr_reconcile_tenant_report(uuid,date)` | YES | two 42501 PASS | Verify reporting disclosure, real tenant A/B data and authorization |
| C4-F07 | `hr_reconcile_tenant(uuid)` | YES | two 42501 PASS | Authorized state-changing invocation / rollback review needed |
| C4-F08 | `hr_reconcile_tenant(uuid,date)` | YES | two 42501 PASS | Authorized state-changing invocation / rollback review needed |
| C4-F09 | `hr_backfill_tenant(uuid)` | YES | two 42501 PASS | Privileged backfill mutation, rollback/idempotency and use-role review needed |
| C4-F10 | `hr_backfill_tenant(uuid,date)` | YES | two 42501 PASS | Privileged backfill mutation, rollback/idempotency and use-role review needed |
| C4-F11 | `sync_login_tenant_index()` | YES | Existing S5 user/login integration; no full hostile trigger-path matrix | source resolved by V20261104_1; check tenant transfer, email/username index history, DML grants, trigger enabled, duplicate/orphan invariants |
| C4-F12 | `sync_workflow_external_action_token_index()` | NO | No full hostile trigger-path matrix established | Review new/old token rotation, revoked/expired token and index consistency, cross-tenant pointer ownership and external-portal proof |

**Source-derived hypothesis C4-TOKEN-01 (NOT DYNAMICALLY CONFIRMED):** The `V20261029_3` trigger's `UPDATE` branch with *new non-null `NEW.token_hash`* inserts/upserts the new hash but does **not** delete the old hash: old-hash deletion appears only in the `ELSIF` branch, which is not reached when the new hash is non-null. Possible stale index pointer on token rotation. Whether application updates token hashes in place, or an old pointer yields any actual unauthorized access, remains **UNVERIFIED**. Request a fixture-level `OLD.token_hash != NEW.token_hash` assertion of old pointer absence and authoritative row revalidation; do not call an exploit or fix without a failing reproducible test.

## Explicit effective ACL decision gates

1. `sanad EXECUTE` on **all 12**: **REVIEW REQUIRED**; no sufficient business-call-path evidence for universal EXECUTE grants. Do not blanket REVOKE from production: confirm relevant migration/backfill/trigger dependencies and create targeted migration after review.
2. `service_role EXECUTE` on **11**: **REVIEW REQUIRED**; prove role usage and why this privileged path needs each HR/backfill routine, and verify externally exposed RPC/API route restrictions.
3. `pg_default_acl` in `public` for `sanad_migrator`: `f:{sanad=X}` (future functions), `r:{sanad=arwd}` (future tables), `S:{sanad=rU}` (future sequences). **LEAST-PRIVILEGE RISK — NOT CERTIFIED**. Design narrowly scoped explicit grants and test live compatibility in disposable replica before altering production.
4. Production RLS exception review: **321** base/partitioned public tables, **295** ENABLE+FORCE RLS, **26** RLS-OFF. In particular, `tenants` and `platform_audit_logs` grant runtime `sanad` SELECT/INSERT/UPDATE/DELETE without RLS. All 26 tested `anon` and `authenticated` effective SELECT denied. **Actual privilege condition, but no external exploitation proved.** Each exception requires owner, privilege necessity, control-plane entry-point and fail-closed tests. Do not apply FORCE RLS indiscriminately to global/pre-auth tables.
5. Tenant GUC is supplied by the trusted application transaction boundary. A malicious holder of `sanad` SQL access can generally set custom GUCs; verify the **application** enforces tenant identity before setting `app.tenant_id`. The 20/20 tests do **not** establish an independently authenticated GUC.

## Human-review signature block — UNISSUED

- Reviewer real-world name and employing entity: **UNVERIFIED**.
- GitHub actor, identity-control relationship and non-owner independence: **UNRESOLVED**. Historic `abdulrhmansenan1985-creator` PR approval does not establish independent third-party review.
- Exact SHA reviewed: **must be recorded**.
- Each C4-F01..F12 + C4-TOKEN-01 and default ACL/26 RLS-off disposition: **PENDING**.
- Decision `APPROVED` / `CHANGES_REQUESTED` / `HOLD`, reasoning, accepted residual risk and time: **PENDING**.
- Signer: **NO SIGNATURE**; this document is an evidence packet, not an approval.

## Ordered release dependencies

1. Independent C4 source/security review and closure findings; isolated negative tests for token rotation and runtime grants; 26-table RLS exception decision.
2. [Backup/restore approval #1481](https://github.com/snadaiapp-png/SNAD/issues/1481) and **production-derived full-scope** recovery plus key custody/ACL/roles/RLS/rollback compatibility attestation.
3. New exact-SHA [CAB #1517 is for earlier `286f905e`](https://github.com/snadaiapp-png/SNAD/issues/1517) and is not authorization for `354598c1`.
4. Protected Render/Vercel deploy and exact-sha/digest/domain verification only AFTER the above approvals. Previously observed Render backend still built from `48322d0b`, and Vercel list deployments returned 403; the frontend identity is unverified.
5. Actual 3-user employee/manager/HR production acceptance; prior [3-user run 43423404248](https://github.com/snadaiapp-png/SNAD/actions/runs/43423404248) **SKIPPED**.
6. Separate authorized G1-security and production readiness certificates; separate payments decision.

**Invariants:** `P0_SECURITY_HOLD=ACTIVE` · `G1=HOLD` · `PRODUCTION_RELEASE=NO-GO` · `PAYMENTS=OFF`.
