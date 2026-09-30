# G3 execution ledger

Plan: `docs/superpowers/plans/2026-09-30-hrm-g3-performance-reviews-goals-implementation.md`

## Baseline and planning

- G2 baseline: `d2b93450b6774f14814a825e83b7e2dc76a6cc81`
- Design commit: `0f1e6c334f9b9d9103b385a4ebf8670a4db154e8`
- Plan commit: `389a77a2be3d83922bce5a4703bf4ba2478bd04f`
- Plan amendment: `0609fcd091524e9f089f9cd02a3ae866164980ab`
- Task 1 RED test commit: `af42051fa58adf2d1bf6fa50f78d458984df7d2a`
- Task 1 RED evidence placeholder commit: `e27327ff22802bb3105288a408fba15286ae02e9`
- Execution model: official pull-request CI with host-native PostgreSQL Direct. Docker/Testcontainers were not used.

## Task 1 RED gate

- Exact RED execution head: `6415299f4821e5325173825358b5a81249bd5b6b`
- CI run: `36648847620`
- Maven job: `109678051443`
- Result: `HrG3PostgresIntegrationTest` = `2 tests / 2 failures / 0 errors / 0 skipped`
- PostgreSQL Acceptance: SUCCESS
- CRM Integration: SUCCESS
- Ruling: valid RED. Failure layer was missing G3 persistence/schema, not PostgreSQL infrastructure.

## First GREEN implementation

- Minimal persistence commit: `579d0cfcc1b6234a694957afd6d2b6c1d39bd7dd`
- Added `V20260930_1__hr_g3_performance_goals_foundation.sql` only for performance-goal persistence.
- No reviews schema, service, controller, API, UI, broad RBAC change, PostgreSQL config change, or Docker/Testcontainers path was added.

## Root-cause analysis and repair

The first GREEN run exposed four real engineering defects that had to be corrected rather than hidden:

1. The G3 tenant-isolation test depended on external HR seed data and could skip instead of executing the security contract.
2. The new Flyway version `20260930.1` was absent from strict migration-ledger guards.
3. `hr_performance_goals` was absent from the exhaustive HR RLS tenant-table inventory and fixture dispatch.
4. Separate Person and Employment foreign keys did not prove that the Person belongs to the selected Employment in the same tenant.

Root-cause repair commit: `6eff4d648006e2c2b95a112f1b5cc630bb97cbfd`.

Repair scope:

- hardened goal identity to the composite canonical relation `(tenant_id, employment_id, person_id) -> hr_employees(tenant_id, id, person_id)`;
- made `HrG3PostgresIntegrationTest` self-contained and behavioral;
- registered `20260930.1` in strict Flyway history guards without weakening assertions;
- registered `hr_performance_goals` in HR RLS inventory with a real canonical fixture;
- preserved FORCE RLS / fail-closed tenant policy;
- did not add RBAC shortcuts or database bypasses.

Temporary repair workflow/script used to apply the scoped patch were removed before final technical verification.

## Technical GREEN verification

Exact technical GREEN head: `9a3a356478c4209c275d19214231e25fbd057792`

CI run: `36714878281`

- Maven Test Suite `109885238397`: SUCCESS
- PostgreSQL Acceptance Tests `109885238144`: SUCCESS
- CRM Integration Tests `109885238343`: SUCCESS
- Surefire artifact `11101447216`
- Artifact digest: `sha256:621cc5c8ea1e70f6b5f2bf75b1c5d2e040d6171a0d1e4566a96d272c46edfb41`

Surefire XML evidence:

- `HrG3PostgresIntegrationTest`: `2 / 0 / 0 / 0`
  - `foreignTenantCannotReadPerformanceGoal`: PASS
  - `goalRequiresCanonicalEmployment`: PASS
- `CrmFlywayHistoryAssertionTest`: `5 / 0 / 0 / 0`
- `CrmPostgresMigrationTest`: `4 / 0 / 0 / 0`
- `HrRlsFailClosedIntegrationTest`: `247 / 0 / 0 / 0`

## Current governance state

- Task 1 technical GREEN: VERIFIED on `9a3a356478c4209c275d19214231e25fbd057792`.
- Evidence reconciliation: IN PROGRESS via evidence-only commits.
- Reviews / Service / API / UI: BLOCKED until CI is terminal and green on the new exact head produced by this evidence reconciliation.
- PR #1208: must remain unmerged until explicit authorization and final exact-head gates.
- Docker/Testcontainers: 0 / not used.
