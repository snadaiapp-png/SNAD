# G7 Mobile Traceability Matrix

Phase 13: Traceability. For each P0/P1 requirement, trace the full path:
**Requirement -> Architecture -> Data/API Contract -> Work Package -> Code -> Test -> Acceptance Gate**

---

## Traceability Entries

### G7-MOB-FR-001 (Mobile-optimized entity list API, P0)

| Layer | Reference |
|-------|-----------|
| **Requirement** | G7-MOB-FR-001: Mobile-optimized entity list API |
| **Architecture** | G7 target architecture section 4.2 |
| **Data/API Contract** | G7_API_CONTRACT_FINAL.md (GET /api/v2/mobile/entity/{type}) |
| **Work Package** | WP-A (Foundation) + mobile entity APIs |
| **Code** | NOT_IMPLEMENTED |
| **Test** | G7-MOB-TEST-001 (NOT_IMPLEMENTED) |
| **Acceptance Gate** | GATE-05 (API Gate) |
| **Status** | UNTRACED (code and test missing) |

---

### G7-MOB-FR-002 (Mobile-optimized entity detail API, P0)

| Layer | Reference |
|-------|-----------|
| **Requirement** | G7-MOB-FR-002: Mobile-optimized entity detail API |
| **Architecture** | G7 target architecture section 4.2 |
| **Data/API Contract** | G7_API_CONTRACT_FINAL.md (GET /api/v2/mobile/entity/{type}/{id}) |
| **Work Package** | WP-A + mobile entity APIs |
| **Code** | NOT_IMPLEMENTED |
| **Test** | G7-MOB-TEST-002 (NOT_IMPLEMENTED) |
| **Acceptance Gate** | GATE-05 |
| **Status** | UNTRACED |

---

### G7-MOB-FR-003 (Delta sync pull API, P0)

| Layer | Reference |
|-------|-----------|
| **Requirement** | G7-MOB-FR-003: Delta sync pull API |
| **Architecture** | G7 sync engine section 8.3 |
| **Data/API Contract** | G7_API_CONTRACT_FINAL.md (GET /api/v2/mobile/sync/pull) |
| **Work Package** | WP-D (Pull Sync) |
| **Code** | NOT_IMPLEMENTED |
| **Test** | G7-MOB-TEST-003, 004, 005 (NOT_IMPLEMENTED) |
| **Acceptance Gate** | GATE-08 (Pull Sync Gate) |
| **Status** | UNTRACED |

---

### G7-MOB-FR-004 (Sync push API, P0)

| Layer | Reference |
|-------|-----------|
| **Requirement** | G7-MOB-FR-004: Sync push API |
| **Architecture** | G7 sync engine section 8.3 |
| **Data/API Contract** | G7_API_CONTRACT_FINAL.md (POST /api/v2/mobile/sync/push) |
| **Work Package** | WP-E (Push Sync) |
| **Code** | NOT_IMPLEMENTED |
| **Test** | G7-MOB-TEST-006, 007, 008, 009, 010 (NOT_IMPLEMENTED) |
| **Acceptance Gate** | GATE-11 (Push Sync Gate) |
| **Status** | UNTRACED |

---

### G7-MOB-FR-005 (Sync status/cursor API, P1)

| Layer | Reference |
|-------|-----------|
| **Requirement** | G7-MOB-FR-005: Sync status/cursor API |
| **Architecture** | G7 sync engine section 8.1 |
| **Data/API Contract** | G7_API_CONTRACT_FINAL.md (GET /api/v2/mobile/sync/status) |
| **Work Package** | WP-D (cursor management) |
| **Code** | NOT_IMPLEMENTED |
| **Test** | NOT_DEFINED |
| **Acceptance Gate** | GATE-08 |
| **Status** | UNTRACED |

---

### G7-MOB-FR-006 (Mobile auth token refresh, P1)

| Layer | Reference |
|-------|-----------|
| **Requirement** | G7-MOB-FR-006: Mobile auth token refresh |
| **Architecture** | G7 security model section 11 |
| **Data/API Contract** | Existing auth endpoints |
| **Work Package** | WP-I (Security) |
| **Code** | NOT_IMPLEMENTED |
| **Test** | G7-MOB-TEST-018, 019 (NOT_IMPLEMENTED) |
| **Acceptance Gate** | GATE-07 (Authentication Gate) |
| **Status** | UNTRACED |

---

### G7-MOB-FR-007 (Offline entity subset definition, P1)

| Layer | Reference |
|-------|-----------|
| **Requirement** | G7-MOB-FR-007: Offline entity subset definition |
| **Architecture** | G7 data model section 10.1 |
| **Data/API Contract** | G7_DATA_MODEL_FINAL_BASELINE.md |
| **Work Package** | WP-A (schema definition) |
| **Code** | NOT_IMPLEMENTED |
| **Test** | NOT_DEFINED |
| **Acceptance Gate** | GATE-02 (Requirements Gate) |
| **Status** | PARTIAL (definition exists, implementation missing) |

---

### G7-MOB-FR-008 (Conflict resolution policy, P1)

| Layer | Reference |
|-------|-----------|
| **Requirement** | G7-MOB-FR-008: Conflict resolution policy |
| **Architecture** | G7 conflict resolution section 9 |
| **Data/API Contract** | G7_CONFLICT_POLICY_FINAL.md |
| **Work Package** | WP-G (Conflict Resolution) |
| **Code** | NOT_IMPLEMENTED |
| **Test** | G7-MOB-TEST-011, 012, 013, 014, 015 (NOT_IMPLEMENTED) |
| **Acceptance Gate** | GATE-12 (Conflict Gate) |
| **Status** | UNTRACED |

---

### G7-MOB-SYNC-001 (Bidirectional sync, P0)

| Layer | Reference |
|-------|-----------|
| **Requirement** | G7-MOB-SYNC-001: Bidirectional sync |
| **Architecture** | G7 sync engine section 8 |
| **Data/API Contract** | G7_SYNC_CONTRACT_TRUTH.md |
| **Work Package** | WP-D + WP-E |
| **Code** | NOT_IMPLEMENTED |
| **Test** | NOT_DEFINED |
| **Acceptance Gate** | GATE-08 + GATE-11 |
| **Status** | UNTRACED |

---

### G7-MOB-SYNC-004 (Sync cursor/version tracking, P0)

| Layer | Reference |
|-------|-----------|
| **Requirement** | G7-MOB-SYNC-004: Sync cursor/version tracking |
| **Architecture** | G7 sync engine section 8.1 |
| **Data/API Contract** | G7_DATA_MODEL_FINAL_BASELINE.md (mobile_sync_cursor) |
| **Work Package** | WP-A + WP-D |
| **Code** | NOT_IMPLEMENTED |
| **Test** | NOT_DEFINED |
| **Acceptance Gate** | GATE-04 (Data Gate) |
| **Status** | UNTRACED |

---

### G7-MOB-DATA-001 (Sync metadata tables, P0)

| Layer | Reference |
|-------|-----------|
| **Requirement** | G7-MOB-DATA-001: Sync metadata tables |
| **Architecture** | G7 data model section 10.2 |
| **Data/API Contract** | G7_DATA_MODEL_FINAL_BASELINE.md |
| **Work Package** | WP-A (Foundation) |
| **Code** | NOT_IMPLEMENTED |
| **Test** | NOT_DEFINED |
| **Acceptance Gate** | GATE-04 |
| **Status** | UNTRACED |

---

### G7-MOB-DATA-002 (Change tracking columns, P0)

| Layer | Reference |
|-------|-----------|
| **Requirement** | G7-MOB-DATA-002: Change tracking columns |
| **Architecture** | Existing version columns on CRM tables |
| **Data/API Contract** | G7_DATA_MODEL_FINAL_BASELINE.md |
| **Work Package** | WP-A |
| **Code** | PARTIAL (version exists, updated_at may be missing) |
| **Test** | NOT_DEFINED |
| **Acceptance Gate** | GATE-04 |
| **Status** | PARTIAL |

---

### G7-MOB-SEC-001 (Offline data encryption, P0)

| Layer | Reference |
|-------|-----------|
| **Requirement** | G7-MOB-SEC-001: Offline data encryption |
| **Architecture** | G7 security model section 11 |
| **Data/API Contract** | G7_SECURITY_FINAL_GATE.md |
| **Work Package** | WP-I (Security) |
| **Code** | NOT_IMPLEMENTED |
| **Test** | NOT_DEFINED |
| **Acceptance Gate** | GATE-13 (Security Gate) |
| **Status** | UNTRACED |

---

### G7-MOB-SEC-005 (Tenant isolation on sync, P0)

| Layer | Reference |
|-------|-----------|
| **Requirement** | G7-MOB-SEC-005: Tenant isolation on sync |
| **Architecture** | Existing RLS infrastructure |
| **Data/API Contract** | G7_SECURITY_FINAL_GATE.md |
| **Work Package** | WP-A (RLS on new tables) + WP-I |
| **Code** | NOT_IMPLEMENTED |
| **Test** | G7-MOB-TEST-016, 017 (NOT_IMPLEMENTED) |
| **Acceptance Gate** | GATE-14 (Tenant Isolation Gate) |
| **Status** | UNTRACED |

---

### G7-MOB-TEST-005 (Tenant isolation sync tests, P0)

| Layer | Reference |
|-------|-----------|
| **Requirement** | G7-MOB-TEST-005: Tenant isolation sync tests |
| **Architecture** | N/A (test requirement) |
| **Data/API Contract** | N/A |
| **Work Package** | WP-K (Testing) |
| **Code** | N/A |
| **Test** | NOT_IMPLEMENTED |
| **Acceptance Gate** | GATE-16 (Testing Gate) |
| **Status** | NOT_STARTED |

---

## Traceability Summary

| Metric | Count |
|--------|-------|
| Total P0/P1 requirements traced | 15 |
| VERIFIED | 0 |
| PARTIAL | 2 |
| UNTRACED | 13 |
| MISSING | 0 |

### Status Breakdown

| Status | Requirements |
|--------|-------------|
| VERIFIED | (none) |
| PARTIAL | FR-007, DATA-002 |
| UNTRACED | FR-001, FR-002, FR-003, FR-004, FR-005, FR-006, FR-008, SYNC-001, SYNC-004, DATA-001, SEC-001, SEC-005, TEST-005 |

---

## P0 Traceability Blocker

**P0_TRACEABILITY_BLOCKER: YES**

13 P0/P1 requirements are UNTRACED -- no code implementations and no test coverage exist. Until code and tests are implemented, acceptance gates cannot be satisfied and the mobile feature set cannot be certified as ready for release.

### Blocking Gates

| Gate | Blocked By | Requirement Count |
|------|------------|-------------------|
| GATE-04 (Data Gate) | SYNC-004, DATA-001, DATA-002 | 3 |
| GATE-05 (API Gate) | FR-001, FR-002 | 2 |
| GATE-07 (Auth Gate) | FR-006 | 1 |
| GATE-08 (Pull Sync Gate) | FR-003, FR-005, SYNC-001 | 3 |
| GATE-11 (Push Sync Gate) | FR-004, SYNC-001 | 2 |
| GATE-12 (Conflict Gate) | FR-008 | 1 |
| GATE-13 (Security Gate) | SEC-001 | 1 |
| GATE-14 (Tenant Isolation Gate) | SEC-005 | 1 |
| GATE-16 (Testing Gate) | TEST-005 | 1 |

### Work Package Coverage

| Work Package | Traced Requirements | Status |
|-------------|-------------------|--------|
| WP-A (Foundation) | FR-001, FR-002, FR-007, SYNC-004, DATA-001, DATA-002, SEC-005 | BLOCKED |
| WP-D (Pull Sync) | FR-003, FR-005, SYNC-001, SYNC-004 | BLOCKED |
| WP-E (Push Sync) | FR-004, SYNC-001 | BLOCKED |
| WP-G (Conflict Resolution) | FR-008 | BLOCKED |
| WP-I (Security) | FR-006, SEC-001, SEC-005 | BLOCKED |
| WP-K (Testing) | TEST-005 | BLOCKED |

---

## Next Steps

1. Implement code for WP-A foundation work packages (data tables, change tracking, entity APIs)
2. Implement WP-D pull sync and WP-E push sync code
3. Write test cases for all NOT_DEFINED test requirements
4. Achieve VERIFIED status on at least GATE-04 (Data Gate) and GATE-05 (API Gate) before proceeding to sync gates
5. Resolve PARTIAL items: complete FR-007 implementation and DATA-002 updated_at column
