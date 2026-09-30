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

## Task 2 — Performance Reviews (RED → implementation → GREEN)

Plan-vs-code deltas recorded before implementation (order §5):

1. Task 1 delivered a single migration (`V20260930_1__hr_g3_performance_goals_foundation.sql`) covering goals only; the planned `V20260930_2__hr_g3_performance_rls_capabilities.sql` was never created and `hr_performance_reviews` did not exist. Task 2 therefore owns the reviews table + RLS migration.
2. The plan's `V20260930_2` filename is stale: main/branch carry `V20261001_1..7` (UAC/Platform IAM, merged via PR #1198) and, mid-task, the forward-only UAC Wave 1 overlay `V20261001_8..12`. Flyway `out-of-order=false` ⇒ the reviews migration is `V20261002_1`.
3. Capability catalog seeding (`HRM.PERFORMANCE.*` codes) and HTTP/OpenAPI work belong to the G3 API slice (plan Task 4); this slice enforces scope at the service layer through `HrEmploymentScopeResolver` (reused without broadening) per plan Task 3.
4. Audit metadata is persisted via `created_by/updated_by` columns and asserted behaviorally; the G1-style outbox/audit-service integration is not part of this slice (matches Task 1's goals precedent).

### Task 2 baseline

- Branch head at start: `cb4eec3b2f54198ee091027073413bde0a50438b` (== Task 1 closure head, == PR #1208 head)
- Base main at start: `b49fe6384d9364d844251a9f079db776aacad9b5` (PR #1198 merge, lineage `40102d47` + `8b8c44f8` verified ancestors)
- Main movement during task (order §43): `d3d0a3a0` (brace-expansion lockfile remediation) and `79ef0d20`/`935a4ccb` (users-module workflow guard) — merged into the branch as merge commit `bd04a36a`; migration-collision check performed (`V20261002_1` stays forward-ordered above `20261001.12`); guard truth sets reconciled by exact union.

### Task 2 RED gate (valid RED, PostgreSQL Direct)

- RED commit: `638ac4948fcd4c4779547f16fb4415a4c717d2b3` (`test(hrm-g3): add performance reviews PostgreSQL contract`)
- RED CI run: `36735657542`; Maven job `109956538863` (terminal FAILURE as required); PostgreSQL Acceptance job `109956539258` SUCCESS; CRM Integration job `109956539302` SUCCESS
- Surefire artifact `11111293434`, digest `sha256:52ec7afda47c53ddaacf57a27f0b9c311d8e9a3e655067e2abd816f895c82fa1`
- `HrG3PerformanceReviewsPostgresIntegrationTest`: `10 tests / 10 failures / 0 errors / 0 skipped`
- Failure signatures (all from TEST-*.xml): explicit table-existence contract messages — `RED: G3 must create hr_performance_reviews before {review persistence | canonical subject enforcement | tenant isolation | no-context isolation | reviewer isolation | the valid-review contract | review integrity contracts} can be certified`
- `HrG3PostgresIntegrationTest` on the same head: `2 / 0 / 0 / 0` (Task 1 untouched)
- Ruling: valid RED — failure layer is the missing reviews persistence contract; PostgreSQL infrastructure healthy; no compilation/environment/skip contamination.

### Task 2 implementation

- Implementation commit: `ec35cc2bf6a0a427b732eb5a36835905fc7c5416` (`feat(hrm-g3): add scoped performance reviews`)
- Scope: `V20261002_1__hr_g3_performance_reviews_rls.sql` (table, composite tenant-safe subject/reviewer FKs → `hr_employees(tenant_id,id,person_id)`, source/status/period/rating CHECKs incl. SELF↔reviewer rules, uniqueness per subject/reviewer/source/period, indexes, RLS ENABLE + FORCE + fail-closed `tenant_isolation` policy); `PerformanceReview` domain record; `PerformanceReviewRepository` (single-transaction `set_config('app.tenant_id',…,true)` pattern, explicit columns, optimistic `version` transitions); `HrPerformanceReviewService` (SELF/TEAM canonical scope, deterministic lifecycle DRAFT→SUBMITTED→ACKNOWLEDGED / DRAFT→CANCELLED, fail-closed denials); PostgreSQL Direct tests `HrG3ReviewServiceTest` + `HrG3AuthorizationScopeTest`; guard truth sets updated exactly in `CrmFlywayHistoryAssertionTest`, `CrmPostgresMigrationTest`, `Crm008bFoundationAcceptanceTest`, `R0C13G02SchemaPostgresTest`, `HrG1MigrationTest`, and `hr_performance_reviews` registered in `HrRlsFailClosedIntegrationTest` with a real canonical fixture.
- Main merge: `bd04a36af096cf80bd5d82d3d661e8cdcef937d5` (conflicts resolved by exact union in the two Flyway guards; terminal-version references advanced to `20261002.1`).

### Task 2 failure forensics (diagnosed → proven → classified → minimally fixed)

1. Run `36745320704` (Maven `109989919061` FAILURE): test-compile error — missing `import java.sql.ResultSet` in `HrG3AuthorizationScopeTest`. Classification: test-defect. Fix: `8f4d5bf36e35455bf3feee8bc24bd7ba60758355`.
2. Run `36746101689` (Maven `109992575926` FAILURE; PG Acceptance + CRM Integration SUCCESS): 5 errors from 2 root causes — (a) SQLSTATE 42702 `column reference "id" is ambiguous` in `listTeamReviewsForManager` projection (bare columns over multi-join); (b) SQLSTATE 23505 `pk_tenants` duplicates from tenant-seeding helpers invoked twice per test. Classification: fixture/SQL defects, no product-logic defect. Fix: `7826687dbf330aab903fcd65f9a78ab14fb6f2a0` (join-safe `REVIEW_COLUMNS`, once-only `seedTenantOnce`).

### Task 2 GREEN verification (exact head `7826687dbf330aab903fcd65f9a78ab14fb6f2a0`)

- CI run: `36753607568` — Maven Test Suite job `110018149873` SUCCESS; PostgreSQL Acceptance job `110018149784` SUCCESS; CRM Integration job `110018149622` SUCCESS
- Surefire artifact `11118836595`, digest `sha256:6283d94dfabcec2d46d7f20786933717b6083ed3470c4abcbc370eb109cab36d`
- XML-verified suites (tests/failures/errors/skipped):
  - `HrG3PerformanceReviewsPostgresIntegrationTest` 10/0/0/0
  - `HrG3ReviewServiceTest` 9/0/0/0
  - `HrG3AuthorizationScopeTest` 6/0/0/0
  - `HrG3PostgresIntegrationTest` (Task 1 regression) 2/0/0/0
  - `HrRlsFailClosedIntegrationTest` 251/0/0/0 (247 → 251: +4 parameterized executions for `hr_performance_reviews`)
  - `CrmFlywayHistoryAssertionTest` 5/0/0/0; `CrmPostgresMigrationTest` 4/0/0/0
  - `Crm008bFoundationAcceptanceTest` 11/0/0/0; `R0C13G02SchemaPostgresTest` 8/0/0/0; `HrG1MigrationTest` 15/0/0/0
- Aggregate Maven: 4153 tests / 0 failures / 0 errors / 36 skipped (all skips are pre-existing environment-conditional acceptance suites unrelated to G3; zero skips in any G3/RLS/Flyway/security suite)
- Security/IAM workflows on this head: Security Baseline run `36753607362` → `Frontend Production Dependency Audit` job `110018148576` FAILED: `npm audit --omit=dev` = 1 CRITICAL — `next` GHSA-vcvr-r3jv-pc5j (Next.js RCE in next/og ImageResponse), affected `16.2.0 - 16.3.5`, resolved `16.3.3`, `fixAvailable=true`. Classification: external registry-metadata event — the advisory was published mid-task (the forensic audit on base `b49fe638` earlier the same day reported next clean, and Security Baseline was SUCCESS on RED head `638ac494`); Task 2 changed no `apps/web` file; main `d3d0a3a0/935a4ccb` is equally exposed; no `next/og`/`ImageResponse` usage exists in `apps/web` source (G3 forensic: `NEXT_OG_DIRECT_USAGE_FOUND=NO`), so no exploitable product code path is proven. Out of Task 2's authorized file scope (order §34) — owner remediation required (bump `next` to the fixed release via `package-lock` in a dedicated remediation order).

### Current governance state

- Task 1 technical GREEN: VERIFIED on `9a3a356478c4209c275d19214231e25fbd057792` (unchanged).
- Task 2 technical GREEN: VERIFIED on `7826687dbf330aab903fcd65f9a78ab14fb6f2a0` (Maven/PostgreSQL Acceptance/CRM Integration all SUCCESS).
- Evidence reconciliation: IN PROGRESS via evidence-only commits; final exact-head CI re-verification follows on the evidence head (order §36).
- External blocker (owner): `next` GHSA-vcvr-r3jv-pc5j critical advisory — Security Baseline cannot be terminal-green on ANY repo head until remediated; unrelated to G3 Task 2.
- PR #1208: must remain unmerged until explicit authorization and final exact-head gates.
- Docker/Testcontainers: 0 / not used.

## Main resynchronization / exact-head re-certification

Owner directive after the `next` remediation landed on main: merge current main into the G3 branch (no rebase, no force, no history rewrite), then re-certify all gates on the exact merged head.

### Resynchronization facts

- Old G3 head: `c66c025ed8d8cc14c70f1ebdd94ac9e8d5baf078` (unchanged on origin before push; drift check passed)
- Merged main: `a014202550b72fe6e8a4eb817448a55ffe8f2c90` (includes owner-merged `next` 16.3.3 → 16.3.6 security fix and users governance closure)
- Merge commit: `0e2f60486d298c5e90742c42d74b62c5457c97ab` (parents `c66c025e` + `a0142025`; verified `git cat-file -t` = commit)
- Conflicts: 0 (`git merge origin/main` clean)
- Migration collision: NO (main latest `20261001_12`; G3 `V20260930_1` goals + `V20261002_1` reviews; merged inventory 192 SQL files, 0 duplicate versions)
- Next.js security fix preserved: manifest `^16.3.6`, lockfile root spec `^16.3.6`, resolved `next` = `16.3.6`, `@next/env` = `16.3.6` (no 16.3.3 regression)
- Local pre-push verification: `npm ci` exit 0; `npm audit --omit=dev` critical=0 high=0 moderate=0 low=0
- Push: fast-forward `c66c025e..0e2f6048` onto `feat/hrm-g3-performance-reviews-goals`, no force; remote head after push verified = `0e2f60486d298c5e90742c42d74b62c5457c97ab`

### First recertification (exact head `0e2f60486d298c5e90742c42d74b62c5457c97ab`)

- CI run: `36782484944` (workflow `ci.yml`)
  - Maven job: `110115873539` SUCCESS
  - PostgreSQL Acceptance job: `110115873862` SUCCESS
  - CRM Integration job: `110115873906` SUCCESS
  - `R0C-12 Canonical Gate G` skipped (duplicate gate, non-required, unchanged from prior heads)
- Web CI: run `36782484804` — `Build Next.js Web` job `110115873880` SUCCESS
- Security Baseline: run `36782484809` — `Frontend Production Dependency Audit` job `110115873289` SUCCESS (`npm audit --omit=dev --audit-level=high` exit 0 → critical=0, high=0); all six SB jobs SUCCESS
- Cross-module gates on the same head, all SUCCESS: Platform IAM Focused `36782484900`; Users Module Closure `36782484999`; HR G2 Employment Binding Focused `36782484763`; G2 Authenticated Acceptance `36782485032`; CRM Deployment Readiness `36782484779`; Stage 07 Artifact Provenance `36782484940`; Compile Diagnostics `36782484834`; Pre-Merge Operational Smoke `36782484744`
- Full battery: 24/24 workflow runs SUCCESS (single `skipped` = `ERP Human Preview`, human-preview gate, unchanged from prior heads)

### Surefire evidence (first recertification)

- Surefire artifact: `11131086755` (main Maven suite, 175571891 bytes); PostgreSQL Acceptance artifact `11127978704`; CRM artifact `11127638966`
- Digest (main artifact zip): `sha256:88e7b39b20424c92295374f96f2cea7f92f9bbbc80b1c015cb7014ad1703102b`
- Digest (pg artifact zip): `sha256:69b608b3ee553e62e09c00d92db654faee08a50c65c6bbd1d8bceb508ae1929f`
- Digest (crm artifact zip): `sha256:1a24a219b9483724f50fe4a1bead2e2a49024cd9de657cb5ac6ca02565e94bc8`
- XML-verified suites (tests/failures/errors/skipped):
  - `HrG3PostgresIntegrationTest` 2/0/0/0
  - `HrG3PerformanceReviewsPostgresIntegrationTest` 10/0/0/0
  - `HrG3ReviewServiceTest` 9/0/0/0
  - `HrG3AuthorizationScopeTest` 6/0/0/0
  - `HrRlsFailClosedIntegrationTest` 251/0/0/0
  - `CrmFlywayHistoryAssertionTest` 5/0/0/0
  - `CrmPostgresMigrationTest` 4/0/0/0
- 7-suite aggregate: 287 tests / 0 failures / 0 errors / 0 skipped
- Aggregate Maven: 578 suites / 4153 tests / 0 failures / 0 errors / 36 skipped — all 36 skips are the same pre-existing environment-conditional acceptance suites (Commerce concurrency 6, ModuleRegistry UAT 10, PlatformIAM session 3, PlatformIAM user 2, RBAC access 15) that executed with skipped=0 in the dedicated PostgreSQL Acceptance artifact (36/36 pass there); zero skips in any G3/RLS/Flyway/security suite
- Docker/Testcontainers: 0 / not used

Final exact-head re-certification on the ledger evidence commit follows this section.
