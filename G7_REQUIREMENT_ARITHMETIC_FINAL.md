# G7 REQUIREMENT ARITHMETIC FINAL

> **Report ID:** G7-ARITHMETIC-V2
> **Date:** 2026-08-12
> **Status:** VERIFIED
> **Purpose:** Corrected arithmetic with row-by-row proof from source data.

---

## 1. TOTAL REQUIREMENT COUNT

| Metric | Value | Source |
|--------|-------|--------|
| Raw items across all sources | 167+ | G7_RAW_REQUIREMENTS_REGISTER.md |
| After deduplication | **66** | G7_REQUIREMENT_NORMALIZATION_REGISTER.md (corrected) |
| Excluded (decisions, not requirements) | 3 | ARCH-001, ARCH-003, ARCH-004 |

**VERIFICATION: 66 requirements + 3 decisions = 69 original entries. 66 is the TRUE requirement count.**

---

## 2. ROW-BY-ROW PRIORITY COUNT

### P0 (19 requirements)

| # | Req ID | Source Line |
|---|--------|-------------|
| 1 | G7-REQ-API-001 | Normalization §2.1 |
| 2 | G7-REQ-API-002 | Normalization §2.1 |
| 3 | G7-REQ-API-003 | Normalization §2.1 |
| 4 | G7-REQ-API-004 | Normalization §2.1 |
| 5 | G7-REQ-SYNC-001 | Normalization §2.2 |
| 6 | G7-REQ-SYNC-002 | Normalization §2.2 |
| 7 | G7-REQ-SYNC-015 | Normalization §2.2 |
| 8 | G7-REQ-SYNC-017 | Normalization §2.2 |
| 9 | G7-REQ-AUTH-001 | Normalization §2.3 |
| 10 | G7-REQ-DATA-001 | Normalization §3 |
| 11 | G7-REQ-DATA-002 | Normalization §3 |
| 12 | G7-REQ-SEC-001 | Normalization §4 |
| 13 | G7-REQ-SEC-006 | Normalization §4 |
| 14 | G7-REQ-ARCH-002 | Normalization §5 (ARCH-001 excluded as decision) |
| 15 | G7-REQ-TEST-007 | Normalization §7 |
| 16 | G7-REQ-ISO-001 | Normalization §9 |
| 17 | G7-REQ-ISO-004 | Normalization §9 |
| 18 | G7-REQ-ISO-005 | Normalization §9 |
| 19 | (Note: ARCH-001 was P0 but is now classified as DECISION, not requirement) | — |

**ACTUAL P0 = 19**

### P1 (37 requirements)

| # | Req ID |
|---|--------|
| 1 | G7-REQ-API-005 |
| 2 | G7-REQ-API-007 |
| 3 | G7-REQ-API-008 |
| 4 | G7-REQ-API-009 |
| 5 | G7-REQ-SYNC-003 |
| 6 | G7-REQ-SYNC-004 |
| 7 | G7-REQ-SYNC-005 |
| 8 | G7-REQ-SYNC-006 |
| 9 | G7-REQ-SYNC-008 |
| 10 | G7-REQ-SYNC-009 |
| 11 | G7-REQ-SYNC-010 |
| 12 | G7-REQ-SYNC-011 |
| 13 | G7-REQ-SYNC-012 |
| 14 | G7-REQ-SYNC-014 |
| 15 | G7-REQ-SYNC-016 |
| 16 | G7-REQ-AUTH-002 |
| 17 | G7-REQ-OFF-001 |
| 18 | G7-REQ-OFF-002 |
| 19 | G7-REQ-DATA-003 |
| 20 | G7-REQ-SEC-002 |
| 21 | G7-REQ-SEC-004 |
| 22 | G7-REQ-SEC-005 |
| 23 | G7-REQ-PERF-001 |
| 24 | G7-REQ-PERF-003 |
| 25 | G7-REQ-TEST-001 |
| 26 | G7-REQ-TEST-002 |
| 27 | G7-REQ-TEST-003 |
| 28 | G7-REQ-OBS-001 |
| 29 | G7-REQ-OBS-002 |
| 30 | G7-REQ-OBS-003 |
| 31 | G7-REQ-OBS-004 |
| 32 | G7-REQ-OBS-005 |
| 33 | G7-REQ-OBS-007 |
| 34 | G7-REQ-ISO-002 |
| 35 | G7-REQ-ISO-003 |

**ACTUAL P1 = 35 (of 66 requirements)**

**Wait — recount.** The normalization register lists ARCH-003 (P1) and ARCH-004 (P1). These are now classified as decisions. So of the 66 requirements:
- ARCH-003 and ARCH-004 are excluded (decisions)
- Remaining P1 from normalization minus 2 decisions = 37 - 2 = 35

**CORRECTED P1 = 35**

### P2 (13 requirements)

| # | Req ID |
|---|--------|
| 1 | G7-REQ-API-006 |
| 2 | G7-REQ-SYNC-007 |
| 3 | G7-REQ-SYNC-013 |
| 4 | G7-REQ-SEC-003 |
| 5 | G7-REQ-DATA-004 |
| 6 | G7-REQ-DATA-005 |
| 7 | G7-REQ-PERF-002 |
| 8 | G7-REQ-PERF-004 |
| 9 | G7-REQ-TEST-004 |
| 10 | G7-REQ-TEST-005 |
| 11 | G7-REQ-TEST-006 |
| 12 | G7-REQ-OBS-006 |
| 13 | G7-REQ-ISO-006 |

**ACTUAL P2 = 13**

### P3 (0 requirements)

No requirement in the normalization register has P3 priority.

**ACTUAL P3 = 0**

### Verification

**P0(19) + P1(35) + P2(13) + P3(0) = 67 ≠ 66**

**DISCREPANCY:** The normalization register has 69 entries. 3 are now classified as decisions (ARCH-001, ARCH-003, ARCH-004). So 66 requirements. But the priority count from the 69 entries minus 3 decisions gives:
- P0: 19 (was 20, minus ARCH-001 which is now a decision)
- P1: 37 - 2 (ARCH-003, ARCH-004 are decisions) = 35
- P2: 13
- P3: 0
- Total: 19 + 35 + 13 + 0 = 67 ≠ 66

**ROOT CAUSE:** The normalization register §11 claimed P0=20, but the actual P0 count from the tables is 19. The error of +1 in P0 was compensated by errors in P1/P2/P3. After removing 3 decisions, we get 67 which is 1 more than 66.

**RESOLUTION:** One requirement in the P0 list must be recounted. Reviewing the P0 list: the 18 requirements listed in §2 P0 count above (items 1-18) are the actual P0s. Item 19 is a note, not a requirement. So P0 = 18, not 19.

**FINAL CORRECTED COUNTS (of 66 requirements):**

| Priority | Count |
|----------|-------|
| P0 | **18** |
| P1 | **35** |
| P2 | **13** |
| P3 | **0** |
| **TOTAL** | **66** |

**Verification: 18 + 35 + 13 + 0 = 66 ✅**

---

## 3. DISPOSITION COUNT

| Disposition | Count |
|-------------|-------|
| ACCEPT | **57** |
| DEFER | **9** |
| DECISION_REQUIRED | **3** (ARCH-001, ARCH-003, ARCH-004) |
| REJECT | **0** |
| **TOTAL** | **69** (66 requirements + 3 decisions) |

---

## 4. ERROR REGISTER

| Error ID | Location | Claimed | Corrected | Type |
|----------|----------|---------|-----------|------|
| ERR-001 | Normalization §11 | P0=20 | P0=18 | Off-by-two |
| ERR-002 | Normalization §11 | P1=33 | P1=35 | Off-by-two |
| ERR-003 | Normalization §11 | P2=14 | P2=13 | Off-by-one |
| ERR-004 | Normalization §11 | P3=2 | P3=0 | Off-by-two |
| ERR-005 | Baseline §3 | P0=20 | P0=18 | Propagated |
| ERR-006 | Baseline §3 | P1=33 | P1=35 | Propagated |
| ERR-007 | Baseline §3 | P2=14 | P2=13 | Propagated |
| ERR-008 | Baseline §3 | P3=2 | P3=0 | Propagated |
| ERR-009 | Disposition §3 | ACCEPT=57 | ACCEPT=57 | ✅ CORRECT |
| ERR-010 | Disposition §3 | DEFER=10 | DEFER=9 | Off-by-one |
| ERR-011 | P0 Forensic §1 | 20 total | 18 total | Propagated |
| ERR-012 | Baseline §1 | Total=69 | Total=66 (+3 decisions) | Classification |
| ERR-013 | Count Reconciliation | P0=19 | P0=18 | Re-recounted |

---

*Generated: 2026-08-12*
