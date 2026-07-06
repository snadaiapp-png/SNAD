# Health Workflow Failure — Missing Production Secrets

## Executive Summary

**Run ID**: 28759733288
**Workflow**: Executive Health Production Verification
**SHA**: 88beddedcf6bda1a621213f1bdcfb4be57fdc4c4 (FINAL_MAIN_SHA)
**Result**: FAILURE
**Failed Step**: "Validate production identity configuration"
**Failure Reason**: Missing required GitHub Secrets

## Failure Evidence

The workflow's first step validates that all required secrets are present
before any production API call. The validation failed because these secrets
are NOT configured in the repository:

```
::error::CONTROL_PLANE_TENANT_ID is required.
```

## Required Secrets (per health-production-verification.yml)

| Secret Name | Status | Purpose |
|-------------|--------|---------|
| `SANAD_ADMIN_EMAIL` | ❌ MISSING | Admin user email for login |
| `SANAD_ADMIN_PASSWORD` | ✅ Present (2026-07-05) | Admin user password |
| `CONTROL_PLANE_TENANT_ID` | ❌ MISSING | Admin's tenant UUID |
| `CONTROL_PLANE_NON_ADMIN_EMAIL` | ❌ MISSING | Identity B email |
| `CONTROL_PLANE_NON_ADMIN_PASSWORD` | ❌ MISSING | Identity B password |
| `CONTROL_PLANE_NON_ADMIN_TENANT_ID` | ❌ MISSING | Identity B tenant UUID |

## Existing Related Secrets

| Secret Name | Status | Likely Maps To |
|-------------|--------|----------------|
| `SANAD_ADMIN_PASSWORD` | ✅ Present | `CONTROL_PLANE_ADMIN_PASSWORD` |
| `IDENTITY_B_EMAIL` | ✅ Present | `CONTROL_PLANE_NON_ADMIN_EMAIL` |
| `IDENTITY_B_PASSWORD` | ✅ Present | `CONTROL_PLANE_NON_ADMIN_PASSWORD` |
| `SANAD_CONTROL_PLANE_TENANT_ID` | ❌ NOT in secrets list (likely a Render env var, not GitHub secret) | `CONTROL_PLANE_TENANT_ID` |

## Root Cause

PR #244 introduced a new naming convention for secrets in
`health-production-verification.yml`:

```yaml
CONTROL_PLANE_ADMIN_EMAIL: ${{ secrets.SANAD_ADMIN_EMAIL }}
CONTROL_PLANE_ADMIN_PASSWORD: ${{ secrets.SANAD_ADMIN_PASSWORD }}
CONTROL_PLANE_TENANT_ID: ${{ secrets.CONTROL_PLANE_TENANT_ID }}
CONTROL_PLANE_NON_ADMIN_EMAIL: ${{ secrets.CONTROL_PLANE_NON_ADMIN_EMAIL }}
CONTROL_PLANE_NON_ADMIN_PASSWORD: ${{ secrets.CONTROL_PLANE_NON_ADMIN_PASSWORD }}
CONTROL_PLANE_NON_ADMIN_TENANT_ID: ${{ secrets.CONTROL_PLANE_NON_ADMIN_TENANT_ID }}
```

But the GitHub repository secrets were not updated to match. The repo has
the OLD names (`IDENTITY_B_*`) but the workflow expects the NEW names
(`CONTROL_PLANE_NON_ADMIN_*`).

## Required Operator Action

The operator must add these 4 missing GitHub Secrets. Their values can be
obtained from:

1. **SANAD_ADMIN_EMAIL**: The admin email used in production login
   (likely stored in Render env var `BOOTSTRAP_ADMIN_EMAIL` or similar)

2. **CONTROL_PLANE_TENANT_ID**: The production tenant UUID
   (likely stored in Render env var `SANAD_CONTROL_PLANE_TENANT_ID`)

3. **CONTROL_PLANE_NON_ADMIN_EMAIL**: Same as existing `IDENTITY_B_EMAIL`

4. **CONTROL_PLANE_NON_ADMIN_PASSWORD**: Same as existing `IDENTITY_B_PASSWORD`

5. **CONTROL_PLANE_NON_ADMIN_TENANT_ID**: Identity B's tenant UUID
   (different from admin's tenant — must be queried from production DB)

### How to add the secrets

```bash
# Use gh CLI to set secrets (values must be obtained from Render dashboard
# or production database):

# 1. SANAD_ADMIN_EMAIL (from Render env or admin bootstrap config)
gh secret set SANAD_ADMIN_EMAIL --repo snadaiapp-png/SNAD --body "admin@sanad.local"

# 2. CONTROL_PLANE_TENANT_ID (from Render env SANAD_CONTROL_PLANE_TENANT_ID)
gh secret set CONTROL_PLANE_TENANT_ID --repo snadaiapp-png/SNAD --body "<uuid>"

# 3. CONTROL_PLANE_NON_ADMIN_EMAIL (alias for IDENTITY_B_EMAIL)
gh secret set CONTROL_PLANE_NON_ADMIN_EMAIL --repo snadaiapp-png/SNAD --body "<identity-b-email>"

# 4. CONTROL_PLANE_NON_ADMIN_PASSWORD (alias for IDENTITY_B_PASSWORD)
gh secret set CONTROL_PLANE_NON_ADMIN_PASSWORD --repo snadaiapp-png/SNAD --body "<identity-b-password>"

# 5. CONTROL_PLANE_NON_ADMIN_TENANT_ID (query from production DB)
gh secret set CONTROL_PLANE_NON_ADMIN_TENANT_ID --repo snadaiapp-png/SNAD --body "<uuid>"
```

### Alternative: Modify the workflow to use existing secret names

If the operator prefers to keep the existing secret names, modify
`.github/workflows/health-production-verification.yml` to use:

```yaml
CONTROL_PLANE_ADMIN_EMAIL: ${{ secrets.SANAD_ADMIN_EMAIL }}
CONTROL_PLANE_ADMIN_PASSWORD: ${{ secrets.SANAD_ADMIN_PASSWORD }}
CONTROL_PLANE_TENANT_ID: ${{ secrets.SANAD_CONTROL_PLANE_TENANT_ID }}
CONTROL_PLANE_NON_ADMIN_EMAIL: ${{ secrets.IDENTITY_B_EMAIL }}
CONTROL_PLANE_NON_ADMIN_PASSWORD: ${{ secrets.IDENTITY_B_PASSWORD }}
CONTROL_PLANE_NON_ADMIN_TENANT_ID: ${{ secrets.IDENTITY_B_TENANT_ID }}
```

This requires a new PR to update the workflow file.

## Decision

```
Execution Status: BLOCKED
Failed Gate: §13 — Executive Health Production Verification
Failed Step: Validate production identity configuration
Failure Reason: Missing GitHub Secrets (5 of 6 required)
PR Head SHA: 88beddedcf6bda1a621213f1bdcfb4be57fdc4c4
Merge SHA: 88beddedcf6bda1a621213f1bdcfb4be57fdc4c4
Final Main SHA: 88beddedcf6bda1a621213f1bdcfb4be57fdc4c4
Workflow Run ID: 28759733288
Workflow Conclusion: failure
Failure Evidence: ::error::CONTROL_PLANE_TENANT_ID is required.
                 Process completed with exit code 1.
Rollback Triggered: N/A (no production changes; workflow failed before any API call)
Rollback Result: N/A
Security Impact: None (no credentials exposed; workflow validates before use)
Corrective Action: Add the 5 missing GitHub Secrets, then re-run the workflow:
                   gh workflow run health-production-verification.yml \
                     --repo snadaiapp-png/SNAD --ref main
Closure Status: OPEN
Final Decision: GO SUSPENDED
```
