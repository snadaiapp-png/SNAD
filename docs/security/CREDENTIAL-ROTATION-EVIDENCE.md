# Credential Rotation Evidence

**Status:** OWNER ACTION REQUIRED
**Last Updated:** 2026-06-25T21:20:00Z

---

## Rotation Requirements

The unsafe workflow (Run 28191175591) had access to Production environment secrets while installing unpinned Python packages. All credentials accessible during the workflow run must be considered potentially exposed.

| Credential Category | Rotation Required | Rotation Status | Completed Timestamp | Actor Role | Old Credential Revoked | Post-Rotation Health | Evidence Reference |
|---------------------|-------------------|-----------------|---------------------|------------|----------------------|---------------------|-------------------|
| Render API credential | YES | OWNER ACTION REQUIRED | — | — | — | — | — |
| Production database password | YES | OWNER ACTION REQUIRED | — | — | — | — | — |
| Administrative login credential | YES | OWNER ACTION REQUIRED | — | — | — | — | — |
| Administrative sessions | YES | COMPLETED (by workflow) | 2026-06-25T18:18:07Z | workflow | N/A (session_version incremented) | Backend UP | Run 28191175591 logs |
| Refresh tokens | YES | COMPLETED (by workflow) | 2026-06-25T18:18:07Z | workflow | N/A (all deleted) | N/A | Run 28191175591 logs |
| Production environment access config | REVIEW | OWNER ACTION REQUIRED | — | — | — | — | — |

## Approved Rotation Mechanisms

Do NOT use GitHub Actions workflows for credential rotation. Use:

1. **Render API:** Render Dashboard → Account or Workspace → API Keys
2. **Production Database:** Supabase Dashboard → Database → Reset password
3. **Admin Credential:** Application login + password change (or break-glass per docs/security/BREAK-GLASS-ACCESS-POLICY.md)
4. **Sessions:** Application-supported session revocation (already completed via session_version increment)
5. **Refresh Tokens:** Application-supported token-family invalidation (already completed via DELETE FROM refresh_tokens)

## Post-Rotation Verification (To Be Performed After Rotation)

After owner confirms rotation:
1. Backend health = UP (verify via /actuator/health)
2. Frontend health = UP (verify via HTTP 200)
3. Approved admin login = SUCCESS (verify via /api/v1/auth/login with new credential)
4. Old admin credential = REJECTED (verify old password fails)
5. New session creation = SUCCESS
6. Tenant binding = CORRECT (verify via /api/v1/auth/me)
7. No cross-tenant access = VERIFIED (verify tenant isolation tests pass)

## Closure Criteria for Issue #109

Issue #109 may be closed when:
- All required rotations complete with evidence: PENDING
- Old credentials revoked: PENDING
- Post-rotation validation passed: PENDING
- Scanner required CI passed: COMPLETE ✅
- Equivalent workflows = 0: COMPLETE ✅ (27/27 pass)
- Owner acknowledges closure: PENDING

## Evidence Classification

All evidence must be recorded without exposing credential values.
Use "COMPLETED" or "OWNER ACTION REQUIRED" status only.
No credential values, hashes, or connection strings are recorded in this document.
