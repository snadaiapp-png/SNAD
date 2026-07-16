# SNAD Runtime Configuration Matrix

## 1. Purpose

This document is the canonical runtime configuration map for the current SNAD pilot topology:

```text
Browser → Vercel Next.js BFF → Public HTTPS tunnel → Local Windows backend → PostgreSQL
```

The backend is not currently hosted on Render. It runs on the project owner's Windows computer and is exposed to Vercel through an approved HTTPS tunnel.

## 2. Frontend — Vercel

| Variable | Classification | Required | Purpose | Validation |
|---|---|---:|---|---|
| `BACKEND_API_BASE_URL` | Server-side deployment configuration | Yes | Public HTTPS tunnel origin used by the trusted Next.js BFF | HTTPS origin, no credentials, no path/query/fragment |
| `BACKEND_REQUEST_TIMEOUT_MS` | Server-side deployment configuration | No | Bounded upstream timeout for tunneled requests | `1000..25000`; default `15000` |
| `NEXT_PUBLIC_API_BASE_URL` | Public direct-development override | No | Bypasses the BFF during explicit local diagnostics | Normally unset |
| Node version | Project configuration | Yes | Next.js build/runtime | `24.x` |

Public frontend:

```text
https://snad-app.vercel.app
```

Browser API namespace:

```text
/api/platform/api/v1/**
```

The BFF forwards only `/api/v1/**`, protects state-changing requests with same-origin checks, stores refresh tokens in a Secure HttpOnly first-party cookie, forwards bearer authorization, and fails closed when the upstream URL is invalid or unreachable.

## 3. Backend — Local Windows Server

### Network

| Value | Required | Purpose |
|---|---:|---|
| `server.address=0.0.0.0` | Yes | Allows the local tunnel agent to reach Spring Boot |
| `server.port=8080` | Yes | Canonical local backend port |
| ngrok/approved tunnel process | Yes for Vercel | Creates the public HTTPS origin |
| `PRODUCTION_BASE_URL` GitHub variable | Yes for monitoring | Must equal the active public tunnel origin |

### Core runtime

| Variable | Required | Purpose |
|---|---:|---|
| `SPRING_PROFILES_ACTIVE=prod` | Yes | Production-like profile |
| `JWT_SECRET` | Yes | JWT signing material |
| `SANAD_CONTROL_PLANE_TENANT_ID` | Yes for Control Plane | Dedicated platform-control tenant UUID |
| `SANAD_CORS_ALLOWED_ORIGINS` | Yes | Exact direct-browser origins for controlled development/diagnostics |
| `BOOTSTRAP_ENABLED=false` | Yes after provisioning | Prevents uncontrolled bootstrap |
| `SERVER_PORT=8080` | Yes | Backend listener |

### Database

| Variable | Required | Purpose |
|---|---:|---|
| `DATABASE_URL` | Yes | PostgreSQL JDBC endpoint |
| `DATABASE_USERNAME` | Yes | Database login |
| `DATABASE_PASSWORD` | Yes | Database credential |
| `JPA_DDL_AUTO=validate` | Yes | Prevents uncontrolled schema mutation |
| `FLYWAY_ENABLED=true` | Yes | Controlled migrations |

## 4. Local Frontend Development

Create `apps/web/.env.local` from `apps/web/.env.local.example`:

```dotenv
BACKEND_API_BASE_URL=http://127.0.0.1:8080
BACKEND_REQUEST_TIMEOUT_MS=15000
```

Local browser traffic also uses `/api/platform`, so the Next.js server—not the browser—connects to Spring Boot. This removes local CORS and cross-site refresh-cookie failure modes.

## 5. Connection Bootstrap

Run:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\windows\connect-local-backend.ps1
```

The script validates local health, starts/reuses ngrok, validates public health, writes the local frontend environment, and updates `PRODUCTION_BASE_URL` through GitHub CLI when available.

The printed tunnel origin must also be configured in Vercel as `BACKEND_API_BASE_URL`, followed by a production redeployment.

## 6. Verification Gate

```text
LOCAL_BACKEND_HEALTH_HTTP_200: PASS
PUBLIC_TUNNEL_HEALTH_HTTP_200: PASS
FRONTEND_PUBLIC_URL_HTTP_200: PASS
BFF_ROUTE_INCLUDED_IN_BUILD: PASS
BFF_AUTH_ME_UNAUTHENTICATED_HTTP_401: PASS
BFF_AUTH_ME_NOT_404_502_503: PASS
BACKEND_STATUS_CONFIGURED_TRUE: PASS
BACKEND_STATUS_REACHABLE_TRUE: PASS
DATABASE_MIGRATIONS_VALID: PASS
JWT_SECRET_PRESENT: PASS
BOOTSTRAP_DISABLED: PASS
```

Failure interpretation:

- `404`: BFF catch-all route is missing from the Vercel deployment.
- `503`: Vercel `BACKEND_API_BASE_URL` is missing or invalid.
- `502`: Vercel cannot reach the tunnel, the tunnel cannot reach the computer, or the backend is not healthy.
- `401` from `/api/v1/auth/me` without a token: expected and proves the authentication boundary is reachable.

## 7. Secret Handling

- Keep database credentials, JWT secrets, tunnel tokens, and Vercel/GitHub tokens out of the repository.
- Never place credentials inside `BACKEND_API_BASE_URL`.
- Do not commit `.env.local`.
- Do not print authentication tokens in CI or application logs.
- Rotate credentials after suspected exposure.

## 8. Availability Constraint

The Vercel application is available only while the local computer, backend, database, internet connection, and tunnel process are running. Temporary ngrok URLs change when the tunnel restarts; Vercel and GitHub must point to the same active URL. Use a reserved/static tunnel domain for a stable pilot endpoint.
