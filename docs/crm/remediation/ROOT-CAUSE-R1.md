# ROOT-CAUSE-R1 — CRM Migration / RLS Test Failures (CORRECTED)

| Field | Value |
|-------|-------|
| Workstream | R1 — Maven Migration Contract (RECOVERY-CRM-022) |
| Date | 2026-07-31 |
| Repo | `snadaiapp-png/SNAD` |
| Base SHA | `61cf9a5b13473c131b4ed43f7cb6442499917d56` |
| Status | **CORRECTED.** Initial hypothesis disproven by CI. Real root cause below. |
| Security impact | **Yes — tenant isolation (RLS) is silently defeated in full-migrate.** |

> **Correction notice (2026-07-31):** The first version of this document
> concluded "the test expectation is incorrect; the migrations are correct"
> and proposed reverting a version constant to `20260729.2`. CI on the
> resulting PR (#831) **disproved** that: the test still failed 3/4, and
> additionally surfaced `CrmRlsTenantIsolationPostgresTest` failing 7/9 with
> tenant-isolation assertions (`expected: 1L but was: 2L`). The real root
> cause is a migration-sequence defect, not a constant value. This document
> supersedes the earlier analysis. The constant-revert change in #831 is
> **withdrawn** (PR marked DO NOT MERGE).

---

## 1. Symptom (evidence from CI)

On `main` @ `61cf9a5b`, three test classes fail:

| Class | Result | Root failure |
|-------|--------|--------------|
| `CrmPostgresMigrationTest` | 3 of 4 fail | version/list assertions |
| `Crm008bFoundationAcceptanceTest` | 1 of 11 fails | version assertion |
| `CrmRlsTenantIsolationPostgresTest` | **7 of 9 fail** | **`expected: 1L but was: 2L` — isolation not enforced** |

The migration-version failures are a *symptom*. The RLS isolation failures
are the *substantive defect*.

## 2. The real root cause — enable→disable RLS on the forward path

Two CRM-018 migrations exist in `db/vendor/postgresql/`:

| File | Version | Effect |
|------|---------|--------|
| `V20260730_1__enable_crm_row_level_security.sql` | `20260730.1` | **ENABLE** RLS + create `tenant_isolation` policy on every `crm_*` table with `tenant_id` |
| `V20260730_2__disable_crm_row_level_security.sql` | `20260730.2` | **DISABLE** RLS + drop the policy. Self-described as "Rollback migration for V20260730_1." |

Flyway runs migrations in version order. Under `flyway.migrate()` with **no
target** — which is how `installsCompletedCrmOnCleanPostgresDatabase`,
`CrmRlsTenantIsolationPostgresTest.migrateAndSeed`, **and production** all
run — both execute in the same pass:

```
... 20260729.2 (seed scoring models) → 20260730.1 (ENABLE RLS) → 20260730.2 (DISABLE RLS)
```

**Net effect: RLS is enabled and then immediately disabled. Tenant isolation
ends up OFF.** This is why:

1. `CrmRlsTenantIsolationPostgresTest.selectWithTenantContextReturnsOnlyOwnRows:123`
   sees **2 rows** (both tenants) instead of 1 — RLS is not filtering.
2. `latestVersion(...)` returns `20260730.2` (the disable runs last) — so the
   migration test's terminal-version assumption (`latest == scoring models`)
   breaks.
3. The `containsExactly` pending-list assertions (which enumerate every
   expected pending migration and end at the scoring-models version) now have
   two extra entries (`20260730.1`, `20260730.2`) → "some elements were not
   expected."

## 3. Why PR #826 and my #831 both failed

| Attempt | Change | Effect on the 3 assertions |
|---------|--------|----------------------------|
| Original (CRM-010 era) | constant = `20260729.2` | Worked **before** CRM-018 RLS migrations existed. Broke once `V20260730_1/2` were added (pending-list + latestVersion). |
| PR #826 | constant → `20260730.2` | Fixed `latestVersion` and pending-list (20260730.2 is genuinely last) **but** broke `assertMigration(version, "seed default scoring models")` because 20260730.2's description is "disable crm row level security". |
| PR #831 (this author, withdrawn) | constant → `20260729.2` | Fixed `assertMigration` description match **but** broke `latestVersion` (full-migrate still reaches 20260730.2) and the pending-list. Did **not** touch the real RLS defect. |

**No single value of the constant can satisfy all three assertions**, because
the constant is overloaded: it is simultaneously "the scoring-models
migration" (description match) and "the terminal migration" (latestVersion).
After CRM-018, those are two different versions.

## 4. The substantive defect (security)

A **disable-RLS migration must not sit on the forward migration path.** As
written, any environment that runs `flyway.migrate()` to head — including
production — ends up with RLS disabled and tenant isolation inactive, while
the codebase believes CRM-018 delivered "defense-in-depth tenant isolation"
(see the enable migration's header comment). This is a silent,
security-significant regression that predates CRM-022.

The `CrmRlsTenantIsolationPostgresTest` was written to *prove* RLS works; its
7/9 failure is the test correctly catching that it does not.

## 5. Why this is an architecture decision, not a constant fix

Candidate remediations all have trade-offs that are **not mine to choose
unilaterally** in a recovery operation with a "no production changes without
verified cause / do not guess" mandate, especially given the security angle:

- **Option A — Remove `V20260730_2` (disable) from the forward path.** RLS
  stays enabled; isolation works; the migration test's terminal version
  becomes `20260730.1`. Cleanest for security, but deletes a migration others
  may depend on (rollback tooling, other envs) and changes Flyway history.
- **Option B — Relabel/disable-RLS as a non-Flyway rollback script** (move out
  of `db/vendor/postgresql/`). Keeps it available for manual rollback without
  running on every migrate.
- **Option C — Keep both migrations but re-target the migration tests** so
  they stop at `20260730.1`, and accept that production full-migrate leaves
  RLS disabled (i.e., accept the isolation gap — NOT recommended).
- **Option D — Add `FORCE ROW LEVEL SECURITY` / owner handling** so the
  disable is benign — does not address the enable→disable ordering.

Each option needs a human decision because it changes production migration
behavior and/or security posture.

## 6. Disposition

- **R1 (as originally scoped: "fix the Maven migration constant") is not
  achievable by a constant change.** The premise was wrong.
- The real defect spans R1 (migration test) **and** tenant isolation (RLS),
  and has security impact.
- PR #831 is **DO NOT MERGE** (constant-revert is incorrect; would not fix
  isolation and would not pass CI).
- A corrected fix requires the architecture decision in §5 and likely a new
  PR (e.g., remove/relocate `V20260730_2`) **plus** corresponding test
  updates. This must be authorized before code changes.

## 6a. RESOLUTION (applied under RECOVERY-CRM-022 R1, Option A)

**Decision (human-approved, 2026-07-31):** Option A — remove the disable-RLS
migration from the Flyway forward path; retain it as a manual rollback script.

**Changes (PR: recovery-crm-022/r1-rls-migration-fix):**
1. Moved `V20260730_2__disable_crm_row_level_security.sql` out of
   `db/vendor/postgresql/` to `docs/runbooks/CRM-018-RLS-DISABLE-rollback.sql`
   (operator-applied manual rollback; NOT run by Flyway). Added a header
   explaining why it is off the forward path.
2. Removed the H2 test mirror
   `src/test/resources/db/vendor/h2/V20260730_2__disable_crm_row_level_security.sql`
   to preserve Flyway version parity (otherwise H2 tests would report a
   missing-migration validation error vs the PostgreSQL path).
3. `CrmPostgresMigrationTest`: restored `CRM_010_SCORING_MODELS_VERSION =
   "20260729.2"` (correct description match for `assertMigration`); added
   `CRM_018_RLS_ENABLE_VERSION = "20260730.1"` (new terminal migration);
   appended it to both `containsExactly` pending lists; changed the
   `latestVersion` assertion and added an `assertMigration` for the
   RLS-enable migration.
4. `Crm008bFoundationAcceptanceTest`: same constant corrections; `latest`
   assertion now expects `CRM_018_RLS_ENABLE_VERSION`.

**Why this fixes all three failure classes:**
- `CrmPostgresMigrationTest`: terminal version is now consistently
  `20260730.1` across the `latestVersion` assertion, the pending lists, and
  the Flyway target (`flyway(null)` full-migrate stops at the new terminal).
  `assertMigration` matches the correct descriptions for both scoring models
  (`20260729.2`) and RLS enable (`20260730.1`).
- `CrmRlsTenantIsolationPostgresTest`: full-migrate no longer runs a disable
  after the enable, so RLS stays **enabled** and the 7 isolation tests that
  expect `1L` (own-tenant-only rows) should pass. The
  `rollbackMigrationDisablesRls` test is unaffected — it inlines its own
  disable-RLS SQL and never invoked `V20260730_2` via Flyway.
- `Crm008bFoundationAcceptanceTest`: `latest` matches the new terminal.

**Security outcome:** production `flyway.migrate()` now leaves RLS
**enabled** on all CRM tables (defense-in-depth tenant isolation active),
matching CRM-018's stated intent.

**Local verification:** could not run Testcontainers locally (Docker daemon
unavailable in authoring environment). CI on this PR is the authoritative
verification (Maven Test Suite, CRM G1 Schema Isolation, and the RLS
isolation test must all go GREEN).

## 7. Evidence index

- CI run on PR #831 (`30589421585`): `CrmPostgresMigrationTest` 3/4 fail,
  `Crm008bFoundationAcceptanceTest` 1/11 fail, `CrmRlsTenantIsolationPostgresTest` 7/9 fail.
- `installsCompletedCrmOnCleanPostgresDatabase:243 -> assertCompletedSchema:348`:
  `expected: "20260729.2" but was: "20260730.2"`.
- `CrmRlsTenantIsolationPostgresTest.selectWithTenantContextReturnsOnlyOwnRows:123`:
  `expected: 1L but was: 2L`.
- `V20260730_1__enable_crm_row_level_security.sql` — ENABLE RLS + policy.
- `V20260730_2__disable_crm_row_level_security.sql` — DISABLE RLS + drop policy,
  header: "Rollback migration for V20260730_1".
- `CrmRlsTenantIsolationPostgresTest.migrateAndSeed` (line 64-74) uses
  `flyway.migrate()` with no target → both RLS migrations run.
