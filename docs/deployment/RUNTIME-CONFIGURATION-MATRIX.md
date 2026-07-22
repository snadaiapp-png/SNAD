# SNAD Runtime Configuration Matrix

## 1. Production topology

```text
Browser → Vercel Next.js BFF → Render sanad-backend → Supabase PostgreSQL
```

The production backend is hosted exclusively on Render at:

```text
https://sanad-backend-mcrj.onrender.com
```

No local server, tunnel, alternate public origin, or direct-browser backend path is permitted in production.

## 2. Frontend — Vercel

| Variable | Required | Purpose |
|---|---:|---|
| `BACKEND_API_BASE_URL` | Yes | Server-only Render origin used by the Next.js BFF; must equal the approved Render URL |
| `BACKEND_REQUEST_TIMEOUT_MS` | No | Bounded upstream timeout; default `15000` |
| `NEXT_PUBLIC_API_BASE_URL` | Forbidden in Production | Local development override only; must be absent from Vercel Production |

Production frontend: `https://snad-app.vercel.app`

Browser API requests use `/api/platform/api/v1/**`. Refresh tokens remain in Secure HttpOnly first-party cookies managed by the Vercel BFF.

## 3. Backend — Render

| Setting | Required value |
|---|---|
| Runtime | Prebuilt image (`runtime: image`) |
| Image registry | `ghcr.io/snadaiapp-png/snad-backend` |
| Region | Frankfurt |
| Health check | `/actuator/health` |
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `SERVER_PORT` | `8080` |
| `BOOTSTRAP_ENABLED` | `false` |
| `JPA_DDL_AUTO` | `validate` |
| `FLYWAY_ENABLED` | `true` |
| `SANAD_CORS_ALLOWED_ORIGINS` | `https://snad-app.vercel.app` |

`DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`, `JWT_SECRET`, notification credentials, and the Control Plane tenant ID are encrypted runtime values. They must never be committed.

Temporary `SANAD_SECURITY_BOOTSTRAP_*` and `CONTROL_PLANE_BOOTSTRAP_ENABLED` variables are forbidden after provisioning.

## 4. Release flow

1. A backend change on `main` triggers `.github/workflows/publish-render-image.yml`.
2. GitHub Actions builds an immutable `linux/amd64` image and publishes both the commit tag and `latest` to GHCR.
3. Render deploys an immutable commit tag through its API; Render never builds the repository.
4. The optional CI deployment stage runs only when `RENDER_AUTODEPLOY_ENABLED=true` and the protected `RENDER_API_KEY` is current.
5. Every deployment must reach `live` and pass health, liveness, and readiness.
6. `.github/workflows/migrate-vercel-backend-to-render.yml` sets Vercel Production `BACKEND_API_BASE_URL`, removes `NEXT_PUBLIC_API_BASE_URL`, forces a production deployment, and verifies the Vercel BFF route.
7. Production web dependencies must pass the locked `npm audit --omit=dev --audit-level=high` gate and the complete Web CI build before release.

This avoids dependence on local infrastructure and keeps GitHub, Render, and Vercel as the production release authorities. Rotate exposed Render or Vercel tokens before storing replacements in protected environments.

## 5. Local development

Create `apps/web/.env.local` from `apps/web/.env.local.example`:

```dotenv
BACKEND_API_BASE_URL=http://127.0.0.1:8080
BACKEND_REQUEST_TIMEOUT_MS=15000
```

Local development uses direct localhost access only. Local values must never be copied into Vercel Production.

## 6. Production verification gate

```text
RENDER_HEALTH_HTTP_200_UP: PASS
RENDER_LIVENESS_HTTP_200_UP: PASS
RENDER_READINESS_HTTP_200_UP: PASS
VERCEL_FRONTEND_HTTP_200: PASS
VERCEL_BACKEND_STATUS_CONFIGURED_TRUE: PASS
VERCEL_BACKEND_STATUS_REACHABLE_TRUE: PASS
VERCEL_BACKEND_STATUS_STATUS_CODE_200: PASS
VERCEL_BACKEND_STATUS_NO_TARGET_HOST: PASS
VERCEL_BFF_AUTH_ME_UNAUTHENTICATED_HTTP_401: PASS
VERCEL_PUBLIC_BACKEND_OVERRIDE_ABSENT: PASS
WEB_PRODUCTION_DEPENDENCY_AUDIT: PASS
WEB_CI_BUILD: PASS
DATABASE_MIGRATIONS_VALID: PASS
BOOTSTRAP_DISABLED: PASS
```

An unauthenticated `401` from `/api/v1/auth/me` is expected and proves the authentication boundary is reachable.

## 7. Secret handling

- Keep Render, Vercel, database, JWT, and notification credentials out of Git.
- Rotate any token disclosed in chat, logs, screenshots, or shell history.
- Do not print authentication tokens in CI or application logs.
- Keep `BACKEND_API_BASE_URL` credential-free, HTTPS-only, and pinned to the approved Render origin in production.
