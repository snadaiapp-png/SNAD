# Render Backend Deployment Guide

## Deployment Status: PENDING

## Provisioning Status: PENDING

## Production Backend URL: PENDING VERIFICATION

## Architecture

```
Vercel Frontend (snad-app.vercel.app)
    ↓ HTTPS
Render Backend — pending provisioning
    ↓ private/internal connection
Render PostgreSQL — pending provisioning
```

## Prerequisites

1. A Render account with billing configured
2. The `render.yaml` blueprint in the repository root
3. GitHub repository connected to Render

## Manual Provisioning Gate

Before Blueprint Apply:

1. Open Render Dashboard.
2. Create a new Blueprint.
3. Select `snadaiapp-png/SNAD`.
4. Confirm Render parses `render.yaml` without schema or field errors.
5. Do not apply resources if Render reports any Blueprint validation error.
6. Capture the validation result without exposing secrets.

## Initial Provisioning (Manual)

### Step 1: Create Render account

1. Go to https://render.com/register
2. Sign up with GitHub
3. Add a payment method

### Step 2: Apply the Blueprint

1. Go to https://dashboard.render.com/blueprints
2. Select the `snadaiapp-png/SNAD` repository
3. Render will detect `render.yaml` and propose the services
4. Review the configuration
5. Confirm Render parses the Blueprint without errors
6. Click "Apply"

### Step 3: Database Credential Wiring

Database credentials are wired automatically via `fromDatabase` references in `render.yaml`:

```
DATABASE_USERNAME:
  fromDatabase.property=user

DATABASE_PASSWORD:
  fromDatabase.property=password

RENDER_DATABASE_URL:
  fromDatabase.property=connectionString
```

No manual database credential copying is required.

`RenderDatabaseUrlConverter` converts `RENDER_DATABASE_URL` (postgresql:// format) to JDBC properties (`spring.datasource.url`, `spring.datasource.username`, `spring.datasource.password`) at application startup. Explicit `DATABASE_URL`, `DATABASE_USERNAME`, or `DATABASE_PASSWORD` override the converter output.

### Step 4: Deploy

1. In the Render dashboard, click "Manual Deploy" → "Deploy latest commit"
2. Wait for the build to complete (3–5 minutes)
3. Verify health: `curl https://<backend-url>/actuator/health`

### Step 5: First Deployment Verification

After the first deployment:

1. Verify Flyway V1–V9 migrations completed successfully (check Render logs)
2. Verify Hibernate validate mode passed
3. Verify health endpoint returns 200 UP
4. Verify liveness endpoint returns 200 UP
5. Verify readiness endpoint returns 200 UP
6. Verify `/actuator/env` returns 404
7. Verify `/swagger-ui.html` returns 404

## Region

Frankfurt — EU Central

## Database Plan

basic-256mb

## Auto-Deploy

`autoDeployTrigger: off`

The first deployment is manual.
Automatic deployment is not enabled.

After one successful production deployment and rollback validation, a future change may use `autoDeployTrigger: checksPass`.

## Environment Variables

Non-secret variables are defined in `render.yaml`. Secret variables are wired via `fromDatabase` references.

See `.env.example` at the repository root for the full variable inventory.

## Health Checks

Render monitors the backend via the configured health check path (`/actuator/health`). Health check behavior must be verified after provisioning.

## Rollback

1. Go to the service in the Render dashboard
2. Click the "Deployments" tab
3. Find the last known-good deployment
4. Click "Roll back to this deployment"
5. Wait 3–5 minutes for the rollback to complete
6. Verify: `curl https://<backend-url>/actuator/health`

Rollback validation: NOT YET EXECUTED

## Custom Domain

To add a custom domain (e.g., `api.sanad.example`):

1. Go to the service → Settings → Custom Domains
2. Add the domain
3. Configure DNS CNAME record pointing to the Render service URL
4. Wait for TLS certificate provisioning (automatic)
5. Update `CORS_ALLOWED_ORIGINS` if the frontend domain changes

No custom domain is currently configured.

## Cost Estimate

| Service | Plan | Estimated Monthly Cost |
|---|---|---|
| sanad-backend (Web Service) | Starter | ~$7 |
| sanad-database (PostgreSQL) | basic-256mb | ~$7 |
| **Total** | | **~$14** |

Pricing must be confirmed in Render Dashboard before provisioning.

## PITR and Backups

PITR availability and recovery window depend on the Render Workspace plan:
- Hobby workspace: 3-day recovery window
- Pro or higher workspace: 7-day recovery window

Final availability must be confirmed during provisioning.
