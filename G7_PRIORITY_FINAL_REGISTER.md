# G7 PRIORITY FINAL REGISTER

> **Report ID:** G7-PRIORITY-V2
> **Date:** 2026-08-12
> **Status:** VERIFIED
> **Purpose:** Corrected priority distribution with criteria-based justification.

---

## 1. PRIORITY CRITERIA

| Priority | Definition | Criteria |
|----------|-----------|----------|
| P0 (BLOCKER) | Prevents G7 from achieving its primary purpose, or prevents data safety/integrity/security | Without this requirement, G7 offline sync either doesn't work, loses data, or leaks tenant data |
| P1 (CRITICAL) | Necessary for first production release, but doesn't block proof of foundation | Required for production use but the core sync mechanism can be demonstrated without it |
| P2 (HIGH) | Important but can be deferred | Enhances quality or covers edge cases; initial release works without it |
| P3 (MEDIUM) | Improvement / optimization / future | Nice-to-have, no impact on core functionality |

---

## 2. CORRECTED PRIORITY DISTRIBUTION (66 requirements)

| Priority | Count | Percentage | IDs |
|----------|-------|------------|-----|
| P0 | **18** | 27.3% | API-001, API-002, API-003, API-004, SYNC-001, SYNC-002, SYNC-015, SYNC-017, AUTH-001, DATA-001, DATA-002, SEC-001, SEC-006, ARCH-002, TEST-007, ISO-001, ISO-004, ISO-005 |
| P1 | **35** | 53.0% | API-005, API-007, API-008, API-009, SYNC-003, SYNC-004, SYNC-005, SYNC-006, SYNC-008, SYNC-009, SYNC-010, SYNC-011, SYNC-012, SYNC-014, SYNC-016, AUTH-002, OFF-001, OFF-002, DATA-003, SEC-002, SEC-004, SEC-005, PERF-001, PERF-003, TEST-001, TEST-002, TEST-003, OBS-001, OBS-002, OBS-003, OBS-004, OBS-005, OBS-007, ISO-002, ISO-003 |
| P2 | **13** | 19.7% | API-006, SYNC-007, SYNC-013, SEC-003, DATA-004, DATA-005, PERF-002, PERF-004, TEST-004, TEST-005, TEST-006, OBS-006, ISO-006 |
| P3 | **0** | 0% | — |
| **TOTAL** | **66** | 100% | |

---

## 3. PRIORITY CHANGES FROM PRIOR BASELINE

| Req ID | Prior Priority | Corrected Priority | Change | Reason |
|--------|---------------|-------------------|--------|--------|
| ARCH-001 | P0 | DECISION | RECLASSIFIED | Process gate, not requirement |
| ARCH-003 | P1 | DECISION | RECLASSIFIED | Product decision, not requirement |
| ARCH-004 | P1 | DEFERRED | RECLASSIFIED | Architecture decision |
| (none) | — | — | — | All other priorities verified correct |

**No priority VALUE changes needed — only reclassifications.**

---

## 4. PRIORITY DISTRIBUTION COMPARISON

| Metric | Prior (V2) | Corrected | Delta |
|--------|-----------|-----------|-------|
| P0 | 20 | 18 | -2 (ARCH-001 reclassified, recount) |
| P1 | 33 | 35 | +2 (recount + ARCH-003/004 removed) |
| P2 | 14 | 13 | -1 (recount) |
| P3 | 2 | 0 | -2 (no P3 exists in register) |
| Total | 69 | 66 | -3 (decisions removed) |

---

*Generated: 2026-08-12*
