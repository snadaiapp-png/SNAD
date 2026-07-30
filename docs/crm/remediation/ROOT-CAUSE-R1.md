# ROOT-CAUSE-R1 — Maven Migration Test Failure

| Field | Value |
|-------|-------|
| Workstream | R1 — Maven Migration Contract (RECOVERY-CRM-022) |
| Date | 2026-07-31 |
| Repo | `snadaiapp-png/SNAD` |
| Base SHA | `61cf9a5b13473c131b4ed43f7cb6442499917d56` (CRM-022 failed-gate tip) |
| Failing check | `Maven Test Suite`, `CRM G1 Schema Isolation`, `Post-Merge Verification` |
| Failing class | `com.sanad.platform.crm.web.CrmPostgresMigrationTest` (3 of 4 tests) |
| Disposition | **Test expectation was incorrect (introduced by PR #826). Migrations are correct.** |

---

## 1. Symptom (evidence)

From CI run `30578916574` (Post-Merge Verification) and run `30578916619`
(CRM G1 Schema Isolation), the same single class fails:

```
[ERROR] Tests run: 4, Failures: 3, Errors: 0 … <<< FAILURE! -- in
        com.sanad.platform.crm.web.CrmPostgresMigrationTest
[ERROR] CrmPostgresMigrationTest.installsCompletedCrmOnCleanPostgresDatabase:241
        -> assertCompletedSchema:344 -> assertMigration:530
        org.opentest4j.AssertionFailedError:
        expected: 1L
         but was: 0L
```

Failing sub-tests:
- `installsCompletedCrmOnCleanPostgresDatabase`
- `upgradesExistingPlatformThroughCrmRbacAndCompletion`
- `upgradesUnifiedCrmCoreThroughReconciliationAndCompletion`

The 4th test (`jsonbColumnsHaveExactPostgresCatalogValues`) passes.

## 2. The assertion under test

`CrmPostgresMigrationTest.assertMigration` (line ~530):

```java
private void assertMigration(JdbcTemplate jdbc, String version, String type, String description) {
    assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM flyway_schema_history WHERE version=? AND type=? AND description=? AND success=TRUE",
            Long.class, version, type, description)).isOne();
}
```

`assertCompletedSchema` calls it (line 344):

```java
assertMigration(jdbc, CRM_010_SCORING_MODELS_VERSION, "SQL", "seed default scoring models");
assertThat(latestVersion(jdbc)).isEqualTo(CRM_010_SCORING_MODELS_VERSION);
```

So the test demands that **one** row exist with
`(version = CRM_010_SCORING_MODELS_VERSION, type='SQL', description='seed default scoring models', success=TRUE)`,
and that this same version be the latest applied. `expected: 1L / but was: 0L` means **no row matched** that triple.

## 3. Migration inventory on disk (evidence)

| File | Flyway version | `description` (from filename) |
|------|----------------|-------------------------------|
| `V20260729_1__create_crm_customer_intelligence.sql` | `20260729.1` | create crm customer intelligence |
| `V20260729_2__seed_default_scoring_models.sql` | **`20260729.2`** | **seed default scoring models** |
| `V20260730_1__enable_crm_row_level_security.sql` | `20260730.1` | enable crm row level security |
| `V20260730_2__disable_crm_row_level_security.sql` | **`20260730.2`** | disable crm row level security |

**The migration that matches `(description='seed default scoring models')` is version `20260729.2`, NOT `20260730.2`.** Version `20260730.2` is the unrelated CRM-018 RLS-disable rollback migration.

## 4. Expected vs actual version mapping

| What the test needs | Required version | Constant value at base SHA | Match? |
|---------------------|------------------|----------------------------|--------|
| `description='seed default scoring models'` | `20260729.2` | `CRM_010_SCORING_MODELS_VERSION = "20260730.2"` | ❌ NO |
| `latestVersion == scoring models` | `20260729.2` | `20260730.2` | ❌ NO (latest scoring seed is 20260729.2; 20260730.2 is RLS-disable) |

Because the constant points at `20260730.2`, `assertMigration` finds zero rows
with `(20260730.2, SQL, 'seed default scoring models')` → `0L` → failure.

## 5. Root cause — proven via git history

The constant `CRM_010_SCORING_MODELS_VERSION` was introduced by the CRM-010
feature commit `c59bcd21` (`feat(crm-010): Customer 360 & Unified Customer
Intelligence (#818)`) with the **correct** value:

```
c59bcd212dc33e07f893b3c4e1101453888e5cdb:
    private static final String CRM_010_SCORING_MODELS_VERSION = "20260729.2";
```

PR #826 ("Workstream 2 — Fix Maven Test Suite failures", merge `a12b73da`)
changed it — in BOTH `CrmPostgresMigrationTest.java` and
`Crm008bFoundationAcceptanceTest.java` — from `20260729.2` to `20260730.2`.

PR #826's own remediation report table claims:

> `CrmPostgresMigrationTest.java` — Hardcoded version `20260729.2` → Updated to `20260730.2`

That change is the defect. The version was not "hardcoded wrongly" — `20260729.2`
is the correct location of the scoring-models seed. #826 misidentified the
target and moved the constant onto an unrelated migration. The same wrong
value was applied to `Crm008bFoundationAcceptanceTest.java`, which even
contains a self-contradicting comment at line 510
(`// Latest version is 20260729.2 (CRM-010 added scoring models seed)`)
next to the now-wrong constant.

## 6. Conclusion — migration vs test expectation?

**The migration is correct. The test expectation is incorrect.**

- Migration `V20260729_2__seed_default_scoring_models.sql` is a valid,
  self-consistent Flyway migration at version `20260729.2` with the
  description the test expects.
- The test constant was corrupted by PR #826 to point at `20260730.2`
  (the RLS-disable migration), so no migration row can satisfy the
  `(version, type, description)` triple the assertion queries for.

## 7. Fix (one-line-per-file, two files)

Revert the constant to its proven-correct value in both files:

- `apps/sanad-platform/src/test/java/com/sanad/platform/crm/web/CrmPostgresMigrationTest.java`
  `CRM_010_SCORING_MODELS_VERSION = "20260730.2"` → `"20260729.2"`
- `apps/sanad-platform/src/test/java/com/sanad/platform/crm/web/Crm008bFoundationAcceptanceTest.java`
  `CRM_010_SCORING_MODELS_VERSION = "20260730.2"` → `"20260729.2"`

A guard comment is added beside each constant explaining why it must not be
`20260730.2`, to prevent regression.

## 8. Why this single root cause breaks three workflows

`CrmPostgresMigrationTest` is invoked by three independent gating workflows:

1. **Maven Test Suite** (`CI` workflow) — runs the class directly.
2. **CRM G1 Schema Isolation** — runs
   `mvn -Dtest=CrmPostgresMigrationTest,CrmG1TenantIsolationPostgresTest test`
   in one invocation. `CrmG1TenantIsolationPostgresTest` itself PASSES
   (1 test, 0 failures); the workflow fails only because the migration test
   in the same Maven run fails. **This is not a tenant-isolation regression.**
3. **Post-Merge Verification** — runs the full backend unit-test suite, which
   includes the migration test; the resulting manifest is `FAIL`, so the
   workflow's final gate refuses to close.

Fixing the constant fixes all three.

## 9. Acceptance criteria for R1

- [ ] `CrmPostgresMigrationTest` — 4/4 tests pass (Maven).
- [ ] `Crm008bFoundationAcceptanceTest` — passes.
- [ ] `Maven Test Suite` workflow GREEN.
- [ ] `CRM G1 Schema Isolation` workflow GREEN.
- [ ] `Post-Merge Verification` backend-unit-tests step no longer fails on
      this class (full workflow GREEN contingent on R2 clearing drift).

## 10. Evidence index

- `gh run view 30578916574 --log-failed` — `expected: 1L but was: 0L` at `assertMigration:530`.
- `gh run view 30578916619 --log-failed` — same class; `CrmG1TenantIsolationPostgresTest` passes.
- `git show c59bcd21:…/CrmPostgresMigrationTest.java` — original constant `20260729.2`.
- `git log -S CRM_010_SCORING_MODELS_VERSION` — only #818 (introduce) and #826 (corrupt) touched it.
- Filesystem: `V20260729_2__seed_default_scoring_models.sql` is the sole "seed default scoring models" migration.
