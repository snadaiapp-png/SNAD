# 05 — FLYWAY & MIGRATION VERIFICATION (Phase 4.1)

**Exact SHA:** `f95a30b64e75815d571e582e1b555c01bcd53abe`
**Tooling:** PostgreSQL **17.6.0** (target-major match) + Flyway CLI **11.7.2** (project version), fully isolated host-native cluster; worktree `wt-final` checked out at the exact SHA; **no `flyway repair`, no `baseline`, no global `outOfOrder=true`** — anywhere, at any point.
**Logs:** `finalclosure-flyway-rehearsal{,-v2,-v3,-v4}.log` + per-scenario captures in `evidence-raw/`.

## 4.1 Scenario A — Fresh installation (full chain + validate)

```
"Successfully applied 250 migrations to schema "public", now at version v20261102.2"
A_MIGRATE_EXIT=0   A_MIGRATE_EXIT_CODE=0   A_VALIDATE_EXIT_CODE=0
A_HISTORY_SUCCESS_COUNT=250   A_HISTORY_FAILURE_COUNT=0
```

- Chain composition: **222 versioned migration files** (+ vendor/repeatable set = 250 applied), ending `v20261102.2`.
- All five mandated migrations present and checksum-valid under validate.
- Chain-only runtime grants verified post-migration (03 §2.0) — privileges provably originate in the ledger.

Checksum manifest (SHA-256 of the five mandated files at f95a30b6 — raw: `final-key-migration-sha256.txt`):

| Migration | SHA-256 (full, raw: `final-key-migration-sha256.txt`) |
|---|---|
| V20261028_2__d6_auth_token_tenant_index_and_rls.sql | `7dd365bf61cdde2adce359a73e4ca49f3b2d3f894aaa309d445d1eb1ed1f0eaf` |
| V20261031_1__d6_t2_batch2_rls_closure.sql | `ef2cfa97528cc9c45da7c363dd59daad421d5a6855ad73bd7da4c8af0913d8f9` |
| V20261101_1__auth_token_index_acl_and_identity.sql | `9117c7a8b8c70e53602c9a82f9ae2fc88f2b351db618861077b282c0ae7787de` |
| V20261102_1__auth_token_runtime_grants.sql | `3da2b86fd8df039d13daf536aef2e6e8a1f2968af82e7b0194d9be74941df938` |
| V20261102_2__t2_batch2_runtime_grants.sql | `759d9960585786b9580a12a17330a696e9e12fa298437f775b322750798dd576` |

(Flyway-computed checksums for these versions were additionally validated by `flyway validate` = OK on every scenario database; the file manifest pins the exact content audited.)

## 4.2 Scenario B — Historical upgrade with data preservation (rehearsal B3)

Starting point: a database migrated to the recognized historical ledger state **`20261021.1`** (240 migrations), then seeded with live-shaped data (tenant + user + ACTIVE refresh token), then upgraded with the **full current chain**:

```
B3_TARGET_MIGRATE_EXIT=0
SEED tenants=2 users=2 tokens=1
"Successfully applied 10 migrations … now at version v20261102.2"
B3_UPGRADE_EXIT=0   B3_VALIDATE_EXIT=0   "Successfully validated 250 migrations"
B3_POST_TENANT=1   B3_POST_USER=1   B3_POST_TOKEN=1      ← all seeded data preserved
```

The 10 applied migrations were exactly `20261026.4, 20261028.1, 20261028.2, 20261029.1, 20261029.2, 20261029.3, 20261031.1, 20261101.1, 20261102.1, 20261102.2` (raw: `final-B3-new-migrations.txt`) — all `success=true`, forward-only, no data-erasing statement in any of them (audited: the only TRUNCATE mention in the batch is a comment).

> Harness honesty note: two earlier B-seed attempts failed on schema-validity (status CHECK is uppercase `ACTIVE/USED/REVOKED` at every chain state; `authorization_version` is `bigint` at 20261021.1). The failures were harness-side, corrected in v3/v4; no chain behavior was affected. Recorded for audit transparency.

## 4.3 Scenario C — Out-of-order exposure of the pre-#1458 fleet (rehearsal C3)

**The real fleet risk, reproduced exactly:** main was at `b5f73cac6` (218-file chain, max version `20261031.1`) **before** PR #1458 landed. Any database migrated in that window holds `20261031.1` **without** `20261028.2`. Upgrading that fleet to the current chain:

```
C3_PRE_MIGRATE_EXIT=0            (chain at b5f73cac6 → 246 migrations, top 20261031.1)
C3_PRE_HAS_20261028_2=0          (20261028.2 absent — the exposure)
SEED tenants=2 users=2 tokens=1  (data present)
C3_DEFAULT_MIGRATE_EXIT=1
"ERROR: Validate failed: Migrations have failed validation"
"Detected resolved migration not applied to database: 20261028.2."
C3_TOKENS_AFTER_REFUSAL=1   C3_TENANT_AFTER_REFUSAL=1   ← data untouched after refusal
```

- **Default behavior = fail-closed refusal** (exit 1, upgrade blocked, zero side effects). Correct: a fleet cannot silently skip a resolved migration.
- **`outOfOrder=true` was NOT adopted** — globally or per-run — in any artifact of this command. A single isolated probe (`-outOfOrder=true` on the throwaway rehearsal database only) demonstrated the catch-up path applies the 4 pending migrations (`20261028.2 [out of order], 20261101.1, 20261102.1, 20261102.2`) and validates clean afterwards, with seed data intact (`C3_TOKENS_AFTER_CATCHUP=1`). This probe exists only to bound the remediation cost for the affected fleet window; adopting it for any real database is **PROHIBITED by mandate** and requires a separate authorized change with a scoped target-database list.
- **Necessity/scope statement for the record:** the exposure window is "databases migrated from main between the merges of #1464 (`b5f73cac6`) and #1458 (`12f92d7cf`)" (2026-11-01→2026-11-02). Production identity of such databases cannot be proven from the sandbox (see 06/08) → any fleet remediation is **BLOCKED** pending the production verification gate, and must use a scoped, explicit mechanism (never a global default).

## 4.4 Scenario D — Authorized backup restore rehearsal

→ See **06-BACKUP-RESTORE-CERTIFICATION.md**: `BLOCKED` (no authorized backup was provided through any secure channel). Per the command, no artificial/synthetic database may be presented as a substitute, and none was.

## 4.5 Migration-ownership & restricted-runtime rules verified

- Every scenario ran migrations as the migrator (`postgres`) and runtime probes as restricted `sanad` — matching the deployment model documented in `docker/postgres-init/01-create-restricted-runtime-role.sh` (runtime role created/normalized `NOSUPERUSER … NOBYPASSRLS` at first init; compose files require `DATABASE_USERNAME`).
- Pending-migration detection, checksum validation, and transactional rollback all behaved fail-closed in every probe.

**FLYWAY_MIGRATION_VERIFICATION = PASS** for Scenarios A/B/C at `f95a30b6`; Scenario D remains BLOCKED (06).
