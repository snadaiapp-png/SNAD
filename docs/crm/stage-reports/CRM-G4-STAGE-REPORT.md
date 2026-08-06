# CRM-G4 Stage Report — Opportunities, Pipeline, and Kanban

> **Report ID:** `G4-STAGE-REPORT-V1`
> **Repository HEAD:** `91c6c2ea0954df4a0f8f0866a642f6fb0d7c809b`
> **Report date:** 2026-08-06
> **Technical implementation:** `COMPLETE`
> **Gate status:** `CLOSED`

## 1. Objective

Deliver opportunities management, pipeline Kanban board, and defense-in-depth
row-level security for the CRM Command Center.

## 2. Scope

| Prompt | Description | Status |
|---|---|---|
| EXEC-PROMPT-CRM-018 | Add row-level security as defense-in-depth | DONE |
| EXEC-PROMPT-CRM-019 | Wire opportunities tab | DONE |
| EXEC-PROMPT-CRM-020 | Wire pipeline Kanban board | DONE |

## 3. Repository Evidence

| Evidence | Location | Verified |
|---|---|---|
| G4 closure report | `docs/crm/stage-reports/CRM-G4-CLOSURE-REPORT.md` | YES |
| G4 audit summary | `docs/crm/stage-reports/CRM-G4-AUDIT-SUMMARY.md` | YES |
| G4 completion certificate | `docs/crm/stage-reports/CRM-G4-COMPLETION-CERTIFICATE.md` | YES |
| G4 security certificate | `docs/crm/stage-reports/CRM-G4-SECURITY-CERTIFICATE.md` | YES |

## 4. Deliverables

| Deliverable | Status | Evidence |
|---|---|---|
| Row-level security on 62 CRM tables | DONE | `V20260730_1__enable_crm_row_level_security.sql` |
| Cross-tenant denial test (9 scenarios) | DONE | `CrmRlsTenantIsolationPostgresTest` |
| Tenant context propagation | DONE | `TenantRlsDataSource` + `TenantRlsConnectionHandler` |
| Opportunities tab with CRUD | DONE | `opportunities-tab.tsx` + `OpportunitiesCreateForm` |
| Stage transition with win/loss | DONE | `MoveStageDialog` with reason parameter |
| Pipeline Kanban board | DONE | `CrmPipelineBoard` with drag-and-drop |

## 5. Entry Criteria

- [x] G3 complete (leads, customers, contacts wired)
- [x] Customer-360 view functional (prompt 017)

## 6. Exit Criteria

- [x] All 62 CRM tables have RLS policies
- [x] Cross-tenant writes denied at PostgreSQL level
- [x] Opportunities tab lists, creates, and filters opportunities
- [x] Pipeline Kanban board supports drag-and-drop stage transitions
- [x] Win/loss reasons captured on opportunity closure

## 7. Acceptance Criteria

- [x] RLS policy on every CRM table (62 tables)
- [x] Testcontainers test proves cross-tenant denial
- [x] Application sets `app.tenant_id` on every connection
- [x] Opportunities list with stage, value, and probability
- [x] Create form calls `crmApi.createOpportunity()`
- [x] Kanban board shows value totals per stage
- [x] i18n support for Arabic and English

## 8. Risks

| Risk | Mitigation |
|---|---|
| RLS performance impact | Indexes on tenant_id ensure query performance |

## 9. Final Gate

**CLOSED** — All 3 prompts complete. Security certificate issued.
Repository evidence verified at HEAD `91c6c2ea`.

## 10. Repository HEAD

```
91c6c2ea0954df4a0f8f0866a642f6fb0d7c809b
```
