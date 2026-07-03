# SNAD Current Implementation Status

Last documentation baseline: 27 June 2026.

## 1. Controlling status

```text
ISSUE_101: OPEN
DEVELOPMENT_GATE_01: NOT APPROVED
OWASP_FINAL: NOT PASSED
COMMERCIAL_GO_LIVE: NOT AUTHORIZED
```

Issue #101 is the controlling security and Sprint 0 entry gate. Documentation, successful pilot deployment, or individual passing workflows do not override that issue.

## 2. Current repository baseline

The documentation baseline was added after the NVD R12L implementation and subsequent edge-case corrections. The repository contains:

- Resumable NVD bulk-feed mirror.
- Per-feed META and digest verification.
- Persistent checkpoint and Last-Known-Good design.
- SNAD identity and typography implementation.
- Secure account-recovery notification workflow.
- Pilot frontend and backend deployment configuration.
- CI, PostgreSQL, performance, backup/restore, identity, and security workflows.

## 3. Visual identity and frontend

Status: **IMPLEMENTED AND DEPLOYED TO PILOT**.

Evidence:

- PR #157 merged.
- Merge SHA: `c0115022feeb0cf21fdd0373b812c6eaa5bb120d`.
- SNAD semantic colors and typography implemented.
- Noto Sans Arabic and Noto Sans loaded through `next/font`.
- Identity governance and Web CI passed.
- Vercel deployment reached `READY`.
- Public pilot URL returned HTTP 200.

This is pilot availability, not commercial production approval.

## 4. Authentication and account recovery

Status: **CODE COMPLETE; RUNTIME EMAIL ACTIVATION PENDING**.

Evidence:

- PR #158 merged.
- Merge SHA: `a12ec50890a59fc89bf3fab91b3926b7a625e3f2`.
- Forgotten-password UI implemented.
- Reset-password page implemented.
- Administrator-issued set-password link implemented.
- Plaintext password delivery prohibited.
- Random single-use recovery values stored as hashes.
- Thirty-minute expiry enforced by the implementation contract.
- Delivery failure revokes the generated self-service value.
- Password-change confirmation implemented.
- CI, compile diagnostics, Web CI, identity governance, security baseline, development security acceptance, PostgreSQL acceptance, performance, and backup/restore passed for the reviewed change.

Remaining evidence:

- Deployment-managed notification endpoint configured.
- Provider credential configured outside GitHub.
- Approved sender configured through `SECURITY_NOTIFICATION_FROM`.
- Full end-to-end recovery email test completed from the deployed backend.

Issue #150 tracks this final runtime evidence.

## 5. Approved mailbox

The current approved mailbox identity and its basic send/receive test are recorded in Issue #150 and the merged account-recovery PR record. Repository documentation references it through `SECURITY_NOTIFICATION_FROM` to avoid duplicating operational details and credentials.

The basic mailbox test confirms account capability only. It does not prove that the deployed backend can deliver recovery messages.

## 6. Pilot infrastructure

### Frontend

- Vercel project: `snad-app`.
- Public domain: `snad-app.vercel.app`.
- Status observed: ready and HTTP 200.

### Backend

- Render pilot service: `sanad-backend`.
- Region: Frankfurt.
- Automatic deployment disabled.
- Exact reviewed commit deployment required.

### Database

- Supabase PostgreSQL pilot.
- Frankfurt/Central EU.
- Session Pooler used by the pilot.

### Limitations

- Free-tier cold starts and capacity limits.
- Pilot data and controlled users only.
- No production SLA.
- No commercial authorization.

## 7. NVD and OWASP security pipeline

Status: **R12L IMPLEMENTED; FINAL GATE STILL CONTROLLED BY ISSUE #101**.

The repository includes a resumable NVD bulk-feed mirror with checkpointing and integrity verification. Recent corrections handle META/feed race conditions and non-fatal checkpoint asset inconsistencies.

Do not represent the OWASP final gate as passed until:

1. A complete immutable feed release is verified.
2. Snapshot bootstrap succeeds from that feed.
3. Snapshot integrity passes.
4. OWASP offline analysis passes under the defined thresholds.
5. R12B final evidence passes.
6. Final attestation is posted.
7. The project owner explicitly approves Issue #101.

## 8. Testing status

The following evidence has passed for the identity and account-recovery changes:

```text
MAVEN_TESTS: PASS
COMPILE_DIAGNOSTICS: PASS
WEB_CI: PASS
IDENTITY_GOVERNANCE: PASS
SECURITY_BASELINE: PASS
DEVELOPMENT_SECURITY_ACCEPTANCE: PASS
POSTGRESQL_ACCEPTANCE: PASS
PERFORMANCE_BASELINE: PASS
BACKUP_RESTORE_VALIDATION: PASS
MASTER_BACKLOG_VALIDATION: PASS
SERVICE_DECOMPOSITION_VALIDATION: PASS
```

OWASP final is excluded from this passing list because its NVD-controlled acceptance remains unresolved.

## 9. Decision matrix

| Capability | Current decision |
|---|---|
| Continue development work | Allowed under current restrictions |
| Pilot frontend availability | Active |
| Pilot backend integration | Active, controlled |
| Real recovery email from backend | Pending provider configuration and E2E evidence |
| Use plaintext passwords in email | Prohibited |
| Close Issue #150 | Not yet; E2E email evidence pending |
| Close Issue #101 | Not authorized |
| Sprint 0 unrestricted entry | Not approved by the controlling gate |
| Staging/production release | Not authorized |
| Commercial go-live | Not authorized |

## 10. Next documentation updates

Update this file when any of the following occurs:

- Backend email provider becomes operational.
- End-to-end recovery test passes or fails.
- Issue #150 closes.
- NVD feed, snapshot, Integrity, OWASP, or R12B changes state.
- Issue #101 receives an explicit owner decision.
- Pilot infrastructure or public URLs change.
- A commercial-production architecture is approved.
