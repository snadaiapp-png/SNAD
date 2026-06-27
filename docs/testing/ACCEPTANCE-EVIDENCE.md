# SNAD Acceptance Evidence Matrix

## 1. Purpose

This matrix separates implementation evidence from operational acceptance and owner authorization. A capability is not accepted merely because its code was merged.

## 2. Evidence states

- `IMPLEMENTED`: code or configuration exists on `main`.
- `CI_VERIFIED`: required automated checks passed.
- `DEPLOYED_PILOT`: capability is present in the pilot environment.
- `E2E_VERIFIED`: the complete user-to-runtime path passed.
- `OWNER_APPROVED`: the controlling owner or gate explicitly approved it.
- `BLOCKED`: a required condition is unresolved.

## 3. Capability matrix

| Capability | Implemented | CI verified | Pilot deployed | E2E verified | Owner approved | Notes |
|---|---:|---:|---:|---:|---:|---|
| SNAD visual identity | Yes | Yes | Yes | Yes | Development use | PR #157 |
| Arabic/Latin typography | Yes | Yes | Yes | Yes | Development use | Noto Sans Arabic/Noto Sans |
| Authentication boundary | Yes | Yes | Yes | Partial | Development use | Tenant ambiguity and credential rotation supported |
| Forgotten-password UI | Yes | Yes | Yes | Pending provider | No | PR #158 |
| Reset-password page | Yes | Yes | Yes | Pending provider | No | Single-use runtime test pending |
| Administrator set-password link | Yes | Yes | Yes | Pending provider | No | Direct password payload prohibited |
| Password-change confirmation | Yes | Yes | Backend path present | Pending provider | No | Delivery proof pending |
| Mailbox basic send/receive | External account ready | N/A | N/A | Basic test passed | Mailbox approved | Does not prove backend delivery |
| Backend notification provider | Interface implemented | Yes | Configuration pending | No | No | Runtime endpoint and secret required |
| PostgreSQL migrations | Yes | Yes | Pilot active | Yes | Pilot only | Commercial production not approved |
| Backup and restore | Yes | Yes | CI evidence | Yes in validation workflow | Pilot only | Production DR plan still separate |
| Performance baseline | Yes | Yes | CI evidence | Yes | Pilot only | Free-tier limits apply |
| NVD bulk-feed mirror | Yes | Tests/evidence present | Workflow controlled | Operational sequence incomplete | No | R12L and later fixes |
| OWASP final gate | Workflow exists | Not finally accepted | No | No | No | Issue #101 blocker |
| Commercial production | No final architecture | No | No | No | No | Not authorized |

## 4. Identity evidence

```text
PR: 157
MERGE_SHA: c0115022feeb0cf21fdd0373b812c6eaa5bb120d
IDENTITY_GOVERNANCE: PASS
WEB_CI: PASS
SECURITY_BASELINE: PASS
VERCEL_DEPLOYMENT: READY
PUBLIC_HTTP_RESPONSE: 200
```

## 5. Account recovery evidence

```text
PR: 158
MERGE_SHA: a12ec50890a59fc89bf3fab91b3926b7a625e3f2
MAVEN_TESTS: PASS
COMPILE_DIAGNOSTICS: PASS
WEB_CI: PASS
IDENTITY_GOVERNANCE: PASS
SECURITY_BASELINE: PASS
DEVELOPMENT_SECURITY_ACCEPTANCE: PASS
POSTGRESQL_ACCEPTANCE: PASS
PERFORMANCE_BASELINE: PASS
BACKUP_RESTORE_VALIDATION: PASS
PLAINTEXT_PASSWORD_EMAIL: PROHIBITED
BACKEND_EMAIL_E2E: PENDING
```

## 6. Required E2E email evidence

Before closing Issue #150, capture:

```text
MAIN_SHA=<tested commit>
BACKEND_DEPLOYMENT_ID=<deployment>
BACKEND_HEALTH=UP
NOTIFICATION_PROVIDER=CONFIGURED
APPROVED_FROM_MATCH=PASS
RECOVERY_REQUEST_RESULT=GENERIC_SUCCESS
DELIVERY_RESULT=PASS
EMAIL_CONTAINS_PASSWORD=NO
EMAIL_CONTAINS_HTTPS_RESET_LINK=YES
RESET_RESULT=PASS
TOKEN_REUSE_RESULT=REJECTED
TOKEN_EXPIRY_RESULT=PASS
OLD_SESSION_RESULT=REVOKED
CHANGE_CONFIRMATION_RESULT=PASS
RAW_TOKEN_IN_LOGS=NO
```

## 7. Security gate evidence

Issue #101 remains the authority for the development security gate. The final attestation must include the verified NVD feed release, snapshot, integrity, OWASP offline result, R12B result, and owner decision.

Do not use the passing account-recovery CI results to claim that OWASP Final or Issue #101 passed.

## 8. Evidence retention

- Keep pull requests and workflow runs immutable.
- Store sanitized test reports and screenshots.
- Do not store raw reset values, passwords, database credentials, provider credentials, or bearer values.
- Record UTC timestamps and exact commit/deployment identifiers.
- Prefer artifacts and release assets with checksums.
- Link evidence from the controlling issue.

## 9. Acceptance rule

A capability is operationally accepted only when every required column through `E2E_VERIFIED` is satisfied and any controlling owner approval is recorded. Missing evidence is a pending state, not a pass.
