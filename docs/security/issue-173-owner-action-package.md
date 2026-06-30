# SNAD Issue #173 — Owner Action Package

## Purpose

This document provides the account owner with step-by-step instructions to close the P0 security credential exposure debt (Issue #173). No secret values are included.

## P0 Debt Items

### CD-00-P0-001 — Historical Administrator Password (HF-01)

| Field | Value |
|---|---|
| Finding ID | HF-01 |
| File (deleted) | `.github/workflows/set-admin-password.yml` |
| Line | 51 |
| Commit | `f766e429` (deleted in `6dfd05e`) |
| Credential Type | Administrator password |
| System | SNAD Platform Backend (Spring Boot) |
| Environment | Production (was set via GitHub Actions workflow) |
| Owner | Account owner (`snadaiapp-png`) |
| Current Status | BLOCKED — credential rotation NOT VERIFIED |
| Classification | CONFIRMED_SECRET |

#### Owner Actions Required

1. **Identify the affected administrator account**
   - Check `BOOTSTRAP_ADMIN_EMAIL` in Render environment variables
   - Verify the account exists in the production database

2. **Rotate the password**
   - Use the application's password-reset flow (`/reset-password?token=...`)
   - OR use the admin reset API (`POST /api/v1/auth/admin-reset-password/{userId}`)
   - OR update directly in the database (BCrypt hash)
   - Generate a new strong password (32+ characters, mixed case, digits, symbols)
   - Do NOT reuse any value that appeared in git history or chat

3. **Store the new password securely**
   - In a password manager (not in the repository, not in chat, not in Issues)
   - Update any automated scripts that reference the old password

4. **Verify the old password is rejected**
   - Attempt authentication with the old value — it should fail with HTTP 401
   - Record the timestamp and verification method
   - Do NOT print the old password in any log or report

5. **Record closure evidence**
   - Timestamp of rotation
   - Timestamp of revocation (when old value stopped working)
   - Verification method (auth rejection, audit log, etc.)
   - Verifier identity
   - Update Issue #173 with a redacted comment

6. **Verify Gitleaks**
   - Current-tree scan: should be PASS (0 findings — value is in history only)
   - History scan: finding remains discoverable but credential is revoked

#### Closure Criteria

```
Rotation: COMPLETE
Old-value rejection: VERIFIED
Current-tree scan: PASS
Issue #173: Updated with evidence
```

---

### CD-00-P0-002 — Historical Email-Proxy Fallback (HF-06)

| Field | Value |
|---|---|
| Finding ID | HF-06 |
| File | `apps/web/app/api/email-proxy/route.ts` |
| Line | 18 |
| Commit | `a6b11112` |
| Credential Type | Environment variable fallback (RESEND_API_KEY) |
| System | SNAD Frontend (Next.js email proxy) |
| Environment | Was present in code; removed by PR #172 |
| Owner | Account owner (`snadaiapp-png`) |
| Current Status | BLOCKED — owner verification required |
| Classification | NEEDS_OWNER_VERIFICATION |

#### Context

PR #172 documented that this route contained hardcoded runtime fallback credentials. The value was removed from the current tree. The question is whether the fallback value was:
- A real Resend API key used in production (CONFIRMED_SECRET)
- A placeholder/default value never used operationally (FALSE_POSITIVE)
- A non-secret sensitive value (NON_SECRET_SENSITIVE_VALUE)

The value is 8 characters long and does NOT start with `re_` (Resend API key prefix).

#### Owner Actions Required

1. **Determine the operational status of the fallback value**
   - Was this value ever used to authenticate against the Resend API?
   - Was it a placeholder like `"missing"` or `"not-set"`?
   - Did the email proxy ever function with this fallback?

2. **If CONFIRMED_SECRET (was used operationally):**
   - Revoke the old Resend API key from the Resend dashboard
   - Generate a new restricted Resend API key
   - Configure it as `RESEND_API_KEY` in Vercel environment variables
   - Verify the old key is rejected by making a test API call (non-delivery)
   - Record evidence as in CD-00-P0-001

3. **If FALSE_POSITIVE (was never a real credential):**
   - Document why it's not a credential (format mismatch, never used, etc.)
   - Record the reasoning in the closure evidence
   - No rotation needed, but the value should not reappear in code
   - Add a narrow Gitleaks allowlist entry if needed (path-specific, fingerprint-specific only)

4. **If NON_SECRET_SENSITIVE_VALUE:**
   - Document what the value was (without printing it)
   - Explain why it's sensitive but not a credential
   - Verify it's been replaced with environment-based configuration
   - Record evidence

#### Closure Criteria

```
Classification: CONFIRMED_SECRET | FALSE_POSITIVE | NON_SECRET_SENSITIVE_VALUE
If CONFIRMED_SECRET: Rotation + rejection verified
If FALSE_POSITIVE: Reasoning documented
Issue #173: Updated with evidence
```

---

## Additional Credentials in Issue #173

The following credentials were also exposed in IM chat and require rotation:

| Credential | System | Exposed In | Action Required |
|---|---|---|---|
| Render API Key(s) | Render | IM chat (LEAKED) | Revoke all exposed keys from Render Dashboard |
| Supabase DB Password | Supabase | IM chat (LEAKED) | Rotate password from Supabase Dashboard |
| Resend API Key | Resend | IM chat (LEAKED) | Revoke old key, create new restricted key |
| Brevo SMTP Key | Brevo | IM chat (LEAKED) | Already invalid (confirmed via API) |
| EMAIL_PROXY_BEARER_TOKEN | Vercel + Render | Not yet generated | Generate and configure after rotation |

### Rotation Steps for Each

1. **Render**: Dashboard → Settings → API Keys → Revoke exposed keys → Create new key → Update any scripts using old key
2. **Supabase**: Dashboard → Database → Settings → Reset database password → Update Render `DATABASE_PASSWORD` env var → Restart backend
3. **Resend**: Dashboard → API Keys → Revoke exposed key → Create new key with minimum permissions → Update Vercel `RESEND_API_KEY` env var
4. **Brevo**: Already invalid — verify in Brevo dashboard that the key is revoked
5. **EMAIL_PROXY_BEARER_TOKEN**: Generate using `python3 -c "import secrets; print(secrets.token_urlsafe(48))"` → Store in Vercel + Render as `EMAIL_PROXY_BEARER_TOKEN`

### Post-Rotation Verification

After all rotations:
1. Redeploy backend on Render
2. Redeploy frontend on Vercel
3. Verify health: `curl https://[backend-url]/actuator/health`
4. Verify auth: Login with new admin credentials
5. Verify old credentials are rejected (without printing them)
6. Run `gitleaks detect --no-git` — should be PASS
7. Update Issue #173 with redacted closure evidence
8. Close Issue #173 only when ALL credentials are rotated and verified

---

## Important Rules

- **NEVER** print secret values in Issues, PRs, commits, chat, or logs
- **NEVER** reuse any value that appeared in git history or chat
- **NEVER** store secrets in the repository
- **ALWAYS** use HTTPS for provider dashboards
- **ALWAYS** verify old credentials are rejected after rotation
- **ALWAYS** record evidence (timestamp, method, result) without exposing values
