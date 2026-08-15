# SNAD Render Clean-Room R1 — Zero-Loss Forensic Baseline

## Status

`R1_STATUS=IN_PROGRESS`

This document records verified production/deployment facts before any Clean-Room cutover. R1 does not modify the production database, does not create a green Render service, and does not delete or reset the legacy backend.

## Source Lock

```text
REPOSITORY=snadaiapp-png/SNAD
GOVERNING_BRANCH=main
ISOLATION_BRANCH=infra/backend-clean-room-v1
ISOLATION_BASE_SHA=95dc60c35b6b2c44aafc32610d40fde753905472
MAIN_PROTECTED=true
REGISTERED_GITHUB_WORKFLOWS=310
```

The clean-room branch was created from the exact verified `main` SHA above. Feature development may continue to advance `main`; infrastructure changes must be reconciled deliberately and must not force `main` backwards.

## Zero-Loss Guardrails

The following are forbidden during R1/R2:

```text
DROP DATABASE
DROP SCHEMA
TRUNCATE production application data
Flyway clean
production schema reset/recreate
force-push main
delete legacy Render service
delete last known-good image digest
print/commit secret values
unreviewed business/domain refactoring
```

## Verified Current Runtime

### Backend Docker image

Path: `apps/sanad-platform/Dockerfile`

Verified properties:

- Multi-stage Maven/Temurin 21 build and Temurin 21 JRE runtime.
- Runtime user is non-root.
- Fixed JVM limits are tuned specifically for the 512 MB Render free tier:
  - `-Xmx192m`
  - `-XX:MaxMetaspaceSize=160m`
  - `-XX:MaxDirectMemorySize=32m`
  - `-Xss256k`
  - `SerialGC`
- Dockerfile documentation states the application may take 3–18 minutes to start depending on Flyway and database connectivity.
- Container health check targets `/actuator/health`.

### Production Spring configuration

Path: `apps/sanad-platform/src/main/resources/application-prod.yml`

Verified properties:

```text
FLYWAY_ENABLED default=true
FLYWAY_BASELINE_ON_MIGRATE default=false
FLYWAY_VALIDATE_ON_MIGRATE default=true
FLYWAY clean disabled=true
JPA ddl-auto=validate
DATABASE_POOL_MAX default=5
DATABASE_POOL_MIN default=1
server.port=${SERVER_PORT:8080}
health probes enabled=true
graceful shutdown enabled=true
```

The web process therefore still owns Flyway startup by default, and Render `$PORT` is not the first-priority port source.

### Render Blueprint

Path: `render.yaml`

Verified properties:

```text
service=sanad-backend
runtime=image
image=ghcr.io/snadaiapp-png/snad-backend:latest
region=frankfurt
plan=free
healthCheckPath=/actuator/health
FLYWAY_ENABLED=true
DATABASE_POOL_MAX=3
DATABASE_POOL_MIN=1
```

This does not yet meet the clean-room target because production consumes a mutable `latest` tag and the web runtime owns Flyway.

## Verified Production Control-Plane Conflict

The repository has multiple independent mechanisms capable of affecting production.

### Confirmed Render/deploy writers

At least the following are verified from source and must be classified/replaced before green cutover:

- `.github/workflows/publish-render-image.yml`
  - builds GHCR image
  - reads Render credentials
  - mutates Render Flyway environment variables
  - posts Render deploy
  - polls production health
- `.github/workflows/production-release.yml`
  - posts Render deploy for exact current main commit
  - performs verification
  - can request rollback deploy
- `.github/workflows/backend-deploy.yml`
  - triggers a Render deploy hook
- `.github/workflows/render-go-live.yml`
  - suspends/resumes service
  - mutates Flyway settings
  - terminates selected PostgreSQL sessions
  - triggers Render deploy
- additional repair/emergency workflows are present and remain subject to full inventory.

### Confirmed database/Flyway writers

- `.github/workflows/database-migrate-production.yml`
  - runs Flyway against production
  - uses `baselineOnMigrate=true`
  - uses `validateOnMigrate=false`
- multiple active emergency/repair workflows reference Flyway/database mutation and require classification before any archive decision.

### CI conflict requiring separate remediation

`.github/workflows/ci.yml` still contains Docker/Testcontainers execution assumptions. Project governance requires the PostgreSQL Direct path for database/testing, so CI migration is a required clean-room workstream. Required branch-protection check names must not be broken during that migration.

## Production Mutation Observed During R1 Review

While R1 was being established, a normal `main` push at source SHA:

```text
95dc60c35b6b2c44aafc32610d40fde753905472
```

automatically launched GitHub Actions run:

```text
WORKFLOW=Publish Render Backend Image
RUN_ID=31914972536
```

Verified outcome:

```text
GHCR_IMAGE_BUILD=PASS
IMAGE_DIGEST=sha256:fa3fd0e622a011e7ee44b2a88f9f6bc6bfc1880f3ad8c96aed995622efeb1868
RENDER_AUTODEPLOY_ENABLED=true
RENDER_FLYWAY_ENV_RECONCILIATION=PASS
RENDER_DEPLOY=FAIL
RENDER_TERMINAL_STATUS=update_failed
POST_DEPLOY_HEALTH_GATE=SKIPPED
```

The successful environment-reconciliation step explicitly set:

```text
FLYWAY_ENABLED=true
FLYWAY_LOCATIONS=classpath:db/migration,classpath:db/vendor/{vendor}
```

before the deploy failed.

This is evidence that a normal backend push can mutate production configuration and attempt a production deployment. Therefore the first implementation change in R1 is to make the push-triggered image workflow build-only.

## Migration-State Verification Gap

`scripts/production/verify-flyway.sh` currently verifies a small hard-coded subset of historical migration versions. The repository now contains substantially newer migrations, including current 2026-08 series. This script is not sufficient as the future production migration certification gate and must be replaced/generalized before green deployment.

## Clean-Room Target

```text
main / certified SHA
  -> CI gates
  -> immutable GHCR digest
  -> PostgreSQL Direct migration gate
       validate -> migrate -> validate -> schema/RLS evidence
  -> green Render deploy of exact digest
       FLYWAY_ENABLED=false
  -> health/readiness/security/business parity
  -> Vercel cutover
  -> rollback window
  -> legacy retirement
```

## R1 Immediate Execution Order

1. Remove Render write authority from the push-triggered image workflow.
2. Produce machine-readable inventory of all executable workflows.
3. Identify every Render writer, Render env writer, DB writer, Flyway runner, image builder, and source writer.
4. Preserve workflows that back required branch-protection checks until replacements are proven.
5. Open a focused draft PR for the production-auto-deploy freeze and R1 evidence.
6. Do not provision green service until the control plane and direct database gate are clean.

## Current Gate State

```text
SOURCE_LOCK=PASS
ISOLATION_BRANCH=PASS
BUSINESS_LOGIC_MUTATION=0
PRODUCTION_DB_MUTATION_BY_CLEAN_ROOM=0
PRODUCTION_RENDER_MUTATION_BY_CLEAN_ROOM=0
LEGACY_AUTODEPLOY_MUTATION_OBSERVED=true
WORKFLOW_INVENTORY=PARTIAL
AUTO_DEPLOY_FREEZE=NOT_YET_MERGED
GREEN_SERVICE=NOT_CREATED
CUTOVER=BLOCKED_BY_R1
```
