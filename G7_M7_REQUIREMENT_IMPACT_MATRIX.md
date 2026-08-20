# G7 M7 — REQUIREMENT IMPACT MATRIX

> **Report ID:** G7-M7-IMPACT-MATRIX-V1
> **Date:** 2026-08-12
> **Status:** COMPLETE
> **Purpose:** Map each of the 66 requirements to blocker dependencies and impact

---

## 1. IMPACT MAPPING RULES

| Impact Level | Definition |
|-------------|-----------|
| NO_IMPACT | Requirement not affected by any blocker |
| DEPENDENT | Requirement has dependency on a blocker but can proceed with design |
| BLOCKED | Requirement cannot proceed until blocker is resolved |
| REQUIRES_REVIEW | Requirement may need revision after blocker resolution |

---

## 2. BLOCKER-TO-REQUIREMENT MAP

### B1 (ADR-G7-001) → Requirements Affected

| Req ID | Name | Priority | Impact | Rationale |
|--------|------|----------|--------|-----------|
| SYNC-005 | Conflict Detection | P1 | BLOCKED | Cannot implement without approved conflict policy |
| SYNC-006 | Conflict Resolution | P1 | BLOCKED | Cannot implement without approved resolution strategy |
| SYNC-009 | Conflict Isolation | P1 | BLOCKED | Depends on resolution policy design |
| SYNC-010 | Delete Conflicts | P1 | BLOCKED | Depends on resolution policy for delete scenarios |
| ARCH-002 | 12 Conflict Classes | P0 | BLOCKED | Implementation depends on ADR-defined classes |

### B2 (Framework) → Requirements Affected

| Req ID | Name | Priority | Impact | Rationale |
|--------|------|----------|--------|-----------|
| SYNC-001 | Sync Engine | P0 | BLOCKED | Client-side sync engine requires framework |
| SYNC-003 | Mutation Queue | P1 | BLOCKED | Client-side queue requires framework |
| SYNC-012 | Crash Recovery | P1 | BLOCKED | Client-side recovery requires framework |
| DATA-003 | Local Storage Schema | P1 | BLOCKED | Client-side storage depends on framework |
| PERF-003 | Network Detection | P1 | DEFERRED | Already deferred to v1.1 |
| PERF-004 | Background Sync | P2 | DEFERRED | Already deferred to v1.1 |
| TEST-005 | E2E Test | P2 | DEFERRED | Already deferred to v1.1 |

### B3 (Encryption) → Requirements Affected

| Req ID | Name | Priority | Impact | Rationale |
|--------|------|----------|--------|-----------|
| SEC-001 | Offline Encryption | P0 | BLOCKED | Cannot implement without encryption strategy |
| SEC-002 | Token Caching | P1 | BLOCKED | Token storage depends on encryption approach |
| AUTH-001 | Mobile Auth Flow | P0 | BLOCKED | Auth token storage depends on encryption |

### B4 (Sign-off) → Requirements Affected

| Req ID | Name | Priority | Impact | Rationale |
|--------|------|----------|--------|-----------|
| ALL 66 | All requirements | ALL | DEPENDENT | Governance blocker — no implementation until sign-off |

---

## 3. FULL 66-REQUIREMENT IMPACT TABLE

### API Requirements (9)

| Req ID | Name | Priority | B1 | B2 | B3 | B4 | Overall Impact |
|--------|------|----------|----|----|----|----|---------------|
| API-001 | Entity List API | P0 | — | — | — | DEP | NO_IMPACT (greenfield only) |
| API-002 | Entity Detail API | P0 | — | — | — | DEP | NO_IMPACT (greenfield only) |
| API-003 | Delta Sync Pull API | P0 | — | — | — | DEP | NO_IMPACT (greenfield only) |
| API-004 | Batch Sync Push API | P0 | — | — | — | DEP | NO_IMPACT (greenfield only) |
| API-005 | Sync Status API | P1 | — | — | — | DEP | NO_IMPACT (greenfield only) |
| API-006 | Device Registration API | P2 | — | — | — | DEP | DEFERRED (no impact) |
| API-007 | Conflict List API | P1 | — | — | — | DEP | NO_IMPACT (greenfield only) |
| API-008 | Conflict Resolve API | P1 | REF | — | — | DEP | REQUIRES_REVIEW (ADR ref) |
| API-009 | Conflict Skip API | P1 | — | — | — | DEP | NO_IMPACT (greenfield only) |

### Sync Requirements (17)

| Req ID | Name | Priority | B1 | B2 | B3 | B4 | Overall Impact |
|--------|------|----------|----|----|----|----|---------------|
| SYNC-001 | Sync Engine | P0 | — | BLOCKED | — | DEP | BLOCKED (framework) |
| SYNC-002 | Delta Pull | P0 | — | — | — | DEP | NO_IMPACT (greenfield only) |
| SYNC-003 | Mutation Queue | P1 | — | BLOCKED | — | DEP | BLOCKED (framework) |
| SYNC-004 | Cursor Invalidation | P1 | — | — | — | DEP | NO_IMPACT (greenfield only) |
| SYNC-005 | Conflict Detection | P1 | BLOCKED | — | — | DEP | BLOCKED (ADR) |
| SYNC-006 | Conflict Resolution | P1 | BLOCKED | — | — | DEP | BLOCKED (ADR) |
| SYNC-007 | Retry/Backoff | P2 | — | — | — | DEP | DEFERRED (no impact) |
| SYNC-008 | Idempotency | P1 | — | — | — | DEP | NO_IMPACT (greenfield only) |
| SYNC-009 | Conflict Isolation | P1 | BLOCKED | — | — | DEP | BLOCKED (ADR) |
| SYNC-010 | Delete Conflicts | P1 | BLOCKED | — | — | DEP | BLOCKED (ADR) |
| SYNC-011 | Full Resync | P1 | — | — | — | DEP | NO_IMPACT (greenfield only) |
| SYNC-012 | Crash Recovery | P1 | — | BLOCKED | — | DEP | BLOCKED (framework) |
| SYNC-013 | Sequence Gap | P2 | — | — | — | DEP | DEFERRED (no impact) |
| SYNC-014 | Client Timeout | P1 | — | — | — | DEP | NO_IMPACT (greenfield only) |
| SYNC-015 | Entity Coverage | P0 | — | — | — | DEP | NO_IMPACT (greenfield only) |
| SYNC-016 | Server Authority | P1 | REF | — | — | DEP | REQUIRES_REVIEW (ADR ref) |
| SYNC-017 | Per-Mutation ACK | P0 | — | — | — | DEP | NO_IMPACT (greenfield only) |

### Auth Requirements (2)

| Req ID | Name | Priority | B1 | B2 | B3 | B4 | Overall Impact |
|--------|------|----------|----|----|----|----|---------------|
| AUTH-001 | Mobile Auth Flow | P0 | — | — | BLOCKED | DEP | BLOCKED (encryption) |
| AUTH-002 | Offline Token | P1 | — | — | — | DEP | NO_IMPACT (greenfield only) |

### Offline Requirements (2)

| Req ID | Name | Priority | B1 | B2 | B3 | B4 | Overall Impact |
|--------|------|----------|----|----|----|----|---------------|
| OFF-001 | Entity Subset | P1 | — | — | — | DEP | NO_IMPACT (greenfield only) |
| OFF-002 | Eligibility Rules | P1 | — | — | — | DEP | DEFERRED (no impact) |

### Data Requirements (5)

| Req ID | Name | Priority | B1 | B2 | B3 | B4 | Overall Impact |
|--------|------|----------|----|----|----|----|---------------|
| DATA-001 | Sync Tables | P0 | — | — | — | DEP | NO_IMPACT (greenfield only) |
| DATA-002 | Change Tracking | P0 | — | — | — | DEP | NO_IMPACT (greenfield only) |
| DATA-003 | Local Storage Schema | P1 | — | BLOCKED | — | DEP | BLOCKED (framework) |
| DATA-004 | Sync Audit Trail | P2 | — | — | — | DEP | DEFERRED (no impact) |
| DATA-005 | Conflict Log | P2 | — | — | — | DEP | DEFERRED (no impact) |

### Security Requirements (6)

| Req ID | Name | Priority | B1 | B2 | B3 | B4 | Overall Impact |
|--------|------|----------|----|----|----|----|---------------|
| SEC-001 | Offline Encryption | P0 | — | — | BLOCKED | DEP | BLOCKED (encryption) |
| SEC-002 | Token Caching | P1 | — | — | BLOCKED | DEP | BLOCKED (encryption) |
| SEC-003 | Device Registration | P2 | — | — | — | DEP | DEFERRED (no impact) |
| SEC-004 | Offline Auth | P1 | — | — | — | DEP | NO_IMPACT (greenfield only) |
| SEC-005 | Transport Security | P1 | — | — | — | DEP | APPROVED (no impact) |
| SEC-006 | Tenant Isolation | P0 | — | — | — | DEP | NO_IMPACT (greenfield only) |

### Architecture Requirements (1)

| Req ID | Name | Priority | B1 | B2 | B3 | B4 | Overall Impact |
|--------|------|----------|----|----|----|----|---------------|
| ARCH-002 | 12 Conflict Classes | P0 | BLOCKED | — | — | DEP | BLOCKED (ADR) |

### Performance Requirements (4)

| Req ID | Name | Priority | B1 | B2 | B3 | B4 | Overall Impact |
|--------|------|----------|----|----|----|----|---------------|
| PERF-001 | Response Time <200ms | P1 | — | — | — | DEP | NO_IMPACT (greenfield only) |
| PERF-002 | Storage Quota | P2 | — | — | — | DEP | DEFERRED (no impact) |
| PERF-003 | Network Detection | P1 | — | — | — | DEP | DEFERRED (no impact) |
| PERF-004 | Background Sync | P2 | — | — | — | DEP | DEFERRED (no impact) |

### Test Requirements (7)

| Req ID | Name | Priority | B1 | B2 | B3 | B4 | Overall Impact |
|--------|------|----------|----|----|----|----|---------------|
| TEST-001 | Unit Tests | P1 | — | — | — | DEP | NO_IMPACT (greenfield only) |
| TEST-002 | Pull Sync Tests | P1 | — | — | — | DEP | NO_IMPACT (greenfield only) |
| TEST-003 | Push Sync Tests | P1 | — | — | — | DEP | NO_IMPACT (greenfield only) |
| TEST-004 | Conflict Tests | P2 | — | — | — | DEP | DEFERRED (no impact) |
| TEST-005 | E2E Test | P2 | — | — | — | DEP | DEFERRED (no impact) |
| TEST-006 | Performance Tests | P2 | — | — | — | DEP | DEFERRED (no impact) |
| TEST-007 | Tenant Isolation Tests | P0 | — | — | — | DEP | NO_IMPACT (greenfield only) |

### Observability Requirements (7)

| Req ID | Name | Priority | B1 | B2 | B3 | B4 | Overall Impact |
|--------|------|----------|----|----|----|----|---------------|
| OBS-001 | Sync Metrics | P1 | — | — | — | DEP | NO_IMPACT (greenfield only) |
| OBS-002 | Conflict Metrics | P1 | — | — | — | DEP | NO_IMPACT (greenfield only) |
| OBS-003 | Queue Metrics | P1 | — | — | — | DEP | NO_IMPACT (greenfield only) |
| OBS-004 | Error Metrics | P1 | — | — | — | DEP | NO_IMPACT (greenfield only) |
| OBS-005 | Alerting | P1 | — | — | — | DEP | NO_IMPACT (greenfield only) |
| OBS-006 | Dashboards | P2 | — | — | — | DEP | DEFERRED (no impact) |
| OBS-007 | Structured Logging | P1 | — | — | — | DEP | NO_IMPACT (greenfield only) |

### Isolation Requirements (6)

| Req ID | Name | Priority | B1 | B2 | B3 | B4 | Overall Impact |
|--------|------|----------|----|----|----|----|---------------|
| ISO-001 | Tenant-Scoped Cursors | P0 | — | — | — | DEP | NO_IMPACT (greenfield only) |
| ISO-002 | Device-Scoped State | P1 | — | — | — | DEP | NO_IMPACT (greenfield only) |
| ISO-003 | User-Device Binding | P1 | — | — | — | DEP | NO_IMPACT (greenfield only) |
| ISO-004 | Failure Isolation | P0 | — | — | — | DEP | NO_IMPACT (greenfield only) |
| ISO-005 | Network Isolation | P0 | — | — | — | DEP | NO_IMPACT (greenfield only) |
| ISO-006 | Max Devices | P2 | — | — | — | DEP | DEFERRED (no impact) |

---

## 4. IMPACT SUMMARY

| Impact Level | Count | Requirements |
|-------------|-------|-------------|
| BLOCKED (by specific blocker) | 12 | SYNC-001, SYNC-003, SYNC-005, SYNC-006, SYNC-009, SYNC-010, SYNC-012, DATA-003, SEC-001, SEC-002, AUTH-001, ARCH-002 |
| REQUIRES_REVIEW (ADR reference) | 2 | API-008, SYNC-016 |
| DEFERRED (already deferred) | 13 | API-006, SYNC-007, SYNC-013, OFF-002, DATA-004, DATA-005, PERF-002, PERF-003, PERF-004, TEST-004, TEST-005, TEST-006, OBS-006, ISO-006 |
| NO_IMPACT (greenfield only) | 38 | All remaining |
| APPROVED (no impact) | 1 | SEC-005 |
| DEPENDENT on B4 (all) | 66 | All (governance) |

---

## 5. BLOCKER RESOLUTION IMPACT

If B1 (ADR) resolved:
- UNBLOCKS: 5 requirements (SYNC-005, SYNC-006, SYNC-009, SYNC-010, ARCH-002)
- REVIEWS: 2 requirements (API-008, SYNC-016)

If B2 (Framework) resolved:
- UNBLOCKS: 4 requirements (SYNC-001, SYNC-003, SYNC-012, DATA-003)
- Note: 3 deferred requirements also affected but already deferred

If B3 (Encryption) resolved:
- UNBLOCKS: 3 requirements (SEC-001, SEC-002, AUTH-001)

If B4 (Sign-off) resolved:
- UNBLOCKS: governance blocker for all 66

**TOTAL UNIQUE BLOCKED BY DECISIONS: 12 out of 66 (18.2%)**

---

*Generated: 2026-08-12*
*G7 Mission 7 — Requirement Impact Matrix*
