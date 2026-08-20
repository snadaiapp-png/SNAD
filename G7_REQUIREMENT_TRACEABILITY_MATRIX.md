# G7 REQUIREMENT TRACEABILITY MATRIX

> **Report ID:** G7-REQ-TRACE-V1
> **Date:** 2026-08-12
> **Status:** DRAFT — NOT APPROVED
> **Purpose:** Trace every normalized requirement to its sources, implementation, tests, and acceptance criteria.

---

## 1. TRACEABILITY LEGEND

| Symbol | Meaning |
|--------|---------|
| ✅ | Fully traced (source + implementation + test) |
| 🔶 | Partially traced (source exists, implementation partial) |
| ❌ | Untraced (source exists, no implementation) |
| ⬜ | Not applicable |

---

## 2. FULL TRACEABILITY MATRIX

### 2.1 API Requirements

| Norm ID | Description | Source | Impl Status | Code Evidence | Test | Gate | DoD |
|---------|-------------|--------|-------------|---------------|------|------|-----|
| G7-REQ-API-001 | Entity List API | SRC-03, SRC-15 | ❌ MISSING | 0 mobile endpoints | ❌ NONE | GATE-05 ✅ (defined) | DOD-API-1 ❌ |
| G7-REQ-API-002 | Entity Detail API | SRC-03, SRC-15 | ❌ MISSING | 0 mobile endpoints | ❌ NONE | GATE-05 ✅ (defined) | DOD-API-1 ❌ |
| G7-REQ-API-003 | Delta Sync Pull | SRC-03, SRC-10, SRC-15 | ❌ MISSING | 0 sync endpoints | ❌ NONE | GATE-08 ❌ | DOD-API-1 ❌ |
| G7-REQ-API-004 | Batch Sync Push | SRC-03, SRC-10, SRC-15 | ❌ MISSING | 0 sync endpoints | ❌ NONE | GATE-11 ❌ | DOD-API-1 ❌ |
| G7-REQ-API-005 | Sync Status | SRC-03, SRC-15 | ❌ MISSING | 0 sync endpoints | ❌ NONE | GATE-08 ❌ | DOD-API-1 ❌ |
| G7-REQ-API-006 | Device Registration | SRC-03, SRC-11, SRC-15 | ❌ MISSING | 0 device endpoints | ❌ NONE | GATE-13 ❌ | DOD-SEC-2 ❌ |
| G7-REQ-API-007 | Conflict List | SRC-03, SRC-15 | ❌ MISSING | 0 conflict endpoints | ❌ NONE | GATE-12 ❌ | DOD-API-1 ❌ |
| G7-REQ-API-008 | Conflict Resolve | SRC-03, SRC-15 | ❌ MISSING | 0 conflict endpoints | ❌ NONE | GATE-12 ❌ | DOD-API-1 ❌ |
| G7-REQ-API-009 | Conflict Skip | SRC-03, SRC-15 | ❌ MISSING | 0 conflict endpoints | ❌ NONE | GATE-12 ❌ | DOD-API-1 ❌ |

### 2.2 Sync Requirements

| Norm ID | Description | Source | Impl Status | Code Evidence | Test | Gate | DoD |
|---------|-------------|--------|-------------|---------------|------|------|-----|
| G7-REQ-SYNC-001 | Sync Engine | SRC-03, SRC-10 | ❌ MISSING | `SyncEngine.java` empty | ❌ NONE | GATE-08 ❌ | DOD-CODE-1 ❌ |
| G7-REQ-SYNC-002 | Delta Pull | SRC-03, SRC-10 | ❌ MISSING | No cursor sync | ❌ NONE | GATE-08 ❌ | DOD-CODE-1 ❌ |
| G7-REQ-SYNC-003 | Mutation Queue | SRC-03, SRC-10 | ❌ MISSING | No queue impl | ❌ NONE | GATE-09 ❌ | DOD-CODE-1 ❌ |
| G7-REQ-SYNC-004 | Cursor Invalidation | SRC-03, SRC-10 | ❌ MISSING | No cursor invalidation | ❌ NONE | GATE-08 ❌ | DOD-CODE-1 ❌ |
| G7-REQ-SYNC-005 | Conflict Detection | SRC-03, SRC-10 | ❌ MISSING | No version-based detection | ❌ NONE | GATE-12 ❌ | DOD-CODE-1 ❌ |
| G7-REQ-SYNC-006 | Conflict Resolution | SRC-03, SRC-10 | ❌ MISSING | No resolution logic | ❌ NONE | GATE-12 ❌ | DOD-CODE-1 ❌ |
| G7-REQ-SYNC-007 | Retry/Backoff | SRC-03, SRC-10 | ❌ MISSING | No retry logic | ❌ NONE | GATE-09 ❌ | DOD-CODE-1 ❌ |
| G7-REQ-SYNC-008 | Idempotency | SRC-03, SRC-10, SRC-11 | 🔶 PARTIAL | `IdempotencyService` EXISTS (web) | 🔶 PARTIAL | GATE-10 ❌ | DOD-SEC-9 ❌ |
| G7-REQ-SYNC-009 | Conflict Isolation | SRC-10 | ❌ MISSING | No batch processing | ❌ NONE | GATE-12 ❌ | DOD-CODE-1 ❌ |
| G7-REQ-SYNC-010 | Delete Conflicts | SRC-10 | ❌ MISSING | No delete conflict handling | ❌ NONE | GATE-12 ❌ | DOD-CODE-1 ❌ |
| G7-REQ-SYNC-011 | Full Resync | SRC-10 | ❌ MISSING | No resync logic | ❌ NONE | GATE-17 ❌ | DOD-CODE-1 ❌ |
| G7-REQ-SYNC-012 | Crash Recovery | SRC-10 | ❌ MISSING | No recovery logic | ❌ NONE | GATE-17 ❌ | DOD-CODE-1 ❌ |
| G7-REQ-SYNC-013 | Sequence Gap | SRC-10 | ❌ MISSING | No gap detection | ❌ NONE | GATE-12 ❌ | DOD-CODE-1 ❌ |
| G7-REQ-SYNC-014 | Client Timeout | SRC-10 | ❌ MISSING | No client timeout config | ❌ NONE | GATE-08 ❌ | DOD-CODE-1 ❌ |
| G7-REQ-SYNC-015 | Entity Coverage | SRC-10 | ❌ MISSING | No entity sync | ❌ NONE | GATE-08 ❌ | DOD-CODE-1 ❌ |
| G7-REQ-SYNC-016 | Server Authority | SRC-03, SRC-10 | ❌ MISSING | No server-authoritative logic | ❌ NONE | GATE-12 ❌ | DOD-CODE-1 ❌ |
| G7-REQ-SYNC-017 | Per-Mutation ACK | SRC-10 | ❌ MISSING | No batch processing | ❌ NONE | GATE-08 ❌ | DOD-CODE-1 ❌ |

### 2.3 Auth Requirements

| Norm ID | Description | Source | Impl Status | Code Evidence | Test | Gate | DoD |
|---------|-------------|--------|-------------|---------------|------|------|-----|
| G7-REQ-AUTH-001 | Mobile Auth Flow | SRC-03, SRC-11 | 🔶 PARTIAL | JWT EXISTS, mobile caching MISSING | 🔶 PARTIAL | GATE-07 ✅ (defined) | DOD-SEC-3 ❌ |
| G7-REQ-AUTH-002 | Offline Token | SRC-10, SRC-11 | ❌ MISSING | No offline token handling | ❌ NONE | GATE-07 ❌ | DOD-SEC-3 ❌ |

### 2.4 Offline Requirements

| Norm ID | Description | Source | Impl Status | Code Evidence | Test | Gate | DoD |
|---------|-------------|--------|-------------|---------------|------|------|-----|
| G7-REQ-OFF-001 | Entity Subset | SRC-03, SRC-05 | 🔶 PARTIAL | Partial definition in baseline | ❌ NONE | GATE-06 ❌ | DOD-CODE-1 ❌ |
| G7-REQ-OFF-002 | Eligibility Rules | SRC-03 | ❌ MISSING | No eligibility rules | ❌ NONE | GATE-06 ❌ | DOD-CODE-1 ❌ |

### 2.5 Data Requirements

| Norm ID | Description | Source | Impl Status | Code Evidence | Test | Gate | DoD |
|---------|-------------|--------|-------------|---------------|------|------|-----|
| G7-REQ-DATA-001 | Sync Tables (4) | SRC-03, SRC-06, SRC-16 | ❌ MISSING | 0 tables exist | ❌ NONE | GATE-04 ✅ (defined) | DOD-DB-1 ❌ |
| G7-REQ-DATA-002 | Change Tracking | SRC-03, SRC-06 | 🔶 PARTIAL | `version` exists, `updated_at` unclear | 🔶 PARTIAL | GATE-04 ✅ (defined) | DOD-DB-2 ❌ |
| G7-REQ-DATA-003 | Local Storage Schema | SRC-03, SRC-10 | ❌ MISSING | No client schema | ❌ NONE | GATE-06 ❌ | DOD-DB-1 ❌ |
| G7-REQ-DATA-004 | Sync Audit Trail | SRC-03, SRC-06 | ❌ MISSING | No mobile_sync_log | ❌ NONE | GATE-15 ❌ | DOD-OBS-2 ❌ |
| G7-REQ-DATA-005 | Conflict Log | SRC-03, SRC-06 | ❌ MISSING | No mobile_conflict_log | ❌ NONE | GATE-12 ❌ | DOD-OBS-2 ❌ |

### 2.6 Security Requirements

| Norm ID | Description | Source | Impl Status | Code Evidence | Test | Gate | DoD |
|---------|-------------|--------|-------------|---------------|------|------|-----|
| G7-REQ-SEC-001 | Offline Encryption | SRC-03, SRC-06, SRC-11 | ❌ MISSING | No encryption strategy | ❌ NONE | GATE-13 ❌ | DOD-SEC-1 ❌ |
| G7-REQ-SEC-002 | Token Caching | SRC-03, SRC-11 | ❌ MISSING | No mobile token caching | ❌ NONE | GATE-13 ❌ | DOD-SEC-3 ❌ |
| G7-REQ-SEC-003 | Device Registration | SRC-03, SRC-11 | ❌ MISSING | No device registry | ❌ NONE | GATE-13 ❌ | DOD-SEC-2 ❌ |
| G7-REQ-SEC-004 | Offline Auth | SRC-03, SRC-06, SRC-11 | ❌ MISSING | No offline RBAC | ❌ NONE | GATE-13 ❌ | DOD-SEC-3 ❌ |
| G7-REQ-SEC-005 | Transport Security | SRC-03, SRC-11 | ✅ EXISTS | HTTPS enforced | ✅ EXISTS | GATE-13 🔶 | DOD-SEC-5 ❌ |
| G7-REQ-SEC-006 | Tenant Isolation | SRC-03, SRC-11 | 🔶 PARTIAL | RLS on CRM tables, NOT on sync tables | 🔶 PARTIAL | GATE-14 ❌ | DOD-TI-1 ❌ |

### 2.7 Architecture Requirements

| Norm ID | Description | Source | Impl Status | Code Evidence | Test | Gate | DoD |
|---------|-------------|--------|-------------|---------------|------|------|-----|
| G7-REQ-ARCH-001 | ADR Approval | SRC-04, SRC-06 | ❌ NOT APPROVED | REQUIRES_REVISION | ⬜ N/A | GATE-03 🔶 | DOD-ARCH-1 ❌ |
| G7-REQ-ARCH-002 | 12 Conflict Classes | SRC-04, SRC-10 | 🔶 DEFINED | Classes defined, no impl | ❌ NONE | GATE-12 ❌ | DOD-CODE-1 ❌ |
| G7-REQ-ARCH-003 | Framework Selection | SRC-05 | ❌ UNKNOWN | No selection made | ⬜ N/A | ⬜ N/A | ⬜ N/A |
| G7-REQ-ARCH-004 | Hybrid Strategy | SRC-10, ADR | 🔶 DEFINED | Defined in ADR | ❌ NONE | GATE-12 ❌ | DOD-CODE-1 ❌ |

### 2.8 Performance Requirements

| Norm ID | Description | Source | Impl Status | Code Evidence | Test | Gate | DoD |
|---------|-------------|--------|-------------|---------------|------|------|-----|
| G7-REQ-PERF-001 | <200ms Response | SRC-03, SRC-06 | ❌ NOT MEASURED | No mobile APIs to measure | ❌ NONE | GATE-15 ❌ | DOD-API-4 ❌ |
| G7-REQ-PERF-002 | Storage Quota | SRC-03 | ❌ MISSING | No quota management | ❌ NONE | ⬜ N/A | ⬜ N/A |
| G7-REQ-PERF-003 | Network Detection | SRC-03 | ❌ MISSING | No network detection | ❌ NONE | ⬜ N/A | ⬜ N/A |
| G7-REQ-PERF-004 | Background Sync | SRC-03 | ❌ MISSING | No background sync | ❌ NONE | ⬜ N/A | ⬜ N/A |

### 2.9 Test Requirements

| Norm ID | Description | Source | Impl Status | Code Evidence | Test | Gate | DoD |
|---------|-------------|--------|-------------|---------------|------|------|-----|
| G7-REQ-TEST-001 | Unit Tests | SRC-03 | ❌ MISSING | 0 G7 tests | ❌ NONE | GATE-16 ❌ | DOD-TEST-1 ❌ |
| G7-REQ-TEST-002 | Pull Sync Tests | SRC-03 | ❌ MISSING | 0 G7 tests | ❌ NONE | GATE-16 ❌ | DOD-TEST-1 ❌ |
| G7-REQ-TEST-003 | Push Sync Tests | SRC-03 | ❌ MISSING | 0 G7 tests | ❌ NONE | GATE-16 ❌ | DOD-TEST-1 ❌ |
| G7-REQ-TEST-004 | Conflict Tests | SRC-03, SRC-06 | ❌ MISSING | 0 G7 tests | ❌ NONE | GATE-16 ❌ | DOD-TEST-1 ❌ |
| G7-REQ-TEST-005 | E2E Test | SRC-03 | ❌ MISSING | 0 G7 tests | ❌ NONE | GATE-16 ❌ | DOD-TEST-1 ❌ |
| G7-REQ-TEST-006 | Performance Tests | SRC-03 | ❌ MISSING | 0 G7 tests | ❌ NONE | GATE-16 ❌ | DOD-TEST-1 ❌ |
| G7-REQ-TEST-007 | Tenant Isolation Tests | SRC-11 | ❌ MISSING | 0 G7 tests | ❌ NONE | GATE-14 ❌ | DOD-TI-4 ❌ |

### 2.10 Observability Requirements

| Norm ID | Description | Source | Impl Status | Code Evidence | Test | Gate | DoD |
|---------|-------------|--------|-------------|---------------|------|------|-----|
| G7-REQ-OBS-001 | Sync Metrics | SRC-05, SRC-11 | ❌ MISSING | No sync metrics | ❌ NONE | GATE-15 ❌ | DOD-OBS-1 ❌ |
| G7-REQ-OBS-002 | Conflict Metrics | SRC-11 | ❌ MISSING | No conflict metrics | ❌ NONE | GATE-15 ❌ | DOD-OBS-1 ❌ |
| G7-REQ-OBS-003 | Queue Metrics | SRC-11 | ❌ MISSING | No queue metrics | ❌ NONE | GATE-15 ❌ | DOD-OBS-1 ❌ |
| G7-REQ-OBS-004 | Error Metrics | SRC-11 | ❌ MISSING | No error metrics | ❌ NONE | GATE-15 ❌ | DOD-OBS-1 ❌ |
| G7-REQ-OBS-005 | Alerting | SRC-11 | ❌ MISSING | No alerts | ❌ NONE | GATE-15 ❌ | DOD-OBS-3 ❌ |
| G7-REQ-OBS-006 | Dashboards | SRC-11 | ❌ MISSING | No dashboards | ❌ NONE | GATE-15 ❌ | DOD-OBS-4 ❌ |
| G7-REQ-OBS-007 | Structured Logging | SRC-11 | ❌ MISSING | No structured logging | ❌ NONE | GATE-15 ❌ | DOD-OBS-2 ❌ |

### 2.11 Isolation Requirements

| Norm ID | Description | Source | Impl Status | Code Evidence | Test | Gate | DoD |
|---------|-------------|--------|-------------|---------------|------|------|-----|
| G7-REQ-ISO-001 | Tenant-Scoped Cursors | SRC-10 | 🔶 PARTIAL | CursorCodec has tenant hash | 🔶 PARTIAL | GATE-14 ❌ | DOD-TI-1 ❌ |
| G7-REQ-ISO-002 | Device-Scoped State | SRC-10 | ❌ MISSING | No device isolation | ❌ NONE | GATE-14 ❌ | DOD-TI-1 ❌ |
| G7-REQ-ISO-003 | User-Device Binding | SRC-10 | ❌ MISSING | No user-device binding | ❌ NONE | GATE-14 ❌ | DOD-TI-1 ❌ |
| G7-REQ-ISO-004 | Failure Isolation | SRC-10 | ❌ MISSING | No batch processing | ❌ NONE | GATE-14 ❌ | DOD-TI-1 ❌ |
| G7-REQ-ISO-005 | Network Isolation | SRC-10 | ❌ MISSING | No network isolation | ❌ NONE | GATE-14 ❌ | DOD-TI-1 ❌ |
| G7-REQ-ISO-006 | Max Devices | SRC-10 | ❌ MISSING | No device limit | ❌ NONE | GATE-14 ❌ | DOD-TI-1 ❌ |

---

## 3. TRACEABILITY SUMMARY

| Status | Count | Percentage |
|--------|-------|------------|
| ✅ Fully Traced | 1 | 1.4% |
| 🔶 Partially Traced | 8 | 11.6% |
| ❌ Untraced | 60 | 87.0% |
| **TOTAL** | **69** | 100% |

---

## 4. TRACEABILITY BLOCKERS

| Blocker | Affected Requirements | Impact |
|---------|----------------------|--------|
| ADR-G7-001 not approved | SYNC-005, SYNC-006, SYNC-009, SYNC-010, ARCH-002, ARCH-004 | 6 requirements blocked |
| No mobile framework selected | ALL client-side requirements | 15+ requirements affected |
| No encryption strategy defined | SEC-001, SEC-002 | 2 requirements blocked |

**P0_TRACEABILITY_BLOCKER: YES** — 20 P0 requirements with 0 fully traced.

---

*Generated: 2026-08-12*
*Phase 12 of G7 Requirements Reconciliation*
