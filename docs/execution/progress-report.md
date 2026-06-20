# Progress Report

## Stage 2 — Engineering Foundation and Delivery Governance

### Step 1 — Main Branch CI Stabilization (EXEC-PROMPT-026)

**Status:** COMPLETE

**Summary:** Stabilized GitHub Actions CI workflows for the `main` branch. Removed obsolete feature-branch-only triggers from `ci.yml` and `web-ci.yml`. Added `workflow_dispatch` and least-privilege `permissions: contents: read` to all three workflows. Added a lint step to the frontend CI. Verified backend (250 tests pass) and frontend (lint + build pass) locally before pushing.

**Branch:** `fix/EXEC-PROMPT-026-main-ci-stabilization`

**Commit:** `6e5a89ec00931a82f72b522488da45918eb1a518`

**PR:** https://github.com/snadaiapp-png/SNAD/pull/19

**Backend path detected:** `apps/sanad-platform/` (Maven, pom.xml)

**Frontend path detected:** `apps/web/` (npm, package-lock.json, Next.js 16)

**Test totals:** 250 backend tests (0 failures), frontend lint + build pass

---

### Previous Stages

**Stage 1 — Backend Foundation (EXEC-PROMPT-001 through EXEC-PROMPT-017)**

Built the SANAD platform backend from skeleton through full REST API:
- Tenant, Organization, Membership, and User domains
- Spring Boot 3.3.5, Java 17, PostgreSQL/H2, Flyway V1–V9
- 250+ automated tests
- REST APIs and GitHub Actions CI

---

## Stage 3 — Backend Production Runtime

### Step 1 — Backend Hosting Readiness (EXEC-PROMPT-027)

**Status:** COMPLETE

**Summary:** Established the production runtime baseline, production profile validation, CORS configuration, Java 21 Docker runtime, PostgreSQL production compose validation, health checks, graceful shutdown, and three backend CI jobs.

**Branch:** `feat/EXEC-PROMPT-027-backend-hosting-readiness`

**Commit:** `0b060ecd240b59c2aa964421a996dbcdbc3bc09a`

**PR:** https://github.com/snadaiapp-png/SNAD/pull/21

**EXEC-PROMPT-027 historical test total:** 278 tests (0 failures, 0 errors, 10 skipped locally).

---

## Stage 4 — Backend Production Release

### Step 1 — Backend Production Release Readiness (EXEC-PROMPT-028)

**Status:** IN PROGRESS — PILOT PROVISIONING PENDING

**Summary:** Render remains the backend hosting provider. The owner approved a temporary free-tier pilot architecture using Render Free for the Spring Boot backend and Supabase Free PostgreSQL in Central EU (Frankfurt). The pilot uses the Supabase Session Pooler on port 5432, manual secret entry in Render, Flyway V1–V9, manual first deployment, and `autoDeployTrigger: off`.

| Item | Status |
|---|---|
| Frontend | Vercel `snad-app` |
| Backend provider | Render |
| Backend region | Frankfurt — EU Central |
| Backend plan | `free` — pilot only |
| Database provider | Supabase PostgreSQL |
| Database region | Central EU (Frankfurt) |
| Database plan | `free` — pilot only |
| Database connection | Session Pooler, port 5432, TLS required |
| Blueprint structural validation | Pending PR CI |
| Render Blueprint validation | Pending manual provisioning gate |
| Provisioning | Pending |
| Pilot deployment | Pending |
| Flyway production verification | Pending |
| Production smoke | Pending |
| Rollback validation | Pending |

**Original release PR:** https://github.com/snadaiapp-png/SNAD/pull/23

**Pilot branch:** `chore/supabase-free-pilot`

**Current test totals from EXEC-PROMPT-028:**
- Backend: 303 tests, 0 failures, 0 errors, 10 skipped locally.
- Frontend: 19 tests, 0 failures.

**Pilot configuration changes:**
- Remove Render-managed PostgreSQL from `render.yaml`.
- Keep only `sanad-backend` on Render Free.
- Add `DATABASE_URL`, `DATABASE_USERNAME`, and `DATABASE_PASSWORD` as `sync: false` secrets.
- Reduce Hikari pool to maximum 5 and minimum idle 1.
- Preserve Flyway, production profile, CORS, health checks, and manual deploy.
- Record the Supabase pilot amendment in ADR-028 and deployment documentation.

**Production gate:** The free-tier architecture is not approved for commercial production. Before launch, backend and database must move to approved paid plans and complete backup, restore, monitoring, smoke, rollback, latency, residency, and compliance validation.
