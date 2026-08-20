# G7 IMPLEMENTATION TRACEABILITY MATRIX

> **Report ID:** G7-TRACE-V1
> **Date:** 2026-08-12
> **Status:** ACTIVE
> **Purpose:** Every requirement maps to design → component → source files → DB/API changes → tests → acceptance gate → evidence

---

## TRACEABILITY RULES

1. No orphan requirements — every requirement has an implementation package
2. No implementation package without requirements
3. No source modification without an implementation package
4. Every source file traces back to at least one requirement

---

## LAYER 0-1: REPOSITORY + MOBILE FOUNDATION

| Requirement | Design | Component | Source Files | DB/API Changes | Tests | Acceptance Gate |
|------------|--------|-----------|-------------|----------------|-------|-----------------|
| — (infra) | Expo scaffold | apps/mobile/ | app.json, tsconfig, navigation | None | Build passes | STEP-1 gate |

## LAYER 2: SECURE LOCAL PERSISTENCE

| Requirement | Design | Component | Source Files | DB/API Changes | Tests | Acceptance Gate |
|------------|--------|-----------|-------------|----------------|-------|-----------------|
| DATA-001 | 4 sync tables | Flyway migration | V20260812_1__mobile_sync_*.sql | 4 tables + RLS | Migration test | GATE-04 |
| DATA-002 | Change tracking | Flyway migration | V20260812_2__add_sync_columns.sql | 7 columns | Column test | GATE-04 |
| SEC-001 | AES-256-GCM | EncryptionService | src/crypto/encryption.ts | None | Encrypt/decrypt test | GATE-07 |

## LAYER 3: DEVICE + SESSION FOUNDATION

| Requirement | Design | Component | Source Files | DB/API Changes | Tests | Acceptance Gate |
|------------|--------|-----------|-------------|----------------|-------|-----------------|
| AUTH-001 | JWT mobile auth | AuthService | src/auth/auth.ts, src/auth/token.ts | None | Auth flow test | GATE-07 |
| AUTH-002 | Token refresh | TokenService | src/auth/refresh.ts | None | Refresh test | GATE-07 |
| SEC-002 | Secure store | SecureStorage | src/storage/secure.ts | None | Keychain test | GATE-07 |
| SEC-004 | Offline auth | OfflineAuth | src/auth/offline.ts | None | Offline auth test | GATE-07 |
| ISO-003 | Device fingerprint | DeviceRegistry | src/device/fingerprint.ts | None | Fingerprint test | GATE-07 |

## LAYER 4: OFFLINE DATA LAYER

| Requirement | Design | Component | Source Files | DB/API Changes | Tests | Acceptance Gate |
|------------|--------|-----------|-------------|----------------|-------|-----------------|
| DATA-003 | SQLite schema | LocalDB | src/storage/schema.ts, src/storage/db.ts | SQLite tables | Schema test | GATE-06 |
| OFF-001 | Entity subset | EntityConfig | src/config/entities.ts | None | Config test | GATE-06 |

## LAYER 5: MUTATION QUEUE

| Requirement | Design | Component | Source Files | DB/API Changes | Tests | Acceptance Gate |
|------------|--------|-----------|-------------|----------------|-------|-----------------|
| SYNC-003 | Mutation queue | MutationQueue | src/queue/mutation-queue.ts, src/queue/state-machine.ts | SQLite queue table | Queue test | GATE-09 |

## LAYER 6: SYNC INFRASTRUCTURE

| Requirement | Design | Component | Source Files | DB/API Changes | Tests | Acceptance Gate |
|------------|--------|-----------|-------------|----------------|-------|-----------------|
| SYNC-001 | Sync engine | SyncEngine | src/sync/sync-engine.ts | None | Engine test | GATE-08 |
| SYNC-014 | Client timeout | SyncConfig | src/sync/config.ts | None | Timeout test | GATE-08 |

## LAYER 7: PULL / DELTA SYNCHRONIZATION

| Requirement | Design | Component | Source Files | DB/API Changes | Tests | Acceptance Gate |
|------------|--------|-----------|-------------|----------------|-------|-----------------|
| SYNC-002 | Delta pull | PullSync | src/sync/pull.ts | None | Pull test | GATE-08 |
| SYNC-004 | Cursor invalidation | CursorManager | src/sync/cursor.ts | None | Cursor test | GATE-08 |
| API-001 | Entity list API | PullSyncController | PullSyncController.java | Endpoint | API test | GATE-05 |
| API-002 | Entity detail API | PullSyncController | PullSyncController.java | Endpoint | API test | GATE-05 |
| API-003 | Delta sync pull API | PullSyncController | PullSyncController.java | Endpoint | API test | GATE-05 |

## LAYER 8: PUSH SYNCHRONIZATION

| Requirement | Design | Component | Source Files | DB/API Changes | Tests | Acceptance Gate |
|------------|--------|-----------|-------------|----------------|-------|-----------------|
| SYNC-017 | Per-mutation ACK | PushSync | src/sync/push.ts | None | ACK test | GATE-11 |
| SYNC-015 | Entity coverage | EntitySync | src/sync/entity-sync.ts | None | Coverage test | GATE-11 |
| ISO-004 | Failure isolation | BatchProcessor | src/sync/batch.ts | None | Isolation test | GATE-11 |
| ISO-005 | Network isolation | NetworkGuard | src/sync/network.ts | None | Network test | GATE-11 |
| API-004 | Batch sync push API | PushSyncController | PushSyncController.java | Endpoint | API test | GATE-05 |
| API-005 | Sync status API | SyncStatusController | SyncStatusController.java | Endpoint | API test | GATE-05 |

## LAYER 9: IDEMPOTENCY / RETRY / RECOVERY

| Requirement | Design | Component | Source Files | DB/API Changes | Tests | Acceptance Gate |
|------------|--------|-----------|-------------|----------------|-------|-----------------|
| SYNC-008 | Idempotency | IdempotencyGuard | src/sync/idempotency.ts | None | Idempotency test | GATE-10 |

## LAYER 10-11: CONFLICT DETECTION + RESOLUTION

| Requirement | Design | Component | Source Files | DB/API Changes | Tests | Acceptance Gate |
|------------|--------|-----------|-------------|----------------|-------|-----------------|
| ARCH-002 | 12 conflict classes | ConflictClassifier | src/conflict/classifier.ts | None | Classification test | GATE-12 |
| SYNC-005 | Conflict detection | ConflictDetector | src/conflict/detector.ts | None | Detection test | GATE-12 |
| SYNC-006 | Conflict resolution | ConflictResolver | src/conflict/resolver.ts | None | Resolution test | GATE-12 |
| SYNC-009 | Conflict isolation | ConflictIsolator | src/conflict/isolator.ts | None | Isolation test | GATE-12 |
| SYNC-016 | Server authority | ServerAuthority | src/conflict/server-authority.ts | None | Authority test | GATE-12 |
| ISO-002 | Multi-device | MultiDeviceHandler | src/conflict/multi-device.ts | None | Multi-device test | GATE-12 |
| API-007 | Conflict list API | ConflictController | ConflictController.java | Endpoint | API test | GATE-05 |
| API-008 | Conflict resolve API | ConflictController | ConflictController.java | Endpoint | API test | GATE-05 |
| API-009 | Conflict skip API | ConflictController | ConflictController.java | Endpoint | API test | GATE-05 |
| DATA-005 | Conflict log | ConflictLogger | src/conflict/logger.ts | mobile_conflict_log | Log test | GATE-12 |

## LAYER 12: DELETE CONFLICT HANDLING

| Requirement | Design | Component | Source Files | DB/API Changes | Tests | Acceptance Gate |
|------------|--------|-----------|-------------|----------------|-------|-----------------|
| SYNC-010 | Delete conflicts | DeleteHandler | src/conflict/delete-handler.ts | None | Delete test | GATE-12 |

## LAYER 13: ENTITY-SPECIFIC OFFLINE POLICIES

| Requirement | Design | Component | Source Files | DB/API Changes | Tests | Acceptance Gate |
|------------|--------|-----------|-------------|----------------|-------|-----------------|
| — (ADR) | Entity policies | PolicyEngine | src/conflict/policy-engine.ts | None | Policy test | GATE-12 |

## LAYER 14: SECURITY / ENCRYPTION HARDENING

| Requirement | Design | Component | Source Files | DB/API Changes | Tests | Acceptance Gate |
|------------|--------|-----------|-------------|----------------|-------|-----------------|
| SEC-006 | Tenant isolation on sync | TenantGuard | src/security/tenant-guard.ts | RLS policies | Isolation test | GATE-14 |
| ISO-001 | Tenant-scoped cursors | CursorCodec | src/sync/cursor.ts | None | Cursor test | GATE-14 |

## LAYER 15: OBSERVABILITY / TELEMETRY

| Requirement | Design | Component | Source Files | DB/API Changes | Tests | Acceptance Gate |
|------------|--------|-----------|-------------|----------------|-------|-----------------|
| OBS-001 | Sync metrics | MetricsCollector | src/obs/metrics.ts | None | Metrics test | GATE-15 |
| OBS-002 | Error tracking | ErrorTracker | src/obs/errors.ts | None | Error test | GATE-15 |
| OBS-003 | Crash reporting | CrashReporter | src/obs/crash.ts | None | Crash test | GATE-15 |
| OBS-004 | Sync alerts | AlertManager | src/obs/alerts.ts | None | Alert test | GATE-15 |
| OBS-005 | Conflict dashboards | DashboardService | src/obs/dashboard.ts | None | Dashboard test | GATE-15 |
| DATA-004 | Sync audit trail | AuditLogger | src/obs/audit.ts | None | Audit test | GATE-15 |

## LAYER 16-17: TESTING + ACCEPTANCE

| Requirement | Design | Component | Source Files | DB/API Changes | Tests | Acceptance Gate |
|------------|--------|-----------|-------------|----------------|-------|-----------------|
| TEST-001 | Unit tests | TestSuite | src/__tests__/ | None | All unit tests | GATE-16 |
| TEST-002 | Integration tests | TestSuite | src/__tests__/integration/ | None | All integration tests | GATE-16 |
| TEST-003 | E2E tests | TestSuite | src/__tests__/e2e/ | None | All E2E tests | GATE-16 |
| TEST-004 | Conflict tests | TestSuite | src/__tests__/conflict/ | None | All conflict tests | GATE-16 |
| TEST-005 | Sync tests | TestSuite | src/__tests__/sync/ | None | All sync tests | GATE-16 |
| TEST-007 | Tenant isolation tests | SecurityTest | src/__tests__/security/ | None | All security tests | GATE-14 |
| PERF-001 | Sync performance | PerfTest | src/__tests__/perf/ | None | Performance benchmarks | GATE-16 |

## FULL RESYNC + RECOVERY

| Requirement | Design | Component | Source Files | DB/API Changes | Tests | Acceptance Gate |
|------------|--------|-----------|-------------|----------------|-------|-----------------|
| SYNC-011 | Full resync | FullResync | src/sync/full-resync.ts | None | Resync test | GATE-17 |
| SYNC-012 | Crash recovery | CrashRecovery | src/sync/recovery.ts | None | Recovery test | GATE-17 |

---

## SUMMARY

| Layer | Requirements | Components | Source Files | DB Changes | API Endpoints | Tests |
|-------|-------------|-----------|-------------|------------|--------------|-------|
| 0-1 | 0 (infra) | 1 | 3 | 0 | 0 | 1 |
| 2 | 3 | 2 | 4 | 2 | 0 | 3 |
| 3 | 5 | 5 | 5 | 0 | 0 | 5 |
| 4 | 2 | 2 | 3 | 1 | 0 | 2 |
| 5 | 1 | 2 | 2 | 1 | 0 | 1 |
| 6 | 2 | 2 | 2 | 0 | 0 | 2 |
| 7 | 5 | 3 | 3 | 0 | 3 | 5 |
| 8 | 6 | 4 | 4 | 0 | 3 | 6 |
| 9 | 1 | 1 | 1 | 0 | 0 | 1 |
| 10-11 | 10 | 7 | 7 | 1 | 3 | 10 |
| 12 | 1 | 1 | 1 | 0 | 0 | 1 |
| 13 | 1 | 1 | 1 | 0 | 0 | 1 |
| 14 | 2 | 2 | 2 | 1 | 0 | 2 |
| 15 | 6 | 6 | 6 | 0 | 0 | 6 |
| 16-17 | 7 | 1 | 7 | 0 | 0 | 7 |
| **TOTAL** | **57** | **40** | **52** | **6** | **9** | **53** |

---

*Generated: 2026-08-12*
*TRACEABILITY = ACTIVE*
*ORPHAN_REQUIREMENTS = 0*
