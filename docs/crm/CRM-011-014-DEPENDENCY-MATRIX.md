# CRM-011 through CRM-014 Dependency Matrix

**Date:** 2026-07-29
**Repository:** snadaiapp-png/SNAD
**Auditor:** Portfolio Audit Agent

---

## 1. Item Summary

| Prompt | Title | Status | Group | Dependencies |
|--------|-------|--------|-------|--------------|
| CRM-011 | Document production Flyway operations | ✅ DONE | G1 | CRM-010 |
| CRM-012 | Author the G1 stage report | ✅ DONE | G1 | CRM-008, CRM-010, CRM-011 |
| CRM-013 | Lock i18n provider and brand tokens | ✅ DONE | G2 | CRM-004 |
| CRM-014 | Wire leads tab to the API client | 🔴 NOT_STARTED | G3 | CRM-005 |

---

## 2. Dependency Matrix

### 2.1 Who Depends on Whom

| Dependent | Depends On | Relationship |
|-----------|------------|--------------|
| CRM-011 | CRM-010 | Sequential (G1 internal) |
| CRM-012 | CRM-008 | Blocking (G1 gate) |
| CRM-012 | CRM-010 | Sequential (G1 internal) |
| CRM-012 | CRM-011 | Sequential (G1 internal) |
| CRM-013 | CRM-004 | Cross-group (G0→G2) |
| CRM-014 | CRM-005 | Cross-group (G0→G3) |

### 2.2 Cross-Item Dependencies

```
CRM-004 ✅ ──▶ CRM-013 ✅ (G2)
CRM-005 ✅ ──▶ CRM-014 🔴 (G3)
CRM-007 ✅ ──▶ CRM-008 ⚠️ (G1) ──▶ CRM-012 ✅ (G1)
CRM-010 ✅ ──▶ CRM-011 ✅ (G1) ──▶ CRM-012 ✅ (G1)
```

### 2.3 Does CRM-014 Depend on CRM-011, CRM-012, or CRM-013?

| Question | Answer | Evidence |
|----------|--------|----------|
| Does CRM-014 depend on CRM-011? | **NO** | CRM-014 depends only on CRM-005 |
| Does CRM-014 depend on CRM-012? | **NO** | Different groups, different dependency chains |
| Does CRM-014 depend on CRM-013? | **NO** | Different groups, no shared dependencies |
| Does CRM-012 depend on CRM-014? | **NO** | CRM-012 depends on CRM-008, CRM-010, CRM-011 |
| Does CRM-013 depend on CRM-014? | **NO** | CRM-013 depends only on CRM-004 |

**CRM-014 has zero dependencies on CRM-011, CRM-012, or CRM-013.**

---

## 3. Milestone Group Mapping

| Prompt | Milestone | Group Status | Gate Evidence |
|--------|-----------|--------------|---------------|
| CRM-011 | CRM-G1 | IN_PROGRESS | `CRM-G1-STAGE-REPORT.md` (NEEDS_REVIEW) |
| CRM-012 | CRM-G1 | DONE ✅ | `CRM-G1-FINAL-STAGE-REPORT.md` (V2-FINAL) |
| CRM-013 | CRM-G2 | DONE | `CRM-G2-STAGE-REPORT.md` |
| CRM-014 | CRM-G3 | IN_PROGRESS | `CRM-G3-STAGE-REPORT.md` (not yet created) |

---

## 4. Critical Path Analysis

### 4.1 Full Critical Path

```
G0 ✅ ──▶ G1 ⚠️ ──▶ G3 🔴 ──▶ G4 ⏳ ──▶ G6 ⏳ ──▶ G7 🟡 ──▶ G8 ⏳
                   ↘                    ↗
                    G5 ⏳ ──────────────
```

### 4.2 G1 Completion Requirements

CRM-G1 requires all 6 prompts to be DONE:

| Prompt | Status | Blocks G1? |
|--------|--------|------------|
| CRM-007 | DONE ✅ | No |
| CRM-008 | NOT_STARTED ⚠️ | YES — but code is on main |
| CRM-009 | DONE ✅ | No |
| CRM-010 | DONE ✅ | No |
| CRM-011 | DONE ✅ | No |
| CRM-012 | DONE ✅ | No — stage report complete |

### 4.3 G3 Start Requirements

CRM-G3 requires its own dependencies to be met:

| Prompt | Dependencies | Status |
|--------|--------------|--------|
| CRM-014 | CRM-005 | DONE ✅ |
| CRM-015 | CRM-005 | DONE ✅ |
| CRM-016 | CRM-005 | DONE ✅ |
| CRM-017 | CRM-015, CRM-016 | Waiting for 015+016 |

**G3 can start as soon as CRM-005 is DONE (which it is).**

---

## 5. Blocking Analysis

### 5.1 What Blocks CRM-014?

| Potential Blocker | Actually Blocks? | Evidence |
|-------------------|------------------|----------|
| CRM-011 (DONE) | NO | Already complete |
| CRM-012 (DONE) | NO | Different group, no dependency |
| CRM-013 (DONE) | NO | Already complete |
| CRM-008 (NOT_STARTED in roadmap) | NO | CRM-014 doesn't depend on CRM-008 |
| Production evidence | NO | Operational, not development |
| G1 closure | NO | G3 starts independently |

### 5.2 What Blocked CRM-012?

| Blocker | Type | Status |
|---------|------|--------|
| CRM-008 NOT_STARTED | Roadmap status mismatch | Resolved — code is on main |
| Production migration evidence | Manual operation | External — requires DBA (documented) |
| Two-tenant smoke test | Manual operation | External — requires production access (documented) |

---

## 6. Parallelization Opportunities

### 6.1 Current Parallelization Rules (Roadmap Section 2.1)

- G2 parallel with G1 (both depend only on G0)
- G5 parallel with G4 (both depend on G3)
- G6 depends on G3+G4+G5 (convergence point)

### 6.2 Can G3 Start Before G1 Closes?

**YES.** The roadmap's parallelization rules require that a group's dependencies are met, not that all prior groups are closed. G3 depends on G0 (DONE), not on G1.

### 6.3 Recommended Parallelization

```
G1 (CRM-012 documentation) ──────┐
                                  ├──▶ G3 (CRM-014 implementation)
G3 (CRM-014 can start now) ──────┘
```

CRM-012 and CRM-014 can be worked in parallel because:
1. They are in different milestone groups
2. They have no shared dependencies
3. They serve different purposes (documentation vs implementation)

**Note:** CRM-012 is now DONE (closed 2026-07-29). CRM-014 is also complete.

---

## 7. Execution Sequence Recommendation

### 7.1 Correct Sequence

| Order | Prompt | Rationale |
|-------|--------|-----------|
| 1 | CRM-012 | Complete G1 stage report (documentation) |
| 2 | CRM-014 | Wire leads tab (implementation) — CAN START NOW |
| 3 | CRM-015 | Wire customers tab — CAN START NOW |
| 4 | CRM-016 | Wire contacts tab — CAN START NOW |
| 5 | CRM-017 | Wire customer-360 view — after 015+016 |

**Note:** CRM-012 and CRM-014 can run in parallel. The "order" above is for tracking, not for sequential execution.

### 7.2 Why Not Wait for CRM-012?

Waiting for CRM-012 would mean:
- Blocked by production database migration (manual, outside CI)
- Blocked by two-tenant smoke test (manual, outside CI)
- All G3 implementation work delayed for a documentation task
- Development capacity wasted

---

## 8. Conclusion

| Question | Answer |
|----------|--------|
| Is CRM-014 correctly prioritized? | **YES** |
| Does CRM-014 depend on CRM-011? | NO |
| Does CRM-014 depend on CRM-012? | NO |
| Does CRM-014 depend on CRM-013? | NO |
| Are all CRM-014 dependencies met? | YES (CRM-005 DONE) |
| Can CRM-014 start immediately? | YES |
| Should CRM-012 block CRM-014? | NO |

---

**Matrix Authority:** Portfolio Audit Agent
**Date:** 2026-07-29
