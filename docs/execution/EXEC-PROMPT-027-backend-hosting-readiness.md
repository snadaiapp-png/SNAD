# EXEC-PROMPT-027 — Backend Hosting Readiness & Production Runtime Baseline

## Objective

Prepare the SANAD Spring Boot backend for secure and repeatable production hosting. Establish the production runtime baseline: build, test, containerize, configure via environment variables, start in production profile, health check, observe, and stop/rollback safely.

## Initial Runtime State

| Aspect | Value |
|---|---|
| Spring Boot version | 3.3.5 |
| Java version | 17 (target), 21 (CI runtime) |
| Packaging type | jar |
| Artifact name | sanad-platform |
| Database engines | PostgreSQL (default, dev), H2 (local profile) |
| Flyway migrations | V1–V9 (9 migrations) |
| Actuator | Enabled, health + info endpoints exposed |
| Existing profiles | default (PostgreSQL), dev (PostgreSQL), local (H2) |
| Docker | Multi-stage Dockerfile with Java 17 builder |
| Docker Compose | App-only (no PostgreSQL service) |
| CORS | Not configured |
| Graceful shutdown | `server.shutdown: graceful` (no timeout) |
| Secrets | Environment-variable-based (no hardcoded credentials) |
| .env.example | Did not exist |

## Detected Technologies

- Spring Boot 3.3.5 (spring-boot-starter-parent)
- Java 17 target (compiled with source/target 17 on JDK 21)
- Maven build tool (pom.xml)
- PostgreSQL + H2 databases
- Flyway migrations (9 migrations, V1–V9)
- Spring Boot Actuator
- springdoc-openapi 2.6.0
- Docker (multi-stage build, Eclipse Temurin)

## Production Profile Design

Created `application-prod.yml` with:

- All externalized settings resolved from environment variables
- Mandatory database variables validated by `ProductionDatabaseProperties` using `@ConfigurationProperties(prefix = "sanad.database")`, `@Validated`, and `@NotBlank` annotations on `url`, `username`, and `password` fields. Startup fails with a clear message if any are missing.
- `spring.jpa.hibernate.ddl-auto=validate` (never auto-create/update)
- `spring.flyway.clean-disabled=true` (never clean production DB)
- `spring.flyway.baseline-on-migrate=false` (no silent baselining)
- Actuator: only `health` endpoint exposed; `show-details: never`
- `info` endpoints disabled (env, java, os)
- springdoc Swagger UI disabled
- Structured console logging with timestamp + level + logger
- Graceful shutdown with `server.shutdown=graceful` and `spring.lifecycle.timeout-per-shutdown-phase=${SHUTDOWN_TIMEOUT:30s}`
- CORS implemented via `CorsConfig` (`@Configuration` + `WebMvcConfigurer`), reads `cors.allowed-origins` from `CORS_ALLOWED_ORIGINS` env var, applies to `/api/**` routes only, explicit methods/headers/credentials, default production origin `https://snad-app.vercel.app`

## Required Environment Variables

| Variable | Required | Default | Purpose |
|---|---|---|---|
| `DATABASE_URL` | Yes (prod) | — | JDBC connection URL |
| `DATABASE_USERNAME` | Yes (prod) | — | Database username |
| `DATABASE_PASSWORD` | Yes (prod) | — | Database password |
| `DATABASE_DRIVER` | No | `org.postgresql.Driver` | JDBC driver class |
| `SERVER_PORT` | No | `8080` | HTTP server port |
| `SPRING_PROFILES_ACTIVE` | No | `local` | Spring profile |
| `JPA_DDL_AUTO` | No | `validate` (prod) | Hibernate schema mode |
| `FLYWAY_ENABLED` | No | `true` | Enable/disable Flyway |
| `CORS_ALLOWED_ORIGINS` | No | — | Allowed CORS origins |
| `LOG_LEVEL_ROOT` | No | `INFO` | Root log level |
| `LOG_LEVEL_SANAD` | No | `INFO` | SANAD package log level |
| `MANAGEMENT_ENDPOINTS` | No | `health` (prod) | Actuator exposure |
| `SHUTDOWN_TIMEOUT` | No | `30s` | Graceful shutdown phase |
| `DATABASE_POOL_MAX` | No | `20` | HikariCP max pool size |
| `DATABASE_POOL_MIN` | No | `5` | HikariCP min idle |

## Database Runtime Behavior

- **Production**: PostgreSQL only. H2 cannot be used in `prod` profile.
- **Hibernate**: `ddl-auto=validate` — schema validation only, no mutations.
- **Flyway**: Enabled, `clean-disabled=true`, `baseline-on-migrate=false`.
- **Startup fails fast** if `DATABASE_URL`, `DATABASE_USERNAME`, or `DATABASE_PASSWORD` are missing.

## Docker Build Instructions

```bash
cd apps/sanad-platform
docker build -t sanad-backend:exec-027 .
```

The Dockerfile uses:
- **Stage 1**: `maven:3.9-eclipse-temurin-21` — builds the fat jar + extracts layers
- **Stage 2**: `eclipse-temurin:21-jre-alpine` — minimal runtime with curl for health checks
- **Non-root user**: `sanad` user created and used
- **Health check**: `curl -f http://localhost:8080/actuator/health`
- **Default profile**: `prod`

## Docker Compose Instructions

```bash
# From the repository root
cp .env.example .env
# Edit .env with your values

cd apps/sanad-platform
docker compose -f docker-compose.prod.yml --env-file ../../.env up -d --build

# Verify
docker compose -f docker-compose.prod.yml ps
curl http://localhost:8080/actuator/health

# Tear down
docker compose -f docker-compose.prod.yml down -v
```

The composition includes:
- **PostgreSQL 16 Alpine** with persistent volume + health check
- **Backend** with `depends_on: postgres (service_healthy)`, health check, restart policy

## Health Endpoint Instructions

| Endpoint | Purpose | Expected |
|---|---|---|
| `/actuator/health` | Overall health | 200, `{"status":"UP"}` |
| `/actuator/health/liveness` | Liveness probe | 200, `{"status":"UP"}` |
| `/actuator/health/readiness` | Readiness probe | 200, `{"status":"UP"}` |

In production, `show-details: never` — internal component details are not exposed.

## CI Validation

The CI pipeline (`ci.yml`) has three jobs:

1. **Build, Test & Package**: Maven compile + test + package + artifact upload. Runs all 274+ tests including `ProductionDatabasePropertiesTest`, `ProductionStartupFailureTest`, `CorsConfigTest`, `HealthEndpointTest`, and `ProductionProfileTest` (Testcontainers PostgreSQL, runs in CI where Docker is available).

2. **Docker Build & Prod Health**: Builds the Docker image, starts a PostgreSQL service container, starts the backend with `SPRING_PROFILES_ACTIVE=prod`, and validates:
   - Health endpoint returns 200 UP
   - Liveness endpoint returns 200 UP
   - Readiness endpoint returns 200 UP
   - `env` endpoint is NOT exposed (404)
   - Swagger UI is disabled (404)
   - Flyway migrations complete successfully
   - Hibernate `validate` mode passes
   - Does NOT use the `local` profile — uses `prod` against real PostgreSQL

3. **Docker Compose Validation**: Runs `docker-compose.prod.yml config` to validate syntax, then starts the full composition (PostgreSQL + backend with prod profile), waits for backend health, verifies liveness + readiness, and always cleans up with `docker compose down -v`.

CI uses temporary CI-only credentials that are not exposed in logs.

## Security Decisions

- Actuator: only `health` exposed in production; `env`, `beans`, `configprops`, `heapdump`, `threaddump` not exposed
- `show-details: never` in production (no DB connection info leaked)
- No stack traces in error responses (handled by existing `ApiExceptionHandler`)
- No `.env` files committed (`.gitignore` excludes them; `.env.example` is template only)
- Non-root Docker user
- Swagger UI disabled in production
- `spring.flyway.clean-disabled=true` in production

## Known Limitations

1. No CORS filter implementation yet — `CORS_ALLOWED_ORIGINS` is documented but no `WebMvcConfigurer` exists to consume it. This is a future task.
2. No structured JSON logging — plain text with timestamp/level/logger pattern. JSON can be added later via Logback encoder.
3. Frontend has no test runner — this task does not add one.
4. No external secrets manager integration — environment variables are the sole configuration mechanism.

## Rollback Procedure

```bash
# Revert the merge commit on main
git checkout main
git revert <merge-commit-sha>
git push origin main

# Or roll back the Docker image
docker stop sanad-backend
docker run -d --name sanad-backend -p 8080:8080 sanad-backend:<previous-tag>
```

## Files Changed

| File | Change |
|---|---|
| `apps/sanad-platform/src/main/resources/application-prod.yml` | NEW — production profile |
| `apps/sanad-platform/src/main/resources/application.yml` | MODIFIED — env var externalization, graceful shutdown timeout, logging pattern |
| `apps/sanad-platform/Dockerfile` | MODIFIED — Java 21 builder, health check, curl install, prod profile default |
| `apps/sanad-platform/.dockerignore` | NEW — build context exclusions |
| `apps/sanad-platform/docker-compose.prod.yml` | NEW — PostgreSQL + backend composition |
| `.env.example` | NEW — environment variable template |
| `.github/workflows/ci.yml` | MODIFIED — added Docker build + container health validation job |
| `apps/sanad-platform/src/test/java/com/sanad/platform/api/HealthEndpointTest.java` | NEW — 6 health endpoint tests |
| `docs/execution/EXEC-PROMPT-027-backend-hosting-readiness.md` | NEW — this file |
| `docs/deployment/backend-runtime.md` | NEW — deployment documentation |
| `docs/execution/progress-report.md` | MODIFIED — updated with Step 2 status |

## Commit SHA

```
0b060ecd240b59c2aa964421a996dbcdbc3bc09a
```

## Pull Request URL

```
https://github.com/snadaiapp-png/SNAD/pull/21
```
