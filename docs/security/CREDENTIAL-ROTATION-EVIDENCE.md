# Credential Rotation Evidence

**Status:** OWNER ACTION REQUIRED  
**Last Updated:** 2026-07-03  
**Controlled Issues:** #109 and #173

## Non-secret evidence rule

No credential value, hash, connection string, recovery code, token fragment, or screenshot containing a secret may be committed. Evidence records only provider action identifiers, timestamps, actors, rejection results, deployment SHA and reviewer approval.

## Required rotations

| Control | Required | Status | Completion time | Actor | Old value rejected | Runtime health | Evidence reference |
|---|---:|---|---|---|---:|---:|---|
| Render API credential | YES | OWNER ACTION REQUIRED | — | — | — | — | — |
| Production database password | YES | OWNER ACTION REQUIRED | — | — | — | — | — |
| Administrative login credential | YES | OWNER ACTION REQUIRED | — | — | — | — | — |
| Administrative sessions | YES | COMPLETE | 2026-06-25T18:18:07Z | controlled workflow | N/A | PASS | Run 28191175591 |
| Refresh-token families | YES | COMPLETE | 2026-06-25T18:18:07Z | controlled workflow | N/A | N/A | Run 28191175591 |
| Resend API credential | YES | OWNER ACTION REQUIRED | — | — | — | — | — |
| Email-proxy bearer token | YES | OWNER ACTION REQUIRED | — | — | — | — | — |
| Verified email sender configuration | YES | OWNER ACTION REQUIRED | — | — | N/A | — | — |
| Production environment access review | YES | OWNER ACTION REQUIRED | — | — | N/A | N/A | — |
| Provider audit-log review | YES | OWNER ACTION REQUIRED | — | — | N/A | N/A | — |

## Approved execution locations

- Render credentials and environment settings: Render dashboard.
- Database password and recovery controls: production database provider dashboard.
- Resend credential and verified sender: Resend dashboard.
- Vercel runtime secret configuration: Vercel project environment settings.
- Administrator password: approved application or break-glass process.

GitHub Actions must not be used to rotate production credentials.

## Mandatory post-rotation verification

1. Previous Render credential is rejected.
2. Previous database password is rejected.
3. Previous administrator password is rejected.
4. Previously issued administrator sessions are rejected.
5. Previous Resend credential is rejected.
6. Previous email-proxy bearer token is rejected.
7. Backend health and readiness return success on the reviewed deployment SHA.
8. Frontend production deployment is healthy on the same release candidate.
9. Approved administrator login succeeds with the replacement credential.
10. Tenant binding and cross-tenant denial checks pass.
11. Password-recovery delivery succeeds through the approved sender.
12. Unauthorized email-proxy requests are rejected.
13. Provider logs show no unexplained use during the exposure window.

## Closure requirements

Issue #109 may close only after the Render, database and administrator controls above are complete and reviewed.

Issue #173 may close only after the Resend/email-proxy controls, rejection verification, reviewed deployment and authorized recovery-delivery evidence are complete.

Stage 07 remains fail-closed while either issue is open.
