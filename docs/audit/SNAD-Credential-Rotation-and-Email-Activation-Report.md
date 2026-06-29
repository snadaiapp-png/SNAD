# SNAD Credential Rotation & Email Activation Report

## Executive Summary

**المهمة:** Credential Rotation & Production Email Activation  
**Issue:** #173  
**Security Merge SHA:** `839ab054f37e210fa11b0e6bbd7358f6165b9f03`  
**Current main baseline:** `76f3362bda2c7199aae9274222ae118ac7bbc9b6`  
**Date:** 2026-06-29  

**النتيجة العامة:** PARTIAL — تم تنظيف المصدر بالكامل وإيقاف Workflow القديم. تدوير الأسرار يتطلب إجراء المالك على المنصات الخارجية. البريد الإنتاجي محظور حتى اكتمال التدوير.

---

## Credential Rotation Matrix

| Credential Identifier | Platform | Revoked | Replaced | Old Value Rejected | Evidence Type |
|-----------------------|----------|:------:|:-------:|:------------------:|---------------|
| RENDER_KEY_A | Render | PENDING | NOT REQUIRED | NOT VERIFIED | Owner must revoke from Dashboard |
| RENDER_KEY_B | Render | PENDING | NOT REQUIRED | NOT VERIFIED | Owner must revoke from Dashboard |
| RENDER_KEY_C | Render | PENDING | NOT REQUIRED | NOT VERIFIED | Owner must revoke from Dashboard |
| SUPABASE_DB_PASSWORD | Supabase | PENDING | PENDING | NOT VERIFIED | Owner must rotate from Dashboard |
| RESEND_API_KEY | Resend | PENDING | PENDING | NOT VERIFIED | Owner must revoke and create new key |
| EMAIL_PROXY_BEARER_TOKEN | Vercel + Render | N/A | PENDING | NOT VERIFIED | New token generated (not recorded here) |
| BREVO_SMTP_KEY | Brevo | DONE | N/A | VERIFIED | Already invalid (confirmed via API) |
| Any derived environment copies | All | PENDING | PENDING | NOT VERIFIED | Owner must audit all platforms |

### Notes:
- A new `EMAIL_PROXY_BEARER_TOKEN` was generated using `openssl rand -hex 32` (256-bit entropy). The value is NOT recorded in this report. The owner must store it directly in Vercel and Render.
- `RENDER_KEY_A/B/C` are no longer needed by any automation. They should be revoked, not replaced.
- `SUPABASE_DB_PASSWORD` must be rotated from Supabase Dashboard, then updated in Render Environment.
- `RESEND_API_KEY` must be revoked from Resend Dashboard and a new key created.

---

## Runtime Configuration Requirements

The following environment variables MUST be set by the owner on each platform. Values are NOT recorded in this report.

### Vercel (Production Environment)

| Variable Name | Platform | Environment | Configured | Action Required |
|---------------|----------|------------|:----------:|-----------------|
| `RESEND_API_KEY` | Vercel | Production | PENDING | Owner: create new key in Resend, store in Vercel |
| `EMAIL_PROXY_BEARER_TOKEN` | Vercel | Production | PENDING | Owner: use generated token, store in Vercel |
| `EMAIL_PROXY_FROM` | Vercel | Production | PENDING | Owner: set to `onboarding@resend.dev` or verified domain sender |

### Render (sanad-backend)

| Variable Name | Platform | Environment | Configured | Action Required |
|---------------|----------|------------|:----------:|-----------------|
| `DATABASE_URL` | Render | Production | PENDING | Owner: update with new Supabase password |
| `DATABASE_PASSWORD` | Render | Production | PENDING | Owner: set to new Supabase password |
| `SECURITY_NOTIFICATION_PROVIDER` | Render | Production | PENDING | Set to `http` |
| `SECURITY_NOTIFICATION_ENDPOINT` | Render | Production | PENDING | Set to `https://snad-app.vercel.app/api/email-proxy` |
| `SECURITY_NOTIFICATION_BEARER_TOKEN` | Render | Production | PENDING | Owner: use same generated token as Vercel |
| `SECURITY_NOTIFICATION_FROM` | Render | Production | PENDING | Set to `onboarding@resend.dev` |

---

## Source Tree Security

| Check | Tool | Result | Evidence |
|-------|------|--------|----------|
| Secrets scan (regex) | `grep -rn` comprehensive patterns | PASS | 0 matches in source files |
| route.ts fail-closed | Manual code review (12-point checklist) | PASS | Returns 503 when any env var missing |
| No hardcoded credentials | Manual code review | PASS | No defaults, no fallbacks |
| Sender spoofing prevention | Code review | PASS | `body.from` ignored, uses `EMAIL_PROXY_FROM` only |
| No response body logging | Code review | PASS | Only logs `{ status: number }` |
| Cache-Control: no-store | Code review | PASS | Present on all responses via `jsonResponse` |

**SOURCE TREE: CLEAN** ✅  
**HISTORICAL CREDENTIAL ROTATION: NOT COMPLETE** ❌

---

## Workflow Cleanup

| Workflow | Previous State | New State | Action |
|----------|---------------|-----------|--------|
| `nvd-feed-mirror-publisher.yml` | `on: workflow_dispatch` (dispatchable) | `on: {}` (fully deprecated) | ✅ FIXED in this branch |

---

## Local Validation Results

| Check | Command | Result | Evidence |
|-------|---------|--------|----------|
| Frontend lint | `npm run lint` | PASS | No errors |
| Frontend tests | `npm test` | PASS | 194/194 passed |
| Frontend build | `npm run build` | PASS | 5 routes compiled |
| Backend tests | `mvn test` | PASS | 434 tests, 0 failures, 11 skipped |
| Python tests | `pytest tests/` | PASS | 165/165 passed |
| Secrets scan | `grep -rn` (regex) | PASS | 0 matches |
| Git diff check | `git diff --check` | PASS | No whitespace errors |

### Skipped Tests (11)

| Test Class | Count | Reason | Blocking? |
|-----------|-------|--------|-----------|
| `RefreshTokenConcurrencyPostgresTest` | 1 | `@Testcontainers(disabledWithoutDocker = true)` | No |
| `ProductionProfileTest` | 10 | `@DisabledIf("dockerNotAvailable")` | No |

---

## Deployment Evidence

| Platform | Status | Notes |
|----------|--------|-------|
| Vercel Frontend | ✅ LIVE | HTTP 200 on `https://snad-app.vercel.app` |
| Vercel Email Proxy | ✅ ACTIVE (503) | Fail-closed — env vars not yet configured |
| Render Backend | ⏠ SLEEPING | Free tier — wakes on request |
| Email Smoke Test | ❌ NOT AUTHORIZED | Credentials not yet rotated |

---

## Smoke Test Conditions

The Email Smoke Test is **NOT AUTHORIZED** until ALL of the following are verified:

```
[ ] All exposed Render keys revoked (RENDER_KEY_A/B/C)
[ ] Supabase password rotated and old password rejected
[ ] Old Resend key revoked and rejected
[ ] New Resend key stored in Vercel (RESEND_API_KEY)
[ ] New bearer token stored in Vercel (EMAIL_PROXY_BEARER_TOKEN)
[ ] New bearer token stored in Render (SECURITY_NOTIFICATION_BEARER_TOKEN)
[ ] EMAIL_PROXY_FROM configured in Vercel
[ ] Vercel redeployed successfully
[ ] Render backend operational with new credentials
[ ] No secret appears in logs
```

Current status: **0/10 conditions met**

---

## Old Credential Rejection Verification

After rotation, the owner must verify each old credential is rejected:

| Credential | Verification Method | Expected Result | Status |
|-----------|-------------------|-----------------|--------|
| Render API Keys | Attempt API call with old key | HTTP 401 Unauthorized | PENDING |
| Supabase Password | Attempt DB connection with old password | Authentication failed | PENDING |
| Resend API Key | Attempt API call with old key | HTTP 401 Unauthorized | PENDING |
| Email Proxy Bearer | Send request with old token | HTTP 401 Unauthorized | PENDING |
| Brevo SMTP Key | Already verified | REJECTED (535 Auth Failed) | ✅ DONE |

---

## Remaining Risks

| # | Risk | Severity | Mitigation |
|---|------|----------|------------|
| 1 | Render API keys still active | P0 | Owner: revoke from Render Dashboard |
| 2 | Supabase password not rotated | P0 | Owner: rotate from Supabase Dashboard |
| 3 | Resend API key not rotated | P0 | Owner: revoke and create new from Resend Dashboard |
| 4 | Render deploy instability | P1 | Env vars wiped on deploy; consider Starter plan |
| 5 | Stale branches (30+) | P3 | Delete merged branches in separate operation |
| 6 | Dual lockfile warning | P4 | Remove root `package-lock.json` if unused |
| 7 | Suspicious activity in provider logs | P2 | Owner: review Resend/Brevo delivery logs since leak date |

---

## Issue #173 Exit Criteria

```
[ ] All exposed Render keys revoked
[ ] Supabase password rotated
[ ] Old Supabase password rejected
[ ] Old Resend key revoked
[ ] Old Resend key rejected
[ ] New Resend key stored in Vercel
[ ] New bearer token stored in Vercel
[ ] New bearer token stored in Render
[ ] EMAIL_PROXY_FROM configured and verified
[ ] Production redeployed
[ ] Vercel validation passed
[ ] Render validation passed
[ ] Password recovery email delivered
[ ] Invalid bearer token rejected
[ ] No secrets found in source
[ ] No secrets found in logs
[ ] Incident evidence recorded safely
```

Current status: **2/17 items complete** (source tree clean, workflow deprecated)

---

## Owner Action Checklist

The following actions MUST be performed by the account owner on each platform. No values are recorded in this report.

### Render Dashboard
1. Go to: `https://dashboard.render.com/account/api-keys`
2. Revoke ALL existing API keys (3 exposed keys)
3. Do NOT create a new key unless needed for automation

### Supabase Dashboard
1. Go to: `https://supabase.com/dashboard` → Project → Settings → Database
2. Click "Reset Database Password"
3. Copy the new password (do NOT share it)
4. Go to Render → sanad-backend → Environment
5. Update `DATABASE_PASSWORD` with the new password
6. Update `DATABASE_URL` with the new password embedded

### Resend Dashboard
1. Go to: `https://resend.com/api-keys`
2. Revoke the exposed key
3. Create a new API key named "SNAD Production"
4. Copy the new key (do NOT share it)
5. Go to Vercel → snad-app → Settings → Environment Variables
6. Add `RESEND_API_KEY` = new key (Production only)

### Vercel Dashboard
1. Go to: `https://vercel.com/snadaiapp-png/snad-app/settings/environment-variables`
2. Add `RESEND_API_KEY` = new Resend key
3. Add `EMAIL_PROXY_BEARER_TOKEN` = generated token (contact executor for value)
4. Add `EMAIL_PROXY_FROM` = `onboarding@resend.dev`
5. Redeploy (Deployments → latest → Redeploy)

### Render Dashboard (sanad-backend)
1. Go to: `https://dashboard.render.com/web/srv-d8ragqkm0tmc73bviqq0/environment`
2. Update `DATABASE_PASSWORD` = new Supabase password
3. Update `DATABASE_URL` = new connection string
4. Set `SECURITY_NOTIFICATION_PROVIDER` = `http`
5. Set `SECURITY_NOTIFICATION_ENDPOINT` = `https://snad-app.vercel.app/api/email-proxy`
6. Set `SECURITY_NOTIFICATION_BEARER_TOKEN` = same token as Vercel
7. Set `SECURITY_NOTIFICATION_FROM` = `onboarding@resend.dev`
8. Manual Deploy

---

## Final Recommendation

```
RECOMMEND CONDITIONAL CLOSURE
```

**Justification:**
- ✅ Source tree is clean — no secrets in code
- ✅ Email proxy is fail-closed — returns 503 without configuration
- ✅ All local tests pass (194 + 434 + 165)
- ✅ nvd-feed-mirror-publisher.yml fully deprecated
- ✅ Security hardening from PR #172 is on main
- ❌ Credential rotation NOT complete — requires owner action on Render, Supabase, Resend, Vercel
- ❌ Email smoke test NOT authorized — credentials not rotated
- ❌ Issue #173 cannot be closed until all 17 exit criteria are met

**The code is ready. The credentials are not.** Issue #173 remains open until the owner completes the rotation checklist above.
