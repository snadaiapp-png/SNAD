# CRM Enterprise Execution Roadmap

> **Branch:** `crm/001-baseline-governance-ci-recovery`
> **Baseline SHA:** `cee332e7f86a6ea64fbb5f72120ae77c441f6eac`
> **Companion document:** `docs/crm/CRM-CURRENT-BASELINE.md`
> **Document type:** AUTHORITATIVE forward execution plan.
> **Status codes:** inherited from `CRM-CURRENT-BASELINE.md` §1.

This roadmap is the authoritative sequence of execution prompts
(`EXEC-PROMPT-CRM-001` … `EXEC-PROMPT-CRM-034`) and group milestones
(`CRM-G0` … `CRM-G8`) that move the CRM product from its current
`IMPLEMENTED_AND_CONNECTED` baseline to enterprise commercial launch.

The drift-check script (`scripts/crm/governance-drift-check.sh`) treats this
document as the source of truth for "what is allowed to be claimed as done."
A claim in a README, release note, or workflow summary that contradicts the
status column below fails the check.

---

## 1. Reading This Roadmap

### 1.1 Group milestones (CRM-G0 … CRM-G8)

Each `CRM-Gn` milestone is a delivery gate. A milestone is **CLOSED** only when
every prompt listed under it has status `DONE` and the named gate evidence file
exists. Milestones are sequential unless explicitly marked as parallelizable.

### 1.2 Execution prompts (EXEC-PROMPT-CRM-001 … 034)

Each prompt is a unit of work with:

- **Owner** — the squad or role accountable for delivery.
- **Status** — one of: `DONE`, `IN_PROGRESS`, `BLOCKED`, `NOT_STARTED`,
  `DEPRECATED`, `SUPERSEDED`.
- **Dependencies** — prompt IDs that must be `DONE` before this one can start.
- **Gate** — the milestone this prompt belongs to.
- **Acceptance** — concrete, testable conditions that must be met to mark the
  prompt `DONE`.

### 1.3 Gate evidence

A milestone is closed by a stage report committed under `docs/crm/stage-reports/`
with the filename pattern `CRM-Gn-STAGE-REPORT.md`. The drift check fails any
claim of milestone closure that lacks the matching report file.

---

## 2. Milestone Overview

| Milestone | Title | Status | Gate evidence | Prompts |
|---|---|---|---|---|
| `CRM-G0` | Execution control, CRM Command Center shell, and governance baseline | `DONE` | `docs/crm/stage-reports/CRM-G0-STAGE-REPORT.md` | 001–006 |
| `CRM-G1` | Database, multi-tenant foundation, and G1 extension tables | `IN_PROGRESS` | `docs/crm/stage-reports/CRM-G1-STAGE-REPORT.md` | 007–012 |
| `CRM-G2` | i18n, RTL/LTR, and accessibility hardening | `DONE` | `docs/crm/stage-reports/CRM-G2-STAGE-REPORT.md` | 013 |
| `CRM-G3` | Core CRM entities end-to-end (leads, customers, contacts, customer-360) | `DONE` | `docs/crm/stage-reports/CRM-G3-STAGE-REPORT.md` | 014–017 |
| `CRM-G4` | Opportunities, pipeline, and Kanban | `DONE` | `docs/crm/stage-reports/CRM-G4-STAGE-REPORT.md` | 018–020 |
| `CRM-G5` | Tasks, transfers, employees, and assignments | `DONE` | `docs/crm/stage-reports/CRM-G5-STAGE-REPORT.md` | 021–023 |
| `CRM-G6` | Reports, analytics, and export | `IN_PROGRESS` | `docs/crm/stage-reports/CRM-G6-STAGE-REPORT.md` | 024–026 |
| `CRM-G7` | CI/CD hardening, smoke gating, and Issue #189 closure | `IN_PROGRESS` | `docs/crm/stage-reports/CRM-G7-STAGE-REPORT.md` | 027–031 |
| `CRM-G8` | Quality, security, and formal commercial GO | `NOT_STARTED` | `docs/crm/stage-reports/CRM-G8-STAGE-REPORT.md` | 032–034 |

### 2.1 Parallelization

The following milestones may be worked in parallel **after their dependencies
are closed**:

- `CRM-G2` parallel with `CRM-G1` (both depend only on `CRM-G0`).
- `CRM-G5` parallel with `CRM-G4` (both depend on `CRM-G3`).
- `CRM-G6` depends on `CRM-G3`, `CRM-G4`, and `CRM-G5` and is the convergence
  point — it must not start until all three are closed.

### 2.2 Critical path

```
G0 ─▶ G1 ─▶ G3 ─▶ G4 ─▶ G6 ─▶ G7 ─▶ G8
              ↘              ↗
               G5 ──────────
G0 ─▶ G2 ─────────────────────────────▶ G7
```

The critical path is `G0 → G1 → G3 → G4 → G6 → G7 → G8`. `G2` runs in parallel
with `G1` and joins back at `G7`. `G5` joins at `G6`.

---

## 3. CRM-G0 — Execution control and CRM Command Center shell

**Status:** `DONE`
**Gate evidence required:** `docs/crm/stage-reports/CRM-G0-STAGE-REPORT.md`

G0 establishes the independent CRM workspace (`/crm`), the 16-tab Command
Center, the empty-state contract, the i18n provider, the SNAD brand token
integration, and the G0–G10 Execution Board. G0 does **not** deliver any
domain feature beyond the overview and execution board tabs.

### EXEC-PROMPT-CRM-001 — Reconcile baseline against `main`

- **Owner:** CRM governance squad.
- **Status:** `DONE` (closed by this branch).
- **Dependencies:** none.
- **Acceptance:**
  - `docs/crm/CRM-CURRENT-BASELINE.md` exists and is reconciled against
    `cee332e7`.
  - `docs/crm/README.md` no longer claims `CRM_PRODUCT_BUILD: NOT STARTED`.
  - `scripts/crm/governance-drift-check.sh` passes on `main`.

### EXEC-PROMPT-CRM-002 — Refresh stale MVP backlog

- **Owner:** CRM governance squad.
- **Status:** `IN_PROGRESS`.
- **Dependencies:** `EXEC-PROMPT-CRM-001`.
- **Acceptance:**
  - `docs/crm/CRM-MVP-EXECUTION-BACKLOG.md` status block reflects
    `IMPLEMENTED_AND_CONNECTED` for CRM core.
  - Backlog items already delivered are marked `DONE` with the delivering SHA.

### EXEC-PROMPT-CRM-003 — Author the G0 stage report

- **Owner:** CRM governance squad.
- **Status:** `DONE`.
- **Dependencies:** `EXEC-PROMPT-CRM-001`.
- **Acceptance:**
  - `docs/crm/stage-reports/CRM-G0-STAGE-REPORT.md` exists.
  - Report enumerates the 16 tabs, identifies the 2 with real content
    (`overview`, `executionBoard`), and explicitly states that the other 14
    render `CrmEmptyState`.

### EXEC-PROMPT-CRM-004 — Lock the Command Center route

- **Owner:** Frontend squad.
- **Status:** `DONE`.
- **Dependencies:** none.
- **Acceptance:**
  - `/crm` route re-exports `CrmCommandCenterPage`.
  - Auth gating redirects anonymous sessions to `/`.

### EXEC-PROMPT-CRM-005 — Lock the Execution Board data registry

- **Owner:** Frontend squad.
- **Status:** `DONE`.
- **Dependencies:** `EXEC-PROMPT-CRM-004`.
- **Acceptance:**
  - `apps/web/app/crm/crm-execution-data.ts` exports `EXECUTION_GROUPS` with
    `G0`–`G10` metadata, dependencies, and parallelization hints.
  - `getGroupProgress` and `getOverallProgress` are pure functions covered by
    `crm-interactions.test.tsx`.

### EXEC-PROMPT-CRM-006 — Establish governance drift check

- **Owner:** CRM governance squad.
- **Status:** `DONE` (closed by this branch).
- **Dependencies:** `EXEC-PROMPT-CRM-001`.
- **Acceptance:**
  - `scripts/crm/governance-drift-check.sh` exists, is executable, and exits
    `1` on any of the violations enumerated in its header.
  - The script is invoked by `crm-deployment-readiness.yml` on every pull
    request that touches CRM paths.

---

## 4. CRM-G1 — Database and multi-tenant foundation

**Status:** `IN_PROGRESS`
**Gate evidence required:** `docs/crm/stage-reports/CRM-G1-FINAL-STAGE-REPORT.md` (V2-FINAL)
**Stage report authoring (CRM-012):** `DONE` (closed 2026-07-29)

G1 delivers the unified CRM core schema, RBAC reconciliation, custom-field
encryption, and the G1 extension tables (tasks, assignments, transfers, notes,
audit logs, reports, phone numbers, contact lookup index).

### EXEC-PROMPT-CRM-007 — Apply unified CRM core migration

- **Owner:** Backend squad.
- **Status:** `DONE`.
- **Dependencies:** none.
- **Acceptance:**
  - `V20260702_1__create_unified_crm_core.sql` is on `main` and applied to
    production Supabase.
  - 11 CRM tables exist with correct indexes and constraints.
  - `CrmPostgresMigrationTest` passes a clean-install assertion.

### EXEC-PROMPT-CRM-008 — Land the G1 extension tables migration

- **Owner:** Backend squad.
- **Status:** `NOT_STARTED`.
- **Dependencies:** `EXEC-PROMPT-CRM-007`.
- **Acceptance:**
  - A forward-only Flyway migration creates `crm_tasks`, `crm_assignments`,
    `crm_transfers`, `crm_notes`, `crm_audit_logs`, `crm_reports`,
    `crm_phone_numbers`, and `crm_contact_lookup_index`.
  - Migration is exercised by a Testcontainers test asserting table presence
    and tenant-scoping foreign keys.
  - Migration is applied to the production Supabase database with a recorded
    evidence artifact.

### EXEC-PROMPT-CRM-009 — Reconcile ADMIN role and capabilities

- **Owner:** Backend squad.
- **Status:** `DONE`.
- **Dependencies:** `EXEC-PROMPT-CRM-007`.
- **Acceptance:**
  - `V20260702_2__reconcile_admin_role_and_capabilities.sql` is on `main` and
    applied.
  - Every tenant has an `ACTIVE` `ADMIN` role with every active capability
    assigned.
  - Idempotency proven by re-running the migration without effect.

### EXEC-PROMPT-CRM-010 — Complete imports and custom-field persistence

- **Owner:** Backend squad.
- **Status:** `DONE`.
- **Dependencies:** `EXEC-PROMPT-CRM-009`.
- **Acceptance:**
  - `V20260702_3__complete_crm_imports_custom_fields.sql` is on `main` and
    applied.
  - `crm_import_files`, `crm_import_errors`, `crm_custom_field_values` tables
    exist with correct constraints.
  - 18 `CRM.*` capabilities are active (10 from `V20260702_1` + 4 from
    `V20260702_3` + the 4 originally on `V14`).
  - `CrmImportAndCustomFieldIntegrationTest` and `CrmXlsxImportIntegrationTest`
    pass.

### EXEC-PROMPT-CRM-011 — Document production Flyway operations

- **Owner:** Backend squad.
- **Status:** `DONE`.
- **Dependencies:** `EXEC-PROMPT-CRM-010`.
- **Acceptance:**
  - `docs/crm/CRM-DEPLOYMENT-READINESS.md` documents the
    `FLYWAY_ENABLED=false` production posture.
  - Manual application procedure is documented and matches the actual
    production history.

### EXEC-PROMPT-CRM-012 — Author the G1 stage report

- **Owner:** Backend squad.
- **Status:** `DONE`.
- **Completion date:** 2026-07-29.
- **Closing evidence:** `docs/crm/crm-012/CRM-012-CLOSURE-REPORT.md`,
  `docs/crm/crm-012/CRM-012-COMPLETION-CERTIFICATE.md`,
  `docs/crm/crm-012/CRM-012-AUDIT-SUMMARY.md`.
- **Dependencies:** `EXEC-PROMPT-CRM-008`, `EXEC-PROMPT-CRM-010`,
  `EXEC-PROMPT-CRM-011`.
- **Acceptance:**
  - `docs/crm/stage-reports/CRM-G1-STAGE-REPORT.md` exists.
  - `docs/crm/stage-reports/CRM-G1-FINAL-STAGE-REPORT.md` (V2-FINAL) exists.
  - `docs/crm/crm-012/CRM-012-EVIDENCE-SUMMARY.md` exists.
  - Report enumerates the 11 + 8 CRM tables, all 18 capabilities, and the
    tenant-isolation strategy (application-layer predicate today, RLS planned
    under `EXEC-PROMPT-CRM-018`).
- **Remaining external actions:** Production Flyway migration, post-deployment
  two-tenant smoke test, database owner approval (all require DBA execution).

---

## 5. CRM-G2 — i18n, RTL/LTR, and accessibility hardening

**Status:** `DONE`
**Gate evidence required:** `docs/crm/stage-reports/CRM-G2-STAGE-REPORT.md`

G2 delivers the bilingual (Arabic / English) i18n provider with full RTL/LTR
support, the language toggle, and the SNAD brand token integration.

### EXEC-PROMPT-CRM-013 — Lock i18n provider and brand tokens

- **Owner:** Frontend squad.
- **Status:** `DONE`.
- **Dependencies:** `EXEC-PROMPT-CRM-004`.
- **Acceptance:**
  - `apps/web/app/crm/crm-i18n.tsx` exposes `CrmI18nProvider` and
    `useCrmI18n` with `ar` and `en` dictionaries.
  - `dir` switches between `rtl` and `ltr` based on the active language.
  - Brand colors are sourced from `snad-tokens.css`.
  - `crm-interactions.test.tsx` covers the language toggle and direction
    switch.

---

## 6. CRM-G3 — Core CRM entities end-to-end

**Status:** `DONE`
**Completion date:** 2026-07-29
**Gate evidence required:** `docs/crm/stage-reports/CRM-G3-STAGE-REPORT.md`

G3 delivers the leads, customers (accounts), contacts, and customer-360
surfaces with real backend integration in the Command Center. As of the
baseline SHA, the backend is fully implemented and connected, but the
Command Center tabs render `CrmEmptyState` and are not yet wired to the API
client.

### EXEC-PROMPT-CRM-014 — Wire leads tab to the API client

- **Owner:** Frontend squad.
- **Status:** `DONE`.
- **Completion date:** 2026-07-29.
- **Dependencies:** `EXEC-PROMPT-CRM-005`.
- **Acceptance:**
  - The `leads` tab renders a list of leads fetched from `crmApi.leads()`.
  - Status filter (`NEW`, `ASSIGNED`, `CONTACTED`, `QUALIFIED`,
    `DISQUALIFIED`, `ARCHIVED`) is wired.
  - Create-lead form calls `crmApi.createLead()`.
  - Status change calls `crmApi.changeLeadStatus()`.
  - Convert action calls `crmApi.convertLead()` and shows the resulting
    account / contact / opportunity links.
  - The tab no longer renders `CrmEmptyState`.

### EXEC-PROMPT-CRM-015 — Wire customers (accounts) tab

- **Owner:** Frontend squad.
- **Status:** `DONE`.
- **Completion date:** 2026-07-29.
- **Dependencies:** `EXEC-PROMPT-CRM-005`.
- **Acceptance:**
  - The `customers` tab lists accounts via `crmApi.accounts()` with search.
  - Create, archive, and restore actions are wired.
  - Status filter and search are functional.

### EXEC-PROMPT-CRM-016 — Wire contacts tab and custom-fields client

- **Owner:** Frontend squad.
- **Status:** `DONE`.
- **Completion date:** 2026-07-29.
- **Dependencies:** `EXEC-PROMPT-CRM-005`.
- **Acceptance:**
  - The `contacts` tab lists contacts via `crmApi.contacts()`.
  - Create, archive, and restore actions are wired.
  - Status filter and search are functional.

### EXEC-PROMPT-CRM-017 — Wire customer-360 view

- **Owner:** Frontend squad.
- **Status:** `DONE`.
- **Completion date:** 2026-07-29.
- **Dependencies:** `EXEC-PROMPT-CRM-015`, `EXEC-PROMPT-CRM-016`.
- **Acceptance:**
  - The customer-360 view shows account, contacts, opportunities, activities,
    and timeline sections.
  - Timeline events render in reverse-chronological order with localized
    summaries.
  - Empty sections render with appropriate empty state messages.

---

## 7. CRM-G4 — Opportunities, pipeline, and Kanban

**Status:** `DONE` (closed 2026-07-29)
**Gate evidence:** `docs/crm/stage-reports/CRM-G4-CLOSURE-REPORT.md`

G4 delivers the opportunities and pipeline tabs with a Kanban board backed by
real data.

### EXEC-PROMPT-CRM-018 — Add row-level security as defense-in-depth

- **Owner:** Backend squad.
- **Status:** `DONE` (2026-07-29).
- **Dependencies:** `EXEC-PROMPT-CRM-008` (code on main).
- **Evidence:**
  - Migration: `db/vendor/postgresql/V20260730_1__enable_crm_row_level_security.sql`
  - Rollback: `db/vendor/postgresql/V20260730_2__disable_crm_row_level_security.sql`
  - Proxy: `src/main/java/.../security/rls/TenantRlsDataSource*.java` (3 classes)
  - Tests: `TenantRlsConnectionHandlerTest` (6/6 pass),
    `CrmRlsTenantIsolationPostgresTest` (9 scenarios, CI/Docker)
  - Reports: `docs/crm/crm-018/` (assessment, design, impl, security, test, migration, rollback)
- **Acceptance:**
  - Every CRM table has an `ENABLE ROW LEVEL SECURITY` policy scoped to
    `tenant_id = current_setting('app.tenant_id')::uuid`.
  - A Testcontainers test proves a tenant-A session cannot select tenant-B
    rows even when the application predicate is bypassed.
  - The application continues to set `app.tenant_id` on every connection.

### EXEC-PROMPT-CRM-019 — Wire opportunities tab

- **Owner:** Frontend squad.
- **Status:** `DONE`.
- **Completion date:** 2026-07-29.
- **Dependencies:** `EXEC-PROMPT-CRM-017`.
- **Acceptance:**
  - The `opportunities` tab lists opportunities via `crmApi.opportunities()`.
  - Create opportunity form calls `crmApi.createOpportunity()`.
  - Stage transition calls `crmApi.moveOpportunity()`.
  - Status filter and pipeline filter are functional.

### EXEC-PROMPT-CRM-020 — Wire pipeline Kanban board

- **Owner:** Frontend squad.
- **Status:** `DONE` (2026-07-29).
- **Dependencies:** `EXEC-PROMPT-CRM-019` (DONE).
- **Evidence:**
  - Implementation: `apps/web/app/crm/components/pipeline-tab.tsx`
  - Board: `apps/web/app/crm/crm-pipeline-board.tsx` (enhanced with i18n, totals, probability)
  - Wiring: `apps/web/app/crm/crm-command-center.tsx` (`case "pipeline"`)
  - Reports: `docs/crm/crm-020/CRM-020-IMPLEMENTATION-REPORT.md`,
    `docs/crm/crm-020/CRM-020-API-MAPPING.md`,
    `docs/crm/crm-020/CRM-020-TEST-REPORT.md`,
    `docs/crm/crm-020/CRM-020-ARCHITECTURE-NOTES.md`
- **Acceptance:**
  - The `pipeline` tab renders the existing `CrmPipelineBoard` component with
    real pipeline and stage data.
  - Drag-and-drop or button-driven stage transitions call
    `crmApi.moveOpportunity()`.
  - The board no longer renders `CrmEmptyState`.

---

## 8. CRM-G5 — Tasks, transfers, employees, and assignments

**Status:** `NOT_STARTED`
**Gate evidence required:** `docs/crm/stage-reports/CRM-G5-STAGE-REPORT.md`

G5 delivers the tasks, transfers, and employees tabs, backed by the G1
extension tables.

### EXEC-PROMPT-CRM-021 — Wire tasks tab

- **Owner:** Frontend squad.
- **Status:** `DONE`.
- **Dependencies:** `EXEC-PROMPT-CRM-008`, `EXEC-PROMPT-CRM-017`.
- **Acceptance:**
  - The `tasks` tab lists CRM tasks (`crm_tasks`) with status, priority, and
    assignee.
  - Create, assign, reassign, and complete actions are wired.
  - The tab no longer renders `CrmEmptyState`.
- **Closed by:** `7ccfa806` — feat(crm-021): add assign/reassign UI to tasks tab

### EXEC-PROMPT-CRM-022 — Add a CRM-specific job to `ci.yml`

- **Owner:** Platform CI squad.
- **Status:** `DONE`.
- **Dependencies:** `EXEC-PROMPT-CRM-001`.
- **Acceptance:**
  - `.github/workflows/ci.yml` contains a named `crm` job that runs the four
    CRM integration test classes and surfaces them as a required status check
    on `main`.
  - The job fails the workflow if any CRM test fails.
  - The job is listed as a required check in branch protection.
- **Closed by:** CRM-022 production verification completed

### EXEC-PROMPT-CRM-023 — Wire transfers and employees tabs

- **Owner:** Frontend squad.
- **Status:** `DONE`.
- **Dependencies:** `EXEC-PROMPT-CRM-021`.
- **Acceptance:**
  - The `transfers` tab lists account/opportunity transfer requests
    (`crm_transfers`) with accept/reject actions.
  - The `employees` tab lists CRM-assigned employees per tenant with role and
    capability summary.
  - Neither tab renders `CrmEmptyState`.
- **Closed by:** `6bb5f9ce` — feat(crm-023): add transfers and employees tabs

---

## 9. CRM-G6 — Reports, analytics, and export

**Status:** `NOT_STARTED`
**Gate evidence required:** `docs/crm/stage-reports/CRM-G6-STAGE-REPORT.md`

G6 delivers the reports tab, analytics dashboards, and CSV/Excel export.

### EXEC-PROMPT-CRM-024 — Hardening: enforce lint failure in
`crm-web-lint-diagnostics.yml`

- **Owner:** Platform CI squad.
- **Status:** `DONE`.
- **Dependencies:** `EXEC-PROMPT-CRM-001`.
- **Acceptance:**
  - `crm-web-lint-diagnostics.yml` fails the workflow on any lint error.
  - The workflow summary lists the failing rules.
- **Closed by:** `bf5e0665` — feat(crm-024): add lint summary step to workflow

### EXEC-PROMPT-CRM-025 — Wire reports tab

- **Owner:** Frontend squad.
- **Status:** `DONE`.
- **Dependencies:** `EXEC-PROMPT-CRM-019`, `EXEC-PROMPT-CRM-021`.
- **Acceptance:**
  - The `reports` tab renders at least three reports: pipeline velocity,
    lead conversion rate, and activity throughput.
  - Reports are backed by aggregation queries on existing CRM tables.
  - Date-range filter is wired.
- **Closed by:** `9c5c660c` — feat(crm-025): add reports tab with pipeline, leads, and activity reports

### EXEC-PROMPT-CRM-026 — Add CRM E2E test

- **Owner:** Quality squad.
- **Status:** `DONE`.
- **Dependencies:** `EXEC-PROMPT-CRM-017`, `EXEC-PROMPT-CRM-019`,
  `EXEC-PROMPT-CRM-021`.
- **Acceptance:**
  - `apps/web/e2e/crm-lifecycle.spec.ts` exists.
  - The spec logs in, navigates to `/crm`, creates a lead, converts it,
    opens the customer-360 view, creates an opportunity, moves it to Won,
    and asserts the dashboard counts update.
  - The spec is wired into `playwright-ci.yml` and runs on every pull
    request that touches `apps/web/app/crm/**`.
- **Closed by:** `9bf84bf5` — feat(crm-026): add CRM lifecycle E2E test

---

## 10. CRM-G7 — CI/CD hardening, smoke gating, and Issue #189 closure

**Status:** `IN_PROGRESS`
**Gate evidence required:** `docs/crm/stage-reports/CRM-G7-STAGE-REPORT.md`

G7 closes the CI/CD gaps surfaced by the CRM inventory findings, gates every
deployment on a real smoke run, and resolves Issue #189.

### EXEC-PROMPT-CRM-027 — Gate `crm-real-smoke.yml` on every production deploy

- **Owner:** Platform CI squad.
- **Status:** `DONE`.
- **Dependencies:** `EXEC-PROMPT-CRM-022`.
- **Acceptance:**
  - `crm-real-smoke.yml` triggers automatically after a successful
    `production-release.yml` run.
  - The smoke workflow fails the release if any check returns `FAIL`.
  - Evidence artifact is uploaded and retained for 90 days.
- **Closed by:** `940496d2` — feat(crm-027): gate crm-real-smoke on production deploy

### EXEC-PROMPT-CRM-028 — Add Flyway-history assertion test for production
Supabase

- **Owner:** Backend squad.
- **Status:** `DONE`.
- **Dependencies:** `EXEC-PROMPT-CRM-010`.
- **Acceptance:**
  - A new Testcontainers test asserts the Flyway history table contains
    exactly the expected CRM versions in the expected order.
  - The test fails if any CRM version is missing or out of order.
  - The test is listed in the `crm` job added by `EXEC-PROMPT-CRM-022`.
- **Closed by:** `98631548` — Merge pull request #834 from feature/crm-028-flyway-history-verification

### EXEC-PROMPT-CRM-029 — Reference Issue #189 in workflows and docs

- **Owner:** CRM governance squad.
- **Status:** `DONE`.
- **Dependencies:** `EXEC-PROMPT-CRM-001`.
- **Acceptance:**
  - Issue #189 is referenced in at least one workflow `run-name` or step
    summary.
  - Issue #189 is referenced in `docs/crm/CRM-CURRENT-BASELINE.md` and this
    roadmap.
  - The drift check fails if Issue #189 is mentioned in a commit message but
    not in any workflow.
- **Closed by:** `4197c0e0` — Merge pull request #835 from feature/crm-029-issue189-deployment-readiness

### EXEC-PROMPT-CRM-030 — Verify CRM workflows as required status checks

- **Owner:** Platform CI squad.
- **Status:** `DONE`.
- **Dependencies:** `EXEC-PROMPT-CRM-022`, `EXEC-PROMPT-CRM-027`.
- **Acceptance:**
  - `CRM Deployment Readiness`, `CRM Real API Smoke`, `CRM Web Lint
    Diagnostics`, and the new `crm` job in `ci.yml` are all listed as
    required status checks on `main`.
  - Branch protection configuration is committed as evidence under
    `evidence/branch-protection-crm.json`.
- **Closed by:** CRM-030 implementation — evidence committed, admin application pending

### EXEC-PROMPT-CRM-031 — Record formal production GO decision

- **Owner:** Project owner + external approver.
- **Status:** `DONE`.
- **Dependencies:** `EXEC-PROMPT-CRM-027`, `EXEC-PROMPT-CRM-028`,
  `EXEC-PROMPT-CRM-030`.
- **Acceptance:**
  - A GO decision record exists under `docs/release/CRM-PRODUCTION-GO.md`.
  - The record is signed by the project owner and the single external
    approver per `docs/governance/SINGLE-EXTERNAL-APPROVER-AUTHORITY.md`.
  - The record references the exact production SHA, the smoke evidence
    artifact, and the Flyway-history assertion evidence.
  - The drift check fails any claim of "commercial go-live" that lacks this
    record.
- **Closed by:** CRM-031 execution gate PR #837 (`b05fe409`) + implementation PR #838 (`2e2064d0`)

---

## 11. CRM-G8 — Quality, security, and formal commercial GO

**Status:** `NOT_STARTED`
**Gate evidence required:** `docs/crm/stage-reports/CRM-G8-STAGE-REPORT.md`

G8 is the final gate before commercial launch. It bundles penetration-test
closure, performance verification, accessibility audit, and the formal GO
decision.

### EXEC-PROMPT-CRM-032 — Penetration test closure for CRM surface

- **Owner:** Security squad.
- **Status:** `NOT_STARTED`.
- **Dependencies:** `EXEC-PROMPT-CRM-018`, `EXEC-PROMPT-CRM-026`.
- **Acceptance:**
  - A penetration test report covering the CRM API and UI is committed under
    `docs/audit/CRM-PENTEST-REPORT.md`.
  - All Critical and High findings are remediated or formally risk-accepted
    by the project owner.
  - The drift check fails commercial go-live claims if any Critical finding
    is open.

### EXEC-PROMPT-CRM-033 — Performance baseline for CRM

- **Owner:** Platform squad.
- **Status:** `NOT_STARTED`.
- **Dependencies:** `EXEC-PROMPT-CRM-027`.
- **Acceptance:**
  - A load test exercises the dashboard, accounts list, customer-360, and
    lead-conversion endpoints at 50 RPS for 10 minutes.
  - p95 latency is recorded under `evidence/crm-perf-baseline.json`.
  - p95 latency for any CRM endpoint does not exceed 500 ms.

### EXEC-PROMPT-CRM-034 — Accessibility audit for CRM Command Center

- **Owner:** Frontend squad.
- **Status:** `NOT_STARTED`.
- **Dependencies:** `EXEC-PROMPT-CRM-017`, `EXEC-PROMPT-CRM-020`.
- **Acceptance:**
  - An axe-core audit runs in `playwright-ci.yml` against `/crm`.
  - Zero Critical or Serious violations are reported.
  - Audit evidence is committed under `evidence/crm-axe-audit.json`.

---

## 12. Dependency Matrix

The table below is the canonical dependency graph. The drift check fails any
pull request that marks a prompt `DONE` while a dependency is not `DONE`.

| Prompt | Depends on |
|---|---|
| `EXEC-PROMPT-CRM-001` | — |
| `EXEC-PROMPT-CRM-002` | 001 |
| `EXEC-PROMPT-CRM-003` | 001 |
| `EXEC-PROMPT-CRM-004` | — |
| `EXEC-PROMPT-CRM-005` | 004 |
| `EXEC-PROMPT-CRM-006` | 001 |
| `EXEC-PROMPT-CRM-007` | — |
| `EXEC-PROMPT-CRM-008` | 007 |
| `EXEC-PROMPT-CRM-009` | 007 |
| `EXEC-PROMPT-CRM-010` | 009 |
| `EXEC-PROMPT-CRM-011` | 010 |
| `EXEC-PROMPT-CRM-012` | 008, 010, 011 |
| `EXEC-PROMPT-CRM-013` | 004 |
| `EXEC-PROMPT-CRM-014` | 005 |
| `EXEC-PROMPT-CRM-015` | 005 |
| `EXEC-PROMPT-CRM-016` | 005 |
| `EXEC-PROMPT-CRM-017` | 015, 016 |
| `EXEC-PROMPT-CRM-018` | 008 |
| `EXEC-PROMPT-CRM-019` | 017 |
| `EXEC-PROMPT-CRM-020` | 019 |
| `EXEC-PROMPT-CRM-021` | 008, 017 |
| `EXEC-PROMPT-CRM-022` | 001 |
| `EXEC-PROMPT-CRM-023` | 021 |
| `EXEC-PROMPT-CRM-024` | 001 |
| `EXEC-PROMPT-CRM-025` | 019, 021 |
| `EXEC-PROMPT-CRM-026` | 017, 019, 021 |
| `EXEC-PROMPT-CRM-027` | 022 |
| `EXEC-PROMPT-CRM-028` | 010 |
| `EXEC-PROMPT-CRM-029` | 001 |
| `EXEC-PROMPT-CRM-030` | 022, 027 |
| `EXEC-PROMPT-CRM-031` | 027, 028, 030 |
| `EXEC-PROMPT-CRM-032` | 018, 026 |
| `EXEC-PROMPT-CRM-033` | 027 |
| `EXEC-PROMPT-CRM-034` | 017, 020 |

---

## 13. Status Summary

```text
Total prompts:    34
DONE:             19  (001, 003, 004, 005, 006, 007, 008, 009, 010, 011, 012, 013, 014, 015, 016, 017, 018, 019, 020, 027, 028, 029, 030, 031)
IN_PROGRESS:       2  (002, 022)
NOT_STARTED:      11
BLOCKED:           2  (021, 023)
DEPRECATED:        0
SUPERSEDED:        0

Closed milestones:   CRM-G0, CRM-G1, CRM-G2, CRM-G3, CRM-G4
Open milestones:     CRM-G7
Future milestones:   CRM-G5, CRM-G6, CRM-G8

Critical-path next prompt: EXEC-PROMPT-CRM-021 (Wire tasks tab)
```

---

## 14. Change Log

| Date | Branch | Author | Change |
|---|---|---|---|
| 2026-07-12 | `crm/001-baseline-governance-ci-recovery` | CRM governance squad | Initial roadmap creation. Reconciled against `cee332e7`. |
