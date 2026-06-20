# Render Backend Deployment Guide

## Deployment Status: PENDING

## Provisioning Status: PENDING

## Production Backend URL: PENDING VERIFICATION

## Pilot Architecture

```text
Vercel Frontend (snad-app.vercel.app)
    ↓ HTTPS
Render Free Backend (sanad-backend)
    ↓ TLS / Session Pooler
Supabase Free PostgreSQL (Frankfurt)
```

This configuration is approved for pilot and integration verification only. It is not production approval.

## Prerequisites

1. Render account
2. Supabase project in Central EU (Frankfurt)
3. Supabase Session Pooler connection on port `5432`
4. `render.yaml` in the repository root
5. GitHub repository connected to Render

## Manual Provisioning Gate

Before Blueprint Apply:

1. Open Render Dashboard.
2. Create a new Blueprint.
3. Select `snadaiapp-png/SNAD` and branch `main`.
4. Confirm Render parses `render.yaml` without schema or field errors.
5. Confirm only `sanad-backend` is proposed; no Render PostgreSQL resource should appear.
6. Do not apply resources if Render reports a Blueprint validation error.

## Supabase Connection Values

Use the Supabase Session Pooler values from the Frankfurt project:

```text
DATABASE_URL=jdbc:postgresql://<session-pooler-host>:5432/postgres?sslmode=require
DATABASE_USERNAME=<supabase-session-pooler-user>
DATABASE_PASSWORD=<database-password>
```

Rules:

- Do not commit any credential.
- Do not include username or password inside `DATABASE_URL`.
- Use Session Pooler port `5432`.
- Store all three values as Render secret environment variables.

## Apply the Blueprint

1. Go to Render Dashboard → Blueprints.
2. Select `snadaiapp-png/SNAD`.
3. Confirm branch `main` and file `render.yaml`.
4. Enter `DATABASE_URL`, `DATABASE_USERNAME`, and `DATABASE_PASSWORD` when prompted.
5. Click **Apply Blueprint**.

## First Deployment

1. Open `sanad-backend`.
2. Click **Manual Deploy** → **Deploy latest commit**.
3. Wait for the Docker build and Spring Boot startup.
4. Review logs without exposing credentials.

## First Deployment Verification

1. Verify Flyway V1–V9 migrations completed successfully.
2. Verify Hibernate validation passed.
3. Verify `/actuator/health` returns HTTP 200 and `UP`.
4. Verify `/actuator/health/liveness` returns HTTP 200.
5. Verify `/actuator/health/readiness` returns HTTP 200.
6. Verify `/actuator/env` returns 404.
7. Verify `/swagger-ui.html` returns 404.
8. Verify frontend CORS origin is `https://snad-app.vercel.app`.

## Region

- Render backend: Frankfurt
- Supabase database: Central EU (Frankfurt)

## Plans

- Render backend: Free
- Supabase database: Free

## Auto-Deploy

`autoDeployTrigger: off`

The first deployment remains manual. Automatic deployment is not enabled.

## Environment Variables

Non-secret variables are defined in `render.yaml`. The following secret variables use `sync: false` and must be entered in Render Dashboard:

```text
DATABASE_URL
DATABASE_USERNAME
DATABASE_PASSWORD
```

The pilot uses reduced connection pooling:

```text
DATABASE_POOL_MAX=5
DATABASE_POOL_MIN=1
```

## Free-Tier Constraints

- Render Free may sleep during inactivity and has cold-start latency.
- Supabase Free has limited compute, storage, and connection capacity.
- This environment is for pilot verification only.
- Upgrade both backend and database before commercial production launch.

## Rollback

1. Open the service in Render Dashboard.
2. Select **Deployments**.
3. Identify the last known-good deployment.
4. Roll back only after explicit operational approval.
5. Re-run health and integration verification.

Rollback validation: NOT YET EXECUTED
