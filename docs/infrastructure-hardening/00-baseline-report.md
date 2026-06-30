# SNAD Infrastructure Hardening — Stage 00 Baseline Report

## Execution Context

| Field | Value |
|---|---|
| Repository | snadaiapp-png/SNAD |
| Branch | infra/00-baseline |
| Commit SHA | 4ea1b05b0fb38b1e09f36836bbd4b06351cd9980 |
| Started At | 2026-06-30T15:55:00Z |
| Completed At | 2026-06-30T16:00:00Z |
| Working Tree | CLEAN |

## Baseline Summary

| Check | Result | Evidence |
|---|---|---|
| Frontend npm ci | PASS | Exit 0, clean install from lockfile |
| Frontend lint | PASS | Exit 0, 0 errors, 0 warnings |
| Frontend brand:check | PASS | "SNAD identity validation passed" |
| Frontend tests | PASS | 238 passed (22 test files) |
| Frontend build | PASS | 6 routes: /, /reset-password, /workspace, /api/email-proxy, /api/system/backend-status, /_not-found |
| Python tests | PASS | 165 passed |
| Gitleaks current-tree | PASS | 0 findings |
| Gitleaks history | 8 raw detections (all classified in PR #179) | 1 CONFIRMED_SECRET (HF-01), 1 NEEDS_OWNER_VERIFICATION (HF-06), 6 FALSE_POSITIVE |
| YAML validation | PASS | All workflow + config YAML files valid |
| JSON validation | PASS | All JSON files valid |
| Backend build | NOT_RUN | Maven not available locally; delegated to GitHub Actions CI (passes on main) |
| Backend tests | NOT_RUN | Same as above; 50 test files / 130 source files; CI reports 434+ passed |
| Container build | NOT_RUN | Docker not available locally; Dockerfile validated statically (non-root, healthcheck, multi-stage) |
| Flyway validation | NOT_RUN (static only) | 16 migration files verified for structure and naming; live PostgreSQL validation requires Docker |

## Architecture Baseline

```
Modular Monolith (confirmed)
├── Backend: Spring Boot 3.5.6, Java 17 (built with JDK 21)
├── Frontend: Next.js 16.2.9, React 19.2.4, TypeScript 5.9.3
├── Database: PostgreSQL 16 (prod), H2 (local dev)
├── CI/CD: GitHub Actions (35 workflows)
├── Hosting: Render (backend), Vercel (frontend)
├── Security: Gitleaks v8.24.3, OWASP Dependency-Check
└── No changes to architecture in this stage
```

## Dockerfile Validation (Static)

| Check | Result |
|---|---|
| Multi-stage build | YES (builder + runtime) |
| Build JDK | 21 (eclipse-temurin) |
| Runtime JRE | 21 (eclipse-temurin-alpine) |
| Non-root user | YES (sanad:sanad) |
| Health check | YES (30s interval, 5s timeout, 60s start-period, 3 retries) |
| JVM opts | MaxRAMPercentage=75, G1GC, UTF-8 |
| Exposed port | 8080 |

## Flyway Migration Inventory

16 migrations, all using standard `V<n>__<description>.sql` naming convention.

| Migration | Description | Lines |
|---|---|---|
| V1 | Create tenants table | 44 |
| V2 | Create organizations table | 45 |
| V3 | Create organization memberships | 56 |
| V4 | Create users table | 43 |
| V5 | Link users to organization memberships | 30 |
| V6 | Create roles table | 18 |
| V7 | Create access capabilities | 14 |
| V8 | Create role capabilities | 16 |
| V9 | Create user role assignments | 26 |
| V10 | Add auth credentials | 37 |
| V11 | Add credential bootstrap columns | 20 |
| V12 | Create password reset tokens | 27 |
| V13 | Add session version | 20 |
| V14 | Seed RBAC capabilities and roles | 43 |
| V15 | Seed admin role and capabilities | 44 |
| V20260629_2 | Add user mobile contact | 3 |

## Security Baseline

| Check | Result |
|---|---|
| Current-tree Gitleaks | 0 findings (PASS) |
| History Gitleaks | 8 raw detections — all classified in PR #179 triage report |
| HF-01 (historical admin password) | CONFIRMED_SECRET — Issue #173 OPEN, owner verification required |
| HF-06 (historical email-proxy fallback) | NEEDS_OWNER_VERIFICATION — Issue #173 OPEN |
| Untracked secret-like files | None found |
| OWASP Dependency-Check | CI workflow exists and passes on main |

## Blockers

| ID | Description | Severity | Status |
|---|---|---|---|
| B-01 | Backend build/tests not runnable locally (no Maven, no Docker) | NOT_RUN | Delegated to CI — CI passes on main |
| B-02 | Container build not runnable locally (no Docker) | NOT_RUN | Dockerfile validated statically |

## Final Status

```
FINAL STATUS: PASS (with NOT_RUN items delegated to CI)
NEXT ALLOWED STAGE: 01 — TOOLCHAIN AND ENVIRONMENT PARITY
```
