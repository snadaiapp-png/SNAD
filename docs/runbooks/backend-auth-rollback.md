# Backend Authentication Rollback Runbook

## Scope

This runbook covers rollback of EXEC-PROMPT-032A authentication and session functionality after production deployment.

## Authoritative revisions

- Authentication merge: `eabaf127deff75a2ba590f5ce6d148c63d10a16d`
- Last pre-auth runtime revision: `083511210c21c7a93e7886df7f5dffd550429361`
- Production schema version after deployment: Flyway `11`

## Constraints

- Do not run `flyway clean` in production.
- Do not delete or reverse V10/V11 directly.
- V10/V11 are additive; retain schema version 11 during application rollback.
- Preserve database backup and deployment evidence before rollback.
- Rotate any credential or signing material that may have been exposed.

## Rollback procedure

1. Record the current Render deployment revision and timestamp.
2. Confirm the approved rollback revision is `083511210c21c7a93e7886df7f5dffd550429361`.
3. Deploy that revision through Render Manual Deploy, or create and merge a revert of `eabaf127deff75a2ba590f5ce6d148c63d10a16d` when repository history must represent the rollback.
4. Keep PostgreSQL at Flyway schema version 11.
5. Wait for the Render deployment to reach `Live`.
6. Verify:
   - `/actuator/health` returns HTTP 200 and `status=UP`.
   - `/actuator/health/liveness` returns HTTP 200.
   - `/actuator/health/readiness` returns HTTP 200.
   - `/actuator/env` remains unavailable.
   - Swagger UI remains unavailable.
7. Run `Backend Production Smoke` against the authoritative Render URL.
8. Record the deployment, health, smoke run, and incident decision in the governing issue.

## Forward recovery

After root-cause remediation, deploy the corrected `main` revision, confirm Flyway remains at version 11 or a later approved forward migration, and rerun:

- Render Production Preflight
- Backend Production Smoke
- Auth & Tenant Production Acceptance

## Acceptance evidence

A rollback is considered operationally executable when the target revision, non-destructive database policy, provider deployment steps, health checks, and post-deployment smoke workflow are documented and reviewed. An actual rollback is not performed solely to generate evidence on a healthy production service.
