# Production Rollback Runbook

## Purpose

Provide a controlled rollback path for production-impacting release failures.

## Rollback triggers

- Production Smoke failure after deployment.
- Backend/BFF reachability failure.
- Authentication or tenant isolation failure.
- Critical security finding.
- Data integrity issue.
- Governance evidence later found invalid.

## Required rollback metadata

| Field | Value |
|---|---|
| Failed release SHA | TBD |
| Previous known-good SHA | TBD |
| Failed deployment ID | TBD |
| Rollback deployment ID | TBD |
| Trigger reason | TBD |
| Decision owner | TBD |
| UTC timestamp | TBD |

## Rollback sequence

1. Freeze further production promotion.
2. Record failed SHA and deployment ID.
3. Identify previous known-good deployment.
4. Execute provider rollback or redeploy known-good SHA.
5. Re-run baseline health checks.
6. Re-run minimal auth/session checks.
7. Re-run tenant isolation smoke.
8. Record rollback evidence.
9. Update the related blocker issue.
10. Keep final release decision as NO-GO until root cause is remediated.

## Post-rollback review

- Root cause analysis required.
- Regression test required.
- Evidence update required.
- Owner approval required before any renewed production attempt.

## Closure

Rollback is considered complete only when production is stable on a known-good SHA and the failed release remains blocked from re-promotion.
