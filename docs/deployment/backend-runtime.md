# Backend Runtime Documentation

## Overview

The SANAD Platform backend is a Spring Boot 3.3.5 application built with Maven and Java 17 (target), running on JDK 21. It uses PostgreSQL as the production database and H2 for local development/testing.

## Profiles

| Profile | Database | Use Case |
|---|---|---|
| `local` | H2 in-memory | Developer laptops, automated tests |
| `dev` | PostgreSQL | Shared development environment |
| `prod` | PostgreSQL | Production |

## Environment Variables

See `.env.example` at the repository root for the full list with defaults.

### Mandatory in Production

| Variable | Description |
|---|---|
| `DATABASE_URL` | PostgreSQL JDBC URL |
| `DATABASE_USERNAME` | Database user |
| `DATABASE_PASSWORD` | Database password |

### Optional with Defaults

| Variable | Default | Description |
|---|---|---|
| `SERVER_PORT` | 8080 | HTTP port |
| `SPRING_PROFILES_ACTIVE` | local | Spring profile |
| `DATABASE_DRIVER` | org.postgresql.Driver | JDBC driver |
| `JPA_DDL_AUTO` | validate (prod) | Hibernate DDL mode |
| `FLYWAY_ENABLED` | true | Enable Flyway |
| `MANAGEMENT_ENDPOINTS` | health (prod) | Actuator exposure |
| `LOG_LEVEL_ROOT` | INFO | Root log level |
| `LOG_LEVEL_SANAD` | INFO | SANAD package log level |
| `SHUTDOWN_TIMEOUT` | 30s | Graceful shutdown phase |

## Docker

### Build

```bash
cd apps/sanad-platform
docker build -t sanad-backend:<tag> .
```

### Run

```bash
docker run -d \
  --name sanad-backend \
  -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e DATABASE_URL=jdbc:postgresql://host:5432/sanad \
  -e DATABASE_USERNAME=sanad \
  -e DATABASE_PASSWORD=secret \
  sanad-backend:<tag>
```

### Docker Compose (Local Production Simulation)

Docker Compose requires explicit `DATABASE_USERNAME` and `DATABASE_PASSWORD` — no unsafe default passwords are provided.

```bash
cp .env.example .env
# edit .env with your secure credentials

cd apps/sanad-platform
docker compose -f docker-compose.prod.yml --env-file ../../.env up -d --build
```

## Health Endpoints

| Endpoint | Method | Expected Response |
|---|---|---|
| `/actuator/health` | GET | 200 `{"status":"UP"}` |
| `/actuator/health/liveness` | GET | 200 `{"status":"UP"}` |
| `/actuator/health/readiness` | GET | 200 `{"status":"UP"}` |

In production, `show-details` is set to `never` — component-level details are not exposed.

## Flyway

- **Production**: `enabled=true`, `clean-disabled=true`, `baseline-on-migrate=false`, `validate-on-migrate=true`
- **Local**: `enabled=true`, `clean-disabled=false`, `baseline-on-migrate=true`, `validate-on-migrate=false`
- 9 migrations (V1–V9) covering: tenants, organizations, memberships, users, roles, access capabilities

## Graceful Shutdown

- `server.shutdown=graceful`
- `spring.lifecycle.timeout-per-shutdown-phase=${SHUTDOWN_TIMEOUT:30s}` (configurable via `SHUTDOWN_TIMEOUT`)
- Active requests are drained before the application stops

## Security

- Non-root Docker user (`sanad`)
- Actuator: only `health` exposed in production
- No Swagger UI in production
- No environment/config endpoints exposed
- `JPA_DDL_AUTO=validate` — no schema mutations
- `flyway.clean-disabled=true` — no production DB cleaning
- `ProductionDatabaseProperties` with `@NotBlank` validation fails startup if `DATABASE_URL`, `DATABASE_USERNAME`, or `DATABASE_PASSWORD` are missing
- CORS: `CorsConfig` applies `CORS_ALLOWED_ORIGINS` to `/api/**` routes only; explicit methods, headers, and credentials; no wildcard in production
- Docker Compose requires explicit `DATABASE_USERNAME` and `DATABASE_PASSWORD` — no default credentials
