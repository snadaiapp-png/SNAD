# SANAD Rollback Drill Plan
## EXEC-PROMPT-010R2 Section 11

---

## 1. Objective

Validate that the SANAD staging environment can be safely rolled back
to a previous known-good deployment without data loss, authentication
regression, or tenant isolation failure.

## 2. Scope

**In scope:**
- Application-level rollback (deploy previous SHA)
- Health and smoke checks
- Authentication verification
- Database schema compatibility verification

**Out of scope:**
- Database-destructive rollback (NO Flyway migration reversal)
- Production environment (staging only)
- Tenant data deletion

## 3. Environment

| Parameter | Value |
|-----------|-------|
| Target | Staging environment (NOT production) |
| Backend | Render staging service (to be provisioned) |
| Frontend | Vercel staging preview (to be provisioned) |
| Database | Staging PostgreSQL (separate from production) |

**BLOCKER:** Staging environment is NOT currently provisioned. This
rollback drill plan is documented but cannot be executed until staging
is provisioned.

**Status: BLOCKED BY PROVIDER ACCESS** — staging environment required.

## 4. Required Sequence

1. Record current staging deployment SHA.
2. Deploy an approved reversible test release.
3. Execute health and smoke checks.
4. Trigger rollback to the previous known-good SHA.
5. Verify Backend health.
6. Verify Frontend health (when applicable).
7. Verify database schema compatibility.
8. Verify authentication.
9. Verify no tenant data loss.
10. Record elapsed recovery time.

## 5. Evidence to Record

- Starting SHA
- Test-release SHA
- Rollback SHA
- Deployment IDs (Render + Vercel)
- Timestamps (start, deploy, rollback, verify)
- Rollback duration
- Health results
- Smoke results
- Authentication results
- Database results
- Residual risk

## 6. Constraints

- **No database-destructive rollback.** Do NOT reverse Flyway migrations
  by editing or deleting historical migration files.
- When schema rollback is not backward-compatible:
  - Use application rollback with forward-compatible database schema.
  - Document the limitation.

## 7. Pass/Fail Criteria

**PASS:**
- Rollback completes within 10 minutes
- Backend health UP after rollback
- Frontend HTTP 200 after rollback
- Authentication works (login + /me + logout)
- No tenant data loss
- Database schema compatible (Flyway validate passes)

**FAIL:**
- Rollback exceeds 10 minutes
- Backend health DOWN after rollback
- Authentication broken
- Tenant data loss
- Schema incompatible

## 8. Execution Blocker

**Status: BLOCKED BY PROVIDER ACCESS**

The rollback drill cannot be executed because:
1. No staging environment is provisioned
2. No reversible test release has been approved
3. No access to Render deployment history for staging

**Required owner actions before execution:**
1. Provision staging environment
2. Approve a reversible test release (e.g., a minor version bump)
3. Provide access to Render deployment management for staging
4. Approve rollback drill execution

## 9. Report Template

See `SANAD-ROLLBACK-DRILL-REPORT.md` for the report template. The report
will be populated after the rollback drill is executed.
