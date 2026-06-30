# SNAD Issue #173 — Closure Evidence Record

## Status: BLOCKED — OWNER ACTION REQUIRED

No credential rotation has been performed. This document records the current state and the evidence required for closure.

## P0 Debt Items

### CD-00-P0-001 — Historical Administrator Password (HF-01)

| Field | Value |
|---|---|
| Finding ID | HF-01 |
| Debt ID | CD-00-P0-001 |
| System | SNAD Platform Backend |
| Environment | Production (via GitHub Actions workflow) |
| Owner | Account owner |
| Classification | CONFIRMED_SECRET |
| Action performed | NONE — executor does not have owner access |
| Rotation timestamp | NOT SET |
| Revocation timestamp | NOT SET |
| Verification method | NOT RUN |
| Verification result | NOT RUN |
| Issue reference | Issue #173 |
| Current-tree scan | PASS (0 findings — value not in current tree) |
| History scan | Finding discoverable in commit `f766e429` (deleted file) |

### CD-00-P0-002 — Historical Email-Proxy Fallback (HF-06)

| Field | Value |
|---|---|
| Finding ID | HF-06 |
| Debt ID | CD-00-P0-002 |
| System | SNAD Frontend (Next.js email proxy) |
| Environment | Was in code; removed by PR #172 |
| Owner | Account owner |
| Classification | NEEDS_OWNER_VERIFICATION |
| Action performed | NONE — executor does not have owner access |
| Determination | NOT MADE — requires owner to determine if value was operational |
| Rotation timestamp | NOT APPLICABLE (pending classification) |
| Revocation timestamp | NOT APPLICABLE |
| Verification method | NOT RUN |
| Verification result | NOT RUN |
| Issue reference | Issue #173 |
| Current-tree scan | PASS (0 findings — value not in current tree) |
| History scan | Finding discoverable in commit `a6b11112` |

## Additional Exposed Credentials (from Issue #173)

| Credential | System | Rotation Status | Old-Value Rejection |
|---|---|---|---|
| Render API Key(s) | Render | NOT PERFORMED | NOT VERIFIED |
| Supabase DB Password | Supabase | NOT PERFORMED | NOT VERIFIED |
| Resend API Key | Resend | NOT PERFORMED | NOT VERIFIED |
| Brevo SMTP Key | Brevo | DONE (already invalid) | VERIFIED (confirmed via API) |
| EMAIL_PROXY_BEARER_TOKEN | Vercel + Render | NOT GENERATED | NOT APPLICABLE |

## Gitleaks Results

| Scan | Result | Date |
|---|---|---|
| Current-tree (repo config) | PASS — 0 findings | 2026-06-30 |
| History (repo config) | 8 raw detections (all classified in PR #179) | 2026-06-30 |

## Blocking Condition

```
CD-00-P0-001: BLOCKED — owner access required for credential rotation
CD-00-P0-002: BLOCKED — owner verification required for operational-use determination
Issue #173: OPEN
```

## Required Evidence for Closure

When the owner completes the actions in `issue-173-owner-action-package.md`, the following evidence must be recorded here (without secret values):

### For CD-00-P0-001
- [ ] Rotation timestamp
- [ ] Revocation timestamp
- [ ] Affected systems list
- [ ] New credential deployment confirmation
- [ ] Old credential rejection confirmation
- [ ] Verifier identity
- [ ] Verification method

### For CD-00-P0-002
- [ ] Final classification (CONFIRMED_SECRET / FALSE_POSITIVE / NON_SECRET_SENSITIVE_VALUE)
- [ ] If CONFIRMED_SECRET: rotation + rejection evidence
- [ ] If FALSE_POSITIVE: reasoning and evidence
- [ ] Verifier identity

### For all additional credentials
- [ ] Each credential rotated
- [ ] Each old value verified rejected
- [ ] Runtime environments updated
- [ ] Deployment verified
- [ ] Issue #173 closed

## Commit SHA

```
This evidence record: (pending commit)
Issue #173: OPEN
```
