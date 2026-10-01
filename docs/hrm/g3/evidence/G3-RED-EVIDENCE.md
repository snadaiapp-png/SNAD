# HRM G3 RED / GREEN Evidence

**Status:** TASK 1 TECHNICAL GREEN VERIFIED — FINAL CLOSURE PENDING EXACT-HEAD CI AFTER EVIDENCE RECONCILIATION  
**Branch:** `feat/hrm-g3-performance-reviews-goals`  
**G2 baseline:** `d2b93450b6774f14814a825e83b7e2dc76a6cc81`  
**RED test commit:** `af42051fa58adf2d1bf6fa50f78d458984df7d2a`  
**Authoritative RED execution head:** `6415299f4821e5325173825358b5a81249bd5b6b`  
**Technical GREEN head:** `9a3a356478c4209c275d19214231e25fbd057792`  
**Execution model:** PostgreSQL Direct / host-native only; Docker/Testcontainers are out of scope and were not used.  

## Contract under test

1. `foreignTenantCannotReadPerformanceGoal()` — a goal belonging to tenant A must not be visible under tenant B context.
2. `goalRequiresCanonicalEmployment()` — G3 goal persistence must be constrained to the same canonical tenant + Person + Employment identity.

## Authoritative RED evidence

- Workflow run: `36648847620`
- Maven job: `109678051443`
- Exact RED execution head: `6415299f4821e5325173825358b5a81249bd5b6b`
- PostgreSQL Acceptance job: terminal `SUCCESS`
- CRM Integration job: terminal `SUCCESS`
- `HrG3PostgresIntegrationTest`: `tests=2`, `failures=2`, `errors=0`, `skipped=0`

Observed failures:

1. `foreignTenantCannotReadPerformanceGoal()` failed at the explicit table-existence assertion with:
   `RED: G3 must create hr_performance_goals before tenant isolation can be certified`
2. `goalRequiresCanonicalEmployment()` failed at the explicit persistence assertion with:
   `RED: G3 goal persistence must exist before canonical-employment enforcement can be certified`

Forensic ruling: the RED was valid and located in the missing G3 persistence/schema layer. PostgreSQL Direct infrastructure was healthy; Docker/Testcontainers were not involved.

## Root-cause repair

The first GREEN iteration exposed additional governance and integrity defects beyond mere table existence:

- the G3 test depended on an external canonical HR seed, allowing the tenant-isolation scenario to become skipped;
- the new Flyway version `20260930.1` was not registered in strict migration-history guards;
- `hr_performance_goals` was absent from the exhaustive HR tenant-table RLS inventory;
- independent `person_id` / `employment_id` foreign keys did not prove that the selected Person is the owner of the selected Employment in the same tenant.

The repair hardened the database contract to a canonical composite identity:

`FOREIGN KEY (tenant_id, employment_id, person_id) REFERENCES hr_employees (tenant_id, id, person_id)`

and made the PostgreSQL contract fixture self-contained so the two G3 tests execute rather than skip.

Root-cause repair commit: `6eff4d648006e2c2b95a112f1b5cc630bb97cbfd`.

## Technical GREEN evidence

Authoritative technical GREEN verification:

- Exact head: `9a3a356478c4209c275d19214231e25fbd057792`
- CI run: `36714878281`
- Maven Test Suite job: `109885238397` — `SUCCESS`
- PostgreSQL Acceptance job: `109885238144` — `SUCCESS`
- CRM Integration job: `109885238343` — `SUCCESS`
- Surefire artifact: `11101447216` (`surefire-reports`)
- Surefire artifact digest: `sha256:621cc5c8ea1e70f6b5f2bf75b1c5d2e040d6171a0d1e4566a96d272c46edfb41`

Surefire XML results:

- `HrG3PostgresIntegrationTest`: `tests=2`, `failures=0`, `errors=0`, `skipped=0`
  - `foreignTenantCannotReadPerformanceGoal` — PASS
  - `goalRequiresCanonicalEmployment` — PASS
- `CrmFlywayHistoryAssertionTest`: `tests=5`, `failures=0`, `errors=0`, `skipped=0`
- `CrmPostgresMigrationTest`: `tests=4`, `failures=0`, `errors=0`, `skipped=0`
- `HrRlsFailClosedIntegrationTest`: `tests=247`, `failures=0`, `errors=0`, `skipped=0`

## Governance ruling

Task 1 is technically GREEN on `9a3a356478c4209c275d19214231e25fbd057792`. This evidence reconciliation intentionally creates a newer branch head. Final Task 1 closure MUST therefore wait for the repository CI gates on the new exact head produced by the evidence-only commits. Until that exact-head verification is terminal and green, Reviews / Service / API / UI remain blocked and the PR must not be merged.
