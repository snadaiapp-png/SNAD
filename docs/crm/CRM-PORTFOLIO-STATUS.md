# CRM Portfolio Status

**Date:** 2026-07-29 (updated post CRM-018 / G4 closure)
**Repository:** snadaiapp-png/SNAD
**Authority:** CRM-018 Security Implementation Authority

---

## 1. Executive Summary

CRM-018 (Row-Level Security) is complete and G4 is officially closed. The
portfolio now has **18 of 34 prompts DONE** (52.9%). Four milestone groups
are fully closed (G0*, G2, G3, G4). The critical path now advances to G6
(reports, analytics, export), with G5 (tasks, transfers, employees) available
in parallel.

---

## 2. Milestone Group Status

| Group | Title | Prompts | DONE | IN_PROGRESS | NOT_STARTED | Status |
|-------|-------|---------|------|-------------|-------------|--------|
| G0 | Execution control and shell | 6 | 5 | 1 | 0 | ⚠️ IN_PROGRESS |
| G1 | Database and multi-tenant foundation | 6 | 5 | 0 | 1 | ⚠️ IN_PROGRESS |
| G2 | i18n, RTL/LTR, accessibility | 1 | 1 | 0 | 0 | ✅ DONE |
| G3 | Core CRM entities end-to-end | 4 | 4 | 0 | 0 | ✅ CLOSED |
| G4 | Opportunities, pipeline, Kanban | 3 | 3 | 0 | 0 | ✅ CLOSED |
| G5 | Tasks, transfers, employees | 3 | 0 | 0 | 3 | ⏳ NOT_STARTED |
| G6 | Reports, analytics, export | 3 | 0 | 0 | 3 | ⏳ NOT_STARTED |
| G7 | CI/CD hardening, smoke gating | 5 | 0 | 0 | 5 | ⏳ NOT_STARTED |
| G8 | Quality, security, commercial GO | 3 | 0 | 0 | 3 | ⏳ NOT_STARTED |
| **Total** | | **34** | **18** | **1** | **15** | |

### Closed Milestones
- ✅ **CRM-G2** — i18n and accessibility (1/1 DONE)
- ✅ **CRM-G3** — Core CRM entities end-to-end (4/4 DONE)
- ✅ **CRM-G4** — Opportunities, pipeline, Kanban (3/3 DONE)

### Active Milestones
- ⚠️ **CRM-G0** — Execution control (5/6 DONE, CRM-002 IN_PROGRESS)
- ⚠️ **CRM-G1** — Database foundation (5/6 DONE, CRM-008 code on main)

---

## 3. Prompt Status Distribution

| Status | Count | Percentage | Prompts |
|--------|-------|------------|---------|
| ✅ DONE | 18 | 52.9% | 001, 003, 004, 005, 006, 007, 009, 010, 011, 012, 013, 014, 015, 016, 017, 018, 019, 020 |
| ⚠️ IN_PROGRESS | 1 | 2.9% | 002 |
| 🔴 NOT_STARTED | 15 | 44.1% | 008, 021–034 |
| ⏳ BLOCKED | 0 | 0% | — |

---

## 4. G4 Closure Summary

| Prompt | Title | Status | Evidence |
|--------|-------|--------|----------|
| CRM-018 | Add row-level security | ✅ DONE | `docs/crm/crm-018/` (7 documents) |
| CRM-019 | Wire opportunities tab | ✅ DONE | `docs/crm/crm-019/` (4 documents) |
| CRM-020 | Wire pipeline Kanban board | ✅ DONE | `docs/crm/crm-020/` (4 documents) |

**G4 Status:** ✅ **CLOSED** (3/3 prompts DONE, closure report generated)

---

## 5. Ready-to-Start Items

| Prompt | Title | Group | Dependencies | Status |
|--------|-------|-------|--------------|--------|
| CRM-022 | Add CRM CI job | G5 | CRM-001 (DONE) | 🔴 READY |
| CRM-024 | Enforce lint failure | G6 | CRM-001 (DONE) | 🔴 READY |
| CRM-025 | Reports dashboard | G6 | CRM-019 (DONE), CRM-021 | ⏳ Needs CRM-021 |
| CRM-029 | Reference Issue #189 | G7 | CRM-001 (DONE) | 🔴 READY |

---

## 6. Next Executable Work Items (Critical Path)

| Order | Prompt | Title | Group | Rationale |
|-------|--------|-------|-------|-----------|
| 1 | CRM-021 | Wire tasks tab | G5 | Unblocks CRM-023/025/026; depends on CRM-008 (code on main) |
| 2 | CRM-022 | Add CRM CI job | G5 | Ready now; unblocks CRM-027 |
| 3 | CRM-025 | Reports dashboard | G6 | Critical path; needs CRM-021 |

---

## 7. Progress Metrics

| Metric | Value |
|--------|-------|
| Total prompts | 34 |
| DONE | 18 (52.9%) |
| IN_PROGRESS | 1 (2.9%) |
| NOT_STARTED | 15 (44.1%) |
| Closed milestones | 3 / 9 (33.3%) + G0/G1 nearly complete |
| Critical path items DONE | 8 / 12 (66.7%) |

---

**Portfolio Authority:** CRM-018 Security Implementation Authority
**Date:** 2026-07-29
