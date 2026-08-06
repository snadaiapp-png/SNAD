# EXECUTION MODEL MAPPING

> **Document ID:** `EXECUTION-MODEL-MAPPING-V1`
> **Repository HEAD:** `91c6c2ea0954df4a0f8f0866a642f6fb0d7c809b`
> **Date:** 2026-08-06

## 1. Executive Summary

The SNAD repository contains TWO distinct execution models:

1. **GOVERNANCE Model** (`CRM-ENTERPRISE-EXECUTION-ROADMAP.md`): G0–G8
2. **PRODUCT Model** (`crm-execution-data.ts`): G0–G10

These models are NOT conflicting. They serve different purposes and have
different scopes. This document maps them explicitly.

## 2. Model Definitions

### 2.1 GOVERNANCE Model (Roadmap)

- **Authority:** `docs/crm/CRM-ENTERPRISE-EXECUTION-ROADMAP.md`
- **Scope:** Current enterprise execution phase
- **Phases:** G0–G8 (9 milestones)
- **Status vocabulary:** DONE, IN_PROGRESS, NOT_STARTED, BLOCKED
- **Purpose:** Track execution of 34 prompts toward commercial launch

### 2.2 PRODUCT Model (Execution Board)

- **Authority:** `apps/web/app/crm/crm-execution-data.ts`
- **Scope:** Full product lifecycle (current + future)
- **Phases:** G0–G10 (11 groups)
- **Status vocabulary:** APPROVED, IN_PROGRESS, NOT_STARTED, BLOCKED, DONE
- **Purpose:** Visual execution tracking in the CRM Command Center UI

## 3. Phase Mapping

### 3.1 Overlapping Phases (G0–G6)

The first 7 phases overlap between both models. The Roadmap G0–G6 represent
the same work as Execution Board G0–G6.

| Roadmap Phase | Roadmap Title | Execution Board Phase | Execution Board Title | Status Alignment |
|---|---|---|---|---|
| G0 | Execution control, CRM Command Center shell | G0 | Execution Control & CRM Dashboard | Roadmap: DONE → Board: APPROVED |
| G1 | Database, multi-tenant foundation | G1 | Database & Multi-Tenant Foundation | Roadmap: IN_PROGRESS → Board: APPROVED |
| G2 | i18n, RTL/LTR, accessibility | G2 | i18n, RTL/LTR & UI Shell | Roadmap: DONE → Board: APPROVED |
| G3 | Core CRM entities | G3 | Core CRM Entities | Roadmap: DONE → Board: APPROVED |
| G4 | Opportunities, pipeline, Kanban | G4 | Opportunities & Pipeline | Roadmap: DONE → Board: APPROVED |
| G5 | Tasks, transfers, employees | G5 | Tasks, Transfers & Employees | Roadmap: DONE → Board: APPROVED |
| G6 | Reports, analytics, export | G6 | Reports & Analytics | Roadmap: DONE → Board: APPROVED |

### 3.2 Non-Overlapping Phases

The remaining phases have DIFFERENT scopes:

| Roadmap Phase | Roadmap Title | Execution Board Phase | Execution Board Title | Relationship |
|---|---|---|---|---|
| G7 | CI/CD hardening, smoke gating | G7 | Mobile Offline Foundation | DIFFERENT SCOPE |
| G8 | Quality, security, commercial GO | G8 | Caller Identification | DIFFERENT SCOPE |
| — | (not in roadmap) | G9 | AI CRM Free & Paid Billing | FUTURE SCOPE |
| — | (not in roadmap) | G10 | QA, Security & Acceptance | FUTURE SCOPE |

### 3.3 Status Vocabulary Mapping

| Roadmap Status | Execution Board Status | Meaning |
|---|---|---|
| DONE | APPROVED | Phase complete and verified |
| IN_PROGRESS | IN_PROGRESS | Phase actively being worked |
| NOT_STARTED | NOT_STARTED | Phase not yet started |
| BLOCKED | BLOCKED | Phase blocked by dependency |
| — | DONE | Individual task complete |

## 4. Canonical Model Decision

**Both models are correct.** They serve different purposes:

- The **Roadmap** is the GOVERNANCE authority for the current execution phase.
  It tracks 34 prompts across 9 milestones toward commercial launch.

- The **Execution Board** is the PRODUCT lifecycle authority. It tracks 11
  groups across the full product roadmap, including future phases (Mobile,
  Caller ID, AI) that are beyond the current governance scope.

The Roadmap is the SOURCE OF TRUTH for governance decisions. The Execution
Board is the SOURCE OF TRUTH for product status display.

## 5. Reconciliation Rules

1. When Roadmap says `DONE`, Execution Board should say `APPROVED` for G0–G6.
2. When Roadmap says `IN_PROGRESS`, Execution Board should say `APPROVED` or
   `IN_PROGRESS` depending on whether the phase is functionally complete.
3. Roadmap G7–G8 (CI/CD, Quality) are GOVERNANCE concerns that do NOT map
   to Execution Board G7–G10 (Mobile, Caller ID, AI, QA).
4. Execution Board G7–G10 are FUTURE product phases that will be added to
   the Roadmap when they enter the execution scope.

## 6. Evidence

| Evidence | Location | Verified |
|---|---|---|
| Roadmap G0–G8 statuses | `docs/crm/CRM-ENTERPRISE-EXECUTION-ROADMAP.md` §2 | YES |
| Execution Board G0–G10 data | `apps/web/app/crm/crm-execution-data.ts` | YES |
| Manifest G0–G8 statuses | `docs/governance/MASTER-EXECUTION-MANIFEST.md` §2 | YES |

## 7. Conclusion

The apparent conflict between the two models is a STRUCTURAL DIFFERENCE, not
a GOVERNANCE CONFLICT. Both models are correct within their respective scopes.
The mapping above provides full traceability between them.
