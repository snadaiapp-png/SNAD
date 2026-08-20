# G7 ACCEPTANCE CRITERIA REGISTER

> **Report ID:** G7-ACCEPT-V2
> **Date:** 2026-08-12
> **Status:** DEFINED
> **Purpose:** Explicit, testable acceptance criteria for all P0 and P1 requirements.

---

## 1. ACCEPTANCE CRITERIA FORMAT

Each criterion follows GIVEN/WHEN/THEN/EXPECTED/FAILURE format:
- **GIVEN:** Preconditions
- **WHEN:** Action/event
- **THEN:** Expected result
- **EXPECTED:** Measurable outcome
- **FAILURE CONDITION:** What constitutes failure

---

## 2. P0 ACCEPTANCE CRITERIA (18 requirements)

### G7-REQ-API-001: Entity List API
- **GIVEN:** Mobile client with valid JWT token
- **WHEN:** GET /api/v2/mobile/entity/{type} with pagination params
- **THEN:** Response contains paginated entity list with reduced payload
- **EXPECTED:** Response time <200ms, payload <50% of full V2 response, correct pagination
- **FAILURE CONDITION:** Response >200ms, full payload returned, pagination broken

### G7-REQ-API-002: Entity Detail API
- **GIVEN:** Mobile client with valid JWT token and entity ID
- **WHEN:** GET /api/v2/mobile/entity/{type}/{id}
- **THEN:** Response contains single entity with mobile-appropriate fields
- **EXPECTED:** Response time <200ms, all required fields present, no unnecessary fields
- **FAILURE CONDITION:** Response >200ms, missing required fields, extra fields included

### G7-REQ-API-003: Delta Sync Pull API
- **GIVEN:** Mobile client with valid cursor and JWT token
- **WHEN:** GET /api/v2/mobile/sync/pull with cursor parameter
- **THEN:** Response contains only entities changed since cursor, new cursor, has_more flag
- **EXPECTED:** Only changed entities returned, new cursor valid, pagination works
- **FAILURE CONDITION:** All entities returned (no delta), cursor invalid, has_more incorrect

### G7-REQ-API-004: Batch Sync Push API
- **GIVEN:** Mobile client with queued mutations and valid JWT token
- **WHEN:** POST /api/v2/mobile/sync/push with array of mutation envelopes
- **THEN:** Response contains per-mutation results (ACKNOWLEDGED/CONFLICT/RETRYABLE_FAILURE)
- **EXPECTED:** Each mutation independently processed, partial failure allowed, idempotent
- **FAILURE CONDITION:** Batch fails atomically (no partial results), idempotency broken

### G7-REQ-SYNC-001: Sync Engine
- **GIVEN:** Mobile client with local storage and network connectivity
- **WHEN:** Sync engine initiated
- **THEN:** Bidirectional data flow between local storage and server operational
- **EXPECTED:** Pull sync fetches changes, push sync sends mutations, queue processes FIFO
- **FAILURE CONDITION:** Engine doesn't start, one direction doesn't work, queue stuck

### G7-REQ-SYNC-002: Delta Pull
- **GIVEN:** Valid cursor from previous sync
- **WHEN:** Delta pull executed
- **THEN:** Only entities with updated_at > cursor timestamp returned
- **EXPECTED:** Delta is correct (no missed changes, no extra entities), new cursor valid
- **FAILURE CONDITION:** Missing changes, extra entities, cursor corruption

### G7-REQ-SYNC-015: Entity Coverage
- **GIVEN:** Sync engine operational
- **WHEN:** Any of 7 entity types (CONTACT, ACCOUNT, LEAD, OPPORTUNITY, TASK, ACTIVITY, NOTE) modified
- **THEN:** Modified entity synced to/from server
- **EXPECTED:** All 7 types support CREATE, UPDATE, DELETE, Pull, Push
- **FAILURE CONDITION:** Any entity type fails to sync

### G7-REQ-SYNC-017: Per-Mutation ACK
- **GIVEN:** Batch of mutations sent to server
- **WHEN:** Server processes batch
- **THEN:** Each mutation individually acknowledged or conflict-logged
- **EXPECTED:** ACKNOWLEDGED mutations removed from queue, CONFLICT mutations logged with details
- **FAILURE CONDITION:** Batch-level ACK only, no per-mutation detail, queue not cleaned

### G7-REQ-AUTH-001: Mobile Auth Flow
- **GIVEN:** Mobile client with no cached tokens
- **WHEN:** Auth flow initiated (login)
- **THEN:** Access token (15min) and refresh token (7d) cached securely
- **EXPECTED:** Tokens cached, auto-refresh on expiry, re-auth on refresh expiry
- **FAILURE CONDITION:** Tokens not cached, no auto-refresh, re-auth fails

### G7-REQ-DATA-001: Sync Tables
- **GIVEN:** Database migration executed
- **WHEN:** 4 sync tables queried
- **THEN:** Tables exist with correct schema and RLS policies
- **EXPECTED:** mobile_device_registry, mobile_sync_cursor, mobile_sync_log, mobile_conflict_log created, RLS enforced
- **FAILURE CONDITION:** Tables missing, schema incorrect, RLS not enforced

### G7-REQ-DATA-002: Change Tracking
- **GIVEN:** CRM entity table with version column
- **WHEN:** Entity updated
- **THEN:** version column incremented atomically, updated_at set
- **EXPECTED:** Version is BIGINT, monotonically increasing, updated_at is TIMESTAMP
- **FAILURE CONDITION:** Version not incremented, updated_at missing, non-atomic

### G7-REQ-SEC-001: Offline Encryption
- **GIVEN:** Mobile device with offline data
- **WHEN:** Device lost or stolen
- **THEN:** All local CRM data encrypted at rest
- **EXPECTED:** Data unreadable without decryption key, encryption verified
- **FAILURE CONDITION:** Data readable without key, encryption not applied

### G7-REQ-SEC-006: Tenant Isolation
- **GIVEN:** Two tenants (A and B) with sync tables
- **WHEN:** Tenant A attempts to use tenant B's cursor
- **THEN:** Operation blocked by RLS policy
- **EXPECTED:** Cross-tenant access denied, error returned, no data leakage
- **FAILURE CONDITION:** Cross-tenant access allowed, data leaked

### G7-REQ-ARCH-002: 12 Conflict Classes
- **GIVEN:** ADR-G7-001 approved, conflict detection operational
- **WHEN:** Conflict detected for any entity type
- **THEN:** Conflict classified into one of 12 defined classes (C1-C12)
- **EXPECTED:** Correct class assigned, resolution strategy applied per class
- **FAILURE CONDITION:** Unclassified conflict, wrong class, wrong resolution

### G7-REQ-TEST-007: Tenant Isolation Tests
- **GIVEN:** Sync tables with RLS policies
- **WHEN:** Test suite executed
- **THEN:** All cross-tenant access attempts denied
- **EXPECTED:** 100% of cross-tenant tests pass, no RLS bypass
- **FAILURE CONDITION:** Any cross-tenant test fails, RLS bypass found

### G7-REQ-ISO-001: Tenant-Scoped Cursors
- **GIVEN:** Cursor generated for tenant A
- **WHEN:** Cursor used for tenant B sync
- **THEN:** Cursor rejected, full resync triggered
- **EXPECTED:** No cross-tenant data accessible via wrong cursor
- **FAILURE CONDITION:** Wrong cursor accepted, cross-tenant data returned

### G7-REQ-ISO-004: Failure Isolation
- **GIVEN:** Batch of 10 mutations, 1 destined to fail
- **WHEN:** Batch processed
- **THEN:** 9 mutations succeed, 1 fails with error
- **EXPECTED:** Failed mutation logged, successful mutations ACK'd, queue cleaned for successes
- **FAILURE CONDITION:** All 10 fail, successful mutations not ACK'd

### G7-REQ-ISO-005: Network Isolation
- **GIVEN:** Push operation in progress, pull cursor valid
- **WHEN:** Push fails due to network error
- **THEN:** Pull cursor state unchanged
- **EXPECTED:** Next pull uses same cursor, push retried independently
- **FAILURE CONDITION:** Pull cursor corrupted by push failure

---

## 3. P1 ACCEPTANCE CRITERIA (35 requirements)

### G7-REQ-API-005: Sync Status API
- **GIVEN:** Mobile device with sync history
- **WHEN:** GET /api/v2/mobile/sync/status
- **THEN:** Current sync state returned (cursor position, last sync time, queue depth)
- **EXPECTED:** Accurate status for device, response <200ms
- **FAILURE CONDITION:** Incorrect status, stale data, slow response

### G7-REQ-API-007: Conflict List API
- **GIVEN:** Device with unresolved conflicts
- **WHEN:** GET /api/v2/mobile/conflicts
- **THEN:** List of unresolved conflicts returned
- **EXPECTED:** All conflicts listed with entity details, version info, timestamps
- **FAILURE CONDITION:** Missing conflicts, incorrect details

### G7-REQ-API-008: Conflict Resolve API
- **GIVEN:** Unresolved conflict for specific entity
- **WHEN:** POST /api/v2/mobile/conflicts/{id}/resolve with resolution
- **THEN:** Conflict resolved per resolution type
- **EXPECTED:** Auto-merge merges non-conflicting fields, server-wins overwrites, manual applies user choice
- **FAILURE CONDITION:** Resolution not applied, data inconsistency

### G7-REQ-API-009: Conflict Skip API
- **GIVEN:** Unresolved conflict
- **WHEN:** POST /api/v2/mobile/conflicts/{id}/skip
- **THEN:** Conflict marked as deferred
- **EXPECTED:** Conflict remains in queue, sync continues, conflict reappears on next sync
- **FAILURE CONDITION:** Conflict deleted, sync blocked

### G7-REQ-SYNC-003: Mutation Queue
- **GIVEN:** Offline mutations created
- **WHEN:** Mutations queued
- **THEN:** FIFO order per entity type with sequence numbers
- **EXPECTED:** Sequence monotonically increasing, no gaps (unless gap detection enabled)
- **FAILURE CONDITION:** Out-of-order processing, duplicate sequence numbers

### G7-REQ-SYNC-004: Cursor Invalidation
- **GIVEN:** Valid cursor from previous sync
- **WHEN:** Schema change, token expiry, or explicit request
- **THEN:** Cursor invalidated, full resync triggered
- **EXPECTED:** All entities re-fetched, new cursor valid
- **FAILURE CONDITION:** Stale data served after invalidation

### G7-REQ-SYNC-005: Conflict Detection
- **GIVEN:** Server entity version = 5, client base_version = 4
- **WHEN:** Client pushes mutation with base_version = 4
- **THEN:** Conflict detected (version mismatch)
- **EXPECTED:** HTTP 412 returned with conflict details
- **FAILURE CONDITION:** Mutation silently applied (version mismatch ignored)

### G7-REQ-SYNC-006: Conflict Resolution
- **GIVEN:** Conflict detected for Account entity
- **WHEN:** Auto-merge attempted
- **THEN:** Non-conflicting fields merged, conflicting fields use server value
- **EXPECTED:** Merged entity is consistent, no data loss
- **FAILURE CONDITION:** Data loss, inconsistent merge

### G7-REQ-SYNC-008: Idempotency
- **GIVEN:** Mutation with idempotency_key sent twice
- **WHEN:** Second push received
- **THEN:** Second push returns same result as first (not reprocessed)
- **EXPECTED:** Idempotent response, no duplicate side effects
- **FAILURE CONDITION:** Mutation processed twice, duplicate data

### G7-REQ-SYNC-009: Conflict Isolation
- **GIVEN:** Batch of 10 mutations, 2 have conflicts
- **WHEN:** Batch processed
- **THEN:** 8 ACK'd, 2 logged as conflicts, batch not blocked
- **EXPECTED:** Per-mutation results, no batch-level failure
- **FAILURE CONDITION:** Batch rejected due to 2 conflicts

### G7-REQ-SYNC-010: Delete Conflicts
- **GIVEN:** Server has entity, client pushes UPDATE + DELETE conflict
- **WHEN:** Conflict processed
- **THEN:** Server wins (UPDATE applied, DELETE rejected)
- **EXPECTED:** Entity updated, not deleted
- **FAILURE CONDITION:** Entity deleted when it should be updated

### G7-REQ-SYNC-011: Full Resync
- **GIVEN:** Cursor invalidated
- **WHEN:** Full resync initiated
- **THEN:** Local cache cleared, cursor cleared, queue cleared, all entities pulled
- **EXPECTED:** Complete fresh data set, new cursor valid
- **FAILURE CONDITION:** Partial resync, stale data retained

### G7-REQ-SYNC-012: Crash Recovery
- **GIVEN:** Mutations in SENT state, app crashes
- **WHEN:** App restarts
- **THEN:** Queue reloaded, SENT mutations reset to READY
- **EXPECTED:** All SENT mutations retried, no data loss
- **FAILURE CONDITION:** Mutations lost, queue corrupted

### G7-REQ-SYNC-014: Client Timeout
- **GIVEN:** Server slow to respond
- **WHEN:** 30 seconds elapsed
- **THEN:** Client request times out
- **EXPECTED:** Timeout error returned, operation retryable
- **FAILURE CONDITION:** No timeout, indefinite hang

### G7-REQ-SYNC-016: Server Authority
- **GIVEN:** Server entity with state=ACTIVE, client pushes state=INACTIVE
- **WHEN:** Conflict detected
- **THEN:** Server value (ACTIVE) wins for state transitions
- **EXPECTED:** State remains ACTIVE, client notified
- **FAILURE CONDITION:** Client value overwrites server state

### G7-REQ-AUTH-002: Offline Token
- **GIVEN:** Mobile client with cached tokens, network lost
- **WHEN:** Mutations created offline
- **THEN:** Mutations queued, tokens retained in memory
- **EXPECTED:** On reconnect, tokens checked, re-auth if expired
- **FAILURE CONDITION:** Tokens lost offline, mutations cannot be sent on reconnect

### G7-REQ-OFF-001: Entity Subset
- **GIVEN:** 7 entity types defined
- **WHEN:** Entity subset configuration loaded
- **THEN:** Correct entities marked as available offline
- **EXPECTED:** Configuration matches product specification
- **FAILURE CONDITION:** Wrong entities available, missing entities

### G7-REQ-OFF-002: Eligibility Rules
- **GIVEN:** Entity subset defined
- **WHEN:** Eligibility rules applied per entity type
- **THEN:** Per-entity offline capabilities enforced (read/write/pull-only)
- **EXPECTED:** Rules match product specification
- **FAILURE CONDITION:** Wrong capabilities per entity

### G7-REQ-DATA-003: Local Storage Schema
- **GIVEN:** Framework selected
- **WHEN:** Local storage schema created
- **THEN:** Schema mirrors server entity models with local_version
- **EXPECTED:** All 7 entity types storable locally, local_version tracked
- **FAILURE CONDITION:** Schema mismatch, missing entity types

### G7-REQ-SEC-002: Token Caching
- **GIVEN:** Mobile client with valid tokens
- **WHEN:** Tokens cached
- **THEN:** Tokens stored securely, accessible for API calls
- **EXPECTED:** Auto-refresh before expiry, rotation on use
- **FAILURE CONDITION:** Tokens not cached, no auto-refresh

### G7-REQ-SEC-004: Offline Auth
- **GIVEN:** Mobile client with cached RBAC permissions
- **WHEN:** Mutation created offline
- **THEN:** Permissions validated locally before accepting mutation
- **EXPECTED:** Unauthorized mutations rejected offline
- **FAILURE CONDITION:** Unauthorized mutations accepted

### G7-REQ-SEC-005: Transport Security
- **GIVEN:** All sync API endpoints
- **WHEN:** API called
- **THEN:** HTTPS TLS 1.2+ enforced, HSTS headers present
- **EXPECTED:** No HTTP fallback, cert pinning recommended
- **FAILURE CONDITION:** HTTP allowed, TLS downgrade possible

### G7-REQ-PERF-001: Response Time <200ms
- **GIVEN:** Mobile API endpoint
- **WHEN:** Request made under normal load
- **THEN:** Response returned within 200ms
- **EXPECTED:** P95 < 200ms, P99 < 500ms
- **FAILURE CONDITION:** P95 > 200ms consistently

### G7-REQ-PERF-003: Network Detection
- **GIVEN:** Mobile device with variable connectivity
- **WHEN:** Network state changes
- **THEN:** Sync behavior adapts (pause push on offline, queue, resume on reconnect)
- **EXPECTED:** No data loss on network transitions
- **FAILURE CONDITION:** Mutations lost during network transitions

### G7-REQ-TEST-001: Unit Tests
- **GIVEN:** Sync engine components
- **WHEN:** Unit test suite executed
- **THEN:** All unit tests pass
- **EXPECTED:** >80% code coverage for sync components
- **FAILURE CONDITION:** Any unit test fails

### G7-REQ-TEST-002: Pull Sync Tests
- **GIVEN:** Pull sync implementation
- **WHEN:** Integration tests executed
- **THEN:** Pull sync scenarios verified (delta, pagination, cursor)
- **EXPECTED:** All pull sync tests pass
- **FAILURE CONDITION:** Any pull sync test fails

### G7-REQ-TEST-003: Push Sync Tests
- **GIVEN:** Push sync implementation
- **WHEN:** Integration tests executed
- **THEN:** Push sync scenarios verified (batch, idempotency, partial failure)
- **EXPECTED:** All push sync tests pass
- **FAILURE CONDITION:** Any push sync test fails

### G7-REQ-OBS-001: Sync Metrics
- **GIVEN:** Sync operations running
- **WHEN:** Metrics collected
- **THEN:** Pull/push counts, latency histograms, entity counts available
- **EXPECTED:** Metrics accurate, dashboards functional
- **FAILURE CONDITION:** Metrics missing or incorrect

### G7-REQ-OBS-002: Conflict Metrics
- **GIVEN:** Conflicts detected and resolved
- **WHEN:** Metrics collected
- **THEN:** Conflict counts, resolution latency, breakdown by type/entity available
- **EXPECTED:** Metrics accurate
- **FAILURE CONDITION:** Metrics missing

### G7-REQ-OBS-003: Queue Metrics
- **GIVEN:** Mutation queue processing
- **WHEN:** Metrics collected
- **THEN:** Queue depth, retry count, dead letter count, processing time available
- **EXPECTED:** Metrics accurate
- **FAILURE CONDITION:** Metrics missing

### G7-REQ-OBS-004: Error Metrics
- **GIVEN:** Sync errors occurring
- **WHEN:** Metrics collected
- **THEN:** Error count by type, timeout count, auth failure count available
- **EXPECTED:** Metrics accurate
- **FAILURE CONDITION:** Metrics missing

### G7-REQ-OBS-005: Alerting
- **GIVEN:** Metrics thresholds defined
- **WHEN:** Threshold exceeded
- **THEN:** Alert triggered (conflict rate >10%, queue depth, latency SLA, auth failures, tenant violations)
- **EXPECTED:** Alerts fire correctly, no false positives
- **FAILURE CONDITION:** Alert not fired, false positive

### G7-REQ-OBS-007: Structured Logging
- **GIVEN:** Sync operations executing
- **WHEN:** Operation completed
- **THEN:** Structured log entry written (operation, entity, device, duration, status, correlation ID)
- **EXPECTED:** All operations logged, correlation IDs traceable
- **FAILURE CONDITION:** Operations not logged, correlation IDs missing

### G7-REQ-ISO-002: Device-Scoped State
- **GIVEN:** Two devices for same user
- **WHEN:** Each device syncs independently
- **THEN:** Each device maintains own cursor and sync state
- **EXPECTED:** Device A changes don't affect Device B sync
- **FAILURE CONDITION:** Device states interfere

### G7-REQ-ISO-003: User-Device Binding
- **GIVEN:** Device bound to User A
- **WHEN:** User B logs in on same device
- **THEN:** Cache cleared, cursors invalidated, fresh sync for User B
- **EXPECTED:** No User A data accessible to User B
- **FAILURE CONDITION:** User A data persists after user switch

---

## 4. ACCEPTANCE CRITERIA COVERAGE

| Priority | Requirements | Criteria Defined | Coverage |
|----------|-------------|-----------------|----------|
| P0 | 18 | 18 | 100% |
| P1 | 35 | 35 | 100% |
| P2 | 13 | 0 | 0% (deferred) |
| **TOTAL** | **66** | **53** | **80.3%** |

---

*Generated: 2026-08-12*
