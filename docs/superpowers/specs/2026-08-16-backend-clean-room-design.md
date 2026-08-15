# SNAD Backend Clean-Room / Blue-Green Design

> Status: APPROVED architecture; implementation is isolated on `infra/backend-clean-room-v1`.

## Goal

Re-establish the SNAD production backend on a clean Render service without rewriting business logic, without deleting the legacy service first, and without destructive database operations. The legacy backend remains the rollback target until the green backend passes all release gates.

## Verified Baseline

- Repository: `snadaiapp-png/SNAD`
- Isolation baseline SHA: `95dc60c35b6b2c44aafc32610d40fde753905472`
- GitHub Actions reports 310 registered workflows.
- `main` is protected by required status checks.
- Current production image workflow builds an immutable SHA tag but also has permission/configuration to change Render Flyway settings and deploy the image on every backend push when `RENDER_AUTODEPLOY_ENABLED=true`.
- At baseline capture, the workflow run for SHA `95dc60c35b6b2c44aafc32610d40fde753905472` had already completed the Render Flyway reconciliation step and was executing the Render deploy step.
- `render.yaml` currently points Render at `ghcr.io/snadaiapp-png/snad-backend:latest`, uses the free plan, and enables Flyway in the web runtime.
- `application-prod.yml` defaults `FLYWAY_ENABLED=true`, Hikari max/min to 5/1, and binds the server to `${SERVER_PORT:8080}`.
- The Dockerfile is tuned around a 512 MB instance (`-Xmx192m`, metaspace 160 MB, direct memory 32 MB) and documents a 3–18 minute startup path because migration/database initialization is coupled to application startup.
- `production-release.yml`, `backend-deploy.yml`, `render-go-live.yml`, and `publish-render-image.yml` provide overlapping production control paths.
- `database-migrate-production.yml` enables `baselineOnMigrate=true` and disables validation; it is not suitable as the future canonical production migration gate.
- `ci.yml` still contains Testcontainers/Docker assumptions even though project governance requires PostgreSQL Direct for the database/test path; this is a separate CI remediation track and must not be silently mixed with Render cutover.

## Non-Negotiable Zero-Loss Rules

1. No `DROP DATABASE`, `DROP SCHEMA`, destructive reset, `TRUNCATE` of production data, or Flyway `clean`.
2. Do not recreate production PostgreSQL as part of the Render recovery.
3. Do not delete the legacy Render service before green certification and rollback-window expiry.
4. Do not delete the last known-good GHCR digest before rollback-window expiry.
5. Do not print or commit secret values.
6. Do not force-push `main`.
7. Business/domain code is preserved. The clean-room project replaces deployment machinery, not SNAD itself.
8. Production Flyway migration must be a separate release stage; the web application starts with `FLYWAY_ENABLED=false` after the migration track is certified.
9. PostgreSQL Direct is the governing migration route. A network inability to reach Direct PostgreSQL is a blocker, not permission to silently switch to transaction pooling.
10. Every production change must have a rollback target and machine-verifiable evidence.

## Architecture

```text
Active development (main)
        |
        v
     CI gates
        |
        v
Certified source SHA
        |
        +--> build once --> GHCR immutable SHA/digest
        |
        +--> PostgreSQL Direct migration gate
        |       validate -> migrate -> validate -> schema/RLS evidence
        |
        v
Green Render service
  exact image digest
  FLYWAY_ENABLED=false
        |
        v
liveness + readiness + smoke + contract parity
        |
   +----+----+
   |         |
 PASS       FAIL
   |         |
cutover    legacy remains active rollback target
   |
rollback window
   |
retire legacy
```

## Control-Plane Target

Production authority will be reduced to one writer per concern:

- `ci.yml`: compile/test/security/architecture only; no Render mutation.
- `backend-image.yml`: build and publish immutable image only; no Render mutation.
- `database-migrate.yml`: the only schema migration writer.
- `render-deploy.yml`: the only Render production deployment writer.
- `production-smoke.yml`: read-only verification.
- `render-rollback.yml`: redeploy the last certified immutable digest only.

Unrelated valid workflows (security, backup, web/mobile CI, governance) are not deleted merely to reach a small workflow count. Only duplicate, superseded, diagnostic, emergency, and conflicting deployment/database control paths are archived or retired after dependency analysis.

## Runtime Target

- Server port priority: `${PORT:${SERVER_PORT:8080}}`.
- Graceful shutdown retained.
- Actuator liveness and readiness must be proven available before Render is configured to rely on them.
- Web runtime: `FLYWAY_ENABLED=false`.
- JPA: `ddl-auto=validate`.
- Initial green pool sizing is intentionally conservative and measured before final tuning.
- JVM limits are based on the selected paid/production instance and measured RSS, not permanent 512 MB survival hacks.

## Database / Flyway Target

Release order:

```text
source SHA
 -> CI PASS
 -> immutable image digest
 -> direct PostgreSQL preflight
 -> Flyway validate
 -> Flyway migrate
 -> Flyway validate
 -> schema/RLS verification
 -> deploy same image digest
```

Normal production settings:

- `baselineOnMigrate=false`
- `validateOnMigrate=true`
- `cleanDisabled=true`

A one-time baseline operation is permitted only under a separate documented incident/adoption procedure; it is never a normal release default.

## Blue-Green Policy

- Legacy service is not modified into the green service.
- Green receives a separate service identity and URL.
- Vercel continues to target the legacy backend until green certification.
- Green reads the same production database only after the database gate proves schema compatibility.
- Cutover is a routing/configuration change performed only after parity gates pass.
- Rollback restores the previous backend target/digest; it never reverses database migrations destructively.

## Parity Gates

Before cutover, evidence must cover at least:

- Flyway history integrity and no failed/duplicate versions.
- Required schema objects and RLS state.
- Tenant isolation.
- Authentication/RBAC/capability checks.
- CRM and Finance read paths.
- Senior Management and System Health APIs.
- Workflow/Analytics integration boundaries.
- Mobile/G7 contracts.
- Website public and management contracts.
- API operation-count regression gate.
- Health, liveness, readiness.
- CORS/security boundaries.
- Vercel BFF/backend integration.

## Implementation Missions

- R1: Source lock, forensic inventory, production-writer freeze, zero-loss baseline.
- R2: Workflow decontamination and archive map.
- R3: Canonical CI/CD control plane.
- R4: PostgreSQL Direct migration runner and Flyway separation.
- R5: Runtime/Docker hardening and image certification.
- R6: Green Render service provisioning with immutable digest.
- R7: Full parity, smoke, security, schema/RLS certification.
- R8: Vercel cutover with immediate rollback path.
- R9: Legacy retirement and immutable final baseline.

## Explicit Exclusions During R1/R2

Do not refactor domain packages, controllers, repositories, Senior Management business logic, Website business logic, CRM/Finance logic, or migration SQL merely for cleanup. Infrastructure work must remain low-collision while feature development continues.
