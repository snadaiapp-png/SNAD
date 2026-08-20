# G7 REQUIREMENT DISPOSITION REGISTER

> **Report ID:** G7-REQ-DISPOSITION-V1
> **Date:** 2026-08-12
> **Status:** DRAFT — NOT APPROVED
> **Purpose:** Final disposition of every normalized requirement — ACCEPT, DEFER, REJECT, or SPLIT.

---

## 1. DISPOSITION CATEGORIES

| Disposition | Meaning |
|-------------|---------|
| **ACCEPT** | Requirement is valid, in scope, and must be implemented |
| **DEFER** | Requirement is valid but deferred to a future release |
| **REJECT** | Requirement is invalid, out of scope, or superseded |
| **SPLIT** | Requirement must be split into multiple sub-requirements |

---

## 2. REQUIREMENT DISPOSITIONS

### 2.1 API Requirements

| Norm ID | Description | Disposition | Rationale |
|---------|-------------|-------------|-----------|
| G7-REQ-API-001 | Entity List API | **ACCEPT** | Core mobile data access |
| G7-REQ-API-002 | Entity Detail API | **ACCEPT** | Core mobile data access |
| G7-REQ-API-003 | Delta Sync Pull | **ACCEPT** | Foundational read path |
| G7-REQ-API-004 | Batch Sync Push | **ACCEPT** | Foundational write path |
| G7-REQ-API-005 | Sync Status | **ACCEPT** | Needed for sync state visibility |
| G7-REQ-API-006 | Device Registration | **ACCEPT** | Security requirement |
| G7-REQ-API-007 | Conflict List | **ACCEPT** | Required for conflict resolution UI |
| G7-REQ-API-008 | Conflict Resolve | **ACCEPT** | Required for conflict resolution |
| G7-REQ-API-009 | Conflict Skip | **ACCEPT** | Required for deferred conflict handling |

### 2.2 Sync Requirements

| Norm ID | Description | Disposition | Rationale |
|---------|-------------|-------------|-----------|
| G7-REQ-SYNC-001 | Sync Engine | **ACCEPT** | Core component |
| G7-REQ-SYNC-002 | Delta Pull | **ACCEPT** | Core sync behavior |
| G7-REQ-SYNC-003 | Mutation Queue | **ACCEPT** | Core offline support |
| G7-REQ-SYNC-004 | Cursor Invalidation | **ACCEPT** | Data consistency |
| G7-REQ-SYNC-005 | Conflict Detection | **ACCEPT** | Data integrity |
| G7-REQ-SYNC-006 | Conflict Resolution | **ACCEPT** | Data integrity |
| G7-REQ-SYNC-007 | Retry/Backoff | **ACCEPT** | Reliability |
| G7-REQ-SYNC-008 | Idempotency | **ACCEPT** | Deduplication |
| G7-REQ-SYNC-009 | Conflict Isolation | **ACCEPT** | Batch integrity |
| G7-REQ-SYNC-010 | Delete Conflicts | **ACCEPT** | Data integrity |
| G7-REQ-SYNC-011 | Full Resync | **ACCEPT** | Recovery |
| G7-REQ-SYNC-012 | Crash Recovery | **ACCEPT** | Reliability |
| G7-REQ-SYNC-013 | Sequence Gap | **DEFER** | P2, can be added after initial release |
| G7-REQ-SYNC-014 | Client Timeout | **ACCEPT** | Network reliability |
| G7-REQ-SYNC-015 | Entity Coverage | **ACCEPT** | Completeness |
| G7-REQ-SYNC-016 | Server Authority | **ACCEPT** | Data integrity |
| G7-REQ-SYNC-017 | Per-Mutation ACK | **ACCEPT** | Batch processing |

### 2.3 Auth Requirements

| Norm ID | Description | Disposition | Rationale |
|---------|-------------|-------------|-----------|
| G7-REQ-AUTH-001 | Mobile Auth Flow | **ACCEPT** | Authentication foundation |
| G7-REQ-AUTH-002 | Offline Token | **ACCEPT** | Offline support |

### 2.4 Offline Requirements

| Norm ID | Description | Disposition | Rationale |
|---------|-------------|-------------|-----------|
| G7-REQ-OFF-001 | Entity Subset | **ACCEPT** | Scope definition |
| G7-REQ-OFF-002 | Eligibility Rules | **DEFER** | P1, can be refined after initial entity sync works |

### 2.5 Data Requirements

| Norm ID | Description | Disposition | Rationale |
|---------|-------------|-------------|-----------|
| G7-REQ-DATA-001 | Sync Tables | **ACCEPT** | Foundation |
| G7-REQ-DATA-002 | Change Tracking | **ACCEPT** | Foundation |
| G7-REQ-DATA-003 | Local Storage Schema | **ACCEPT** | Client-side foundation |
| G7-REQ-DATA-004 | Sync Audit Trail | **ACCEPT** | Observability |
| G7-REQ-DATA-005 | Conflict Log | **ACCEPT** | Observability |

### 2.6 Security Requirements

| Norm ID | Description | Disposition | Rationale |
|---------|-------------|-------------|-----------|
| G7-REQ-SEC-001 | Offline Encryption | **ACCEPT** | Security critical |
| G7-REQ-SEC-002 | Token Caching | **ACCEPT** | Security |
| G7-REQ-SEC-003 | Device Registration | **ACCEPT** | Security |
| G7-REQ-SEC-004 | Offline Auth | **ACCEPT** | Security |
| G7-REQ-SEC-005 | Transport Security | **ACCEPT** | Already exists, verify |
| G7-REQ-SEC-006 | Tenant Isolation | **ACCEPT** | Security critical |

### 2.7 Architecture Requirements

| Norm ID | Description | Disposition | Rationale |
|---------|-------------|-------------|-----------|
| G7-REQ-ARCH-001 | ADR Approval | **ACCEPT** | Process gate |
| G7-REQ-ARCH-002 | 12 Conflict Classes | **ACCEPT** | Technical requirement |
| G7-REQ-ARCH-003 | Framework Selection | **ACCEPT** | Blocking decision |
| G7-REQ-ARCH-004 | Hybrid Strategy | **DEFER** | Can be added after base conflict resolution works |

### 2.8 Performance Requirements

| Norm ID | Description | Disposition | Rationale |
|---------|-------------|-------------|-----------|
| G7-REQ-PERF-001 | <200ms Response | **ACCEPT** | Performance target |
| G7-REQ-PERF-002 | Storage Quota | **DEFER** | P2, can be added after initial release |
| G7-REQ-PERF-003 | Network Detection | **DEFER** | P1, can be added after basic sync works |
| G7-REQ-PERF-004 | Background Sync | **DEFER** | P2, can be added after initial release |

### 2.9 Test Requirements

| Norm ID | Description | Disposition | Rationale |
|---------|-------------|-------------|-----------|
| G7-REQ-TEST-001 | Unit Tests | **ACCEPT** | Quality |
| G7-REQ-TEST-002 | Pull Sync Tests | **ACCEPT** | Quality |
| G7-REQ-TEST-003 | Push Sync Tests | **ACCEPT** | Quality |
| G7-REQ-TEST-004 | Conflict Tests | **ACCEPT** | Quality |
| G7-REQ-TEST-005 | E2E Test | **ACCEPT** | Quality |
| G7-REQ-TEST-006 | Performance Tests | **DEFER** | P2, after performance targets defined |
| G7-REQ-TEST-007 | Tenant Isolation Tests | **ACCEPT** | Security |

### 2.10 Observability Requirements

| Norm ID | Description | Disposition | Rationale |
|---------|-------------|-------------|-----------|
| G7-REQ-OBS-001 | Sync Metrics | **ACCEPT** | Operations |
| G7-REQ-OBS-002 | Conflict Metrics | **ACCEPT** | Operations |
| G7-REQ-OBS-003 | Queue Metrics | **ACCEPT** | Operations |
| G7-REQ-OBS-004 | Error Metrics | **ACCEPT** | Operations |
| G7-REQ-OBS-005 | Alerting | **ACCEPT** | Operations |
| G7-REQ-OBS-006 | Dashboards | **DEFER** | P2, after metrics collection works |
| G7-REQ-OBS-007 | Structured Logging | **ACCEPT** | Operations |

### 2.11 Isolation Requirements

| Norm ID | Description | Disposition | Rationale |
|---------|-------------|-------------|-----------|
| G7-REQ-ISO-001 | Tenant-Scoped Cursors | **ACCEPT** | Security |
| G7-REQ-ISO-002 | Device-Scoped State | **ACCEPT** | Multi-device support |
| G7-REQ-ISO-003 | User-Device Binding | **ACCEPT** | Security |
| G7-REQ-ISO-004 | Failure Isolation | **ACCEPT** | Reliability |
| G7-REQ-ISO-005 | Network Isolation | **ACCEPT** | Reliability |
| G7-REQ-ISO-006 | Max Devices | **DEFER** | P2, can be added after basic device management works |

---

## 3. DISPOSITION SUMMARY

| Disposition | Count | Percentage | IDs |
|-------------|-------|------------|-----|
| **ACCEPT** | 57 | 82.6% | All except DEFERred |
| **DEFER** | 10 | 14.5% | SYNC-013, OFF-002, ARCH-004, PERF-002, PERF-003, PERF-004, TEST-006, OBS-006, ISO-006 |
| **REJECT** | 0 | 0% | None |
| **SPLIT** | 0 | 0% | None |
| **TOTAL** | **69** | 100% | |

---

## 4. DEFERRED REQUIREMENTS DETAIL

| Norm ID | Description | Deferred To | Reason |
|---------|-------------|-------------|--------|
| G7-REQ-SYNC-013 | Sequence Gap Detection | v1.1 | P2, initial sync works without gap detection |
| G7-REQ-OFF-002 | Eligibility Rules | v1.1 | P1, initial sync covers all entities |
| G7-REQ-ARCH-004 | Hybrid Strategy | v1.1 | P1, base conflict resolution works first |
| G7-REQ-PERF-002 | Storage Quota | v1.1 | P2, monitor usage first |
| G7-REQ-PERF-003 | Network Detection | v1.1 | P1, basic connectivity check sufficient |
| G7-REQ-PERF-004 | Background Sync | v1.2 | P2, manual sync first |
| G7-REQ-TEST-006 | Performance Tests | v1.1 | P2, after performance targets defined |
| G7-REQ-OBS-006 | Dashboards | v1.1 | P2, after metrics collection works |
| G7-REQ-ISO-006 | Max Devices | v1.1 | P2, basic device management first |

**NOTE:** 10 requirements deferred = 57 requirements in scope for initial release.

---

*Generated: 2026-08-12*
*Phase 16 of G7 Requirements Reconciliation*
