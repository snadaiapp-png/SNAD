# SNAD Runtime Configuration Matrix

## 1. Production topology

```text
Browser → Vercel Next.js BFF → Render sanad-backend → Supabase PostgreSQL
```

The production backend is hosted on Render at:

```text
https://sanad-backend-mcrj.onrender.com
```

The local Windows server and ngrok are development-only and are not part of the production request path.

## 2. Frontend — Vercel

| Variable | Required | Purpose |
|---|---:|---|
| `BACKEND_API_BASE_URL` | Yes | Server-only Render origin used by the Next.js BFF |
| `BACKEND_REQUEST_TIMEOUT_MS` | No | Bounded upstream timeout; default `15000` |
| `NEXT_PUBLIC_API_BASE_URL` | No | Direct-browser development override; normally unset in production |

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
3. The workflow deploys the immutable commit tag through the Render API.
4. The workflow waits for `live` and verifies health, liveness, and readiness.

Render does not build the repository. This avoids dependence on Render build-pipeline minutes and keeps GitHub as the release authority.

## 5. Local development

Create `apps/web/.env.local` from `apps/web/.env.local.example`:

```dotenv
BACKEND_API_BASE_URL=http://127.0.0.1:8080
BACKEND_REQUEST_TIMEOUT_MS=15000
```

Local development may use localhost or a temporary tunnel, but those values must not be copied into Vercel Production.

## 6. Production verification gate

```text
RENDER_HEALTH_HTTP_200_UP: PASS
RENDER_LIVENESS_HTTP_200_UP: PASS
RENDER_READINESS_HTTP_200_UP: PASS
VERCEL_FRONTEND_HTTP_200: PASS
VERCEL_BACKEND_STATUS_CONFIGURED_TRUE: PASS
VERCEL_BACKEND_STATUS_REACHABLE_TRUE: PASS
VERCEL_BFF_AUTH_ME_UNAUTHENTICATED_HTTP_401: PASS
DATABASE_MIGRATIONS_VALID: PASS
BOOTSTRAP_DISABLED: PASS
```

An unauthenticated `401` from `/api/v1/auth/me` is expected and proves the authentication boundary is reachable.

## 7. Secret handling

- Keep Render, Vercel, database, JWT, and notification credentials out of Git.
- Rotate any token disclosed in chat, logs, screenshots, or shell history.
- Do not print authentication tokens in CI or application logs.
- Keep `BACKEND_API_BASE_URL` credential-free and HTTPS-only in production.
