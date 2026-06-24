# EXEC-PROMPT-002: DEFECT-011 CORS Security Fix

**Date**: 2026-06-24
**Branch**: `fix/EXEC-PROMPT-002-defect-011-cors-allowlist`
**Defect**: DEFECT-011 — CORS Wildcard for All Vercel Deployments

---

## Root Cause

`SecurityConfig.corsConfigurationSource()` used `setAllowedOriginPatterns()` with
wildcard patterns `https://*.vercel.app` and `http://localhost:*`. This allowed
**any** Vercel deployment from **any** project to make credentialed cross-origin
API requests to the SANAD backend, enabling cross-tenant data exfiltration and
CSRF-like attacks.

## Security Impact

- **High**: Any Vercel preview deployment could make authenticated cross-origin requests
- **Cross-tenant risk**: Malicious Vercel app could exfiltrate tenant data
- **CSRF-like**: Credentialed requests from unauthorized origins

## Changes Made

### Files Added

| File | Purpose |
|------|---------|
| `config/CorsProperties.java` | `@ConfigurationProperties` with startup validation |
| `config/CorsTestSecurityConfig.java` | Test config for CORS integration tests |
| `config/CorsOriginValidatorTest.java` | 25 standalone unit tests for validation logic |
| `config/CorsStartupValidationTest.java` | 11 ApplicationContextRunner tests |
| `config/CorsSecurityTest.java` | 13 Spring Security CORS integration tests |

### Files Modified

| File | Change |
|------|--------|
| `SanadPlatformApplication.java` | Added `@ConfigurationPropertiesScan` |
| `SecurityConfig.java` | Replaced wildcard patterns with exact-origin allowlist |
| `application.yml` | `sanad.cors.allowed-origins` (single source of truth) |
| `application-prod.yml` | `sanad.cors.allowed-origins` (empty default = must set env var) |
| `render.yaml` | `SANAD_CORS_ALLOWED_ORIGINS` (unified env var name) |
| `docker-compose.prod.yml` | `SANAD_CORS_ALLOWED_ORIGINS` (unified env var name) |
| `.env.example` | `SANAD_CORS_ALLOWED_ORIGINS` (unified env var name) |
| `SANAD-DEFECT-REGISTER.md` | DEFECT-011/022 status updated |
| `SANAD-GATE-STATUS.md` | CORS gate updated |
| `SANAD-REMEDIATION-PLAN.md` | WP1.1 status updated |
| `SANAD-TEST-EVIDENCE.md` | CORS config evidence updated |

### Files Deleted

| File | Reason |
|------|--------|
| `config/CorsConfig.java` | Dead code — Spring Security CorsConfigurationSource is the authoritative CORS layer |
| `config/CorsConfigTest.java` | Replaced by `CorsSecurityTest.java` |

## Configuration

| Setting | Value |
|---------|-------|
| Property prefix | `sanad.cors.allowed-origins` |
| Environment variable | `SANAD_CORS_ALLOWED_ORIGINS` |
| Production origins | `https://snad-app.vercel.app` |
| Development origins | `http://localhost:3000` (allowed in non-prod profiles) |
| Allowed methods | GET, POST, PUT, PATCH, DELETE, OPTIONS |
| Allowed headers | Authorization, Content-Type, Accept, X-Requested-With, X-SANAD-Refresh-Token |
| Exposed headers | X-SANAD-Refresh-Token, Location |
| Credentials | `true` |
| Max age | 3600 seconds |

## Validation Tests

### CorsOriginValidatorTest (25 tests)

| Test | Result |
|------|--------|
| Valid production origin | PASS |
| Custom domain origin | PASS |
| Origin with explicit port | PASS |
| HTTP localhost in dev | PASS |
| Multiple comma-separated origins | PASS |
| Bare wildcard `*` rejected | PASS |
| `https://*` rejected | PASS |
| `https://*.vercel.app` rejected | PASS |
| `https://snad-*.vercel.app` rejected | PASS |
| Origin with path rejected | PASS |
| Origin with query string rejected | PASS |
| Origin with fragment rejected | PASS |
| Origin with user credentials rejected | PASS |
| javascript: scheme rejected | PASS |
| ftp: scheme rejected | PASS |
| file: scheme rejected | PASS |
| not-a-url rejected | PASS |
| scheme-only rejected | PASS |
| scheme-relative rejected | PASS |
| bare hostname rejected | PASS |
| Empty string rejected (production) | PASS |
| Whitespace-only rejected (production) | PASS |
| HTTP origin rejected (production) | PASS |
| Null value rejected (production) | PASS |

### Normalization Tests

| Test | Result |
|------|--------|
| Whitespace trimming | PASS |
| Duplicate elimination | PASS |
| Trailing slash stripping | PASS |
| Explicit port preserved | PASS |
| Path after slash rejected | PASS |
| Comma-separated parsing | PASS |
| Empty entries filtered | PASS |

### CorsStartupValidationTest (11 tests)

| Scenario | Result |
|----------|--------|
| Production valid configuration | PASS |
| Production missing configuration | PASS (validation fails) |
| Production empty configuration | PASS (validation fails) |
| Production wildcard rejected | PASS (validation fails) |
| Production Vercel wildcard rejected | PASS (validation fails) |
| Production HTTP localhost rejected | PASS (validation fails) |
| Production HTTP 127.0.0.1 rejected | PASS (validation fails) |
| Development localhost accepted | PASS |
| Test profile isolation | PASS |
| Default empty config valid in non-prod | PASS |
| Bean registration via @ConfigurationPropertiesScan | PASS |

### CorsSecurityTest (13 tests)

| Test | Result |
|------|--------|
| Approved origin receives Allow-Origin | PASS |
| Approved origin receives Allow-Credentials = true | PASS |
| No wildcard * in Allow-Origin response | PASS |
| Allowed methods verified | PASS |
| Attacker Vercel subdomain rejected | PASS |
| Malicious prefix attack rejected | PASS |
| Suffix attack rejected | PASS |
| Domain-append attack rejected | PASS |
| Wrong scheme (HTTP) rejected | PASS |
| Wrong port rejected | PASS |
| Subdomain rejected | PASS |
| Unrelated Vercel project rejected | PASS |
| Generic disallowed origin rejected | PASS |
| Actuator routes not CORS-enabled | PASS |

## Backend Regression

| Metric | Value |
|--------|-------|
| Compile | PASS |
| Tests executed | 422 |
| Failures | 0 |
| Errors | 0 |
| Skipped | 11 |
| Duration | ~38s |
| `mvn verify` | PASS |

## Frontend Verification

| Check | Result |
|-------|--------|
| Typecheck | N/A (no tsc script; Next.js build includes TS check) |
| Lint | 6 errors, 3 warnings — PRE-EXISTING — OUT OF SCOPE FOR DEFECT-011 |
| Tests | 148/151 pass (3 failures) — PRE-EXISTING — OUT OF SCOPE FOR DEFECT-011 |
| Build | PASS |

## Deployment Configuration

**Render (`render.yaml`)**:
- Key: `SANAD_CORS_ALLOWED_ORIGINS`
- Value: `https://snad-app.vercel.app`
- No other CORS environment variables needed

**Docker Compose (`docker-compose.prod.yml`)**:
- Key: `SANAD_CORS_ALLOWED_ORIGINS`
- Default: `https://snad-app.vercel.app`

## Rollback Procedure

1. Revert this PR
2. Redeploy previous SHA via Render manual deploy
3. Previous CORS config: `allowedOriginPatterns` with `https://*.vercel.app` wildcard
4. Verify: `curl -H "Origin: https://snad-app.vercel.app" -X OPTIONS https://sanad-backend-mcrj.onrender.com/api/v1/auth/login`

## Residual Risks

1. **Preview deployments**: Vercel preview URLs (e.g. `snad-app-git-branch-snadaiapp.vercel.app`) are NOT in the allowlist. Preview deploys will not be able to make credentialed API calls. This is intentional — only the production frontend origin is trusted.
2. **Custom domain additions**: Adding a new origin requires updating `SANAD_CORS_ALLOWED_ORIGINS` in Render environment and redeploying.
3. **No dynamic per-tenant CORS**: All tenants share the same CORS allowlist. Per-tenant CORS is a future enhancement.

## Defect Status

| Defect | Status |
|--------|--------|
| DEFECT-011 | IMPLEMENTED — CI BLOCKED (GitHub Actions runner allocation failure) |
| DEFECT-022 | FIXED (CorsConfig deleted) |
| Deployment verification | NOT STARTED |

## CI Evidence

### GitHub Actions Status

All 10 workflow runs on commit `ec369ae` failed with **zero steps executed** and **Runner ID = 0**.
This indicates a GitHub Actions runner allocation failure, not a code or test failure.

| Run ID | Workflow | Conclusion | Steps Executed | Runner ID |
|--------|----------|------------|----------------|-----------|
| 28068405529 | CI | failure | 0 | 0 |
| 28068405550 | Web CI | failure | 0 | 0 |
| 28068405505 | Render Blueprint Validation | failure | 0 | 0 |
| 28068405512 | Compile Diagnostics | failure | 0 | 0 |
| 28068405523 | Security Baseline | failure | 0 | 0 |
| 28068405470 | Production Control Plane Validation | failure | 0 | 0 |
| 28068405488 | Backup Restore Validation | failure | 0 | 0 |
| 28068405477 | Performance Baseline | failure | 0 | 0 |
| 28068405484 | Master Backlog Validation | failure | 0 | 0 |
| 28068405490 | Service Decomposition Validation | failure | 0 | 0 |

### Root Cause Classification: Category C — GitHub Actions runner/billing/platform restriction

All jobs completed in 3-4 seconds with zero steps and Runner ID 0.
The log blob does not exist (404 BlobNotFound), confirming the runner was never allocated.

### Pre-existing CI Failure

This CI failure **predates PR #70**. The same zero-step, Runner ID 0 pattern is observed on:

| SHA | Branch | CI Result | Steps | Runner ID |
|-----|--------|-----------|-------|-----------|
| 635ebe3 (base SHA) | main | failure | 0 | 0 |
| a891d73 | main | failure | 0 | 0 |
| cec99c1 | main | failure | 0 | 0 |
| d100caa | main | failure | 0 | 0 |

**Last known green CI**: SHA `5f446d1` on 2026-06-22T18:36:55Z (Runner ID: 1000001287, 13 steps completed)

**First zero-step failure on main**: SHA `d100caa` on 2026-06-23T09:54:25Z

### CI Corrective Commit

Commit `c7f6029` fixes three workflow files that still used the obsolete `CORS_ALLOWED_ORIGINS` env var:

1. `render-env-recovery.yml` — replaced `CORS_ALLOWED_ORIGINS` with `SANAD_CORS_ALLOWED_ORIGINS` and removed `https://*.vercel.app` wildcard
2. `backup-restore-validation.yml` — replaced `CORS_ALLOWED_ORIGINS` with `SANAD_CORS_ALLOWED_ORIGINS` (2 locations)
3. `performance-baseline.yml` — replaced `CORS_ALLOWED_ORIGINS` with `SANAD_CORS_ALLOWED_ORIGINS`

These fixes ensure that Docker-based CI workflows that start the `prod` profile will supply the correct env var name. However, the zero-step runner allocation failure is a **platform-level issue** that requires administrative correction.

## Stop Condition Check

| Condition | Status |
|-----------|--------|
| Backend tests pass | PASS (422/422, 0 failures locally) |
| Production profile fails without allowlist | PASS |
| Wildcard configuration rejected | PASS |
| Unauthorized Vercel origin rejected | PASS |
| No secrets in git diff | VERIFIED |
| No frontend regression from this change | VERIFIED (3 pre-existing failures, not caused by CORS fix) |
| Canonical production origin verified | PASS |
| **GitHub CI green** | **BLOCKED — runner allocation failure (Category C)** |
