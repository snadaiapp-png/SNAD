# SNAD Credential Rotation & Email Activation Report

## Executive Summary

```
Execution Result: PARTIAL
Code and workflow cleanup: COMPLETE
Credential rotation: NOT EXECUTED — OWNER ACTION REQUIRED
Runtime email configuration: NOT EXECUTED
Email smoke test: NOT AUTHORIZED
Issue #173: REMAINS OPEN
```

تم إنجاز الجزء البرمجي والتوثيقي المتاح للمنفذ.
تبقى إجراءات المالك الخارجية إلزامية.

---

## Credential Rotation Matrix

| Credential Identifier | Platform | Revoked | Replaced | Old Value Rejected | Evidence |
|-----------------------|----------|:------:|:-------:|:------------------:|----------|
| RENDER_KEY_A | Render | PENDING | NOT REQUIRED | NOT VERIFIED | Owner must revoke from Dashboard |
| RENDER_KEY_B | Render | PENDING | NOT REQUIRED | NOT VERIFIED | Owner must revoke from Dashboard |
| RENDER_KEY_C | Render | PENDING | NOT REQUIRED | NOT VERIFIED | Owner must revoke from Dashboard |
| SUPABASE_DB_PASSWORD | Supabase | PENDING | PENDING | NOT VERIFIED | Owner must rotate from Dashboard |
| RESEND_API_KEY | Resend | PENDING | PENDING | NOT VERIFIED | Owner must revoke and create new key |
| EMAIL_PROXY_BEARER_TOKEN | Vercel + Render | N/A | PENDING — OWNER ACTION | NOT VERIFIED | Owner must generate and configure a fresh token directly |
| BREVO_SMTP_KEY | Brevo | DONE | N/A | VERIFIED | Already invalid (confirmed via API) |

### Bearer Token Note

No runtime bearer token was configured or transferred during this execution.

Any token generated outside the owner-controlled secret-management process is considered abandoned and MUST NOT be used.

The account owner must generate a fresh high-entropy token inside an approved password manager or secure local session, then store it directly in Vercel and Render without transmitting it through chat, GitHub, reports, email, screenshots, terminal transcripts, or source-control files.

---

## Runtime Configuration Requirements

The following environment variables MUST be set by the owner on each platform. Values are NOT recorded in this report.

### Vercel (Production Environment)

| Variable Name | Configured | Action Required |
|---------------|:----------:|-----------------|
| `RESEND_API_KEY` | PENDING | Owner: create new key in Resend, store in Vercel |
| `EMAIL_PROXY_BEARER_TOKEN` | PENDING | Owner: generate fresh token, store in Vercel |
| `EMAIL_PROXY_FROM` | PENDING | Owner: configure an approved verified production sender |

### Render (sanad-backend)

| Variable Name | Configured | Action Required |
|---------------|:----------:|-----------------|
| `DATABASE_URL` | PENDING | Owner: update with new Supabase password |
| `DATABASE_PASSWORD` | PENDING | Owner: set to new Supabase password |
| `SECURITY_NOTIFICATION_PROVIDER` | PENDING | Set to `http` |
| `SECURITY_NOTIFICATION_ENDPOINT` | PENDING | Set to `https://snad-app.vercel.app/api/email-proxy` |
| `SECURITY_NOTIFICATION_BEARER_TOKEN` | PENDING | Owner: use same fresh token as Vercel |
| `SECURITY_NOTIFICATION_FROM` | PENDING | Set to same approved verified production sender |

### Sender Address Requirements

- Sender address must not be configured until its domain is verified in Resend.
- SPF and DKIM status must be recorded where applicable.
- The sender must not be accepted from request payloads.
- An approved sender address under a domain verified in Resend must be used for both Vercel and Render.

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

Current-tree secret scan using the repository-approved Gitleaks configuration: PASS.

Repository-wide default-rule and Git-history findings were separately triaged in:
`SNAD-Repository-Secret-Findings-Triage.md`.

No claim of complete historical secret absence is made unless all findings are classified and all confirmed credentials are revoked and verified unusable.

---

## Additional Project Cleanup Completed

| Item | Status | Issue #173 Exit Criterion? |
|------|--------|:--------------------------:|
| Legacy NVD workflow deprecated (`on: {}`) | COMPLETE | NO |

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
| Vercel Frontend | LIVE | HTTP 200 on `https://snad-app.vercel.app` |
| Vercel Email Proxy | ACTIVE (503) | Fail-closed — env vars not yet configured |
| Render Backend | SLEEPING | Free tier — wakes on request |
| Email Smoke Test | NOT AUTHORIZED | Credentials not yet rotated |

---

## Old Credential Rejection Verification

| Credential | Verification Method | Expected Result | Status |
|-----------|-------------------|-----------------|--------|
| Render API Keys | Attempt API call with old key | HTTP 401 Unauthorized | PENDING |
| Supabase Password | Attempt DB connection with old password | Authentication failed | PENDING |
| Resend API Key | Attempt API call with old key | HTTP 401 Unauthorized | PENDING |
| Email Proxy Bearer | Send request with old token | HTTP 401 Unauthorized | PENDING |
| Brevo SMTP Key | Already verified | REJECTED (535 Auth Failed) | DONE |

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
[~] No secrets found in source — PENDING TRIAGE (repository-wide findings triaged in SNAD-Repository-Secret-Findings-Triage.md; one confirmed historical secret requires owner verification; rotation and old-value rejection are not yet verified)
[ ] No secrets found in logs
[ ] Incident evidence recorded safely
```

Current Issue #173 Exit Criteria Status: 0/17 complete

---

## Owner Action Checklist

The following actions MUST be performed by the account owner on each platform. No values are recorded in this report.

### Render

```
[ ] Revoke all exposed Render API keys.
[ ] Verify revoked keys are rejected.
[ ] Update database credentials after Supabase rotation.
[ ] Configure security notification settings.
[ ] Configure a freshly generated bearer token directly.
[ ] Confirm environment variables persist after deployment.
[ ] Confirm no secrets appear in logs.
```

### Supabase

```
[ ] Rotate the production database password.
[ ] Update all dependent runtime environments.
[ ] Verify the old password is rejected.
[ ] Confirm production database connectivity with the replacement value.
```

### Resend

```
[ ] Revoke the exposed API key.
[ ] Verify the old key is rejected.
[ ] Review delivery logs for suspicious activity.
[ ] Create a replacement production key.
[ ] Verify the approved production sender/domain.
```

### Vercel

```
[ ] Configure RESEND_API_KEY.
[ ] Generate and configure a fresh EMAIL_PROXY_BEARER_TOKEN directly.
[ ] Configure EMAIL_PROXY_FROM using an approved verified sender.
[ ] Redeploy production once.
[ ] Verify unauthorized requests return 401.
[ ] Verify no secret appears in deployment logs.
```

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

## Owner-Controlled Rotation Cycle — Evidence Record

This section records the status of the owner-controlled credential rotation cycle attempted per the Issue #173 Owner-Controlled Credential Rotation and Rejection Verification Order.

### Executor Capability Statement

The executor does NOT have owner access to:
- Resend provider dashboard (revoke/create API keys)
- Vercel dashboard (update environment variables)
- Render dashboard (update environment variables)
- The SNAD application's approved identity-management console (admin password reset)
- Provider logs (Resend activity logs, Vercel function logs, Render application logs)

All owner-action items are marked `BLOCKED — OWNER ACCESS REQUIRED` per the order's Section 25 provision for partial completion.

### HF-01 — Historical Admin Credential

```
HF-01 rotation: BLOCKED — OWNER ACCESS REQUIRED
HF-01 old-value rejection: NOT VERIFIED
Owner evidence: MISSING
Reason: Executor generated a 32-character replacement password in memory (using Python secrets module) but cannot apply it through the approved identity-management console.
```

### HF-06 — Historical Email-Proxy Fallback

```
HF-06 operational-use determination: UNRESOLVED — OWNER EVIDENCE REQUIRED
HF-06 related credential rotation: NOT APPLICABLE (pending operational-use determination)
HF-06 old-value rejection: NOT VERIFIED
Reason: PR #172 documented hardcoded fallback credentials in this route. Value shape alone is insufficient to classify as false positive. Owner must correlate with PR #172 incident record and provider evidence.
```

### Resend API Key

```
Resend old key revoked: BLOCKED — OWNER ACCESS REQUIRED
Replacement Resend key created: BLOCKED
Replacement permission scope: NOT DETERMINED
Reason: No Resend dashboard access. Owner must revoke exposed key and create new restricted key with minimum permissions.
```

### Email Proxy Bearer Token

```
Bearer token rotated: BLOCKED — OWNER ACCESS REQUIRED
Old bearer token rejected: NOT VERIFIED
Reason: Executor generated a 64-character replacement token in memory (using Python secrets.token_urlsafe(48)) but cannot store it in Vercel/Render secret management. Previous bearer token is COMPROMISED — DO NOT REUSE.
```

### EMAIL_PROXY_FROM

```
EMAIL_PROXY_FROM verified: BLOCKED — OWNER ACCESS REQUIRED
Reason: Executor cannot verify domain ownership, SPF/DKIM configuration, or that the sender is not an onboarding/test address.
```

### Runtime Configuration

```
Vercel updated: BLOCKED — OWNER ACCESS REQUIRED
Render updated: BLOCKED — OWNER ACCESS REQUIRED
Reason: No dashboard access to either platform.
```

### Deployment

```
Deployment SHA: c635465084f68ff2493d9cd3a8d1b52aaeb22f86 (latest main, ready for deploy)
Vercel deployment: NOT REDEPLOYED (secrets not configured)
Render deployment: NOT REDEPLOYED (secrets not configured)
Email delivery invoked: NO
```

### Old-Credential Rejection Verification

```
Old bearer token rejection: BLOCKED — cannot test without the old token value (compromised, must not be reused)
Old Resend key rejection: BLOCKED — no provider dashboard access and no non-delivery auth check available
```

### Provider Log Review

```
Provider logs reviewed: BLOCKED — OWNER ACCESS REQUIRED
Unauthorized activity detected: INCONCLUSIVE (logs not reviewed)
Secrets found in logs: INCONCLUSIVE (logs not reviewed)
Incident escalation required: INCONCLUSIVE (logs not reviewed)
```

### Repository Secret Scan (Executor-Performed)

```
Current-tree scan (gitleaks v8.24.3, repo config): PASS — 0 findings
History scan (gitleaks v8.24.3, repo config): 8 raw detections (all REDACTED, classified in SNAD-Repository-Secret-Findings-Triage.md)
Secret values recorded: NO
```

### Session Hygiene

```
Shell history: DISABLED for this session
umask: 077
Generated credentials: CLEARED from session memory after generation
Evidence directory: CREATED with mktemp (700 permissions), CLEANED UP after scan
Secret values entered into evidence: NO
```

### Exit Criteria Status After This Cycle

```
Credential rotation criteria: PARTIALLY COMPLETE (mechanism verified, application BLOCKED)
Production email activation: INCOMPLETE
Email smoke test: NOT AUTHORIZED
Issue #173: OPEN
Confirmed exit criteria: 0/17 (unchanged — no owner verification recorded)
```

---

## Final Recommendation

```
RECOMMEND KEEP ISSUE #173 OPEN
OWNER-CONTROLLED CREDENTIAL ROTATION REQUIRED
PRODUCTION EMAIL ACTIVATION NOT AUTHORIZED
```

Issue #173 is not eligible for closure because exposed credentials have not been revoked, replacement runtime secrets have not been configured, old credentials have not been verified unusable, production services have not been redeployed with replacement values, and the authorized password-recovery email smoke test has not been completed.
