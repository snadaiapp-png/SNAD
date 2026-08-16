# R11 — Testcontainers / Legacy Test-Path Decontamination Design

**Status:** APPROVED BY CONTINUATION DIRECTIVE
**Date:** 2026-08-16
**Branch:** `infra/backend-clean-room-v1`
**Baseline before R11:** `cfc8603221d070f0283ae00adf33d86856a9b430`

## Objective

Remove Testcontainers and Docker-daemon assumptions from SNAD's active backend test path while preserving real PostgreSQL semantics in CI. The governing test database route is an explicit PostgreSQL 16 GitHub Actions service (or an externally supplied PostgreSQL URL for local/manual execution), never an application-created Docker/Testcontainers database.

## Evidence at design time

- `main` is already an ancestor of the Clean-Room branch; branch comparison reports `behind_by=0`.
- `apps/sanad-platform/pom.xml` still declares `testcontainers.version`, `org.testcontainers:postgresql`, and `org.testcontainers:junit-jupiter`.
- Repository code search for `org.testcontainers` finds no active Java test imports outside the Maven dependency declaration.
- `RefreshTokenConcurrencyPostgresTest` already reads `SPRING_DATASOURCE_URL`, username, and password and therefore already supports an externally supplied PostgreSQL instance.
- `.github/workflows/ci.yml` already provisions `postgres:16-alpine`, but still contains obsolete Docker/Testcontainers verification, environment variables, comments, and failure artifacts.
- `.github/workflows/postgres-acceptance.yml` is still designed around discovering `@Testcontainers` classes and must be converted to explicit PostgreSQL-service acceptance.
- `.github/workflows/development-security-acceptance.yml` verifies Docker but does not provision PostgreSQL even though its concurrency test now expects an external PostgreSQL database.

## Architecture

### Active test database authority

```text
GitHub Actions job
  -> PostgreSQL 16 service container declared in workflow
  -> SPRING_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:5432/sanad
  -> Flyway migrations enabled for the isolated CI database
  -> Spring/JPA integration tests
```

The application test code does not create or manage containers. GitHub Actions owns the service lifecycle.

### Local/manual test contract

Tests that require PostgreSQL continue to accept `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, and `SPRING_DATASOURCE_PASSWORD`. No automatic Docker fallback is permitted.

## Changes

1. Add a Clean-Room contract test that fails if active backend Maven configuration contains Testcontainers dependencies/version or if active test workflows contain `@Testcontainers`, `TESTCONTAINERS_`, Testcontainers log handling, or Docker-daemon prerequisite steps for database tests.
2. Remove Testcontainers Maven property and dependencies after the RED contract is proven.
3. Refactor `.github/workflows/ci.yml` without changing required job names `Maven Test Suite` and `CRM Integration Tests`; retain PostgreSQL 16 service and remove Testcontainers-specific Docker checks/env/log artifacts/comments.
4. Convert `.github/workflows/postgres-acceptance.yml` to PostgreSQL-service acceptance. Preserve the workflow name `PostgreSQL Acceptance`; run `RefreshTokenConcurrencyPostgresTest` against the declared PostgreSQL service and verify Surefire reports contain nonzero tests, zero failures, zero errors, and zero skipped tests.
5. Convert `.github/workflows/development-security-acceptance.yml` to provision PostgreSQL 16 and pass explicit datasource/Flyway/JPA settings; remove the Docker verification step.
6. Do not modify application business logic or production migrations.

## Explicit exclusions

- No Production database connection or migration.
- No Render API call, deploy, resume/suspend, environment mutation, or cutover.
- No Vercel mutation.
- No removal of Docker image build/deployment support; Docker remains valid for packaging/runtime images.
- No rewriting of historical documentation solely because it mentions Testcontainers.
- Test fixtures that intentionally exercise policy scanners may retain the word `Testcontainers` if they are not executable backend database paths.

## Safety gates

- `main` must remain an ancestor of the Clean-Room branch before implementation.
- Required check names must not change.
- `org.testcontainers` active Java imports = 0 before dependency removal.
- Active Testcontainers Maven dependencies = 0 after change.
- Active backend test workflow Testcontainers controls = 0 after change.
- PostgreSQL 16 service must exist in every workflow executing PostgreSQL-dependent tests.
- Clean-Room control-plane audit must remain `unexpected_production_writers=0`, `secret_candidate_files=0`, and `render_env_writers=0`.

## Acceptance

R11 is PASS only when:

```text
TESTCONTAINERS_ACTIVE_JAVA_IMPORTS=0
TESTCONTAINERS_MAVEN_DEPENDENCIES=0
TESTCONTAINERS_ACTIVE_WORKFLOW_CONTROLS=0
POSTGRESQL_TEST_ROUTE=POSTGRESQL_16_SERVICE
DOCKER_DATABASE_TEST_ROUTE=0
MAVEN_TEST_SUITE_CHECK_NAME=PRESERVED
CRM_INTEGRATION_TESTS_CHECK_NAME=PRESERVED
CLEAN_ROOM_CONTRACT_TESTS=PASS
UNEXPECTED_PRODUCTION_WRITERS=0
SECRET_CANDIDATE_FILES=0
RENDER_ENV_WRITERS=0
PRODUCTION_DB_MUTATIONS=0
PRODUCTION_RENDER_MUTATIONS=0
```
