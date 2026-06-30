# SNAD Infrastructure Hardening — Stage 01 Environment Variable Contract

All environment variables used by the SNAD platform. No secret values are included.

## Backend

| Variable | Component | Required? | Environment | Value Type | Safe Example | Owner | Notes |
|---|---|---|---|---|---|---|---|
| `SPRING_PROFILES_ACTIVE` | Backend | YES | All | String | `prod` | DevOps | Profile selection |
| `SERVER_PORT` | Backend | YES | All | Integer | `8080` | DevOps | HTTP port |
| `DATABASE_URL` | Backend | YES | prod, dev | JDBC URL | `jdbc:postgresql://host:5432/sanad` | DevOps | Render: set via `RENDER_DATABASE_URL` |
| `DATABASE_USERNAME` | Backend | YES | prod, dev | String | `sanad` | DevOps | |
| `DATABASE_PASSWORD` | Backend | YES | prod, dev | Secret | (REDACTED) | Owner | Never commit |
| `DATABASE_DRIVER` | Backend | YES | prod | String | `org.postgresql.Driver` | DevOps | |
| `DATABASE_POOL_MAX` | Backend | NO | prod | Integer | `20` | DevOps | Default: 20 |
| `DATABASE_POOL_MIN` | Backend | NO | prod | Integer | `5` | DevOps | Default: 5 |
| `DATABASE_POOL_TIMEOUT` | Backend | NO | prod | Integer | `30000` | DevOps | Default: 30000ms |
| `JPA_DDL_AUTO` | Backend | YES | prod | String | `validate` | DevOps | Never `create` or `update` in prod |
| `FLYWAY_ENABLED` | Backend | YES | prod | Boolean | `true` | DevOps | |
| `JWT_SECRET` | Backend | YES | prod | Secret (≥32 bytes) | (REDACTED) | Owner | JWT signing key |
| `SANAD_CORS_ALLOWED_ORIGINS` | Backend | YES | prod | String (CSV) | `https://snad-app.vercel.app` | DevOps | Exact HTTPS origins only |
| `BOOTSTRAP_ENABLED` | Backend | NO | prod | Boolean | `false` | DevOps | Admin bootstrap on first run |
| `LOG_LEVEL_ROOT` | Backend | NO | All | String | `INFO` | DevOps | |
| `LOG_LEVEL_SANAD` | Backend | NO | All | String | `INFO` | DevOps | |
| `MANAGEMENT_ENDPOINTS` | Backend | NO | prod | String | `health` | DevOps | Actuator endpoints |
| `SHUTDOWN_TIMEOUT` | Backend | NO | prod | Duration | `30s` | DevOps | Graceful shutdown |

## Email/Security

| Variable | Component | Required? | Environment | Value Type | Safe Example | Owner | Notes |
|---|---|---|---|---|---|---|---|
| `SECURITY_NOTIFICATION_PROVIDER` | Backend | NO | prod | String | `smtp` | DevOps | Email provider for password recovery |
| `SECURITY_NOTIFICATION_FROM` | Backend | NO | prod | Email | (REDACTED) | Owner | Verified sender address |
| `APPLICATION_BASE_URL` | Backend | NO | prod | URL | `https://snad-app.vercel.app` | DevOps | Frontend URL for email links |
| `SPRING_MAIL_HOST` | Backend | NO | prod | String | `smtp.gmail.com` | DevOps | |
| `SPRING_MAIL_PORT` | Backend | NO | prod | Integer | `587` | DevOps | |
| `SPRING_MAIL_USERNAME` | Backend | NO | prod | Email | (REDACTED) | Owner | |
| `SPRING_MAIL_PASSWORD` | Backend | NO | prod | Secret | (REDACTED) | Owner | App password, never commit |

## Frontend

| Variable | Component | Required? | Environment | Value Type | Safe Example | Owner | Notes |
|---|---|---|---|---|---|---|---|
| `NEXT_PUBLIC_API_BASE_URL` | Frontend | YES | prod | URL | `https://backend.onrender.com` | DevOps | Backend API URL |
| `RESEND_API_KEY` | Frontend | NO | prod | Secret | (REDACTED) | Owner | Resend email API key |
| `EMAIL_PROXY_BEARER_TOKEN` | Frontend | NO | prod | Secret | (REDACTED) | Owner | Bearer token for email proxy |
| `EMAIL_PROXY_FROM` | Frontend | NO | prod | Email | (REDACTED) | Owner | Verified sender |
| `NODE_ENV` | Frontend | YES | All | String | `production` | DevOps | |

## Deployment

| Variable | Component | Required? | Environment | Value Type | Notes |
|---|---|---|---|---|---|
| `RENDER_DATABASE_URL` | Render | YES | prod | PostgreSQL URI | Render's internal DB URL format |

## CI (GitHub Actions)

CI secrets are managed in GitHub repository settings. They are never printed in logs.
