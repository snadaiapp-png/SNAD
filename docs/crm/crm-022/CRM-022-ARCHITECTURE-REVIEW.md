# CRM-022 — Architecture Review

| Field | Value |
|-------|-------|
| Work item | EXEC-PROMPT-CRM-022 — Add a CRM-specific job to `ci.yml` |
| Spec source | `docs/crm/CRM-ENTERPRISE-EXECUTION-ROADMAP.md` (EXEC-PROMPT-CRM-022) |
| Branch | `feature/crm-022-ci-job` |
| Date | 2026-07-30 |

## 1. Scope of review

Read-only review of the existing CI/CD surface to determine how CRM-022 must
be implemented to remain architecturally consistent. No file was modified
during this review.

## 2. Existing CI architecture

| Workflow | Purpose | Conventions observed |
|----------|---------|----------------------|
| `.github/workflows/ci.yml` | Backend Maven test suite (Java 21 + Testcontainers) | `permissions: contents: read`; `ubuntu-latest`; `actions/checkout@v4`, `actions/setup-java@v4` (temurin 21, `cache: maven`); Surefire `useFile`; artifact upload of `surefire-reports`; `$GITHUB_STEP_SUMMARY` parsing. |
| `.github/workflows/web-ci.yml` | Next.js frontend (build, lint, vitest, design-system governance) | Node 24; `concurrency` group; `timeout-minutes: 15`. |
| `.github/workflows/crm-web-lint-diagnostics.yml` | CRM web ESLint diagnostics | PR-only trigger; Node 24. |
| `.github/actions/` | **None** — no reusable composite actions exist. |

## 3. Key findings

1. **No composite actions exist.** CRM-022 cannot "reuse composite actions" because there are none; instead it reuses the same versioned marketplace actions as the `test` job.
2. **TypeScript / ESLint / CRM web tests are already covered** by `web-ci.yml` and `crm-web-lint-diagnostics.yml`. Duplicating them in a backend `crm` job would violate the directive's "no duplicated workflow logic" rule. Therefore the `crm` job is scoped to the **backend CRM integration tests**, which is precisely what the authoritative spec (`runs the four CRM integration test classes`) mandates.
3. **CRM integration tests are Java/Surefire**, live under `apps/sanad-platform/src/test/java/com/sanad/platform/crm/**`, and at least one uses Testcontainers → the `crm` job must keep the Docker-availability check.
4. **No Jacoco** is configured in `apps/sanad-platform/pom.xml` → coverage generation is intentionally skipped per the directive's "if tooling exists" clause.
5. **JUnit XML** is produced natively by Surefire (`-Dsurefire.useFile=true` → `TEST-*.xml`), satisfying the "JUnit output if supported" clause.

## 4. Test-selection strategy

The spec mentions "the four CRM integration test classes"; the repository now contains **16** such classes. Rather than hard-code four (which would silently drop twelve), the job selects by **package**: `-Dtest='com.sanad.platform.crm.**.*IntegrationTest'`. This matches all 16 (verified) and is robust to future additions. `-DfailIfNoTests=true` guarantees the job fails loudly if the scope ever drifts to zero.

## 5. Decision

Add a single additive job `crm` (`CRM Integration Tests`) to `ci.yml`, structurally mirroring the existing `test` job, scoped to the CRM integration-test package, with its own artifact names to avoid collisions.

## 6. Open items / risks

- The `crm` job is **declared**; making it a *required* branch-protection check is a **post-merge governance action** on GitHub (out of scope for this code change) — tracked in CRM-G5-PROGRESS-UPDATE.
- Local live test run could not be performed (Docker daemon not running on the workstation); CI's `ubuntu-latest` provides Docker.
