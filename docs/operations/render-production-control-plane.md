# SANAD Render Production Control Plane

## Purpose

This control plane removes manual release dependence from Render Dashboard. GitHub is the release authority, `render.yaml` is the infrastructure contract, Render stores runtime secrets, and protected workflows produce closure evidence.

## Authoritative contract

- Repository: `snadaiapp-png/SNAD`
- Branch: `main`
- Blueprint service name: `sanad-backend`
- Health endpoint: `/actuator/health`
- Readiness endpoint: `/actuator/health/readiness`
- Auto-deploy: disabled; releases deploy an exact commit through Render CLI.
- Bootstrap: permanently disabled during normal operation.
- JWT signing material: generated and retained by Render; never committed or copied into conversations.

The externally approved production URL is stored as the GitHub `production` environment variable `PRODUCTION_BASE_URL`. This separates stable public identity from Render's provider hostname.

## One-time protected environment configuration

Create a GitHub Environment named `production` and require owner approval.

### Secrets

| Name | Purpose |
|---|---|
| `RENDER_API_KEY` | Render API/CLI authentication |
| `RENDER_SERVICE_ID` | Authoritative production service |
| `PRODUCTION_DATABASE_URL` | Read-only-capable migration verification connection |
| `SMOKE_TENANT_A_PASSWORD` | Dedicated production smoke account A |
| `SMOKE_TENANT_B_PASSWORD` | Dedicated production smoke account B |

### Variables

| Name | Purpose |
|---|---|
| `RENDER_SERVICE_NAME` | Expected service name, normally `sanad-backend` |
| `PRODUCTION_BASE_URL` | Approved HTTPS production API URL |
| `SMOKE_TENANT_A_ID` | Tenant A UUID |
| `SMOKE_TENANT_A_EMAIL` | Smoke account A |
| `SMOKE_TENANT_B_ID` | Tenant B UUID |
| `SMOKE_TENANT_B_EMAIL` | Smoke account B |

Smoke accounts must be non-human, least-privileged, active, and isolated from business data.

## Gate sequence

1. Run **Render Production Preflight**.
2. The workflow verifies provider identity, repository, `main`, environment-key policy, bootstrap removal, and health.
3. Passing preflight records sanitized evidence and closes Issue #52.
4. Squash-merge the approved feature PR with expected-head locking.
5. Run **SANAD Production Release** with the exact new `main` SHA.
6. The release workflow deploys that SHA, verifies readiness, checks Flyway V10/V11, tests authentication, refresh rotation/replay rejection, tenant binding, logout revocation, and hidden operational endpoints.
7. Passing release posts evidence and closes Gate #032.

## Rollback

Before deploying, the workflow captures the previous successful commit. If deployment verification fails and rollback is enabled, it redeploys that commit and verifies health. Database rollback remains forward-only: create a corrective Flyway migration; never run Flyway clean in production.

## Secret handling

No workflow prints provider values. Evidence artifacts contain service metadata and environment-variable names only. Secret values stay in Render or the protected GitHub production environment.
