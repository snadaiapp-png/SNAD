# G7 REQUIREMENT DUPLICATE REGISTER

> **Report ID:** G7-REQ-DUP-V1
> **Date:** 2026-08-12
> **Status:** DRAFT — NOT APPROVED
> **Purpose:** Identify all duplicate requirements across sources and define canonical resolution.

---

## 1. DEDUPLICATION METHODOLOGY

1. **Semantic Matching:** Requirements are duplicates if they describe the same capability/behavior, regardless of wording
2. **Granularity Check:** A detailed spec (e.g., "mutation envelope must include 8 fields") and a high-level requirement (e.g., "batch push API") may be the SAME requirement at different detail levels
3. **Source Authority:** When duplicates exist, the CANONICAL source (SRC-01: crm-execution-data.ts) takes precedence
4. **Cross-Reference:** All raw IDs are preserved as cross-references to the canonical normalized ID

---

## 2. DUPLICATE CLUSTERS

### Cluster 1: Mobile-Optimized Entity APIs
**Canonical:** G7-REQ-API-001 (Entity List) + G7-REQ-API-002 (Entity Detail)

| Raw ID | Source | Wording |
|--------|--------|---------|
| G7-MOB-FR-001 | SRC-03 (Baseline) | "Mobile-optimized CRM entity APIs" |
| G7-MOB-FR-002 | SRC-03 (Baseline) | "Offline sync schema" |
| API-05 | SRC-15 (API Contract) | "GET /api/v2/mobile/entity/{type}/{id} -- Optimized entity detail" |
| API-06 | SRC-15 (API Contract) | "GET /api/v2/mobile/entity/{type} -- Optimized entity list" |
| GAP-R-009 | SRC-06 (Gap) | "Mobile Entity APIs: optimized mobile payloads, <200ms" |
| GAP-R-014 | SRC-06 (Gap) | "Performance Budget: mobile API <200ms" |
| TRUTH-R-001 | SRC-05 (Truth) | "9 new mobile APIs must be implemented" |
| DOD-API-1 | SRC-09 (DoD) | "9 mobile APIs implemented" |

**Resolution:** Split into G7-REQ-API-001 (list) and G7-REQ-API-002 (detail). GAP-R-014 merges into G7-REQ-PERF-001.

---

### Cluster 2: Delta Sync Pull
**Canonical:** G7-REQ-API-003 + G7-REQ-SYNC-002

| Raw ID | Source | Wording |
|--------|--------|---------|
| G7-MOB-FR-003 | SRC-03 (Baseline) | "Delta pull with cursor" |
| API-01 | SRC-15 (API Contract) | "GET /api/v2/mobile/sync/pull -- Delta sync pull" |
| SYNC-R-010 through SYNC-R-014 | SRC-10 (Sync Contract) | Pull request/response/processing specs |
| SYNC-CONTRACT-04 | SRC-10 (Sync Contract) | "Pull: server returns entities changed since cursor" |
| SYNC-R-021 | SRC-10 (Sync Contract) | "Cursor invalidation: full resync on schema change, token expiry, explicit request" |
| G7-MOB-SYNC-002 | SRC-03 (Baseline) | "Delta sync with cursor-based pagination" |
| GAP-R-001 | SRC-06 (Gap) | "Mobile Sync API Layer: 9 mobile APIs implemented" |

**Resolution:** G7-REQ-API-003 (API contract) and G7-REQ-SYNC-002 (sync behavior) are the same requirement at API vs. behavioral level. Both retained as separate normalized requirements (API vs. Sync category).

---

### Cluster 3: Batch Push Sync
**Canonical:** G7-REQ-API-004 + G7-REQ-SYNC-017

| Raw ID | Source | Wording |
|--------|--------|---------|
| G7-MOB-FR-004 | SRC-03 (Baseline) | "Batch push with idempotency" |
| API-02 | SRC-15 (API Contract) | "POST /api/v2/mobile/sync/push -- Batch sync push" |
| SYNC-R-015 through SYNC-R-018 | SRC-10 (Sync Contract) | Push request/processing/response/batch integrity |
| SYNC-CONTRACT-05 | SRC-10 (Sync Contract) | "Push: per-mutation independent processing" |
| SYNC-CONTRACT-15 | SRC-10 (Sync Contract) | "Acknowledgement: per-mutation, not per-batch" |

**Resolution:** G7-REQ-API-004 (API) and G7-REQ-SYNC-017 (behavioral) are the same requirement. Both retained.

---

### Cluster 4: Conflict Resolution Policy
**Canonical:** G7-REQ-ARCH-001 + G7-REQ-ARCH-002

| Raw ID | Source | Wording |
|--------|--------|---------|
| G7-MOB-FR-008 | SRC-03 (Baseline) | "Conflict resolution policy (12 classes)" |
| GAP-R-004 | SRC-06 (Gap) | "Conflict Resolution Policy: ADR approved and implemented" |
| GAP-R-008 | SRC-06 (Gap) | "Conflict Detection + Resolution: all 12 conflict classes handled" |
| FORENSIC-R-003 | SRC-04 (Forensic) | "12 conflict classes must be implemented" |
| FORENSIC-R-004 | SRC-04 (Forensic) | "Conflict policy must be approved (ADR-G7-001)" |
| TRUTH-R-005 | SRC-05 (Truth) | "Conflict resolution with 12 conflict classes" |
| TRUTH-R-011 | SRC-05 (Truth) | "ADR-G7-001 must be approved" |
| SYNC-R-030 through SYNC-R-037 | SRC-10 (Sync Contract) | Conflict detection, response, isolation, merge, delete handling |
| SYNC-CONTRACT-06 | SRC-10 (Sync Contract) | "Conflict detected when server.entity.version != client.base_version" |
| SYNC-CONTRACT-09 | SRC-10 (Sync Contract) | "Auto-merge: non-conflicting fields merged" |
| SYNC-CONTRACT-10 | SRC-10 (Sync Contract) | "Delete conflicts: server always wins" |
| ADR-G7-001-C1 through C10 | SRC-04 (ADR) | 10 ADR constraints |
| ADR-G7-001-AC-1 through AC-10 | SRC-04 (ADR) | 10 ADR acceptance criteria |
| DOD-ARCH-1 | SRC-09 (DoD) | "ADR-G7-001 APPROVED" |

**Resolution:** This is the most heavily duplicated cluster. Split into:
- G7-REQ-ARCH-001: ADR approval (process gate)
- G7-REQ-ARCH-002: 12 conflict classes implementation (technical requirement)
- G7-REQ-SYNC-005: Conflict detection (behavioral)
- G7-REQ-SYNC-006: Conflict resolution strategies (behavioral)
- G7-REQ-SYNC-010: Delete conflict handling (behavioral)

---

### Cluster 5: Offline Data Encryption
**Canonical:** G7-REQ-SEC-001

| Raw ID | Source | Wording |
|--------|--------|---------|
| G7-MOB-SEC-001 | SRC-03 (Baseline) | "Offline data encryption strategy" |
| G7-MOB-NFR-002 | SRC-03 (Baseline) | "Offline data encrypted at rest" |
| GAP-R-006 | SRC-06 (Gap) | "Offline Data Encryption: all offline data encrypted at rest" |
| SEC-R-023 | SRC-11 (Security) | "Client-side encryption: SQLCipher or OS-level" |
| SEC-R-024 | SRC-11 (Security) | "Encryption key management: 256-bit, device-specific" |
| SEC-RISK-001 | SRC-11 (Security) | "No offline data encryption" |
| TRUTH-R-007 | SRC-05 (Truth) | "Offline data encryption strategy" |
| DOD-SEC-1 | SRC-09 (DoD) | "Offline data encryption defined" |

**Resolution:** All map to G7-REQ-SEC-001. G7-MOB-NFR-002 was misclassified as NFR; it's a security requirement.

---

### Cluster 6: Device Registration
**Canonical:** G7-REQ-SEC-003 + G7-REQ-API-006

| Raw ID | Source | Wording |
|--------|--------|---------|
| G7-MOB-SEC-003 | SRC-03 (Baseline) | "Device registration and binding" |
| GAP-R-011 | SRC-06 (Gap) | "Device Registry: device registration and binding" |
| SEC-R-016 | SRC-11 (Security) | "Device UUID: v4 on first launch, secure storage" |
| SEC-R-017 | SRC-11 (Security) | "Device registry table: mobile_device_registry schema" |
| SEC-R-018 | SRC-11 (Security) | "Device tracking: registration, update, query, revocation" |
| SEC-RISK-002 | SRC-11 (Security) | "No device binding" |
| API-04 | SRC-15 (API Contract) | "POST /api/v2/mobile/device/register" |
| DOD-SEC-2 | SRC-09 (DoD) | "Device registration implemented" |

**Resolution:** G7-REQ-SEC-003 (security) and G7-REQ-API-006 (API) are the same requirement at different levels.

---

### Cluster 7: Idempotency
**Canonical:** G7-REQ-SYNC-008

| Raw ID | Source | Wording |
|--------|--------|---------|
| G7-MOB-SYNC-008 | SRC-03 (Baseline) | "Idempotency for all sync mutations" |
| SYNC-R-029 | SRC-10 (Sync Contract) | "Idempotency: SHA-256 fingerprint, 24h retention" |
| SYNC-CONTRACT-08 | SRC-10 (Sync Contract) | "Idempotency: SHA-256 fingerprint, 24-hour retention" |
| SEC-R-019 | SRC-11 (Security) | "Idempotency key: UUID v4, SHA-256 fingerprint, 24h retention" |
| SEC-R-020 | SRC-11 (Security) | "Idempotency on all push operations" |
| SEC-R-029 | SRC-11 (Security) | "Duplicate mutation handling: idempotency dedup, 24h window" |
| DOD-SEC-9 | SRC-09 (DoD) | "Idempotency verified for all sync operations" |

**Resolution:** All map to G7-REQ-SYNC-008.

---

### Cluster 8: Sync Metadata Tables
**Canonical:** G7-REQ-DATA-001

| Raw ID | Source | Wording |
|--------|--------|---------|
| G7-MOB-DATA-001 | SRC-03 (Baseline) | "Sync metadata tables (4 tables)" |
| GAP-R-002 | SRC-06 (Gap) | "Sync Metadata Schema: 4 tables with RLS" |
| TABLE-01 through TABLE-04 | SRC-16 (Data Model) | 4 table definitions |
| TRUTH-R-002 | SRC-05 (Truth) | "4 new sync metadata tables must be created" |
| DOD-DB-1 | SRC-09 (DoD) | "4 sync metadata tables created" |
| DOD-DB-3 | SRC-09 (DoD) | "RLS policies on all new tables" |

**Resolution:** All map to G7-REQ-DATA-001.

---

### Cluster 9: Change Tracking Columns
**Canonical:** G7-REQ-DATA-002

| Raw ID | Source | Wording |
|--------|--------|---------|
| G7-MOB-DATA-002 | SRC-03 (Baseline) | "Change tracking columns on CRM tables" |
| GAP-R-003 | SRC-06 (Gap) | "Change Tracking Columns: all CRM tables have version + updated_at" |
| TRUTH-R-003 | SRC-05 (Truth) | "Change tracking columns on all CRM tables" |
| DOD-DB-2 | SRC-09 (DoD) | "Change tracking columns added" |

**Resolution:** All map to G7-REQ-DATA-002.

---

### Cluster 10: Tenant Isolation on Sync
**Canonical:** G7-REQ-SEC-006 + G7-REQ-ISO-001

| Raw ID | Source | Wording |
|--------|--------|---------|
| G7-MOB-SEC-005 | SRC-03 (Baseline) | "Sync transport security (HTTPS)" — partially overlaps |
| SEC-R-008 | SRC-11 (Security) | "RLS on all CRM tables" |
| SEC-R-010 | SRC-11 (Security) | "RLS on new sync tables" |
| SEC-R-027 | SRC-11 (Security) | "Cross-tenant sync blocked by RLS" |
| TRUTH-R-008 | SRC-05 (Truth) | "RLS must extend to sync tables" |
| ISO-TENANT-1 | SRC-19 (Isolation) | "Every sync operation scoped to single tenant" |
| ISO-TENANT-2 | SRC-19 (Isolation) | "Cursor tokens scoped to tenant" |
| DOD-TI-1 through TI-4 | SRC-09 (DoD) | Tenant isolation DoD criteria |

**Resolution:** Split into G7-REQ-SEC-006 (transport + RLS) and G7-REQ-ISO-001 (cursor scoping).

---

### Cluster 11: 9 Mobile APIs (Aggregate)
**Canonical:** G7-REQ-API-001 through API-009 (individual APIs)

| Raw ID | Source | Wording |
|--------|--------|---------|
| GAP-R-001 | SRC-06 (Gap) | "Mobile Sync API Layer: 9 mobile APIs implemented" |
| TRUTH-R-001 | SRC-05 (Truth) | "9 new mobile APIs must be implemented" |
| DOD-API-1 | SRC-09 (DoD) | "9 mobile APIs implemented" |

**Resolution:** This is an AGGREGATE reference to 9 individual API requirements. Not a separate requirement.

---

### Cluster 12: 26 Tests (Aggregate)
**Canonical:** G7-REQ-TEST-001 through TEST-007 (individual tests)

| Raw ID | Source | Wording |
|--------|--------|---------|
| GAP-R-010 | SRC-06 (Gap) | "Test Suite: 26 tests covering all scenarios" |
| TRUTH-R-010 | SRC-05 (Truth) | "26 tests must be implemented" |
| DOD-TEST-1 | SRC-09 (DoD) | "All 26 tests implemented" |

**Resolution:** Aggregate reference to individual test requirements. The "26" count may need reconciliation against the 7 normalized test requirements (which may decompose into 26 individual test cases).

---

## 3. RECLASSIFICATION FIXES

These requirements were misclassified in the prior baseline and are corrected:

| Raw ID | Prior Classification | Corrected Classification | Reason |
|--------|---------------------|-------------------------|--------|
| G7-MOB-NFR-002 | Non-Functional | Security (G7-REQ-SEC-001) | "Offline data encrypted at rest" is a security requirement |
| G7-MOB-FR-002 | Functional (baseline) | Data (G7-REQ-DATA-001) | "Offline sync schema" refers to database schema, not API behavior |
| G7-MOB-SYNC-001 | Sync | Sync (G7-REQ-SYNC-001) | "Client-side sync engine" — correctly classified but too broad |
| G7-MOB-SEC-005 | Security | Security (G7-REQ-SEC-005) | Correctly classified |

---

## 4. DEDUPLICATION STATISTICS

| Metric | Count |
|--------|-------|
| Gross raw items across all sources | 300 |
| Unique normalized requirements | 69 |
| Duplicate items collapsed | 231 |
| Deduplication ratio | 77.0% |
| Clusters identified | 12 |
| Reclassification fixes | 2 |

---

## 5. REQUIREMENTS WITH NO DUPLICATES

These normalized requirements appear in only ONE source and have no duplicates:

| Norm ID | Description | Source |
|---------|-------------|--------|
| G7-REQ-SYNC-013 | Sequence gap detection | SRC-10 only |
| G7-REQ-SYNC-014 | Client request timeout (30s) | SRC-10 only |
| G7-REQ-AUTH-002 | Offline token handling | SRC-10 + SRC-11 only |
| G7-REQ-OFF-002 | Entity-level offline eligibility | SRC-03 only |
| G7-REQ-PERF-002 | Storage quota management | SRC-03 only |
| G7-REQ-PERF-003 | Network state detection | SRC-03 only |
| G7-REQ-PERF-004 | Background sync scheduling | SRC-03 only |
| G7-REQ-ISO-003 | User-device binding | SRC-10 only |
| G7-REQ-ISO-006 | Max devices per user | SRC-10 only |

---

*Generated: 2026-08-12*
*Phase 4 of G7 Requirements Reconciliation*
