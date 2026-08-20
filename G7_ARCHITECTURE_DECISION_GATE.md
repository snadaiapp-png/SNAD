# G7 ARCHITECTURE DECISION GATE

> **Report ID:** G7-ARCH-GATE-V2
> **Date:** 2026-08-12
> **Status:** GATE_EVALUATED
> **Purpose:** Verify all architecture decisions required for G7 implementation.

---

## 1. DECISION INVENTORY

| Decision | Status | Authority | Evidence | Dependent Requirements |
|----------|--------|-----------|----------|----------------------|
| ADR-G7-001: Conflict Resolution Policy | **REQUIRES_REVISION** | Architecture Team | ADR-G7-001-MOBILE-CONFLICT-RESOLUTION.md | SYNC-005, SYNC-006, SYNC-009, SYNC-010, ARCH-002, ARCH-004 |
| Mobile Framework Selection | **DECISION_REQUIRED** | Product Team | None — no evaluation started | All client-side requirements (15+) |
| Local Storage Technology | **IMPLIED** by framework | Technical Team | Sync contract specifies SQLite (mobile), IndexedDB (web) | DATA-003 |
| Encryption Strategy | **DECISION_REQUIRED** | Security Team | None — no evaluation started | SEC-001, SEC-002, SEC-004 |
| Background Sync Mechanism | **DECISION_REQUIRED** | Product Team | PERF-004 is DEFERRED | PERF-004 (deferred) |
| Push Notification Strategy | **NOT_IN_SCOPE** | — | G8 scope, not G7 | None |
| Device Identity Strategy | **DECISION_REQUIRED** | Security Team | SEC-003 is P2 | SEC-003 |
| Offline Authentication Model | **DECISION_REQUIRED** | Security Team | AUTH-001 + SEC-004 defined | AUTH-001, SEC-004 |

---

## 2. DECISIONS RESOLVED

| Decision | Resolution | Source |
|----------|-----------|--------|
| Offline Duration | OPTION B: Staleness Detection with Natural Auth Bound (7-day refresh token is hard max) | G7_C2_C3_ARCHITECTURAL_DECISION.md |
| Conflict Lifecycle | OPTION C: Technical Retention Without User-Resolution SLA (1 year retention, auto-resolve, archive) | G7_C2_C3_ARCHITECTURAL_DECISION.md |
| Conflict Policy (12 classes) | Hybrid: Auto-merge (Account, Contact, Task) + User Resolution (Lead, Opportunity, Pipeline, Tags, Custom Fields) + Server Authority (Activity, Note, Financial) | ADR-G7-001-MOBILE-CONFLICT-RESOLUTION.md |

---

## 3. DECISIONS STILL REQUIRED

| # | Decision | Blocking | Required Before | Owner |
|---|----------|----------|-----------------|-------|
| 1 | ADR-G7-001 APPROVAL | 6 requirements | WP-G (Conflict Resolution) | Architecture Team |
| 2 | Mobile Framework Selection | 15+ requirements | Client implementation | Product Team |
| 3 | Encryption Strategy | 2 requirements (SEC-001, SEC-002) | WP-I (Security) | Security Team |

---

## 4. ARCHITECTURE DECISION GATE VERDICT

| Gate Condition | Status |
|----------------|--------|
| ADR resolved | ⚠️ PARTIAL (content defined, approval pending) |
| Framework selected | ❌ NO |
| Encryption defined | ❌ NO |
| Offline duration resolved | ✅ YES |
| Conflict lifecycle resolved | ✅ YES |
| Background sync resolved | ⚠️ DEFERRED |
| Device identity resolved | ❌ NO |
| Offline auth model resolved | ⚠️ PARTIAL (requirements defined, strategy pending) |

**ARCHITECTURE_DECISION_GATE = CONDITIONAL_PASS**
**3 DECISIONS REQUIRED before full implementation can begin.**

---

*Generated: 2026-08-12*
