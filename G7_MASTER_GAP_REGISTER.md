# Phase 14: Master Gap Register

For each gap, document: ID, Requirement, Area, Current, Target, Severity, Priority, Dependency, Owner, Acceptance.

---

## GAP-001: Mobile Sync API Layer

| Field | Value |
|-------|-------|
| **ID** | GAP-001 |
| **Requirement** | G7-MOB-FR-001/002/003/004 |
| **Area** | API |
| **Current** | No mobile-specific APIs exist |
| **Target** | 9 mobile APIs implemented |
| **Severity** | BLOCKER |
| **Priority** | P0 |
| **Dependency** | G1, G3 (COMPLETE) |
| **Owner** | Backend Team |
| **Acceptance** | All 9 APIs functional with tests |

---

## GAP-002: Sync Metadata Schema

| Field | Value |
|-------|-------|
| **ID** | GAP-002 |
| **Requirement** | G7-MOB-DATA-001 |
| **Area** | Database |
| **Current** | No sync metadata tables |
| **Target** | 4 tables with RLS |
| **Severity** | BLOCKER |
| **Priority** | P0 |
| **Dependency** | None |
| **Owner** | Backend Team |
| **Acceptance** | Flyway migration applied, RLS verified |

---

## GAP-003: Change Tracking Columns

| Field | Value |
|-------|-------|
| **ID** | GAP-003 |
| **Requirement** | G7-MOB-DATA-002 |
| **Area** | Database |
| **Current** | version BIGINT exists on CRM tables, updated_at may be missing |
| **Target** | All CRM tables have version + updated_at |
| **Severity** | BLOCKER |
| **Priority** | P0 |
| **Dependency** | None |
| **Owner** | Backend Team |
| **Acceptance** | All CRM tables have required columns |

---

## GAP-004: Conflict Resolution Policy

| Field | Value |
|-------|-------|
| **ID** | GAP-004 |
| **Requirement** | G7-MOB-FR-008 |
| **Area** | Architecture |
| **Current** | Policy PROPOSED (ADR-G7-001 REQUIRES_REVISION) |
| **Target** | Policy APPROVED and implemented |
| **Severity** | BLOCKER |
| **Priority** | P0 |
| **Dependency** | None |
| **Owner** | Architecture Team |
| **Acceptance** | ADR accepted, policy implemented |

---

## GAP-005: Sync Engine (Client-Side)

| Field | Value |
|-------|-------|
| **ID** | GAP-005 |
| **Requirement** | G7-MOB-SYNC-001-008 |
| **Area** | Mobile Client |
| **Current** | No sync engine |
| **Target** | Full sync engine with queue, retry, conflict handling |
| **Severity** | BLOCKER |
| **Priority** | P0 |
| **Dependency** | GAP-001, GAP-002, GAP-004 |
| **Owner** | Mobile Team |
| **Acceptance** | Sync engine handles all scenarios |

---

## GAP-006: Offline Data Encryption

| Field | Value |
|-------|-------|
| **ID** | GAP-006 |
| **Requirement** | G7-MOB-SEC-001 |
| **Area** | Security |
| **Current** | No encryption strategy |
| **Target** | All offline data encrypted at rest |
| **Severity** | BLOCKER |
| **Priority** | P0 |
| **Dependency** | None |
| **Owner** | Security Team |
| **Acceptance** | Encryption implemented and verified |

---

## GAP-007: Offline Authorization

| Field | Value |
|-------|-------|
| **ID** | GAP-007 |
| **Requirement** | G7-MOB-SEC-004 |
| **Area** | Security |
| **Current** | No offline auth model |
| **Target** | Token caching with expiry check |
| **Severity** | HIGH |
| **Priority** | P1 |
| **Dependency** | Auth system (COMPLETE) |
| **Owner** | Security Team |
| **Acceptance** | Offline auth works correctly |

---

## GAP-008: Conflict Detection + Resolution

| Field | Value |
|-------|-------|
| **ID** | GAP-008 |
| **Requirement** | G7-MOB-SYNC-005/006 |
| **Area** | Sync |
| **Current** | No conflict handling for mobile |
| **Target** | Full conflict detection and resolution |
| **Severity** | HIGH |
| **Priority** | P1 |
| **Dependency** | GAP-004 |
| **Owner** | Backend Team |
| **Acceptance** | All 12 conflict classes handled |

---

## GAP-009: Mobile Entity APIs

| Field | Value |
|-------|-------|
| **ID** | GAP-009 |
| **Requirement** | G7-MOB-FR-001/002, G7-MOB-FR-010 |
| **Area** | API |
| **Current** | Full-payload APIs only |
| **Target** | Optimized mobile payloads |
| **Severity** | HIGH |
| **Priority** | P1 |
| **Dependency** | None |
| **Owner** | Backend Team |
| **Acceptance** | Response time < 200ms |

---

## GAP-010: Test Suite

| Field | Value |
|-------|-------|
| **ID** | GAP-010 |
| **Requirement** | G7-MOB-TEST-001-006 |
| **Area** | Testing |
| **Current** | 0 G7-specific tests |
| **Target** | 26 tests covering all scenarios |
| **Severity** | HIGH |
| **Priority** | P1 |
| **Dependency** | All implementation gaps |
| **Owner** | QA Team |
| **Acceptance** | All tests pass |

---

## GAP-011: Device Registry

| Field | Value |
|-------|-------|
| **ID** | GAP-011 |
| **Requirement** | G7-MOB-SEC-003 |
| **Area** | Security |
| **Current** | No device identity |
| **Target** | Device registration and binding |
| **Severity** | MEDIUM |
| **Priority** | P2 |
| **Dependency** | GAP-002 |
| **Owner** | Mobile Team |
| **Acceptance** | Device registration works |

---

## GAP-012: Sync Log

| Field | Value |
|-------|-------|
| **ID** | GAP-012 |
| **Requirement** | G7-MOB-DATA-004 |
| **Area** | Observability |
| **Current** | No sync audit trail |
| **Target** | All sync operations logged |
| **Severity** | MEDIUM |
| **Priority** | P2 |
| **Dependency** | GAP-002 |
| **Owner** | Backend Team |
| **Acceptance** | Sync operations auditable |

---

## GAP-013: Offline Entity Subset Definition

| Field | Value |
|-------|-------|
| **ID** | GAP-013 |
| **Requirement** | G7-MOB-FR-007 |
| **Area** | Architecture |
| **Current** | Partial definition in baseline |
| **Target** | Complete entity offline requirements |
| **Severity** | MEDIUM |
| **Priority** | P1 |
| **Dependency** | None |
| **Owner** | Architecture Team |
| **Acceptance** | All entities have offline policies |

---

## GAP-014: Performance Budget

| Field | Value |
|-------|-------|
| **ID** | GAP-014 |
| **Requirement** | G7-MOB-NFR-001 |
| **Area** | Performance |
| **Current** | No performance budget |
| **Target** | Mobile API < 200ms |
| **Severity** | MEDIUM |
| **Priority** | P1 |
| **Dependency** | GAP-009 |
| **Owner** | Performance Team |
| **Acceptance** | Load tests pass |

---

## Summary

| Severity | Count | IDs |
|----------|-------|-----|
| BLOCKER | 6 | GAP-001, GAP-002, GAP-003, GAP-004, GAP-005, GAP-006 |
| HIGH | 4 | GAP-007, GAP-008, GAP-009, GAP-010 |
| MEDIUM | 4 | GAP-011, GAP-012, GAP-013, GAP-014 |
