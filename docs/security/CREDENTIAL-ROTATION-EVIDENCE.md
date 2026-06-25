# Credential Rotation Evidence

**Status:** OWNER ACTION REQUIRED
**Date:** 2026-06-25

---

## Rotation Requirements

The unsafe workflow (Run 28191175591) had access to Production environment secrets while installing unpinned Python packages. All credentials accessible during the workflow run must be considered potentially exposed.

| Credential Category | Rotation Required | Rotation Status | Completed Timestamp | Actor | Old Credential Revoked | Post-Rotation Health |
|---------------------|-------------------|-----------------|---------------------|-------|----------------------|---------------------|
| Render API credential | YES | OWNER ACTION REQUIRED | — | — | — | — |
| Production database password | YES | OWNER ACTION REQUIRED | — | — | — | — |
| Administrative login credential | YES | OWNER ACTION REQUIRED | — | — | — | — |
| Administrative sessions | YES | COMPLETED (by workflow) | 2026-06-25T18:18:07Z | workflow | N/A (session_version incremented) | Backend UP |
| Refresh tokens | YES | COMPLETED (by workflow) | 2026-06-25T18:18:07Z | workflow | N/A (all deleted) | N/A |
| Production environment access config | REVIEW | OWNER ACTION REQUIRED | — | — | — | — |

## Approved Rotation Mechanisms

Do NOT use GitHub Actions workflows for credential rotation. Use:
1. Render Dashboard → Settings → API Keys (for Render API)
2. Supabase Dashboard → Database → Reset password (for DB)
3. Application login + password change (for admin credential)
4. Provider console (for any provider-specific credential)

## Evidence Classification

All evidence must be recorded without exposing credential values.
Use "COMPLETED" or "OWNER ACTION REQUIRED" status only.
