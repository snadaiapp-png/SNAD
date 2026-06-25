# Incident #109 — Production Credential Reset Workflow

**Status:** CONTAINED — OWNER ACTION PENDING
**Severity:** P0 — CRITICAL
**Date:** 2026-06-25

---

## Summary

A production credential reset workflow (`.github/workflows/reset-admin-password.yml`) was introduced on `main` via PRs #104 and #106. The workflow accepted passwords through `workflow_dispatch` inputs, accessed Production environment secrets, connected directly to production PostgreSQL, and modified administrative user credentials.

## Timeline

| Time (UTC) | Event |
|------------|-------|
| 2026-06-25T18:11:01Z | Run 28190772962 dispatched (FAILED — URL parse error) |
| 2026-06-25T18:17:56Z | Run 28191175591 dispatched (SUCCESS — DB mutation confirmed) |
| 2026-06-25T19:00:00Z | Incident detected (EXEC-PROMPT-010R8) |
| 2026-06-25T19:05:00Z | Workflow disabled via GitHub API |
| 2026-06-25T19:30:00Z | Workflow removed via PR #110 (Merge SHA: 2daafc5) |
| 2026-06-25T20:00:00Z | Scanner implemented (PR #110) |
| 2026-06-25T20:30:00Z | Scanner hardened + CI enforcement (PR #111, Merge SHA: 866b2c1) |

## Containment Actions

1. ✅ Workflow disabled via GitHub API (`disabled_manually`)
2. ✅ Workflow file deleted from repository (PR #110)
3. ✅ Security incident Issue #109 created
4. ✅ Workflow security scanner implemented (scripts/ci/check_workflow_security.py)
5. ✅ Scanner structurally hardened with YAML parser + 15 test scenarios
6. ✅ Scanner integrated into Security Baseline CI (PR #111)
7. ⏳ Credential rotation (OWNER ACTION REQUIRED)

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
- **Raw identity exposure found:** YES (user_id and tenant_id printed in logs — masked by GitHub)

## Required Owner Actions

1. Rotate Render API credential
2. Rotate production database password
3. Reset admin credential through approved application/provider mechanism
4. Revoke all admin sessions
5. Review Production environment approval settings
6. Review GitHub Actions access log
7. Review Render audit logs

## Approved Reset Mechanisms

Per EXEC-PROMPT-010R8 Section 9.3, only these mechanisms are approved:
- Application-supported password reset
- Identity-provider password reset
- Controlled administrative console
- Provider-supported secure database administration
- Documented break-glass procedure (requires 2-person approval)

## Incident Closure Criteria

Issue #109 may be closed when:
- All required rotations complete with evidence
- Post-rotation application health verified
- Scanner required by CI (✅ achieved via PR #111)
- No equivalent workflow exists (✅ verified)
- Owner acknowledges closure
