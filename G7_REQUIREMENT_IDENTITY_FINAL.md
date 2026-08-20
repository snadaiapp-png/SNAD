# G7 REQUIREMENT IDENTITY FINAL

> **Report ID:** G7-IDENTITY-V2
> **Date:** 2026-08-12
> **Status:** VERIFIED
> **Purpose:** Complete identity register for all 66 normalized requirements + 3 decisions.

---

## 1. IDENTITY SCHEME

**Canonical ID:** `G7-REQ-{CATEGORY}-{SEQ}`
**Categories:** API, SYNC, AUTH, OFF, DATA, SEC, PERF, TEST, OBS, ISO
**Total Requirements:** 66
**Total Decisions:** 3 (ARCH-001, ARCH-003, ARCH-004 — reclassified from requirements)

---

## 2. COMPLETE IDENTITY REGISTER

### 2.1 API Requirements (9)

| ID | Canonical Name | Description | Priority | Category | Disposition | Primary Source | Duplicate Cluster |
|----|---------------|-------------|----------|----------|-------------|----------------|-------------------|
| G7-REQ-API-001 | Entity List API | Mobile-optimized entity list API returns paginated, reduced-payload entity lists | P0 | Functional | ACCEPT | SRC-03, SRC-10 | Cluster 1 |
| G7-REQ-API-002 | Entity Detail API | Mobile-optimized entity detail API returns single entity with mobile-appropriate fields | P0 | Functional | ACCEPT | SRC-03, SRC-10 | Cluster 1 |
| G7-REQ-API-003 | Delta Sync Pull API | Delta sync pull API returns only entities changed since client's last cursor | P0 | Functional | ACCEPT | SRC-03, SRC-10 | Cluster 2 |
| G7-REQ-API-004 | Batch Sync Push API | Batch sync push API accepts array of mutation envelopes and returns per-mutation results | P0 | Functional | ACCEPT | SRC-03, SRC-10 | Cluster 3 |
| G7-REQ-API-005 | Sync Status API | Sync status API returns current sync state for a device including cursor position | P1 | Functional | ACCEPT | SRC-03 | — |
| G7-REQ-API-006 | Device Registration API | Device registration API registers mobile device and returns device credentials | P2 | Functional | ACCEPT | SRC-03 | Cluster 6 |
| G7-REQ-API-007 | Conflict List API | Conflict list API returns all unresolved conflicts for a device/user | P1 | Functional | ACCEPT | SRC-03 | — |
| G7-REQ-API-008 | Conflict Resolve API | Conflict resolve API accepts resolution for a specific conflict | P1 | Functional | ACCEPT | SRC-03 | — |
| G7-REQ-API-009 | Conflict Skip API | Conflict skip API marks a conflict as skipped without resolution | P1 | Functional | ACCEPT | SRC-03 | — |

### 2.2 Sync Requirements (17)

| ID | Canonical Name | Description | Priority | Category | Disposition | Primary Source | Duplicate Cluster |
|----|---------------|-------------|----------|----------|-------------|----------------|-------------------|
| G7-REQ-SYNC-001 | Sync Engine | Client-side sync engine manages bidirectional data flow | P0 | Sync | ACCEPT | SRC-03, SRC-10 | Cluster 3 |
| G7-REQ-SYNC-002 | Delta Pull | Delta/incremental pull uses cursor-based pagination | P0 | Sync | ACCEPT | SRC-03, SRC-10 | Cluster 2 |
| G7-REQ-SYNC-003 | Mutation Queue | Mutation queue stores offline changes in FIFO order | P1 | Sync | ACCEPT | SRC-03, SRC-10 | — |
| G7-REQ-SYNC-004 | Cursor Invalidation | Cursor invalidation triggers full resync | P1 | Sync | ACCEPT | SRC-03, SRC-10 | — |
| G7-REQ-SYNC-005 | Conflict Detection | Conflict detection compares server.entity.version vs client.base_version | P1 | Sync | ACCEPT | SRC-03, SRC-10 | Cluster 4 |
| G7-REQ-SYNC-006 | Conflict Resolution | Conflict resolution supports auto-merge, server-wins, and manual | P1 | Sync | ACCEPT | SRC-03, SRC-10 | Cluster 4 |
| G7-REQ-SYNC-007 | Retry/Backoff | Retry with exponential backoff (1s→16s), ±20% jitter, max 5 attempts | P2 | Sync | ACCEPT | SRC-03, SRC-10 | — |
| G7-REQ-SYNC-008 | Idempotency | Idempotency for all sync mutations using SHA-256 fingerprint | P1 | Sync | ACCEPT | SRC-03, SRC-10 | Cluster 7 |
| G7-REQ-SYNC-009 | Conflict Isolation | Conflict isolation: per-mutation, no batch blocking | P1 | Sync | ACCEPT | SRC-10 | Cluster 4 |
| G7-REQ-SYNC-010 | Delete Conflicts | Delete conflict handling: UPDATE vs DELETE → server wins | P1 | Sync | ACCEPT | SRC-10 | Cluster 4 |
| G7-REQ-SYNC-011 | Full Resync | Full resync procedure: clear cache, cursor, queue; pull all | P1 | Sync | ACCEPT | SRC-10 | — |
| G7-REQ-SYNC-012 | Crash Recovery | Crash/restart recovery: queue persists, in-progress reset to READY | P1 | Sync | ACCEPT | SRC-10 | — |
| G7-REQ-SYNC-013 | Sequence Gap | Sequence gap detection: reject with SEQUENCE_GAP_DETECTED | P2 | Sync | DEFER | SRC-10 | — |
| G7-REQ-SYNC-014 | Client Timeout | Client request timeout: 30 seconds | P1 | Sync | ACCEPT | SRC-10 | — |
| G7-REQ-SYNC-015 | Entity Coverage | Entity type coverage: 7 types support CRUD | P0 | Sync | ACCEPT | SRC-10 | — |
| G7-REQ-SYNC-016 | Server Authority | Server-authoritative state management | P1 | Sync | ACCEPT | SRC-03, SRC-10 | — |
| G7-REQ-SYNC-017 | Per-Mutation ACK | Acknowledgement is per-mutation, not per-batch | P0 | Sync | ACCEPT | SRC-10 | Cluster 3 |

### 2.3 Auth Requirements (2)

| ID | Canonical Name | Description | Priority | Category | Disposition | Primary Source | Duplicate Cluster |
|----|---------------|-------------|----------|----------|-------------|----------------|-------------------|
| G7-REQ-AUTH-001 | Mobile Auth Flow | Mobile auth flow supports token caching, refresh, re-auth | P0 | Functional | ACCEPT | SRC-03, SRC-11 | — |
| G7-REQ-AUTH-002 | Offline Token | Offline token handling: cache, queue, re-auth on reconnect | P1 | Functional | ACCEPT | SRC-10, SRC-11 | — |

### 2.4 Offline Requirements (2)

| ID | Canonical Name | Description | Priority | Category | Disposition | Primary Source | Duplicate Cluster |
|----|---------------|-------------|----------|----------|-------------|----------------|-------------------|
| G7-REQ-OFF-001 | Entity Subset | Offline entity subset defines which entities are available offline | P1 | Functional | ACCEPT | SRC-03, SRC-05 | — |
| G7-REQ-OFF-002 | Eligibility Rules | Entity-level offline eligibility rules | P1 | Functional | DEFER | SRC-03 | — |

### 2.5 Data Requirements (5)

| ID | Canonical Name | Description | Priority | Category | Disposition | Primary Source | Duplicate Cluster |
|----|---------------|-------------|----------|----------|-------------|----------------|-------------------|
| G7-REQ-DATA-001 | Sync Tables | Sync metadata tables: 4 new PostgreSQL tables with RLS | P0 | Data | ACCEPT | SRC-03, SRC-06 | Cluster 8 |
| G7-REQ-DATA-002 | Change Tracking | Change tracking columns: version + updated_at on all CRM tables | P0 | Data | ACCEPT | SRC-03, SRC-06 | Cluster 9 |
| G7-REQ-DATA-003 | Local Storage Schema | Client-side local storage schema mirrors server models | P1 | Data | ACCEPT | SRC-03, SRC-10 | — |
| G7-REQ-DATA-004 | Sync Audit Trail | Sync audit trail via mobile_sync_log table | P2 | Data | ACCEPT | SRC-03, SRC-06 | — |
| G7-REQ-DATA-005 | Conflict Log | Conflict log via mobile_conflict_log table | P2 | Data | ACCEPT | SRC-03, SRC-06 | — |

### 2.6 Security Requirements (6)

| ID | Canonical Name | Description | Priority | Category | Disposition | Primary Source | Duplicate Cluster |
|----|---------------|-------------|----------|----------|-------------|----------------|-------------------|
| G7-REQ-SEC-001 | Offline Encryption | Offline data encryption: SQLCipher or OS-level | P0 | Security | ACCEPT | SRC-03, SRC-06, SRC-11 | Cluster 5 |
| G7-REQ-SEC-002 | Token Caching | Mobile token caching and refresh | P1 | Security | ACCEPT | SRC-03, SRC-11 | — |
| G7-REQ-SEC-003 | Device Registration | Device registration and binding: UUID v4 | P2 | Security | ACCEPT | SRC-03, SRC-11 | Cluster 6 |
| G7-REQ-SEC-004 | Offline Auth | Offline authorization enforcement: cache RBAC locally | P1 | Security | ACCEPT | SRC-03, SRC-06, SRC-11 | — |
| G7-REQ-SEC-005 | Transport Security | Sync transport security: HTTPS TLS 1.2+ | P1 | Security | ACCEPT | SRC-03, SRC-11 | — |
| G7-REQ-SEC-006 | Tenant Isolation | Tenant isolation on sync: RLS on all sync tables | P0 | Security | ACCEPT | SRC-11, SRC-03 | Cluster 10 |

### 2.7 Performance Requirements (4)

| ID | Canonical Name | Description | Priority | Category | Disposition | Primary Source | Duplicate Cluster |
|----|---------------|-------------|----------|----------|-------------|----------------|-------------------|
| G7-REQ-PERF-001 | Response Time <200ms | Mobile API response time under 200ms | P1 | Performance | ACCEPT | SRC-03, SRC-06 | — |
| G7-REQ-PERF-002 | Storage Quota | Client-side storage quota management | P2 | Performance | DEFER | SRC-03 | — |
| G7-REQ-PERF-003 | Network Detection | Network state detection and adaptive sync | P1 | Performance | DEFER | SRC-03 | — |
| G7-REQ-PERF-004 | Background Sync | Background sync scheduling | P2 | Performance | DEFER | SRC-03 | — |

### 2.8 Test Requirements (7)

| ID | Canonical Name | Description | Priority | Category | Disposition | Primary Source | Duplicate Cluster |
|----|---------------|-------------|----------|----------|-------------|----------------|-------------------|
| G7-REQ-TEST-001 | Unit Tests | Unit tests for sync engine components | P1 | Test | ACCEPT | SRC-03 | — |
| G7-REQ-TEST-002 | Pull Sync Tests | Integration tests for pull sync | P1 | Test | ACCEPT | SRC-03 | — |
| G7-REQ-TEST-003 | Push Sync Tests | Integration tests for push sync | P1 | Test | ACCEPT | SRC-03 | — |
| G7-REQ-TEST-004 | Conflict Tests | Integration tests for conflict resolution | P2 | Test | ACCEPT | SRC-03, SRC-06 | — |
| G7-REQ-TEST-005 | E2E Test | End-to-end offline/online transition test | P2 | Test | ACCEPT | SRC-03 | — |
| G7-REQ-TEST-006 | Performance Tests | Performance tests for mobile APIs | P2 | Test | DEFER | SRC-03 | — |
| G7-REQ-TEST-007 | Tenant Isolation Tests | Tenant isolation sync tests verifying RLS | P0 | Test | ACCEPT | SRC-11 | — |

### 2.9 Observability Requirements (7)

| ID | Canonical Name | Description | Priority | Category | Disposition | Primary Source | Duplicate Cluster |
|----|---------------|-------------|----------|----------|-------------|----------------|-------------------|
| G7-REQ-OBS-001 | Sync Metrics | Sync metrics: pull/push counts, latency histograms | P1 | Observability | ACCEPT | SRC-05, SRC-11 | — |
| G7-REQ-OBS-002 | Conflict Metrics | Conflict metrics: detected, resolved, latency | P1 | Observability | ACCEPT | SRC-11 | — |
| G7-REQ-OBS-003 | Queue Metrics | Queue metrics: depth, retry, dead letter | P1 | Observability | ACCEPT | SRC-11 | — |
| G7-REQ-OBS-004 | Error Metrics | Error metrics: sync errors, timeouts, auth failures | P1 | Observability | ACCEPT | SRC-11 | — |
| G7-REQ-OBS-005 | Alerting | Alerting: 5 alert types | P1 | Observability | ACCEPT | SRC-11 | — |
| G7-REQ-OBS-006 | Dashboards | Dashboards: 4 dashboard types | P2 | Observability | DEFER | SRC-11 | — |
| G7-REQ-OBS-007 | Structured Logging | Structured logging: all sync operations | P1 | Observability | ACCEPT | SRC-11 | — |

### 2.10 Isolation Requirements (6)

| ID | Canonical Name | Description | Priority | Category | Disposition | Primary Source | Duplicate Cluster |
|----|---------------|-------------|----------|----------|-------------|----------------|-------------------|
| G7-REQ-ISO-001 | Tenant-Scoped Cursors | Tenant-scoped cursors: tenant A cannot use tenant B cursor | P0 | Security | ACCEPT | SRC-10 | Cluster 10 |
| G7-REQ-ISO-002 | Device-Scoped State | Device-scoped sync state | P1 | Security | ACCEPT | SRC-10 | — |
| G7-REQ-ISO-003 | User-Device Binding | User-device binding: one device per user | P1 | Security | ACCEPT | SRC-10 | — |
| G7-REQ-ISO-004 | Failure Isolation | Failure isolation: one mutation failure doesn't affect others | P0 | Security | ACCEPT | SRC-10 | — |
| G7-REQ-ISO-005 | Network Isolation | Network failure isolation: push failures don't affect pull | P0 | Security | ACCEPT | SRC-10 | — |
| G7-REQ-ISO-006 | Max Devices | Max devices per user: 5 (configurable) | P2 | Security | DEFER | SRC-10 | — |

---

## 3. RECLASSIFIED DECISIONS (3)

| ID | Description | Priority | Status | Reason for Reclassification |
|----|-------------|----------|--------|----------------------------|
| G7-REQ-ARCH-001 | ADR-G7-001 approval | P0 | DECISION_REQUIRED | Process gate, not implementation requirement |
| G7-REQ-ARCH-003 | Mobile framework selection | P1 | DECISION_REQUIRED | Product/architecture decision, not requirement |
| G7-REQ-ARCH-004 | Hybrid conflict strategy | P1 | DEFERRED | Architecture decision deferred after ADR |

**These 3 items are tracked in the Architecture Decision Gate, not the requirement register.**

---

## 4. REQUIREMENTS WITH NO SOURCE

**NONE.** All 66 requirements have at least one source document attribution.

---

## 5. ALIASES AND SPLIT/MERGE

| Operation | Details |
|-----------|---------|
| SPLIT | G7-MOB-FR-001 → API-001 + API-002 (list vs detail) |
| SPLIT | G7-MOB-FR-008 → ARCH-001 + ARCH-002 + SYNC-005 + SYNC-006 |
| RECLASSIFY | G7-MOB-NFR-002 → SEC-001 (security, not NFR) |
| RECLASSIFY | G7-MOB-FR-002 → DATA-001 (data, not functional) |
| MERGE | GAP-R-009 + GAP-R-014 → PERF-001 (performance constraint) |

---

*Generated: 2026-08-12*
