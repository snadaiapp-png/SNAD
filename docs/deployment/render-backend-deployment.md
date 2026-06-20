# Render Backend Deployment Guide

## Overview

The SANAD backend is deployed to [Render](https://render.com) as a Docker web service with a managed PostgreSQL database.

## Architecture

```
Vercel Frontend (snad-app.vercel.app)
    ↓ HTTPS
Render Backend (sanad-backend.onrender.com)
    ↓ Internal connection
Render PostgreSQL (sanad-database)
```

## Prerequisites

1. A Render account with billing configured
2. The `render.yaml` blueprint in the repository root
3. GitHub repository connected to Render

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
5. Click "Apply"

### Step 3: Configure Secrets

After the blueprint is applied, set the following secret environment variables in the Render dashboard for the `sanad-backend` service:

| Variable | Value |
|---|---|
| `DATABASE_URL` | JDBC URL from the PostgreSQL service (see conversion below) |
| `DATABASE_USERNAME` | Database username (from Render PostgreSQL) |
| `DATABASE_PASSWORD` | Database password (from Render PostgreSQL) |

### Step 4: Convert Render PostgreSQL URL to JDBC format

Render provides the database URL in PostgreSQL format:
```
postgresql://user:password@host:port/database
```

Spring Boot requires JDBC format:
```
jdbc:postgresql://host:port/database
```

To convert:
1. Copy the internal database URL from the Render PostgreSQL dashboard
2. Remove the `postgresql://user:password@` prefix
3. Prepend `jdbc:postgresql://`
4. Set as `DATABASE_URL` in the backend service environment variables

**Never commit the actual URL or credentials to the repository.**

### Step 5: Deploy

1. In the Render dashboard, click "Manual Deploy" → "Deploy latest commit"
2. Wait for the build to complete (3–5 minutes)
3. Verify health: `curl https://sanad-backend.onrender.com/actuator/health`

## Environment Variables

Non-secret variables are defined in `render.yaml`. Secret variables must be set in the Render dashboard.

See `.env.example` at the repository root for the full variable inventory.

## Health Checks

Render monitors the backend via the configured health check path (`/actuator/health`). If the endpoint returns non-200 for 3 consecutive checks, Render restarts the service.

## Auto-Deploy

Auto-deploy is enabled in `render.yaml`. Every push to `main` triggers a new deployment. To disable, set `autoDeploy: false` in `render.yaml` or toggle in the dashboard.

## Rollback

1. Go to the service in the Render dashboard
2. Click the "Deployments" tab
3. Find the last known-good deployment
4. Click "Roll back to this deployment"
5. Wait 3–5 minutes for the rollback to complete
6. Verify: `curl https://sanad-backend.onrender.com/actuator/health`

## Custom Domain

To add a custom domain (e.g., `api.sanad.example`):

1. Go to the service → Settings → Custom Domains
2. Add the domain
3. Configure DNS CNAME record pointing to `sanad-backend.onrender.com`
4. Wait for TLS certificate provisioning (automatic)
5. Update `CORS_ALLOWED_ORIGINS` if the frontend domain changes

## Region

The backend and database are deployed in the **Frankfurt** region (US West). This is the closest available Render region to Saudi Arabia.

## Cost

| Service | Plan | Monthly Cost |
|---|---|---|
| sanad-backend (Web Service) | Starter | $7 |
| sanad-database (PostgreSQL) | Starter | $7 |
| **Total** | | **~$14** |
