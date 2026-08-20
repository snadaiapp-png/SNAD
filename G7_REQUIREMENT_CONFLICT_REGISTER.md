# G7 REQUIREMENT CONFLICT REGISTER

> **Report ID:** G7-REQ-CONFLICT-V1
> **Date:** 2026-08-12
> **Status:** DRAFT — NOT APPROVED
> **Purpose:** Identify, analyze, and resolve all conflicts between requirements across sources.

---

## 1. CONFLICT CATEGORIES

| Type | Description |
|------|-------------|
| **DEFINITIONAL** | Same requirement ID used with different meanings across sources |
| **PRIORITIZATION** | Same requirement assigned different priorities across sources |
| **SCOPE** | Same requirement has different scope/boundaries across sources |
| **EXISTENCE** | Requirement exists in one source but contradicts another |
| **AGGREGATION** | One source treats as single requirement, another splits into multiple |

---

## 2. DEFINITIONAL CONFLICTS

### CONFLICT-001: G7-MOB-FR-001 — "Mobile-optimized CRM entity APIs"

| Source | Meaning |
|--------|---------|
| SRC-03 (Baseline) | Single requirement: "Mobile-optimized CRM entity APIs" (P0) |
| SRC-15 (API Contract) | Split into 2 APIs: Entity List (API-06) + Entity Detail (API-05) |
| SRC-06 (Gap Register) | "Mobile Entity APIs: optimized payloads, <200ms" (GAP-009, P1) |

**Analysis:** The baseline treats this as one requirement. The API contract splits it into list and detail. The gap register adds a performance constraint.

**Resolution:** Split into G7-REQ-API-001 (list) and G7-REQ-API-002 (detail). Performance constraint merges into G7-REQ-PERF-001.

---

### CONFLICT-002: G7-MOB-FR-002 — "Offline sync schema"

| Source | Meaning |
|--------|---------|
| SRC-03 (Baseline) | "Offline sync schema" — functional requirement (P0) |
| SRC-15 (Data Model) | 4 specific tables with full DDL |
| SRC-06 (Gap Register) | "Sync Metadata Schema: 4 tables with RLS" (GAP-002, P0) |

**Analysis:** The baseline says "schema" ambiguously (could mean API schema or database schema). The data model defines 4 specific tables. The gap register clarifies it's database schema.

**Resolution:** Reclassify as G7-REQ-DATA-001 (database schema). The baseline's "Offline sync schema" was a data requirement misclassified as functional.

---

### CONFLICT-003: G7-MOB-SYNC-001 — "Client-side sync engine"

| Source | Meaning |
|--------|---------|
| SRC-03 (Baseline) | "Client-side sync engine with queue" (P0) |
| SRC-10 (Sync Contract) | 27 detailed behavioral requirements (mutation envelope, state machine, pull/push, retry, etc.) |
| SRC-07 (Backlog) | Covered by WP-B (Local Persistence) — only local storage aspect |

**Analysis:** The baseline treats sync engine as one requirement. The sync contract decomposes it into 27 behavioral specifications. The backlog only maps the local storage portion.

**Resolution:** G7-REQ-SYNC-001 is the parent. SYNC-002 through SYNC-017 are child requirements that decompose it.

---

### CONFLICT-004: G7-MOB-FR-008 — "Conflict resolution policy"

| Source | Meaning |
|--------|---------|
| SRC-03 (Baseline) | "Conflict resolution policy (12 classes)" — functional (P0) |
| SRC-04 (ADR) | 10 constraints + 10 acceptance criteria (PROPOSED, not approved) |
| SRC-10 (Sync Contract) | Behavioral specs for detection, isolation, merge, delete conflicts |

**Analysis:** The baseline is a high-level requirement. The ADR adds architectural constraints. The sync contract adds behavioral details. These are at different abstraction levels.

**Resolution:** Split into:
- G7-REQ-ARCH-001: ADR approval (process gate)
- G7-REQ-ARCH-002: 12 conflict classes (technical requirement)
- G7-REQ-SYNC-005: Conflict detection (behavioral)
- G7-REQ-SYNC-006: Conflict resolution (behavioral)

---

## 3. PRIORITIZATION CONFLICTS

### CONFLICT-005: G7-MOB-FR-005 — "Mobile auth flow"

| Source | Priority |
|--------|----------|
| SRC-03 (Baseline) | P0 |
| SRC-15 (API Contract) — API-03 Sync Status | P1 |

**Analysis:** The baseline classifies mobile auth as P0. The API contract classifies the sync status API as P1. These are different requirements but were conflated.

**Resolution:** G7-REQ-AUTH-001 (auth flow) = P0. G7-REQ-API-005 (sync status) = P1. No conflict after normalization.

---

### CONFLICT-006: G7-MOB-SYNC-004 — "Cursor invalidation"

| Source | Priority |
|--------|----------|
| SRC-03 (Baseline) | P0 (SYNC-004 mapped as P0 in some references) |
| SRC-15 (API Contract) — implied | P1 |

**Analysis:** The baseline lists SYNC-004 as P0 in the master truth report but P1 in the baseline document itself.

**Resolution:** P1 (CRITICAL). Cursor invalidation is important but not a blocker — the initial sync works without it.

---

### CONFLICT-007: G7-MOB-SEC-005 — "Tenant isolation on sync"

| Source | Priority |
|--------|----------|
| SRC-03 (Baseline) | P1 |
| SRC-15 (Baseline from agent) | P0 BLOCKER |

**Analysis:** One source says P1, another says P0. Tenant isolation is foundational and should be P0.

**Resolution:** P0 (BLOCKER). Tenant isolation is non-negotiable for multi-tenant SaaS.

---

### CONFLICT-008: G7-MOB-SYNC-001 — "Bidirectional sync"

| Source | Meaning |
|--------|---------|
| SRC-03 (Baseline) | "Client-side sync engine with queue" |
| SRC-15 (Baseline from agent) | "Bidirectional sync support" |

**Analysis:** The baseline says "sync engine with queue" (implies bidirectional). Another source explicitly says "bidirectional." These are the same requirement with different wording.

**Resolution:** G7-REQ-SYNC-001 = "Client-side sync engine manages bidirectional data flow." Both wordings merged.

---

## 4. SCOPE CONFLICTS

### CONFLICT-009: G7-MOB-DATA-003 — "Client-side local storage schema"

| Source | Scope |
|--------|-------|
| SRC-03 (Baseline) | "Client-side local storage schema" (P1) |
| SRC-10 (Sync Contract) | Full schema with pending_mutations table, local_version tracking |
| SRC-07 (Backlog) | WP-B covers "Local Persistence" (SQLite/IndexedDB) |

**Analysis:** The baseline is vague ("schema"). The sync contract defines specific tables and fields. The backlog scopes to client-side only.

**Resolution:** G7-REQ-DATA-003 scope = client-side schema as defined in SYNC-CONTRACT sections 3-4.

---

### CONFLICT-010: G7-MOB-FR-006 — "Offline entity subset"

| Source | Scope |
|--------|-------|
| SRC-03 (Baseline) | "Offline entity subset definition" (P1) |
| SRC-06 (Gap Register) | "Offline Entity Subset Definition: complete entity offline requirements" (P1) |
| SRC-10 (Sync Contract) | Section 22: 7 entity types all support CRUD |

**Analysis:** The baseline and gap register say "define subset." The sync contract says ALL 7 entities are eligible. Is there a subset or not?

**Resolution:** G7-REQ-OFF-001 scope = define per-entity eligibility rules. The sync contract defines the default (all 7 eligible), but eligibility rules may restrict some entities to read-only or pull-only.

---

## 5. EXISTENCE CONFLICTS

### CONFLICT-011: "101 Requirements" vs "39 Requirements"

| Source | Count |
|--------|-------|
| Mission specification | "101 Requirements / 33 P0" (mentioned as prior claim) |
| SRC-03 (Baseline) | "39 Requirements / 12 P0" |
| This reconciliation | 69 normalized requirements / 20 P0 |

**Analysis:** The "101" number has no verifiable source in any document. It may be an artifact from counting raw items across all sources before deduplication (300 raw items ÷ 3 ≈ 100). The "39" is the prior baseline count. The "69" is the normalized count after deduplication.

**Resolution:** The TRUE count is 69 normalized requirements. The "101" is an artifact of pre-deduplication counting. The "39" was a reasonable approximation but missed detailed requirements from the sync contract and security gate.

---

### CONFLICT-012: P0 Count Discrepancy

| Source | P0 Count |
|--------|----------|
| SRC-03 (Baseline) | Claims 12 but enumerates 13 |
| This reconciliation | 20 P0 |

**Analysis:** The baseline has a counting error (13 items marked P0 but summary says 12). The reconciliation finds 20 P0 after including security, architecture, and isolation requirements.

**Resolution:** TRUE P0 count = 20. The baseline undercounted by treating some P0 items as P1.

---

## 6. AGGREGATION CONFLICTS

### CONFLICT-013: "9 APIs" vs Individual API Requirements

| Source | Treatment |
|--------|-----------|
| SRC-06 (Gap) | "9 mobile APIs" — aggregate |
| SRC-15 (API Contract) | 9 individual API specifications |
| SRC-03 (Baseline) | APIs spread across FR-001, FR-003, FR-004 |

**Analysis:** The gap register counts 9 APIs as one gap. The API contract defines 9 individual endpoints. The baseline doesn't enumerate all 9.

**Resolution:** 9 individual API requirements (G7-REQ-API-001 through API-009). The "9 APIs" is an aggregate reference, not a separate requirement.

---

### CONFLICT-014: "26 Tests" vs Individual Test Requirements

| Source | Treatment |
|--------|-----------|
| SRC-06 (Gap) | "26 tests covering all scenarios" — aggregate |
| SRC-03 (Baseline) | 6 test requirements (TEST-001 through TEST-006) |
| SRC-09 (DoD) | "All 26 tests implemented" |

**Analysis:** The gap and DoD reference 26 tests. The baseline defines only 6 test requirements. The 26 likely refers to individual test cases within those 6 categories.

**Resolution:** 7 normalized test requirements (TEST-001 through TEST-007). The "26" is the expected number of individual test cases, not requirements.

---

## 7. CONFLICT RESOLUTION SUMMARY

| Conflict ID | Type | Resolution |
|-------------|------|------------|
| CONFLICT-001 | Definitional | Split into API-001 + API-002 |
| CONFLICT-002 | Definitional | Reclassify as DATA-001 |
| CONFLICT-003 | Definitional | Decompose into SYNC-001 parent + children |
| CONFLICT-004 | Definitional | Split into ARCH-001/002 + SYNC-005/006 |
| CONFLICT-005 | Prioritization | Resolved: AUTH-001=P0, API-005=P1 |
| CONFLICT-006 | Prioritization | Resolved: SYNC-004=P1 |
| CONFLICT-007 | Prioritization | Resolved: SEC-005=P0 |
| CONFLICT-008 | Prioritization | Resolved: merged wording |
| CONFLICT-009 | Scope | Resolved: scope from sync contract |
| CONFLICT-010 | Scope | Resolved: per-entity eligibility rules |
| CONFLICT-011 | Existence | Resolved: TRUE count = 69 |
| CONFLICT-012 | Existence | Resolved: TRUE P0 = 20 |
| CONFLICT-013 | Aggregation | Resolved: 9 individual APIs |
| CONFLICT-014 | Aggregation | Resolved: 7 test requirements |

**All 14 conflicts RESOLVED.**

---

*Generated: 2026-08-12*
*Phase 8 of G7 Requirements Reconciliation*
