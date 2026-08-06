# MASTER EXECUTION MANIFEST

**Generated**: 2026-08-06
**Repository HEAD**: 91c6c2ea0954df4a0f8f0866a642f6fb0d7c809b
**Authority**: `docs/crm/CRM-ENTERPRISE-EXECUTION-ROADMAP.md`

---

## EXECUTION SYSTEMS IDENTIFIED

| # | System | Scope | Authority File |
|---|--------|-------|----------------|
| 1 | CRM-G0–G8 | CRM product milestones | `docs/crm/CRM-ENTERPRISE-EXECUTION-ROADMAP.md` |
| 2 | EXEC-PROMPT-CRM-001–034 | CRM execution prompts | `docs/crm/CRM-ENTERPRISE-EXECUTION-ROADMAP.md` |
| 3 | CRM MVP Backlog | CRM feature backlog | `docs/crm/CRM-MVP-EXECUTION-BACKLOG.md` |
| 4 | Next Execution Plan | Platform phases | `docs/next-execution-plan.md` |
| 5 | Sprint 0 | Development baseline | `docs/execution/SPRINT-0-PROPOSED-BACKLOG.md` |
| 6 | Stage-08 | Enterprise architecture | `docs/stage-08/` |
| 7 | Stage-11 | Backlog classification | `docs/stage-11/` |
| 8 | Stage-15 | Product roadmap governance | `docs/stage-15/` |
| 9 | Stage-16 | Closure | `docs/stage-16/` |
| 10 | Stage-17 | Closure | `docs/stage-17/` |
| 11 | Stage-20 | Penetration testing | `docs/stage-20/` |
| 12 | Stage-26 | Market execution | `docs/stage-26/` |
| 13 | Stage-27 | Partner execution | `docs/stage-27/` |
| 14 | Stage-28 | Subscription lifecycle | `docs/stage-28/` |
| 15 | Stage-29 | Renewal expansion | `docs/stage-29/` |
| 16 | Stage-30 | Closure | `docs/stage-30/` |
| 17 | Zero-Trust Protocol | Continuous certification | Ad-hoc (G4, G5, etc.) |

---

## CRM-G0–G8 MILESTONES

| Milestone | Title | Status | Gate Evidence | Prompts |
|-----------|-------|--------|---------------|---------|
| CRM-G0 | Execution control, CRM Command Center shell, governance baseline | DONE | `docs/crm/stage-reports/CRM-G0-STAGE-REPORT.md` | 001–006 |
| CRM-G1 | Database, multi-tenant foundation, G1 extension tables | IN_PROGRESS | `docs/crm/stage-reports/CRM-G1-STAGE-REPORT.md` | 007–012 |
| CRM-G2 | i18n, RTL/LTR, accessibility hardening | DONE | `docs/crm/stage-reports/CRM-G2-STAGE-REPORT.md` | 013 |
| CRM-G3 | Core CRM entities (leads, customers, contacts, customer-360) | DONE | `docs/crm/stage-reports/CRM-G3-STAGE-REPORT.md` | 014–017 |
| CRM-G4 | Opportunities, pipeline, Kanban | DONE | `docs/crm/stage-reports/CRM-G4-STAGE-REPORT.md` | 018–020 |
| CRM-G5 | Tasks, transfers, employees, assignments | DONE | `docs/crm/stage-reports/CRM-G5-STAGE-REPORT.md` | 021–023 |
| CRM-G6 | Reports, analytics, export | DONE | `docs/crm/stage-reports/CRM-G6-STAGE-REPORT.md` | 024–026 |
| CRM-G7 | CI/CD hardening, smoke gating, Issue #189 | DONE | `docs/crm/stage-reports/CRM-G7-STAGE-REPORT.md` | 027–031 |
| CRM-G8 | Quality, security, formal commercial GO | IN_PROGRESS | `docs/crm/stage-reports/CRM-G8-STAGE-REPORT.md` | 032–034 |

---

## EXECUTION PROMPTS (001–034)

### CRM-G0 (001–06)

| Prompt | Title | Status | Dependencies |
|--------|-------|--------|--------------|
| 001 | Reconcile baseline against main | DONE | — |
| 002 | Refresh stale MVP backlog | IN_PROGRESS | 001 |
| 003 | Author G0 stage report | DONE | 001 |
| 004 | Lock Command Center route | DONE | — |
| 005 | Lock Execution Board data registry | DONE | 004 |
| 006 | Establish governance drift check | DONE | 001 |

### CRM-G1 (007–012)

| Prompt | Title | Status | Dependencies |
|--------|-------|--------|--------------|
| 007 | Apply unified CRM core migration | DONE | — |
| 008 | Land G1 extension tables migration | NOT_STARTED | 007 |
| 009 | Reconcile ADMIN role and capabilities | DONE | 007 |
| 010 | Complete imports and custom-field persistence | DONE | 009 |
| 011 | Document production Flyway operations | DONE | 010 |
| 012 | Author G1 stage report | DONE | 008, 010, 011 |

### CRM-G2 (013)

| Prompt | Title | Status | Dependencies |
|--------|-------|--------|--------------|
| 013 | Lock i18n provider and brand tokens | DONE | 004 |

### CRM-G3 (014–017)

| Prompt | Title | Status | Dependencies |
|--------|-------|--------|--------------|
| 014 | Wire leads tab to API client | DONE | 005 |
| 015 | Wire customers (accounts) tab | DONE | 005 |
| 016 | Wire contacts tab and custom-fields client | DONE | 005 |
| 017 | Wire customer-360 view | DONE | 015, 016 |

### CRM-G4 (018–020)

| Prompt | Title | Status | Dependencies |
|--------|-------|--------|--------------|
| 018 | Add row-level security as defense-in-depth | DONE | 008 |
| 019 | Wire opportunities tab | DONE | 017 |
| 020 | Wire pipeline Kanban board | DONE | 019 |

### CRM-G5 (021–023)

| Prompt | Title | Status | Dependencies |
|--------|-------|--------|--------------|
| 021 | Wire tasks tab | DONE | 008, 017 |
| 022 | Add CRM-specific job to ci.yml | GOVERNANCE COMPLETE | 001 |
| 023 | Wire transfers and employees tabs | DONE | 021 |

### CRM-G6 (024–026)

| Prompt | Title | Status | Dependencies |
|--------|-------|--------|--------------|
| 024 | Hardening: enforce lint failure | DONE | 001 |
| 025 | Wire reports tab | DONE | 019, 021 |
| 026 | Add CRM E2E test | DONE | 017, 019, 021 |

### CRM-G7 (027–031)

| Prompt | Title | Status | Dependencies |
|--------|-------|--------|--------------|
| 027 | Gate crm-real-smoke.yml on production deploy | DONE | 022 |
| 028 | Add Flyway-history assertion test | DONE | 010 |
| 029 | Reference Issue #189 in workflows and docs | DONE | 001 |
| 030 | Verify CRM workflows as required status checks | DONE | 022, 027 |
| 031 | Record formal production GO decision | DONE | 027, 028, 030 |

### CRM-G8 (032–034)

| Prompt | Title | Status | Dependencies |
|--------|-------|--------|--------------|
| 032 | Penetration test closure for CRM surface | COMPLETE | 018, 026 |
| 033 | Performance baseline for CRM | DONE | 027 |
| 034 | Accessibility audit for CRM Command Center | NOT_STARTED | 017, 020 |

---

## CRITICAL PATH

```
G0 → G1 → G3 → G4 → G6 → G7 → G8
         ↘         ↗
          G5 ─────
G0 → G2 ──────────────────────────→ G7
```

---

## NEXT EXECUTION PLAN PHASES

| Phase | Title | Status |
|-------|-------|--------|
| Phase 1 | Unblock Production | IMMEDIATE |
| Phase 2 | Forward-Only Infrastructure Migrations | 1-2 SPRINTS |
| Phase 3 | CRM Completion | 2-4 SPRINTS |
| Phase 4 | Security Hardening | ONGOING |
| Phase 5 | Production Monitoring | ONGOING |

---

## ZERO-TRUST PROTOCOL PHASES

| Phase | Title | Status | HEAD |
|-------|-------|--------|------|
| G4 | Opportunities & Pipeline Certification | APPROVED | 404175f0 |
| G5 | Orphan Cleanup Certification | APPROVED | 87c77668 |

---

## SUMMARY STATISTICS

| Metric | Value |
|--------|-------|
| Total milestones | 9 (CRM-G0–G8) |
| Total prompts | 34 (001–034) |
| DONE prompts | 29 |
| IN_PROGRESS prompts | 1 (002) |
| NOT_STARTED prompts | 2 (008, 034) |
| COMPLETE prompts | 2 (022, 032) |
| Execution systems | 17 |
| Stage-based phases | 12 (stage-08 through stage-30) |
