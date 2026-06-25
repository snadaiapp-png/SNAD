# Incident #109 — Production Credential Reset Workflow

**Status:** CONTAINED — OWNER ACTION PENDING
**Severity:** P0 — CRITICAL
**Date:** 2026-06-25
**Last Updated:** 2026-06-25T21:20:00Z

---

## Summary

A production credential reset workflow (`.github/workflows/reset-admin-password.yml`) was introduced on `main` via PRs #104 and #106. The workflow accepted passwords through `workflow_dispatch` inputs, accessed Production environment secrets, connected directly to production PostgreSQL, and modified administrative user credentials.

## Timeline

| Time (UTC) | Event |
|------------|-------|
| 2026-06-25T18:11:01Z | Run 28190772962 dispatched (FAILED — URL parse error) |
| 2026-06-25T18:17:56Z | Run 28191175591 dispatched (SUCCESS — DB mutation confirmed) |
| 2026-06-25T19:00:00Z | Incident detected (EXEC-PROMPT-010R8) |
| 2026-06-25T19:05:00Z | Workflow disabled via GitHub API (`disabled_manually`) |
| 2026-06-25T19:30:00Z | Workflow removed via PR #110 (Merge SHA: 2daafc5) |
| 2026-06-25T20:00:00Z | Scanner implemented (PR #110) |
| 2026-06-25T20:30:00Z | Scanner structurally hardened + CI enforcement (PR #111, Merge SHA: 866b2c1) |
| 2026-06-25T20:35:00Z | Incident documentation created (PR #112, Merge SHA: 8b0222b) |
| 2026-06-25T20:38:00Z | Branch disposition corrected + SECURITY HOLD (PR #113, Merge SHA: 42d42e6) |

## Containment Status

| Action | Status | Evidence |
|--------|--------|----------|
| Workflow disabled | ✅ COMPLETE | GitHub API: state=disabled_manually |
| Workflow removed | ✅ COMPLETE | PR #110, Merge SHA: 2daafc5 |
| Scanner implementation | ✅ COMPLETE | scripts/ci/check_workflow_security.py (structural YAML) |
| Scanner CI enforcement | ✅ COMPLETE | security-baseline.yml → workflow-security-policy job (PR #111) |
| Scanner tests | ✅ COMPLETE | 15/15 pass, 6 fixtures |
| Incident documentation | ✅ COMPLETE | 4 security docs (PR #112) |
| Branch disposition | ✅ COMPLETE | 0 deletion-eligible, fix/reset-admin-password-v2 SECURITY HOLD (PR #113) |
| Credential rotation | ⏳ OWNER ACTION REQUIRED | See CREDENTIAL-ROTATION-EVIDENCE.md |
| Incident validation | ⏳ PENDING ROTATION | — |
| Owner closure acknowledgment | ⏳ PENDING | — |

## Execution History

| Run ID | Conclusion | DB Mutation | Notes |
|--------|------------|-------------|-------|
| 28190772962 | failure | No | URL parse error — DATABASE_URL format mismatch |
| 28191175591 | success | YES | Password reset confirmed — admin credential changed |

## Evidence Classification

- **Workflow executed:** YES
- **Production environment accessed:** YES
- **Render API called:** YES
- **Production DB connection attempted:** YES
- **Production DB connection succeeded:** YES
- **Password mutation succeeded:** YES
- **Session version changed:** YES (incremented)
- **Refresh tokens deleted:** YES
- **User enumeration occurred:** NO (admin found directly)
- **Raw secret exposure found:** POTENTIAL (unpinned packages installed while secrets available)
- **Raw identity exposure found:** YES (user_id and tenant_id printed in logs — masked by GitHub `::add-mask::`)

## Required Owner Actions (Blocking Closure)

1. Rotate Render API credential (Render Dashboard → API Keys)
2. Rotate production database password (Supabase Dashboard → Database → Reset password)
3. Reset admin credential through approved mechanism (application login + password change, or break-glass per policy)
4. Revoke all admin sessions (verify session_version increment is sufficient)
5. Review Production environment approval settings
6. Review GitHub Actions access log for unauthorized dispatches
7. Review Render audit logs

## Incident Closure Criteria

Issue #109 may be closed when ALL are true:
- All required rotations complete with evidence ✅ PENDING
- Old credentials revoked ✅ PENDING
- Post-rotation validation passed ✅ PENDING
- Scanner required by CI ✅ COMPLETE
- No equivalent workflow exists ✅ COMPLETE (27/27 pass)
- Incident evidence documented ✅ COMPLETE
- Owner acknowledges closure ⏳ PENDING

## Incident State: CONTAINED — OWNER ACTION PENDING
