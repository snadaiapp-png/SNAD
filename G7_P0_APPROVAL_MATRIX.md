# G7 P0 APPROVAL MATRIX

> **Report ID:** G7-P0-MATRIX-V1
> **Date:** 2026-08-12
> **Status:** COMPLETE
> **Purpose:** Forensic audit of all 18 P0 requirements

---

## 1. P0 REQUIREMENTS (18 total)

### P0 Audit Checklist Per Requirement:
- Is P0 justified? → Does it block G7 primary purpose?
- Does it have Evidence?
- Does it have Acceptance Criteria?
- Does it have Test Path?
- Does it have Dependency Resolution?
- Is there a Blocking Decision?

---

### G7-REQ-API-001 — Entity List API (P0)

| Check | Status | Detail |
|-------|--------|--------|
| P0 Justified | ✅ YES | Without mobile-optimized list, mobile cannot consume entities |
| Evidence | ❌ NO | 0 mobile endpoints exist |
| Acceptance Criteria | ✅ YES | GIVEN/WHEN/THEN defined |
| Test Path | ❌ NO | No tests exist |
| Dependency Resolution | ✅ YES | No external dependencies |
| Blocking Decision | ❌ NO | — |
| **Classification** | **P0_APPROVED** | Valid P0, blocked only by greenfield status |

---

### G7-REQ-API-002 — Entity Detail API (P0)

| Check | Status | Detail |
|-------|--------|--------|
| P0 Justified | ✅ YES | Mobile needs optimized detail views |
| Evidence | ❌ NO | 0 mobile endpoints exist |
| Acceptance Criteria | ✅ YES | Defined |
| Test Path | ❌ NO | — |
| Dependency Resolution | ✅ YES | None |
| Blocking Decision | ❌ NO | — |
| **Classification** | **P0_APPROVED** | Valid P0, blocked only by greenfield status |

---

### G7-REQ-API-003 — Delta Sync Pull API (P0)

| Check | Status | Detail |
|-------|--------|--------|
| P0 Justified | ✅ YES | Core read path for offline sync |
| Evidence | ❌ NO | 0 sync endpoints exist |
| Acceptance Criteria | ✅ YES | Defined |
| Test Path | ❌ NO | — |
| Dependency Resolution | ⚠️ PARTIAL | Depends on DATA-001, SYNC-001 |
| Blocking Decision | ❌ NO | — |
| **Classification** | **P0_APPROVED** | Valid P0, dependencies are also P0 (critical path) |

---

### G7-REQ-API-004 — Batch Sync Push API (P0)

| Check | Status | Detail |
|-------|--------|--------|
| P0 Justified | ✅ YES | Core write path for offline sync |
| Evidence | ❌ NO | 0 sync endpoints exist |
| Acceptance Criteria | ✅ YES | Defined |
| Test Path | ❌ NO | — |
| Dependency Resolution | ⚠️ PARTIAL | Depends on DATA-001, SYNC-001 |
| Blocking Decision | ❌ NO | — |
| **Classification** | **P0_APPROVED** | Valid P0, dependencies are P0 |

---

### G7-REQ-SYNC-001 — Sync Engine (P0)

| Check | Status | Detail |
|-------|--------|--------|
| P0 Justified | ✅ YES | Foundational component — nothing works without it |
| Evidence | ❌ NO | SyncEngine.java empty |
| Acceptance Criteria | ✅ YES | Defined |
| Test Path | ❌ NO | — |
| Dependency Resolution | ⚠️ PARTIAL | Depends on DATA-001, DATA-002, API-003, API-004 |
| Blocking Decision | ⚠️ YES | UNKNOWN-001 (framework selection) |
| **Classification** | **P0_JUSTIFIED_BUT_BLOCKED** | Valid P0, blocked by framework selection |

---

### G7-REQ-SYNC-002 — Delta Pull (P0)

| Check | Status | Detail |
|-------|--------|--------|
| P0 Justified | ✅ YES | Core read path |
| Evidence | ❌ NO | No cursor sync exists |
| Acceptance Criteria | ✅ YES | Defined |
| Test Path | ❌ NO | — |
| Dependency Resolution | ⚠️ PARTIAL | Depends on SYNC-001, DATA-001, DATA-002 |
| Blocking Decision | ❌ NO | — |
| **Classification** | **P0_APPROVED** | Valid P0, no blocking decisions |

---

### G7-REQ-SYNC-015 — Entity Coverage (P0)

| Check | Status | Detail |
|-------|--------|--------|
| P0 Justified | ✅ YES | All 7 entity types must sync |
| Evidence | ❌ NO | No entity sync exists |
| Acceptance Criteria | ✅ YES | Defined |
| Test Path | ❌ NO | — |
| Dependency Resolution | ⚠️ PARTIAL | Depends on SYNC-001 |
| Blocking Decision | ❌ NO | — |
| **Classification** | **P0_APPROVED** | Valid P0 |

---

### G7-REQ-SYNC-017 — Per-Mutation ACK (P0)

| Check | Status | Detail |
|-------|--------|--------|
| P0 Justified | ✅ YES | Data integrity critical |
| Evidence | ❌ NO | No batch processing exists |
| Acceptance Criteria | ✅ YES | Defined |
| Test Path | ❌ NO | — |
| Dependency Resolution | ⚠️ PARTIAL | Depends on SYNC-001 |
| Blocking Decision | ❌ NO | — |
| **Classification** | **P0_APPROVED** | Valid P0 |

---

### G7-REQ-AUTH-001 — Mobile Auth Flow (P0)

| Check | Status | Detail |
|-------|--------|--------|
| P0 Justified | ✅ YES | Prerequisite for all API calls |
| Evidence | ⚠️ PARTIAL | JWT exists (web), mobile missing |
| Acceptance Criteria | ✅ YES | Defined |
| Test Path | ❌ NO | — |
| Dependency Resolution | ⚠️ PARTIAL | Depends on UNKNOWN-003 (encryption) |
| Blocking Decision | ⚠️ YES | UNKNOWN-003 (encryption strategy) |
| **Classification** | **P0_JUSTIFIED_BUT_BLOCKED** | Valid P0, blocked by encryption decision |

---

### G7-REQ-DATA-001 — Sync Tables (P0)

| Check | Status | Detail |
|-------|--------|--------|
| P0 Justified | ✅ YES | Data foundation for all sync |
| Evidence | ❌ NO | 0 tables exist |
| Acceptance Criteria | ✅ YES | Defined |
| Test Path | ❌ NO | — |
| Dependency Resolution | ✅ YES | No external dependencies |
| Blocking Decision | ❌ NO | — |
| **Classification** | **P0_APPROVED** | Valid P0, implementation-ready |

---

### G7-REQ-DATA-002 — Change Tracking (P0)

| Check | Status | Detail |
|-------|--------|--------|
| P0 Justified | ✅ YES | Enables delta sync |
| Evidence | ⚠️ PARTIAL | version exists on some tables |
| Acceptance Criteria | ✅ YES | Defined |
| Test Path | ❌ NO | — |
| Dependency Resolution | ✅ YES | None |
| Blocking Decision | ❌ NO | — |
| **Classification** | **P0_APPROVED** | Valid P0, partially exists |

---

### G7-REQ-SEC-001 — Offline Encryption (P0)

| Check | Status | Detail |
|-------|--------|--------|
| P0 Justified | ✅ YES | Data protection |
| Evidence | ❌ NO | No encryption strategy defined |
| Acceptance Criteria | ✅ YES | Defined |
| Test Path | ❌ NO | — |
| Dependency Resolution | ❌ NO | Depends on UNKNOWN-003 (encryption strategy) |
| Blocking Decision | ❌ YES | UNKNOWN-003 (encryption) |
| **Classification** | **P0_JUSTIFIED_BUT_BLOCKED** | Valid P0, blocked by encryption strategy |

---

### G7-REQ-SEC-006 — Tenant Isolation (P0)

| Check | Status | Detail |
|-------|--------|--------|
| P0 Justified | ✅ YES | Security critical — cross-tenant leak |
| Evidence | ⚠️ PARTIAL | RLS exists on some tables |
| Acceptance Criteria | ✅ YES | Defined |
| Test Path | ❌ NO | — |
| Dependency Resolution | ⚠️ PARTIAL | Depends on DATA-001 |
| Blocking Decision | ❌ NO | — |
| **Classification** | **P0_APPROVED** | Valid P0 |

---

### G7-REQ-ARCH-002 — 12 Conflict Classes (P0)

| Check | Status | Detail |
|-------|--------|--------|
| P0 Justified | ✅ YES | Conflict classification is core |
| Evidence | ⚠️ PARTIAL | Classes C1-C12 defined in ADR |
| Acceptance Criteria | ✅ YES | Defined |
| Test Path | ❌ NO | — |
| Dependency Resolution | ❌ NO | Depends on ADR-G7-001 (REQUIRES_REVISION) |
| Blocking Decision | ❌ YES | ADR-G7-001 not approved |
| **Classification** | **P0_JUSTIFIED_BUT_BLOCKED** | Valid P0, blocked by ADR |

---

### G7-REQ-TEST-007 — Tenant Isolation Tests (P0)

| Check | Status | Detail |
|-------|--------|--------|
| P0 Justified | ✅ YES | Security verification |
| Evidence | ❌ NO | 0 tests exist |
| Acceptance Criteria | ✅ YES | Defined |
| Test Path | ❌ NO | — |
| Dependency Resolution | ⚠️ PARTIAL | Depends on DATA-001, SEC-006 |
| Blocking Decision | ❌ NO | — |
| **Classification** | **P0_APPROVED** | Valid P0 |

---

### G7-REQ-ISO-001 — Tenant-Scoped Cursors (P0)

| Check | Status | Detail |
|-------|--------|--------|
| P0 Justified | ✅ YES | Security critical |
| Evidence | ⚠️ PARTIAL | CursorCodec has tenant hash |
| Acceptance Criteria | ✅ YES | Defined |
| Test Path | ❌ NO | — |
| Dependency Resolution | ⚠️ PARTIAL | Depends on DATA-001 |
| Blocking Decision | ❌ NO | — |
| **Classification** | **P0_APPROVED** | Valid P0 |

---

### G7-REQ-ISO-004 — Failure Isolation (P0)

| Check | Status | Detail |
|-------|--------|--------|
| P0 Justified | ✅ YES | Prevents cascade failures |
| Evidence | ❌ NO | No batch processing exists |
| Acceptance Criteria | ✅ YES | Defined |
| Test Path | ❌ NO | — |
| Dependency Resolution | ⚠️ PARTIAL | Depends on SYNC-001 |
| Blocking Decision | ❌ NO | — |
| **Classification** | **P0_APPROVED** | Valid P0 |

---

### G7-REQ-ISO-005 — Network Isolation (P0)

| Check | Status | Detail |
|-------|--------|--------|
| P0 Justified | ✅ YES | Prevents cursor corruption |
| Evidence | ❌ NO | No network isolation exists |
| Acceptance Criteria | ✅ YES | Defined |
| Test Path | ❌ NO | — |
| Dependency Resolution | ⚠️ PARTIAL | Depends on SYNC-001 |
| Blocking Decision | ❌ NO | — |
| **Classification** | **P0_APPROVED** | Valid P0 |

---

## 2. P0 CLASSIFICATION SUMMARY

| Classification | Count | IDs |
|---------------|-------|-----|
| P0_APPROVED | 14 | API-001, API-002, API-003, API-004, SYNC-002, SYNC-015, SYNC-017, DATA-001, DATA-002, SEC-006, TEST-007, ISO-001, ISO-004, ISO-005 |
| P0_JUSTIFIED_BUT_BLOCKED | 4 | SYNC-001 (framework), AUTH-001 (encryption), SEC-001 (encryption), ARCH-002 (ADR) |
| P0_MISCLASSIFIED | 0 | — |
| P0_UNVERIFIED | 0 | — |
| P0_DEFERRED | 0 | — |
| **TOTAL** | **18** | |

---

## 3. P0 BLOCKING ASSESSMENT

| Blocker | Count | Affected P0s | Resolution |
|---------|-------|-------------|------------|
| Framework undecided | 1 | SYNC-001 | Product team selection |
| Encryption undecided | 2 | AUTH-001, SEC-001 | Security team decision |
| ADR-G7-001 pending | 1 | ARCH-002 | Operator approval |
| **Total blocked P0s** | **4** | | |

**14/18 P0s have no blocking decisions.** They are valid and approved in principle, blocked only by greenfield status (no implementation exists).

---

## 4. P0 CRITICAL PATH

```
DATA-001 (Sync Tables) ─── P0_APPROVED ─── foundation
  ├──→ DATA-002 (Change Tracking) ─── P0_APPROVED
  ├──→ SEC-006 (Tenant Isolation) ─── P0_APPROVED
  ├──→ ISO-001 (Tenant-Scoped Cursors) ─── P0_APPROVED
  └──→ TEST-007 (Tenant Isolation Tests) ─── P0_APPROVED

DATA-001 + DATA-002 → SYNC-001 (Sync Engine) ─── P0_JUSTIFIED_BUT_BLOCKED ⚠️
  ├──→ SYNC-002 (Delta Pull) ─── P0_APPROVED
  ├──→ SYNC-015 (Entity Coverage) ─── P0_APPROVED
  ├──→ SYNC-017 (Per-Mutation ACK) ─── P0_APPROVED
  ├──→ ISO-004 (Failure Isolation) ─── P0_APPROVED
  └──→ ISO-005 (Network Isolation) ─── P0_APPROVED

API-003 + API-004 → SYNC-001 ─── P0_APPROVED (APIs independent)

AUTH-001 (Mobile Auth) ─── P0_JUSTIFIED_BUT_BLOCKED ⚠️

SEC-001 (Encryption) ─── P0_JUSTIFIED_BUT_BLOCKED ⚠️

ARCH-002 (12 Conflict Classes) ─── P0_JUSTIFIED_BUT_BLOCKED ⚠️
```

---

## 5. P0 VERDICT

**14/18 P0s are APPROVED (valid, no blocking decisions).**

**4/18 P0s are JUSTIFIED BUT BLOCKED:**
- SYNC-001: Blocked by framework selection
- AUTH-001: Blocked by encryption strategy
- SEC-001: Blocked by encryption strategy
- ARCH-002: Blocked by ADR-G7-001

**ALL 18 P0s have valid acceptance criteria (100%).**

**0/18 P0s have full traceability (0%) — expected for greenfield.**

---

*Generated: 2026-08-12*
*G7 Mission 5 — P0 Forensic Approval Matrix*
