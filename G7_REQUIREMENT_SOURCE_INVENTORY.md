# G7 REQUIREMENT SOURCE INVENTORY

> **Report ID:** G7-REQ-SRC-INV-V1
> **Date:** 2026-08-12
> **Status:** DRAFT — NOT APPROVED
> **Purpose:** Complete inventory of every G7 requirement extracted from every source document, with exact source attribution.

---

## 1. SOURCE DOCUMENTS

| Source ID | Document | Path | Lines | Authority Level |
|-----------|----------|------|-------|-----------------|
| SRC-01 | CRM Execution Data | `apps/web/app/crm/crm-execution-data.ts` | 129-137 | **CANONICAL** (product spec) |
| SRC-02 | G7 Identity Final | `G7_IDENTITY_FINAL.md` | Full | AUTHORED (identity lock) |
| SRC-03 | G7 Master Requirements Baseline | `G7_MASTER_REQUIREMENTS_BASELINE.md` | Full | DERIVED (prior reconciliation) |
| SRC-04 | G7 Forensic Extraction Report | `G7_FORENSIC_EXTRACTION_REPORT.md` | Full | DERIVED (forensic analysis) |
| SRC-05 | G7 Master Truth Report | `G7_MASTER_TRUTH_REPORT.md` | Full | DERIVED (truth reconciliation) |
| SRC-06 | G7 Master Gap Register | `G7_MASTER_GAP_REGISTER.md` | Full | DERIVED (gap analysis) |
| SRC-07 | G7 Implementation Backlog | `G7_IMPLEMENTATION_BACKLOG.md` | Full | DERIVED (work packages) |
| SRC-08 | G7 Acceptance Gates | `G7_ACCEPTANCE_GATES.md` | Full | DERIVED (gate criteria) |
| SRC-09 | G7 DoD Final | `G7_DOD_FINAL.md` | Full | DERIVED (completion criteria) |
| SRC-10 | G7 Sync Contract Truth | `G7_SYNC_CONTRACT_TRUTH.md` | Full | AUTHORED (sync contract) |
| SRC-11 | G7 Security Final Gate | `G7_SECURITY_FINAL_GATE.md` | Full | AUTHORED (security spec) |
| SRC-12 | G7 Forensic Extraction Dataset | `G7_FORENSIC_EXTRACTION_DATASET.md` | Full | DERIVED (codebase extraction) |

---

## 2. SOURCE-LEVEL REQUIREMENT EXTRACTION

### 2.1 SRC-01: crm-execution-data.ts (Lines 129-137) — CANONICAL

This is the product specification source of truth. It defines G7 scope at the highest authority level.

```
Line 129: "G7"
Line 130: "Mobile Offline Foundation" / "أساس الجوال"
Line 131: Dependencies: G1, G3
Line 132: "Mobile-optimized CRM entity APIs"
Line 133: "offline sync schema"
Line 134: "client-side offline storage architecture"
Line 135: "sync engine architecture"
Line 136: "mobile-specific auth flow"
Line 137: "offline entity subset"
```

**Extracted Scope Items (7):**

| ID | Scope Item | Canonical Form |
|----|-----------|----------------|
| SRC01-SI-001 | Mobile-optimized CRM entity APIs | Mobile-optimized CRM entity APIs |
| SRC01-SI-002 | Offline sync schema | Offline sync schema |
| SRC01-SI-003 | Client-side offline storage architecture | Client-side offline storage architecture |
| SRC01-SI-004 | Sync engine architecture | Sync engine architecture |
| SRC01-SI-005 | Mobile-specific auth flow | Mobile-specific auth flow |
| SRC01-SI-006 | Offline entity subset | Offline entity subset |
| SRC01-SI-007 | Entity-level offline eligibility rules | Entity-level offline eligibility rules (implied by "offline entity subset") |

**Note:** SRC-01 provides scope items, not individual requirements. Requirements are derived from these scope items through decomposition.

---

### 2.2 SRC-02: G7_IDENTITY_FINAL.md — IDENTITY LOCK

**Identity Definition:**
- G7 = أساس الجوال = Mobile Offline Foundation
- Scope: Mobile-optimized CRM APIs, offline sync schema, client-side storage architecture, sync engine architecture, mobile auth flow, offline entity subset
- Non-Scope: Native mobile UI, push notifications (G8), caller ID (G8), real-time collaboration
- Dependencies: G1 (COMPLETE), G3 (COMPLETE)

**Extracted Requirements from Identity:**

| ID | Requirement | Line Reference |
|----|------------|----------------|
| SRC02-R-001 | G7 identity is locked: Mobile Offline Foundation | Section 1 |
| SRC02-R-002 | Native mobile app UI is OUT OF SCOPE | Section 3 (Non-Scope) |
| SRC02-R-003 | Push notifications are OUT OF SCOPE (G8) | Section 3 (Non-Scope) |
| SRC02-R-004 | Caller identification is OUT OF SCOPE (G8) | Section 3 (Non-Scope) |
| SRC02-R-005 | Real-time collaboration is OUT OF SCOPE | Section 3 (Non-Scope) |
| SRC02-R-006 | G1 dependency must be COMPLETE | Section 4 |
| SRC02-R-007 | G3 dependency must be COMPLETE | Section 4 |

---

### 2.3 SRC-03: G7_MASTER_REQUIREMENTS_BASELINE.md — PRIOR RECONCILIATION

This document contains 39 individually enumerated requirements across 6 categories. This is the most detailed requirement source.

**Category A: Functional Requirements (10)**

| ID | Requirement | Priority | Status |
|----|------------|----------|--------|
| G7-MOB-FR-001 | Mobile-optimized CRM entity APIs | P0 | MISSING |
| G7-MOB-FR-002 | Offline sync schema | P0 | MISSING |
| G7-MOB-FR-003 | Delta pull with cursor | P0 | MISSING |
| G7-MOB-FR-004 | Batch push with idempotency | P0 | MISSING |
| G7-MOB-FR-005 | Mobile-specific auth flow | P0 | MISSING |
| G7-MOB-FR-006 | Offline entity subset definition | P1 | MISSING |
| G7-MOB-FR-007 | Entity-level offline eligibility rules | P1 | MISSING |
| G7-MOB-FR-008 | Conflict resolution policy (12 classes) | P0 | MISSING |
| G7-MOB-FR-009 | Server-authoritative state management | P1 | MISSING |
| G7-MOB-FR-010 | Client-side offline storage architecture | P0 | MISSING |

**Category B: Non-Functional Requirements (5)**

| ID | Requirement | Priority | Status |
|----|------------|----------|--------|
| G7-MOB-NFR-001 | Mobile API response time < 200ms | P1 | MISSING |
| G7-MOB-NFR-002 | Offline data encrypted at rest | P0 | MISSING |
| G7-MOB-NFR-003 | Client-side storage quota management | P2 | MISSING |
| G7-MOB-NFR-004 | Network state detection and adaptive sync | P1 | MISSING |
| G7-MOB-NFR-005 | Background sync scheduling | P2 | MISSING |

**Category C: Security Requirements (5)**

| ID | Requirement | Priority | Status |
|----|------------|----------|--------|
| G7-MOB-SEC-001 | Offline data encryption strategy | P0 | MISSING |
| G7-MOB-SEC-002 | Mobile token caching and refresh | P1 | MISSING |
| G7-MOB-SEC-003 | Device registration and binding | P2 | MISSING |
| G7-MOB-SEC-004 | Offline authorization enforcement | P1 | MISSING |
| G7-MOB-SEC-005 | Sync transport security (HTTPS) | P1 | MISSING |

**Category D: Sync Requirements (8)**

| ID | Requirement | Priority | Status |
|----|------------|----------|--------|
| G7-MOB-SYNC-001 | Client-side sync engine with queue | P0 | MISSING |
| G7-MOB-SYNC-002 | Delta sync with cursor-based pagination | P0 | MISSING |
| G7-MOB-SYNC-003 | Mutation queue with FIFO ordering | P1 | MISSING |
| G7-MOB-SYNC-004 | Cursor invalidation and full resync | P1 | MISSING |
| G7-MOB-SYNC-005 | Conflict detection (version-based) | P1 | MISSING |
| G7-MOB-SYNC-006 | Conflict resolution (auto-merge + manual) | P1 | MISSING |
| G7-MOB-SYNC-007 | Retry with exponential backoff | P2 | MISSING |
| G7-MOB-SYNC-008 | Idempotency for all sync mutations | P1 | MISSING |

**Category E: Data Requirements (5)**

| ID | Requirement | Priority | Status |
|----|------------|----------|--------|
| G7-MOB-DATA-001 | Sync metadata tables (4 tables) | P0 | MISSING |
| G7-MOB-DATA-002 | Change tracking columns on CRM tables | P0 | MISSING |
| G7-MOB-DATA-003 | Client-side local storage schema | P1 | MISSING |
| G7-MOB-DATA-004 | Sync audit trail (mobile_sync_log) | P2 | MISSING |
| G7-MOB-DATA-005 | Conflict log (mobile_conflict_log) | P2 | MISSING |

**Category F: Test Requirements (6)**

| ID | Requirement | Priority | Status |
|----|------------|----------|--------|
| G7-MOB-TEST-001 | Unit tests for sync engine components | P1 | MISSING |
| G7-MOB-TEST-002 | Integration tests for pull sync | P1 | MISSING |
| G7-MOB-TEST-003 | Integration tests for push sync | P1 | MISSING |
| G7-MOB-TEST-004 | Integration tests for conflict resolution | P2 | MISSING |
| G7-MOB-TEST-005 | End-to-end offline/online transition test | P2 | MISSING |
| G7-MOB-TEST-006 | Performance tests for mobile APIs | P2 | MISSING |

**Priority Summary from SRC-03:**
- P0: 12 (FR-001, FR-002, FR-003, FR-004, FR-005, FR-008, FR-010, NFR-002, SEC-001, SYNC-001, SYNC-002, DATA-001, DATA-002)
- P1: 13 (FR-006, FR-007, FR-009, NFR-001, NFR-004, SEC-002, SEC-004, SEC-005, SYNC-003, SYNC-004, SYNC-005, SYNC-006, SYNC-008, DATA-003, TEST-001, TEST-002, TEST-003)
- P2: 9 (NFR-003, NFR-005, SEC-003, SYNC-007, DATA-004, DATA-005, TEST-004, TEST-005, TEST-006)
- P3: 2 (implied by baseline but not enumerated)

**NOTE:** P0 count of 12 includes FR-001, FR-002, FR-003, FR-004, FR-005, FR-008, FR-010, NFR-002, SEC-001, SYNC-001, SYNC-002, DATA-001, DATA-002 = 13, not 12. This is a discrepancy to investigate.

---

### 2.4 SRC-04: G7_FORENSIC_EXTRACTION_REPORT.md — FORENSIC ANALYSIS

This document identifies 4 conflicting G7 definitions and provides forensic analysis.

**Extracted Requirements from Forensic Analysis:**

| ID | Requirement | Section |
|----|------------|---------|
| SRC04-R-001 | G7 must be resolved to a single canonical definition | Conflict Register |
| SRC04-R-002 | Mobile Offline Foundation is the selected interpretation | Conflict Resolution |
| SRC04-R-003 | 12 conflict classes must be implemented | Conflict Matrix |
| SRC04-R-004 | Conflict policy must be approved (ADR-G7-001) | ADR Status |
| SRC04-R-005 | Server-authoritative fields must be defined per entity | Conflict Matrix |
| SRC04-R-006 | Auto-merge must handle non-conflicting field changes | Conflict Matrix |
| SRC04-R-007 | Manual resolution must be available for conflicting fields | Conflict Matrix |
| SRC04-R-008 | Delete conflicts must favor server | Conflict Matrix |

---

### 2.5 SRC-05: G7_MASTER_TRUTH_REPORT.md — TRUTH RECONCILIATION

**Extracted Requirements from Truth Report:**

| ID | Requirement | Section |
|----|------------|---------|
| SRC05-R-001 | 9 new mobile APIs must be implemented | Section 8 |
| SRC05-R-002 | 4 new sync metadata tables must be created | Section 7 |
| SRC05-R-003 | Change tracking columns on all CRM tables | Section 7 |
| SRC05-R-004 | Client-side sync engine with queue, retry, conflict handling | Section 10 |
| SRC05-R-005 | Conflict resolution with 12 conflict classes | Section 11 |
| SRC05-R-006 | Mobile auth with token caching | Section 12 |
| SRC05-R-007 | Offline data encryption strategy | Section 12 |
| SRC05-R-008 | RLS must extend to sync tables | Section 12 |
| SRC05-R-009 | Sync metrics and observability | Section 13 |
| SRC05-R-010 | 26 tests must be implemented | Section 14 |
| SRC05-R-011 | ADR-G7-001 must be approved | Section 6 |
| SRC05-R-012 | Mobile framework must be selected | Section 15 |

---

### 2.6 SRC-06: G7_MASTER_GAP_REGISTER.md — GAP ANALYSIS

**Extracted Requirements from Gap Register (14 gaps → 14 requirements):**

| ID | Requirement | Gap | Severity | Priority |
|----|------------|-----|----------|----------|
| SRC06-R-001 | Mobile Sync API Layer (9 APIs) | GAP-001 | BLOCKER | P0 |
| SRC06-R-002 | Sync Metadata Schema (4 tables) | GAP-002 | BLOCKER | P0 |
| SRC06-R-003 | Change Tracking Columns | GAP-003 | BLOCKER | P0 |
| SRC06-R-004 | Conflict Resolution Policy (ADR) | GAP-004 | BLOCKER | P0 |
| SRC06-R-005 | Sync Engine (Client-Side) | GAP-005 | BLOCKER | P0 |
| SRC06-R-006 | Offline Data Encryption | GAP-006 | BLOCKER | P0 |
| SRC06-R-007 | Offline Authorization | GAP-007 | HIGH | P1 |
| SRC06-R-008 | Conflict Detection + Resolution | GAP-008 | HIGH | P1 |
| SRC06-R-009 | Mobile Entity APIs | GAP-009 | HIGH | P1 |
| SRC06-R-010 | Test Suite (26 tests) | GAP-010 | HIGH | P1 |
| SRC06-R-011 | Device Registry | GAP-011 | MEDIUM | P2 |
| SRC06-R-012 | Sync Log | GAP-012 | MEDIUM | P2 |
| SRC06-R-013 | Offline Entity Subset Definition | GAP-013 | MEDIUM | P1 |
| SRC06-R-014 | Performance Budget | GAP-014 | MEDIUM | P1 |

---

### 2.7 SRC-07: G7_IMPLEMENTATION_BACKLOG.md — WORK PACKAGES

**Extracted Requirements from Work Packages (12 WPs → mapped to requirement IDs):**

| WP | Requirement IDs | Objective |
|----|----------------|-----------|
| WP-A | G7-MOB-DATA-001, G7-MOB-DATA-002 | Foundation (schema) |
| WP-B | G7-MOB-SYNC-001 (local storage) | Local Persistence |
| WP-C | G7-MOB-SYNC-003, G7-MOB-SYNC-007, G7-MOB-SYNC-008 | Mutation Queue |
| WP-D | G7-MOB-FR-003, G7-MOB-SYNC-002, G7-MOB-SYNC-004 | Pull Sync |
| WP-E | G7-MOB-FR-004, G7-MOB-SYNC-003 | Push Sync |
| WP-F | G7-MOB-SYNC-008 | Idempotency |
| WP-G | G7-MOB-SYNC-005, G7-MOB-SYNC-006, G7-MOB-FR-008 | Conflict Resolution |
| WP-H | Conflict classes C3, C4, C12 | Delete/Recovery |
| WP-I | G7-MOB-SEC-001, SEC-002, SEC-003, SEC-004, SEC-005 | Security |
| WP-J | G7-MOB-DATA-004 | Observability |
| WP-K | G7-MOB-TEST-001 through TEST-006 | Testing |
| WP-L | All | Release |

**Unmapped Requirements (not referenced in any WP):**
- G7-MOB-FR-001 (Mobile-optimized CRM entity APIs) — partially covered by WP-D, WP-E
- G7-MOB-FR-002 (Offline sync schema) — covered by WP-A
- G7-MOB-FR-005 (Mobile-specific auth flow) — covered by WP-I
- G7-MOB-FR-006 (Offline entity subset definition) — **NOT MAPPED**
- G7-MOB-FR-007 (Entity-level offline eligibility rules) — **NOT MAPPED**
- G7-MOB-FR-009 (Server-authoritative state management) — partially in WP-G
- G7-MOB-FR-010 (Client-side offline storage) — covered by WP-B
- G7-MOB-NFR-001 (Response time < 200ms) — **NOT MAPPED** (WP-K only)
- G7-MOB-NFR-002 (Offline data encryption) — covered by WP-B, WP-I
- G7-MOB-NFR-003 (Storage quota management) — **NOT MAPPED**
- G7-MOB-NFR-004 (Network state detection) — **NOT MAPPED**
- G7-MOB-NFR-005 (Background sync scheduling) — **NOT MAPPED**
- G7-MOB-DATA-003 (Client-side local storage schema) — covered by WP-B
- G7-MOB-DATA-005 (Conflict log) — covered by WP-G

**Unmapped Count: 6 requirements have no work package coverage.**

---

### 2.8 SRC-08: G7_ACCEPTANCE_GATES.md — GATE CRITERIA

**Extracted Requirements from Acceptance Gates (18 gates):**

| Gate | Requirement Implied | Status |
|------|-------------------|--------|
| GATE-01 | G7 identity locked | PASS |
| GATE-02 | Requirements reconciled and baselined | PASS |
| GATE-03 | Architecture stable and approved (ADR) | CONDITIONAL |
| GATE-04 | Data model defined (4 tables) | PASS |
| GATE-05 | API contracts defined (9 APIs) | PASS |
| GATE-06 | Client storage architecture defined | NOT_STARTED |
| GATE-07 | Mobile auth flow defined | PASS |
| GATE-08 | Delta pull API functional | NOT_STARTED |
| GATE-09 | Mutation queue functional | NOT_STARTED |
| GATE-10 | Idempotency verified | NOT_STARTED |
| GATE-11 | Batch push API functional | NOT_STARTED |
| GATE-12 | Conflict detection and resolution functional (12 classes) | NOT_STARTED |
| GATE-13 | All security requirements met | NOT_STARTED |
| GATE-14 | RLS enforced on all sync tables | NOT_STARTED |
| GATE-15 | Sync operations observable | NOT_STARTED |
| GATE-16 | All tests pass | NOT_STARTED |
| GATE-17 | Recovery scenarios handled | NOT_STARTED |
| GATE-18 | All gates pass (production readiness) | NOT_STARTED |

**Note:** GATE-02 claims "39 requirements baselined" — this is the source of the prior 39 count.

---

### 2.9 SRC-09: G7_DOD_FINAL.md — COMPLETION CRITERIA

**Extracted Requirements from DoD (46 criteria across 11 categories):**

| Category | Criteria Count | Key Requirements |
|----------|---------------|------------------|
| Requirements | 4 | All 39 verified, P0/P1 implemented, no conflicts |
| Architecture | 4 | ADR approved, C2/C3 decisions approved, no drift |
| Code | 4 | 12 WPs implemented, code review, no critical issues |
| Database | 5 | 4 tables, change tracking, RLS, Flyway, no conflicts |
| API | 4 | 9 APIs, contracts, tests, <200ms response |
| Tests | 4 | 26 tests, all passing, >80% coverage, no flaky |
| Security | 5 | Encryption, device registration, mobile auth, tenant isolation, audit |
| Tenant Isolation | 4 | RLS on sync tables, cross-tenant blocked, context enforced, tests |
| Observability | 4 | Metrics, logging, alerts, dashboard |
| Documentation | 4 | API docs, architecture docs, runbook, changelog |
| Dependencies | 4 | G1/G3 verified, no blocking unknowns, risks mitigated |
| **Total** | **46** | |

**Note:** DoD line 9 says "All 39 requirements verified or deferred" — confirms 39 count.

---

### 2.10 SRC-10: G7_SYNC_CONTRACT_TRUTH.md — SYNC CONTRACT

**Extracted Requirements from Sync Contract (detailed behavioral specs):**

| ID | Requirement | Section |
|----|------------|---------|
| SRC10-R-001 | Mutation envelope must include: idempotency_key, entity_type, entity_id, operation, base_version, payload, timestamp, device_id | Section 2 |
| SRC10-R-002 | Local storage: SQLite (mobile), IndexedDB (web/PWA) | Section 3 |
| SRC10-R-003 | Queue table schema: pending_mutations with status state machine | Section 3.3 |
| SRC10-R-004 | Queue ordering: FIFO per entity type, sequence_number | Section 4 |
| SRC10-R-005 | State machine: LOCAL_CHANGE → QUEUED → READY → SENT → (ACKNOWLEDGED / CONFLICT / RETRYABLE_FAILURE / PERMANENT_FAILURE) | Section 5 |
| SRC10-R-006 | Pull: cursor-based, incremental, max page 1000 | Section 6 |
| SRC10-R-007 | Push: batch, per-mutation independent processing | Section 7 |
| SRC10-R-008 | Cursor: base64-encoded, one per entity type per device | Section 8 |
| SRC10-R-009 | Cursor invalidation: full resync on schema change, token expiry, explicit request | Section 8.3 |
| SRC10-R-010 | Acknowledgement: per-mutation, removes from queue on success | Section 9 |
| SRC10-R-011 | Retry: exponential backoff (1s→2s→4s→8s→16s), ±20% jitter, max 5 attempts | Section 10 |
| SRC10-R-012 | Retryable errors: 500, 502, 503, 408, network timeout | Section 10.3 |
| SRC10-R-013 | Non-retryable errors: 401, 403, 404, 412 | Section 10.4 |
| SRC10-R-014 | Ordering: FIFO per entity type, sequence gap detection | Section 11 |
| SRC10-R-015 | Idempotency: SHA-256 fingerprint, 24h retention, existing IdempotencyService | Section 12 |
| SRC10-R-016 | Partial failure: batch allows mixed results | Section 13 |
| SRC10-R-017 | Client timeout: 30 seconds | Section 14 |
| SRC10-R-018 | Conflict detection: server.entity.version != client.base_version | Section 15 |
| SRC10-R-019 | Conflict response: status, server_version, client_version, conflict_id, server_payload, client_payload | Section 15.2 |
| SRC10-R-020 | Conflict isolation: per-mutation, no batch blocking | Section 16 |
| SRC10-R-021 | Auto-merge: non-conflicting fields, server wins on conflicts | Section 17.1 |
| SRC10-R-022 | Manual resolution: user selects keep server/client/merge | Section 17.2 |
| SRC10-R-023 | Server-authoritative fields: state transitions, financial data, system-generated | Section 17.3 |
| SRC10-R-024 | Delete conflict: UPDATE vs DELETE → server wins; DELETE vs DELETE → idempotent | Section 18 |
| SRC10-R-025 | Full resync: clear cache, clear cursor, clear queue (log conflicts first), pull all | Section 19 |
| SRC10-R-026 | Entity types: CONTACT, ACCOUNT, LEAD, OPPORTUNITY, TASK, ACTIVITY, NOTE | Section 22 |
| SRC10-R-027 | 10 invariants must hold at all times | Section 24 |

**Total from Sync Contract: 27 detailed behavioral requirements.**

---

### 2.11 SRC-11: G7_SECURITY_FINAL_GATE.md — SECURITY SPEC

**Extracted Requirements from Security Gate:**

| ID | Requirement | Section | Status |
|----|------------|---------|--------|
| SRC11-R-001 | JWT access token (RS256, 15min TTL, memory-only) | 2.1 | EXISTS |
| SRC11-R-002 | Refresh token (opaque, 7-day TTL, rotation on use) | 2.2 | EXISTS |
| SRC11-R-003 | Mobile token management: cached tokens, expiry handling | 2.3 | NOT_DEFINED |
| SRC11-R-004 | Re-authentication flow on token expiry | 2.4 | EXISTS |
| SRC11-R-005 | RBAC enforcement on all sync operations | 3.1 | EXISTS |
| SRC11-R-006 | Sync-specific authorization matrix | 3.3 | NOT_DEFINED |
| SRC11-R-007 | RLS on all CRM tables | 4.1 | EXISTS |
| SRC11-R-008 | RLS on new sync tables (4 tables) | 5.2 | NOT_YET_IMPLEMENTED |
| SRC11-R-009 | Entity ownership tracking (owner_id) | 6.1 | EXISTS |
| SRC11-R-010 | Ownership rules: create/update/delete/transfer | 6.2 | EXISTS |
| SRC11-R-011 | Ownership and sync: server-authoritative | 6.4 | NOT_DEFINED |
| SRC11-R-012 | JWT validation: signature, expiry, issuer, audience, tenant, roles | 7.2 | EXISTS |
| SRC11-R-013 | Refresh token security: rotation, revocation, theft detection | 8.2 | EXISTS |
| SRC11-R-014 | Device UUID: v4 on first launch, secure storage | 9.1 | NOT_IMPLEMENTED |
| SRC11-R-015 | Device registry table: mobile_device_registry | 9.2 | NOT_IMPLEMENTED |
| SRC11-R-016 | Device tracking: registration, update, query, revocation | 9.3 | NOT_IMPLEMENTED |
| SRC11-R-017 | Idempotency key: UUID v4, SHA-256 fingerprint, 24h retention | 10.1 | EXISTS |
| SRC11-R-018 | Idempotency on all push operations | 11.3 | EXISTS |
| SRC11-R-019 | Audit logging: PlatformAuditWriter, before/after JSON | 12.1 | EXISTS |
| SRC11-R-020 | Mobile sync audit: mobile_sync_log table | 12.3 | NOT_IMPLEMENTED |
| SRC11-R-021 | Client-side encryption: SQLCipher or OS-level | 13.1 | NOT_DEFINED |
| SRC11-R-022 | Encryption key management: 256-bit, device-specific | 13.4 | NOT_DEFINED |
| SRC11-R-023 | Transport security: HTTPS (TLS 1.2+), HSTS, cert pinning | 14.1 | EXISTS |
| SRC11-R-024 | Sync endpoint security: JWT + RBAC + ownership on all endpoints | 14.2 | NOT_DEFINED |
| SRC11-R-025 | Cross-tenant sync blocked by RLS | 15.3 | EXISTS |
| SRC11-R-026 | Offline token handling: cache, reconnect, re-auth | 16.2 | NOT_DEFINED |
| SRC11-R-027 | Duplicate mutation handling: idempotency dedup | 17.1 | EXISTS |
| SRC11-R-028 | Authorization audit: authorization_logs table | 18.3 | NOT_DEFINED |
| SRC11-R-029 | Security gate: 4 items FAIL (encryption, device identity, device binding, offline auth) | 20.2 | FAIL |

**Security Gate Summary:**
- PASS: 6 (JWT, RBAC, RLS, Idempotency, Audit, HTTPS)
- FAIL: 4 (Offline Encryption, Device Identity, Device Binding, Offline Authorization)
- NOT_DEFINED: 8 (Token Management, Sync Auth, Ownership+Sync, Encryption Strategy, Key Mgmt, Endpoint Security, Offline Token, Auth Audit)

---

### 2.12 SRC-12: G7_FORENSIC_EXTRACTION_DATASET.md — CODEBASE EXTRACTION

**Extracted from Codebase Analysis:**

| Finding | Detail |
|---------|--------|
| Existing CRM tables | ~97 tables with RLS, version columns |
| Existing controllers | 56 controllers, ~250+ endpoints |
| Existing services | IdempotencyService, PlatformAuditWriter, RoleCapabilityService |
| Existing auth | JWT + RBAC + RLS (all COMPLETE) |
| Existing idempotency | SHA-256 fingerprint, 24h retention |
| Existing ETag | SHA-256 based, If-Match validation |
| Missing components | 12 components (Offline Mode, Sync Engine, Delta Sync, Device Registration, etc.) |
| Missing tables | 4 (mobile_device_registry, mobile_sync_cursor, mobile_sync_log, mobile_conflict_log) |
| Missing APIs | 9 mobile-specific endpoints |
| G7 definitions found | 7 distinct definitions across CRM, ERP, Finance modules |

---

## 3. CROSS-SOURCE REQUIREMENT COUNT

| Source | Requirements Extracted | Notes |
|--------|----------------------|-------|
| SRC-01 (Canonical) | 7 scope items | Scope level, not requirements |
| SRC-02 (Identity) | 7 identity constraints | Boundary conditions |
| SRC-03 (Baseline) | 39 enumerated requirements | Prior reconciliation output |
| SRC-04 (Forensic) | 8 conflict/analysis requirements | Derived from analysis |
| SRC-05 (Truth) | 12 truth requirements | Derived from reconciliation |
| SRC-06 (Gap) | 14 gap requirements | Derived from gap analysis |
| SRC-07 (Backlog) | 12 WPs → ~25 requirement refs | Work package mapping |
| SRC-08 (Gates) | 18 gate criteria | Acceptance conditions |
| SRC-09 (DoD) | 46 completion criteria | Completion conditions |
| SRC-10 (Sync) | 27 behavioral requirements | Detailed sync contract |
| SRC-11 (Security) | 29 security requirements | Security specification |
| SRC-12 (Codebase) | 8 forensic findings | Codebase analysis |

**GROSS REQUIREMENTS ACROSS ALL SOURCES: ~204 raw items**

**NOTE:** Many of these are duplicates, subsets, or supersets of each other. The reconciliation process (Phases 2-5) will normalize, deduplicate, and classify to produce the TRUE requirement count.

---

## 4. REQUIREMENT ID NAMING CONVENTIONS ACROSS SOURCES

| Source | ID Pattern | Example |
|--------|-----------|---------|
| SRC-03 (Baseline) | G7-MOB-{CATEGORY}-{SEQ} | G7-MOB-FR-001 |
| SRC-06 (Gap) | GAP-{SEQ} | GAP-001 |
| SRC-07 (Backlog) | WP-{LETTER} | WP-A |
| SRC-08 (Gates) | GATE-{SEQ} | GATE-01 |
| SRC-09 (DoD) | Category-based | No IDs |
| SRC-10 (Sync) | Section-based | Section 2.1 |
| SRC-11 (Security) | REQ-SEC-{SEQ} | REQ-SEC-001 |
| SRC-04 (Forensic) | Conflict Register | C1-C12 |

**NOTE:** No unified requirement ID scheme exists across all sources. The normalization phase must establish a canonical ID scheme.

---

## 5. CRITICAL OBSERVATIONS

1. **The "39 requirements" count comes from SRC-03 (Baseline)** — which was the output of a prior reconciliation. It is NOT independently verified against all sources.

2. **SRC-10 (Sync Contract) alone defines 27 behavioral requirements** that are NOT individually enumerated in the 39-count baseline. Many are implicit or aggregated.

3. **SRC-11 (Security Gate) defines 29 security requirements** — many overlap with baseline but add detail not present in the 39-count.

4. **No source defines exactly 101 requirements** — the "101" number mentioned in the reconciliation spec has no verifiable source. It may be an artifact or miscalculation.

5. **The gap register (SRC-06) defines 14 requirements** that map to a subset of the 39 baseline requirements, not additional ones.

6. **Work package coverage gap**: 6 baseline requirements have no work package mapping (FR-006, FR-007, NFR-001, NFR-003, NFR-004, NFR-005).

7. **P0 count discrepancy**: SRC-03 lists 13 items as P0 but claims P0=12. One item may be misclassified.

---

*Generated: 2026-08-12*
*Phase 1 of G7 Requirements Reconciliation*
