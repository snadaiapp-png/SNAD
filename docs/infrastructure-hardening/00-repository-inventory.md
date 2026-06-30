# SNAD Infrastructure Hardening — Stage 00 Repository Inventory

## Backend

| Property | Value | Source |
|---|---|---|
| Java version | 17 | pom.xml `<java.version>` |
| JDK build | 21 (maven:3.9-eclipse-temurin-21) | Dockerfile |
| JRE runtime | 21 (eclipse-temurin:21-jre-alpine) | Dockerfile |
| Spring Boot | 3.5.6 | pom.xml `<version>` |
| Maven Wrapper | NOT present | No `mvnw` file |
| Profiles | 4 (base, local, dev, prod) | `application*.yml` |
| HikariCP (prod) | max=20, min=5, timeout=30000ms | `application-prod.yml` |
| Hibernate | `open-in-view: false`, `ddl-auto: validate` (prod) | `application-prod.yml` |
| Flyway | enabled=true, `clean-disabled: true` (prod), `baseline-on-migrate: false` (prod) | `application-prod.yml` |
| Migration count | 16 | `db/migration/` |
| H2 usage | `local` profile only (MODE=PostgreSQL) | `application-local.yml` |
| PostgreSQL usage | `dev` and `prod` profiles | `application-dev.yml`, `application-prod.yml` |
| API entry points | ~50 REST endpoints across 10 controllers | Source code analysis |
| Repository interfaces | 10 | Source code analysis |
| @Transactional count | 54 | `grep -rn @Transactional` |
| Locks | 2 PESSIMISTIC_WRITE (RefreshToken, PasswordResetToken) | Source code |
| Background jobs | 0 (@Scheduled) | `grep -rn @Scheduled` |
| Cache points | 4 Caffeine caches (login failure, reset request, registration attempts) | AuthService, MobileSelfRegistrationService |
| Health endpoints | `/actuator/health` (prod: health only) | `application-prod.yml` |
| Source files | 130 | `find src/main` |
| Test files | 50 | `find src/test` |

## Frontend

| Property | Value | Source |
|---|---|---|
| Node.js required | 24.x | `package.json` engines |
| Package manager | npm | `package-lock.json` (root level) |
| Next.js | 16.2.9 | `package.json` dependencies |
| React | 19.2.4 | `package.json` dependencies |
| TypeScript | ^5.9.3 | `package.json` devDependencies |
| Build script | `next build` | `package.json` scripts |
| Test script | `vitest run` | `package.json` scripts |
| Lint script | `eslint` | `package.json` scripts |
| Brand check | `python3 ../../scripts/quality/check_snad_identity.py` | `package.json` scripts |
| API Client | `lib/api/` (17 files: auth, client, config, errors, health, memberships, organizations, registration, users, validation, types, index, user-facing-errors) | Source code |
| Auth flow | `lib/auth/auth-provider.tsx` (10-state machine) + `tenant-context.tsx` | Source code |
| Environment variables | NEXT_PUBLIC_API_BASE_URL, RESEND_API_KEY, EMAIL_PROXY_BEARER_TOKEN, EMAIL_PROXY_FROM, NODE_ENV | `grep process.env` |
| Server routes | 2: `/api/email-proxy`, `/api/system/backend-status` | `find app -name route.ts` |
| Middleware | None | No `middleware.ts` found |
| Error boundaries | None | No `error.tsx` or `ErrorBoundary` found |
| Test files | 22 (238 tests) | `vitest run` output |

## Infrastructure

| Property | Value | Source |
|---|---|---|
| Dockerfiles | 1: `apps/sanad-platform/Dockerfile` (multi-stage, non-root) | File system |
| Docker Compose | 2: `docker-compose.yml` (dev), `docker-compose.prod.yml` (prod) | File system |
| Render config | `render.yaml` (Docker runtime, Frankfurt, free plan, autoDeploy: off) | File system |
| Vercel config | None (auto-detects Next.js) | No `vercel.json` |
| GitHub Actions | 35 workflows | `.github/workflows/` |
| Scripts | 26 files (CI, security, ops, quality, generation) | `scripts/` |
| Health checks | Docker HEALTHCHECK + Render healthCheckPath + Actuator health | Dockerfile, render.yaml |
| Deploy hooks | Render: autoDeploy off (manual/CI-triggered); Vercel: auto-deploy from Git | render.yaml |
| Backup workflows | 2: `backup-restore-validation.yml`, `backup-verify.yml` | `.github/workflows/` |
| Security workflows | 3: `security-baseline.yml`, `security-scan.yml`, `development-security-acceptance.yml` | `.github/workflows/` |
| Load testing | 2 k6 scripts: `health-baseline.js`, `sanad-staging-load.js` | `performance/k6/`, `tests/performance/k6/` |
| Staging config | None (no dedicated staging environment) | — |
