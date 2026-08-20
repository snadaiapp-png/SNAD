# G7 BASELINE BLOCKER REGISTER

> **Report ID:** G7-BLOCKER-REG-V1
> **Date:** 2026-08-12
> **Status:** COMPLETE
> **Purpose:** Catalog all blockers preventing baseline approval, with severity and resolution paths.

---

## 1. BLOCKER CLASSIFICATION

| Severity | Definition |
|----------|------------|
| CRITICAL | Must be resolved before ANY implementation work begins |
| HIGH | Must be resolved before P0 implementation |
| MEDIUM | Must be resolved before P1 implementation |
| LOW | Can be resolved during implementation |

---

## 2. BLOCKER REGISTER

### BLOCKER-001: Arithmetic Errors in Baseline Summaries

| Field | Value |
|-------|-------|
| **ID** | BLOCKER-001 |
| **Severity** | CRITICAL |
| **Category** | DATA_INTEGRITY |
| **Description** | The baseline contains 13 arithmetic errors (6 unique, 7 propagated) in priority and disposition summary statistics. P0=20 (should be 19), P1=33 (should be 37), P2=14 (should be 13), P3=2 (should be 0), ACCEPT=57 (should be 60), DEFER=10 (should be 9). |
| **Evidence** | G7_REQUIREMENT_COUNT_RECONCILIATION.md §5 |
| **Impact** | Any decision made on priority counts is unreliable. Sprint planning based on these numbers would be wrong. |
| **Resolution** | Correct all summary tables in the baseline and all dependent registers. |
| **Status** | IDENTIFIED — Correction possible without changing requirements |

---

### BLOCKER-002: ADR-G7-001 NOT APPROVED

| Field | Value |
|-------|-------|
| **ID** | BLOCKER-002 |
| **Severity** | CRITICAL |
| **Category** | ARCHITECTURE_DECISION |
| **Description** | ADR-G7-001 (conflict resolution policy) status is REQUIRES_REVISION. The foundational architecture decision for conflict resolution has not been approved. All 12 conflict classes and the conflict resolution matrix depend on this decision. |
| **Evidence** | ADR-G7-001.md status: REQUIRES_REVISION |
| **Impact** | Cannot implement conflict resolution (SYNC-005, SYNC-006, SYNC-009, SYNC-010, ARCH-002) without approved policy. 5 P0/P1 requirements blocked. |
| **Resolution** | Complete ADR-G7-001 revision and obtain approval. |
| **Status** | IDENTIFIED — Requires stakeholder action |

---

### BLOCKER-003: Framework Selection Pending

| Field | Value |
|-------|-------|
| **ID** | BLOCKER-003 |
| **Severity** | CRITICAL |
| **Category** | ARCHITECTURE_DECISION |
| **Description** | ARCH-003 (mobile framework selection) is unresolved. React Native, Flutter, Capacitor, and PWA are all candidates. This decision determines the entire client-side implementation approach. |
| **Evidence** | G7_MASTER_TRUTH_REPORT.md UNKNOWN-001 |
| **Impact** | Cannot begin client-side implementation. Blocks all SYNC-* requirements, DATA-003 (local storage schema), and all test requirements. |
| **Resolution** | Complete framework evaluation and make selection decision. |
| **Status** | IDENTIFIED — Requires stakeholder action |

---

### BLOCKER-004: Encryption Strategy Undefined

| Field | Value |
|-------|-------|
| **ID** | BLOCKER-004 |
| **Severity** | CRITICAL |
| **Category** | SECURITY |
| **Description** | SEC-001 (offline data encryption) has no defined strategy. SQLCipher, OS-level encryption, and other options are mentioned but none selected. No encryption implementation exists. |
| **Evidence** | G7_SECURITY_FINAL_GATE.md SEC-001 FAIL |
| **Impact** | Security gate FAIL. Cannot ship mobile offline without encryption. Blocks SEC-001 and SEC-006 (tenant isolation). |
| **Resolution** | Evaluate encryption options, select approach, implement. |
| **Status** | IDENTIFIED — Requires technical decision |

---

### BLOCKER-005: Zero P0 Traceability

| Field | Value |
|-------|-------|
| **ID** | BLOCKER-005 |
| **Severity** | HIGH |
| **Category** | TRACEABILITY |
| **Description** | 0/19 P0 requirements are fully traced. 16/19 have zero implementation. 3/19 have only partial implementation (existing web code, not mobile-specific). |
| **Evidence** | G7_REQUIREMENT_TRACEABILITY_AUDIT.md §4 |
| **Impact** | No evidence that any P0 requirement will be met. Cannot verify readiness for implementation. |
| **Resolution** | Acceptable for pre-implementation baseline, but must be resolved before implementation begins. |
| **Status** | EXPECTED — Feature not yet built |

---

### BLOCKER-006: Zero Acceptance Criteria

| Field | Value |
|-------|-------|
| **ID** | BLOCKER-006 |
| **Severity** | HIGH |
| **Category** | REQUIREMENTS_QUALITY |
| **Description** | 0/69 requirements have explicit, testable acceptance criteria. The normalization register defines WHAT each requirement is but not what DONE looks like. |
| **Evidence** | G7_REQUIREMENT_TRACEABILITY_AUDIT.md §5 |
| **Impact** | Cannot verify when a requirement is implemented correctly. Definition of Done (46 criteria) operates at feature level, not requirement level. |
| **Resolution** | Add acceptance criteria to each normalized requirement before implementation. |
| **Status** | IDENTIFIED — Requirements improvement needed |

---

### BLOCKER-007: 3 Architecture Decisions Misclassified as Requirements

| Field | Value |
|-------|-------|
| **ID** | BLOCKER-007 |
| **Severity** | MEDIUM |
| **Category** | CLASSIFICATION |
| **Description** | ARCH-001 (ADR approval), ARCH-003 (framework selection), and ARCH-004 (hybrid strategy) are architecture/product decisions, not implementation requirements. They should be tracked as decisions, not requirements. |
| **Evidence** | G7_BASELINE_AUDIT_REPORT.md AUDIT-06 |
| **Impact** | Inflates requirement count, confuses implementation tracking with decision tracking. |
| **Resolution** | Reclassify as decisions and remove from requirement count (69→66). |
| **Status** | IDENTIFIED — Classification fix possible |

---

### BLOCKER-008: No Stakeholder Sign-Off

| Field | Value |
|-------|-------|
| **ID** | BLOCKER-008 |
| **Severity** | MEDIUM |
| **Category** | GOVERNANCE |
| **Description** | No stakeholder has signed off on the baseline. The decision register (DECISION-001 through DECISION-010) was created during reconciliation but no external approval exists. |
| **Evidence** | G7_REQUIREMENT_DECISION_REGISTER.md — no sign-off section |
| **Impact** | Baseline lacks authority. Can be challenged or overridden at any time. |
| **Resolution** | Obtain stakeholder approval (product, architecture, security, QA). |
| **Status** | IDENTIFIED — Requires stakeholder action |

---

### BLOCKER-009: Security Gates Failing

| Field | Value |
|-------|-------|
| **ID** | BLOCKER-009 |
| **Severity** | HIGH |
| **Category** | SECURITY |
| **Description** | 4 security gates FAIL: SEC-001 (encryption), SEC-003 (device identity), SEC-004 (device binding), SEC-008 (offline auth). These are P0/P1 security requirements. |
| **Evidence** | G7_SECURITY_FINAL_GATE.md §4 |
| **Impact** | Security-critical gaps. Cannot deploy mobile offline without passing security gates. |
| **Resolution** | Implement encryption, device registration, device binding, and offline auth. |
| **Status** | IDENTIFIED — Requires implementation |

---

## 3. BLOCKER SUMMARY

| Severity | Count | IDs |
|----------|-------|-----|
| CRITICAL | 4 | BLOCKER-001, BLOCKER-002, BLOCKER-003, BLOCKER-004 |
| HIGH | 3 | BLOCKER-005, BLOCKER-006, BLOCKER-009 |
| MEDIUM | 2 | BLOCKER-007, BLOCKER-008 |
| LOW | 0 | — |
| **TOTAL** | **9** | |

---

## 4. BLOCKER RESOLUTION PATH

```
Phase 0: CORRECTION (Before any work)
  └── BLOCKER-001: Fix arithmetic errors in baseline summaries

Phase 0: DECISIONS (Before implementation)
  ├── BLOCKER-002: Approve ADR-G7-001
  ├── BLOCKER-003: Select mobile framework
  └── BLOCKER-004: Define encryption strategy

Phase 0: CLASSIFICATION (Before tracking)
  └── BLOCKER-007: Reclassify 3 decisions as non-requirements

Phase 0: GOVERNANCE (Before authority)
  └── BLOCKER-008: Obtain stakeholder sign-off

Phase 1: PRE-IMPLEMENTATION (Before coding)
  ├── BLOCKER-005: Add acceptance criteria for all P0 requirements
  └── BLOCKER-006: Add acceptance criteria for all requirements

Phase 2: IMPLEMENTATION (During coding)
  └── BLOCKER-009: Implement security gates
```

---

## 5. BASELINE APPROVAL ASSESSMENT

**Can this baseline be approved?**

| Condition | Status |
|-----------|--------|
| All requirements correctly classified | ❌ NO (3 misclassified) |
| All arithmetic correct | ❌ NO (13 errors) |
| All P0 requirements traced | ❌ NO (0/19) |
| All acceptance criteria defined | ❌ NO (0/69) |
| All architecture decisions approved | ❌ NO (ADR-001 pending) |
| All security gates passing | ❌ NO (4 FAIL) |
| Stakeholder sign-off obtained | ❌ NO |

**BASELINE_CAN_BE_APPROVED = NO**

**Minimum for conditional approval:**
1. Fix arithmetic errors (BLOCKER-001) — mechanical fix
2. Approve or reject ADR-G7-001 (BLOCKER-002) — stakeholder decision
3. Select framework (BLOCKER-003) — stakeholder decision
4. Define encryption (BLOCKER-004) — technical decision

---

*Generated: 2026-08-12*
