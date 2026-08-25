# SANAD Render Production Control Plane

## Purpose

This control plane removes manual release dependence from Render Dashboard. GitHub is the source authority, GHCR immutable SHA-tagged images are the artifact authority, `render.yaml` is the infrastructure contract, Render stores runtime secrets, and protected workflows produce closure evidence.

## Authoritative contract

- Repository: `snadaiapp-png/SNAD`
- Branch: `main`
- Blueprint service name: `sanad-backend`
- Production runtime: **IMAGE-backed** (not Git-backed)
- Image registry: `ghcr.io/snadaiapp-png/snad-backend`
- Image tag for release: exact main SHA (e.g., `ghcr.io/snadaiapp-png/snad-backend:<commit-sha>`)
- `:latest` tag: provisioning/bootstrap convenience only — NOT the production release identity
- Health endpoint: `/actuator/health`
- Readiness endpoint: `/actuator/health/readiness`
- Auto-deploy: **disabled**; releases deploy an exact SHA-tagged image through `production-release.yml`.
- Bootstrap: permanently disabled during normal operation.
- JWT signing material: generated and retained by Render; never committed or copied into conversations.

The externally approved production URL is stored as the GitHub `production` environment variable `PRODUCTION_BASE_URL`. This separates stable public identity from Render's provider hostname.

## Deployment model

Production Render service is **IMAGE-backed**:

- `render.yaml` declares `runtime: image` with `image.url: ghcr.io/snadaiapp-png/snad-backend:latest`.
- The `:latest` tag is used by Render for initial provisioning/bootstrap only.
- Production releases deploy an exact SHA-tagged image via `POST /services/<id>/deploys` with `{ "imageUrl": "ghcr.io/snadaiapp-png/snad-backend:<exact-sha>" }`.
- Render's `autoDeploy` is disabled — no automatic deploys on push or image update.
- `production-release.yml` is the **single deployment authority**.
- `publish-render-image.yml` ONLY builds and publishes the image artifact — it does NOT deploy.

## One-time protected environment configuration

Create a GitHub Environment named `production` and require owner approval.

### Secrets

| Name | Purpose |
|---|---|
| `RENDER_API_KEY` | Render API authentication |
| `RENDER_SERVICE_ID` | Authoritative production service |

### Variables

| Name | Purpose |
|---|---|
| `RENDER_SERVICE_NAME` | Expected service name, normally `sanad-backend` |
| `PRODUCTION_BASE_URL` | Approved HTTPS production API URL |
| `WEB_PRODUCTION_BASE_URL` | Vercel/BFF production URL |
| `SMOKE_TENANT_A_ID` | Tenant A UUID |
| `SMOKE_TENANT_A_EMAIL` | Smoke account A |
| `SMOKE_TENANT_B_ID` | Tenant B UUID |
| `SMOKE_TENANT_B_EMAIL` | Smoke account B |

Database credentials (`DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`) are obtained directly from the authoritative Render service environment at runtime — they are NOT duplicated as GitHub secrets.

Smoke accounts must be non-human, least-privileged, active, and isolated from business data.

## Gate sequence

1. Run **Render Production Preflight**.
2. The workflow verifies image-backed service identity, runtime, autoDeploy disabled, production URL, required Render environment keys (fetched from Render, not GitHub secrets), bootstrap removal, Control Plane tenant DB validation, and health.
3. Passing preflight records sanitized evidence and closes Issue #52 (idempotent — already-closed is not a failure).
4. Squash-merge the approved feature PR with expected-head locking.
5. Trigger `publish-render-image.yml` on main to build and publish `ghcr.io/snadaiapp-png/snad-backend:<new-main-sha>`.
6. Run **SANAD Production Release** with the exact new `main` SHA.
7. The release workflow verifies the exact SHA-tagged image exists in GHCR, deploys it to Render via `{ "imageUrl": "..." }`, verifies readiness, checks Flyway, tests authentication, refresh rotation/replay rejection, tenant binding, logout revocation, and hidden operational endpoints.
8. Passing release posts evidence and closes Gate #032.

## Rollback

Before deploying, the workflow captures the previous live **image reference** from Render deployment history. If deployment verification fails and rollback is enabled, it redeploys that image via `{ "imageUrl": "<previous-image-ref>" }`. Database rollback remains forward-only: create a corrective Flyway migration; never run Flyway clean in production.

## Secret handling

No workflow prints provider values. Evidence artifacts contain service metadata and environment-variable names only. Secret values stay in Render or the protected GitHub production environment.
