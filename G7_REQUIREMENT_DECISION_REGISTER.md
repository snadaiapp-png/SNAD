# G7 REQUIREMENT DECISION REGISTER

> **Report ID:** G7-REQ-DECISION-V1
> **Date:** 2026-08-12
> **Status:** DRAFT — NOT APPROVED
> **Purpose:** Record all decisions made during the requirements reconciliation process.

---

## 1. DECISION LOG

### DECISION-001: Rebuild Requirement Count from Sources

| Field | Value |
|-------|-------|
| **Decision** | Do NOT accept either "39" or "101" as the requirement count. Rebuild from all source documents. |
| **Rationale** | The "39" count came from a prior reconciliation that missed detailed requirements from sync contract and security gate. The "101" count has no verifiable source and may be an artifact of pre-deduplication counting. |
| **Outcome** | 69 normalized requirements identified after deduplication of 300 raw items across 12 source documents. |
| **Impact** | TRUE_G7_REQUIREMENTS_COUNT = 69 (not 39, not 101) |

---

### DECISION-002: Canonical Source Authority

| Field | Value |
|-------|-------|
| **Decision** | SRC-01 (crm-execution-data.ts lines 129-137) is the CANONICAL source for G7 scope. All other sources are DERIVED. |
| **Rationale** | crm-execution-data.ts is the product specification file maintained by the product team. It has the highest authority level. |
| **Outcome** | 7 scope items from SRC-01 decompose into 69 normalized requirements. |
| **Impact** | All requirement descriptions must be consistent with SRC-01 scope items. |

---

### DECISION-003: Normalized ID Scheme

| Field | Value |
|-------|-------|
| **Decision** | Use `G7-REQ-{CATEGORY}-{SEQ}` as the canonical requirement ID scheme. |
| **Rationale** | The prior baseline used `G7-MOB-{CATEGORY}-{SEQ}` which was source-specific. The new scheme is source-agnostic and covers all categories. |
| **Outcome** | 69 requirements with IDs: G7-REQ-API-001 through API-009, SYNC-001 through SYNC-017, AUTH-001/002, OFF-001/002, DATA-001 through DATA-005, SEC-001 through SEC-006, ARCH-001 through ARCH-004, PERF-001 through PERF-004, TEST-001 through TEST-007, OBS-001 through OBS-007, ISO-001 through ISO-006. |
| **Impact** | All downstream documents (backlog, gates, DoD) must reference normalized IDs. |

---

### DECISION-004: Reclassify Misclassified Requirements

| Field | Value |
|-------|-------|
| **Decision** | Reclassify requirements that were misclassified in the prior baseline. |
| **Rationale** | G7-MOB-NFR-002 ("Offline data encrypted at rest") is a SECURITY requirement, not non-functional. G7-MOB-FR-002 ("Offline sync schema") is a DATA requirement, not functional. |
| **Outcome** | 2 requirements reclassified: NFR-002 → SEC-001, FR-002 → DATA-001. |
| **Impact** | Priority and category counts adjusted accordingly. |

---

### DECISION-005: P0 Priority = 20 (not 12)

| Field | Value |
|-------|-------|
| **Decision** | The TRUE P0 count is 20, not 12 as claimed in the prior baseline. |
| **Rationale** | The prior baseline undercounted P0 by: (1) treating some P0 items as P1, (2) not including security/architecture/isolation requirements in the P0 count, (3) having a counting error (13 items marked P0 but summary said 12). |
| **Outcome** | P0=20, P1=33, P2=14, P3=2. |
| **Impact** | More requirements are BLOCKER priority than previously understood. |

---

### DECISION-006: Sync Contract as Behavioral Truth

| Field | Value |
|-------|-------|
| **Decision** | G7_SYNC_CONTRACT_TRUTH.md is the single source of truth for all sync behavioral requirements. |
| **Rationale** | It is the most detailed and authoritative document for sync behavior, with explicit invariants and state machine definitions. |
| **Outcome** | 17 sync requirements derived from the sync contract. |
| **Impact** | All sync implementation must conform to the sync contract. |

---

### DECISION-007: Security Gate as Security Truth

| Field | Value |
|-------|-------|
| **Decision** | G7_SECURITY_FINAL_GATE.md is the single source of truth for all security requirements. |
| **Rationale** | It provides the most comprehensive security specification with existing vs. missing analysis. |
| **Outcome** | 6 security requirements derived from the security gate. |
| **Impact** | Security implementation must address all 4 FAIL items. |

---

### DECISION-008: ADR-G7-001 Remains BLOCKER

| Field | Value |
|-------|-------|
| **Decision** | ADR-G7-001 approval remains a P0 BLOCKER. Do not proceed with conflict resolution implementation until ADR is approved. |
| **Rationale** | The ADR defines the conflict resolution policy. Implementing without approved policy risks rework. |
| **Outcome** | ARCH-001 = P0, status = NOT_APPROVED. |
| **Impact** | WP-G (Conflict Resolution) is blocked until ADR approved. |

---

### DECISION-009: 69 is the TRUE Requirement Count

| Field | Value |
|-------|-------|
| **Decision** | TRUE_G7_REQUIREMENTS_COUNT = 69. This is the final, reconciled, deduplicated count. |
| **Rationale** | 300 raw items across 12 sources → 12 duplicate clusters → 69 unique normalized requirements. |
| **Outcome** | 69 requirements across 11 categories. |
| **Impact** | All DoD criteria reference "69 requirements" instead of "39". |

---

### DECISION-010: BASELINE_STATUS = NOT_APPROVED

| Field | Value |
|-------|-------|
| **Decision** | The master baseline status is NOT_APPROVED. Requirements are baselined for tracking but not formally approved for implementation. |
| **Rationale** | ADR-G7-001 is not approved. Mobile framework is not selected. Encryption strategy is not defined. These blocking unknowns prevent formal approval. |
| **Outcome** | BASELINE_STATUS = NOT_APPROVED |
| **Impact** | Implementation can begin on unblocked work packages (WP-A, WP-B, WP-D) but not on blocked ones (WP-G, WP-I). |

---

## 2. DECISION SUMMARY

| Decision | Status | Impact |
|----------|--------|--------|
| DECISION-001 | ✅ MADE | Count rebuilt from sources |
| DECISION-002 | ✅ MADE | Canonical source identified |
| DECISION-003 | ✅ MADE | ID scheme established |
| DECISION-004 | ✅ MADE | 2 requirements reclassified |
| DECISION-005 | ✅ MADE | P0 = 20 |
| DECISION-006 | ✅ MADE | Sync contract = truth |
| DECISION-007 | ✅ MADE | Security gate = truth |
| DECISION-008 | ✅ MADE | ADR remains blocker |
| DECISION-009 | ✅ MADE | TRUE count = 69 |
| DECISION-010 | ✅ MADE | Status = NOT_APPROVED |

**All 10 decisions made.**

---

*Generated: 2026-08-12*
*Phase 19 of G7 Requirements Reconciliation*
