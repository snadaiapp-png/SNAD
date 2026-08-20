# G7 REQUIREMENT NORMALIZATION REGISTER

> **Report ID:** G7-REQ-NORM-V1
> **Date:** 2026-08-12
> **Status:** DRAFT — NOT APPROVED
> **Purpose:** Normalize all raw requirements into canonical form with standardized IDs, descriptions, and categories.

---

## 1. NORMALIZATION RULES

1. **Canonical ID:** `G7-REQ-{CATEGORY}-{SEQ}` where CATEGORY ∈ {API, DATA, SYNC, SEC, ARCH, TEST, PERF, OBS}
2. **Description:** One sentence, active voice, testable where possible
3. **Category:** Functional, Non-Functional, Security, Sync, Data, Test, Architecture, Observability
4. **Priority:** P0 (BLOCKER), P1 (CRITICAL), P2 (HIGH), P3 (MEDIUM) — derived from consensus across sources
5. **Source:** Primary source document (SRC-01 = canonical, SRC-10 = sync contract, SRC-11 = security)
6. **Raw Mappings:** All raw IDs that map to this normalized requirement

---

## 2. NORMALIZED FUNCTIONAL REQUIREMENTS

### 2.1 API Requirements

| Norm ID | Description | Priority | Category | Sources | Raw Mappings |
|---------|-------------|----------|----------|---------|-------------|
| G7-REQ-API-001 | Mobile-optimized entity list API returns paginated, reduced-payload entity lists for mobile consumption | P0 | Functional | SRC-03, SRC-10 | G7-MOB-FR-001, API-06, GAP-R-009, TRUTH-R-001 |
| G7-REQ-API-002 | Mobile-optimized entity detail API returns single entity with mobile-appropriate fields | P0 | Functional | SRC-03, SRC-10 | G7-MOB-FR-002 (baseline), API-05 |
| G7-REQ-API-003 | Delta sync pull API returns only entities changed since client's last cursor | P0 | Functional | SRC-03, SRC-10 | G7-MOB-FR-003, API-01, SYNC-R-010 through SYNC-R-014, SYNC-CONTRACT-04 |
| G7-REQ-API-004 | Batch sync push API accepts array of mutation envelopes and returns per-mutation results | P0 | Functional | SRC-03, SRC-10 | G7-MOB-FR-004, API-02, SYNC-R-015 through SYNC-R-018, SYNC-CONTRACT-05 |
| G7-REQ-API-005 | Sync status API returns current sync state for a device including cursor position | P1 | Functional | SRC-03 | G7-MOB-FR-005 (baseline), API-03 |
| G7-REQ-API-006 | Device registration API registers mobile device and returns device credentials | P2 | Functional | SRC-03 | G7-MOB-SEC-003, API-004, SEC-R-016 through SEC-R-018 |
| G7-REQ-API-007 | Conflict list API returns all unresolved conflicts for a device/user | P1 | Functional | SRC-03 | API-07, GAP-R-008 |
| G7-REQ-API-008 | Conflict resolve API accepts resolution (auto-merge, server-wins, client-wins, manual) for a specific conflict | P1 | Functional | SRC-03 | API-08, GAP-R-008 |
| G7-REQ-API-009 | Conflict skip API marks a conflict as skipped (deferred) without resolution | P1 | Functional | SRC-03 | API-09 |

### 2.2 Sync Requirements

| Norm ID | Description | Priority | Category | Sources | Raw Mappings |
|---------|-------------|----------|----------|---------|-------------|
| G7-REQ-SYNC-001 | Client-side sync engine manages bidirectional data flow between local storage and server | P0 | Sync | SRC-03, SRC-10 | G7-MOB-SYNC-001, GAP-R-005, SYNC-CONTRACT-01 through SYNC-CONTRACT-16 |
| G7-REQ-SYNC-002 | Delta/incremental pull uses cursor-based pagination to fetch only changed entities since last sync | P0 | Sync | SRC-03, SRC-10 | G7-MOB-SYNC-002, SYNC-R-010 through SYNC-R-014 |
| G7-REQ-SYNC-003 | Mutation queue stores offline changes in FIFO order per entity type with sequence numbers | P1 | Sync | SRC-03, SRC-10 | G7-MOB-SYNC-003, SYNC-R-005 through SYNC-R-007, SYNC-CONTRACT-02 |
| G7-REQ-SYNC-004 | Cursor invalidation triggers full resync on schema change, token expiry, or explicit request | P1 | Sync | SRC-03, SRC-10 | G7-MOB-SYNC-004, SYNC-R-021, SYNC-CONTRACT-14 |
| G7-REQ-SYNC-005 | Conflict detection compares server.entity.version against client.mutation.base_version | P1 | Sync | SRC-03, SRC-10 | G7-MOB-SYNC-005, SYNC-R-030, SYNC-CONTRACT-06 |
| G7-REQ-SYNC-006 | Conflict resolution supports auto-merge (non-conflicting fields), server-wins (financial/state), and manual resolution (user) | P1 | Sync | SRC-03, SRC-10 | G7-MOB-SYNC-006, SYNC-R-034 through SYNC-R-036, SYNC-CONTRACT-09 |
| G7-REQ-SYNC-007 | Retry with exponential backoff (1s→2s→4s→8s→16s), ±20% jitter, max 5 attempts for retryable errors | P2 | Sync | SRC-03, SRC-10 | G7-MOB-SYNC-007, SYNC-R-024 through SYNC-R-026, SYNC-CONTRACT-07 |
| G7-REQ-SYNC-008 | Idempotency for all sync mutations using SHA-256 fingerprint with 24-hour retention | P1 | Sync | SRC-03, SRC-10 | G7-MOB-SYNC-008, SYNC-R-029, SYNC-CONTRACT-08 |
| G7-REQ-SYNC-009 | Conflict isolation: per-mutation, no batch blocking, each mutation processed independently | P1 | Sync | SRC-10 | SYNC-R-032, SYNC-CONTRACT-05, ISO-CONFLICT-1, ISO-FAILURE-1 |
| G7-REQ-SYNC-010 | Delete conflict handling: UPDATE vs DELETE → server wins; DELETE vs DELETE → idempotent acknowledged | P1 | Sync | SRC-10 | SYNC-R-037, SYNC-CONTRACT-10 |
| G7-REQ-SYNC-011 | Full resync procedure: clear local cache, clear cursor, clear queue (log conflicts first), pull all entities | P1 | Sync | SRC-10 | SYNC-R-038, SYNC-R-039, SYNC-CONTRACT-11 |
| G7-REQ-SYNC-012 | Crash/restart recovery: queue persists in local storage, in-progress mutations reset to READY | P1 | Sync | SRC-10 | SYNC-R-040 through SYNC-R-042, SYNC-CONTRACT-12 |
| G7-REQ-SYNC-013 | Sequence gap detection: reject mutation with SEQUENCE_GAP_DETECTED if gap in sequence numbers | P2 | Sync | SRC-10 | SYNC-R-028 |
| G7-REQ-SYNC-014 | Client request timeout: 30 seconds for both pull and push operations | P1 | Sync | SRC-10 | SYNC-R-017, SYNC-CONTRACT-13 |
| G7-REQ-SYNC-015 | Entity type coverage: CONTACT, ACCOUNT, LEAD, OPPORTUNITY, TASK, ACTIVITY, NOTE all support CREATE/UPDATE/DELETE/Pull/Push | P0 | Sync | SRC-10 | SYNC-R-045, SYNC-CONTRACT-16 |
| G7-REQ-SYNC-016 | Server-authoritative state management: state transitions, financial data, system-generated fields always use server value | P1 | Sync | SRC-03, SRC-10 | G7-MOB-FR-009, SYNC-R-036, SYNC-CONTRACT-09 |
| G7-REQ-SYNC-017 | Acknowledgement is per-mutation (not per-batch): ACKNOWLEDGED mutations removed from queue, CONFLICT mutations logged | P0 | Sync | SRC-10 | SYNC-R-022, SYNC-R-023, SYNC-CONTRACT-15 |

### 2.3 Auth Requirements

| Norm ID | Description | Priority | Category | Sources | Raw Mappings |
|---------|-------------|----------|----------|---------|-------------|
| G7-REQ-AUTH-001 | Mobile auth flow supports token caching, refresh, and re-authentication on expiry | P0 | Functional | SRC-03, SRC-11 | G7-MOB-FR-005, SEC-R-003, SEC-R-004, SEC-R-026, SEC-R-028 |
| G7-REQ-AUTH-002 | Offline token handling: cache tokens in memory, queue mutations while offline, re-auth on reconnect if expired | P1 | Functional | SRC-10, SRC-11 | SYNC-R-043, SYNC-R-044, SEC-R-028 |

### 2.4 Offline/Entity Requirements

| Norm ID | Description | Priority | Category | Sources | Raw Mappings |
|---------|-------------|----------|----------|---------|-------------|
| G7-REQ-OFF-001 | Offline entity subset defines which entities are available offline and their sync eligibility | P1 | Functional | SRC-03, SRC-05 | G7-MOB-FR-006, G7-MOB-FR-007, GAP-R-013, FORENSIC-R-002 |
| G7-REQ-OFF-002 | Entity-level offline eligibility rules define per-entity-type offline capabilities (read/write/pull-only) | P1 | Functional | SRC-03 | G7-MOB-FR-007 |

---

## 3. NORMALIZED DATA REQUIREMENTS

| Norm ID | Description | Priority | Category | Sources | Raw Mappings |
|---------|-------------|----------|----------|---------|-------------|
| G7-REQ-DATA-001 | Sync metadata tables: 4 new PostgreSQL tables (mobile_device_registry, mobile_sync_cursor, mobile_sync_log, mobile_conflict_log) with RLS | P0 | Data | SRC-03, SRC-06 | G7-MOB-DATA-001, GAP-R-002, TABLE-01 through TABLE-04, TRUTH-R-002 |
| G7-REQ-DATA-002 | Change tracking columns: version BIGINT and updated_at TIMESTAMP on all CRM entity tables | P0 | Data | SRC-03, SRC-06 | G7-MOB-DATA-002, GAP-R-003, TRUTH-R-003 |
| G7-REQ-DATA-003 | Client-side local storage schema mirrors server entity models with local_version tracking | P1 | Data | SRC-03, SRC-10 | G7-MOB-DATA-003, SYNC-R-003, SYNC-R-004 |
| G7-REQ-DATA-004 | Sync audit trail via mobile_sync_log table logging all sync operations with device_id, sync_type, status | P2 | Data | SRC-03, SRC-06 | G7-MOB-DATA-004, GAP-R-012, SEC-R-022, SYNC-R-033 |
| G7-REQ-DATA-005 | Conflict log via mobile_conflict_log table logging all conflicts with conflict_id, entity_type, versions, resolution | P2 | Data | SRC-03, SRC-06 | G7-MOB-DATA-005, SYNC-R-033 |

---

## 4. NORMALIZED SECURITY REQUIREMENTS

| Norm ID | Description | Priority | Category | Sources | Raw Mappings |
|---------|-------------|----------|----------|---------|-------------|
| G7-REQ-SEC-001 | Offline data encryption: all local CRM data encrypted at rest using SQLCipher or OS-level encryption | P0 | Security | SRC-03, SRC-06, SRC-11 | G7-MOB-SEC-001, G7-MOB-NFR-002, GAP-R-006, SEC-R-023, SEC-R-024, SEC-RISK-001 |
| G7-REQ-SEC-002 | Mobile token caching and refresh: cache access/refresh tokens, handle expiry, rotate on use | P1 | Security | SRC-03, SRC-11 | G7-MOB-SEC-002, SEC-R-003, SEC-R-015, SEC-RISK-003 |
| G7-REQ-SEC-003 | Device registration and binding: UUID v4 per device, secure storage, admin revocation | P2 | Security | SRC-03, SRC-11 | G7-MOB-SEC-003, SEC-R-016 through SEC-R-018, SEC-RISK-002 |
| G7-REQ-SEC-004 | Offline authorization enforcement: cache RBAC permissions locally, validate before accepting mutations | P1 | Security | SRC-03, SRC-06, SRC-11 | G7-MOB-SEC-004, GAP-R-007, SEC-R-007, SEC-R-013, SEC-RISK-004 |
| G7-REQ-SEC-005 | Sync transport security: HTTPS TLS 1.2+, HSTS, certificate pinning recommended | P1 | Security | SRC-03, SRC-11 | G7-MOB-SEC-005, SEC-R-025 |
| G7-REQ-SEC-006 | Tenant isolation on sync: RLS enforced on all 4 new sync tables, cross-tenant sync blocked | P0 | Security | SRC-11, SRC-03 | SEC-R-008, SEC-R-010, SEC-R-027, GAP-R-002 (partial), TRUTH-R-008 |

---

## 5. NORMALIZED ARCHITECTURE REQUIREMENTS

| Norm ID | Description | Priority | Category | Sources | Raw Mappings |
|---------|-------------|----------|----------|---------|-------------|
| G7-REQ-ARCH-001 | ADR-G7-001 (conflict resolution policy) must be APPROVED (not REQUIRES_REVISION) | P0 | Architecture | SRC-04, SRC-06 | FORENSIC-R-004, GAP-R-004, TRUTH-R-011, ADR-G7-001-C1 through C10 |
| G7-REQ-ARCH-002 | 12 conflict classes must be implemented as defined in the conflict matrix | P0 | Architecture | SRC-04, SRC-10 | FORENSIC-R-003, GAP-R-008, SYNC-R-030 through SYNC-R-037 |
| G7-REQ-ARCH-003 | Mobile framework selection must be completed (React Native, Flutter, Capacitor, or PWA) | P1 | Architecture | SRC-05 | TRUTH-R-012, UNKNOWN-001 |
| G7-REQ-ARCH-004 | Conflict resolution policy supports hybrid strategy: different policies per entity type | P1 | Architecture | SRC-10, ADR | SYNC-R-034 through SYNC-R-037, ADR-G7-001-C7, ADR-G7-001-C8 |

---

## 6. NORMALIZED NON-FUNCTIONAL REQUIREMENTS

| Norm ID | Description | Priority | Category | Sources | Raw Mappings |
|---------|-------------|----------|----------|---------|-------------|
| G7-REQ-PERF-001 | Mobile API response time must be under 200ms for entity list and detail endpoints | P1 | Performance | SRC-03, SRC-06 | G7-MOB-NFR-001, GAP-R-009, GAP-R-014 |
| G7-REQ-PERF-002 | Client-side storage quota management to prevent excessive local data accumulation | P2 | Performance | SRC-03 | G7-MOB-NFR-003 |
| G7-REQ-PERF-003 | Network state detection and adaptive sync behavior based on connectivity | P1 | Performance | SRC-03 | G7-MOB-NFR-004 |
| G7-REQ-PERF-004 | Background sync scheduling for periodic data refresh | P2 | Performance | SRC-03 | G7-MOB-NFR-005 |

---

## 7. NORMALIZED TEST REQUIREMENTS

| Norm ID | Description | Priority | Category | Sources | Raw Mappings |
|---------|-------------|----------|----------|---------|-------------|
| G7-REQ-TEST-001 | Unit tests for sync engine components (queue, retry, conflict detection) | P1 | Test | SRC-03 | G7-MOB-TEST-001 |
| G7-REQ-TEST-002 | Integration tests for pull sync (delta, pagination, cursor management) | P1 | Test | SRC-03 | G7-MOB-TEST-002 |
| G7-REQ-TEST-003 | Integration tests for push sync (batch, idempotency, partial failure) | P1 | Test | SRC-03 | G7-MOB-TEST-003 |
| G7-REQ-TEST-004 | Integration tests for conflict resolution (all 12 conflict classes) | P2 | Test | SRC-03, SRC-06 | G7-MOB-TEST-004, GAP-R-010 |
| G7-REQ-TEST-005 | End-to-end offline/online transition test | P2 | Test | SRC-03 | G7-MOB-TEST-005 |
| G7-REQ-TEST-006 | Performance tests for mobile APIs (response time < 200ms) | P2 | Test | SRC-03 | G7-MOB-TEST-006 |
| G7-REQ-TEST-007 | Tenant isolation sync tests verifying RLS on all sync tables | P0 | Test | SRC-11 | SEC-R-008, SEC-R-010, DOD-TI-1 through DOD-TI-4 |

---

## 8. NORMALIZED OBSERVABILITY REQUIREMENTS

| Norm ID | Description | Priority | Category | Sources | Raw Mappings |
|---------|-------------|----------|----------|---------|-------------|
| G7-REQ-OBS-001 | Sync metrics collection: pull/push counts, latency histograms, entity counts | P1 | Observability | SRC-05, SRC-11 | TRUTH-R-009, OBS-METRIC-1 through OBS-METRIC-6 |
| G7-REQ-OBS-002 | Conflict metrics: detected count, resolved count, resolution latency, breakdown by type/entity | P1 | Observability | SRC-11 | OBS-METRIC-7 through OBS-METRIC-11 |
| G7-REQ-OBS-003 | Queue metrics: depth gauge, retry count, dead letter count, processing time | P1 | Observability | SRC-11 | OBS-METRIC-12 through OBS-METRIC-15 |
| G7-REQ-OBS-004 | Error metrics: sync error count by type, timeout count, auth failure count | P1 | Observability | SRC-11 | OBS-METRIC-16 through OBS-METRIC-18 |
| G7-REQ-OBS-005 | Alerting: high conflict rate (>10%), queue depth threshold, sync latency SLA, auth failures, tenant isolation violations | P1 | Observability | SRC-11 | OBS-ALERT-1 through OBS-ALERT-5 |
| G7-REQ-OBS-006 | Dashboards: sync operations, conflict resolution, queue health, error rate | P2 | Observability | SRC-11 | OBS-DASH-1 through OBS-DASH-4 |
| G7-REQ-OBS-007 | Structured logging: all sync operations to mobile_sync_log, all conflicts to mobile_conflict_log, correlation IDs | P1 | Observability | SRC-11 | OBS-LOG-1 through OBS-LOG-3 |

---

## 9. NORMALIZED ISOLATION REQUIREMENTS

| Norm ID | Description | Priority | Category | Sources | Raw Mappings |
|---------|-------------|----------|----------|---------|-------------|
| G7-REQ-ISO-001 | Tenant-scoped cursors: tenant A cursor cannot be used for tenant B | P0 | Security | SRC-10 | ISO-TENANT-1, ISO-TENANT-2 |
| G7-REQ-ISO-002 | Device-scoped sync state: each device maintains own cursor and sync state independently | P1 | Security | SRC-10 | ISO-DEVICE-1, ISO-DEVICE-2 |
| G7-REQ-ISO-003 | User-device binding: each device bound to one user, user switch clears cache and invalidates cursors | P1 | Security | SRC-10 | ISO-USER-1 |
| G7-REQ-ISO-004 | Failure isolation: one mutation failure does not affect processing of any other mutation | P0 | Security | SRC-10 | ISO-FAILURE-1, ISO-CONFLICT-1 |
| G7-REQ-ISO-005 | Network failure isolation: push failures do not affect pull cursor state and vice versa | P0 | Security | SRC-10 | ISO-NETWORK-1, ISO-NETWORK-2 |
| G7-REQ-ISO-006 | Max devices per user: 5 (configurable) | P2 | Security | SRC-10 | ISO-CONFIG-1 |

---

## 10. NORMALIZED SUMMARY

| Category | Count | IDs |
|----------|-------|-----|
| API | 9 | G7-REQ-API-001 through API-009 |
| Sync | 17 | G7-REQ-SYNC-001 through SYNC-017 |
| Auth | 2 | G7-REQ-AUTH-001, AUTH-002 |
| Offline | 2 | G7-REQ-OFF-001, OFF-002 |
| Data | 5 | G7-REQ-DATA-001 through DATA-005 |
| Security | 6 | G7-REQ-SEC-001 through SEC-006 |
| Architecture | 4 | G7-REQ-ARCH-001 through ARCH-004 |
| Performance | 4 | G7-REQ-PERF-001 through PERF-004 |
| Test | 7 | G7-REQ-TEST-001 through TEST-007 |
| Observability | 7 | G7-REQ-OBS-001 through OBS-007 |
| Isolation | 6 | G7-REQ-ISO-001 through ISO-006 |
| **TOTAL** | **69** | |

**NOTE:** This is the NORMALIZED unique requirement count after deduplication of 300 raw items across all sources.

---

## 11. PRIORITY DISTRIBUTION

| Priority | Count | Percentage |
|----------|-------|------------|
| P0 (BLOCKER) | 20 | 29.0% |
| P1 (CRITICAL) | 33 | 47.8% |
| P2 (HIGH) | 14 | 20.3% |
| P3 (MEDIUM) | 2 | 2.9% |
| **TOTAL** | **69** | 100% |

---

*Generated: 2026-08-12*
*Phase 3 of G7 Requirements Reconciliation*
