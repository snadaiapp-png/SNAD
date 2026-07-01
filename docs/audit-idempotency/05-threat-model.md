# Stage 05 — Threat Model

## Overview

Stage 05 introduces immutable audit logging and persistent idempotency enforcement to the SANAD platform. This document identifies the threats addressed and the mitigations implemented.

## Threats Addressed

### T1: Audit Log Tampering
**Threat**: An attacker with database access modifies or deletes audit events to cover their tracks.
**Mitigation**: 
- PostgreSQL triggers (V23 migration) block UPDATE, DELETE, and TRUNCATE on `audit_events` at the database level, even for the table owner.
- The runtime role (`sanad_runtime_app`) can only INSERT and SELECT.
- The fixture role (`sanad_fixture_ci`) has BYPASSRLS for CI test cleanup but is never used in production.

### T2: Cross-Tenant Audit Data Leakage
**Threat**: A user in Tenant A reads audit events belonging to Tenant B.
**Mitigation**: 
- FORCE RLS on `audit_events` with policy `tenant_isolation_audit_events` using `current_setting('app.current_tenant_id')`.
- The runtime role is NOSUPERUSER NOBYPASSRLS — subject to RLS.
- Cross-tenant GET returns 404 (not 500 or 403) to avoid leaking resource existence.

### T3: Sensitive Data in Audit Payloads
**Threat**: Passwords, tokens, or secrets are captured in audit `beforeState`/`afterState`/`metadata` JSON fields.
**Mitigation**: 
- `AuditRedactionService` recursively walks JSON trees and replaces sensitive field values with `[REDACTED]` before persistence.
- Sensitive field patterns: password, token, secret, authorization, cookie, credential, otp, etc.
- Raw JWTs, Authorization headers, password hashes, and refresh-token hashes are NEVER passed to the redaction service — they are omitted entirely from state payloads.

### T4: Audit Event Forgery (Hash Chain Attack)
**Threat**: An attacker inserts a forged audit event with a valid-looking hash.
**Mitigation**: 
- Each event's `eventHash` = SHA-256(canonical payload + previousHash + tenantId + occurredAt).
- The `previousHash` links to the preceding event, forming a tamper-evident chain.
- `AuditIntegrityVerificationService` recomputes the entire chain and detects any mismatch.
- The hash includes `tenantId` and `occurredAt` to prevent cross-tenant chain replay.

### T5: Audit-Business Transaction Inconsistency
**Threat**: A business mutation commits but the audit event fails (or vice versa), leading to false success audits or unaudited mutations.
**Mitigation**: 
- `AuditService.record()` uses `Propagation.REQUIRED` — joins the caller's transaction.
- If the business mutation rolls back, the audit event rolls back too.
- For denied requests, `AuditService.recordDenied()` uses `Propagation.REQUIRES_NEW` to ensure the denial audit persists even if the caller's transaction is marked for rollback.

### T6: Duplicate Command Execution
**Threat**: A network timeout causes the client to retry a POST request, resulting in duplicate business state (e.g., two organizations created).
**Mitigation**: 
- `IdempotencyService.reserveOrReplay()` uses a DB-level unique constraint on `(tenant_id, operation, route, idempotency_key)` to serialize concurrent requests.
- Same key + same fingerprint → replay original response.
- Same key + different fingerprint → 409 SANAD-IDEMP-002.
- Concurrent same-key requests → one executes, others get IN_PROGRESS.

### T7: Idempotency Key Reuse Across Tenants
**Threat**: Tenant A and Tenant B use the same idempotency key K, and one tenant's request replays the other's response.
**Mitigation**: 
- The unique constraint includes `tenant_id`, so the same key in different tenants creates independent records.
- RLS on `idempotency_records` prevents cross-tenant reads.

### T8: Credential Leakage via Idempotent Response Replay
**Threat**: A replayed response includes `Set-Cookie` or `Authorization` headers that leak credentials to the retrying client.
**Mitigation**: 
- `IdempotencyService.complete()` strips `Set-Cookie` and `Authorization` from stored response headers before persistence.
- The `sanitizeHeaders()` method filters these headers out.

### T9: Stuck PROCESSING Records
**Threat**: A process crash during business execution leaves an idempotency record stuck in PROCESSING forever.
**Mitigation**: 
- `expiresAt` field on every record. After expiration, `reserveOrReplay()` returns EXPIRED.
- `FAILED_RETRYABLE` status allows re-execution after a transient failure.
- `FAILED_FINAL` status blocks retry for permanent failures.

## Residual Risks

### R1: Audit Hash Chain Fork (Concurrency)
When multiple audit events are written concurrently within the same tenant, they may share the same `previousHash` (the latest event at the time of reservation). This creates a "fork" rather than a strict linear chain. The integrity verification service handles this by recomputing hashes in order — a fork does not cause a false positive, but it means the chain is not strictly linear. This is acceptable for a high-throughput audit log.

### R2: Fixture Role BYPASSRLS in CI
The `sanad_fixture_ci` role has BYPASSRLS and is used in CI for test fixture setup and cleanup. This role must NEVER be used in production. The role password is generated per-run using `github.run_id` and is not persisted.

### R3: Deferred P0 Security Debt
`CD-00-P0-001` (historical admin password) and `CD-00-P0-002` (historical email-proxy fallback) remain BLOCKED and block production release. These require owner access for credential rotation and are outside the scope of Stage 05.
