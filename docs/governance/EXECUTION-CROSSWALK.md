# EXECUTION CROSSWALK

**Generated**: 2026-08-06
**Purpose**: Cross-reference all execution systems to identify overlaps, conflicts, and gaps.

---

## CROSS-REFERENCE MATRIX

| CRM-G Milestone | Next Execution Phase | MVP Backlog Epic | Sprint 0 Story | Stage Reference |
|-----------------|---------------------|------------------|----------------|-----------------|
| G0: Command Center shell | Phase 1: Unblock Production | CRM-000: Platform dependency | S0-01: Repo governance | Stage-08: Enterprise arch |
| G1: Database, multi-tenant | Phase 2: Forward migrations | CRM-001: Database foundation | S0-02: CI stability | Stage-08: ADR-001 |
| G2: i18n, RTL, a11y | Phase 3: CRM Completion | CRM-002: i18n foundation | S0-03: Auth contracts | Stage-08: ADR-002 |
| G3: Core CRM entities | Phase 3: CRM Completion | CRM-003: Leads, customers | S0-04: Core infra | Stage-15: Roadmap gov |
| G4: Opportunities, pipeline | Phase 3: CRM Completion | CRM-004: Opportunities | — | Stage-20: Pen testing |
| G5: Tasks, transfers, employees | Phase 3: CRM Completion | CRM-005: Tasks, transfers | — | Stage-26: Market exec |
| G6: Reports, analytics, export | Phase 3: CRM Completion | CRM-006: Reports, export | — | Stage-27: Partner exec |
| G7: CI/CD hardening | Phase 4: Security hardening | CRM-007: CI/CD, smoke | — | Stage-28: Subscription |
| G8: Quality, security, GO | Phase 5: Production monitoring | CRM-008: Quality, security | — | Stage-30: Closure |

---

## STATUS CROSSWALK

| System | DONE | IN_PROGRESS | NOT_STARTED | BLOCKED |
|--------|------|-------------|-------------|---------|
| CRM-G0–G8 milestones | 5 (G0,G2,G3,G4,G5) | 3 (G1,G6,G7) | 1 (G8) | 0 |
| EXEC-PROMPT 001–034 | 29 | 1 (002) | 2 (008, 034) | 0 |
| Next Execution Phases | 0 | 0 | 5 | 0 |
| MVP Backlog Epics | 0 | 0 | 9 | 0 |
| Sprint 0 Stories | 0 | 0 | 4 | 0 |
| Zero-Trust G4–G5 | 2 (G4, G5) | 0 | 0 | 0 |

---

## DEPENDENCY CROSSWALK

### Critical Path Dependencies

```
G0 (DONE) → G1 (IN_PROGRESS) → G3 (DONE) → G4 (DONE) → G6 (IN_PROGRESS) → G7 (IN_PROGRESS) → G8 (NOT_STARTED)
                   ↘                        ↗
                    G5 (DONE) ─────────────
```

### Blocking Items

| Item | Blocks | Status | Evidence |
|------|--------|--------|----------|
| CRM-008 (G1 extension tables) | G1 completion | NOT_STARTED | `docs/crm/CRM-ENTERPRISE-EXECUTION-ROADMAP.md` |
| CRM-034 (Accessibility audit) | G8 completion | NOT_STARTED | `docs/crm/CRM-ENTERPRISE-EXECUTION-ROADMAP.md` |
| CRM-002 (Refresh MVP backlog) | Backlog accuracy | IN_PROGRESS | `docs/crm/CRM-ENTERPRISE-EXECUTION-ROADMAP.md` |

---

## CONFLICT DETECTION

### Status Conflicts

| Conflict | System A | System B | Resolution |
|----------|----------|----------|------------|
| G1 status | IN_PROGRESS (Roadmap) | DONE (Stage Report exists) | Roadmap is authoritative |
| G5 status | DONE (Roadmap) | Done (Zero-Trust G5) | Different G5 definitions |

### Numbering Conflicts

| Conflict | System A | System B | Resolution |
|----------|----------|----------|------------|
| "G5" meaning | CRM-G5: Tasks/transfers | Zero-Trust G5: Orphan cleanup | Different contexts, no conflict |
| "Phase 5" meaning | Next Execution: Production Monitoring | Zero-Trust G5: Certification | Different contexts, no conflict |

### Ownership Conflicts

| Conflict | System A | System B | Resolution |
|----------|----------|----------|------------|
| CRM completion | Next Execution Phase 3 | CRM-G3–G6 | Overlapping scope, same work |

---

## GAP ANALYSIS

### Missing Execution Artifacts

| Expected | Found | Gap |
|----------|-------|-----|
| CRM-G5-STAGE-REPORT.md | Empty file | Stage report not written |
| CRM-G6-STAGE-REPORT.md | Empty file | Stage report not written |
| CRM-G7-STAGE-REPORT.md | Empty file | Stage report not written |
| CRM-G8-STAGE-REPORT.md | Does not exist | Stage report not created |
| G0-STAGE-REPORT.md | Does not exist | Stage report not created |

### Missing Execution Plans

| Expected | Found | Gap |
|----------|-------|-----|
| CRM-G5 execution plan | None | No detailed execution plan |
| CRM-G6 execution plan | `CRM-025-EXECUTION-PLAN.md`, `CRM-026-EXECUTION-PLAN.md` | Partial |
| CRM-G7 execution plan | `CRM-027-EXECUTION-PLAN.md` | Partial |
| CRM-G8 execution plan | None | No detailed execution plan |

### Incomplete Prompts

| Prompt | Title | Status | Missing |
|--------|-------|--------|---------|
| 002 | Refresh stale MVP backlog | IN_PROGRESS | Backlog not refreshed |
| 008 | Land G1 extension tables migration | NOT_STARTED | Migration not created |
| 034 | Accessibility audit | NOT_STARTED | Audit not conducted |

---

## RECOMMENDATIONS

1. **Close G1**: Execute CRM-008 (G1 extension tables migration) to close G1
2. **Write stage reports**: Create/update stage reports for G5, G6, G7, G8
3. **Execute CRM-034**: Conduct accessibility audit to unblock G8
4. **Refresh MVP backlog**: Complete CRM-002 to update backlog accuracy
5. **Align execution systems**: Reconcile Next Execution Phases with CRM-G milestones
