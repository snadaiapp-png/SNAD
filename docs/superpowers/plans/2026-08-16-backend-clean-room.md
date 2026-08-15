# SNAD Backend Clean-Room Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build and certify a clean Blue/Green SNAD backend on Render without destructive database actions or loss of existing system behavior.

**Architecture:** Preserve the current SNAD application and PostgreSQL database, replace the fragmented production deployment control plane with single-purpose release workflows, decouple Flyway from Spring Boot startup, build immutable GHCR artifacts, deploy a separate green Render service, prove parity, then cut Vercel over with a retained legacy rollback path.

**Tech Stack:** GitHub Actions, Spring Boot 3.5.6, Java runtime 21 / Java target 17, Maven, PostgreSQL, Flyway, GHCR, Render, Vercel.

## Global Constraints

- Repository: `snadaiapp-png/SNAD`.
- R1 isolated branch: `infra/backend-clean-room-v1`.
- Isolation baseline: `95dc60c35b6b2c44aafc32610d40fde753905472`.
- PostgreSQL Direct is the governing migration route.
- No production Flyway `clean`, destructive reset, `DROP DATABASE`, `DROP SCHEMA`, or data `TRUNCATE`.
- No secret values in repository, logs, evidence files, PR comments, or artifacts.
- Do not delete the legacy Render service or previous known-good image until green certification and rollback-window expiry.
- Do not refactor business/domain modules as part of infrastructure cleanup.
- Do not declare PASS when a gate is unverified.

---

### Task 1: Establish the forensic and zero-loss baseline

**Files:**
- Create: `ops/render/RENDER-CLEAN-ROOM-R1.md`
- Create: `ops/render/workflow-inventory-schema.md`

**Interfaces:**
- Consumes: GitHub Actions workflow metadata, `render.yaml`, backend Dockerfile, `application-prod.yml`, `pom.xml`, production release scripts.
- Produces: immutable baseline facts and classification rules used by Tasks 2–9.

- [ ] **Step 1: Record source and control-plane evidence**

Record repository, isolation SHA, branch, total registered workflow count, current active production writers discovered, and all verified runtime/database facts. Record names/keys only for secrets; never values.

- [ ] **Step 2: Define workflow classification schema**

Each workflow must be classified with:

```text
path
name
state
trigger
writes_source
writes_render
writes_render_env
writes_database
runs_flyway
builds_image
deploys_image
reads_production
required_status_check
replacement
classification
risk
reason
```

Allowed classifications:

```text
KEEP
REPLACE
ARCHIVE
DANGEROUS
UNRESOLVED
```

- [ ] **Step 3: Record current verified contradictions**

At minimum include:

```text
render.yaml -> image :latest
render.yaml -> FLYWAY_ENABLED=true
application-prod.yml -> FLYWAY_ENABLED defaults true
application-prod.yml -> server.port ignores Render PORT
Dockerfile -> fixed 512 MB survival tuning
publish-render-image.yml -> build + Render env mutation + deployment
production-release.yml -> second deploy writer
backend-deploy.yml -> deploy-hook writer
render-go-live.yml -> suspend/resume + env + DB session mutation + deploy
database-migrate-production.yml -> baselineOnMigrate=true + validateOnMigrate=false
ci.yml -> Testcontainers/Docker assumptions remain
```

- [ ] **Step 4: Commit baseline documents**

Expected commit prefix:

```text
docs(render): capture R1 zero-loss forensic baseline
```

---

### Task 2: Stop automatic backend pushes from mutating Render

**Files:**
- Modify: `.github/workflows/publish-render-image.yml`
- Create: `ops/render/production-freeze.md`

**Interfaces:**
- Consumes: current image builder.
- Produces: a push-triggered builder that has no Render write authority.

- [ ] **Step 1: Convert current publisher to build-only behavior**

The push-triggered workflow must retain:

```text
checkout
Buildx
GHCR login
linux/amd64 build
commit-SHA image tag
build digest evidence
```

It must remove from the push path:

```text
RENDER_API_KEY
RENDER_SERVICE_ID
Production environment binding
Render env-variable PUT operations
FLYWAY_ENABLED mutation
FLYWAY_LOCATIONS mutation
POST /deploys
production health polling
production issue mutation
```

It may retain `latest` temporarily as a convenience tag, but production deployment must not consume it after Task 6.

- [ ] **Step 2: Prove the workflow no longer contains production mutation primitives**

Repository-level assertions must find zero matches in the modified workflow for:

```text
api.render.com/v1/services
RENDER_API_KEY
RENDER_SERVICE_ID
/deploys
env-vars/
```

- [ ] **Step 3: Open a focused draft PR**

PR scope: R1 freeze + baseline only. Do not include application/domain changes.

Expected title:

```text
chore(render): freeze automatic production deploys for clean-room recovery
```

---

### Task 3: Inventory all 310 workflows and decontaminate production writers

**Files:**
- Create: `scripts/ops/audit-github-workflows.py`
- Create: `ops/render/workflow-inventory.csv`
- Create: `ops/render/workflow-inventory.json`
- Create: `ops/archive/legacy-render-workflows/README.md`
- Move: classified legacy workflow files from `.github/workflows/` to `ops/archive/legacy-render-workflows/` only after dependency/status-check analysis.

**Interfaces:**
- Consumes: all workflow YAML files and branch-protection required checks.
- Produces: canonical executable workflow set with historical files preserved outside `.github/workflows`.

- [ ] **Step 1: Write inventory parser**

Parser must read every `.yml`/`.yaml` under `.github/workflows`, extract triggers, secrets/env references, `curl` calls, GitHub/Render endpoints, PostgreSQL commands, Docker/Flyway use, and obvious source-write operations.

- [ ] **Step 2: Test parser against known dangerous workflows**

Assertions must classify at least these as write-capable or dangerous/replaced until superseded:

```text
publish-render-image.yml
production-release.yml
backend-deploy.yml
render-go-live.yml
force-deploy-suspend.yml
switch-db-pool-mode.yml
emergency-deploy.yml
enable-flyway.yml
enable-flyway-fix.yml
database-migrate-production.yml
create-postgres-database.yml
create-external-postgres.yml
```

- [ ] **Step 3: Protect required status checks**

No workflow referenced by `main` branch protection may be archived until its replacement reports the exact required check context or branch protection is deliberately migrated in a separate reviewed action.

- [ ] **Step 4: Archive only proven legacy workflows**

Moving a YAML outside `.github/workflows` disables it as a GitHub Action while preserving Git history and source evidence.

- [ ] **Step 5: Verify no production writer was missed**

Inventory result must show explicit counts for:

```text
RENDER_DEPLOY_WRITERS
RENDER_ENV_WRITERS
DATABASE_MUTATION_WRITERS
FLYWAY_PRODUCTION_RUNNERS
IMAGE_BUILDERS
SOURCE_WRITE_WORKFLOWS
```

---

### Task 4: Build the canonical CI/CD control plane

**Files:**
- Create: `.github/workflows/backend-image.yml`
- Create: `.github/workflows/database-migrate.yml`
- Create: `.github/workflows/render-deploy.yml`
- Create: `.github/workflows/production-smoke.yml`
- Create: `.github/workflows/render-rollback.yml`
- Modify: `.github/workflows/ci.yml`

**Interfaces:**
- Produces: one writer per production concern.

- [ ] **Step 1: Implement build-only image workflow**

Must publish `${GITHUB_SHA}` and expose the returned `sha256` digest in evidence.

- [ ] **Step 2: Implement migration-only workflow**

Must be manual/protected, use PostgreSQL Direct only, run `validate -> migrate -> validate`, reject missing direct connectivity, and keep `cleanDisabled=true`, `baselineOnMigrate=false` for normal releases.

- [ ] **Step 3: Implement deploy-only workflow**

Must require a certified immutable image digest and must not execute Flyway.

- [ ] **Step 4: Implement read-only production smoke**

Must not have Render or database write credentials.

- [ ] **Step 5: Implement immutable rollback**

Must redeploy a previously certified digest; no database rollback or destructive DDL.

- [ ] **Step 6: Reconcile CI governance**

Replace remaining Testcontainers/Docker test assumptions with the approved PostgreSQL test route without changing production database state. Preserve required branch-protection check names until a controlled branch-protection migration is approved.

---

### Task 5: Separate Flyway from Spring Boot web startup

**Files:**
- Modify: `apps/sanad-platform/src/main/resources/application-prod.yml`
- Modify: `render.yaml`
- Create/Modify: migration-only runner files selected by implementation after validating existing project conventions.
- Test: existing Flyway/migration integration tests plus a new production-config assertion test.

**Interfaces:**
- Produces: web runtime that validates schema but does not migrate it.

- [ ] **Step 1: Write failing production-config test**

Test must assert production web default is `FLYWAY_ENABLED=false` and `JPA_DDL_AUTO=validate`.

- [ ] **Step 2: Change production default**

Set:

```yaml
spring:
  flyway:
    enabled: ${FLYWAY_ENABLED:false}
```

- [ ] **Step 3: Remove Flyway enablement from the Render web service**

`render.yaml` must not set `FLYWAY_ENABLED=true` for the production web runtime.

- [ ] **Step 4: Prove migration runner loads both migration locations**

It must include:

```text
classpath/db/migration
classpath/db/vendor/postgresql
```

or equivalent filesystem paths from the checked-out artifact, with validation of the exact source SHA.

---

### Task 6: Harden port, health, Docker, and immutable-image semantics

**Files:**
- Modify: `apps/sanad-platform/src/main/resources/application-prod.yml`
- Modify: `apps/sanad-platform/Dockerfile`
- Modify: `render.yaml`
- Create/Modify: `.dockerignore` under backend context if absent/incomplete.
- Test: production configuration/Actuator tests.

**Interfaces:**
- Produces: fast, deterministic web startup suitable for green deployment.

- [ ] **Step 1: Write failing port-resolution test**

Expected target:

```yaml
server:
  port: ${PORT:${SERVER_PORT:8080}}
```

Management server must resolve consistently.

- [ ] **Step 2: Prove liveness/readiness endpoints**

Tests must confirm both endpoints exist and return expected status under production-like configuration.

- [ ] **Step 3: Remove deployment dependence on `latest`**

Production Render deployment consumes exact digest/immutable SHA only.

- [ ] **Step 4: Replace 512 MB hard-coding only after target instance is selected**

No paid instance change is performed implicitly. JVM options are changed only with measured startup/steady-state evidence.

- [ ] **Step 5: Verify container build**

Build linux/amd64, run as non-root, no secrets baked into layers, one runtime JAR.

---

### Task 7: Certify PostgreSQL Direct before green provisioning

**Files:**
- Create: `ops/database/direct-migration-runbook.md`
- Create: `ops/database/production-schema-evidence-format.md`

**Interfaces:**
- Produces: a non-destructive database gate that green deployment depends on.

- [ ] **Step 1: Classify actual production database endpoint without printing credentials**

Evidence includes host class and port only.

- [ ] **Step 2: Verify Direct connectivity from the selected migration runner**

If network route fails, report:

```text
DB_GATE=BLOCKED_BY_DIRECT_NETWORK
```

Do not fall back silently to transaction pooler.

- [ ] **Step 3: Capture Flyway and schema evidence**

Record failed migrations, duplicates, pending versions, expected tables, RLS flags, and migration source SHA.

- [ ] **Step 4: Run migration gate only after CI/image certification**

No green deploy if migration gate is not PASS.

---

### Task 8: Provision and certify Green Render service

**Files:**
- Modify: `render.yaml` or create a separately reviewed green Blueprint definition, ensuring the same service is never controlled by conflicting Blueprints.
- Create: `ops/render/green-service-runbook.md`

**Interfaces:**
- Consumes: certified digest + database gate PASS.
- Produces: separate green URL with legacy untouched.

- [ ] **Step 1: Snapshot legacy service metadata**

Record service ID/name/URL/region/instance/image/config key names; mask values.

- [ ] **Step 2: Obtain explicit authorization for any new paid instance**

Do not create/upgrade paid resources implicitly.

- [ ] **Step 3: Deploy exact digest to green**

Green web runtime must have `FLYWAY_ENABLED=false`.

- [ ] **Step 4: Run health and security gates**

Health, liveness, readiness, Actuator exposure, Swagger policy, unauthenticated rejection, CORS.

- [ ] **Step 5: Run business parity gates**

Cover authentication, tenant isolation, CRM, Finance, Senior Management, System Health, Workflow, Analytics, Mobile/G7, Websites, and API count/contract tests.

---

### Task 9: Cut over Vercel, preserve rollback, retire legacy

**Files:**
- Create: `ops/render/cutover-runbook.md`
- Create: `ops/render/rollback-runbook.md`
- Create: `ops/render/final-certification.md`

**Interfaces:**
- Consumes: green certification PASS.
- Produces: production cutover with reversible routing and final immutable baseline.

- [ ] **Step 1: Capture pre-cutover frontend/backend routing**

- [ ] **Step 2: Point Vercel backend route to green**

Only after green certification.

- [ ] **Step 3: Run post-cutover smoke and authenticated parity tests**

- [ ] **Step 4: Roll back immediately on any critical parity failure**

Rollback changes routing/digest only; no destructive database rollback.

- [ ] **Step 5: Start rollback window**

Legacy service and last-known-good digest remain retained.

- [ ] **Step 6: Retire legacy after stability evidence**

Archive obsolete workflows, remove old deploy hooks/control paths, then delete the legacy service only after explicit final gate.

- [ ] **Step 7: Publish final certification**

Required fields:

```text
BASELINE_SHA
RELEASE_SHA
IMAGE_DIGEST
WORKFLOWS_BEFORE
WORKFLOWS_AFTER
RENDER_DEPLOY_WRITERS
DATABASE_MUTATION_WRITERS
DB_ROUTE
FLYWAY_GATE
RLS_GATE
GREEN_SERVICE
LEGACY_SERVICE
HEALTH_GATE
SECURITY_GATE
PARITY_GATE
CUTOVER_STATUS
ROLLBACK_DIGEST
FINAL_VERDICT
```
