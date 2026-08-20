# G7 IMPLEMENTATION ENTRY CONTRACT

> **Report ID:** G7-IMPL-CONTRACT-V1
> **Date:** 2026-08-12
> **Status:** ACTIVE
> **Purpose:** Ordered implementation plan — 20 steps from foundation to verification

---

## 1. CONTRACT SUMMARY

```
╔══════════════════════════════════════════════════════════════╗
║ G7 IMPLEMENTATION ENTRY CONTRACT                            ║
║ STEPS = 20                                                  ║
║ REQUIREMENTS = 57 (approved) + 9 (deferred)                ║
║ FRAMEWORK = React Native (Expo Managed Workflow)            ║
║ ENCRYPTION = AES-256-GCM Hybrid                             ║
║ CONFLICT POLICY = Hybrid (ADR-G7-001)                      ║
╚══════════════════════════════════════════════════════════════╝
```

---

## 2. ORDERED IMPLEMENTATION STEPS

### STEP 1: Project Scaffolding
**Requirements:** — (infrastructure)
**Effort:** 1 day
**Description:** Initialize React Native Expo project, configure TypeScript, set up navigation, configure build profiles (EAS).

**Deliverables:**
- `apps/mobile/` directory with Expo project
- `app.json` configured for SNAD
- EAS build profiles (development, preview, production)
- TypeScript configuration
- Basic navigation structure

**Acceptance:**
- `npx expo start` runs without errors
- EAS build succeeds for iOS and Android

---

### STEP 2: Database Schema — Sync Metadata Tables
**Requirements:** DATA-001 (P0)
**Effort:** 2 days
**Description:** Create 4 sync metadata tables in PostgreSQL via Flyway migration.

**Deliverables:**
- `mobile_sync_cursor` table
- `mobile_sync_log` table
- `mobile_device_registry` table
- `mobile_conflict_log` table
- RLS policies for tenant isolation
- Indexes for query performance

**Acceptance:**
- Flyway migration runs successfully
- RLS policies enforce tenant isolation
- Tables match C3 schema definition

---

### STEP 3: Change Tracking Columns
**Requirements:** DATA-002 (P0)
**Effort:** 1 day
**Description:** Add change tracking columns to existing CRM entity tables.

**Deliverables:**
- `last_synced_at` column on Account, Contact, Lead, Opportunity, Task, Activity, Note
- `sync_version` column (bigint, auto-increment on update)
- Flyway migration script

**Acceptance:**
- Columns exist on all 7 entity types
- `sync_version` increments on UPDATE
- No breaking changes to existing APIs

---

### STEP 4: Mobile Auth Flow
**Requirements:** AUTH-001 (P0), SEC-002 (P1)
**Effort:** 3 days
**Description:** Implement mobile authentication using existing JWT + refresh token infrastructure.

**Deliverables:**
- Mobile login screen (email + password)
- JWT access token storage (expo-secure-store)
- Refresh token storage (expo-secure-store)
- Token refresh logic (7-day TTL)
- Biometric authentication (optional)
- Logout + token cleanup

**Acceptance:**
- GIVEN mobile client, WHEN valid credentials entered, THEN JWT + refresh token issued
- GIVEN expired access token, WHEN refresh attempted, THEN new access token issued
- GIVEN refresh token expired (7d), WHEN refresh attempted, THEN re-authentication required
- Tokens stored in Keychain/Keystore (not AsyncStorage)

---

### STEP 5: Offline Encryption Implementation
**Requirements:** SEC-001 (P0)
**Effort:** 2 days
**Description:** Implement AES-256-GCM field-level encryption for sensitive data.

**Deliverables:**
- Encryption utility (encrypt/decrypt functions)
- Key management (expo-secure-store)
- Sensitive field mapping per entity type
- Encryption middleware for sync operations

**Acceptance:**
- GIVEN sensitive field, WHEN stored locally, THEN encrypted with AES-256-GCM
- GIVEN encrypted field, WHEN read from local DB, THEN decrypted correctly
- GIVEN encrypted data, WHEN device is rooted, THEN data remains encrypted
- Key stored in Keychain/Keystore (not in code or config)

---

### STEP 6: Local Storage Schema (SQLite)
**Requirements:** DATA-003 (P1)
**Effort:** 2 days
**Description:** Design and implement local SQLite schema for offline data storage.

**Deliverables:**
- SQLite schema for 7 entity types
- Schema versioning mechanism
- Migration strategy (schema upgrades)
- Index strategy for query performance

**Acceptance:**
- All 7 entity types have local tables
- Schema version stored and checked on startup
- Migrations run automatically on version upgrade
- Query performance <50ms for local reads

---

### STEP 7: Sync Engine Core
**Requirements:** SYNC-001 (P0), SYNC-003 (P1)
**Effort:** 5 days
**Description:** Implement the core sync engine with mutation queue and conflict detection.

**Deliverables:**
- SyncEngine class (pull + push orchestration)
- MutationQueue (local queue with retry)
- Network connectivity detection
- Sync state machine (ONLINE/OFFLINE/REAUTH_REQUIRED/FULL_RESYNC)
- Sync status tracking

**Acceptance:**
- GIVEN device online, WHEN sync triggered, THEN pull + push execute
- GIVEN device offline, WHEN mutation created, THEN queued locally
- GIVEN device reconnects, WHEN sync triggered, THEN queued mutations pushed
- GIVEN sync in progress, WHEN network lost, THEN sync pauses gracefully

---

### STEP 8: Delta Sync Pull
**Requirements:** SYNC-002 (P0), API-003 (P0)
**Effort:** 3 days
**Description:** Implement delta sync pull endpoint and client logic.

**Deliverables:**
- Server endpoint: `GET /api/v2/mobile/sync/pull`
- Client: DeltaPull class
- Cursor management (Base64-URL encoded)
- If-Modified-Since header support
- Entity delta processing

**Acceptance:**
- GIVEN valid cursor, WHEN pull requested, THEN only changed entities returned
- GIVEN no changes, WHEN pull requested, THEN empty delta + same cursor
- GIVEN new cursor, WHEN stored locally, THEN next pull uses new cursor
- Response time <200ms for typical delta

---

### STEP 9: Batch Sync Push
**Requirements:** SYNC-017 (P0), API-004 (P0)
**Effort:** 3 days
**Description:** Implement batch sync push endpoint and client logic.

**Deliverables:**
- Server endpoint: `POST /api/v2/mobile/sync/push`
- Client: BatchPush class
- Per-mutation ACK (success/failure per mutation)
- ETag + If-Match concurrency control
- Idempotency key support

**Acceptance:**
- GIVEN batch of mutations, WHEN pushed, THEN per-mutation results returned
- GIVEN version match, WHEN mutation pushed, THEN APPLIED (200)
- GIVEN version mismatch, WHEN mutation pushed, THEN REJECTED (412) + conflict logged
- GIVEN duplicate idempotency_key, WHEN pushed, THEN DUPLICATE response

---

### STEP 10: Entity List & Detail APIs
**Requirements:** API-001 (P0), API-002 (P0)
**Effort:** 2 days
**Description:** Implement mobile-optimized entity list and detail endpoints.

**Deliverables:**
- `GET /api/v2/mobile/entity/{type}` (list with pagination)
- `GET /api/v2/mobile/entity/{type}/{id}` (detail)
- Reduced payload (mobile-appropriate fields)
- Cursor-based pagination

**Acceptance:**
- GIVEN mobile client, WHEN list requested, THEN paginated entities returned
- GIVEN entity ID, WHEN detail requested, THEN single entity with required fields
- Response time <200ms
- Payload <50% of full V2 response

---

### STEP 11: Entity Coverage & Sync Status
**Requirements:** SYNC-015 (P0), API-005 (P1)
**Effort:** 2 days
**Description:** Implement sync for all 7 entity types and sync status endpoint.

**Deliverables:**
- Sync support for Account, Contact, Lead, Opportunity, Task, Activity, Note
- `GET /api/v2/mobile/sync/status` endpoint
- Entity-type-specific sync logic

**Acceptance:**
- All 7 entity types sync correctly
- Sync status shows last sync time, pending mutations, conflicts

---

### STEP 12: 12 Conflict Classes
**Requirements:** ARCH-002 (P0)
**Effort:** 3 days
**Description:** Implement 12 conflict classes (C1-C12) per ADR-G7-001.

**Deliverables:**
- Conflict classifier (maps conflicts to C1-C12)
- Entity-specific conflict strategies (per ADR-G7-001 table)
- Conflict logging (mobile_conflict_log)

**Acceptance:**
- All 12 conflict types correctly classified
- Entity-specific strategies applied correctly
- Conflicts logged with full before/after payloads

---

### STEP 13: Conflict Resolution Logic
**Requirements:** SYNC-005 (P1), SYNC-006 (P1)
**Effort:** 3 days
**Description:** Implement conflict detection and resolution engine.

**Deliverables:**
- Conflict detection (version mismatch)
- Auto-merge for non-conflicting fields (Account, Contact, Task, Activity)
- User resolution for conflicting fields (Lead, Opportunity, Pipeline, Tags, Custom Fields)
- Push-only handling (Note)
- Conflict resolution state machine

**Acceptance:**
- GIVEN same-field conflict, WHEN detected, THEN user resolution required
- GIVEN different-field conflict, WHEN detected, THEN auto-merge non-conflicting fields
- GIVEN Lead/Opportunity conflict, WHEN detected, THEN user resolution always required
- GIVEN Note creation, WHEN pushed, THEN accepted without conflict (push-only)

---

### STEP 14: Conflict Isolation & Delete Conflicts
**Requirements:** SYNC-009 (P1), SYNC-010 (P1)
**Effort:** 2 days
**Description:** Implement conflict isolation and delete conflict handling.

**Deliverables:**
- Conflict isolation (conflicted entity doesn't block other syncs)
- Delete-vs-update conflict handling
- Conflict queue management

**Acceptance:**
- GIVEN conflict on Contact, WHEN syncing other entities, THEN other entities sync normally
- GIVEN delete-vs-update conflict, WHEN detected, THEN user must choose
- GIVEN resolved conflict, WHEN applied, THEN entity updated correctly

---

### STEP 15: Tenant Isolation & Security
**Requirements:** SEC-006 (P0), ISO-001 (P0), ISO-004 (P0), ISO-005 (P0)
**Effort:** 3 days
**Description:** Implement tenant isolation, cursor scoping, failure isolation, and network isolation.

**Deliverables:**
- RLS enforcement on all sync queries
- Tenant-scoped cursors (tenant hash validation)
- Failure isolation (batch failure doesn't cascade)
- Network isolation (offline queue independent of sync engine)

**Acceptance:**
- GIVEN cross-tenant sync attempt, WHEN detected, THEN REJECTED
- GIVEN tenant-scoped cursor, WHEN used, THEN only tenant data returned
- GIVEN batch mutation failure, WHEN occurred, THEN other mutations not affected
- GIVEN offline, WHEN mutations created, THEN queued independently of sync state

---

### STEP 16: Crash Recovery & Client Timeout
**Requirements:** SYNC-012 (P1), SYNC-014 (P1)
**Effort:** 2 days
**Description:** Implement crash recovery and client timeout handling.

**Deliverables:**
- Crash recovery (persistent local state)
- Client timeout (configurable sync timeout)
- Graceful degradation on timeout

**Acceptance:**
- GIVEN app crash, WHEN restarted, THEN local state preserved
- GIVEN sync timeout, WHEN occurred, THEN partial sync completed
- GIVEN network timeout, WHEN detected, THEN sync paused, not crashed

---

### STEP 17: Full Resync
**Requirements:** SYNC-011 (P1)
**Effort:** 2 days
**Description:** Implement full resync capability.

**Deliverables:**
- Full resync endpoint: `POST /api/v2/mobile/sync/full`
- Full resync client logic
- Local data purge + fresh download
- Resync trigger detection

**Acceptance:**
- GIVEN full resync requested, WHEN executed, THEN all entities downloaded fresh
- GIVEN stale cursor, WHEN sync attempted, THEN full resync triggered
- GIVEN re-authentication, WHEN completed, THEN full resync executed

---

### STEP 18: Testing
**Requirements:** TEST-001 (P1), TEST-002 (P1), TEST-003 (P1), TEST-007 (P0)
**Effort:** 5 days
**Description:** Implement unit, integration, E2E, and tenant isolation tests.

**Deliverables:**
- Unit tests for sync engine, conflict resolution, encryption
- Integration tests for API endpoints
- E2E tests for full offline→online cycle
- Tenant isolation security tests

**Acceptance:**
- Unit test coverage >80% for sync logic
- All integration tests pass
- E2E test: offline edit → reconnect → sync → conflict resolved
- Tenant isolation test: cross-tenant access rejected

---

### STEP 19: Observability
**Requirements:** OBS-001 (P1), OBS-002 (P1), OBS-003 (P1), OBS-004 (P1)
**Effort:** 2 days
**Description:** Implement sync metrics, error tracking, crash reporting, and sync alerts.

**Deliverables:**
- Sync metrics (success rate, latency, conflict rate)
- Error tracking (Sentry integration)
- Crash reporting (Sentry/Firebase Crashlytics)
- Sync alerts (conflict backlog, sync failures)

**Acceptance:**
- Sync metrics visible in monitoring dashboard
- Errors tracked with context
- Crashes reported with stack traces
- Alerts fire on sync failure threshold

---

### STEP 20: Documentation & Final Verification
**Requirements:** — (documentation)
**Effort:** 2 days
**Description:** Complete API documentation, user guide, and final verification.

**Deliverables:**
- API documentation (OpenAPI/Swagger)
- Mobile app user guide
- Architecture decision record updates
- Final test run verification

**Acceptance:**
- API docs complete and accurate
- User guide covers all sync workflows
- All tests pass
- No P0/P1 defects open

---

## 3. TOTAL EFFORT ESTIMATE

| Phase | Steps | Days |
|-------|-------|------|
| Foundation | 1-6 | 11 |
| Core Sync | 7-9 | 11 |
| Entity & Conflict | 10-14 | 15 |
| Security & Isolation | 15-17 | 7 |
| Testing & Observability | 18-20 | 9 |
| **TOTAL** | **20** | **53 days** |

**Estimated Duration: ~11 weeks (1 developer) or ~6 weeks (2 developers)**

---

## 4. DEPENDENCY CHAIN

```
Step 1 (Scaffolding)
  → Step 2 (DB Schema) → Step 3 (Change Tracking)
  → Step 4 (Auth) → Step 5 (Encryption)
  → Step 6 (Local Schema) → Step 7 (Sync Engine)
    → Step 8 (Delta Pull) → Step 9 (Batch Push)
    → Step 10 (Entity APIs) → Step 11 (Entity Coverage)
  → Step 12 (Conflict Classes) → Step 13 (Conflict Resolution)
    → Step 14 (Conflict Isolation) → Step 15 (Tenant Isolation)
    → Step 16 (Crash Recovery) → Step 17 (Full Resync)
  → Step 18 (Testing) → Step 19 (Observability) → Step 20 (Documentation)
```

---

## 5. GATE EXIT CRITERIA

| # | Criterion | Verification |
|---|-----------|-------------|
| 1 | All 20 steps complete | Step completion checklist |
| 2 | All 57 approved requirements implemented | Requirement traceability |
| 3 | All acceptance criteria passing | Test results |
| 4 | No P0/P1 defects | Bug tracker |
| 5 | Security review passed | Security audit |
| 6 | Performance targets met | Benchmark results |
| 7 | Documentation complete | Doc review |

---

*Generated: 2026-08-12*
*CONTRACT_STATUS = ACTIVE*
*IMPLEMENTATION_READY = YES*
