# Clean-Room Wave D — Production Writer Retirement

Date: 2026-08-16
Branch: `infra/backend-clean-room-v1`

## Purpose

Retire the remaining legacy production mutation paths proven by the sanitized control-plane audit and manual source review. Raw operational files are not copied into this archive; Git history remains the forensic source. This avoids preserving sensitive defaults or dangerous executable production procedures in the current tree.

## GitHub Actions retired

- `.github/workflows/backend-deploy.yml` — legacy Render deploy-hook writer; conflicts with the future single canonical Render deploy workflow.
- `.github/workflows/check-db-schema.yml` — Production database workflow that executes a test INSERT transaction even though it rolls back.
- `.github/workflows/final-fix-password.yml` — directly rewrites privileged application-user credentials in Production.
- `.github/workflows/final-fix-v3.yml` — directly inserts/updates Production tenant, role, capability, user, and role-assignment state.
- `.github/workflows/flyway-prod-migrate.yml` — legacy Flyway Production migration writer using a non-canonical execution path and validation override.
- `.github/workflows/render-go-live.yml` — suspends/resumes Render, terminates PostgreSQL sessions, mutates Flyway/JPA env, and deploys.
- `.github/workflows/render-suspend-assess.yml` — suspends Render and terminates PostgreSQL JDBC sessions.
- `.github/workflows/snad-release-orchestrator.yml` — automatic `main` release authority combining Production Flyway, backend verification, and Vercel Production deployment.

## Legacy production release scripts retired

- `scripts/production/commercial-go-live-runtime.sh` — direct Render deployment writer with production health/smoke/cutover orchestration.
- `scripts/production/run-production-release.sh` — direct Render release/rollback writer and legacy release authority.

Read-only verification scripts remain in place. CI workflows using isolated PostgreSQL remain in place; they are not Production writers.

## Evidence boundary

The GitHub branch-protection endpoint for `main` was not readable by the integration (HTTP 403). As a fail-closed substitute, the current PR head check-runs were inspected before this wave. None of the retired manual production writer job names appeared among the active PR checks. This does not claim access to the unavailable branch-protection rule itself.

## Governance after Wave D

```text
PLAINTEXT_SECRET_CANDIDATES_TARGET=0
LEGACY_GITHUB_RENDER_WRITERS_TARGET=0
LEGACY_PRODUCTION_DB_WRITERS_TARGET=0
LEGACY_PRODUCTION_RELEASE_SCRIPTS_TARGET=0
CANONICAL_RENDER_DEPLOY_WRITER=NOT_YET_CREATED
CANONICAL_DIRECT_DB_MIGRATION_WRITER=NOT_YET_CREATED
GREEN_RENDER_PROVISIONING=BLOCKED
PRODUCTION_CUTOVER=NOT_EXECUTED
CREDENTIAL_ROTATION=REQUIRED
```

A fresh sanitized audit is mandatory after this wave. No PASS is permitted until the audit proves the intended writer counts and all relevant CI checks are evaluated.
