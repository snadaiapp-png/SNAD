# G7 MASTER REQUIREMENTS BASELINE

> **Report ID:** G7-REQ-BASELINE-V2
> **Date:** 2026-08-12
> **Status:** **DRAFT — NOT APPROVED**
> **BASELINE_STATUS:** NOT_APPROVED
> **Authority:** Derived from forensic analysis of 12 source documents
> **Prior Version:** G7_MASTER_REQUIREMENTS_BASELINE.md (39 requirements) — SUPERSEDED

---

## 1. BASELINE IDENTITY

| Field | Value |
|-------|-------|
| **G7 ID** | G7 |
| **Name** | Mobile Offline Foundation |
| **Arabic** | أساس الجوال |
| **Canonical Source** | `apps/web/app/crm/crm-execution-data.ts` lines 129-137 |
| **Dependencies** | G1 (Database & Multi-Tenant) — COMPLETE ✅, G3 (Core CRM Entities) — COMPLETE ✅ |
| **Scope** | Mobile-optimized CRM APIs, offline sync schema, client-side storage, sync engine, mobile auth, entity subset |
| **Non-Scope** | Native mobile UI, push notifications (G8), caller ID (G8), real-time collaboration |

---

## 2. RECONCILIATION SUMMARY

| Metric | Prior Value | Reconciled Value | Change |
|--------|-------------|------------------|--------|
| **TRUE_G7_REQUIREMENTS_COUNT** | 39 | **69** | +30 |
| **P0 (BLOCKER)** | 12 | **20** | +8 |
| **P1 (CRITICAL)** | 13 | **33** | +20 |
| **P2 (HIGH)** | 9 | **14** | +5 |
| **P3 (MEDIUM)** | 2 | **2** | 0 |
| **Sources Analyzed** | 1 | **12** | +11 |
| **Raw Items Deduplicated** | N/A | **300 → 69** | 77% dedup |
| **Conflicts Resolved** | 0 | **14** | +14 |
| **Requirements Accepted** | 39 | **57** | +18 |
| **Requirements Deferred** | 0 | **10** | +10 |
| **Requirements Rejected** | 0 | **0** | 0 |

---

## 3. REQUIREMENT CATEGORIES

| Category | Count | P0 | P1 | P2 | P3 |
|----------|-------|----|----|----|----|
| API | 9 | 4 | 4 | 1 | 0 |
| Sync | 17 | 5 | 10 | 2 | 0 |
| Auth | 2 | 1 | 1 | 0 | 0 |
| Offline | 2 | 0 | 2 | 0 | 0 |
| Data | 5 | 2 | 1 | 2 | 0 |
| Security | 6 | 2 | 3 | 1 | 0 |
| Architecture | 4 | 2 | 1 | 0 | 1 |
| Performance | 4 | 0 | 2 | 2 | 0 |
| Test | 7 | 1 | 4 | 2 | 0 |
| Observability | 7 | 0 | 5 | 2 | 0 |
| Isolation | 6 | 3 | 2 | 1 | 0 |
| **TOTAL** | **69** | **20** | **33** | **14** | **2** |

---

## 4. COMPLETE REQUIREMENT LIST

### 4.1 API Requirements (9)

| ID | Description | Priority | Status | Disposition |
|----|-------------|----------|--------|-------------|
| G7-REQ-API-001 | Mobile-optimized entity list API | P0 | MISSING | ACCEPT |
| G7-REQ-API-002 | Mobile-optimized entity detail API | P0 | MISSING | ACCEPT |
| G7-REQ-API-003 | Delta sync pull API | P0 | MISSING | ACCEPT |
| G7-REQ-API-004 | Batch sync push API | P0 | MISSING | ACCEPT |
| G7-REQ-API-005 | Sync status API | P1 | MISSING | ACCEPT |
| G7-REQ-API-006 | Device registration API | P1 | MISSING | ACCEPT |
| G7-REQ-API-007 | Conflict list API | P1 | MISSING | ACCEPT |
| G7-REQ-API-008 | Conflict resolve API | P1 | MISSING | ACCEPT |
| G7-REQ-API-009 | Conflict skip API | P1 | MISSING | ACCEPT |

### 4.2 Sync Requirements (17)

| ID | Description | Priority | Status | Disposition |
|----|-------------|----------|--------|-------------|
| G7-REQ-SYNC-001 | Client-side sync engine | P0 | MISSING | ACCEPT |
| G7-REQ-SYNC-002 | Delta/incremental pull | P0 | MISSING | ACCEPT |
| G7-REQ-SYNC-003 | Mutation queue (FIFO) | P1 | MISSING | ACCEPT |
| G7-REQ-SYNC-004 | Cursor invalidation + full resync | P1 | MISSING | ACCEPT |
| G7-REQ-SYNC-005 | Conflict detection (version-based) | P1 | MISSING | ACCEPT |
| G7-REQ-SYNC-006 | Conflict resolution (auto-merge + manual) | P1 | MISSING | ACCEPT |
| G7-REQ-SYNC-007 | Retry with exponential backoff | P2 | MISSING | ACCEPT |
| G7-REQ-SYNC-008 | Idempotency for all mutations | P1 | PARTIAL | ACCEPT |
| G7-REQ-SYNC-009 | Conflict isolation (per-mutation) | P1 | MISSING | ACCEPT |
| G7-REQ-SYNC-010 | Delete conflict handling | P1 | MISSING | ACCEPT |
| G7-REQ-SYNC-011 | Full resync procedure | P1 | MISSING | ACCEPT |
| G7-REQ-SYNC-012 | Crash/restart recovery | P1 | MISSING | ACCEPT |
| G7-REQ-SYNC-013 | Sequence gap detection | P2 | MISSING | DEFER |
| G7-REQ-SYNC-014 | Client request timeout (30s) | P1 | MISSING | ACCEPT |
| G7-REQ-SYNC-015 | Entity type coverage (7 types) | P0 | MISSING | ACCEPT |
| G7-REQ-SYNC-016 | Server-authoritative state management | P1 | MISSING | ACCEPT |
| G7-REQ-SYNC-017 | Per-mutation acknowledgement | P0 | MISSING | ACCEPT |

### 4.3 Auth Requirements (2)

| ID | Description | Priority | Status | Disposition |
|----|-------------|----------|--------|-------------|
| G7-REQ-AUTH-001 | Mobile auth flow (token caching + refresh) | P0 | PARTIAL | ACCEPT |
| G7-REQ-AUTH-002 | Offline token handling | P1 | MISSING | ACCEPT |

### 4.4 Offline Requirements (2)

| ID | Description | Priority | Status | Disposition |
|----|-------------|----------|--------|-------------|
| G7-REQ-OFF-001 | Offline entity subset definition | P1 | PARTIAL | ACCEPT |
| G7-REQ-OFF-002 | Entity-level eligibility rules | P1 | MISSING | DEFER |

### 4.5 Data Requirements (5)

| ID | Description | Priority | Status | Disposition |
|----|-------------|----------|--------|-------------|
| G7-REQ-DATA-001 | Sync metadata tables (4 tables + RLS) | P0 | MISSING | ACCEPT |
| G7-REQ-DATA-002 | Change tracking columns (version + updated_at) | P0 | PARTIAL | ACCEPT |
| G7-REQ-DATA-003 | Client-side local storage schema | P1 | MISSING | ACCEPT |
| G7-REQ-DATA-004 | Sync audit trail (mobile_sync_log) | P2 | MISSING | ACCEPT |
| G7-REQ-DATA-005 | Conflict log (mobile_conflict_log) | P2 | MISSING | ACCEPT |

### 4.6 Security Requirements (6)

| ID | Description | Priority | Status | Disposition |
|----|-------------|----------|--------|-------------|
| G7-REQ-SEC-001 | Offline data encryption | P0 | MISSING | ACCEPT |
| G7-REQ-SEC-002 | Mobile token caching + refresh | P1 | MISSING | ACCEPT |
| G7-REQ-SEC-003 | Device registration + binding | P1 | MISSING | ACCEPT |
| G7-REQ-SEC-004 | Offline authorization enforcement | P1 | MISSING | ACCEPT |
| G7-REQ-SEC-005 | Sync transport security (HTTPS) | P1 | EXISTS | ACCEPT |
| G7-REQ-SEC-006 | Tenant isolation on sync tables | P0 | MISSING | ACCEPT |

### 4.7 Architecture Requirements (4)

| ID | Description | Priority | Status | Disposition |
|----|-------------|----------|--------|-------------|
| G7-REQ-ARCH-001 | ADR-G7-001 approval | P0 | NOT_APPROVED | ACCEPT |
| G7-REQ-ARCH-002 | 12 conflict classes implementation | P0 | DEFINED | ACCEPT |
| G7-REQ-ARCH-003 | Mobile framework selection | P1 | UNKNOWN | ACCEPT |
| G7-REQ-ARCH-004 | Hybrid conflict strategy | P2 | DEFINED | DEFER |

### 4.8 Performance Requirements (4)

| ID | Description | Priority | Status | Disposition |
|----|-------------|----------|--------|-------------|
| G7-REQ-PERF-001 | Mobile API < 200ms response | P1 | NOT_MEASURED | ACCEPT |
| G7-REQ-PERF-002 | Storage quota management | P2 | MISSING | DEFER |
| G7-REQ-PERF-003 | Network state detection | P1 | MISSING | DEFER |
| G7-REQ-PERF-004 | Background sync scheduling | P2 | MISSING | DEFER |

### 4.9 Test Requirements (7)

| ID | Description | Priority | Status | Disposition |
|----|-------------|----------|--------|-------------|
| G7-REQ-TEST-001 | Unit tests for sync engine | P1 | MISSING | ACCEPT |
| G7-REQ-TEST-002 | Integration tests for pull sync | P1 | MISSING | ACCEPT |
| G7-REQ-TEST-003 | Integration tests for push sync | P1 | MISSING | ACCEPT |
| G7-REQ-TEST-004 | Integration tests for conflicts | P2 | MISSING | ACCEPT |
| G7-REQ-TEST-005 | E2E offline/online test | P2 | MISSING | ACCEPT |
| G7-REQ-TEST-006 | Performance tests for mobile APIs | P2 | MISSING | DEFER |
| G7-REQ-TEST-007 | Tenant isolation sync tests | P0 | MISSING | ACCEPT |

### 4.10 Observability Requirements (7)

| ID | Description | Priority | Status | Disposition |
|----|-------------|----------|--------|-------------|
| G7-REQ-OBS-001 | Sync metrics (pull/push counts, latency) | P1 | MISSING | ACCEPT |
| G7-REQ-OBS-002 | Conflict metrics | P1 | MISSING | ACCEPT |
| G7-REQ-OBS-003 | Queue metrics | P1 | MISSING | ACCEPT |
| G7-REQ-OBS-004 | Error metrics | P1 | MISSING | ACCEPT |
| G7-REQ-OBS-005 | Alerting (5 alert types) | P1 | MISSING | ACCEPT |
| G7-REQ-OBS-006 | Dashboards (4 dashboards) | P2 | MISSING | DEFER |
| G7-REQ-OBS-007 | Structured logging | P1 | MISSING | ACCEPT |

### 4.11 Isolation Requirements (6)

| ID | Description | Priority | Status | Disposition |
|----|-------------|----------|--------|-------------|
| G7-REQ-ISO-001 | Tenant-scoped cursors | P0 | PARTIAL | ACCEPT |
| G7-REQ-ISO-002 | Device-scoped sync state | P1 | MISSING | ACCEPT |
| G7-REQ-ISO-003 | User-device binding | P1 | MISSING | ACCEPT |
| G7-REQ-ISO-004 | Failure isolation (per-mutation) | P0 | MISSING | ACCEPT |
| G7-REQ-ISO-005 | Network failure isolation | P0 | MISSING | ACCEPT |
| G7-REQ-ISO-006 | Max devices per user (5) | P2 | MISSING | DEFER |

---

## 5. IMPLEMENTATION STATUS

| Status | Count | Percentage |
|--------|-------|------------|
| ✅ EXISTS | 1 | 1.4% |
| 🔶 PARTIAL | 6 | 8.7% |
| 🔷 DEFINED | 3 | 4.3% |
| ❌ MISSING | 55 | 79.7% |
| ⬜ UNKNOWN | 1 | 1.4% |
| ❌ NOT_APPROVED | 1 | 1.4% |
| ⬜ NOT_MEASURED | 1 | 1.4% |
| **TOTAL** | **69** | 100% |

---

## 6. BLOCKING REQUIREMENTS

These requirements block other requirements and must be resolved first:

| Blocker | Blocks | Resolution |
|---------|--------|------------|
| G7-REQ-ARCH-001 (ADR approval) | SYNC-005, SYNC-006, SYNC-009, SYNC-010, ARCH-002, ARCH-004 | Submit ADR for review |
| G7-REQ-DATA-001 (sync tables) | SEC-006, ISO-001, DATA-004, DATA-005 | Create Flyway migration |
| G7-REQ-DATA-002 (change tracking) | SYNC-002, SYNC-005 | Verify updated_at columns |
| G7-REQ-API-003 (pull API) | SYNC-002, SYNC-004 | Implement PullSyncController |
| G7-REQ-API-004 (push API) | SYNC-003, SYNC-008, SYNC-009 | Implement PushSyncController |
| G7-REQ-SYNC-001 (sync engine) | SYNC-002 through SYNC-017 | Implement SyncEngine |
| G7-REQ-AUTH-001 (mobile auth) | AUTH-002, SEC-002 | Implement mobile token caching |
| G7-REQ-SEC-001 (encryption) | SEC-002, SEC-004 | Define encryption strategy |
| G7-REQ-ARCH-003 (framework) | All client-side requirements | Select mobile framework |

---

## 7. CRITICAL PATH

```
G7-REQ-ARCH-001 (ADR) ──→ G7-REQ-ARCH-002 (12 classes) ──→ G7-REQ-SYNC-005/006 (conflict)
         │
G7-REQ-DATA-001 (tables) ──→ G7-REQ-API-003/004 (APIs) ──→ G7-REQ-SYNC-001 (engine)
         │
G7-REQ-ARCH-003 (framework) ──→ All client-side requirements
         │
G7-REQ-SEC-001 (encryption) ──→ G7-REQ-SEC-002/004 (security)
```

---

## 8. ACCEPTANCE GATES UPDATE

| Gate | Name | Prior Status | Updated Status |
|------|------|-------------|----------------|
| GATE-01 | Identity | PASS | PASS ✅ |
| GATE-02 | Requirements | PASS (39) | **REVIEW** (69 baselined, not approved) |
| GATE-03 | Architecture | CONDITIONAL | CONDITIONAL 🔶 (ADR still pending) |
| GATE-04 | Data | PASS (4 defined) | PASS ✅ (defined, not implemented) |
| GATE-05 | API | PASS (9 defined) | PASS ✅ (defined, not implemented) |
| GATE-06 | Local Storage | NOT_STARTED | NOT_STARTED ❌ |
| GATE-07 | Authentication | PASS | PASS ✅ (infrastructure exists) |
| GATE-08 | Pull Sync | NOT_STARTED | NOT_STARTED ❌ |
| GATE-09 | Queue | NOT_STARTED | NOT_STARTED ❌ |
| GATE-10 | Idempotency | NOT_STARTED | NOT_STARTED ❌ |
| GATE-11 | Push Sync | NOT_STARTED | NOT_STARTED ❌ |
| GATE-12 | Conflict | NOT_STARTED | NOT_STARTED ❌ |
| GATE-13 | Security | NOT_STARTED | NOT_STARTED ❌ |
| GATE-14 | Tenant Isolation | NOT_STARTED | NOT_STARTED ❌ |
| GATE-15 | Observability | NOT_STARTED | NOT_STARTED ❌ |
| GATE-16 | Testing | NOT_STARTED | NOT_STARTED ❌ |
| GATE-17 | Recovery | NOT_STARTED | NOT_STARTED ❌ |
| GATE-18 | Production | NOT_STARTED | NOT_STARTED ❌ |

---

## 9. DoD UPDATE

| Category | Prior Criteria | Updated Criteria |
|----------|---------------|------------------|
| Requirements | "All 39 verified" | "All 69 verified, 57 accepted, 10 deferred" |
| Architecture | 4 criteria | 4 criteria (unchanged) |
| Code | "All 12 WPs" | "All 12 WPs (WP-A through WP-L)" |
| Database | 5 criteria | 5 criteria (4 tables + change tracking) |
| API | "9 APIs" | 9 individual API requirements |
| Tests | "26 tests" | 7 test requirements (decomposing into test cases) |
| Security | 5 criteria | 6 security requirements |
| Tenant Isolation | 4 criteria | 6 isolation requirements |
| Observability | 4 criteria | 7 observability requirements |
| Documentation | 4 criteria | 4 criteria (unchanged) |
| Dependencies | 4 criteria | 4 criteria (unchanged) |

---

## 10. DECISIONS REQUIRED

| Decision | Blocking | Owner | Deadline |
|----------|----------|-------|----------|
| Approve ADR-G7-001 | 6 requirements | Architecture Team | Before WP-G starts |
| Select mobile framework | 15+ requirements | Product Team | Before client implementation |
| Define encryption strategy | 2 requirements | Security Team | Before WP-I starts |
| Approve baseline (69 requirements) | All | Product + Tech Leads | Before implementation |

---

## 11. BASELINE APPROVAL

| Role | Name | Date | Status |
|------|------|------|--------|
| Product Owner | _________________ | _______ | ⬜ PENDING |
| Tech Lead (Backend) | _________________ | _______ | ⬜ PENDING |
| Tech Lead (Mobile) | _________________ | _______ | ⬜ PENDING |
| Security Lead | _________________ | _______ | ⬜ PENDING |
| QA Lead | _________________ | _______ | ⬜ PENDING |

**BASELINE_STATUS: NOT_APPROVED**

**Approval Conditions:**
1. ADR-G7-001 must be APPROVED (not REQUIRES_REVISION)
2. Mobile framework must be SELECTED
3. Encryption strategy must be DEFINED
4. All stakeholders must SIGN-OFF

---

## 12. CHANGE LOG

| Version | Date | Change | Author |
|---------|------|--------|--------|
| V1 | 2026-08-11 | Initial baseline (39 requirements) | Prior Reconciliation |
| V2 | 2026-08-12 | Reconciled baseline (69 requirements) | Forensic Reconciliation |

---

*Generated: 2026-08-12*
*Phase 20 of G7 Requirements Reconciliation*
*Status: DRAFT — NOT APPROVED*
