# Backend Clean-Room Release Gates Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Complete SNAD backend Clean-Room control-plane hardening without mutating Production until every release gate is certified.

**Architecture:** GitHub is the release control plane. Image construction, PostgreSQL Direct migration, Render deployment, smoke verification, and rollback are separate authorities. Production web runtime never owns schema migration; Render deployment consumes an immutable GHCR digest only.

**Tech Stack:** GitHub Actions, Python contract/audit tests, Maven/Flyway, PostgreSQL Direct, Spring Boot 3.5.x, GHCR, Render image-backed service.

## Global Constraints

- Production PostgreSQL migration route is PostgreSQL Direct on port 5432 only.
- Supabase pooler routes and port 6543 are forbidden for the canonical migration authority.
- Flyway web-runtime default is disabled.
- No destructive production SQL, Flyway clean, blind baseline, database recreation, or broad session termination.
- No Production Render environment mutation from image-build or smoke workflows.
- Production deployment uses an exact immutable image digest.
- No Green service cutover until exposed credentials are rotated and old credentials are proven rejected.
- The Render API token previously exposed outside a protected secret store must not be used.
- No Senior Management business/domain files are modified by this plan.

---

### Task 1: Control-Plane Inventory Gate

**Files:**
- Verify: `scripts/ops/audit_github_workflows.py`
- Verify: `.github/workflows/clean-room-control-plane-audit.yml`
- Test: `scripts/ops/test_audit_github_workflows.py`
- Test: `scripts/ops/test_clean_room_audit_workflow.py`

- [x] Run all contract tests without preventing inventory evidence generation.
- [x] Scan every executable workflow and `scripts/production/**` target.
- [x] Produce sanitized inventory artifacts without printing secret values.
- [x] Enforce `unexpected_production_writers == 0` and `secret_candidate_files == 0`.

Verified evidence at SHA `86a51f9944793eac4e6dd33111f7f367da3db2e5`:

```text
scanned_files=116
workflow_count=104
unexpected_production_writers=0
secret_candidate_files=0
inventory_outcome=success
```

### Task 2: Immutable Backend Image Authority

**Files:**
- Verify: `.github/workflows/publish-render-image.yml`

- [x] Build `linux/amd64` image from exact Git SHA.
- [x] Push exact SHA tag to GHCR.
- [x] Capture image digest as evidence.
- [x] Keep Render credentials/API/deploy/Flyway mutation out of this workflow.

### Task 3: PostgreSQL Direct Migration Authority

**Files:**
- Verify: `.github/workflows/database-migrate.yml`
- Verify: `apps/sanad-platform/pom.xml`
- Verify: `apps/sanad-platform/src/main/resources/application-prod.yml`

- [x] Require manual protected invocation and exact target SHA.
- [x] Reject pooler hostnames and port 6543.
- [x] Require Direct Supabase hostname on port 5432 and direct network connectivity.
- [x] Configure Flyway `clean-disabled=true`, `baseline-on-migrate=false`, `validate-on-migrate=true`.
- [x] Provide the Flyway Maven plugin with PostgreSQL support.
- [x] Keep Spring Boot production web runtime `FLYWAY_ENABLED=false` by default.
- [ ] Execute against Production only after credential rotation and explicit release authorization.

### Task 4: Runtime Hardening Verification

**Files:**
- Verify: `apps/sanad-platform/Dockerfile`
- Verify: `apps/sanad-platform/src/main/resources/application-prod.yml`
- Verify: `render.yaml`

- [x] Support Render `$PORT`.
- [x] Set Hikari defaults to max `3`, min idle `0`.
- [x] Enable graceful shutdown.
- [x] Separate liveness from DB-dependent readiness.
- [x] Use container-aware JVM heap sizing instead of a fixed `-Xmx`.
- [x] Keep Render auto-deploy disabled in the Blueprint.
- [x] Keep Blueprint plan unchanged until an instance upgrade is explicitly authorized.

### Task 5: Canonical Render Deploy Authority

**Files:**
- Verify: `.github/workflows/render-deploy.yml`
- Test: `scripts/ops/test_release_workflows.py`

- [x] Manual protected invocation only.
- [x] Require `sha256:<64 hex>` immutable digest.
- [x] Deploy exact `ghcr.io/snadaiapp-png/snad-backend@<digest>` image.
- [x] Do not mutate Render env vars, Flyway configuration, or database settings.
- [x] Poll deployment to terminal state and verify readiness.

### Task 6: Canonical Rollback Authority

**Files:**
- Create: `.github/workflows/render-rollback.yml`
- Test: `scripts/ops/test_release_workflows.py`

- [ ] Create a manual Production-protected workflow that accepts an explicit prior Render `deploy_id`.
- [ ] POST only to the Render rollback endpoint with that deploy id.
- [ ] Do not mutate Render environment variables, Flyway settings, or database configuration.
- [ ] Poll the resulting deployment and verify readiness.

Current blocker:

```text
ROLLBACK_WORKFLOW=BLOCKED_BY_CONNECTOR_SAFETY
CONTRACT_TESTS=48_PASS_1_FAIL
```

The GitHub connector rejected creation of the executable rollback workflow. This blocker must not be bypassed with a fake/no-op workflow or alternate hidden production writer.

### Task 7: Credential Rotation Gate

- [ ] Rotate every production database/application credential identified as exposed in repository history.
- [ ] Rotate the Render API token exposed outside protected secret storage.
- [ ] Store replacements only in protected provider/GitHub secret stores.
- [ ] Prove the superseded credentials are rejected.

### Task 8: Final Verification and Release Decision

- [ ] Re-run all Clean-Room contract tests with zero failures.
- [ ] Re-run sanitized inventory with zero unexpected production writers and zero secret-candidate files.
- [ ] Require all protected `main` checks to complete successfully.
- [ ] Build an immutable image from the final certified SHA.
- [ ] Run PostgreSQL Direct migration gate only after explicit release authorization.
- [ ] Provision/deploy Green Render only after credentials and instance-plan gates pass.
- [ ] Run readiness and non-destructive smoke tests.
- [ ] Perform Vercel/backend cutover only after all prior gates PASS.

Final state remains fail-closed until Task 6, Task 7, and protected CI gates are complete.
