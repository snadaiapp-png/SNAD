# SNAD Backend Clean-Room R3-R6 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the legacy production control-plane with one fail-closed, auditable backend image path, one PostgreSQL Direct migration path, one Render deploy path, read-only production verification, and immutable rollback controls without touching SNAD business-domain behavior.

**Architecture:** Build and publish one immutable GHCR image from an exact Git SHA. Run Flyway separately from the Spring Boot web runtime against PostgreSQL Direct only, then allow a protected Render deployment workflow to deploy an exact image digest. Runtime starts with Flyway disabled, reads Render `$PORT`, exposes liveness/readiness probes, and remains deploy-frozen until credential rotation and Green certification gates pass.

**Tech Stack:** GitHub Actions, GitHub Container Registry, Spring Boot 3.5.6, Java 17 source / Temurin 21 runtime, Maven, Flyway, PostgreSQL, Render image services, Bash/Python governance tests.

## Global Constraints

- PostgreSQL Direct is the only governing Production migration route.
- Shared Supavisor pooler endpoints are forbidden for Production Flyway.
- No Docker/Testcontainers migration path.
- No Flyway `clean`, `repair`, blind baseline, `DROP`, `TRUNCATE`, database recreation, or broad session termination.
- Production Spring Boot runtime must have Flyway disabled.
- Production deployment must use an immutable image digest or immutable SHA identity; never `latest` as deployment authority.
- No Render service creation, paid-plan upgrade, Vercel cutover, or Production migration is executed by this implementation branch.
- Existing exposed DB/application credentials and the exposed Render API key must be rotated before Green certification.
- Do not modify Commerce, Senior Management, CRM, Finance, ERP, Stores, or other business-domain implementation except where a narrowly scoped test must read runtime configuration.
- Preserve all seven required `main` checks: `Build Next.js Web`, `provenance`, `CRM Integration Tests`, `Maven Test Suite`, `CRM Deployment Readiness`, `Post-Merge Verification`, `Verify 8 tables, 26 indexes, and tenant isolation`.
- `main` must be synchronized into `infra/backend-clean-room-v1` before each material R3-R6 implementation batch if the team advances it.

---

### Task 1: Enforce Clean-Room Writer Policy in the Auditor

**Files:**
- Modify: `scripts/ops/audit_github_workflows.py`
- Modify: `scripts/ops/test_audit_github_workflows.py`
- Modify: `.github/workflows/clean-room-control-plane-audit.yml`

**Interfaces:**
- Consumes: all `.github/workflows/*.yml|yaml` and `scripts/production/**/*`.
- Produces: sanitized inventory plus explicit counts for GitHub Render writers, Production DB writers, isolated CI DB writers, and unexpected production writers.

- [ ] **Step 1: Write failing tests** for production-vs-isolated DB classification and canonical writer allowlists.
- [ ] **Step 2: Run the GitHub audit workflow and verify RED.**
- [ ] **Step 3: Implement minimal classification fields**: `is_github_workflow`, `is_production_writer`, `writer_authority`, with canonical allowlists for future `database-migrate.yml`, `render-deploy.yml`, and `render-rollback.yml` only.
- [ ] **Step 4: Make audit fail closed** on secret candidates or unexpected Production writers, while allowing explicitly isolated CI PostgreSQL writers.
- [ ] **Step 5: Re-run audit and require GREEN.**

### Task 2: Harden Spring Boot Runtime and Container Contract

**Files:**
- Create: `scripts/ops/test_runtime_clean_room.py`
- Modify: `apps/sanad-platform/src/main/resources/application-prod.yml`
- Modify: `apps/sanad-platform/Dockerfile`
- Modify: `apps/sanad-platform/.dockerignore`
- Modify: `render.yaml`
- Modify: `.github/workflows/render-blueprint-validation.yml`

**Interfaces:**
- Consumes: Render `$PORT`, DB runtime credentials, container RAM limits.
- Produces: web runtime that starts without migrations and exposes `/actuator/health/liveness` and `/actuator/health/readiness`.

- [ ] **Step 1: Write failing static contract tests** asserting `FLYWAY_ENABLED:false`, `${PORT:${SERVER_PORT:8080}}`, Hikari min idle `0`, max default `3`, `autoDeployTrigger: off`, no `:latest` Blueprint image, no mutable runtime migration flag, and no fixed `-Xmx` heap cap.
- [ ] **Step 2: Run tests and verify RED.**
- [ ] **Step 3: Update `application-prod.yml`**: Flyway default false, pool max 3/min 0, Render `$PORT` support for server and management, graceful shutdown, liveness/readiness health groups.
- [ ] **Step 4: Update Dockerfile** to container-aware RAM percentage sizing, keep non-root runtime, healthcheck `${PORT:-8080}/actuator/health/liveness`, and remove Flyway/startup-duration assumptions.
- [ ] **Step 5: Expand `.dockerignore`** for `.env*`, PEM/key material, IDE/cache/build junk without excluding required Maven source.
- [ ] **Step 6: Update `render.yaml`**: `FLYWAY_ENABLED=false`, pool min `0`, auto-deploy off, readiness health path; keep `plan: free` pending an explicit instance-type decision.
- [ ] **Step 7: Strengthen Blueprint validator** to reject `latest`, runtime Flyway enablement, nonzero pool minimum, and automatic deployment.
- [ ] **Step 8: Run static contract + Blueprint validation and require GREEN.**

### Task 3: Canonical Immutable Backend Image Builder

**Files:**
- Modify: `.github/workflows/publish-render-image.yml`
- Create: `scripts/ops/test_release_workflows.py`

**Interfaces:**
- Consumes: exact `github.sha`.
- Produces: `ghcr.io/snadaiapp-png/snad-backend:<sha>`, digest, and sanitized `backend-image-evidence.json` artifact.

- [ ] **Step 1: Write failing workflow contract tests** requiring no `latest`, no Render credentials/API, exact SHA tag, digest assertion, and artifact evidence.
- [ ] **Step 2: Verify RED.**
- [ ] **Step 3: Remove mutable `latest` publication** and generate JSON evidence `{sourceSha,imageTag,digest,deploymentPerformed:false}`.
- [ ] **Step 4: Upload the JSON as a retained GitHub artifact.**
- [ ] **Step 5: Verify GREEN without deploying Production.**

### Task 4: Canonical PostgreSQL Direct Migration Authority

**Files:**
- Modify: `apps/sanad-platform/pom.xml`
- Create: `.github/workflows/database-migrate.yml`
- Modify: `scripts/ops/test_release_workflows.py`

**Interfaces:**
- Consumes: `PRODUCTION_DATABASE_JDBC_URL`, `PRODUCTION_DATABASE_USERNAME`, `PRODUCTION_DATABASE_PASSWORD`, exact target SHA.
- Produces: Flyway `info -> validate -> migrate -> validate` evidence against PostgreSQL Direct.

- [ ] **Step 1: Write failing workflow/POM contract tests** requiring Flyway Maven plugin and a Direct-only migration workflow.
- [ ] **Step 2: Verify RED.**
- [ ] **Step 3: Add Flyway Maven plugin** using project-managed Flyway/PostgreSQL versions; do not add Docker.
- [ ] **Step 4: Create manual protected migration workflow** that checks out an exact SHA, rejects `.pooler.supabase.com`, rejects port `6543`, requires PostgreSQL host semantics and port `5432`, masks credentials, and forbids baseline/clean overrides.
- [ ] **Step 5: Run `flyway:info`, `flyway:validate`, `flyway:migrate`, then `flyway:validate` with portable + PostgreSQL vendor migration locations.**
- [ ] **Step 6: Upload sanitized migration evidence.**
- [ ] **Step 7: Verify workflow contracts GREEN; do not execute Production migration while credential-rotation/network gates are blocked.**

### Task 5: Canonical Render Deploy and Rollback Authorities

**Files:**
- Create: `.github/workflows/render-deploy.yml`
- Create: `.github/workflows/render-rollback.yml`
- Modify: `scripts/ops/test_release_workflows.py`

**Interfaces:**
- Deploy consumes: rotated `RENDER_API_KEY`, `RENDER_SERVICE_ID`, exact GHCR digest/image URL, exact source SHA.
- Rollback consumes: rotated Render credentials and a prior known-good Render `deployId`.
- Produces: deploy/rollback IDs and health/readiness evidence; no DB/Flyway mutation.

- [ ] **Step 1: Write failing contract tests** requiring manual-only execution, Production environment protection, exact digest validation, no `DATABASE_*`/`FLYWAY_*` mutation, and no deploy hooks.
- [ ] **Step 2: Verify RED.**
- [ ] **Step 3: Create `render-deploy.yml`** using Render API with an exact image digest, polling terminal deploy state and liveness/readiness; never modify Render env vars.
- [ ] **Step 4: Create `render-rollback.yml`** using prior deployment identity and health verification; never modify DB/Flyway configuration.
- [ ] **Step 5: Verify GREEN statically only. Do not call Render until the exposed token has been rotated and Green service authorization exists.**

### Task 6: Read-Only Smoke, Cutover Boundary, and Final Certification

**Files:**
- Modify: `.github/workflows/production-smoke.yml` only if needed to preserve strict read-only behavior.
- Modify: `ops/archive/legacy-render-workflows/wave-d/MANIFEST.md`
- Modify: PR #862 description/status documentation.

**Interfaces:**
- Consumes: certified Green URL only after future cutover authorization.
- Produces: `READY_FOR_INTEGRATION` evidence, never a false LIVE certification.

- [ ] **Step 1: Verify production smoke contains no mutation methods/credentials capable of deployment.**
- [ ] **Step 2: Run Clean-Room auditor and release workflow contract tests.**
- [ ] **Step 3: Evaluate all seven required `main` checks on the current Clean-Room PR head.**
- [ ] **Step 4: Re-fetch `main`; if advanced, merge it into Clean-Room and repeat affected gates.**
- [ ] **Step 5: Record final status fields:** `BASELINE_SHA`, `TEAM_MAIN_SHA`, `CLEAN_ROOM_SHA`, `SECRET_CANDIDATES`, `LEGACY_RENDER_WRITERS`, `PRODUCTION_DB_WRITERS`, `CANONICAL_IMAGE_BUILDER`, `CANONICAL_DB_MIGRATOR`, `CANONICAL_RENDER_DEPLOYER`, `CANONICAL_ROLLBACK`, `RUNTIME_FLYWAY`, `PORT_GATE`, `HEALTH_GATE`, `CREDENTIAL_ROTATION`, `GREEN_RENDER`, `PRODUCTION_CUTOVER`, `READY_FOR_INTEGRATION`, `FINAL_VERDICT`.
- [ ] **Step 6: Keep PR #862 Draft and `FINAL_VERDICT=BLOCKED` if credential rotation, Direct DB network, instance type, Green provisioning, or required checks are unverified.**
