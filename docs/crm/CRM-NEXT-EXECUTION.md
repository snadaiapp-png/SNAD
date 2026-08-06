# CRM Next Execution — Post CRM-012 Closure

**Date:** 2026-07-29
**Repository:** snadaiapp-png/SNAD
**Authority:** CRM-012 Closure Authority

---

## 1. Current State

CRM-012 has been officially closed. The portfolio status:

- **11 / 34 prompts DONE** (32.4%)
- **2 / 9 milestones closed** (G0, G2)
- **Critical path:** G0 → G1 → G3 → G4 → G6 → G7 → G8

---

## 2. Ready-to-Start Items

### 2.1 Critical Path Items (G3 — Core CRM Entities)

| Prompt | Title | Dependencies | Status | Priority |
|--------|-------|--------------|--------|----------|
| CRM-014 | Wire leads tab | CRM-005 ✅ | 🔴 READY | P0 (critical path) |
| CRM-015 | Wire customers tab | CRM-005 ✅ | 🔴 READY | P0 (critical path) |
| CRM-016 | Wire contacts tab | CRM-005 ✅ | 🔴 READY | P0 (critical path) |

CRM-014 was already implemented in the previous session on branch `feature/crm-014-leads-tab-wiring`.

### 2.2 Non-Critical-Path Items

| Prompt | Title | Dependencies | Status | Priority |
|--------|-------|--------------|--------|----------|
| CRM-022 | Add CRM CI job | CRM-001 ✅ | 🔴 READY | P1 |
| CRM-024 | Enforce lint failure | CRM-001 ✅ | 🔴 READY | P1 |
| CRM-029 | Reference Issue #189 | CRM-001 ✅ | 🔴 READY | P2 |

---

## 3. Recommended Next Work Items

### Option A: Continue G3 Critical Path (RECOMMENDED)

| Order | Prompt | Title | Rationale |
|-------|--------|-------|-----------|
| 1 | CRM-015 | Wire customers tab | Next on critical path, ready |
| 2 | CRM-016 | Wire contacts tab | Parallel with CRM-015 |
| 3 | CRM-017 | Wire customer-360 view | After CRM-015 + CRM-016 |

**Why this is recommended:**
- G3 is on the critical path (G0→G1→G3→G4→G6→G7→G8)
- All G3 dependencies are satisfied (CRM-005 DONE)
- CRM-015 and CRM-016 can run in parallel
- Completing G3 unblocks G4, G5, G6, and G8

### Option B: Close G1 First

| Order | Prompt | Title | Rationale |
|-------|--------|-------|-----------|
| 1 | CRM-008 | Land G1 extension tables | Code is on main, just needs roadmap update |

**Why this is lower priority:**
- CRM-008 code is already merged — this is a tracking issue
- G1 closure requires external DBA actions (production evidence)
- G3 can start independently of G1 closure

### Option C: Non-Critical-Path Items

| Order | Prompt | Title | Rationale |
|-------|--------|-------|-----------|
| 1 | CRM-022 | Add CRM CI job | Independent, ready |
| 2 | CRM-024 | Enforce lint failure | Independent, ready |

**Why this is lower priority:**
- Not on critical path
- Does not unblock downstream work
- Better to focus on G3 first

---

## 4. Execution Sequence (Critical Path)

```
CRM-014 ✅ (leads tab — implemented)
    │
    ├──▶ CRM-015 (customers tab) ──┐
    │                               ├──▶ CRM-017 (customer-360) ──▶ G4...
    └──▶ CRM-016 (contacts tab) ──┘
```

### Parallelization Opportunities

CRM-015 and CRM-016 can be executed in parallel:
- Both depend only on CRM-005 (DONE)
- Both are in G3
- No shared dependencies between them
- Both are needed before CRM-017 can start

---

## 5. Dependency Satisfaction

### 5.1 G3 Dependencies

| Prompt | Required Dependency | Status | Satisfied? |
|--------|-------------------|--------|------------|
| CRM-014 | CRM-005 | DONE ✅ | YES |
| CRM-015 | CRM-005 | DONE ✅ | YES |
| CRM-016 | CRM-005 | DONE ✅ | YES |
| CRM-017 | CRM-015, CRM-016 | NOT_STARTED | NO (waiting) |

### 5.2 G4 Dependencies (after G3)

| Prompt | Required Dependency | Status | Satisfied? |
|--------|-------------------|--------|------------|
| CRM-018 | CRM-008 | NOT_STARTED | NO |
| CRM-019 | CRM-017 | NOT_STARTED | NO |
| CRM-020 | CRM-019 | NOT_STARTED | NO |

---

## 6. Final Recommendation

**NEXT WORK ITEM: CRM-015 — Wire customers (accounts) tab**

**SECOND WORK ITEM: CRM-016 — Wire contacts tab** (parallel with CRM-015)

These items:
- Are on the critical path (G3)
- Have all dependencies satisfied (CRM-005 DONE)
- Can be executed in parallel
- Unblock CRM-017 (customer-360 view) which unblocks G4, G5, G6, G8

---

```text
NEXT-EXECUTION-SUMMARY
DATE: 2026-07-29
POST_CLOSURE: CRM-012
READY_ITEMS: 7 (CRM-014, 015, 016, 022, 024, 029, 008)
CRITICAL_PATH_READY: 3 (CRM-014, 015, 016)
RECOMMENDED_NEXT: CRM-015 (Wire customers tab)
RECOMMENDED_SECOND: CRM-016 (Wire contacts tab)
PARALLELIZATION: CRM-015 + CRM-016
BLOCKED_ITEMS: 0 (all ready items have satisfied dependencies)
```

---

**Execution Authority:** CRM-012 Closure Authority
**Date:** 2026-07-29
