# CRM-007-TECH-006: Environment Configuration Matrix

> **Task:** TASK 6 — ENVIRONMENT CONFIGURATION REVIEW
> **Date:** 2026-07-28
> **Status:** PASS

---

## Production Configuration

### Database

| Setting | Value | Status |
|---|---|---|
| Driver | PostgreSQL | PASS |
| URL | `jdbc:postgresql://...` | PASS |
| DDL Auto | `validate` | PASS |
| Flyway Enabled | `true` | PASS |
| Pool Max | 20 | PASS |
| Pool Min | 5 | PASS |
| Pool Timeout | 30000ms | PASS |

### Runtime

| Setting | Value | Status |
|---|---|---|
| Server Port | 8080 | PASS |
| Spring Profile | `prod` | PASS |
| Log Level | INFO | PASS |
| Shutdown Timeout | 30s | PASS |

### Security

| Setting | Value | Status |
|---|---|---|
| CORS Origins | `https://snad-app.vercel.app` | PASS |
| Wildcards | NOT ALLOWED | PASS |
| Actuator Endpoints | `health` only | PASS |

---

## Deployment Configuration

| Component | Provider | Status |
|---|---|---|
| Frontend | Vercel | PASS |
| Backend | Render | PASS |
| Database | Supabase PostgreSQL | PASS |
| Container | Docker | PASS |

---

## Environment Variables Required

| Variable | Required | Notes |
|---|---|---|
| `SERVER_PORT` | YES | Default: 8080 |
| `SPRING_PROFILES_ACTIVE` | YES | Use `prod` for production |
| `DATABASE_URL` | YES | PostgreSQL connection string |
| `DATABASE_USERNAME` | YES | Database user |
| `DATABASE_PASSWORD` | YES | Secure password |
| `DATABASE_DRIVER` | YES | `org.postgresql.Driver` |
| `DATABASE_POOL_MAX` | YES | Connection pool max |
| `DATABASE_POOL_MIN` | YES | Connection pool min |
| `DATABASE_POOL_TIMEOUT` | YES | Pool timeout ms |
| `JPA_DDL_AUTO` | YES | Must be `validate` in prod |
| `FLYWAY_ENABLED` | YES | `true` for migrations |
| `SANAD_CORS_ALLOWED_ORIGINS` | YES | Exact HTTPS origins |
| `LOG_LEVEL_ROOT` | YES | `INFO` recommended |
| `LOG_LEVEL_SANAD` | YES | `INFO` recommended |
| `MANAGEMENT_ENDPOINTS` | YES | `health` only |
| `SHUTDOWN_TIMEOUT` | YES | `30s` recommended |

---

## Acceptance Criteria

| Criterion | Status |
|---|---|
| Database configuration documented | PASS |
| Runtime variables documented | PASS |
| Production requirements documented | PASS |

---

**Result:** PASS
