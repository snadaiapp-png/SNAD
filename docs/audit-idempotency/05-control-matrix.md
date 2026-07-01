# Stage 05 — Control Matrix

## Audit Integrity Controls

| Control | Implementation | Test Class | Status |
|---------|---------------|------------|--------|
| AUDIT APPEND-ONLY | V23 triggers block UPDATE/DELETE/TRUNCATE on audit_events | AuditAppendOnlyIntegrationTest | IMPLEMENTED |
| AUDIT UPDATE DENIED | BEFORE UPDATE trigger raises check_violation | AuditAppendOnlyIntegrationTest.auditUpdate_rejected | IMPLEMENTED |
| AUDIT DELETE DENIED | BEFORE DELETE trigger raises check_violation | AuditAppendOnlyIntegrationTest.auditDelete_rejected | IMPLEMENTED |
| AUDIT TRUNCATE DENIED | BEFORE TRUNCATE trigger raises check_violation | AuditAppendOnlyIntegrationTest.auditTruncate_rejected | IMPLEMENTED |
| AUDIT TENANT RLS | V23 ENABLE+FORCE RLS + tenant_isolation policy | AuditRlsIntegrationTest | IMPLEMENTED |
| AUDIT ACTOR ATTRIBUTION | AuditService extracts from TenantContext (not request body) | AuditActorAttributionIntegrationTest | IMPLEMENTED |
| AUDIT REQUEST/CORRELATION | AuditService extracts from MDC (RequestIdFilter) | AuditActorAttributionIntegrationTest | IMPLEMENTED |
| AUDIT REDACTION | AuditRedactionService recursively redacts sensitive JSON fields | AuditRedactionIntegrationTest | IMPLEMENTED |
| AUDIT HASH CHAIN | AuditHashChainService computes SHA-256(previousHash + canonical + tenantId + occurredAt) | AuditHashChainIntegrationTest | IMPLEMENTED |
| AUDIT TAMPER DETECTION | AuditIntegrityVerificationService walks chain, recomputes hashes | AuditHashChainIntegrationTest.hashChain_detectsTampering | IMPLEMENTED |
| AUDIT TRANSACTION BOUNDARY | AuditService.record joins caller's tx; recordDenied uses REQUIRES_NEW | AuditTransactionBoundaryIntegrationTest | IMPLEMENTED |

## Idempotency Controls

| Control | Implementation | Test Class | Status |
|---------|---------------|------------|--------|
| IDEMPOTENCY PERSISTENCE | IdempotencyRecord entity + V22 migration | IdempotencySameRequestReplayIntegrationTest | IMPLEMENTED |
| IDEMPOTENCY TENANT SCOPING | Unique constraint (tenant_id, operation, route, key) + RLS | IdempotencyCrossTenantIsolationIntegrationTest | IMPLEMENTED |
| IDEMPOTENCY REQUEST FINGERPRINT | RequestFingerprintService SHA-256(method+route+body+query+tenant+operation) | IdempotencyPayloadMismatchIntegrationTest | IMPLEMENTED |
| IDEMPOTENCY SAME-REQUEST REPLAY | IdempotencyService.reserveOrReplay returns REPLAY for COMPLETED | IdempotencySameRequestReplayIntegrationTest | IMPLEMENTED |
| IDEMPOTENCY PAYLOAD MISMATCH | Fingerprint comparison → 409 SANAD-IDEMP-002 | IdempotencyPayloadMismatchIntegrationTest | IMPLEMENTED |
| IDEMPOTENCY CONCURRENT EXECUTION | DB unique constraint serializes concurrent inserts | IdempotencyConcurrentExecutionIntegrationTest | IMPLEMENTED |
| IDEMPOTENCY CROSS-TENANT REPLAY DENIED | RLS prevents cross-tenant reads; tenant scope in unique constraint | IdempotencyCrossTenantIsolationIntegrationTest | IMPLEMENTED |
| IDEMPOTENCY FAILURE RECOVERY | FAILED_RETRYABLE allows re-execution; FAILED_FINAL blocks | IdempotencyFailureRecoveryIntegrationTest | IMPLEMENTED |
| IDEMPOTENCY RESPONSE REDACTION | Set-Cookie and Authorization stripped from stored response | IdempotencyResponseRedactionIntegrationTest | IMPLEMENTED |
| IDEMPOTENCY RLS | V23 ENABLE+FORCE RLS on idempotency_records | IdempotencyRlsIntegrationTest | IMPLEMENTED |
| IDEMPOTENCY EXPIRATION | expiresAt check in reserveOrReplay → EXPIRED result | IdempotencyExpirationIntegrationTest | IMPLEMENTED |

## CI/CD Controls

| Control | Implementation | Status |
|---------|---------------|--------|
| AUDIT-IDEMPOTENCY JOB | New CI job in quality-gate.yml running 16 test classes on PostgreSQL 16 | IMPLEMENTED |
| STATIC GATE | scripts/ci/check_audit_idempotency.py runs in repository-policy + audit-idempotency jobs | IMPLEMENTED |
| EVIDENCE ARTIFACT | audit-idempotency-evidence uploaded with surefire reports | IMPLEMENTED |
| DEBT REGISTER RECONCILIATION | scripts/ci/check_closure_debt_register.py runs in repository-policy + quality-gate | IMPLEMENTED |
