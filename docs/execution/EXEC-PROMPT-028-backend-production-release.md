# EXEC-PROMPT-028 — Backend Production Release

## Objective

Deploy the SANAD Spring Boot backend to Render, provision managed PostgreSQL, configure production secrets, connect the Vercel frontend to the production API, and validate the complete production release and rollback path.

## Initial Runtime State

| Aspect | Value |
|---|---|
| Backend internal port | 8080 |
| Backend artifact name | sanad-platform-0.1.0-SNAPSHOT.jar |
| Frontend API configuration | Not configured (no NEXT_PUBLIC_API_BASE_URL) |
| Current CORS origin | https://snad-app.vercel.app |
| Required production variables | DATABASE_URL, DATABASE_USERNAME, DATABASE_PASSWORD, SPRING_PROFILES_ACTIVE, CORS_ALLOWED_ORIGINS |
| Database migration strategy | Flyway V1–V9, ddl-auto=validate, clean-disabled=true |
| Existing custom domains | snad-app.vercel.app (Vercel frontend) |
| Existing smoke-test behavior | Frontend-only (Vercel page check) |
| Render configuration | Did not exist |

## Provider Comparison

See [ADR-028](../architecture/adr/ADR-028-backend-hosting-provider.md) for full comparison.

**Selected: Render** — lowest operational complexity, predictable pricing, native Docker support, managed PostgreSQL with backups.

## Region Decision

**Selected: Oregon (US West)** — closest available Render region to Saudi Arabia (~250ms RTT). Migration to AWS Bahrain or Fly.io Amman should be considered when sub-150ms latency is required.

## Render Service Configuration

| Setting | Value |
|---|---|
| Service name | sanad-backend |
| Type | Web Service |
| Runtime | Docker |
| Root directory | apps/sanad-platform |
| Dockerfile path | ./Dockerfile |
| Branch | main |
| Health check path | /actuator/health |
| Region | oregon |
| Plan | starter |
| Auto-deploy | true (from render.yaml) |

## Database Configuration

| Setting | Value |
|---|---|
| Service name | sanad-database |
| Type | Managed PostgreSQL (psdb) |
| Region | oregon |
| Plan | starter |
| Database name | sanad |
| TLS | Enabled (Render default) |
| Public access | Disabled (internal only) |
| Backups | Daily, 7-day retention (Starter plan) |
| Connection limits | Plan-dependent (Starter: ~20 connections) |

Render provides the database URL in PostgreSQL format (`postgresql://user:pass@host:port/db`). The backend requires JDBC format (`jdbc:postgresql://host:port/db`). The `DATABASE_URL` environment variable in the backend must be set to the JDBC-converted form in the Render dashboard.

## Environment-Variable Inventory

| Variable | Source | Secret? |
|---|---|---|
| SPRING_PROFILES_ACTIVE | render.yaml (value: prod) | No |
| SERVER_PORT | render.yaml (value: 8080) | No |
| DATABASE_URL | Render dashboard (JDBC format) | Yes |
| DATABASE_USERNAME | Render dashboard | Yes |
| DATABASE_PASSWORD | Render dashboard | Yes |
| DATABASE_DRIVER | render.yaml (value: org.postgresql.Driver) | No |
| JPA_DDL_AUTO | render.yaml (value: validate) | No |
| FLYWAY_ENABLED | render.yaml (value: true) | No |
| CORS_ALLOWED_ORIGINS | render.yaml (value: https://snad-app.vercel.app) | No |
| LOG_LEVEL_ROOT | render.yaml (value: INFO) | No |
| LOG_LEVEL_SANAD | render.yaml (value: INFO) | No |
| MANAGEMENT_ENDPOINTS | render.yaml (value: health) | No |
| SHUTDOWN_TIMEOUT | render.yaml (value: 30s) | No |
| DATABASE_POOL_MAX | render.yaml (value: 20) | No |
| DATABASE_POOL_MIN | render.yaml (value: 5) | No |
| DATABASE_POOL_TIMEOUT | render.yaml (value: 30000) | No |
| NEXT_PUBLIC_API_BASE_URL | Vercel project settings | No (public) |
| RENDER_DEPLOY_HOOK_URL | GitHub secret | Yes |

## DNS and TLS

| Domain | Provider | TLS | Status |
|---|---|---|---|
| snad-app.vercel.app | Vercel | Automatic | Active |
| sanad-backend.onrender.com | Render | Automatic | Pending provisioning |
| api.sanad-domain (custom) | TBD | TBD | Pending owner domain acquisition |

## Deployment Commands

```bash
# Manual deployment via GitHub Actions
# Go to: Actions → Backend Deploy → Run workflow → Type "deploy"

# Or via Render CLI
render deploys create --service sanad-backend

# Or via Render dashboard
# https://dashboard.render.com/web/srv-xxx/manual-deploy
```

## Production URLs

| Service | URL |
|---|---|
| Frontend | https://snad-app.vercel.app |
| Backend | https://sanad-backend.onrender.com (pending provisioning) |
| Backend health | https://sanad-backend.onrender.com/actuator/health |
| Backend liveness | https://sanad-backend.onrender.com/actuator/health/liveness |
| Backend readiness | https://sanad-backend.onrender.com/actuator/health/readiness |

## Migration Evidence

Flyway V1–V9 will run on first production startup. Expected:
- 9 migrations applied
- Schema version: 9
- All migrations: SUCCESS
- Hibernate validate: PASS

**Evidence will be captured after first production deployment.**

## Smoke-Test Evidence

**Evidence will be captured after first production deployment.**

## Rollback Procedure

1. **Render dashboard rollback**: Navigate to the service → Deployments → select previous deployment → click "Roll back to this deployment"
2. **Redeploy previous commit**: `git checkout <known-good-sha> && git push origin main`
3. **Expected recovery time**: 3–5 minutes
4. **Database compatibility**: All migrations are forward-compatible; rollback does not require database changes
5. **Verification after rollback**: Run backend-production-smoke.yml workflow

## Monitoring Baseline

See [backend-monitoring.md](../operations/backend-monitoring.md) for full details.

## Known Risks

1. **Latency**: Oregon region adds ~250ms RTT to KSA. Not ideal for real-time features.
2. **Starter plan limits**: 512MB RAM, shared CPU. May need upgrading at scale.
3. **No custom domain yet**: Backend uses `*.onrender.com` until owner acquires a domain.
4. **Render PostgreSQL Starter**: 90-day data retention. Upgrade to Standard for 1-year retention.
5. **First deployment requires manual provisioning**: Render account, database creation, and secret configuration require human access.

## Monthly Cost Estimate

| Service | Plan | Cost/month |
|---|---|---|
| Render Web Service | Starter | $7 |
| Render PostgreSQL | Starter | $7 |
| Vercel | Hobby (free) | $0 |
| GitHub | Free | $0 |
| **Total** | | **~$14/month** |

## Commit SHA

_(filled after push)_

## Pull Request URL

_(filled after PR creation)_
