# HRM G3 Performance Reviews & Goals Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver HRM G3 Performance Reviews & Goals as a canonical, tenant-isolated vertical slice with goals, reviews, web surfaces, authenticated acceptance, and exact-SHA closure evidence.

**Architecture:** Add a new `hr.performance` backend slice using the canonical HR identity graph and existing HR v2 authorization/error conventions. Persist G3 data in PostgreSQL through forward-only Flyway migrations, expose capability-scoped v2 APIs, consume those APIs through a typed web facade, and prove the slice through PostgreSQL Direct integration tests plus authenticated browser acceptance.

**Tech Stack:** Java 17 / Spring Boot 3.5.6 / JdbcTemplate + existing HR repositories / Flyway / PostgreSQL Direct / JUnit 5 / Next.js 16 / React 19 / TypeScript 5.9 / Vitest / Playwright 1.61.

**Spec:** `docs/superpowers/specs/2026-09-30-hrm-g3-performance-reviews-goals-design.md`

## Global Constraints

- Official G2 closure baseline: `d2b93450b6774f14814a825e83b7e2dc76a6cc81`.
- G3 branch: `feat/hrm-g3-performance-reviews-goals`.
- PostgreSQL Direct only; Docker and Testcontainers are prohibited.
- Do not reopen G2 except for a regression reproducibly caused by G3.
- Do not alter global RBAC, existing RLS semantics, Flyway governance, or PostgreSQL server configuration without a failing RED contract proving the owning layer.
- Do not mutate production data during implementation or certification.
- Preserve canonical `User -> Person -> Employment -> PRIMARY Assignment` scope resolution. No legacy `hr_employees.user_id / manager_id` fallback.
- Every persistent G3 row is tenant-bound; cross-tenant reads/writes fail closed.
- G3 does not include payroll, compensation, succession, learning, calibration, promotion, or AI scoring.
- No `G3 CLOSED — FINAL` declaration before post-merge verification succeeds on the exact merge SHA.

## Review Focus

1. Missing or foreign tenant context must never return another tenant's goal/review; PostgreSQL integration tests in Task 1 and API tests in Task 4 pin this.
2. A valid authenticated user without an active canonical Employment must receive a fail-closed authorization error rather than an empty/widened result; Task 3 pins this.
3. Manager TEAM reads must derive from effective-dated PRIMARY assignment reporting, not legacy manager columns; Task 3 pins this.
4. Duplicate/invalid review source, subject, period, or progress values must return deterministic validation errors and never partially write; Tasks 2 and 3 pin this.
5. Browser surfaces must distinguish loading/empty/forbidden/backend-error states and work in Arabic/English plus desktop/mobile; Tasks 5 and 6 pin this.

---

## File Structure

### Backend — create

- `apps/sanad-platform/src/main/java/com/sanad/platform/hr/performance/PerformanceCapabilities.java` — canonical G3 capability codes only.
- `apps/sanad-platform/src/main/java/com/sanad/platform/hr/performance/domain/PerformanceGoal.java` — goal domain record/value contract.
- `apps/sanad-platform/src/main/java/com/sanad/platform/hr/performance/domain/PerformanceReview.java` — review domain record/value contract.
- `apps/sanad-platform/src/main/java/com/sanad/platform/hr/performance/application/HrPerformanceGoalService.java` — goal use cases.
- `apps/sanad-platform/src/main/java/com/sanad/platform/hr/performance/application/HrPerformanceReviewService.java` — review use cases.
- `apps/sanad-platform/src/main/java/com/sanad/platform/hr/performance/infrastructure/PerformanceGoalRepository.java` — explicit-column PostgreSQL access.
- `apps/sanad-platform/src/main/java/com/sanad/platform/hr/performance/infrastructure/PerformanceReviewRepository.java` — explicit-column PostgreSQL access.
- `apps/sanad-platform/src/main/java/com/sanad/platform/hr/api/v2/performance/HrPerformanceGoalV2Controller.java` — v2 goal HTTP surface.
- `apps/sanad-platform/src/main/java/com/sanad/platform/hr/api/v2/performance/HrPerformanceReviewV2Controller.java` — v2 review HTTP surface.
- `apps/sanad-platform/src/main/resources/db/migration/V20260930_1__hr_g3_performance_reviews_goals.sql` — tables, indexes, constraints.
- `apps/sanad-platform/src/main/resources/db/migration/V20260930_2__hr_g3_performance_rls_capabilities.sql` — fail-closed RLS + capability catalog/grants using existing patterns.

### Backend — tests

- `apps/sanad-platform/src/test/java/com/sanad/platform/hr/performance/HrG3PostgresIntegrationTest.java`
- `apps/sanad-platform/src/test/java/com/sanad/platform/hr/performance/HrG3GoalServiceTest.java`
- `apps/sanad-platform/src/test/java/com/sanad/platform/hr/performance/HrG3ReviewServiceTest.java`
- `apps/sanad-platform/src/test/java/com/sanad/platform/hr/performance/HrG3AuthorizationScopeTest.java`
- `apps/sanad-platform/src/test/java/com/sanad/platform/hr/performance/HrG3OpenApiContractTest.java`
- Modify: `apps/sanad-platform/src/test/java/com/sanad/platform/hr/api/HrApiV2AuthorizationTest.java`
- Modify: `apps/sanad-platform/src/test/java/com/sanad/platform/hr/api/v2/HrOpenApiContractTest.java`
- Modify: `docs/hrm/contracts/openapi/hrm-openapi.json`

### Web — create/modify

- `apps/web/lib/api/hr-g3-api.ts`
- `apps/web/lib/api/hr-g3-api.test.ts`
- `apps/web/app/hr/performance/goals/page.tsx`
- `apps/web/app/hr/performance/goals/page.test.tsx`
- `apps/web/app/hr/performance/reviews/page.tsx`
- `apps/web/app/hr/performance/reviews/page.test.tsx`
- Modify: `apps/web/lib/auth/capabilities.ts`
- Modify: `apps/web/app/hr/components/hr-workspace.tsx`
- Modify: `apps/web/app/hr/components/hr-workspace.test.tsx`
- Modify: current HR i18n message catalogs used by the existing G2/G1 pages.

### Acceptance/governance — create/modify

- `apps/sanad-platform/src/test/resources/sql/g3-acceptance-seed.sql`
- `apps/web/e2e/g3-authenticated.spec.ts`
- `apps/web/playwright-g3.config.ts`
- `.github/workflows/g3-authenticated-acceptance.yml`
- `docs/hrm/g3/evidence/G3-RED-EVIDENCE.md`
- `docs/hrm/g3/evidence/G3-FINAL-EVIDENCE-MANIFEST.md`
- `docs/hrm/g3/evidence/HRM-G3-ENGINEERING-CLOSURE.md`

---

### Task 1: RED contract + PostgreSQL schema/RLS foundation

**Files:**
- Create: `apps/sanad-platform/src/test/java/com/sanad/platform/hr/performance/HrG3PostgresIntegrationTest.java`
- Create after RED: `apps/sanad-platform/src/main/resources/db/migration/V20260930_1__hr_g3_performance_reviews_goals.sql`
- Create after RED: `apps/sanad-platform/src/main/resources/db/migration/V20260930_2__hr_g3_performance_rls_capabilities.sql`
- Create: `docs/hrm/g3/evidence/G3-RED-EVIDENCE.md`

**Interfaces:**
- Consumes: canonical `hr_people`, `hr_employees`, `hr_employee_assignments`, tenant context convention already used by G2 PostgreSQL tests.
- Produces: `hr_performance_goals` and `hr_performance_reviews` tables with tenant-bound keys; capability records used by later tasks.

- [ ] **Step 1: Write the first failing PostgreSQL Direct contract** `foreignTenantCannotReadPerformanceGoal()` plus `goalRequiresCanonicalEmployment()` against the not-yet-created G3 tables.
- [ ] **Step 2: Run only `HrG3PostgresIntegrationTest`** against host-native PostgreSQL and capture the expected RED result (missing G3 table/contract), including command, SHA, failure signature, and timestamp in `G3-RED-EVIDENCE.md`.
- [ ] **Step 3: Add `V20260930_1__hr_g3_performance_reviews_goals.sql`** with explicit UUID PKs, `tenant_id`, canonical employment/person references, goal metric/target/progress/status/effective period, review subject/reviewer/source/period/rating/comments/status, timestamps, uniqueness/check constraints, and required indexes.
- [ ] **Step 4: Add `V20260930_2__hr_g3_performance_rls_capabilities.sql`** using the repository's fail-closed tenant-context RLS pattern and idempotent capability seeding; no global RBAC rewrite.
- [ ] **Step 5: Extend the test** with no-tenant-context denial, foreign-tenant insert/update/read denial, invalid progress/rating constraints, and same-tenant success.
- [ ] **Step 6: Run the focused PostgreSQL test** with `SPRING_DATASOURCE_URL/USERNAME/PASSWORD` pointing at host-native PostgreSQL; expected `FAILURES=0 ERRORS=0`.
- [ ] **Step 7: Commit** `test/feat(hrm-g3): establish PostgreSQL performance foundation`.

### Task 2: Goal domain, repository, and service

**Files:**
- Create: `.../hr/performance/domain/PerformanceGoal.java`
- Create: `.../hr/performance/infrastructure/PerformanceGoalRepository.java`
- Create: `.../hr/performance/application/HrPerformanceGoalService.java`
- Create: `.../hr/performance/HrG3GoalServiceTest.java`

**Interfaces:**
- Consumes: Task 1 schema; canonical employment ID resolved by `HrEmploymentScopeResolver` or a narrowly extracted reusable canonical resolver if evidence requires it.
- Produces: `createGoal`, `listGoals`, `getGoal`, `updateGoal`, `updateProgress` service operations with tenant/employment scope explicit in parameters.

- [ ] **Step 1: Write failing service tests** for create/read, progress update, invalid progress, closed-period mutation rejection, and foreign employment rejection.
- [ ] **Step 2: Run `HrG3GoalServiceTest`** and require RED before implementation.
- [ ] **Step 3: Implement `PerformanceGoal`** with immutable IDs/scope and explicit status/progress values; do not expose raw persistence maps.
- [ ] **Step 4: Implement `PerformanceGoalRepository`** with explicit column lists, tenant predicates on every query, deterministic ordering, and optimistic update semantics consistent with existing HR v2 patterns.
- [ ] **Step 5: Implement `HrPerformanceGoalService`** with validation, canonical employment ownership checks, and auditable progress mutation.
- [ ] **Step 6: Run goal unit + PostgreSQL integration tests**; expected green.
- [ ] **Step 7: Commit** `feat(hrm-g3): add governed performance goals`.

### Task 3: Review domain, repository, service, and canonical scope

**Files:**
- Create: `.../hr/performance/domain/PerformanceReview.java`
- Create: `.../hr/performance/infrastructure/PerformanceReviewRepository.java`
- Create: `.../hr/performance/application/HrPerformanceReviewService.java`
- Create: `.../hr/performance/HrG3ReviewServiceTest.java`
- Create: `.../hr/performance/HrG3AuthorizationScopeTest.java`
- Reuse without broadening: `apps/sanad-platform/src/main/java/com/sanad/platform/hr/time/application/HrEmploymentScopeResolver.java`

**Interfaces:**
- Consumes: canonical SELF employment and TEAM assignment reporting scope.
- Produces: self/manager/peer review creation/read/update operations with explicit reviewer source and fail-closed scope.

- [ ] **Step 1: Write failing review tests** for SELF, MANAGER, PEER source validation; reviewer!=subject where source requires; duplicate reviewer/source/period rejection; invalid rating; unauthorized subject.
- [ ] **Step 2: Write failing scope tests** proving user-without-active-employment denies and manager TEAM visibility comes from PRIMARY `reports_to_assignment_id` only.
- [ ] **Step 3: Run both tests and require RED** before implementation.
- [ ] **Step 4: Implement review domain/repository/service** with tenant predicates, canonical subject/reviewer identities, deterministic lifecycle rules, and no legacy manager fallback.
- [ ] **Step 5: Run review + scope + existing `HrEmploymentScopeResolverPostgresTest`/G2 scope regression suites** to prove G3 did not reopen G2 behavior.
- [ ] **Step 6: Commit** `feat(hrm-g3): add scoped performance reviews`.

### Task 4: Capability-scoped HR v2 API and OpenAPI contract

**Files:**
- Create: `.../hr/performance/PerformanceCapabilities.java`
- Create: `.../hr/api/v2/performance/HrPerformanceGoalV2Controller.java`
- Create: `.../hr/api/v2/performance/HrPerformanceReviewV2Controller.java`
- Create: `.../hr/performance/HrG3OpenApiContractTest.java`
- Modify: `apps/sanad-platform/src/test/java/com/sanad/platform/hr/api/HrApiV2AuthorizationTest.java`
- Modify: `apps/sanad-platform/src/test/java/com/sanad/platform/hr/api/v2/HrOpenApiContractTest.java`
- Modify: `docs/hrm/contracts/openapi/hrm-openapi.json`

**Interfaces:**
- Produces routes under `/api/v2/hr/performance/goals` and `/api/v2/hr/performance/reviews`.
- Canonical capability set: `HRM.PERFORMANCE.GOAL.SELF_VIEW`, `HRM.PERFORMANCE.GOAL.SELF_UPDATE`, `HRM.PERFORMANCE.GOAL.TEAM_MANAGE`, `HRM.PERFORMANCE.REVIEW.SELF_VIEW`, `HRM.PERFORMANCE.REVIEW.SELF_SUBMIT`, `HRM.PERFORMANCE.REVIEW.TEAM_MANAGE`, `HRM.PERFORMANCE.ADMIN`.

- [ ] **Step 1: Extend authorization/OpenAPI tests first** to require exact G3 capabilities, route family, operation IDs, documented request/response types, and no implicit broad HR_MANAGER grant.
- [ ] **Step 2: Run the contract tests** and require RED.
- [ ] **Step 3: Implement capability constants/controllers** using existing `@RequireCapability`/HR error mapping conventions and canonical scope services from Tasks 2-3.
- [ ] **Step 4: Regenerate/update the committed HR OpenAPI artifact from the runtime contract**, not by count-only editing.
- [ ] **Step 5: Run `HrApiV2AuthorizationTest`, `HrG3OpenApiContractTest`, `HrOpenApiContractTest` and focused controller tests**; require zero failures/errors.
- [ ] **Step 6: Commit** `feat(hrm-g3): expose capability-scoped performance v2 api`.

### Task 5: Typed web API + goals tracking surface

**Files:**
- Create: `apps/web/lib/api/hr-g3-api.ts`
- Create: `apps/web/lib/api/hr-g3-api.test.ts`
- Modify: `apps/web/lib/auth/capabilities.ts`
- Create: `apps/web/app/hr/performance/goals/page.tsx`
- Create: `apps/web/app/hr/performance/goals/page.test.tsx`
- Modify: `apps/web/app/hr/components/hr-workspace.tsx`
- Modify: `apps/web/app/hr/components/hr-workspace.test.tsx`
- Modify: HR i18n message catalogs.

**Interfaces:**
- `hrG3Api.listGoals()`, `hrG3Api.getGoal(id)`, `hrG3Api.createGoal(input)`, `hrG3Api.updateGoal(id,input)`, `hrG3Api.updateGoalProgress(id,input)`.
- Page route: `/hr/performance/goals`.

- [ ] **Step 1: Write failing API-facade tests** proving authenticated BFF transport/path mapping and error propagation, with no direct page-level `fetch`.
- [ ] **Step 2: Write failing page tests** for capability denied, loading, empty, populated goals, progress update success, validation failure, backend forbidden/error, Arabic/English labels, and mobile-friendly record rendering.
- [ ] **Step 3: Run Vitest for these files** from `apps/web` using `npm test -- --run <paths>`; require RED.
- [ ] **Step 4: Implement the typed facade and capability constants** following `hr-g2-api.ts` transport conventions.
- [ ] **Step 5: Implement the goals page** using existing `HrWorkspace`, feedback, table/mobile-list, badge, header, and action components; no unrelated HR redesign.
- [ ] **Step 6: Run focused Vitest + `npm run typecheck`**; require green.
- [ ] **Step 7: Commit** `feat(hrm-g3): add goals tracking web surface`.

### Task 6: Performance review web surface

**Files:**
- Create: `apps/web/app/hr/performance/reviews/page.tsx`
- Create: `apps/web/app/hr/performance/reviews/page.test.tsx`
- Modify: `apps/web/lib/api/hr-g3-api.ts`
- Modify: `apps/web/lib/api/hr-g3-api.test.ts`
- Modify: HR i18n catalogs and workspace nav tests as required.

**Interfaces:**
- `hrG3Api.listReviews()`, `hrG3Api.getReview(id)`, `hrG3Api.createReview(input)`, `hrG3Api.updateReview(id,input)`.
- Page route: `/hr/performance/reviews`.

- [ ] **Step 1: Write failing page/API tests** for authorized result display, self-review submission, manager/team view when capable, peer source rendering, validation error, forbidden state, loading/empty/backend error, Arabic/English and desktop/mobile behavior.
- [ ] **Step 2: Run focused Vitest** and require RED.
- [ ] **Step 3: Implement typed review methods and page** using established HR product-surface components and deterministic `data-testid` anchors for acceptance.
- [ ] **Step 4: Run focused Vitest + `npm run typecheck` + `npm run build`**; require green.
- [ ] **Step 5: Commit** `feat(hrm-g3): add performance review web surface`.

### Task 7: Authenticated PostgreSQL Direct acceptance

**Files:**
- Create: `apps/sanad-platform/src/test/resources/sql/g3-acceptance-seed.sql`
- Create: `apps/web/e2e/g3-authenticated.spec.ts`
- Create: `apps/web/playwright-g3.config.ts`
- Create: `.github/workflows/g3-authenticated-acceptance.yml`
- Modify generic Playwright configs only to exclude the stateful G3 spec from unrelated matrices.

**Interfaces:**
- Seed creates deterministic tenant + Employee + Manager + HR users and canonical Person -> ACTIVE Employment -> PRIMARY Assignment graph.
- Employee PRIMARY assignment reports to Manager PRIMARY assignment.
- Workflow uses host-native PostgreSQL, never Docker/Testcontainers.

- [ ] **Step 1: Add a source/contract test** requiring the G3 seed to build canonical Person/Employment/Assignment identity before any browser test can rely on it.
- [ ] **Step 2: Write the browser journey** covering employee goal view/update, employee self-review, manager team goals/reviews, and explicit forbidden path for an unauthorized role/capability.
- [ ] **Step 3: Configure a dedicated single-run Playwright config/workflow** with backend/frontend startup, deterministic seed, artifact upload on failure, and exact-head provenance.
- [ ] **Step 4: Run/list the spec locally or in CI against PostgreSQL Direct**; diagnose any failure from trace + backend logs before changing product code.
- [ ] **Step 5: Require zero hidden 403/500 responses** on authorized paths and correct 403 on forbidden paths.
- [ ] **Step 6: Commit** `test(hrm-g3): add authenticated performance acceptance`.

### Task 8: Full verification, evidence, PR, and final gate

**Files:**
- Create/update: `docs/hrm/g3/evidence/G3-FINAL-EVIDENCE-MANIFEST.md`
- Create/update only after evidence exists: `docs/hrm/g3/evidence/HRM-G3-ENGINEERING-CLOSURE.md`
- Reconcile `apps/web/app/hr/hr-execution-data.ts` only when exact-head evidence proves each G3 roadmap item DONE.

**Interfaces:**
- Consumes all prior task evidence.
- Produces exact-head certification chain and, only after merge + PMV, the G3 final closure record.

- [ ] **Step 1: Run focused backend G3 suites** plus existing canonical identity/G2 scope regression suites against PostgreSQL Direct.
- [ ] **Step 2: Run full backend Maven test suite** using the repository CI-equivalent host-native PostgreSQL environment; require `FAILURES=0 ERRORS=0`.
- [ ] **Step 3: Run web `npm test`, `npm run typecheck`, `npm run build`, and generic Playwright listing/regression checks**; require stateful G3 spec excluded from generic matrices.
- [ ] **Step 4: Push exact head and open a protected PR to `main`** with the G2 baseline, RED evidence, migrations, focused test evidence, authenticated acceptance, and no-Docker statement in the PR body.
- [ ] **Step 5: Require all repository required checks terminal SUCCESS on that exact head**, including `Build Next.js Web`, `provenance`, `CRM Integration Tests`, `Maven Test Suite`, `CRM Deployment Readiness`, and `Verify 8 tables, 26 indexes, and tenant isolation` where applicable to the protected branch rule.
- [ ] **Step 6: Require G3 authenticated acceptance and any G3 human/runtime preview gate terminal SUCCESS on exact head.**
- [ ] **Step 7: Obtain required approving review(s); do not bypass branch protection; merge only with explicit user authorization and `expected_head_sha` protection.**
- [ ] **Step 8: Verify `main` post-merge workflows on the exact merge SHA.** No `G3 CLOSED — FINAL` until required post-merge runs are terminal SUCCESS with no relevant failure/cancellation.
- [ ] **Step 9: Only then reconcile G3 roadmap status to DONE and finalize the closure/evidence documents.**

---

## Execution Order

`Task 1 RED/schema -> Task 2 goals -> Task 3 reviews/scope -> Task 4 API/OpenAPI -> Task 5 goals UI -> Task 6 reviews UI -> Task 7 authenticated acceptance -> Task 8 full certification`

Every task follows `RED -> minimal implementation -> GREEN -> commit`. A downstream green signal never overrides an upstream failed gate.