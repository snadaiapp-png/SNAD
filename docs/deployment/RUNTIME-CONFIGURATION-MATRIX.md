# SNAD Runtime Configuration Matrix

## 1. Purpose

This document is the canonical map of runtime configuration for the SNAD pilot environment. It classifies values as public configuration or deployment-managed secrets and defines verification requirements.

## 2. Frontend — Vercel

| Variable | Classification | Required | Purpose | Validation |
|---|---|---:|---|---|
| `BACKEND_API_BASE_URL` | Server-side deployment configuration | Yes | HTTPS base URL used only by the trusted same-origin Next.js BFF | Must be a valid HTTPS URL outside localhost and must not contain credentials |
| `NEXT_PUBLIC_API_BASE_URL` | Public local/development configuration | No in production | Direct API base URL for local development only | Production browser traffic must use `/api/platform`; do not expose backend secrets or credentials |
| Node version | Project configuration | Yes | Next.js build runtime | Must match `24.x` project configuration |

The public frontend URL is `https://snad-app.vercel.app`.

Production browser requests use the same-origin BFF namespace:

```text
/api/platform/api/v1/**
```

The BFF forwards only `/api/v1/**` routes, keeps rotating refresh tokens in a Secure HttpOnly first-party cookie, forwards bearer authorization for protected calls, rejects foreign origins for state-changing requests, and fails closed when the backend URL is unavailable or invalid.

## 3. Backend — Render

### Core runtime

| Variable | Classification | Required | Purpose |
|---|---|---:|---|
| `SPRING_PROFILES_ACTIVE` | Non-secret | Yes | Selects the production profile |
| `SERVER_PORT` | Non-secret | Yes | Backend listening port |
| `JWT_SECRET` | Secret | Yes | JWT signing material |
| `SANAD_CONTROL_PLANE_TENANT_ID` | Secret deployment configuration | Yes for Control Plane | Dedicated platform-control tenant UUID; must never reference a client tenant |
| `SANAD_CORS_ALLOWED_ORIGINS` | Non-secret | Yes | Approved frontend origin |
| `COOKIE_SECURE` | Non-secret | Yes | Requires secure cookies in deployed environments |
| `COOKIE_SAME_SITE` | Non-secret | Yes | Cookie cross-site policy |
| `BOOTSTRAP_ENABLED` | Non-secret | Yes | Must remain false after controlled bootstrap |
| `LOG_LEVEL_ROOT` | Non-secret | No | Root logging level |
| `LOG_LEVEL_SANAD` | Non-secret | No | Application logging level |

### Database

| Variable | Classification | Required | Purpose |
|---|---|---:|---|
| `DATABASE_URL` or mapped Spring datasource URL | Secret-sensitive connection configuration | Yes | PostgreSQL JDBC endpoint |
| `DATABASE_USERNAME` | Secret | Yes | Database login |
| `DATABASE_PASSWORD` | Secret | Yes | Database credential |
| `JPA_DDL_AUTO` | Non-secret | Yes | Must be `validate` in the deployed environment |
| `FLYWAY_ENABLED` | Non-secret | Yes | Enables controlled schema migrations |
| `DATABASE_POOL_MAX` | Non-secret | Yes | Pilot connection-pool upper limit |
| `DATABASE_POOL_MIN` | Non-secret | Yes | Pilot connection-pool lower limit |
| `DATABASE_POOL_TIMEOUT` | Non-secret | Yes | Connection acquisition timeout |

### Account recovery notifications

| Variable | Classification | Required for real email | Purpose |
|---|---|---:|---|
| `SECURITY_NOTIFICATION_PROVIDER` | Non-secret | Yes | Selects the approved notification provider |
| `SECURITY_NOTIFICATION_ENDPOINT` | Secret-sensitive configuration | Only for HTTP provider | Authorized HTTPS delivery endpoint |
| `SECURITY_NOTIFICATION_BEARER_TOKEN` | Secret | Usually for HTTP provider | Authenticates the backend to the delivery endpoint |
| `SECURITY_NOTIFICATION_FROM` | Controlled configuration | Yes | Approved SNAD sender identity |
| `APPLICATION_BASE_URL` | Non-secret | Yes | Builds recovery links to the public frontend |
| `SPRING_MAIL_PASSWORD` | Secret | Required for SMTP provider | SMTP application credential stored only in Render |

## 4. Secret-handling policy

- Store secrets only in Render, Vercel, or an approved secret manager.
- Do not commit `.env` files containing credentials.
- Do not include credentials in JDBC URLs.
- Do not print secret values in CI, deployment, or application logs.
- Rotate secrets after suspected exposure.
- Use the smallest provider permission scope possible.
- Treat application passwords, deploy hooks, refresh tokens, and bearer values as credentials.
- Never expose `BACKEND_API_BASE_URL` through a `NEXT_PUBLIC_*` variable in production unless it is intentionally public and reviewed.

## 5. Environment states

### Local

- External email delivery is not required.
- Local notification behavior may capture or suppress messages.
- HTTP localhost API URLs are permitted.
- Direct `NEXT_PUBLIC_API_BASE_URL` access is permitted for local development.
- Test data only.

### CI/Test

- No external email connection.
- Deterministic test gateway.
- Ephemeral PostgreSQL or approved test database.
- Secrets provided only when a test explicitly requires them.
- BFF tests must prove route allowlisting, same-origin enforcement, refresh-token cookie handling, and generic upstream-failure responses.

### Pilot

- Vercel frontend.
- Render backend.
- Supabase PostgreSQL.
- HTTPS only.
- Same-origin BFF for browser-to-backend traffic.
- Controlled pilot users and data.
- Free-tier constraints acknowledged.
- No commercial production authorization.

### Commercial production

Not authorized. Requires a separate infrastructure, security, compliance, capacity, disaster-recovery, and owner-approval decision.

## 6. Deployment verification checklist

```text
FRONTEND_DEPLOYMENT_READY: PASS
FRONTEND_PUBLIC_URL_HTTP_200: PASS
BFF_ROUTE_INCLUDED_IN_BUILD: PASS
BFF_AUTH_ME_UNAUTHENTICATED_HTTP_401: PASS
BFF_AUTH_ME_NOT_404_502_503: PASS
BACKEND_HEALTH_UP: PASS
BACKEND_LIVENESS_UP: PASS
BACKEND_READINESS_UP: PASS
DATABASE_MIGRATIONS_VALID: PASS
CONTROL_PLANE_TENANT_ID_PRESENT: PASS
CONTROL_PLANE_TENANT_IS_NOT_CLIENT_TENANT: PASS
CORS_ORIGIN_EXACT: PASS
JWT_SECRET_PRESENT: PASS
BOOTSTRAP_DISABLED: PASS
NOTIFICATION_PROVIDER_CONFIGURED: PASS or DOCUMENTED_DISABLED
NO_SECRET_IN_LOGS: PASS
RECOVERY_LINK_BASE_URL_CORRECT: PASS
```

A Vercel build is not accepted when its route inventory omits:

```text
ƒ /api/platform/[...path]
```

An unauthenticated request to the deployed BFF session endpoint must return `401`. The following results are release blockers:

- `404`: BFF route was not included in the deployed build.
- `503`: `BACKEND_API_BASE_URL` is missing or invalid.
- `502`: the configured backend is unreachable or failed during proxying.

## 7. Configuration change control

Every change to a secret name, external endpoint, sender identity, CORS origin, cookie policy, database connection, control-plane tenant binding, authentication value, or BFF route must include:

1. A reviewed repository or deployment change.
2. An owner and rollback plan.
3. Sanitized verification evidence.
4. No secret value in the pull request.
5. Updated documentation.
6. A new end-to-end test when authentication, account recovery, or Control Plane access is affected.
