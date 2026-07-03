# EXEC-PROMPT-028 — Backend Production Release

## Status

**IN PROGRESS — MANUAL PROVISIONING PENDING**

## Objective

Prepare SANAD for the first production backend deployment on Render, provision managed PostgreSQL, connect the Vercel frontend, and validate deployment, smoke testing, and rollback.

## Provider Decision

| Item | Value |
|---|---|
| Provider | Render |
| Region | Frankfurt — EU Central |
| Backend service | `sanad-backend` |
| Runtime | Docker |
| Branch | `main` |
| Web service plan | `starter` |
| Database service | `sanad-database` |
| Database plan | `basic-256mb` |
| Database name | `sanad` |
| First deployment | Manual |
| Automatic deployment | Disabled |
| Blueprint setting | `autoDeployTrigger: off` |

## Repository Configuration

| Setting | Value |
|---|---|
| Root directory | `apps/sanad-platform` |
| Dockerfile path | `apps/sanad-platform/Dockerfile` |
| Docker context | `apps/sanad-platform` |
| Health check path | `/actuator/health` |
| Database declaration | Top-level `databases:` section |
| Structural Blueprint validation | Passed in CI |
| Official Render Blueprint validation | Pending manual provisioning gate |

## Database Wiring

The Blueprint wires database values through `fromDatabase`:

- `DATABASE_USERNAME` ← `user`
- `DATABASE_PASSWORD` ← `password`
- `RENDER_DATABASE_URL` ← `connectionString`

`RenderDatabaseUrlConverter` parses `RENDER_DATABASE_URL` during Spring Boot startup and sets the JDBC datasource URL, username, and password. Explicit `DATABASE_URL`, `DATABASE_USERNAME`, and `DATABASE_PASSWORD` values override converted values.

No database credentials are copied manually and no secrets are committed.

## Production Controls

- Production profile: `prod`
- Hibernate schema mode: `validate`
- Flyway: enabled
- CORS origin: `https://snad-app.vercel.app`
- Health, liveness, and readiness endpoints enabled
- `/actuator/env` must return 404
- Swagger UI must return 404
- Graceful shutdown timeout: 30 seconds

## Frontend Integration

- Public variable: `NEXT_PUBLIC_API_BASE_URL`
- Status route: `/api/system/backend-status`
- Production smoke verifies frontend-to-backend reachability
- Route output is limited to `configured`, `reachable`, and `statusCode`

## Current Verification Status

| Verification | Status |
|---|---|
| Backend tests | 303 passed; 0 failures; 0 errors; 10 skipped locally |
| Frontend tests | 19 passed; 0 failures |
| Backend CI | Passed on current PR head |
| Web CI | Passed on current PR head |
| Blueprint structural validation | Passed |
| Official Render validation | Pending |
| Render resource provisioning | Pending |
| Flyway V1–V9 production execution | Pending |
| Production deployment | Pending |
| Production smoke | Not executed |
| Rollback validation | Not executed |

## Production URLs

| Endpoint | Status |
|---|---|
| Backend production URL | Pending verification |
| Backend health URL | Pending verification |
| Backend liveness URL | Pending verification |
| Backend readiness URL | Pending verification |

## Backup and Recovery

Backup, PITR, log-retention, and recovery-window availability must be confirmed in the Render Dashboard during provisioning. No production backup, PITR, or retention configuration has yet been verified.

## Remaining Manual Gate

1. Open Render Dashboard and create a Blueprint from `snadaiapp-png/SNAD`.
2. Confirm Render parses `render.yaml` without field or schema errors.
3. Apply the Blueprint only after validation succeeds.
4. Confirm `sanad-backend` and `sanad-database` are provisioned in Frankfurt.
5. Configure GitHub and Vercel production variables.
6. Trigger the first manual deployment.
7. Verify Flyway V1–V9, health, liveness, readiness, CORS, and frontend integration.
8. Run production smoke and validate rollback.

## Cost

All pricing is a planning estimate and must be confirmed in the Render Dashboard before provisioning.

## Pull Request

https://github.com/snadaiapp-png/SNAD/pull/23
