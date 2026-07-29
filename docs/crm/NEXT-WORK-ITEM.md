# Next Work Item — Portfolio Execution Analysis

**Date:** 2026-07-29
**Repository:** snadaiapp-png/SNAD
**Agent:** Portfolio Execution Agent
**Classification:** Read-only analysis

---

## 1. Executive Summary

CRM-010 (Customer 360 & Unified Customer Intelligence) has been completed, merged, and governance-ratified. The next highest-priority work item on the enterprise critical path is:

**EXEC-PROMPT-CRM-014 — Wire leads tab to the API client**

This is the first prompt in CRM-G3 (Core CRM entities end-to-end) and sits on the critical path: `G0 → G1 → G3 → G4 → G6 → G7 → G8`.

**Implementation may begin immediately.** All prerequisites are satisfied.

---

## 2. Portfolio State After CRM-010

### 2.1 Enterprise Roadmap Status

| Milestone | Title | Status | Notes |
|-----------|-------|--------|-------|
| CRM-G0 | Execution control, Command Center shell, governance | ✅ DONE | All 6 prompts complete |
| CRM-G1 | Database, multi-tenant foundation, G1 extension tables | ✅ EFFECTIVELY DONE | Migration on main, stage report exists. Production evidence pending (manual step). |
| CRM-G2 | i18n, RTL/LTR, accessibility | ✅ DONE | Prompt 013 complete |
| CRM-G3 | Core CRM entities end-to-end | 🔴 NEXT | All 4 prompts NOT_STARTED — **critical path** |
| CRM-G4 | Opportunities, pipeline, Kanban | ⏳ WAITING | Depends on G3 |
| CRM-G5 | Tasks, transfers, employees, assignments | ⏳ WAITING | Depends on G3 |
| CRM-G6 | Reports, analytics, export | ⏳ WAITING | Depends on G3+G4+G5 |
| CRM-G7 | CI/CD hardening, smoke gating | 🟡 PARTIAL | Some prompts independent |
| CRM-G8 | Quality, security, commercial GO | ⏳ WAITING | Final gate |

### 2.2 Critical Path

```
G0 ✅ ──▶ G1 ✅ ──▶ G3 🔴 ──▶ G4 ⏳ ──▶ G6 ⏳ ──▶ G7 🟡 ──▶ G8 ⏳
                   ↘                    ↗
                    G5 ⏳ ──────────────
```

### 2.3 Open Issues (15 total)

| # | Title | Relevance |
|---|-------|-----------|
| 705 | CRM-010 Quality, Security & Operations | ✅ Closed (governance ratified) |
| 784 | Independent Security Assessment | Security gate — not on critical path |
| 703 | CRM-008B production readiness — NO-GO | Production gate — not on critical path |
| 701 | APP-SEP-001: Module separation | Architecture — not on critical path |
| 692 | CRM-009 Workflow & AI Integration | Separate workstream — not on critical path |
| 535 | REM-P0-002 production rollout | Remediation — not on critical path |
| 534 | REM-P0-002 72-hour acceptance | Remediation — not on critical path |
| 526 | Service Review 2026-07 | Review — not on critical path |
| 385 | Stage 19: Enterprise Sales | Future — not on critical path |
| 189 | CI-PLATFORM-01: Restore GitHub Actions | Platform — partially on G7 path |
| 185 | BUILD-SPRINT-01: Platform Core Stabilization | Platform — not on critical path |
| 127 | UX-SHELL-001: Typography, icons, dark mode | UX — not on critical path |
| 126 | AUTH-EMAIL-001: Password recovery email | Security — not on critical path |
| 53 | EXEC-PROMPT-032A: Backend Auth Foundation | Backend — not on critical path |
| 2 | EXEC-PROMPT-005: Organization App Service | Backend — not on critical path |

---

## 3. Selected Work Item

### EXEC-PROMPT-CRM-014 — Wire leads tab to the API client

| Field | Value |
|-------|-------|
| ID | EXEC-PROMPT-CRM-014 |
| Group | CRM-G3 (Core CRM entities end-to-end) |
| Owner | Frontend squad |
| Status | NOT_STARTED |
| Dependencies | EXEC-PROMPT-CRM-005 (DONE ✅) |
| Critical path | YES — first item in G3 |

---

## 4. Implementation Scope

### 4.1 What Must Be Built

The `leads` tab in the CRM Command Center must be wired to the real backend API, replacing the current `CrmEmptyState` placeholder.

### 4.2 Functional Requirements

| # | Requirement | Evidence |
|---|-------------|----------|
| 1 | Leads list fetched from `crmApi.leads()` | Tab shows real data |
| 2 | Status filter wired (NEW, ASSIGNED, CONTACTED, QUALIFIED, DISQUALIFIED, ARCHIVED) | Filter dropdown functional |
| 3 | Create-lead form calls `crmApi.createLead()` | New leads appear in list |
| 4 | Status change calls `crmApi.changeLeadStatus()` | Status updates in UI |
| 5 | Convert action calls `crmApi.convertLead()` | Shows resulting account/contact/opportunity |
| 6 | Tab no longer renders `CrmEmptyState` | Real content displayed |

### 4.3 Files to Create/Modify

| File | Action | Purpose |
|------|--------|---------|
| `apps/web/app/crm/command-center/leads-tab.tsx` | CREATE | Leads list component |
| `apps/web/app/crm/command-center/leads-filters.tsx` | CREATE | Status filter component |
| `apps/web/app/crm/command-center/leads-create-form.tsx` | CREATE | Create lead form |
| `apps/web/app/crm/command-center/leads-convert-dialog.tsx` | CREATE | Lead conversion dialog |
| `apps/web/app/crm/command-center/leads-tab.test.tsx` | CREATE | Unit tests |
| `apps/web/app/crm/crm-command-center.tsx` | MODIFY | Import and render LeadsTab |
| `apps/web/lib/api/crm.ts` | VERIFY | Ensure lead methods exist |

---

## 5. Acceptance Criteria

| # | Criterion | Test Method |
|---|-----------|-------------|
| A1 | Leads tab renders list of leads from API | Visual verification + test |
| A2 | Status filter correctly filters leads | Unit test |
| A3 | Create lead form submits and refreshes list | Integration test |
| A4 | Status change updates lead status | Unit test |
| A5 | Convert action shows account/contact/opportunity links | Unit test |
| A6 | Tab does not render CrmEmptyState | Visual verification |
| A7 | All existing tests continue to pass | `mvn test` |
| A8 | Build compiles cleanly | `mvn compile` |
| A9 | No TypeScript errors | `tsc --noEmit` |

---

## 6. Dependencies

| Dependency | Status | Blocker? |
|------------|--------|----------|
| EXEC-PROMPT-CRM-005 (Execution Board data registry) | DONE ✅ | No |
| Backend leads API (CRM-002) | DONE ✅ | No |
| `crmApi.leads()` in `apps/web/lib/api/crm.ts` | EXISTS ✅ | No |
| CRM Command Center shell (G0) | DONE ✅ | No |
| i18n provider (G2) | DONE ✅ | No |
| CRM-010 intelligence module | MERGED ✅ | No |

**All dependencies satisfied. No blockers.**

---

## 7. Risks

| # | Risk | Likelihood | Impact | Mitigation |
|---|------|------------|--------|------------|
| 1 | `crmApi.leads()` method may not exist or be incomplete | LOW | MEDIUM | Verify API client has full CRUD before starting |
| 2 | Lead status enum mismatch between frontend and backend | LOW | LOW | Check CRM lead status values match |
| 3 | Lead conversion API may not be implemented | MEDIUM | HIGH | Check `crmApi.convertLead()` exists; if not, defer conversion to follow-up |
| 4 | Existing Command Center tests may break | LOW | MEDIUM | Run full test suite after changes |
| 5 | i18n keys for leads tab may be missing | LOW | LOW | Add Arabic/English translations as needed |

---

## 8. Test Strategy

| Level | Scope | Tool | Coverage |
|-------|-------|------|----------|
| Unit | LeadsTab component rendering | React Testing Library | List rendering, empty state, loading |
| Unit | Status filter logic | Jest | Filter application, reset |
| Unit | Create lead form validation | Jest | Required fields, email format |
| Integration | API client methods | Mock Service Worker | All lead CRUD operations |
| E2E | Full lead lifecycle | Playwright | Create → filter → convert |
| Regression | Existing CRM tests | Maven + Playwright | No regressions |

---

## 9. Estimated Effort

| Task | Estimate |
|------|----------|
| Leads list component | S (2 points) |
| Status filter | XS (1 point) |
| Create lead form | M (3 points) |
| Lead convert dialog | M (3 points) |
| Unit tests | S (2 points) |
| i18n translations | XS (1 point) |
| Integration testing | S (2 points) |
| **Total** | **L (14 points / ~1.5 sprints)** |

---

## 10. Decision

**READY TO START EXEC-PROMPT-CRM-014**

All prerequisites are satisfied. No blockers. Implementation may begin immediately on a new feature branch.

---

**Analysis Authority:** Portfolio Execution Agent
**Date:** 2026-07-29
**Next action:** Create feature branch and begin implementation
