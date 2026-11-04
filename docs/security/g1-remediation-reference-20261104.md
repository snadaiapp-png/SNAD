# SANAD G1 — Locked remediation reference (2026-11-04)

**Reference ID:** SNAD-G1-FORENSIC-CLOSURE-20261104
**Evidence baseline:** Git HEAD `a98fe3db5b1037f26612ed33575bfe00d3159fb7`; isolated local PostgreSQL 17 on 127.0.0.1:55474; DB `g1_restore_cert_20261104_v3`; cluster ID `7703842253085562712`.
**Status:** G1 HOLD / Production NO-GO / Payments OFF. This ledger does not grant deployment approval.
**Scope:** source, migrations, safety evidence, on-transaction database tests. Production and third-party security approval are excluded until separately evidenced.

## Evidence-backed issues and acceptance gates

| ID | Severity | Evidence | Required acceptance | Current state |
|---|---|---|---|---|
| F01 | High | Tenant transfer retained 2 stale login index entries under old tenant (transactional test EXIT 3). | New migration tested via actual Flyway + inserted/updated/transferred/deleted fixture under transaction. No old mappings. | Candidate V20261104_1 tested using psql transaction; not deployed or CI-integrated. |
| F02 | Critical | Under sanad, RLS inactive on 19 CRM tables; all 19 `tenant_isolation` policies also allow NULL `app.tenant_id`, so FORCE alone is insufficient. | Both FORCE RLS and fail-closed USING/WITH CHECK, plus A/B and absent-GUC tests, direct PostgreSQL. | OPEN — 19/19 fail-open policy expressions verified 2026-11-04 |
| F03 | Critical | `sanad` owns public tables and has implicit TRUNCATE privileges including CRM and login index. | Separated ownership and runtime no TRUNCATE/DDL verified after *Flyway running in separated topology* on restored scratch DB. | OPEN |
| F04 | High | `sanad` owns flyway_schema_history; SELECT and TRUNCATE are true in fixture DB. | Runtime cannot access/alter Flyway ledger; migration runner separated; verify PostgreSQL denies. | OPEN |
| F05 | High/Operational | 1 users row and 0 login index rows; count missing mappings=1 in both source and restore. Pre-auth migration documents need for historical backfill. | Idempotent, independently reviewed backfill with reconciled counts and zero unexpected mappings. | OPEN |
| F06 | High | Four indexed/replay tables have tenant_id with RLS disabled. | Document trust boundary and caller-level ACL; protect raw access while preserving pre-auth discovery. | REVIEW |
| F07 | Release blocker | No independently verified production security or A/B acceptance or signed security review. | Post-merge SHA, CI, backup/restore, rollback, direct RLS proof, independent sign-off. | BLOCKED |
| F08 | Disproven in tested scenario | Attempted same-tenant duplicate username insert was rejected by `uk_users_tenant_username_ci` (psql EXIT 3, expected unique violation). | Preserve unique constraint; add regression covering duplicate rejection and no orphaned state. | DISPROVEN for direct duplicate insertion; broad race scenarios not tested |

## Non-negotiable rules
1. Never edit previously applied Flyway migration files. Forward-only migration names, checksum verification, explicit topology guard.
2. No production write, PAYMENTS toggle, deploy, merge, or G1 sign-off without evidence and approvals.
3. Exact local cluster/database validation before writes. All exploratory writes inside BEGIN/ROLLBACK on isolated restored fixtures.
4. Privileged postgres tests do not prove runtime RLS; require separate non-owner runtime test and least-privilege proof.
5. RLS-disabled pre-auth index is *intentional*; no blanket FORCE RLS there without redesign. Do not expose identifiers to untrusted clients.
6. Record tests with date, HEAD, database cluster ID, test command, exit code, actual results; report skips or failures explicitly.

## Execution record (local only)
- Prior: INSERT, same-tenant UPDATE, DELETE trigger behavioral checks PASS and ROLLBACK.
- Prior: historical login index mismatch documented.
- Prior: baseline tenant-transfer test FAIL old mappings 2; candidate forward migration transaction PASS four scenarios; ROLLBACK.
- Post-rollback: users=1, login index=0, function original MD5 unchanged.
- 2026-11-04: read-only release gate executed on V3: 19 non-FORCE tenant tables; 19 CRM policies with absent-GUC OR branch; 321 runtime-owned/TRUNCATE-privileged public tables. Query execution EXIT 0 (the security gate itself remains FAIL).
- 2026-11-04: same-tenant duplicate username rejected by actual database unique constraint `uk_users_tenant_username_ci` (expected SQLSTATE 23505; prior SQL runner exited 3). No claim of successful collision scenario.
- 2026-11-04: Maven compile of apps/sanad-platform completed with BUILD SUCCESS from its own pom.xml; previous root -pl attempt FAILED (project absent). Compile is not tests.
- Current scope: all additional findings require dedicated tests; there is no production certification.

## Exit gates
G1 may be marked PASS **only** after F01–F08 are resolved with linked artifacts, all mandatory integration tests and post-merge CI run successfully on the final commit, production readiness verified under change control, and independent security approver signs. No percentage-complete metric without a locked metric denominator.
