# EXEC-PROMPT-009A — Production Workflow Contract Correction

**Program**: SANAD-FDP-001
**Date**: 2026-06-24
**Starting main SHA**: 47ca7d6b84cf3d9876fd2b89f240250745673c7e
**Branch**: fix/EXEC-PROMPT-009A-production-workflow-contract

---

## Authentication Contract Discovery

Source of truth: `AuthController.java`, `AuthResponse.java`, `RefreshRequest.java`, `AuthService.java`, `AuthApiIntegrationTest.java`

### Login

| Field | Value |
|-------|-------|
| Endpoint | `POST /api/v1/auth/login` |
| Request fields | `email` (required), `password` (required), `tenantId` (optional UUID) |
| Response fields | `accessToken`, `expiresAt`, `user.id`, `user.tenantId`, `user.email`, `user.displayName`, `user.status` |
| Access-token transport | JSON body `accessToken` field |
| Refresh-token transport | `X-SANAD-Refresh-Token` response header (production); Set-Cookie in local/dev |
| Success status | HTTP 200 |
| Wrong credentials | HTTP 401 |
| Duplicate email across tenants | HTTP 409 with `tenantIds` array |
| Suspended user | HTTP 401 |

### Refresh

| Field | Value |
|-------|-------|
| Endpoint | `POST /api/v1/auth/refresh` |
| Request transport | `X-SANAD-Refresh-Token` header (production); JSON body `refreshToken` fallback in local/dev |
| Response | Same as login (new rotated access + refresh tokens) |
| Success status | HTTP 200 |
| Replay detection | HTTP 401, entire refresh family revoked |
| Family revocation | HTTP 401 on all family members after replay |

### Logout

| Field | Value |
|-------|-------|
| Endpoint | `POST /api/v1/auth/logout` |
| Auth required | Yes (Bearer token) |
| Request body | None |
| Success status | HTTP 204 No Content |
| Post-logout refresh | HTTP 401 |

### /me

| Field | Value |
|-------|-------|
| Endpoint | `GET /api/v1/auth/me` |
| Auth required | Yes (Bearer token) |
| Response fields | `id`, `tenantId`, `email`, `displayName`, `status`, `credentialRotationRequired`, `memberships`, `roleGrants` |
| Success status | HTTP 200 |

### Cross-Tenant Rejection

| Scenario | Status |
|----------|--------|
| Identity A credentials against tenant B | HTTP 401 |
| Identity B credentials against tenant A | HTTP 401 |
| Authenticated endpoint tenant mismatch | HTTP 403 |

---

## Workflow Corrections Applied

### 1. Secret env mapping
- **Before**: Shell variable indirection `${!name:-}` without mapping GitHub Secrets to env
- **After**: Explicit `env:` block mapping each `${{ secrets.* }}` to the shell variable name

### 2. Scheduled execution removed
- **Before**: `schedule: cron: '0 7 * * 1'` (weekly)
- **After**: `workflow_dispatch` only — schedule will be added after first successful acceptance

### 3. Safe JSON construction
- **Before**: Direct shell interpolation `"{\"email\":\"${EMAIL}\",\"password\":\"${PASSWORD}\"}"`
- **After**: `jq -n --arg email "$EMAIL" --arg password "$PASSWORD" '{email:$email,password:$password}'`

### 4. Mask protected values before network operations
- All tenant IDs, emails, passwords masked with `::add-mask::` at step start
- Access tokens and refresh tokens masked immediately after extraction
- `set -x` never enabled

### 5. Tenant ID exact comparison
- **Before**: Checked tenant field is non-empty
- **After**: `[ "$returned_tenant_id" = "$TENANT_A_ID" ]` exact string comparison

### 6. Cross-tenant exact assertions
- **Before**: Accepted any non-200 as success
- **After**: `case "$status" in 401) ;; *) exit 1 ;; esac` — only 401 accepted

### 7. Refresh transport aligned with backend contract
- **Before**: Used JSON body `{"refreshToken":"..."}`
- **After**: Uses `X-SANAD-Refresh-Token` header for both request and response

### 8. Logout status aligned with backend contract
- **Before**: Expected HTTP 200
- **After**: Expected HTTP 204 No Content

### 9. Replay rejection exact status
- **Before**: Accepted any non-200
- **After**: Expects exactly HTTP 401

### 10. Family revocation exact status
- **Before**: Accepted any non-200
- **After**: Expects exactly HTTP 401 on rotated token after replay

---

## Backup Configuration Reconciliation

### Canonical secret model
- **PRODUCTION_DATABASE_URL** — single JDBC URL, parsed at runtime

### Duplicate model removed
- DATABASE_HOST, DATABASE_PORT, DATABASE_NAME, DATABASE_USERNAME, DATABASE_PASSWORD — no longer required as separate secrets
- The backup-verify workflow now parses PRODUCTION_DATABASE_URL to extract connection parameters

---

## Workflow Static Validation

Created `scripts/validate-workflows.py` — validates:
1. All required secrets mapped to env
2. No secret values printed
3. No direct password interpolation in JSON
4. No continue-on-error on enforcement steps
5. No || true on security/acceptance steps
6. No schedule before initial production acceptance
7. Cross-tenant assertions require exact status codes
8. Tenant IDs compared exactly
9. Refresh transport matches backend contract (X-SANAD-Refresh-Token)
10. Logout status matches backend contract (204)

Result: VALIDATION PASSED

---

## Protected Values Status

| Secret Name | Status |
|-------------|--------|
| PRODUCTION_BASE_URL | MISSING — owner must configure |
| AUTH_SMOKE_TENANT_A_ID | MISSING — owner must configure |
| AUTH_SMOKE_TENANT_A_EMAIL | MISSING — owner must configure |
| AUTH_SMOKE_TENANT_A_PASSWORD | MISSING — owner must configure |
| AUTH_SMOKE_TENANT_B_ID | MISSING — owner must configure |
| AUTH_SMOKE_TENANT_B_EMAIL | MISSING — owner must configure |
| AUTH_SMOKE_TENANT_B_PASSWORD | MISSING — owner must configure |
| PRODUCTION_DATABASE_URL | PRESENT |

---

## Issues Status

- Issue #59: OPEN — awaiting protected test identities
- Issue #53: OPEN — depends on #59
- Issue #29: OPEN — depends on #59, #53, backup, owner approval
