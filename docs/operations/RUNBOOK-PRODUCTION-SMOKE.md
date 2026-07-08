# Production Smoke Runbook

## Purpose

Prove the production application path end-to-end. Vercel READY is not enough.

## Required preconditions

- Approved production deployment candidate SHA.
- Vercel deployment ID recorded.
- Backend target configured server-side.
- Test identities and tenant fixtures exist in protected environments only.
- No secret values appear in issue text, commit text, workflow logs, or screenshots.

## Smoke sequence

1. Record candidate SHA and deployment ID.
2. Verify backend status through the same-origin BFF.
3. Verify login through the production entry point.
4. Verify current-user endpoint.
5. Verify dashboard load.
6. Verify tenant binding.
7. Verify cross-tenant denial.
8. Verify logout/session revocation.
9. Review workflow output for secret safety.
10. Record result in #200.

## Evidence template

| Check | Result | Evidence link |
|---|---|---|
| Candidate SHA | TBD | TBD |
| Vercel deployment ID | TBD | TBD |
| Backend configured | TBD | TBD |
| Backend reachable | TBD | TBD |
| Login | TBD | TBD |
| Current user | TBD | TBD |
| Dashboard | TBD | TBD |
| Tenant binding | TBD | TBD |
| Cross-tenant denial | TBD | TBD |
| Logout | TBD | TBD |
| No-secret review | TBD | TBD |

## Pass criteria

Production Smoke PASS requires zero failures across all checks and no exposed sensitive values in output.

## Failure handling

- Mark #200 as still blocked.
- Preserve logs as artifacts when safe.
- Do not publish Production GO.
- Trigger rollback runbook if production impact is confirmed.
