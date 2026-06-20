# Operational Readiness Runbook

## Gate
Issue #36 — Operational Readiness

## Roles

- Project owner: final GO/NO-GO authority.
- Incident commander: coordinates response, decisions, and communication.
- Platform owner: hosting, database, networking, deployment, and recovery.
- Application owner: backend and frontend diagnosis and correction.
- Security owner: security-event assessment and containment.
- Communications owner: internal and customer-facing updates.

## Incident severity

| Severity | Definition | Initial acknowledgement |
|---|---|---:|
| SEV-1 | Full outage, data-loss risk, or confirmed security incident | 5 minutes |
| SEV-2 | Major degradation or partial outage | 15 minutes |
| SEV-3 | Limited degradation or capacity warning | 4 hours |
| SEV-4 | Informational or planned maintenance event | 1 business day |

## Standard incident procedure

1. Detect and record the incident start time.
2. Assign severity and incident commander.
3. Preserve logs and evidence.
4. Confirm customer and data impact.
5. Stabilize through restart, rollback, traffic control, or dependency isolation.
6. Verify backend health, readiness, database connectivity, and frontend integration.
7. Communicate status and next update time.
8. Recover service and monitor stability.
9. Record root cause, corrective action, and prevention work.
10. Close only after owner review.

## Production smoke checks

- Backend `/actuator/health` returns `UP`.
- Liveness and readiness return HTTP 200.
- Frontend integration reports configured and reachable with HTTP 200.
- Database connection and Flyway schema version are valid.
- Critical authentication and tenant-isolation journeys pass.
- Monitoring and alert delivery are operational.

## Deployment rollback

- Identify the last known-good application release.
- Confirm database migrations are backward compatible.
- Roll back application release without downgrading or cleaning the database.
- Execute health and integration smoke checks.
- Record timestamps, release identifiers, and observed impact.

## Launch sequence

1. Internal launch.
2. Controlled beta launch.
3. Exit-criteria review.
4. Public launch after explicit GO.
5. Elevated monitoring and rollback authority during the post-launch window.

## Exit rule

Issue #36 closes only when ownership is assigned, contacts are verified, runbooks are committed, launch checklist is complete, and a documented readiness exercise passes.