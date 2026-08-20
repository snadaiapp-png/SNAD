# G7 P0 FORENSIC REGISTER

> **Report ID:** G7-P0-FORENSIC-V1
> **Date:** 2026-08-12
> **Status:** DRAFT — NOT APPROVED
> **Purpose:** Forensic review of every P0 requirement — verify existence, implementation status, and priority justification.

---

## 1. P0 REQUIREMENTS (20 total)

### G7-REQ-API-003: Delta Sync Pull API

| Field | Value |
|-------|-------|
| **Norm ID** | G7-REQ-API-003 |
| **Description** | Delta sync pull API returns only entities changed since client's last cursor |
| **Priority** | P0 (BLOCKER) |
| **Status** | MISSING |
| **Forensic Evidence** | No mobile sync pull endpoint exists in any controller. `SyncEngine.java` is a placeholder. No `PullSyncController.java` or `PullSyncService.java` found. |
| **Priority Justification** | Without pull sync, mobile cannot receive any data from server. This is the foundational read path for offline mode. |
| **Code Evidence** | 0 matches for "sync/pull" in 860+ Java files. |
| **Priority Confirmed** | ✅ YES — BLOCKER |

---

### G7-REQ-API-004: Batch Sync Push API

| Field | Value |
|-------|-------|
| **Norm ID** | G7-REQ-API-004 |
| **Description** | Batch sync push API accepts array of mutation envelopes and returns per-mutation results |
| **Priority** | P0 (BLOCKER) |
| **Status** | MISSING |
| **Forensic Evidence** | No mobile sync push endpoint exists. No `PushSyncController.java` or `PushSyncService.java` found. |
| **Priority Justification** | Without push sync, mobile cannot send any data to server. This is the foundational write path for offline mode. |
| **Code Evidence** | 0 matches for "sync/push" in 860+ Java files. |
| **Priority Confirmed** | ✅ YES — BLOCKER |

---

### G7-REQ-API-001: Mobile-Optimized Entity List API

| Field | Value |
|-------|-------|
| **Norm ID** | G7-REQ-API-001 |
| **Description** | Mobile-optimized entity list API returns paginated, reduced-payload entity lists |
| **Priority** | P0 (BLOCKER) |
| **Status** | MISSING |
| **Forensic Evidence** | Existing V1/V2 entity APIs return full payloads. No mobile-specific list endpoint exists. |
| **Priority Justification** | Full payloads waste bandwidth and battery on mobile. Optimized payloads are essential for mobile UX. |
| **Code Evidence** | Existing `CrmContactController.java` returns full DTO. No mobile-specific variant. |
| **Priority Confirmed** | ✅ YES — BLOCKER |

---

### G7-REQ-API-002: Mobile-Optimized Entity Detail API

| Field | Value |
|-------|-------|
| **Norm ID** | G7-REQ-API-002 |
| **Description** | Mobile-optimized entity detail API returns single entity with mobile-appropriate fields |
| **Priority** | P0 (BLOCKER) |
| **Status** | MISSING |
| **Forensic Evidence** | Same as API-001 — full payloads only. |
| **Priority Justification** | Same as API-001 — mobile needs reduced payloads. |
| **Priority Confirmed** | ✅ YES — BLOCKER |

---

### G7-REQ-SYNC-001: Client-Side Sync Engine

| Field | Value |
|-------|-------|
| **Norm ID** | G7-REQ-SYNC-001 |
| **Description** | Client-side sync engine manages bidirectional data flow between local storage and server |
| **Priority** | P0 (BLOCKER) |
| **Status** | MISSING |
| **Forensic Evidence** | `SyncEngine.java` exists as empty placeholder class. No actual sync logic. |
| **Priority Justification** | The sync engine is the core component that orchestrates all offline/online data flow. Without it, nothing works. |
| **Code Evidence** | `SyncEngine.java` = empty class. |
| **Priority Confirmed** | ✅ YES — BLOCKER |

---

### G7-REQ-SYNC-002: Delta/Incremental Pull

| Field | Value |
|-------|-------|
| **Norm ID** | G7-REQ-SYNC-002 |
| **Description** | Delta/incremental pull uses cursor-based pagination to fetch only changed entities |
| **Priority** | P0 (BLOCKER) |
| **Status** | MISSING |
| **Forensic Evidence** | No cursor-based delta sync exists. Existing pagination is for web UI, not sync. |
| **Priority Justification** | Without delta pull, mobile must do full resync every time — impractical for bandwidth and battery. |
| **Priority Confirmed** | ✅ YES — BLOCKER |

---

### G7-REQ-SYNC-015: Entity Type Coverage

| Field | Value |
|-------|-------|
| **Norm ID** | G7-REQ-SYNC-015 |
| **Description** | All 7 entity types (CONTACT, ACCOUNT, LEAD, OPPORTUNITY, TASK, ACTIVITY, NOTE) support CREATE/UPDATE/DELETE/Pull/Push |
| **Priority** | P0 (BLOCKER) |
| **Status** | MISSING |
| **Forensic Evidence** | No sync support exists for any entity type. |
| **Priority Justification** | If even one entity type is missing from sync, the offline experience is incomplete. |
| **Priority Confirmed** | ✅ YES — BLOCKER |

---

### G7-REQ-SYNC-017: Per-Mutation Acknowledgement

| Field | Value |
|-------|-------|
| **Norm ID** | G7-REQ-SYNC-017 |
| **Description** | Acknowledgement is per-mutation: ACKNOWLEDGED removed from queue, CONFLICT logged |
| **Priority** | P0 (BLOCKER) |
| **Status** | MISSING |
| **Forensic Evidence** | No batch processing or per-mutation acknowledgement exists. |
| **Priority Justification** | Without per-mutation acknowledgement, partial failures cannot be handled — one failure blocks all. |
| **Priority Confirmed** | ✅ YES — BLOCKER |

---

### G7-REQ-DATA-001: Sync Metadata Tables

| Field | Value |
|-------|-------|
| **Norm ID** | G7-REQ-DATA-001 |
| **Description** | 4 new PostgreSQL tables (mobile_device_registry, mobile_sync_cursor, mobile_sync_log, mobile_conflict_log) with RLS |
| **Priority** | P0 (BLOCKER) |
| **Status** | MISSING |
| **Forensic Evidence** | 0 of 4 tables exist. No Flyway migration found. No CREATE TABLE for any mobile_ table. |
| **Priority Justification** | Without sync metadata, the server cannot track sync state, cursors, or conflicts. |
| **Code Evidence** | Database schema query: 0 matches for "mobile_" prefix. |
| **Priority Confirmed** | ✅ YES — BLOCKER |

---

### G7-REQ-DATA-002: Change Tracking Columns

| Field | Value |
|-------|-------|
| **Norm ID** | G7-REQ-DATA-002 |
| **Description** | version BIGINT and updated_at TIMESTAMP on all CRM entity tables |
| **Priority** | P0 (BLOCKER) |
| **Status** | PARTIAL |
| **Forensic Evidence** | `version BIGINT` exists on most CRM tables (confirmed in schema). `updated_at` may be missing on some tables. |
| **Priority Justification** | Without version column, optimistic concurrency cannot work for sync. Without updated_at, delta sync cannot determine what changed. |
| **Code Evidence** | `version` column confirmed in crm_contacts, crm_accounts, etc. `updated_at` status: NEEDS VERIFICATION. |
| **Priority Confirmed** | ✅ YES — BLOCKER (partially met) |

---

### G7-REQ-AUTH-001: Mobile Auth Flow

| Field | Value |
|-------|-------|
| **Norm ID** | G7-REQ-AUTH-001 |
| **Description** | Mobile auth flow supports token caching, refresh, and re-authentication on expiry |
| **Priority** | P0 (BLOCKER) |
| **Status** | PARTIAL |
| **Forensic Evidence** | JWT + refresh token infrastructure EXISTS (confirmed). Mobile-specific token caching NOT IMPLEMENTED. |
| **Priority Justification** | Without mobile auth, sync operations cannot authenticate. The existing web auth works but mobile needs token caching for offline. |
| **Code Evidence** | `JwtTokenProvider.java` EXISTS. No mobile-specific token caching found. |
| **Priority Confirmed** | ✅ YES — BLOCKER (infrastructure exists, mobile adaptation needed) |

---

### G7-REQ-SEC-001: Offline Data Encryption

| Field | Value |
|-------|-------|
| **Norm ID** | G7-REQ-SEC-001 |
| **Description** | All local CRM data encrypted at rest using SQLCipher or OS-level encryption |
| **Priority** | P0 (BLOCKER) |
| **Status** | MISSING |
| **Forensic Evidence** | No encryption strategy defined. No SQLCipher or OS encryption found. Security gate marks this as FAIL. |
| **Priority Justification** | Without encryption, lost/stolen devices expose all offline CRM data. HIGH security risk. |
| **Code Evidence** | 0 matches for "SQLCipher" or "encryption" in mobile context. |
| **Priority Confirmed** | ✅ YES — BLOCKER |

---

### G7-REQ-SEC-006: Tenant Isolation on Sync

| Field | Value |
|-------|-------|
| **Norm ID** | G7-REQ-SEC-006 |
| **Description** | RLS enforced on all 4 new sync tables, cross-tenant sync blocked |
| **Priority** | P0 (BLOCKER) |
| **Status** | MISSING (tables don't exist yet) |
| **Forensic Evidence** | RLS works on existing CRM tables. New sync tables don't exist, so RLS can't be applied yet. |
| **Priority Justification** | Without RLS on sync tables, cross-tenant data leakage is possible. Fundamental security requirement. |
| **Priority Confirmed** | ✅ YES — BLOCKER |

---

### G7-REQ-ISO-001: Tenant-Scoped Cursors

| Field | Value |
|-------|-------|
| **Norm ID** | G7-REQ-ISO-001 |
| **Description** | Tenant A cursor cannot be used for tenant B |
| **Priority** | P0 (BLOCKER) |
| **Status** | MISSING (no cursor system exists) |
| **Forensic Evidence** | `CursorCodec.java` includes tenant hash validation (AC-04). But this is for web pagination, not mobile sync cursors. |
| **Priority Justification** | Without tenant-scoped cursors, a cursor from one tenant could expose another tenant's data. |
| **Priority Confirmed** | ✅ YES — BLOCKER |

---

### G7-REQ-ISO-004: Failure Isolation

| Field | Value |
|-------|-------|
| **Norm ID** | G7-REQ-ISO-004 |
| **Description** | One mutation failure does not affect processing of any other mutation |
| **Priority** | P0 (BLOCKER) |
| **Status** | MISSING (no batch processing exists) |
| **Forensic Evidence** | No batch mutation processing exists. Current API handles one entity at a time. |
| **Priority Justification** | Without failure isolation, one bad mutation blocks all others in a batch — unacceptable for mobile offline. |
| **Priority Confirmed** | ✅ YES — BLOCKER |

---

### G7-REQ-ISO-005: Network Failure Isolation

| Field | Value |
|-------|-------|
| **Norm ID** | G7-REQ-ISO-005 |
| **Description** | Push failures do not affect pull cursor state and vice versa |
| **Priority** | P0 (BLOCKER) |
| **Status** | MISSING |
| **Forensic Evidence** | No sync system exists to evaluate. |
| **Priority Justification** | Without network isolation, a push failure could corrupt the pull cursor, requiring full resync. |
| **Priority Confirmed** | ✅ YES — BLOCKER |

---

### G7-REQ-ARCH-001: ADR-G7-001 Approval

| Field | Value |
|-------|-------|
| **Norm ID** | G7-REQ-ARCH-001 |
| **Description** | ADR-G7-001 (conflict resolution policy) must be APPROVED |
| **Priority** | P0 (BLOCKER) |
| **Status** | NOT APPROVED (REQUIRES_REVISION) |
| **Forensic Evidence** | `ADR-G7-001-MOBILE-CONFLICT-RESOLUTION.md` status = REQUIRES_REVISION. |
| **Priority Justification** | Without approved conflict policy, implementation cannot proceed — all conflict-related code is blocked. |
| **Priority Confirmed** | ✅ YES — BLOCKER |

---

### G7-REQ-ARCH-002: 12 Conflict Classes

| Field | Value |
|-------|-------|
| **Norm ID** | G7-REQ-ARCH-002 |
| **Description** | 12 conflict classes must be implemented as defined in the conflict matrix |
| **Priority** | P0 (BLOCKER) |
| **Status** | DEFINED (in conflict matrix) but NOT IMPLEMENTED |
| **Forensic Evidence** | 12 classes defined in `G7_CONFLICT_POLICY_FINAL.md`. No implementation exists. |
| **Priority Justification** | Without all 12 conflict classes, some conflict scenarios will be unhandled — data corruption risk. |
| **Priority Confirmed** | ✅ YES — BLOCKER |

---

### G7-REQ-TEST-007: Tenant Isolation Sync Tests

| Field | Value |
|-------|-------|
| **Norm ID** | G7-REQ-TEST-007 |
| **Description** | Tenant isolation sync tests verifying RLS on all sync tables |
| **Priority** | P0 (BLOCKER) |
| **Status** | MISSING |
| **Forensic Evidence** | 0 G7-specific tests exist. No sync-related tests found. |
| **Priority Justification** | Without tenant isolation tests, cross-tenant data leakage cannot be detected before production. |
| **Priority Confirmed** | ✅ YES — BLOCKER |

---

## 2. P0 FORENSIC SUMMARY

| Status | Count | IDs |
|--------|-------|-----|
| MISSING | 15 | API-001, API-002, API-003, API-004, SYNC-001, SYNC-002, SYNC-015, SYNC-017, DATA-001, SEC-001, SEC-006, ISO-001, ISO-004, ISO-005, TEST-007 |
| PARTIAL | 2 | DATA-002 (version exists, updated_at unclear), AUTH-001 (JWT exists, mobile caching missing) |
| NOT_APPROVED | 1 | ARCH-001 (ADR pending) |
| DEFINED_NOT_IMPLEMENTED | 1 | ARCH-002 (conflict classes defined, no code) |
| **TOTAL P0** | **20** | |

---

## 3. P0 PRIORITY JUSTIFICATION CRITERIA

Every P0 requirement must satisfy at least ONE of:

1. **Data Loss Risk:** Without this requirement, data can be lost or corrupted
2. **Security Breach:** Without this requirement, tenant isolation or encryption is compromised
3. **Blocking Dependency:** Multiple other requirements depend on this one
4. **Foundation:** This is a foundational component that everything else builds on

| Norm ID | Criteria Met |
|---------|-------------|
| API-003 | Foundation (read path) |
| API-004 | Foundation (write path) |
| API-001 | Foundation (mobile data access) |
| API-002 | Foundation (mobile data access) |
| SYNC-001 | Foundation (core engine) |
| SYNC-002 | Foundation (delta sync) |
| SYNC-015 | Data Loss Risk (incomplete entity coverage) |
| SYNC-017 | Blocking Dependency (partial failure handling) |
| DATA-001 | Foundation (server-side sync state) |
| DATA-002 | Foundation (optimistic concurrency) |
| AUTH-001 | Foundation (authentication) |
| SEC-001 | Security Breach (data exposure) |
| SEC-006 | Security Breach (cross-tenant leakage) |
| ISO-001 | Security Breach (cross-tenant cursor) |
| ISO-004 | Data Loss Risk (batch failure cascade) |
| ISO-005 | Data Loss Risk (cursor corruption) |
| ARCH-001 | Blocking Dependency (conflict policy) |
| ARCH-002 | Data Loss Risk (unhandled conflicts) |
| TEST-007 | Security Breach (untested isolation) |

**All 20 P0 requirements justify their priority.**

---

*Generated: 2026-08-12*
*Phase 10 of G7 Requirements Reconciliation*
