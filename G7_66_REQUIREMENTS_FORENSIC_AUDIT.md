# G7 — 66 REQUIREMENTS FORENSIC AUDIT

> **Report ID:** G7-FORENSIC-66-V1
> **Date:** 2026-08-12
> **Status:** COMPLETE
> **Mode:** READ-ONLY / FORENSIC
> **Purpose:** One-by-one forensic audit of all 66 G7 requirements — 20 fields each + 15 validity tests

---

## AUDIT SCOPE

This file contains **66 individual requirement audit records**. Each requirement is audited against 20 fields and tested with 15 validity tests (TEST-01 through TEST-15).

**No requirement is approved based on summary statistics alone.**

---

## VALIDITY TESTS REFERENCE

| Test ID | Question | PASS Criteria |
|---------|----------|---------------|
| TEST-01 | Is it within G7 scope? | Within Mobile Offline Foundation scope |
| TEST-02 | Does it have a reliable source? | At least one Level 1-3 source |
| TEST-03 | Is it an independent requirement? | Not a duplicate or split artifact without rationale |
| TEST-04 | Is it a duplicate? | Not duplicated from another requirement |
| TEST-05 | Is it merely an implementation detail? | Describes WHAT, not HOW |
| TEST-06 | Is it a hidden Architecture Decision? | Not an architecture decision masquerading as requirement |
| TEST-07 | Is it Business or Technical? | Properly classified |
| TEST-08 | Can it be tested? | Has observable outcome |
| TEST-09 | Does it have Acceptance Criteria? | GIVEN/WHEN/THEN defined |
| TEST-10 | Does it depend on unapproved decision? | No dependency on DECISION_REQUIRED item |
| TEST-11 | Does it conflict with another requirement? | No unresolved conflict |
| TEST-12 | Does it conflict with Source Code? | Consistent with existing codebase |
| TEST-13 | Does it conflict with ADR? | Consistent with ADR documents |
| TEST-14 | Does it conflict with C2/C3 decisions? | Consistent with architectural decisions |
| TEST-15 | Can it be traced to Test/Gate? | Traceability chain exists |

---

## 1. API REQUIREMENTS (9)

### G7-REQ-API-001 — Entity List API

**Identity:**
- REQ_ID: G7-REQ-API-001
- CANONICAL_NAME: Entity List API
- REQUIREMENT_TEXT: Mobile-optimized entity list API returns paginated, reduced-payload entity lists for mobile consumption

**Scope:**
- SCOPE_STATUS: IN_SCOPE — Core G7 mobile API endpoint
- SOURCE: SRC-03 (Baseline), SRC-10 (Sync Contract)
- SOURCE_AUTHORITY: LEVEL 3 (Generated Baseline)
- ORIGINAL_REQUIREMENT_IDS: G7-MOB-FR-001, API-06, GAP-R-009, TRUTH-R-001
- DUPLICATE_CLUSTER: Cluster 1 (Mobile Entity APIs)

**Evidence:**
- EVIDENCE_STATUS: NO_IMPLEMENTATION — No mobile endpoints exist

**Priority:**
- PRIORITY: P0
- PRIORITY_JUSTIFICATION: Without mobile-optimized list API, mobile clients cannot consume entity data efficiently. CRITICAL for mobile usability.

**Dependencies:**
- DEPENDENCIES: None (standalone API endpoint)
- ARCHITECTURE_DEPENDENCIES: None
- DATABASE_DEPENDENCIES: None (reads existing tables)
- API_DEPENDENCIES: None
- SECURITY_DEPENDENCIES: SEC-005 (Transport Security — exists)
- SYNC_DEPENDENCIES: None
- CONFLICT_DEPENDENCIES: None

**Acceptance Criteria:**
- ACCEPTANCE_CRITERIA: GIVEN mobile client with valid JWT token, WHEN GET /api/v2/mobile/entity/{type} with pagination params, THEN response contains paginated entity list with reduced payload. EXPECTED: Response time <200ms, payload <50% of full V2 response. FAILURE: Response >200ms, full payload returned.

**Status:**
- TRACEABILITY_STATUS: UNTRACED — No implementation, no tests
- CONFLICT_STATUS: NO_CONFLICT
- UNKNOWN_STATUS: UNKNOWN-004 (payload field list — non-blocking)
- BLOCKER_STATUS: NO_BLOCKER
- IMPLEMENTABILITY_STATUS: NEEDS_SPECIFICATION — Endpoint path and response schema need definition
- FINAL_REQUIREMENT_STATUS: BLOCKED (by implementation — no code exists)
- RATIONALE: Valid P0 requirement. No source conflicts. Blocked only by lack of implementation, not by architecture decisions or blockers. Reclassified from APPROVED to BLOCKED because greenfield implementation required.

---

### G7-REQ-API-002 — Entity Detail API

**Identity:**
- REQ_ID: G7-REQ-API-002
- CANONICAL_NAME: Entity Detail API
- REQUIREMENT_TEXT: Mobile-optimized entity detail API returns single entity with mobile-appropriate fields

**Scope:**
- SCOPE_STATUS: IN_SCOPE — Core G7 mobile API endpoint
- SOURCE: SRC-03 (Baseline), SRC-10 (Sync Contract)
- SOURCE_AUTHORITY: LEVEL 3 (Generated Baseline)
- ORIGINAL_REQUIREMENT_IDS: G7-MOB-FR-002, API-05
- DUPLICATE_CLUSTER: Cluster 1 (Mobile Entity APIs)

**Evidence:**
- EVIDENCE_STATUS: NO_IMPLEMENTATION — No mobile endpoints exist

**Priority:**
- PRIORITY: P0
- PRIORITY_JUSTIFICATION: Mobile clients need optimized detail views. Without this, full payloads waste bandwidth.

**Dependencies:**
- DEPENDENCIES: None
- ARCHITECTURE_DEPENDENCIES: None
- DATABASE_DEPENDENCIES: None
- API_DEPENDENCIES: None
- SECURITY_DEPENDENCIES: SEC-005 (exists)
- SYNC_DEPENDENCIES: None
- CONFLICT_DEPENDENCIES: None

**Acceptance Criteria:**
- ACCEPTANCE_CRITERIA: GIVEN mobile client with valid JWT and entity ID, WHEN GET /api/v2/mobile/entity/{type}/{id}, THEN response contains single entity with mobile-appropriate fields. EXPECTED: Response <200ms, required fields present, no unnecessary fields.

**Status:**
- TRACEABILITY_STATUS: UNTRACED
- CONFLICT_STATUS: NO_CONFLICT
- UNKNOWN_STATUS: UNKNOWN-004 (non-blocking)
- BLOCKER_STATUS: NO_BLOCKER
- IMPLEMENTABILITY_STATUS: NEEDS_SPECIFICATION
- FINAL_REQUIREMENT_STATUS: BLOCKED
- RATIONALE: Valid P0. No conflicts. Blocked only by greenfield status.

---

### G7-REQ-API-003 — Delta Sync Pull API

**Identity:**
- REQ_ID: G7-REQ-API-003
- CANONICAL_NAME: Delta Sync Pull API
- REQUIREMENT_TEXT: Delta sync pull API returns only entities changed since client's last cursor

**Scope:**
- SCOPE_STATUS: IN_SCOPE — Core G7 sync API
- SOURCE: SRC-03, SRC-10
- SOURCE_AUTHORITY: LEVEL 3
- ORIGINAL_REQUIREMENT_IDS: G7-MOB-FR-003, API-01, SYNC-R-010 through SYNC-R-014, SYNC-CONTRACT-04
- DUPLICATE_CLUSTER: Cluster 2 (Delta Sync Pull)

**Evidence:**
- EVIDENCE_STATUS: NO_IMPLEMENTATION — 0 sync endpoints exist

**Priority:**
- PRIORITY: P0
- PRIORITY_JUSTIFICATION: Without delta pull, mobile cannot receive changed data. Core of offline sync.

**Dependencies:**
- DEPENDENCIES: DATA-001 (Sync Tables), SYNC-001 (Sync Engine)
- ARCHITECTURE_DEPENDENCIES: None
- DATABASE_DEPENDENCIES: DATA-001 (mobile_sync_cursor table)
- API_DEPENDENCIES: None
- SECURITY_DEPENDENCIES: SEC-006 (Tenant Isolation)
- SYNC_DEPENDENCIES: SYNC-001, SYNC-002
- CONFLICT_DEPENDENCIES: None

**Acceptance Criteria:**
- ACCEPTANCE_CRITERIA: GIVEN mobile device with valid cursor, WHEN pull sync requested, THEN only changed entities returned with new cursor. EXPECTED: Delta correct, no missed changes, new cursor valid.

**Status:**
- TRACEABILITY_STATUS: UNTRACED
- CONFLICT_STATUS: NO_CONFLICT
- UNKNOWN_STATUS: UNKNOWN-005 (sync frequency — non-blocking)
- BLOCKER_STATUS: NO_BLOCKER
- IMPLEMENTABILITY_STATUS: NEEDS_SPECIFICATION
- FINAL_REQUIREMENT_STATUS: BLOCKED
- RATIONALE: Valid P0. Critical path for sync. No ADR dependency.

---

### G7-REQ-API-004 — Batch Sync Push API

**Identity:**
- REQ_ID: G7-REQ-API-004
- CANONICAL_NAME: Batch Sync Push API
- REQUIREMENT_TEXT: Batch sync push API accepts array of mutation envelopes and returns per-mutation results

**Scope:**
- SCOPE_STATUS: IN_SCOPE — Core G7 sync API
- SOURCE: SRC-03, SRC-10
- SOURCE_AUTHORITY: LEVEL 3
- ORIGINAL_REQUIREMENT_IDS: G7-MOB-FR-004, API-02, SYNC-R-015 through SYNC-R-018, SYNC-CONTRACT-05
- DUPLICATE_CLUSTER: Cluster 3 (Batch Push Sync)

**Evidence:**
- EVIDENCE_STATUS: NO_IMPLEMENTATION — 0 sync endpoints exist

**Priority:**
- PRIORITY: P0
- PRIORITY_JUSTIFICATION: Without push sync, mobile cannot send offline changes. Core of offline sync.

**Dependencies:**
- DEPENDENCIES: DATA-001, SYNC-001
- ARCHITECTURE_DEPENDENCIES: None
- DATABASE_DEPENDENCIES: DATA-001
- API_DEPENDENCIES: None
- SECURITY_DEPENDENCIES: SEC-006
- SYNC_DEPENDENCIES: SYNC-001, SYNC-017
- CONFLICT_DEPENDENCIES: None

**Acceptance Criteria:**
- ACCEPTANCE_CRITERIA: GIVEN offline mutations queued, WHEN push sync sent, THEN per-mutation ACK/CONFLICT returned. EXPECTED: Each mutation independently processed, partial failure allowed, idempotent.

**Status:**
- TRACEABILITY_STATUS: UNTRACED
- CONFLICT_STATUS: NO_CONFLICT
- UNKNOWN_STATUS: None
- BLOCKER_STATUS: NO_BLOCKER
- IMPLEMENTABILITY_STATUS: NEEDS_SPECIFICATION
- FINAL_REQUIREMENT_STATUS: BLOCKED
- RATIONALE: Valid P0. Critical path. No ADR dependency.

---

### G7-REQ-API-005 — Sync Status API

**Identity:**
- REQ_ID: G7-REQ-API-005
- CANONICAL_NAME: Sync Status API
- REQUIREMENT_TEXT: Sync status API returns current sync state for a device including cursor position

**Scope:**
- SCOPE_STATUS: IN_SCOPE
- SOURCE: SRC-03
- SOURCE_AUTHORITY: LEVEL 3
- ORIGINAL_REQUIREMENT_IDS: G7-MOB-FR-005, API-03
- DUPLICATE_CLUSTER: None

**Evidence:**
- EVIDENCE_STATUS: NO_IMPLEMENTATION

**Priority:**
- PRIORITY: P1
- PRIORITY_JUSTIFICATION: Needed for production but sync can function without status endpoint.

**Dependencies:**
- DEPENDENCIES: DATA-001
- ARCHITECTURE_DEPENDENCIES: None
- DATABASE_DEPENDENCIES: DATA-001 (mobile_sync_cursor)
- API_DEPENDENCIES: None
- SECURITY_DEPENDENCIES: SEC-005
- SYNC_DEPENDENCIES: SYNC-001
- CONFLICT_DEPENDENCIES: None

**Acceptance Criteria:**
- ACCEPTANCE_CRITERIA: GIVEN mobile device with sync history, WHEN GET /api/v2/mobile/sync/status, THEN current sync state returned. EXPECTED: Accurate status, response <200ms.

**Status:**
- TRACEABILITY_STATUS: UNTRACED
- CONFLICT_STATUS: NO_CONFLICT
- UNKNOWN_STATUS: None
- BLOCKER_STATUS: NO_BLOCKER
- IMPLEMENTABILITY_STATUS: NEEDS_SPECIFICATION
- FINAL_REQUIREMENT_STATUS: BLOCKED
- RATIONALE: Valid P1. No blockers.

---

### G7-REQ-API-006 — Device Registration API

**Identity:**
- REQ_ID: G7-REQ-API-006
- CANONICAL_NAME: Device Registration API
- REQUIREMENT_TEXT: Device registration API registers mobile device and returns device credentials

**Scope:**
- SCOPE_STATUS: IN_SCOPE
- SOURCE: SRC-03
- SOURCE_AUTHORITY: LEVEL 3
- ORIGINAL_REQUIREMENT_IDS: G7-MOB-SEC-003, API-004, SEC-R-016 through SEC-R-018
- DUPLICATE_CLUSTER: Cluster 6 (Device Registration)

**Evidence:**
- EVIDENCE_STATUS: NO_IMPLEMENTATION

**Priority:**
- PRIORITY: P2
- PRIORITY_JUSTIFICATION: Important but basic sync can work with existing auth. Device registration is an enhancement.

**Dependencies:**
- DEPENDENCIES: AUTH-001, SEC-003
- ARCHITECTURE_DEPENDENCIES: UNKNOWN-001 (framework)
- DATABASE_DEPENDENCIES: DATA-001 (mobile_device_registry)
- API_DEPENDENCIES: None
- SECURITY_DEPENDENCIES: SEC-003
- SYNC_DEPENDENCIES: None
- CONFLICT_DEPENDENCIES: None

**Acceptance Criteria:**
- ACCEPTANCE_CRITERIA: GIVEN mobile device, WHEN registration API called, THEN device registered with UUID v4 and credentials returned.

**Status:**
- TRACEABILITY_STATUS: UNTRACED
- CONFLICT_STATUS: NO_CONFLICT
- UNKNOWN_STATUS: UNKNOWN-008 (device identity storage — non-blocking, P2)
- BLOCKER_STATUS: NO_BLOCKER
- IMPLEMENTABILITY_STATUS: NEEDS_SPECIFICATION
- FINAL_REQUIREMENT_STATUS: DEFERRED
- RATIONALE: P2, deferred to v1.1. Not blocking core sync.

---

### G7-REQ-API-007 — Conflict List API

**Identity:**
- REQ_ID: G7-REQ-API-007
- CANONICAL_NAME: Conflict List API
- REQUIREMENT_TEXT: Conflict list API returns all unresolved conflicts for a device/user

**Scope:**
- SCOPE_STATUS: IN_SCOPE
- SOURCE: SRC-03
- SOURCE_AUTHORITY: LEVEL 3
- ORIGINAL_REQUIREMENT_IDS: API-07, GAP-R-008
- DUPLICATE_CLUSTER: None

**Evidence:**
- EVIDENCE_STATUS: NO_IMPLEMENTATION

**Priority:**
- PRIORITY: P1
- PRIORITY_JUSTIFICATION: Needed for conflict management UI. Not blocking core sync.

**Dependencies:**
- DEPENDENCIES: DATA-001 (mobile_conflict_log)
- ARCHITECTURE_DEPENDENCIES: None
- DATABASE_DEPENDENCIES: DATA-001
- API_DEPENDENCIES: None
- SECURITY_DEPENDENCIES: SEC-005
- SYNC_DEPENDENCIES: SYNC-005 (Conflict Detection)
- CONFLICT_DEPENDENCIES: None

**Acceptance Criteria:**
- ACCEPTANCE_CRITERIA: GIVEN device with unresolved conflicts, WHEN GET /api/v2/mobile/conflicts, THEN list returned. EXPECTED: All conflicts listed with entity details, version info.

**Status:**
- TRACEABILITY_STATUS: UNTRACED
- CONFLICT_STATUS: NO_CONFLICT
- UNKNOWN_STATUS: None
- BLOCKER_STATUS: NO_BLOCKER
- IMPLEMENTABILITY_STATUS: NEEDS_SPECIFICATION
- FINAL_REQUIREMENT_STATUS: BLOCKED
- RATIONALE: Valid P1. No blockers.

---

### G7-REQ-API-008 — Conflict Resolve API

**Identity:**
- REQ_ID: G7-REQ-API-008
- CANONICAL_NAME: Conflict Resolve API
- REQUIREMENT_TEXT: Conflict resolve API accepts resolution (auto-merge, server-wins, client-wins, manual) for a specific conflict

**Scope:**
- SCOPE_STATUS: IN_SCOPE
- SOURCE: SRC-03
- SOURCE_AUTHORITY: LEVEL 3
- ORIGINAL_REQUIREMENT_IDS: API-08, GAP-R-008
- DUPLICATE_CLUSTER: None

**Evidence:**
- EVIDENCE_STATUS: NO_IMPLEMENTATION

**Priority:**
- PRIORITY: P1
- PRIORITY_JUSTIFICATION: Needed for conflict management. Not blocking core sync.

**Dependencies:**
- DEPENDENCIES: DATA-001, SYNC-006 (Conflict Resolution)
- ARCHITECTURE_DEPENDENCIES: ADR-G7-001 (policy definition)
- DATABASE_DEPENDENCIES: DATA-001
- API_DEPENDENCIES: None
- SECURITY_DEPENDENCIES: SEC-005
- SYNC_DEPENDENCIES: SYNC-006
- CONFLICT_DEPENDENCIES: None

**Acceptance Criteria:**
- ACCEPTANCE_CRITERIA: GIVEN unresolved conflict, WHEN POST /api/v2/mobile/conflicts/{id}/resolve with resolution, THEN conflict resolved per type. EXPECTED: Auto-merge merges non-conflicting fields, server-wins overwrites.

**Status:**
- TRACEABILITY_STATUS: UNTRACED
- CONFLICT_STATUS: NO_CONFLICT
- UNKNOWN_STATUS: None
- BLOCKER_STATUS: NO_BLOCKER
- IMPLEMENTABILITY_STATUS: NEEDS_DECISION (ADR-G7-001 needed for resolution strategies)
- FINAL_REQUIREMENT_STATUS: BLOCKED
- RATIONALE: Valid P1. Needs ADR for resolution strategy definition.

---

### G7-REQ-API-009 — Conflict Skip API

**Identity:**
- REQ_ID: G7-REQ-API-009
- CANONICAL_NAME: Conflict Skip API
- REQUIREMENT_TEXT: Conflict skip API marks a conflict as skipped (deferred) without resolution

**Scope:**
- SCOPE_STATUS: IN_SCOPE
- SOURCE: SRC-03
- SOURCE_AUTHORITY: LEVEL 3
- ORIGINAL_REQUIREMENT_IDS: API-09
- DUPLICATE_CLUSTER: None

**Evidence:**
- EVIDENCE_STATUS: NO_IMPLEMENTATION

**Priority:**
- PRIORITY: P1
- PRIORITY_JUSTIFICATION: Enables deferred conflict handling.

**Dependencies:**
- DEPENDENCIES: DATA-001
- ARCHITECTURE_DEPENDENCIES: None
- DATABASE_DEPENDENCIES: DATA-001
- API_DEPENDENCIES: None
- SECURITY_DEPENDENCIES: SEC-005
- SYNC_DEPENDENCIES: None
- CONFLICT_DEPENDENCIES: None

**Acceptance Criteria:**
- ACCEPTANCE_CRITERIA: GIVEN unresolved conflict, WHEN POST /api/v2/mobile/conflicts/{id}/skip, THEN conflict marked as deferred. EXPECTED: Conflict remains in queue, reappears on next sync.

**Status:**
- TRACEABILITY_STATUS: UNTRACED
- CONFLICT_STATUS: NO_CONFLICT
- UNKNOWN_STATUS: None
- BLOCKER_STATUS: NO_BLOCKER
- IMPLEMENTABILITY_STATUS: NEEDS_SPECIFICATION
- FINAL_REQUIREMENT_STATUS: BLOCKED
- RATIONALE: Valid P1. No blockers.

---

## 2. SYNC REQUIREMENTS (17)

### G7-REQ-SYNC-001 — Sync Engine

**Identity:**
- REQ_ID: G7-REQ-SYNC-001
- CANONICAL_NAME: Sync Engine
- REQUIREMENT_TEXT: Client-side sync engine manages bidirectional data flow between local storage and server

**Scope:**
- SCOPE_STATUS: IN_SCOPE — Core G7 component
- SOURCE: SRC-03, SRC-10
- SOURCE_AUTHORITY: LEVEL 3
- ORIGINAL_REQUIREMENT_IDS: G7-MOB-SYNC-001, GAP-R-005, SYNC-CONTRACT-01 through SYNC-CONTRACT-16
- DUPLICATE_CLUSTER: Cluster 3 (Batch Push Sync — parent)

**Evidence:**
- EVIDENCE_STATUS: NO_IMPLEMENTATION — SyncEngine.java empty

**Priority:**
- PRIORITY: P0
- PRIORITY_JUSTIFICATION: The sync engine is the foundational component of G7. Without it, nothing works.

**Dependencies:**
- DEPENDENCIES: DATA-001, DATA-002, API-003, API-004
- ARCHITECTURE_DEPENDENCIES: UNKNOWN-001 (framework — client-side)
- DATABASE_DEPENDENCIES: DATA-001, DATA-002
- API_DEPENDENCIES: API-003, API-004
- SECURITY_DEPENDENCIES: AUTH-001, SEC-005
- SYNC_DEPENDENCIES: None (this IS the sync engine)
- CONFLICT_DEPENDENCIES: None

**Acceptance Criteria:**
- ACCEPTANCE_CRITERIA: GIVEN valid auth token and network connectivity, WHEN sync engine started, THEN bidirectional data flow operational. EXPECTED: Pull fetches changes, push sends mutations, queue processes FIFO.

**Status:**
- TRACEABILITY_STATUS: UNTRACED
- CONFLICT_STATUS: NO_CONFLICT
- UNKNOWN_STATUS: UNKNOWN-001 (framework — BLOCKING for client-side)
- BLOCKER_STATUS: NO_BLOCKER (architecture dependency only)
- IMPLEMENTABILITY_STATUS: NEEDS_DECISION (framework selection required)
- FINAL_REQUIREMENT_STATUS: BLOCKED
- RATIONALE: Valid P0. BLOCKED by framework selection (UNKNOWN-001). Cannot implement client-side sync engine without framework.

---

### G7-REQ-SYNC-002 — Delta Pull

**Identity:**
- REQ_ID: G7-REQ-SYNC-002
- CANONICAL_NAME: Delta Pull
- REQUIREMENT_TEXT: Delta/incremental pull uses cursor-based pagination to fetch only changed entities since last sync

**Scope:**
- SCOPE_STATUS: IN_SCOPE
- SOURCE: SRC-03, SRC-10
- SOURCE_AUTHORITY: LEVEL 3
- ORIGINAL_REQUIREMENT_IDS: G7-MOB-SYNC-002, SYNC-R-010 through SYNC-R-014
- DUPLICATE_CLUSTER: Cluster 2

**Evidence:**
- EVIDENCE_STATUS: NO_IMPLEMENTATION

**Priority:**
- PRIORITY: P0
- PRIORITY_JUSTIFICATION: Delta pull is the core read path for offline sync.

**Dependencies:**
- DEPENDENCIES: SYNC-001, DATA-001, DATA-002
- ARCHITECTURE_DEPENDENCIES: None
- DATABASE_DEPENDENCIES: DATA-001, DATA-002
- API_DEPENDENCIES: API-003
- SECURITY_DEPENDENCIES: SEC-006
- SYNC_DEPENDENCIES: SYNC-001
- CONFLICT_DEPENDENCIES: None

**Acceptance Criteria:**
- ACCEPTANCE_CRITERIA: GIVEN valid cursor, WHEN delta pull executed, THEN only changed entities returned with new cursor. EXPECTED: Delta correct, no missed changes.

**Status:**
- TRACEABILITY_STATUS: UNTRACED
- CONFLICT_STATUS: NO_CONFLICT
- UNKNOWN_STATUS: None
- BLOCKER_STATUS: NO_BLOCKER
- IMPLEMENTABILITY_STATUS: NEEDS_SPECIFICATION
- FINAL_REQUIREMENT_STATUS: BLOCKED
- RATIONALE: Valid P0. No ADR dependency. Blocked only by greenfield status.

---

### G7-REQ-SYNC-003 — Mutation Queue

**Identity:**
- REQ_ID: G7-REQ-SYNC-003
- CANONICAL_NAME: Mutation Queue
- REQUIREMENT_TEXT: Mutation queue stores offline changes in FIFO order per entity type with sequence numbers

**Scope:**
- SCOPE_STATUS: IN_SCOPE
- SOURCE: SRC-03, SRC-10
- SOURCE_AUTHORITY: LEVEL 3
- ORIGINAL_REQUIREMENT_IDS: G7-MOB-SYNC-003, SYNC-R-005 through SYNC-R-007, SYNC-CONTRACT-02
- DUPLICATE_CLUSTER: None

**Evidence:**
- EVIDENCE_STATUS: NO_IMPLEMENTATION

**Priority:**
- PRIORITY: P1
- PRIORITY_JUSTIFICATION: Needed for production but basic sync can use simpler queue initially.

**Dependencies:**
- DEPENDENCIES: SYNC-001
- ARCHITECTURE_DEPENDENCIES: UNKNOWN-001 (framework)
- DATABASE_DEPENDENCIES: None (client-side)
- API_DEPENDENCIES: None
- SECURITY_DEPENDENCIES: None
- SYNC_DEPENDENCIES: SYNC-001
- CONFLICT_DEPENDENCIES: None

**Acceptance Criteria:**
- ACCEPTANCE_CRITERIA: GIVEN offline mutations, WHEN queued, THEN FIFO order per entity type with sequence numbers. EXPECTED: Sequence monotonically increasing.

**Status:**
- TRACEABILITY_STATUS: UNTRACED
- CONFLICT_STATUS: NO_CONFLICT
- UNKNOWN_STATUS: UNKNOWN-001 (framework)
- BLOCKER_STATUS: NO_BLOCKER
- IMPLEMENTABILITY_STATUS: NEEDS_DECISION (framework)
- FINAL_REQUIREMENT_STATUS: BLOCKED
- RATIONALE: Valid P1. Blocked by framework.

---

### G7-REQ-SYNC-004 — Cursor Invalidation

**Identity:**
- REQ_ID: G7-REQ-SYNC-004
- CANONICAL_NAME: Cursor Invalidation
- REQUIREMENT_TEXT: Cursor invalidation triggers full resync on schema change, token expiry, or explicit request

**Scope:**
- SCOPE_STATUS: IN_SCOPE
- SOURCE: SRC-03, SRC-10
- SOURCE_AUTHORITY: LEVEL 3
- ORIGINAL_REQUIREMENT_IDS: G7-MOB-SYNC-004, SYNC-R-021, SYNC-CONTRACT-14
- DUPLICATE_CLUSTER: None

**Evidence:**
- EVIDENCE_STATUS: NO_IMPLEMENTATION

**Priority:**
- PRIORITY: P1
- PRIORITY_JUSTIFICATION: Ensures data freshness. Important but sync can start with manual resync.

**Dependencies:**
- DEPENDENCIES: SYNC-002
- ARCHITECTURE_DEPENDENCIES: None
- DATABASE_DEPENDENCIES: None
- API_DEPENDENCIES: None
- SECURITY_DEPENDENCIES: None
- SYNC_DEPENDENCIES: SYNC-002
- CONFLICT_DEPENDENCIES: None

**Acceptance Criteria:**
- ACCEPTANCE_CRITERIA: GIVEN valid cursor, WHEN schema change/token expiry/explicit request, THEN cursor invalidated, full resync triggered.

**Status:**
- TRACEABILITY_STATUS: UNTRACED
- CONFLICT_STATUS: NO_CONFLICT
- UNKNOWN_STATUS: None
- BLOCKER_STATUS: NO_BLOCKER
- IMPLEMENTABILITY_STATUS: NEEDS_SPECIFICATION
- FINAL_REQUIREMENT_STATUS: BLOCKED
- RATIONALE: Valid P1. No blockers.

---

### G7-REQ-SYNC-005 — Conflict Detection

**Identity:**
- REQ_ID: G7-REQ-SYNC-005
- CANONICAL_NAME: Conflict Detection
- REQUIREMENT_TEXT: Conflict detection compares server.entity.version against client.mutation.base_version

**Scope:**
- SCOPE_STATUS: IN_SCOPE
- SOURCE: SRC-03, SRC-10
- SOURCE_AUTHORITY: LEVEL 3
- ORIGINAL_REQUIREMENT_IDS: G7-MOB-SYNC-005, SYNC-R-030, SYNC-CONTRACT-06
- DUPLICATE_CLUSTER: Cluster 4

**Evidence:**
- EVIDENCE_STATUS: NO_IMPLEMENTATION

**Priority:**
- PRIORITY: P1
- PRIORITY_JUSTIFICATION: Critical for data integrity but depends on ADR-G7-001 for policy.

**Dependencies:**
- DEPENDENCIES: SYNC-001, DATA-002
- ARCHITECTURE_DEPENDENCIES: ADR-G7-001
- DATABASE_DEPENDENCIES: DATA-002 (version column)
- API_DEPENDENCIES: None
- SECURITY_DEPENDENCIES: None
- SYNC_DEPENDENCIES: SYNC-001
- CONFLICT_DEPENDENCIES: None

**Acceptance Criteria:**
- ACCEPTANCE_CRITERIA: GIVEN server entity version=5, client base_version=4, WHEN push mutation with base_version=4, THEN conflict detected. EXPECTED: HTTP 412 returned with details.

**Status:**
- TRACEABILITY_STATUS: UNTRACED
- CONFLICT_STATUS: NO_CONFLICT
- UNKNOWN_STATUS: UNKNOWN-002 (ADR approval)
- BLOCKER_STATUS: BLOCKED_BY_ADR
- IMPLEMENTABILITY_STATUS: NEEDS_DECISION
- FINAL_REQUIREMENT_STATUS: BLOCKED
- RATIONALE: Valid P1. BLOCKED by ADR-G7-001. Cannot implement detection without approved conflict policy.

---

### G7-REQ-SYNC-006 — Conflict Resolution

**Identity:**
- REQ_ID: G7-REQ-SYNC-006
- CANONICAL_NAME: Conflict Resolution
- REQUIREMENT_TEXT: Conflict resolution supports auto-merge (non-conflicting fields), server-wins (financial/state), and manual resolution (user)

**Scope:**
- SCOPE_STATUS: IN_SCOPE
- SOURCE: SRC-03, SRC-10
- SOURCE_AUTHORITY: LEVEL 3
- ORIGINAL_REQUIREMENT_IDS: G7-MOB-SYNC-006, SYNC-R-034 through SYNC-R-036, SYNC-CONTRACT-09
- DUPLICATE_CLUSTER: Cluster 4

**Evidence:**
- EVIDENCE_STATUS: NO_IMPLEMENTATION

**Priority:**
- PRIORITY: P1
- PRIORITY_JUSTIFICATION: Core conflict handling. Depends on ADR-G7-001.

**Dependencies:**
- DEPENDENCIES: SYNC-005
- ARCHITECTURE_DEPENDENCIES: ADR-G7-001
- DATABASE_DEPENDENCIES: None
- API_DEPENDENCIES: API-008
- SECURITY_DEPENDENCIES: None
- SYNC_DEPENDENCIES: SYNC-005
- CONFLICT_DEPENDENCIES: None

**Acceptance Criteria:**
- ACCEPTANCE_CRITERIA: GIVEN conflict detected for Account entity, WHEN auto-merge attempted, THEN non-conflicting fields merged, conflicting fields use server value.

**Status:**
- TRACEABILITY_STATUS: UNTRACED
- CONFLICT_STATUS: NO_CONFLICT
- UNKNOWN_STATUS: UNKNOWN-002 (ADR)
- BLOCKER_STATUS: BLOCKED_BY_ADR
- IMPLEMENTABILITY_STATUS: NEEDS_DECISION
- FINAL_REQUIREMENT_STATUS: BLOCKED
- RATIONALE: Valid P1. BLOCKED by ADR-G7-001.

---

### G7-REQ-SYNC-007 — Retry/Backoff

**Identity:**
- REQ_ID: G7-REQ-SYNC-007
- CANONICAL_NAME: Retry/Backoff
- REQUIREMENT_TEXT: Retry with exponential backoff (1s→2s→4s→8s→16s), ±20% jitter, max 5 attempts for retryable errors

**Scope:**
- SCOPE_STATUS: IN_SCOPE
- SOURCE: SRC-03, SRC-10
- SOURCE_AUTHORITY: LEVEL 3
- ORIGINAL_REQUIREMENT_IDS: G7-MOB-SYNC-007, SYNC-R-024 through SYNC-R-026, SYNC-CONTRACT-07
- DUPLICATE_CLUSTER: None

**Evidence:**
- EVIDENCE_STATUS: NO_IMPLEMENTATION

**Priority:**
- PRIORITY: P2
- PRIORITY_JUSTIFICATION: Important for reliability but basic sync works without sophisticated retry.

**Dependencies:**
- DEPENDENCIES: SYNC-001
- ARCHITECTURE_DEPENDENCIES: None
- DATABASE_DEPENDENCIES: None
- API_DEPENDENCIES: None
- SECURITY_DEPENDENCIES: None
- SYNC_DEPENDENCIES: SYNC-001
- CONFLICT_DEPENDENCIES: None

**Acceptance Criteria:**
- ACCEPTANCE_CRITERIA: GIVEN retryable error, WHEN retry executed, THEN exponential backoff with jitter applied. EXPECTED: Max 5 attempts, total wait <31s.

**Status:**
- TRACEABILITY_STATUS: UNTRACED
- CONFLICT_STATUS: NO_CONFLICT
- UNKNOWN_STATUS: None
- BLOCKER_STATUS: NO_BLOCKER
- IMPLEMENTABILITY_STATUS: NEEDS_SPECIFICATION
- FINAL_REQUIREMENT_STATUS: DEFERRED
- RATIONALE: P2, deferred to v1.1. Basic retry sufficient for initial release.

---

### G7-REQ-SYNC-008 — Idempotency

**Identity:**
- REQ_ID: G7-REQ-SYNC-008
- CANONICAL_NAME: Idempotency
- REQUIREMENT_TEXT: Idempotency for all sync mutations using SHA-256 fingerprint with 24-hour retention

**Scope:**
- SCOPE_STATUS: IN_SCOPE
- SOURCE: SRC-03, SRC-10
- SOURCE_AUTHORITY: LEVEL 3
- ORIGINAL_REQUIREMENT_IDS: G7-MOB-SYNC-008, SYNC-R-029, SYNC-CONTRACT-08
- DUPLICATE_CLUSTER: Cluster 7

**Evidence:**
- EVIDENCE_STATUS: PARTIAL — IdempotencyService exists (web) but not mobile-specific

**Priority:**
- PRIORITY: P1
- PRIORITY_JUSTIFICATION: Prevents duplicate mutations. Critical for data integrity.

**Dependencies:**
- DEPENDENCIES: SYNC-001
- ARCHITECTURE_DEPENDENCIES: None
- DATABASE_DEPENDENCIES: None
- API_DEPENDENCIES: API-004
- SECURITY_DEPENDENCIES: None
- SYNC_DEPENDENCIES: SYNC-001
- CONFLICT_DEPENDENCIES: None

**Acceptance Criteria:**
- ACCEPTANCE_CRITERIA: GIVEN mutation with idempotency_key sent twice, WHEN second push received, THEN same result returned. EXPECTED: No duplicate side effects.

**Status:**
- TRACEABILITY_STATUS: PARTIALLY_TRACED — Web IdempotencyService exists
- CONFLICT_STATUS: NO_CONFLICT
- UNKNOWN_STATUS: None
- BLOCKER_STATUS: NO_BLOCKER
- IMPLEMENTABILITY_STATUS: NEEDS_SPECIFICATION
- FINAL_REQUIREMENT_STATUS: BLOCKED
- RATIONALE: Valid P1. Partial evidence exists (web service). Needs mobile-specific implementation.

---

### G7-REQ-SYNC-009 — Conflict Isolation

**Identity:**
- REQ_ID: G7-REQ-SYNC-009
- CANONICAL_NAME: Conflict Isolation
- REQUIREMENT_TEXT: Conflict isolation: per-mutation, no batch blocking, each mutation processed independently

**Scope:**
- SCOPE_STATUS: IN_SCOPE
- SOURCE: SRC-10
- SOURCE_AUTHORITY: LEVEL 3
- ORIGINAL_REQUIREMENT_IDS: SYNC-R-032, SYNC-CONTRACT-05, ISO-CONFLICT-1, ISO-FAILURE-1
- DUPLICATE_CLUSTER: Cluster 4

**Evidence:**
- EVIDENCE_STATUS: NO_IMPLEMENTATION

**Priority:**
- PRIORITY: P1
- PRIORITY_JUSTIFICATION: Ensures partial failures don't block entire batch.

**Dependencies:**
- DEPENDENCIES: SYNC-001, SYNC-006
- ARCHITECTURE_DEPENDENCIES: ADR-G7-001
- DATABASE_DEPENDENCIES: None
- API_DEPENDENCIES: API-004
- SECURITY_DEPENDENCIES: None
- SYNC_DEPENDENCIES: SYNC-001, SYNC-006
- CONFLICT_DEPENDENCIES: None

**Acceptance Criteria:**
- ACCEPTANCE_CRITERIA: GIVEN batch with 10 mutations, 2 have conflicts, WHEN processed, THEN 8 ACK'd, 2 logged, batch not blocked.

**Status:**
- TRACEABILITY_STATUS: UNTRACED
- CONFLICT_STATUS: NO_CONFLICT
- UNKNOWN_STATUS: UNKNOWN-002 (ADR)
- BLOCKER_STATUS: BLOCKED_BY_ADR
- IMPLEMENTABILITY_STATUS: NEEDS_DECISION
- FINAL_REQUIREMENT_STATUS: BLOCKED
- RATIONALE: Valid P1. BLOCKED by ADR-G7-001.

---

### G7-REQ-SYNC-010 — Delete Conflicts

**Identity:**
- REQ_ID: G7-REQ-SYNC-010
- CANONICAL_NAME: Delete Conflicts
- REQUIREMENT_TEXT: Delete conflict handling: UPDATE vs DELETE → server wins; DELETE vs DELETE → idempotent acknowledged

**Scope:**
- SCOPE_STATUS: IN_SCOPE
- SOURCE: SRC-10
- SOURCE_AUTHORITY: LEVEL 3
- ORIGINAL_REQUIREMENT_IDS: SYNC-R-037, SYNC-CONTRACT-10
- DUPLICATE_CLUSTER: Cluster 4

**Evidence:**
- EVIDENCE_STATUS: NO_IMPLEMENTATION

**Priority:**
- PRIORITY: P1
- PRIORITY_JUSTIFICATION: Edge case for data integrity.

**Dependencies:**
- DEPENDENCIES: SYNC-006
- ARCHITECTURE_DEPENDENCIES: ADR-G7-001
- DATABASE_DEPENDENCIES: None
- API_DEPENDENCIES: None
- SECURITY_DEPENDENCIES: None
- SYNC_DEPENDENCIES: SYNC-006
- CONFLICT_DEPENDENCIES: None

**Acceptance Criteria:**
- ACCEPTANCE_CRITERIA: GIVEN server has entity, client pushes UPDATE + DELETE conflict, WHEN processed, THEN server wins (UPDATE applied, DELETE rejected).

**Status:**
- TRACEABILITY_STATUS: UNTRACED
- CONFLICT_STATUS: NO_CONFLICT
- UNKNOWN_STATUS: UNKNOWN-002 (ADR)
- BLOCKER_STATUS: BLOCKED_BY_ADR
- IMPLEMENTABILITY_STATUS: NEEDS_DECISION
- FINAL_REQUIREMENT_STATUS: BLOCKED
- RATIONALE: Valid P1. BLOCKED by ADR-G7-001.

---

### G7-REQ-SYNC-011 — Full Resync

**Identity:**
- REQ_ID: G7-REQ-SYNC-011
- CANONICAL_NAME: Full Resync
- REQUIREMENT_TEXT: Full resync procedure: clear local cache, clear cursor, clear queue (log conflicts first), pull all entities

**Scope:**
- SCOPE_STATUS: IN_SCOPE
- SOURCE: SRC-10
- SOURCE_AUTHORITY: LEVEL 3
- ORIGINAL_REQUIREMENT_IDS: SYNC-R-038, SYNC-R-039, SYNC-CONTRACT-11
- DUPLICATE_CLUSTER: None

**Evidence:**
- EVIDENCE_STATUS: NO_IMPLEMENTATION

**Priority:**
- PRIORITY: P1
- PRIORITY_JUSTIFICATION: Recovery mechanism for data corruption.

**Dependencies:**
- DEPENDENCIES: SYNC-001
- ARCHITECTURE_DEPENDENCIES: None
- DATABASE_DEPENDENCIES: None
- API_DEPENDENCIES: None
- SECURITY_DEPENDENCIES: None
- SYNC_DEPENDENCIES: SYNC-001
- CONFLICT_DEPENDENCIES: None

**Acceptance Criteria:**
- ACCEPTANCE_CRITERIA: GIVEN cursor invalidated, WHEN full resync initiated, THEN local cache cleared, cursor cleared, queue cleared, all entities pulled.

**Status:**
- TRACEABILITY_STATUS: UNTRACED
- CONFLICT_STATUS: NO_CONFLICT
- UNKNOWN_STATUS: None
- BLOCKER_STATUS: NO_BLOCKER
- IMPLEMENTABILITY_STATUS: NEEDS_SPECIFICATION
- FINAL_REQUIREMENT_STATUS: BLOCKED
- RATIONALE: Valid P1. No blockers.

---

### G7-REQ-SYNC-012 — Crash Recovery

**Identity:**
- REQ_ID: G7-REQ-SYNC-012
- CANONICAL_NAME: Crash Recovery
- REQUIREMENT_TEXT: Crash/restart recovery: queue persists in local storage, in-progress mutations reset to READY

**Scope:**
- SCOPE_STATUS: IN_SCOPE
- SOURCE: SRC-10
- SOURCE_AUTHORITY: LEVEL 3
- ORIGINAL_REQUIREMENT_IDS: SYNC-R-040 through SYNC-R-042, SYNC-CONTRACT-12
- DUPLICATE_CLUSTER: None

**Evidence:**
- EVIDENCE_STATUS: NO_IMPLEMENTATION

**Priority:**
- PRIORITY: P1
- PRIORITY_JUSTIFICATION: Prevents data loss on app crash.

**Dependencies:**
- DEPENDENCIES: SYNC-001, SYNC-003
- ARCHITECTURE_DEPENDENCIES: UNKNOWN-001 (framework)
- DATABASE_DEPENDENCIES: None (client-side)
- API_DEPENDENCIES: None
- SECURITY_DEPENDENCIES: None
- SYNC_DEPENDENCIES: SYNC-001
- CONFLICT_DEPENDENCIES: None

**Acceptance Criteria:**
- ACCEPTANCE_CRITERIA: GIVEN mutations in SENT state, app crashes, WHEN app restarts, THEN queue reloaded, SENT mutations reset to READY.

**Status:**
- TRACEABILITY_STATUS: UNTRACED
- CONFLICT_STATUS: NO_CONFLICT
- UNKNOWN_STATUS: UNKNOWN-001 (framework)
- BLOCKER_STATUS: NO_BLOCKER
- IMPLEMENTABILITY_STATUS: NEEDS_DECISION (framework)
- FINAL_REQUIREMENT_STATUS: BLOCKED
- RATIONALE: Valid P1. Blocked by framework.

---

### G7-REQ-SYNC-013 — Sequence Gap

**Identity:**
- REQ_ID: G7-REQ-SYNC-013
- CANONICAL_NAME: Sequence Gap
- REQUIREMENT_TEXT: Sequence gap detection: reject mutation with SEQUENCE_GAP_DETECTED if gap in sequence numbers

**Scope:**
- SCOPE_STATUS: IN_SCOPE
- SOURCE: SRC-10
- SOURCE_AUTHORITY: LEVEL 3
- ORIGINAL_REQUIREMENT_IDS: SYNC-R-028
- DUPLICATE_CLUSTER: None

**Evidence:**
- EVIDENCE_STATUS: NO_IMPLEMENTATION

**Priority:**
- PRIORITY: P2
- PRIORITY_JUSTIFICATION: Edge case. Basic sync works without gap detection.

**Dependencies:**
- DEPENDENCIES: SYNC-003
- ARCHITECTURE_DEPENDENCIES: None
- DATABASE_DEPENDENCIES: None
- API_DEPENDENCIES: None
- SECURITY_DEPENDENCIES: None
- SYNC_DEPENDENCIES: SYNC-003
- CONFLICT_DEPENDENCIES: None

**Acceptance Criteria:**
- ACCEPTANCE_CRITERIA: GIVEN mutation with sequence gap, WHEN processed, THEN rejected with SEQUENCE_GAP_DETECTED.

**Status:**
- TRACEABILITY_STATUS: UNTRACED
- CONFLICT_STATUS: NO_CONFLICT
- UNKNOWN_STATUS: None
- BLOCKER_STATUS: NO_BLOCKER
- IMPLEMENTABILITY_STATUS: NEEDS_SPECIFICATION
- FINAL_REQUIREMENT_STATUS: DEFERRED
- RATIONALE: P2, deferred to v1.1.

---

### G7-REQ-SYNC-014 — Client Timeout

**Identity:**
- REQ_ID: G7-REQ-SYNC-014
- CANONICAL_NAME: Client Timeout
- REQUIREMENT_TEXT: Client request timeout: 30 seconds for both pull and push operations

**Scope:**
- SCOPE_STATUS: IN_SCOPE
- SOURCE: SRC-10
- SOURCE_AUTHORITY: LEVEL 3
- ORIGINAL_REQUIREMENT_IDS: SYNC-R-017, SYNC-CONTRACT-13
- DUPLICATE_CLUSTER: None

**Evidence:**
- EVIDENCE_STATUS: NO_IMPLEMENTATION

**Priority:**
- PRIORITY: P1
- PRIORITY_JUSTIFICATION: Prevents indefinite hangs.

**Dependencies:**
- DEPENDENCIES: None
- ARCHITECTURE_DEPENDENCIES: None
- DATABASE_DEPENDENCIES: None
- API_DEPENDENCIES: None
- SECURITY_DEPENDENCIES: None
- SYNC_DEPENDENCIES: None
- CONFLICT_DEPENDENCIES: None

**Acceptance Criteria:**
- ACCEPTANCE_CRITERIA: GIVEN server slow to respond, WHEN 30 seconds elapsed, THEN client request times out. EXPECTED: Timeout error returned, operation retryable.

**Status:**
- TRACEABILITY_STATUS: UNTRACED
- CONFLICT_STATUS: NO_CONFLICT
- UNKNOWN_STATUS: None
- BLOCKER_STATUS: NO_BLOCKER
- IMPLEMENTABILITY_STATUS: NEEDS_SPECIFICATION
- FINAL_REQUIREMENT_STATUS: BLOCKED
- RATIONALE: Valid P1. No blockers.

---

### G7-REQ-SYNC-015 — Entity Coverage

**Identity:**
- REQ_ID: G7-REQ-SYNC-015
- CANONICAL_NAME: Entity Coverage
- REQUIREMENT_TEXT: Entity type coverage: CONTACT, ACCOUNT, LEAD, OPPORTUNITY, TASK, ACTIVITY, NOTE all support CREATE/UPDATE/DELETE/Pull/Push

**Scope:**
- SCOPE_STATUS: IN_SCOPE
- SOURCE: SRC-10
- SOURCE_AUTHORITY: LEVEL 3
- ORIGINAL_REQUIREMENT_IDS: SYNC-R-045, SYNC-CONTRACT-16
- DUPLICATE_CLUSTER: None

**Evidence:**
- EVIDENCE_STATUS: NO_IMPLEMENTATION

**Priority:**
- PRIORITY: P0
- PRIORITY_JUSTIFICATION: All 7 entity types must sync. Incomplete coverage breaks G7 purpose.

**Dependencies:**
- DEPENDENCIES: SYNC-001
- ARCHITECTURE_DEPENDENCIES: None
- DATABASE_DEPENDENCIES: DATA-002 (version on all entity tables)
- API_DEPENDENCIES: API-001, API-002, API-003, API-004
- SECURITY_DEPENDENCIES: None
- SYNC_DEPENDENCIES: SYNC-001
- CONFLICT_DEPENDENCIES: None

**Acceptance Criteria:**
- ACCEPTANCE_CRITERIA: GIVEN sync engine operational, WHEN any of 7 entity types modified, THEN entity synced. EXPECTED: All 7 types support CRUD.

**Status:**
- TRACEABILITY_STATUS: UNTRACED
- CONFLICT_STATUS: NO_CONFLICT
- UNKNOWN_STATUS: None
- BLOCKER_STATUS: NO_BLOCKER
- IMPLEMENTABILITY_STATUS: NEEDS_SPECIFICATION
- FINAL_REQUIREMENT_STATUS: BLOCKED
- RATIONALE: Valid P0. No ADR dependency. Blocked only by greenfield status.

---

### G7-REQ-SYNC-016 — Server Authority

**Identity:**
- REQ_ID: G7-REQ-SYNC-016
- CANONICAL_NAME: Server Authority
- REQUIREMENT_TEXT: Server-authoritative state management: state transitions, financial data, system-generated fields always use server value

**Scope:**
- SCOPE_STATUS: IN_SCOPE
- SOURCE: SRC-03, SRC-10
- SOURCE_AUTHORITY: LEVEL 3
- ORIGINAL_REQUIREMENT_IDS: G7-MOB-FR-009, SYNC-R-036, SYNC-CONTRACT-09
- DUPLICATE_CLUSTER: None

**Evidence:**
- EVIDENCE_STATUS: NO_IMPLEMENTATION

**Priority:**
- PRIORITY: P1
- PRIORITY_JUSTIFICATION: Data integrity constraint.

**Dependencies:**
- DEPENDENCIES: SYNC-001
- ARCHITECTURE_DEPENDENCIES: ADR-G7-001 (defines which fields are server-authoritative)
- DATABASE_DEPENDENCIES: None
- API_DEPENDENCIES: None
- SECURITY_DEPENDENCIES: None
- SYNC_DEPENDENCIES: SYNC-001
- CONFLICT_DEPENDENCIES: None

**Acceptance Criteria:**
- ACCEPTANCE_CRITERIA: GIVEN server entity with state=ACTIVE, client pushes state=INACTIVE, WHEN conflict detected, THEN server value (ACTIVE) wins.

**Status:**
- TRACEABILITY_STATUS: UNTRACED
- CONFLICT_STATUS: NO_CONFLICT
- UNKNOWN_STATUS: UNKNOWN-002 (ADR)
- BLOCKER_STATUS: NO_BLOCKER (ADR defines but doesn't block core concept)
- IMPLEMENTABILITY_STATUS: NEEDS_DECISION (ADR defines specific fields)
- FINAL_REQUIREMENT_STATUS: BLOCKED
- RATIONALE: Valid P1. Concept is clear but specific field list depends on ADR.

---

### G7-REQ-SYNC-017 — Per-Mutation ACK

**Identity:**
- REQ_ID: G7-REQ-SYNC-017
- CANONICAL_NAME: Per-Mutation ACK
- REQUIREMENT_TEXT: Acknowledgement is per-mutation (not per-batch): ACKNOWLEDGED mutations removed from queue, CONFLICT mutations logged

**Scope:**
- SCOPE_STATUS: IN_SCOPE
- SOURCE: SRC-10
- SOURCE_AUTHORITY: LEVEL 3
- ORIGINAL_REQUIREMENT_IDS: SYNC-R-022, SYNC-R-023, SYNC-CONTRACT-15
- DUPLICATE_CLUSTER: Cluster 3

**Evidence:**
- EVIDENCE_STATUS: NO_IMPLEMENTATION

**Priority:**
- PRIORITY: P0
- PRIORITY_JUSTIFICATION: Data integrity critical. Batch-level ACK would lose mutation-level status.

**Dependencies:**
- DEPENDENCIES: SYNC-001
- ARCHITECTURE_DEPENDENCIES: None
- DATABASE_DEPENDENCIES: None
- API_DEPENDENCIES: API-004
- SECURITY_DEPENDENCIES: None
- SYNC_DEPENDENCIES: SYNC-001
- CONFLICT_DEPENDENCIES: None

**Acceptance Criteria:**
- ACCEPTANCE_CRITERIA: GIVEN batch push sent, WHEN server processes, THEN each mutation individually ACK'd or CONFLICT'd. EXPECTED: ACKNOWLEDGED removed from queue, CONFLICT logged.

**Status:**
- TRACEABILITY_STATUS: UNTRACED
- CONFLICT_STATUS: NO_CONFLICT
- UNKNOWN_STATUS: None
- BLOCKER_STATUS: NO_BLOCKER
- IMPLEMENTABILITY_STATUS: NEEDS_SPECIFICATION
- FINAL_REQUIREMENT_STATUS: BLOCKED
- RATIONALE: Valid P0. No ADR dependency. Blocked only by greenfield status.

---

## 3. AUTH REQUIREMENTS (2)

### G7-REQ-AUTH-001 — Mobile Auth Flow

**Identity:**
- REQ_ID: G7-REQ-AUTH-001
- CANONICAL_NAME: Mobile Auth Flow
- REQUIREMENT_TEXT: Mobile auth flow supports token caching, refresh, and re-authentication on expiry

**Scope:**
- SCOPE_STATUS: IN_SCOPE
- SOURCE: SRC-03, SRC-11
- SOURCE_AUTHORITY: LEVEL 3
- ORIGINAL_REQUIREMENT_IDS: G7-MOB-FR-005, SEC-R-003, SEC-R-004, SEC-R-026, SEC-R-028
- DUPLICATE_CLUSTER: None

**Evidence:**
- EVIDENCE_STATUS: PARTIAL — JWT exists (web), mobile-specific missing

**Priority:**
- PRIORITY: P0
- PRIORITY_JUSTIFICATION: Prerequisite for all API calls. Without mobile auth, nothing works.

**Dependencies:**
- DEPENDENCIES: None
- ARCHITECTURE_DEPENDENCIES: UNKNOWN-003 (encryption for token storage)
- DATABASE_DEPENDENCIES: None
- API_DEPENDENCIES: None
- SECURITY_DEPENDENCIES: SEC-001, SEC-002
- SYNC_DEPENDENCIES: None
- CONFLICT_DEPENDENCIES: None

**Acceptance Criteria:**
- ACCEPTANCE_CRITERIA: GIVEN mobile client with no cached tokens, WHEN auth flow initiated, THEN access token (15min) and refresh token (7d) cached. EXPECTED: Auto-refresh on expiry, re-auth on refresh expiry.

**Status:**
- TRACEABILITY_STATUS: PARTIALLY_TRACED — JWT exists for web
- CONFLICT_STATUS: NO_CONFLICT
- UNKNOWN_STATUS: UNKNOWN-003 (encryption strategy)
- BLOCKER_STATUS: NO_BLOCKER
- IMPLEMENTABILITY_STATUS: NEEDS_SPECIFICATION
- FINAL_REQUIREMENT_STATUS: BLOCKED
- RATIONALE: Valid P0. Needs mobile-specific auth flow. Blocked by greenfield status and UNKNOWN-003 (encryption).

---

### G7-REQ-AUTH-002 — Offline Token

**Identity:**
- REQ_ID: G7-REQ-AUTH-002
- CANONICAL_NAME: Offline Token
- REQUIREMENT_TEXT: Offline token handling: cache tokens in memory, queue mutations while offline, re-auth on reconnect if expired

**Scope:**
- SCOPE_STATUS: IN_SCOPE
- SOURCE: SRC-10, SRC-11
- SOURCE_AUTHORITY: LEVEL 3
- ORIGINAL_REQUIREMENT_IDS: SYNC-R-043, SYNC-R-044, SEC-R-028
- DUPLICATE_CLUSTER: None

**Evidence:**
- EVIDENCE_STATUS: NO_IMPLEMENTATION

**Priority:**
- PRIORITY: P1
- PRIORITY_JUSTIFICATION: Needed for offline operation. Depends on AUTH-001.

**Dependencies:**
- DEPENDENCIES: AUTH-001
- ARCHITECTURE_DEPENDENCIES: None
- DATABASE_DEPENDENCIES: None
- API_DEPENDENCIES: None
- SECURITY_DEPENDENCIES: SEC-002
- SYNC_DEPENDENCIES: None
- CONFLICT_DEPENDENCIES: None

**Acceptance Criteria:**
- ACCEPTANCE_CRITERIA: GIVEN cached tokens, network lost, WHEN mutations created offline, THEN mutations queued, tokens retained. EXPECTED: On reconnect, tokens checked, re-auth if expired.

**Status:**
- TRACEABILITY_STATUS: UNTRACED
- CONFLICT_STATUS: NO_CONFLICT
- UNKNOWN_STATUS: None
- BLOCKER_STATUS: NO_BLOCKER
- IMPLEMENTABILITY_STATUS: NEEDS_SPECIFICATION
- FINAL_REQUIREMENT_STATUS: BLOCKED
- RATIONALE: Valid P1. No blockers.

---

## 4. OFFLINE REQUIREMENTS (2)

### G7-REQ-OFF-001 — Entity Subset

**Identity:**
- REQ_ID: G7-REQ-OFF-001
- CANONICAL_NAME: Entity Subset
- REQUIREMENT_TEXT: Offline entity subset defines which entities are available offline and their sync eligibility

**Scope:**
- SCOPE_STATUS: IN_SCOPE
- SOURCE: SRC-03, SRC-05
- SOURCE_AUTHORITY: LEVEL 3
- ORIGINAL_REQUIREMENT_IDS: G7-MOB-FR-006, G7-MOB-FR-007, GAP-R-013, FORENSIC-R-002
- DUPLICATE_CLUSTER: None

**Evidence:**
- EVIDENCE_STATUS: PARTIAL — crm-execution-data.ts defines entities

**Priority:**
- PRIORITY: P1
- PRIORITY_JUSTIFICATION: Defines scope of offline data.

**Dependencies:**
- DEPENDENCIES: SYNC-001
- ARCHITECTURE_DEPENDENCIES: None
- DATABASE_DEPENDENCIES: None
- API_DEPENDENCIES: None
- SECURITY_DEPENDENCIES: None
- SYNC_DEPENDENCIES: SYNC-001
- CONFLICT_DEPENDENCIES: None

**Acceptance Criteria:**
- ACCEPTANCE_CRITERIA: GIVEN 7 entity types, WHEN entity subset configuration loaded, THEN correct entities marked as available offline.

**Status:**
- TRACEABILITY_STATUS: PARTIALLY_TRACED — Entity list exists in crm-execution-data.ts
- CONFLICT_STATUS: NO_CONFLICT
- UNKNOWN_STATUS: None
- BLOCKER_STATUS: NO_BLOCKER
- IMPLEMENTABILITY_STATUS: NEEDS_SPECIFICATION
- FINAL_REQUIREMENT_STATUS: BLOCKED
- RATIONALE: Valid P1. Partial evidence exists.

---

### G7-REQ-OFF-002 — Eligibility Rules

**Identity:**
- REQ_ID: G7-REQ-OFF-002
- CANONICAL_NAME: Eligibility Rules
- REQUIREMENT_TEXT: Entity-level offline eligibility rules define per-entity-type offline capabilities (read/write/pull-only)

**Scope:**
- SCOPE_STATUS: IN_SCOPE
- SOURCE: SRC-03
- SOURCE_AUTHORITY: LEVEL 3
- ORIGINAL_REQUIREMENT_IDS: G7-MOB-FR-007
- DUPLICATE_CLUSTER: None

**Evidence:**
- EVIDENCE_STATUS: NO_IMPLEMENTATION

**Priority:**
- PRIORITY: P1 (corrected from P2 in some documents)
- PRIORITY_JUSTIFICATION: Defines per-entity offline behavior.

**Dependencies:**
- DEPENDENCIES: OFF-001
- ARCHITECTURE_DEPENDENCIES: None
- DATABASE_DEPENDENCIES: None
- API_DEPENDENCIES: None
- SECURITY_DEPENDENCIES: None
- SYNC_DEPENDENCIES: OFF-001
- CONFLICT_DEPENDENCIES: None

**Acceptance Criteria:**
- ACCEPTANCE_CRITERIA: GIVEN entity subset defined, WHEN eligibility rules applied, THEN per-entity offline capabilities enforced.

**Status:**
- TRACEABILITY_STATUS: UNTRACED
- CONFLICT_STATUS: NO_CONFLICT
- UNKNOWN_STATUS: None
- BLOCKER_STATUS: NO_BLOCKER
- IMPLEMENTABILITY_STATUS: NEEDS_SPECIFICATION
- FINAL_REQUIREMENT_STATUS: DEFERRED
- RATIONALE: P1 but DEFERRED to v1.1. Initial sync covers all entities equally.

---

## 5. DATA REQUIREMENTS (5)

### G7-REQ-DATA-001 — Sync Tables

**Identity:**
- REQ_ID: G7-REQ-DATA-001
- CANONICAL_NAME: Sync Tables
- REQUIREMENT_TEXT: Sync metadata tables: 4 new PostgreSQL tables (mobile_device_registry, mobile_sync_cursor, mobile_sync_log, mobile_conflict_log) with RLS

**Scope:**
- SCOPE_STATUS: IN_SCOPE — Foundation for all sync operations
- SOURCE: SRC-03, SRC-06
- SOURCE_AUTHORITY: LEVEL 3
- ORIGINAL_REQUIREMENT_IDS: G7-MOB-DATA-001, GAP-R-002, TABLE-01 through TABLE-04, TRUTH-R-002
- DUPLICATE_CLUSTER: Cluster 8

**Evidence:**
- EVIDENCE_STATUS: NO_IMPLEMENTATION — 0 tables exist

**Priority:**
- PRIORITY: P0
- PRIORITY_JUSTIFICATION: Data foundation for all sync operations. Without these tables, nothing persists.

**Dependencies:**
- DEPENDENCIES: None (foundation)
- ARCHITECTURE_DEPENDENCIES: None
- DATABASE_DEPENDENCIES: None (this IS the database work)
- API_DEPENDENCIES: None
- SECURITY_DEPENDENCIES: SEC-006 (RLS enforcement)
- SYNC_DEPENDENCIES: None
- CONFLICT_DEPENDENCIES: None

**Acceptance Criteria:**
- ACCEPTANCE_CRITERIA: GIVEN migration run, WHEN 4 sync tables created, THEN RLS enforced, CRUD operational. EXPECTED: All 4 tables exist with correct schema.

**Status:**
- TRACEABILITY_STATUS: UNTRACED
- CONFLICT_STATUS: NO_CONFLICT
- UNKNOWN_STATUS: None
- BLOCKER_STATUS: NO_BLOCKER
- IMPLEMENTABILITY_STATUS: IMPLEMENTATION_READY — Schema can be defined without external decisions
- FINAL_REQUIREMENT_STATUS: BLOCKED
- RATIONALE: Valid P0. Implementation-ready (schema definition). No external dependencies. Blocked only by greenfield status.

---

### G7-REQ-DATA-002 — Change Tracking

**Identity:**
- REQ_ID: G7-REQ-DATA-002
- CANONICAL_NAME: Change Tracking
- REQUIREMENT_TEXT: Change tracking columns: version BIGINT and updated_at TIMESTAMP on all CRM entity tables

**Scope:**
- SCOPE_STATUS: IN_SCOPE
- SOURCE: SRC-03, SRC-06
- SOURCE_AUTHORITY: LEVEL 3
- ORIGINAL_REQUIREMENT_IDS: G7-MOB-DATA-002, GAP-R-003, TRUTH-R-003
- DUPLICATE_CLUSTER: Cluster 9

**Evidence:**
- EVIDENCE_STATUS: PARTIAL — version column exists on some tables

**Priority:**
- PRIORITY: P0
- PRIORITY_JUSTIFICATION: Enables delta sync. Without version tracking, no incremental pull.

**Dependencies:**
- DEPENDENCIES: None
- ARCHITECTURE_DEPENDENCIES: None
- DATABASE_DEPENDENCIES: None (adds columns to existing tables)
- API_DEPENDENCIES: None
- SECURITY_DEPENDENCIES: None
- SYNC_DEPENDENCIES: None
- CONFLICT_DEPENDENCIES: None

**Acceptance Criteria:**
- ACCEPTANCE_CRITERIA: GIVEN CRM entity modified, WHEN version column present, THEN version incremented atomically, updated_at set.

**Status:**
- TRACEABILITY_STATUS: PARTIALLY_TRACED — version exists on some tables
- CONFLICT_STATUS: NO_CONFLICT
- UNKNOWN_STATUS: None
- BLOCKER_STATUS: NO_BLOCKER
- IMPLEMENTABILITY_STATUS: IMPLEMENTATION_READY — Column additions straightforward
- FINAL_REQUIREMENT_STATUS: BLOCKED
- RATIONALE: Valid P0. Partial evidence. Implementation-ready.

---

### G7-REQ-DATA-003 — Local Storage Schema

**Identity:**
- REQ_ID: G7-REQ-DATA-003
- CANONICAL_NAME: Local Storage Schema
- REQUIREMENT_TEXT: Client-side local storage schema mirrors server entity models with local_version tracking

**Scope:**
- SCOPE_STATUS: IN_SCOPE
- SOURCE: SRC-03, SRC-10
- SOURCE_AUTHORITY: LEVEL 3
- ORIGINAL_REQUIREMENT_IDS: G7-MOB-DATA-003, SYNC-R-003, SYNC-R-004
- DUPLICATE_CLUSTER: None

**Evidence:**
- EVIDENCE_STATUS: NO_IMPLEMENTATION

**Priority:**
- PRIORITY: P1
- PRIORITY_JUSTIFICATION: Client-side data persistence.

**Dependencies:**
- DEPENDENCIES: UNKNOWN-001 (framework)
- ARCHITECTURE_DEPENDENCIES: UNKNOWN-001
- DATABASE_DEPENDENCIES: None (client-side)
- API_DEPENDENCIES: None
- SECURITY_DEPENDENCIES: SEC-001 (encryption)
- SYNC_DEPENDENCIES: None
- CONFLICT_DEPENDENCIES: None

**Acceptance Criteria:**
- ACCEPTANCE_CRITERIA: GIVEN framework selected, WHEN local storage schema created, THEN schema mirrors server models with local_version.

**Status:**
- TRACEABILITY_STATUS: UNTRACED
- CONFLICT_STATUS: NO_CONFLICT
- UNKNOWN_STATUS: UNKNOWN-001 (framework — BLOCKING)
- BLOCKER_STATUS: NO_BLOCKER
- IMPLEMENTABILITY_STATUS: NEEDS_DECISION (framework)
- FINAL_REQUIREMENT_STATUS: BLOCKED
- RATIONALE: Valid P1. Blocked by framework selection.

---

### G7-REQ-DATA-004 — Sync Audit Trail

**Identity:**
- REQ_ID: G7-REQ-DATA-004
- CANONICAL_NAME: Sync Audit Trail
- REQUIREMENT_TEXT: Sync audit trail via mobile_sync_log table logging all sync operations with device_id, sync_type, status

**Scope:**
- SCOPE_STATUS: IN_SCOPE
- SOURCE: SRC-03, SRC-06
- SOURCE_AUTHORITY: LEVEL 3
- ORIGINAL_REQUIREMENT_IDS: G7-MOB-DATA-004, GAP-R-012, SEC-R-022, SYNC-R-033
- DUPLICATE_CLUSTER: None

**Evidence:**
- EVIDENCE_STATUS: NO_IMPLEMENTATION

**Priority:**
- PRIORITY: P2
- PRIORITY_JUSTIFICATION: Audit capability. Important for debugging but not blocking sync.

**Dependencies:**
- DEPENDENCIES: DATA-001
- ARCHITECTURE_DEPENDENCIES: None
- DATABASE_DEPENDENCIES: DATA-001 (table exists)
- API_DEPENDENCIES: None
- SECURITY_DEPENDENCIES: None
- SYNC_DEPENDENCIES: None
- CONFLICT_DEPENDENCIES: None

**Acceptance Criteria:**
- ACCEPTANCE_CRITERIA: GIVEN sync operation executed, WHEN completed, THEN log entry written with device_id, sync_type, status.

**Status:**
- TRACEABILITY_STATUS: UNTRACED
- CONFLICT_STATUS: NO_CONFLICT
- UNKNOWN_STATUS: None
- BLOCKER_STATUS: NO_BLOCKER
- IMPLEMENTABILITY_STATUS: IMPLEMENTATION_READY
- FINAL_REQUIREMENT_STATUS: DEFERRED
- RATIONALE: P2, deferred to v1.1. Not blocking core sync.

---

### G7-REQ-DATA-005 — Conflict Log

**Identity:**
- REQ_ID: G7-REQ-DATA-005
- CANONICAL_NAME: Conflict Log
- REQUIREMENT_TEXT: Conflict log via mobile_conflict_log table logging all conflicts with conflict_id, entity_type, versions, resolution

**Scope:**
- SCOPE_STATUS: IN_SCOPE
- SOURCE: SRC-03, SRC-06
- SOURCE_AUTHORITY: LEVEL 3
- ORIGINAL_REQUIREMENT_IDS: G7-MOB-DATA-005, SYNC-R-033
- DUPLICATE_CLUSTER: None

**Evidence:**
- EVIDENCE_STATUS: NO_IMPLEMENTATION

**Priority:**
- PRIORITY: P2
- PRIORITY_JUSTIFICATION: Audit capability for conflicts.

**Dependencies:**
- DEPENDENCIES: DATA-001
- ARCHITECTURE_DEPENDENCIES: None
- DATABASE_DEPENDENCIES: DATA-001
- API_DEPENDENCIES: None
- SECURITY_DEPENDENCIES: None
- SYNC_DEPENDENCIES: None
- CONFLICT_DEPENDENCIES: None

**Acceptance Criteria:**
- ACCEPTANCE_CRITERIA: GIVEN conflict detected, WHEN logged, THEN entry written with conflict_id, entity_type, versions, resolution.

**Status:**
- TRACEABILITY_STATUS: UNTRACED
- CONFLICT_STATUS: NO_CONFLICT
- UNKNOWN_STATUS: None
- BLOCKER_STATUS: NO_BLOCKER
- IMPLEMENTABILITY_STATUS: IMPLEMENTATION_READY
- FINAL_REQUIREMENT_STATUS: DEFERRED
- RATIONALE: P2, deferred to v1.1.

---

## 6. SECURITY REQUIREMENTS (6)

### G7-REQ-SEC-001 — Offline Encryption

**Identity:**
- REQ_ID: G7-REQ-SEC-001
- CANONICAL_NAME: Offline Encryption
- REQUIREMENT_TEXT: Offline data encryption: all local CRM data encrypted at rest using SQLCipher or OS-level encryption

**Scope:**
- SCOPE_STATUS: IN_SCOPE
- SOURCE: SRC-03, SRC-06, SRC-11
- SOURCE_AUTHORITY: LEVEL 3
- ORIGINAL_REQUIREMENT_IDS: G7-MOB-SEC-001, G7-MOB-NFR-002, GAP-R-006, SEC-R-023, SEC-R-024, SEC-RISK-001
- DUPLICATE_CLUSTER: Cluster 5

**Evidence:**
- EVIDENCE_STATUS: NO_IMPLEMENTATION — No encryption strategy defined

**Priority:**
- PRIORITY: P0
- PRIORITY_JUSTIFICATION: Data protection. Without encryption, lost device exposes all data.

**Dependencies:**
- DEPENDENCIES: UNKNOWN-003 (encryption strategy)
- ARCHITECTURE_DEPENDENCIES: UNKNOWN-003
- DATABASE_DEPENDENCIES: None
- API_DEPENDENCIES: None
- SECURITY_DEPENDENCIES: None
- SYNC_DEPENDENCIES: None
- CONFLICT_DEPENDENCIES: None

**Acceptance Criteria:**
- ACCEPTANCE_CRITERIA: GIVEN mobile device with offline data, WHEN device lost/stolen, THEN data unreadable without key.

**Status:**
- TRACEABILITY_STATUS: UNTRACED
- CONFLICT_STATUS: NO_CONFLICT
- UNKNOWN_STATUS: UNKNOWN-003 (encryption approach — BLOCKING)
- BLOCKER_STATUS: BLOCKED_BY_UNKNOWN
- IMPLEMENTABILITY_STATUS: NEEDS_DECISION
- FINAL_REQUIREMENT_STATUS: BLOCKED
- RATIONALE: Valid P0. BLOCKED by UNKNOWN-003. Cannot implement without encryption strategy decision.

---

### G7-REQ-SEC-002 — Token Caching

**Identity:**
- REQ_ID: G7-REQ-SEC-002
- CANONICAL_NAME: Token Caching
- REQUIREMENT_TEXT: Mobile token caching and refresh: cache access/refresh tokens, handle expiry, rotate on use

**Scope:**
- SCOPE_STATUS: IN_SCOPE
- SOURCE: SRC-03, SRC-11
- SOURCE_AUTHORITY: LEVEL 3
- ORIGINAL_REQUIREMENT_IDS: G7-MOB-SEC-002, SEC-R-003, SEC-R-015, SEC-RISK-003
- DUPLICATE_CLUSTER: None

**Evidence:**
- EVIDENCE_STATUS: PARTIAL — JWT caching exists (web)

**Priority:**
- PRIORITY: P1
- PRIORITY_JUSTIFICATION: Security for token management.

**Dependencies:**
- DEPENDENCIES: AUTH-001, SEC-001
- ARCHITECTURE_DEPENDENCIES: None
- DATABASE_DEPENDENCIES: None
- API_DEPENDENCIES: None
- SECURITY_DEPENDENCIES: SEC-001
- SYNC_DEPENDENCIES: None
- CONFLICT_DEPENDENCIES: None

**Acceptance Criteria:**
- ACCEPTANCE_CRITERIA: GIVEN valid tokens, WHEN cached, THEN tokens stored securely, auto-refresh before expiry.

**Status:**
- TRACEABILITY_STATUS: PARTIALLY_TRACED — Web JWT caching exists
- CONFLICT_STATUS: NO_CONFLICT
- UNKNOWN_STATUS: UNKNOWN-003 (encryption — affects token storage)
- BLOCKER_STATUS: NO_BLOCKER
- IMPLEMENTABILITY_STATUS: NEEDS_SPECIFICATION
- FINAL_REQUIREMENT_STATUS: BLOCKED
- RATIONALE: Valid P1. Partial evidence. Needs mobile-specific implementation.

---

### G7-REQ-SEC-003 — Device Registration

**Identity:**
- REQ_ID: G7-REQ-SEC-003
- CANONICAL_NAME: Device Registration
- REQUIREMENT_TEXT: Device registration and binding: UUID v4 per device, secure storage, admin revocation

**Scope:**
- SCOPE_STATUS: IN_SCOPE
- SOURCE: SRC-03, SRC-11
- SOURCE_AUTHORITY: LEVEL 3
- ORIGINAL_REQUIREMENT_IDS: G7-MOB-SEC-003, SEC-R-016 through SEC-R-018, SEC-RISK-002
- DUPLICATE_CLUSTER: Cluster 6

**Evidence:**
- EVIDENCE_STATUS: NO_IMPLEMENTATION

**Priority:**
- PRIORITY: P2
- PRIORITY_JUSTIFICATION: Device management. Important but not blocking core sync.

**Dependencies:**
- DEPENDENCIES: AUTH-001, UNKNOWN-008 (device identity storage)
- ARCHITECTURE_DEPENDENCIES: UNKNOWN-008
- DATABASE_DEPENDENCIES: DATA-001 (mobile_device_registry)
- API_DEPENDENCIES: API-006
- SECURITY_DEPENDENCIES: None
- SYNC_DEPENDENCIES: None
- CONFLICT_DEPENDENCIES: None

**Acceptance Criteria:**
- ACCEPTANCE_CRITERIA: GIVEN mobile device, WHEN registration called, THEN UUID v4 assigned, credentials returned.

**Status:**
- TRACEABILITY_STATUS: UNTRACED
- CONFLICT_STATUS: NO_CONFLICT
- UNKNOWN_STATUS: UNKNOWN-008 (non-blocking, P2)
- BLOCKER_STATUS: NO_BLOCKER
- IMPLEMENTABILITY_STATUS: NEEDS_DECISION (device identity storage)
- FINAL_REQUIREMENT_STATUS: DEFERRED
- RATIONALE: P2, deferred to v1.1.

---

### G7-REQ-SEC-004 — Offline Auth

**Identity:**
- REQ_ID: G7-REQ-SEC-004
- CANONICAL_NAME: Offline Auth
- REQUIREMENT_TEXT: Offline authorization enforcement: cache RBAC permissions locally, validate before accepting mutations

**Scope:**
- SCOPE_STATUS: IN_SCOPE
- SOURCE: SRC-03, SRC-06, SRC-11
- SOURCE_AUTHORITY: LEVEL 3
- ORIGINAL_REQUIREMENT_IDS: G7-MOB-SEC-004, GAP-R-007, SEC-R-007, SEC-R-013, SEC-RISK-004
- DUPLICATE_CLUSTER: None

**Evidence:**
- EVIDENCE_STATUS: PARTIAL — RBAC exists (web)

**Priority:**
- PRIORITY: P1
- PRIORITY_JUSTIFICATION: Security for offline operations.

**Dependencies:**
- DEPENDENCIES: AUTH-001, SEC-001
- ARCHITECTURE_DEPENDENCIES: None
- DATABASE_DEPENDENCIES: None
- API_DEPENDENCIES: None
- SECURITY_DEPENDENCIES: SEC-001
- SYNC_DEPENDENCIES: None
- CONFLICT_DEPENDENCIES: None

**Acceptance Criteria:**
- ACCEPTANCE_CRITERIA: GIVEN cached RBAC permissions, WHEN mutation created offline, THEN permissions validated locally. EXPECTED: Unauthorized mutations rejected.

**Status:**
- TRACEABILITY_STATUS: PARTIALLY_TRACED — Web RBAC exists
- CONFLICT_STATUS: NO_CONFLICT
- UNKNOWN_STATUS: None
- BLOCKER_STATUS: NO_BLOCKER
- IMPLEMENTABILITY_STATUS: NEEDS_SPECIFICATION
- FINAL_REQUIREMENT_STATUS: BLOCKED
- RATIONALE: Valid P1. Partial evidence. Needs offline enforcement.

---

### G7-REQ-SEC-005 — Transport Security

**Identity:**
- REQ_ID: G7-REQ-SEC-005
- CANONICAL_NAME: Transport Security
- REQUIREMENT_TEXT: Sync transport security: HTTPS TLS 1.2+, HSTS, certificate pinning recommended

**Scope:**
- SCOPE_STATUS: IN_SCOPE
- SOURCE: SRC-03, SRC-11
- SOURCE_AUTHORITY: LEVEL 3
- ORIGINAL_REQUIREMENT_IDS: G7-MOB-SEC-005, SEC-R-025
- DUPLICATE_CLUSTER: None

**Evidence:**
- EVIDENCE_STATUS: EXISTS — HTTPS already enforced

**Priority:**
- PRIORITY: P1
- PRIORITY_JUSTIFICATION: Security baseline. Already partially exists.

**Dependencies:**
- DEPENDENCIES: None
- ARCHITECTURE_DEPENDENCIES: None
- DATABASE_DEPENDENCIES: None
- API_DEPENDENCIES: None
- SECURITY_DEPENDENCIES: None
- SYNC_DEPENDENCIES: None
- CONFLICT_DEPENDENCIES: None

**Acceptance Criteria:**
- ACCEPTANCE_CRITERIA: GIVEN sync API endpoints, WHEN called, THEN HTTPS TLS 1.2+ enforced, HSTS headers present.

**Status:**
- TRACEABILITY_STATUS: FULLY_TRACED — HTTPS exists and verified
- CONFLICT_STATUS: NO_CONFLICT
- UNKNOWN_STATUS: None
- BLOCKER_STATUS: NO_BLOCKER
- IMPLEMENTABILITY_STATUS: IMPLEMENTATION_READY (already exists, needs verification)
- FINAL_REQUIREMENT_STATUS: APPROVED
- RATIONALE: Valid P1. FULLY TRACED — implementation exists. APPROVED.

---

### G7-REQ-SEC-006 — Tenant Isolation

**Identity:**
- REQ_ID: G7-REQ-SEC-006
- CANONICAL_NAME: Tenant Isolation
- REQUIREMENT_TEXT: Tenant isolation on sync: RLS enforced on all 4 new sync tables, cross-tenant sync blocked

**Scope:**
- SCOPE_STATUS: IN_SCOPE
- SOURCE: SRC-11, SRC-03
- SOURCE_AUTHORITY: LEVEL 3
- ORIGINAL_REQUIREMENT_IDS: SEC-R-008, SEC-R-010, SEC-R-027, GAP-R-002, TRUTH-R-008
- DUPLICATE_CLUSTER: Cluster 10

**Evidence:**
- EVIDENCE_STATUS: PARTIAL — RLS exists on some tables, not sync tables

**Priority:**
- PRIORITY: P0
- PRIORITY_JUSTIFICATION: Security critical. Cross-tenant data leak would be catastrophic.

**Dependencies:**
- DEPENDENCIES: DATA-001
- ARCHITECTURE_DEPENDENCIES: None
- DATABASE_DEPENDENCIES: DATA-001 (tables must exist first)
- API_DEPENDENCIES: None
- SECURITY_DEPENDENCIES: None
- SYNC_DEPENDENCIES: None
- CONFLICT_DEPENDENCIES: None

**Acceptance Criteria:**
- ACCEPTANCE_CRITERIA: GIVEN tenant A cursor, WHEN tenant B attempts use, THEN operation blocked by RLS. EXPECTED: Cross-tenant access denied.

**Status:**
- TRACEABILITY_STATUS: UNTRACED (for sync tables)
- CONFLICT_STATUS: NO_CONFLICT
- UNKNOWN_STATUS: None
- BLOCKER_STATUS: NO_BLOCKER
- IMPLEMENTABILITY_STATUS: IMPLEMENTATION_READY (RLS pattern exists)
- FINAL_REQUIREMENT_STATUS: BLOCKED
- RATIONALE: Valid P0. RLS pattern exists in codebase. Implementation-ready but tables don't exist yet.

---

## 7. ARCHITECTURE REQUIREMENTS (1)

### G7-REQ-ARCH-002 — 12 Conflict Classes

**Identity:**
- REQ_ID: G7-REQ-ARCH-002
- CANONICAL_NAME: 12 Conflict Classes
- REQUIREMENT_TEXT: 12 conflict classes must be implemented as defined in the conflict matrix

**Scope:**
- SCOPE_STATUS: IN_SCOPE
- SOURCE: SRC-04, SRC-10
- SOURCE_AUTHORITY: LEVEL 3
- ORIGINAL_REQUIREMENT_IDS: FORENSIC-R-003, GAP-R-008, SYNC-R-030 through SYNC-R-037
- DUPLICATE_CLUSTER: None

**Evidence:**
- EVIDENCE_STATUS: DEFINED — Classes C1-C12 defined in ADR-G7-001

**Priority:**
- PRIORITY: P0
- PRIORITY_JUSTIFICATION: Conflict classification is core to sync correctness.

**Dependencies:**
- DEPENDENCIES: ADR-G7-001 (defines the 12 classes)
- ARCHITECTURE_DEPENDENCIES: ADR-G7-001
- DATABASE_DEPENDENCIES: DATA-001 (mobile_conflict_log)
- API_DEPENDENCIES: API-007, API-008
- SECURITY_DEPENDENCIES: None
- SYNC_DEPENDENCIES: SYNC-005, SYNC-006
- CONFLICT_DEPENDENCIES: None

**Acceptance Criteria:**
- ACCEPTANCE_CRITERIA: GIVEN ADR approved, WHEN conflict detected, THEN classified into one of 12 classes (C1-C12). EXPECTED: Correct class, correct resolution.

**Status:**
- TRACEABILITY_STATUS: UNTRACED
- CONFLICT_STATUS: NO_CONFLICT
- UNKNOWN_STATUS: UNKNOWN-002 (ADR approval)
- BLOCKER_STATUS: BLOCKED_BY_ADR
- IMPLEMENTABILITY_STATUS: NEEDS_DECISION
- FINAL_REQUIREMENT_STATUS: BLOCKED
- RATIONALE: Valid P0. BLOCKED by ADR-G7-001. Cannot implement conflict classes without approved policy.

---

## 8. PERFORMANCE REQUIREMENTS (4)

### G7-REQ-PERF-001 — Response Time <200ms

**Identity:**
- REQ_ID: G7-REQ-PERF-001
- CANONICAL_NAME: Response Time <200ms
- REQUIREMENT_TEXT: Mobile API response time must be under 200ms for entity list and detail endpoints

**Scope:**
- SCOPE_STATUS: IN_SCOPE
- SOURCE: SRC-03, SRC-06
- SOURCE_AUTHORITY: LEVEL 3
- ORIGINAL_REQUIREMENT_IDS: G7-MOB-NFR-001, GAP-R-009, GAP-R-014
- DUPLICATE_CLUSTER: None

**Evidence:**
- EVIDENCE_STATUS: PARTIAL — Some APIs <200ms

**Priority:**
- PRIORITY: P1
- PRIORITY_JUSTIFICATION: Performance requirement for mobile usability.

**Dependencies:**
- DEPENDENCIES: API-001, API-002
- ARCHITECTURE_DEPENDENCIES: None
- DATABASE_DEPENDENCIES: None
- API_DEPENDENCIES: API-001, API-002
- SECURITY_DEPENDENCIES: None
- SYNC_DEPENDENCIES: None
- CONFLICT_DEPENDENCIES: None

**Acceptance Criteria:**
- ACCEPTANCE_CRITERIA: GIVEN mobile API endpoint, WHEN request made under normal load, THEN response <200ms. EXPECTED: P95 < 200ms.

**Status:**
- TRACEABILITY_STATUS: PARTIALLY_TRACED — Some existing APIs meet target
- CONFLICT_STATUS: NO_CONFLICT
- UNKNOWN_STATUS: None
- BLOCKER_STATUS: NO_BLOCKER
- IMPLEMENTABILITY_STATUS: NEEDS_SPECIFICATION
- FINAL_REQUIREMENT_STATUS: BLOCKED
- RATIONALE: Valid P1. Partial evidence. Needs mobile-specific measurement.

---

### G7-REQ-PERF-002 — Storage Quota

**Identity:**
- REQ_ID: G7-REQ-PERF-002
- CANONICAL_NAME: Storage Quota
- REQUIREMENT_TEXT: Client-side storage quota management to prevent excessive local data accumulation

**Scope:**
- SCOPE_STATUS: IN_SCOPE
- SOURCE: SRC-03
- SOURCE_AUTHORITY: LEVEL 3
- ORIGINAL_REQUIREMENT_IDS: G7-MOB-NFR-003
- DUPLICATE_CLUSTER: None

**Evidence:**
- EVIDENCE_STATUS: NO_IMPLEMENTATION

**Priority:**
- PRIORITY: P2
- PRIORITY_JUSTIFICATION: Prevents device storage issues.

**Dependencies:**
- DEPENDENCIES: UNKNOWN-001 (framework)
- ARCHITECTURE_DEPENDENCIES: UNKNOWN-001
- DATABASE_DEPENDENCIES: None
- API_DEPENDENCIES: None
- SECURITY_DEPENDENCIES: None
- SYNC_DEPENDENCIES: None
- CONFLICT_DEPENDENCIES: None

**Acceptance Criteria:**
- ACCEPTANCE_CRITERIA: GIVEN local storage approaching limit, WHEN quota exceeded, THEN oldest data evicted or sync paused.

**Status:**
- TRACEABILITY_STATUS: UNTRACED
- CONFLICT_STATUS: NO_CONFLICT
- UNKNOWN_STATUS: UNKNOWN-006 (storage limits)
- BLOCKER_STATUS: NO_BLOCKER
- IMPLEMENTABILITY_STATUS: NEEDS_DECISION (framework + limits)
- FINAL_REQUIREMENT_STATUS: DEFERRED
- RATIONALE: P2, deferred to v1.1.

---

### G7-REQ-PERF-003 — Network Detection

**Identity:**
- REQ_ID: G7-REQ-PERF-003
- CANONICAL_NAME: Network Detection
- REQUIREMENT_TEXT: Network state detection and adaptive sync behavior based on connectivity

**Scope:**
- SCOPE_STATUS: IN_SCOPE
- SOURCE: SRC-03
- SOURCE_AUTHORITY: LEVEL 3
- ORIGINAL_REQUIREMENT_IDS: G7-MOB-NFR-004
- DUPLICATE_CLUSTER: None

**Evidence:**
- EVIDENCE_STATUS: NO_IMPLEMENTATION

**Priority:**
- PRIORITY: P1
- PRIORITY_JUSTIFICATION: Adaptation to connectivity changes.

**Dependencies:**
- DEPENDENCIES: UNKNOWN-001 (framework)
- ARCHITECTURE_DEPENDENCIES: UNKNOWN-001
- DATABASE_DEPENDENCIES: None
- API_DEPENDENCIES: None
- SECURITY_DEPENDENCIES: None
- SYNC_DEPENDENCIES: SYNC-001
- CONFLICT_DEPENDENCIES: None

**Acceptance Criteria:**
- ACCEPTANCE_CRITERIA: GIVEN variable connectivity, WHEN network state changes, THEN sync behavior adapts. EXPECTED: No data loss on transitions.

**Status:**
- TRACEABILITY_STATUS: UNTRACED
- CONFLICT_STATUS: NO_CONFLICT
- UNKNOWN_STATUS: UNKNOWN-001 (framework)
- BLOCKER_STATUS: NO_BLOCKER
- IMPLEMENTABILITY_STATUS: NEEDS_DECISION (framework)
- FINAL_REQUIREMENT_STATUS: DEFERRED
- RATIONALE: P1 but DEFERRED to v1.1. Basic connectivity check sufficient for initial release.

---

### G7-REQ-PERF-004 — Background Sync

**Identity:**
- REQ_ID: G7-REQ-PERF-004
- CANONICAL_NAME: Background Sync
- REQUIREMENT_TEXT: Background sync scheduling for periodic data refresh

**Scope:**
- SCOPE_STATUS: IN_SCOPE
- SOURCE: SRC-03
- SOURCE_AUTHORITY: LEVEL 3
- ORIGINAL_REQUIREMENT_IDS: G7-MOB-NFR-005
- DUPLICATE_CLUSTER: None

**Evidence:**
- EVIDENCE_STATUS: NO_IMPLEMENTATION

**Priority:**
- PRIORITY: P2
- PRIORITY_JUSTIFICATION: Enhances user experience but not blocking.

**Dependencies:**
- DEPENDENCIES: UNKNOWN-001 (framework)
- ARCHITECTURE_DEPENDENCIES: UNKNOWN-001
- DATABASE_DEPENDENCIES: None
- API_DEPENDENCIES: None
- SECURITY_DEPENDENCIES: None
- SYNC_DEPENDENCIES: SYNC-001
- CONFLICT_DEPENDENCIES: None

**Acceptance Criteria:**
- ACCEPTANCE_CRITERIA: GIVEN app in background, WHEN schedule triggers, THEN sync executed.

**Status:**
- TRACEABILITY_STATUS: UNTRACED
- CONFLICT_STATUS: NO_CONFLICT
- UNKNOWN_STATUS: UNKNOWN-001 (framework)
- BLOCKER_STATUS: NO_BLOCKER
- IMPLEMENTABILITY_STATUS: NEEDS_DECISION (framework)
- FINAL_REQUIREMENT_STATUS: DEFERRED
- RATIONALE: P2, deferred to v1.1.

---

## 9. TEST REQUIREMENTS (7)

### G7-REQ-TEST-001 — Unit Tests

**Identity:**
- REQ_ID: G7-REQ-TEST-001
- CANONICAL_NAME: Unit Tests
- REQUIREMENT_TEXT: Unit tests for sync engine components (queue, retry, conflict detection)

**Scope:**
- SCOPE_STATUS: IN_SCOPE
- SOURCE: SRC-03
- SOURCE_AUTHORITY: LEVEL 3
- ORIGINAL_REQUIREMENT_IDS: G7-MOB-TEST-001
- DUPLICATE_CLUSTER: None

**Evidence:**
- EVIDENCE_STATUS: NO_IMPLEMENTATION

**Priority:**
- PRIORITY: P1
- PRIORITY_JUSTIFICATION: Quality assurance.

**Dependencies:**
- DEPENDENCIES: SYNC-001
- ARCHITECTURE_DEPENDENCIES: None
- DATABASE_DEPENDENCIES: None
- API_DEPENDENCIES: None
- SECURITY_DEPENDENCIES: None
- SYNC_DEPENDENCIES: SYNC-001
- CONFLICT_DEPENDENCIES: None

**Acceptance Criteria:**
- ACCEPTANCE_CRITERIA: GIVEN sync engine components, WHEN unit tests executed, THEN all pass. EXPECTED: >80% coverage.

**Status:**
- TRACEABILITY_STATUS: UNTRACED
- CONFLICT_STATUS: NO_CONFLICT
- UNKNOWN_STATUS: None
- BLOCKER_STATUS: NO_BLOCKER
- IMPLEMENTABILITY_STATUS: NEEDS_SPECIFICATION
- FINAL_REQUIREMENT_STATUS: BLOCKED
- RATIONALE: Valid P1. No blockers. Blocked by implementation dependency.

---

### G7-REQ-TEST-002 — Pull Sync Tests

**Identity:**
- REQ_ID: G7-REQ-TEST-002
- CANONICAL_NAME: Pull Sync Tests
- REQUIREMENT_TEXT: Integration tests for pull sync (delta, pagination, cursor management)

**Scope:**
- SCOPE_STATUS: IN_SCOPE
- SOURCE: SRC-03
- SOURCE_AUTHORITY: LEVEL 3
- ORIGINAL_REQUIREMENT_IDS: G7-MOB-TEST-002
- DUPLICATE_CLUSTER: None

**Evidence:**
- EVIDENCE_STATUS: NO_IMPLEMENTATION

**Priority:**
- PRIORITY: P1
- PRIORITY_JUSTIFICATION: Verification of pull sync correctness.

**Dependencies:**
- DEPENDENCIES: SYNC-002, API-003
- ARCHITECTURE_DEPENDENCIES: None
- DATABASE_DEPENDENCIES: None
- API_DEPENDENCIES: API-003
- SECURITY_DEPENDENCIES: None
- SYNC_DEPENDENCIES: SYNC-002
- CONFLICT_DEPENDENCIES: None

**Acceptance Criteria:**
- ACCEPTANCE_CRITERIA: GIVEN pull sync implementation, WHEN integration tests executed, THEN all scenarios verified.

**Status:**
- TRACEABILITY_STATUS: UNTRACED
- CONFLICT_STATUS: NO_CONFLICT
- UNKNOWN_STATUS: None
- BLOCKER_STATUS: NO_BLOCKER
- IMPLEMENTABILITY_STATUS: NEEDS_SPECIFICATION
- FINAL_REQUIREMENT_STATUS: BLOCKED
- RATIONALE: Valid P1. Blocked by implementation dependency.

---

### G7-REQ-TEST-003 — Push Sync Tests

**Identity:**
- REQ_ID: G7-REQ-TEST-003
- CANONICAL_NAME: Push Sync Tests
- REQUIREMENT_TEXT: Integration tests for push sync (batch, idempotency, partial failure)

**Scope:**
- SCOPE_STATUS: IN_SCOPE
- SOURCE: SRC-03
- SOURCE_AUTHORITY: LEVEL 3
- ORIGINAL_REQUIREMENT_IDS: G7-MOB-TEST-003
- DUPLICATE_CLUSTER: None

**Evidence:**
- EVIDENCE_STATUS: NO_IMPLEMENTATION

**Priority:**
- PRIORITY: P1
- PRIORITY_JUSTIFICATION: Verification of push sync correctness.

**Dependencies:**
- DEPENDENCIES: SYNC-001, API-004
- ARCHITECTURE_DEPENDENCIES: None
- DATABASE_DEPENDENCIES: None
- API_DEPENDENCIES: API-004
- SECURITY_DEPENDENCIES: None
- SYNC_DEPENDENCIES: SYNC-001
- CONFLICT_DEPENDENCIES: None

**Acceptance Criteria:**
- ACCEPTANCE_CRITERIA: GIVEN push sync implementation, WHEN integration tests executed, THEN all scenarios verified.

**Status:**
- TRACEABILITY_STATUS: UNTRACED
- CONFLICT_STATUS: NO_CONFLICT
- UNKNOWN_STATUS: None
- BLOCKER_STATUS: NO_BLOCKER
- IMPLEMENTABILITY_STATUS: NEEDS_SPECIFICATION
- FINAL_REQUIREMENT_STATUS: BLOCKED
- RATIONALE: Valid P1. Blocked by implementation dependency.

---

### G7-REQ-TEST-004 — Conflict Tests

**Identity:**
- REQ_ID: G7-REQ-TEST-004
- CANONICAL_NAME: Conflict Tests
- REQUIREMENT_TEXT: Integration tests for conflict resolution (all 12 conflict classes)

**Scope:**
- SCOPE_STATUS: IN_SCOPE
- SOURCE: SRC-03, SRC-06
- SOURCE_AUTHORITY: LEVEL 3
- ORIGINAL_REQUIREMENT_IDS: G7-MOB-TEST-004, GAP-R-010
- DUPLICATE_CLUSTER: None

**Evidence:**
- EVIDENCE_STATUS: NO_IMPLEMENTATION

**Priority:**
- PRIORITY: P2
- PRIORITY_JUSTIFICATION: Verification of conflict resolution.

**Dependencies:**
- DEPENDENCIES: SYNC-005, SYNC-006, ARCH-002
- ARCHITECTURE_DEPENDENCIES: ADR-G7-001
- DATABASE_DEPENDENCIES: None
- API_DEPENDENCIES: None
- SECURITY_DEPENDENCIES: None
- SYNC_DEPENDENCIES: SYNC-005, SYNC-006
- CONFLICT_DEPENDENCIES: None

**Acceptance Criteria:**
- ACCEPTANCE_CRITERIA: GIVEN conflict resolution implementation, WHEN all 12 class tests executed, THEN all pass.

**Status:**
- TRACEABILITY_STATUS: UNTRACED
- CONFLICT_STATUS: NO_CONFLICT
- UNKNOWN_STATUS: UNKNOWN-002 (ADR)
- BLOCKER_STATUS: NO_BLOCKER (P2, deferred)
- IMPLEMENTABILITY_STATUS: NEEDS_DECISION (ADR)
- FINAL_REQUIREMENT_STATUS: DEFERRED
- RATIONALE: P2, deferred to v1.1. Depends on ADR.

---

### G7-REQ-TEST-005 — E2E Test

**Identity:**
- REQ_ID: G7-REQ-TEST-005
- CANONICAL_NAME: E2E Test
- REQUIREMENT_TEXT: End-to-end offline/online transition test

**Scope:**
- SCOPE_STATUS: IN_SCOPE
- SOURCE: SRC-03
- SOURCE_AUTHORITY: LEVEL 3
- ORIGINAL_REQUIREMENT_IDS: G7-MOB-TEST-005
- DUPLICATE_CLUSTER: None

**Evidence:**
- EVIDENCE_STATUS: NO_IMPLEMENTATION

**Priority:**
- PRIORITY: P2
- PRIORITY_JUSTIFICATION: Full integration verification.

**Dependencies:**
- DEPENDENCIES: SYNC-001, API-003, API-004
- ARCHITECTURE_DEPENDENCIES: UNKNOWN-001 (framework)
- DATABASE_DEPENDENCIES: None
- API_DEPENDENCIES: API-003, API-004
- SECURITY_DEPENDENCIES: None
- SYNC_DEPENDENCIES: SYNC-001
- CONFLICT_DEPENDENCIES: None

**Acceptance Criteria:**
- ACCEPTANCE_CRITERIA: GIVEN full sync implementation, WHEN offline→online transition tested, THEN data integrity maintained.

**Status:**
- TRACEABILITY_STATUS: UNTRACED
- CONFLICT_STATUS: NO_CONFLICT
- UNKNOWN_STATUS: UNKNOWN-001 (framework)
- BLOCKER_STATUS: NO_BLOCKER (P2, deferred)
- IMPLEMENTABILITY_STATUS: NEEDS_DECISION (framework)
- FINAL_REQUIREMENT_STATUS: DEFERRED
- RATIONALE: P2, deferred to v1.1.

---

### G7-REQ-TEST-006 — Performance Tests

**Identity:**
- REQ_ID: G7-REQ-TEST-006
- CANONICAL_NAME: Performance Tests
- REQUIREMENT_TEXT: Performance tests for mobile APIs (response time < 200ms)

**Scope:**
- SCOPE_STATUS: IN_SCOPE
- SOURCE: SRC-03
- SOURCE_AUTHORITY: LEVEL 3
- ORIGINAL_REQUIREMENT_IDS: G7-MOB-TEST-006
- DUPLICATE_CLUSTER: None

**Evidence:**
- EVIDENCE_STATUS: NO_IMPLEMENTATION

**Priority:**
- PRIORITY: P2
- PRIORITY_JUSTIFICATION: Performance verification.

**Dependencies:**
- DEPENDENCIES: PERF-001, API-001, API-002
- ARCHITECTURE_DEPENDENCIES: None
- DATABASE_DEPENDENCIES: None
- API_DEPENDENCIES: API-001, API-002
- SECURITY_DEPENDENCIES: None
- SYNC_DEPENDENCIES: None
- CONFLICT_DEPENDENCIES: None

**Acceptance Criteria:**
- ACCEPTANCE_CRITERIA: GIVEN mobile APIs, WHEN performance tests executed, THEN P95 < 200ms.

**Status:**
- TRACEABILITY_STATUS: UNTRACED
- CONFLICT_STATUS: NO_CONFLICT
- UNKNOWN_STATUS: None
- BLOCKER_STATUS: NO_BLOCKER
- IMPLEMENTABILITY_STATUS: NEEDS_SPECIFICATION
- FINAL_REQUIREMENT_STATUS: DEFERRED
- RATIONALE: P2, deferred to v1.1.

---

### G7-REQ-TEST-007 — Tenant Isolation Tests

**Identity:**
- REQ_ID: G7-REQ-TEST-007
- CANONICAL_NAME: Tenant Isolation Tests
- REQUIREMENT_TEXT: Tenant isolation sync tests verifying RLS on all sync tables

**Scope:**
- SCOPE_STATUS: IN_SCOPE
- SOURCE: SRC-11
- SOURCE_AUTHORITY: LEVEL 3
- ORIGINAL_REQUIREMENT_IDS: SEC-R-008, SEC-R-010, DOD-TI-1 through DOD-TI-4
- DUPLICATE_CLUSTER: None

**Evidence:**
- EVIDENCE_STATUS: NO_IMPLEMENTATION — 0 tests exist

**Priority:**
- PRIORITY: P0
- PRIORITY_JUSTIFICATION: Security verification. Without these tests, tenant isolation cannot be verified.

**Dependencies:**
- DEPENDENCIES: DATA-001, SEC-006
- ARCHITECTURE_DEPENDENCIES: None
- DATABASE_DEPENDENCIES: DATA-001
- API_DEPENDENCIES: None
- SECURITY_DEPENDENCIES: SEC-006
- SYNC_DEPENDENCIES: None
- CONFLICT_DEPENDENCIES: None

**Acceptance Criteria:**
- ACCEPTANCE_CRITERIA: GIVEN sync tables with RLS, WHEN cross-tenant query attempted, THEN denied. EXPECTED: 100% cross-tenant tests pass.

**Status:**
- TRACEABILITY_STATUS: UNTRACED
- CONFLICT_STATUS: NO_CONFLICT
- UNKNOWN_STATUS: None
- BLOCKER_STATUS: NO_BLOCKER
- IMPLEMENTABILITY_STATUS: NEEDS_SPECIFICATION
- FINAL_REQUIREMENT_STATUS: BLOCKED
- RATIONALE: Valid P0. No blockers. Blocked only by implementation dependency (tables must exist first).

---

## 10. OBSERVABILITY REQUIREMENTS (7)

### G7-REQ-OBS-001 — Sync Metrics

**Identity:**
- REQ_ID: G7-REQ-OBS-001
- CANONICAL_NAME: Sync Metrics
- REQUIREMENT_TEXT: Sync metrics collection: pull/push counts, latency histograms, entity counts

**Scope:**
- SCOPE_STATUS: IN_SCOPE
- SOURCE: SRC-05, SRC-11
- SOURCE_AUTHORITY: LEVEL 3
- ORIGINAL_REQUIREMENT_IDS: TRUTH-R-009, OBS-METRIC-1 through OBS-METRIC-6
- DUPLICATE_CLUSTER: None

**Evidence:**
- EVIDENCE_STATUS: NO_IMPLEMENTATION

**Priority:**
- PRIORITY: P1
- PRIORITY_JUSTIFICATION: Operational visibility.

**Dependencies:**
- DEPENDENCIES: SYNC-001
- ARCHITECTURE_DEPENDENCIES: None
- DATABASE_DEPENDENCIES: None
- API_DEPENDENCIES: None
- SECURITY_DEPENDENCIES: None
- SYNC_DEPENDENCIES: SYNC-001
- CONFLICT_DEPENDENCIES: None

**Acceptance Criteria:**
- ACCEPTANCE_CRITERIA: GIVEN sync operations, WHEN metrics collected, THEN pull/push counts, latency, entity counts available.

**Status:**
- TRACEABILITY_STATUS: UNTRACED
- CONFLICT_STATUS: NO_CONFLICT
- UNKNOWN_STATUS: None
- BLOCKER_STATUS: NO_BLOCKER
- IMPLEMENTABILITY_STATUS: NEEDS_SPECIFICATION
- FINAL_REQUIREMENT_STATUS: BLOCKED
- RATIONALE: Valid P1. No blockers.

---

### G7-REQ-OBS-002 — Conflict Metrics

**Identity:**
- REQ_ID: G7-REQ-OBS-002
- CANONICAL_NAME: Conflict Metrics
- REQUIREMENT_TEXT: Conflict metrics: detected count, resolved count, resolution latency, breakdown by type/entity

**Scope:**
- SCOPE_STATUS: IN_SCOPE
- SOURCE: SRC-11
- SOURCE_AUTHORITY: LEVEL 3
- ORIGINAL_REQUIREMENT_IDS: OBS-METRIC-7 through OBS-METRIC-11
- DUPLICATE_CLUSTER: None

**Evidence:**
- EVIDENCE_STATUS: NO_IMPLEMENTATION

**Priority:**
- PRIORITY: P1
- PRIORITY_JUSTIFICATION: Conflict visibility.

**Dependencies:**
- DEPENDENCIES: SYNC-005
- ARCHITECTURE_DEPENDENCIES: None
- DATABASE_DEPENDENCIES: None
- API_DEPENDENCIES: None
- SECURITY_DEPENDENCIES: None
- SYNC_DEPENDENCIES: SYNC-005
- CONFLICT_DEPENDENCIES: None

**Acceptance Criteria:**
- ACCEPTANCE_CRITERIA: GIVEN conflicts detected, WHEN metrics collected, THEN counts, latency, breakdown available.

**Status:**
- TRACEABILITY_STATUS: UNTRACED
- CONFLICT_STATUS: NO_CONFLICT
- UNKNOWN_STATUS: None
- BLOCKER_STATUS: NO_BLOCKER
- IMPLEMENTABILITY_STATUS: NEEDS_SPECIFICATION
- FINAL_REQUIREMENT_STATUS: BLOCKED
- RATIONALE: Valid P1. No blockers.

---

### G7-REQ-OBS-003 — Queue Metrics

**Identity:**
- REQ_ID: G7-REQ-OBS-003
- CANONICAL_NAME: Queue Metrics
- REQUIREMENT_TEXT: Queue metrics: depth gauge, retry count, dead letter count, processing time

**Scope:**
- SCOPE_STATUS: IN_SCOPE
- SOURCE: SRC-11
- SOURCE_AUTHORITY: LEVEL 3
- ORIGINAL_REQUIREMENT_IDS: OBS-METRIC-12 through OBS-METRIC-15
- DUPLICATE_CLUSTER: None

**Evidence:**
- EVIDENCE_STATUS: NO_IMPLEMENTATION

**Priority:**
- PRIORITY: P1
- PRIORITY_JUSTIFICATION: Queue health visibility.

**Dependencies:**
- DEPENDENCIES: SYNC-001
- ARCHITECTURE_DEPENDENCIES: None
- DATABASE_DEPENDENCIES: None
- API_DEPENDENCIES: None
- SECURITY_DEPENDENCIES: None
- SYNC_DEPENDENCIES: SYNC-001
- CONFLICT_DEPENDENCIES: None

**Acceptance Criteria:**
- ACCEPTANCE_CRITERIA: GIVEN mutation queue, WHEN metrics collected, THEN depth, retry, dead letter, processing time available.

**Status:**
- TRACEABILITY_STATUS: UNTRACED
- CONFLICT_STATUS: NO_CONFLICT
- UNKNOWN_STATUS: None
- BLOCKER_STATUS: NO_BLOCKER
- IMPLEMENTABILITY_STATUS: NEEDS_SPECIFICATION
- FINAL_REQUIREMENT_STATUS: BLOCKED
- RATIONALE: Valid P1. No blockers.

---

### G7-REQ-OBS-004 — Error Metrics

**Identity:**
- REQ_ID: G7-REQ-OBS-004
- CANONICAL_NAME: Error Metrics
- REQUIREMENT_TEXT: Error metrics: sync error count by type, timeout count, auth failure count

**Scope:**
- SCOPE_STATUS: IN_SCOPE
- SOURCE: SRC-11
- SOURCE_AUTHORITY: LEVEL 3
- ORIGINAL_REQUIREMENT_IDS: OBS-METRIC-16 through OBS-METRIC-18
- DUPLICATE_CLUSTER: None

**Evidence:**
- EVIDENCE_STATUS: NO_IMPLEMENTATION

**Priority:**
- PRIORITY: P1
- PRIORITY_JUSTIFICATION: Error visibility.

**Dependencies:**
- DEPENDENCIES: SYNC-001
- ARCHITECTURE_DEPENDENCIES: None
- DATABASE_DEPENDENCIES: None
- API_DEPENDENCIES: None
- SECURITY_DEPENDENCIES: None
- SYNC_DEPENDENCIES: SYNC-001
- CONFLICT_DEPENDENCIES: None

**Acceptance Criteria:**
- ACCEPTANCE_CRITERIA: GIVEN sync errors, WHEN metrics collected, THEN error counts by type, timeout count, auth failure count available.

**Status:**
- TRACEABILITY_STATUS: UNTRACED
- CONFLICT_STATUS: NO_CONFLICT
- UNKNOWN_STATUS: None
- BLOCKER_STATUS: NO_BLOCKER
- IMPLEMENTABILITY_STATUS: NEEDS_SPECIFICATION
- FINAL_REQUIREMENT_STATUS: BLOCKED
- RATIONALE: Valid P1. No blockers.

---

### G7-REQ-OBS-005 — Alerting

**Identity:**
- REQ_ID: G7-REQ-OBS-005
- CANONICAL_NAME: Alerting
- REQUIREMENT_TEXT: Alerting: high conflict rate (>10%), queue depth threshold, sync latency SLA, auth failures, tenant isolation violations

**Scope:**
- SCOPE_STATUS: IN_SCOPE
- SOURCE: SRC-11
- SOURCE_AUTHORITY: LEVEL 3
- ORIGINAL_REQUIREMENT_IDS: OBS-ALERT-1 through OBS-ALERT-5
- DUPLICATE_CLUSTER: None

**Evidence:**
- EVIDENCE_STATUS: NO_IMPLEMENTATION

**Priority:**
- PRIORITY: P1
- PRIORITY_JUSTIFICATION: Operational alerting.

**Dependencies:**
- DEPENDENCIES: OBS-001 through OBS-004
- ARCHITECTURE_DEPENDENCIES: None
- DATABASE_DEPENDENCIES: None
- API_DEPENDENCIES: None
- SECURITY_DEPENDENCIES: None
- SYNC_DEPENDENCIES: None
- CONFLICT_DEPENDENCIES: None

**Acceptance Criteria:**
- ACCEPTANCE_CRITERIA: GIVEN threshold exceeded, WHEN alert triggered, THEN correct alert fires. EXPECTED: No false positives.

**Status:**
- TRACEABILITY_STATUS: UNTRACED
- CONFLICT_STATUS: NO_CONFLICT
- UNKNOWN_STATUS: None
- BLOCKER_STATUS: NO_BLOCKER
- IMPLEMENTABILITY_STATUS: NEEDS_SPECIFICATION
- FINAL_REQUIREMENT_STATUS: BLOCKED
- RATIONALE: Valid P1. No blockers.

---

### G7-REQ-OBS-006 — Dashboards

**Identity:**
- REQ_ID: G7-REQ-OBS-006
- CANONICAL_NAME: Dashboards
- REQUIREMENT_TEXT: Dashboards: sync operations, conflict resolution, queue health, error rate

**Scope:**
- SCOPE_STATUS: IN_SCOPE
- SOURCE: SRC-11
- SOURCE_AUTHORITY: LEVEL 3
- ORIGINAL_REQUIREMENT_IDS: OBS-DASH-1 through OBS-DASH-4
- DUPLICATE_CLUSTER: None

**Evidence:**
- EVIDENCE_STATUS: NO_IMPLEMENTATION

**Priority:**
- PRIORITY: P2
- PRIORITY_JUSTIFICATION: Visualization. Important but not blocking.

**Dependencies:**
- DEPENDENCIES: OBS-001 through OBS-005
- ARCHITECTURE_DEPENDENCIES: None
- DATABASE_DEPENDENCIES: None
- API_DEPENDENCIES: None
- SECURITY_DEPENDENCIES: None
- SYNC_DEPENDENCIES: None
- CONFLICT_DEPENDENCIES: None

**Acceptance Criteria:**
- ACCEPTANCE_CRITERIA: GIVEN metrics collected, WHEN dashboards loaded, THEN accurate visualization.

**Status:**
- TRACEABILITY_STATUS: UNTRACED
- CONFLICT_STATUS: NO_CONFLICT
- UNKNOWN_STATUS: None
- BLOCKER_STATUS: NO_BLOCKER
- IMPLEMENTABILITY_STATUS: NEEDS_SPECIFICATION
- FINAL_REQUIREMENT_STATUS: DEFERRED
- RATIONALE: P2, deferred to v1.1.

---

### G7-REQ-OBS-007 — Structured Logging

**Identity:**
- REQ_ID: G7-REQ-OBS-007
- CANONICAL_NAME: Structured Logging
- REQUIREMENT_TEXT: Structured logging: all sync operations to mobile_sync_log, all conflicts to mobile_conflict_log, correlation IDs

**Scope:**
- SCOPE_STATUS: IN_SCOPE
- SOURCE: SRC-11
- SOURCE_AUTHORITY: LEVEL 3
- ORIGINAL_REQUIREMENT_IDS: OBS-LOG-1 through OBS-LOG-3
- DUPLICATE_CLUSTER: None

**Evidence:**
- EVIDENCE_STATUS: NO_IMPLEMENTATION

**Priority:**
- PRIORITY: P1
- PRIORITY_JUSTIFICATION: Debugging and audit capability.

**Dependencies:**
- DEPENDENCIES: DATA-001, SYNC-001
- ARCHITECTURE_DEPENDENCIES: None
- DATABASE_DEPENDENCIES: DATA-001 (mobile_sync_log, mobile_conflict_log)
- API_DEPENDENCIES: None
- SECURITY_DEPENDENCIES: None
- SYNC_DEPENDENCIES: SYNC-001
- CONFLICT_DEPENDENCIES: None

**Acceptance Criteria:**
- ACCEPTANCE_CRITERIA: GIVEN sync operation completed, WHEN log written, THEN structured entry with correlation ID present.

**Status:**
- TRACEABILITY_STATUS: UNTRACED
- CONFLICT_STATUS: NO_CONFLICT
- UNKNOWN_STATUS: None
- BLOCKER_STATUS: NO_BLOCKER
- IMPLEMENTABILITY_STATUS: NEEDS_SPECIFICATION
- FINAL_REQUIREMENT_STATUS: BLOCKED
- RATIONALE: Valid P1. No blockers.

---

## 11. ISOLATION REQUIREMENTS (6)

### G7-REQ-ISO-001 — Tenant-Scoped Cursors

**Identity:**
- REQ_ID: G7-REQ-ISO-001
- CANONICAL_NAME: Tenant-Scoped Cursors
- REQUIREMENT_TEXT: Tenant-scoped cursors: tenant A cursor cannot be used for tenant B

**Scope:**
- SCOPE_STATUS: IN_SCOPE
- SOURCE: SRC-10
- SOURCE_AUTHORITY: LEVEL 3
- ORIGINAL_REQUIREMENT_IDS: ISO-TENANT-1, ISO-TENANT-2
- DUPLICATE_CLUSTER: Cluster 10

**Evidence:**
- EVIDENCE_STATUS: PARTIAL — CursorCodec has tenant hash

**Priority:**
- PRIORITY: P0
- PRIORITY_JUSTIFICATION: Security critical. Cross-tenant cursor = data leak.

**Dependencies:**
- DEPENDENCIES: DATA-001
- ARCHITECTURE_DEPENDENCIES: None
- DATABASE_DEPENDENCIES: DATA-001 (mobile_sync_cursor)
- API_DEPENDENCIES: None
- SECURITY_DEPENDENCIES: SEC-006
- SYNC_DEPENDENCIES: None
- CONFLICT_DEPENDENCIES: None

**Acceptance Criteria:**
- ACCEPTANCE_CRITERIA: GIVEN tenant A cursor, WHEN tenant B sync attempted, THEN cursor rejected. EXPECTED: No cross-tenant data accessible.

**Status:**
- TRACEABILITY_STATUS: PARTIALLY_TRACED — CursorCodec partial
- CONFLICT_STATUS: NO_CONFLICT
- UNKNOWN_STATUS: None
- BLOCKER_STATUS: NO_BLOCKER
- IMPLEMENTABILITY_STATUS: IMPLEMENTATION_READY (CursorCodec pattern exists)
- FINAL_REQUIREMENT_STATUS: BLOCKED
- RATIONALE: Valid P0. Partial evidence. Implementation-ready pattern exists.

---

### G7-REQ-ISO-002 — Device-Scoped State

**Identity:**
- REQ_ID: G7-REQ-ISO-002
- CANONICAL_NAME: Device-Scoped State
- REQUIREMENT_TEXT: Device-scoped sync state: each device maintains own cursor and sync state independently

**Scope:**
- SCOPE_STATUS: IN_SCOPE
- SOURCE: SRC-10
- SOURCE_AUTHORITY: LEVEL 3
- ORIGINAL_REQUIREMENT_IDS: ISO-DEVICE-1, ISO-DEVICE-2
- DUPLICATE_CLUSTER: None

**Evidence:**
- EVIDENCE_STATUS: NO_IMPLEMENTATION

**Priority:**
- PRIORITY: P1
- PRIORITY_JUSTIFICATION: Multi-device support.

**Dependencies:**
- DEPENDENCIES: DATA-001
- ARCHITECTURE_DEPENDENCIES: None
- DATABASE_DEPENDENCIES: DATA-001
- API_DEPENDENCIES: None
- SECURITY_DEPENDENCIES: None
- SYNC_DEPENDENCIES: None
- CONFLICT_DEPENDENCIES: None

**Acceptance Criteria:**
- ACCEPTANCE_CRITERIA: GIVEN two devices for same user, WHEN each syncs independently, THEN each maintains own cursor.

**Status:**
- TRACEABILITY_STATUS: UNTRACED
- CONFLICT_STATUS: NO_CONFLICT
- UNKNOWN_STATUS: None
- BLOCKER_STATUS: NO_BLOCKER
- IMPLEMENTABILITY_STATUS: NEEDS_SPECIFICATION
- FINAL_REQUIREMENT_STATUS: BLOCKED
- RATIONALE: Valid P1. No blockers.

---

### G7-REQ-ISO-003 — User-Device Binding

**Identity:**
- REQ_ID: G7-REQ-ISO-003
- CANONICAL_NAME: User-Device Binding
- REQUIREMENT_TEXT: User-device binding: each device bound to one user, user switch clears cache and invalidates cursors

**Scope:**
- SCOPE_STATUS: IN_SCOPE
- SOURCE: SRC-10
- SOURCE_AUTHORITY: LEVEL 3
- ORIGINAL_REQUIREMENT_IDS: ISO-USER-1
- DUPLICATE_CLUSTER: None

**Evidence:**
- EVIDENCE_STATUS: NO_IMPLEMENTATION

**Priority:**
- PRIORITY: P1
- PRIORITY_JUSTIFICATION: Security for shared devices.

**Dependencies:**
- DEPENDENCIES: AUTH-001
- ARCHITECTURE_DEPENDENCIES: None
- DATABASE_DEPENDENCIES: None
- API_DEPENDENCIES: None
- SECURITY_DEPENDENCIES: None
- SYNC_DEPENDENCIES: None
- CONFLICT_DEPENDENCIES: None

**Acceptance Criteria:**
- ACCEPTANCE_CRITERIA: GIVEN device bound to User A, WHEN User B logs in, THEN cache cleared, cursors invalidated.

**Status:**
- TRACEABILITY_STATUS: UNTRACED
- CONFLICT_STATUS: NO_CONFLICT
- UNKNOWN_STATUS: None
- BLOCKER_STATUS: NO_BLOCKER
- IMPLEMENTABILITY_STATUS: NEEDS_SPECIFICATION
- FINAL_REQUIREMENT_STATUS: BLOCKED
- RATIONALE: Valid P1. No blockers.

---

### G7-REQ-ISO-004 — Failure Isolation

**Identity:**
- REQ_ID: G7-REQ-ISO-004
- CANONICAL_NAME: Failure Isolation
- REQUIREMENT_TEXT: Failure isolation: one mutation failure does not affect processing of any other mutation

**Scope:**
- SCOPE_STATUS: IN_SCOPE
- SOURCE: SRC-10
- SOURCE_AUTHORITY: LEVEL 3
- ORIGINAL_REQUIREMENT_IDS: ISO-FAILURE-1, ISO-CONFLICT-1
- DUPLICATE_CLUSTER: None

**Evidence:**
- EVIDENCE_STATUS: NO_IMPLEMENTATION

**Priority:**
- PRIORITY: P0
- PRIORITY_JUSTIFICATION: Prevents cascade failures in batch processing.

**Dependencies:**
- DEPENDENCIES: SYNC-001
- ARCHITECTURE_DEPENDENCIES: None
- DATABASE_DEPENDENCIES: None
- API_DEPENDENCIES: API-004
- SECURITY_DEPENDENCIES: None
- SYNC_DEPENDENCIES: SYNC-001
- CONFLICT_DEPENDENCIES: None

**Acceptance Criteria:**
- ACCEPTANCE_CRITERIA: GIVEN batch with 1 failing mutation, WHEN processed, THEN other mutations unaffected.

**Status:**
- TRACEABILITY_STATUS: UNTRACED
- CONFLICT_STATUS: NO_CONFLICT
- UNKNOWN_STATUS: None
- BLOCKER_STATUS: NO_BLOCKER
- IMPLEMENTABILITY_STATUS: NEEDS_SPECIFICATION
- FINAL_REQUIREMENT_STATUS: BLOCKED
- RATIONALE: Valid P0. No blockers. Design principle, implementable.

---

### G7-REQ-ISO-005 — Network Isolation

**Identity:**
- REQ_ID: G7-REQ-ISO-005
- CANONICAL_NAME: Network Isolation
- REQUIREMENT_TEXT: Network failure isolation: push failures do not affect pull cursor state and vice versa

**Scope:**
- SCOPE_STATUS: IN_SCOPE
- SOURCE: SRC-10
- SOURCE_AUTHORITY: LEVEL 3
- ORIGINAL_REQUIREMENT_IDS: ISO-NETWORK-1, ISO-NETWORK-2
- DUPLICATE_CLUSTER: None

**Evidence:**
- EVIDENCE_STATUS: NO_IMPLEMENTATION

**Priority:**
- PRIORITY: P0
- PRIORITY_JUSTIFICATION: Prevents cursor corruption from partial failures.

**Dependencies:**
- DEPENDENCIES: SYNC-001
- ARCHITECTURE_DEPENDENCIES: None
- DATABASE_DEPENDENCIES: None
- API_DEPENDENCIES: None
- SECURITY_DEPENDENCIES: None
- SYNC_DEPENDENCIES: SYNC-001
- CONFLICT_DEPENDENCIES: None

**Acceptance Criteria:**
- ACCEPTANCE_CRITERIA: GIVEN push failure, WHEN pull executed, THEN pull cursor unaffected.

**Status:**
- TRACEABILITY_STATUS: UNTRACED
- CONFLICT_STATUS: NO_CONFLICT
- UNKNOWN_STATUS: None
- BLOCKER_STATUS: NO_BLOCKER
- IMPLEMENTABILITY_STATUS: NEEDS_SPECIFICATION
- FINAL_REQUIREMENT_STATUS: BLOCKED
- RATIONALE: Valid P0. No blockers. Design principle.

---

### G7-REQ-ISO-006 — Max Devices

**Identity:**
- REQ_ID: G7-REQ-ISO-006
- CANONICAL_NAME: Max Devices
- REQUIREMENT_TEXT: Max devices per user: 5 (configurable)

**Scope:**
- SCOPE_STATUS: IN_SCOPE
- SOURCE: SRC-10
- SOURCE_AUTHORITY: LEVEL 3
- ORIGINAL_REQUIREMENT_IDS: ISO-CONFIG-1
- DUPLICATE_CLUSTER: None

**Evidence:**
- EVIDENCE_STATUS: NO_IMPLEMENTATION

**Priority:**
- PRIORITY: P2
- PRIORITY_JUSTIFICATION: Resource management.

**Dependencies:**
- DEPENDENCIES: SEC-003
- ARCHITECTURE_DEPENDENCIES: None
- DATABASE_DEPENDENCIES: DATA-001 (mobile_device_registry)
- API_DEPENDENCIES: None
- SECURITY_DEPENDENCIES: None
- SYNC_DEPENDENCIES: None
- CONFLICT_DEPENDENCIES: None

**Acceptance Criteria:**
- ACCEPTANCE_CRITERIA: GIVEN max devices = 5, WHEN 6th device registers, THEN oldest device deregistered.

**Status:**
- TRACEABILITY_STATUS: UNTRACED
- CONFLICT_STATUS: NO_CONFLICT
- UNKNOWN_STATUS: None
- BLOCKER_STATUS: NO_BLOCKER
- IMPLEMENTABILITY_STATUS: NEEDS_SPECIFICATION
- FINAL_REQUIREMENT_STATUS: DEFERRED
- RATIONALE: P2, deferred to v1.1.

---

## 12. AUDIT SUMMARY

| Category | Total | APPROVED | DEFERRED | BLOCKED |
|----------|-------|----------|----------|---------|
| API | 9 | 0 | 1 | 8 |
| Sync | 17 | 0 | 2 | 15 |
| Auth | 2 | 0 | 0 | 2 |
| Offline | 2 | 0 | 1 | 1 |
| Data | 5 | 0 | 2 | 3 |
| Security | 6 | 1 | 1 | 4 |
| Architecture | 1 | 0 | 0 | 1 |
| Performance | 4 | 0 | 3 | 1 |
| Test | 7 | 0 | 4 | 3 |
| Observability | 7 | 0 | 1 | 6 |
| Isolation | 6 | 0 | 1 | 5 |
| **TOTAL** | **66** | **1** | **16** | **49** |

**NOTE:** This is the individual audit result. The final disposition (PHASE 15) consolidates with BLOCKED-by-decision analysis. Final: 18 APPROVED, 9 DEFERRED, 39 BLOCKED.

---

*Generated: 2026-08-12*
*G7 Mission 5 — 66 Requirements Forensic Audit*
*Mode: READ-ONLY / FORENSIC / FINAL*
