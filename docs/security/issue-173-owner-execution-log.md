# SNAD Issue #173 — Owner Execution Log

## Important Disclosure

This execution log is produced by the AI executor (Super Z), not the human account owner. The AI executor does NOT have access to provider dashboards (Render, Supabase, Resend, Vercel) and cannot perform provider-side credential rotation, revocation, or rejection verification.

All actions below reflect what the AI executor CAN do from the repository and code level. Provider-side actions remain BLOCKED pending human owner execution.

## Credential Execution Status

### HF-01 — Historical Administrator Password (CD-00-P0-001)

| Field | Value |
|---|---|
| Finding ID | HF-01 |
| Debt ID | CD-00-P0-001 |
| Provider | SNAD Platform Backend (Spring Boot) |
| Environment | Production (was set via deleted GitHub Actions workflow) |
| Credential Type | Administrator password |
| Owner | Account owner (snadaiapp-png) |
| Rotation Started At | NOT STARTED |
| Rotation Completed At | NOT STARTED |
| New Credential Generated | NOT APPLICABLE (executor cannot access production auth) |
| Deployment Updated | NOT APPLICABLE |
| Old Credential Revoked At | NOT STARTED |
| Old Credential Rejection Verified | NOT_VERIFIED |
| Verification Method | NONE — requires owner access to production application |
| Audit Event Reference | NONE — requires owner access to audit logs |
| Deployment Reference | NONE — executor cannot deploy |
| Issue #173 Updated | YES (status comment added by executor) |
| Final Status | **BLOCKED — OWNER_ACCESS_REQUIRED** |

**Evidence available to executor:**
- Current-tree Gitleaks: PASS (value not in current tree)
- History Gitleaks: Finding discoverable in commit `f766e429` (deleted file)
- Value removed from current tree: YES (file deleted in commit `6dfd05e`)

**Evidence NOT available to executor (requires owner):**
- New password rotation via application admin reset
- Old password revocation
- Old password rejection verification
- Production audit log review
- Production deployment after rotation

---

### HF-06 — Historical Email-Proxy Fallback (CD-00-P0-002)

| Field | Value |
|---|---|
| Finding ID | HF-06 |
| Debt ID | CD-00-P0-002 |
| Provider | SNAD Frontend (Next.js email proxy) — possibly Resend |
| Environment | Was in code; removed by PR #172 |
| Credential Type | Environment variable fallback (RESEND_API_KEY) |
| Owner | Account owner (snadaiapp-png) |
| Final Classification | **NOT DETERMINED** — requires owner knowledge of operational history |
| Rotation Started At | NOT APPLICABLE (pending classification) |
| Rotation Completed At | NOT APPLICABLE |
| Old Credential Revoked At | NOT APPLICABLE |
| Old Credential Rejection Verified | NOT_VERIFIED |
| Verification Method | NONE |
| Audit Event Reference | NONE |
| Deployment Reference | NONE |
| Issue #173 Updated | YES (status comment added by executor) |
| Final Status | **BLOCKED — OWNER_VERIFICATION_REQUIRED** |

**Evidence available to executor:**
- Value is 8 characters, does NOT start with `re_` (Resend API key prefix)
- Value was a fallback default in `process.env.RESEND_API_KEY || '<value>'`
- Value was removed from current tree by PR #172
- Current-tree Gitleaks: PASS
- History Gitleaks: Finding discoverable in commit `a6b11112`

**Evidence NOT available to executor (requires owner):**
- Whether the fallback value was ever used to authenticate against Resend
- Whether the email proxy ever functioned with this fallback
- Whether the value was a placeholder like "missing" or a real key
- Access to Resend dashboard to verify/revoke

**Possible classifications (owner must choose one):**
1. FALSE_POSITIVE — if value was a placeholder (e.g., "missing", "not-set") never used as a credential
2. CONFIRMED_SECRET — if value was a real Resend API key used operationally
3. NON_SECRET_SENSITIVE_VALUE — if value was sensitive but not authentication material

---

### Render API Keys

| Field | Value |
|---|---|
| Provider | Render |
| Environment | Production / CI |
| Credential Type | API Key(s) |
| Exposed In | IM chat (LEAKED) |
| Rotation | NOT PERFORMED — executor cannot access Render Dashboard |
| Old Key Revoked | NOT_VERIFIED |
| Old Key Rejection | NOT_VERIFIED |
| Final Status | **BLOCKED — OWNER_ACCESS_REQUIRED** |

---

### Supabase Database Password

| Field | Value |
|---|---|
| Provider | Supabase |
| Environment | Production |
| Credential Type | Database password |
| Exposed In | IM chat (LEAKED) |
| Rotation | NOT PERFORMED — executor cannot access Supabase Dashboard |
| Old Password Revoked | NOT_VERIFIED |
| Old Password Rejection | NOT_VERIFIED |
| Final Status | **BLOCKED — OWNER_ACCESS_REQUIRED** |

---

### Resend API Key

| Field | Value |
|---|---|
| Provider | Resend |
| Environment | Production |
| Credential Type | API Key |
| Exposed In | IM chat (LEAKED) |
| Rotation | NOT PERFORMED — executor cannot access Resend Dashboard |
| Old Key Revoked | NOT_VERIFIED |
| Old Key Rejection | NOT_VERIFIED |
| Final Status | **BLOCKED — OWNER_ACCESS_REQUIRED** |

---

### Brevo SMTP Key

| Field | Value |
|---|---|
| Provider | Brevo |
| Environment | Production |
| Credential Type | SMTP Key |
| Exposed In | IM chat (LEAKED) |
| Rotation | NOT REQUIRED (key was already invalid) |
| Old Key Revoked | VERIFIED (confirmed via API in previous work — HTTP rejection) |
| Old Key Rejection | VERIFIED_REVOKED |
| Verification Method | API authentication attempt returned rejection |
| Final Status | **CLOSED** |

---

### EMAIL_PROXY_BEARER_TOKEN

| Field | Value |
|---|---|
| Provider | Vercel + Render |
| Environment | Production |
| Credential Type | Bearer Token |
| Exposure Status | NOT_EXPOSED (no value was ever leaked) |
| Rotation Required | NO (not a rotation — this is new provisioning) |
| Provisioning Required | YES — according to deployment design |
| Final Status | **NOT_APPLICABLE** (not a debt item — no exposure occurred) |

---

## Summary

| Credential | Rotation | Revocation | Rejection Verified | Status |
|---|---|---|---|---|
| HF-01 (admin password) | NOT PERFORMED | NOT VERIFIED | NOT_VERIFIED | BLOCKED |
| HF-06 (email-proxy fallback) | NOT APPLICABLE | NOT VERIFIED | NOT_VERIFIED | BLOCKED |
| Render API Keys | NOT PERFORMED | NOT VERIFIED | NOT_VERIFIED | BLOCKED |
| Supabase DB Password | NOT PERFORMED | NOT VERIFIED | NOT_VERIFIED | BLOCKED |
| Resend API Key | NOT PERFORMED | NOT VERIFIED | NOT_VERIFIED | BLOCKED |
| Brevo SMTP Key | NOT REQUIRED | VERIFIED | VERIFIED_REVOKED | CLOSED |
| EMAIL_PROXY_BEARER_TOKEN | NOT APPLICABLE | NOT APPLICABLE | NOT APPLICABLE | NOT_EXPOSED |

## Root Cause of BLOCKED Status

The AI executor (Super Z) operates from the repository and local execution environment only. It does NOT have:
- Provider dashboard credentials
- Production deployment access
- Production database access
- Provider audit log access

All provider-side credential rotations MUST be performed by the human account owner (`snadaiapp-png`) using:
- Render Dashboard: https://dashboard.render.com
- Supabase Dashboard: https://app.supabase.com
- Resend Dashboard: https://resend.com/api-keys
- Vercel Dashboard: https://vercel.com/dashboard

The owner action package at `docs/security/issue-173-owner-action-package.md` provides detailed step-by-step instructions.
