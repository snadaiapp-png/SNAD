# SNAD Pilot Deployment Operating Model

## Purpose

This document defines how reviewed SNAD changes move from `main` to the controlled pilot environment. It complements the legacy Render provisioning guide and the runtime configuration matrix.

## Current pilot topology

```text
Vercel frontend
  -> HTTPS API
Render backend
  -> PostgreSQL session pooler
Supabase PostgreSQL
```

The pilot is for controlled integration and verification. It is not commercial production.

## Release authority

- GitHub `main` is the code authority.
- A deployment must identify the exact reviewed commit.
- Vercel may publish the frontend from the reviewed branch integration.
- Render backend deployment remains intentional and auditable.
- Database schema is controlled by Flyway migrations.
- Documentation never substitutes for passing evidence.

## Deployment prerequisites

Before deployment, verify:

- Required CI checks passed.
- Security baseline passed.
- The target commit is present on `main`.
- Runtime configuration exists in the deployment platform.
- No secret value is present in repository files.
- A rollback target is known.
- Database migration impact is understood.

## Frontend deployment

1. Confirm the Vercel project is linked to the correct repository.
2. Confirm the root and build settings target `apps/web`.
3. Confirm the public API base URL uses HTTPS.
4. Deploy the reviewed commit.
5. Confirm deployment state is `READY`.
6. Confirm the public page returns HTTP 200.
7. Confirm the Arabic and Latin fonts load from the generated Next.js assets.
8. Confirm login and recovery screens display the active SNAD identity.

## Backend deployment

1. Select the exact reviewed `main` commit.
2. Deploy through the controlled Render service.
3. Confirm the application starts without exposing runtime values.
4. Confirm Flyway migrations complete.
5. Confirm schema validation completes.
6. Confirm health, liveness, and readiness endpoints pass.
7. Confirm browser-origin policy matches the frontend.
8. Confirm controlled bootstrap is disabled.
9. Record deployment ID, commit SHA, and UTC time.

## Database verification

- Confirm expected Flyway version.
- Confirm no destructive migration was applied without approval.
- Confirm the application can read and write pilot data.
- Confirm backup/restore validation remains current.
- Do not use production customer data in the pilot.

## Account-recovery activation

The recovery code may be deployed before external delivery is activated. Real delivery is accepted only when:

- The approved notification provider is configured in the deployment platform.
- The approved sender identity is configured.
- A pilot recovery message is delivered.
- The message contains a one-time HTTPS link and no password.
- The link works once and rejects reuse.
- Previous sessions are revoked after reset.
- A change-confirmation message is delivered.
- Sanitized evidence is recorded in Issue #150.

Use `docs/operations/ACCOUNT-RECOVERY-EMAIL-RUNBOOK.md` for the complete procedure.

## Post-deployment evidence

Record:

```text
MAIN_SHA
FRONTEND_DEPLOYMENT_ID
BACKEND_DEPLOYMENT_ID
DEPLOYED_AT_UTC
FRONTEND_HTTP_RESULT
BACKEND_HEALTH_RESULT
READINESS_RESULT
MIGRATION_RESULT
DATABASE_CONNECTIVITY_RESULT
CORS_RESULT
BOOTSTRAP_DISABLED_RESULT
NOTIFICATION_PROVIDER_STATE
RECOVERY_E2E_RESULT
ROLLBACK_REFERENCE
```

Never record passwords, raw recovery values, signing material, database credentials, or provider credentials.

## Failure and rollback

If deployment fails:

1. Stop further rollout.
2. Preserve logs and identifiers without secrets.
3. Determine whether failure is build, runtime, database, or external-provider related.
4. Roll back to the last known-good deployment when safe.
5. Do not apply a destructive database rollback without explicit approval.
6. Re-run health and integration checks.
7. Record the incident and corrective action.

## Pilot constraints

- Free-tier cold starts may occur.
- Capacity and connection limits apply.
- No commercial SLA applies.
- Monitoring and disaster recovery are pilot-grade.
- Production requires a separately approved architecture and operating model.

## Governance

- Issue #101 controls the development security gate.
- Issue #150 controls account-recovery runtime evidence.
- Pilot success does not authorize commercial go-live.
- OWASP final acceptance must not be inferred from unrelated passing checks.
