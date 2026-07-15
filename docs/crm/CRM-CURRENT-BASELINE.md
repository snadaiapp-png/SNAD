# CRM Current Baseline

> **Branch:** `crm/001-baseline-governance-ci-recovery`
> **Baseline SHA:** `cee332e7f86a6ea64fbb5f72120ae77c441f6eac`
> **Document status:** AUTHORITATIVE — supersedes any older `CRM_PRODUCT_BUILD: NOT STARTED` claim.
> **Last reconciled:** 2026-07-12 against the `main` HEAD at the baseline SHA above.

This document is the single source of truth for the as-built state of the SNAD
CRM product on the SNAD Platform repository. It is consumed by:

- `scripts/crm/governance-drift-check.sh` — governance drift enforcement.
- `docs/crm/CRM-ENTERPRISE-EXECUTION-ROADMAP.md` — forward execution plan.
- `docs/crm/README.md` — top-level CRM index.
- `docs/crm-gap-analysis.md` and `docs/crm-readiness-assessment.md` — gap and readiness rollups.

Where this document disagrees with another CRM document, **this document wins**.
A pull request that re-introduces a contradictory `NOT STARTED` claim is rejected
by the governance drift check.

---

## 1. Status Code Legend

Every CRM component in this baseline is classified with one of the following
status codes. The same codes are used in `CRM-ENTERPRISE-EXECUTION-ROADMAP.md`
and the gap/readiness rollups so that automated tooling can scan for drift.

| Code | Meaning |
|---|---|
| `IMPLEMENTED_AND_CONNECTED` | Code merged to `main`, deployed to the target environment, exercised end-to-end by an automated test or smoke workflow, and reachable from the consumer (UI / API client / external caller). |
| `IMPLEMENTED_NOT_CONNECTED` | Code merged to `main` but not wired into a default user-facing surface. Examples: superseded UI, hidden routes, or unreferenced client modules. |
| `PARTIALLY_IMPLEMENTED` | Subset of the component is implemented and connected; remaining subset is missing, behind a flag, or stubbed. |
| `DOCUMENTED_ONLY` | Specified or planned in a design document under `docs/crm/`; no production code merged. |
| `NOT_IMPLEMENTED` | No code, no design accepted for execution. The default for any CRM capability not enumerated elsewhere. |
| `BLOCKED` | Implementation explicitly blocked by an open dependency, incident, or external approval. |
| `DEPRECATED` | Implemented and reachable, but marked for removal. |
| `SUPERSEDED` | Implemented and reachable historically, but replaced by a newer component that owns the surface. |

---

## 2. Baseline Summary

```text
CRM_PRODUCT_BUILD: IMPLEMENTED_AND_CONNECTED
CRM_BUILD_READINESS: CLOSED
PLATFORM_CORE_DEPENDENCY: SATISFIED
PRODUCTION_AUTHORIZATION: PARTIAL — runtime deployed and smoke-passing; formal GO decision record still required for commercial launch.
```

The CRM product build is **not** `NOT STARTED`. The product code is merged on
`main` at SHA `cee332e7`, deployed to the self-hosted production backend, and
exercised by the authenticated two-tenant smoke workflow
(`.github/workflows/crm-real-smoke.yml`). The CRM Command Center is deployed to
Vercel. The Supabase Postgres database has the unified CRM core schema applied.

What remains **NOT** authorized is the formal **commercial go-live** decision,
which is gated by `docs/release/OWNER-PRODUCTION-GO-CHECKLIST.md` and the
external-approver model in `docs/governance/SINGLE-EXTERNAL-APPROVER-AUTHORITY.md`.
The runtime being live is not the same as commercial authorization; the gap is
tracked as `EXEC-PROMPT-CRM-031` in the execution roadmap.

---

## 3. Backend Baseline

### 3.1 Status

```text
BACKEND: IMPLEMENTED_AND_CONNECTED
```

### 3.2 Source modules

| Module | Path | Status |
|---|---|---|
| `CrmController` | `apps/sanad-platform/src/main/java/com/sanad/platform/crm/web/CrmController.java` (265 lines) | `IMPLEMENTED_AND_CONNECTED` |
| `CrmService` | `apps/sanad-platform/src/main/java/com/sanad/platform/crm/web/CrmService.java` (216 lines) | `IMPLEMENTED_AND_CONNECTED` |
| `CrmExtendedService` | `apps/sanad-platform/src/main/java/com/sanad/platform/crm/web/CrmExtendedService.java` (1,935 lines) | `IMPLEMENTED_AND_CONNECTED` |
| `CrmModels` (request/response records) | `apps/sanad-platform/src/main/java/com/sanad/platform/crm/web/CrmModels.java` (134 lines) | `IMPLEMENTED_AND_CONNECTED` |

### 3.3 API surface

All endpoints are mounted under `/api/v1/crm/*` and gated by `@RequireCapability`
on a `CRM.*` capability. The full authenticated surface is:

| Method | Path | Capability | Status |
|---|---|---|---|
| GET | `/api/v1/crm/dashboard` | `CRM.ACCOUNT.READ` | `IMPLEMENTED_AND_CONNECTED` |
| POST | `/api/v1/crm/accounts` | `CRM.ACCOUNT.WRITE` | `IMPLEMENTED_AND_CONNECTED` |
| GET | `/api/v1/crm/accounts` | `CRM.ACCOUNT.READ` | `IMPLEMENTED_AND_CONNECTED` |
| GET | `/api/v1/crm/accounts/{accountId}` | `CRM.ACCOUNT.READ` | `IMPLEMENTED_AND_CONNECTED` |
| GET | `/api/v1/crm/accounts/{accountId}/customer-360` | `CRM.ACCOUNT.READ` | `IMPLEMENTED_AND_CONNECTED` |
| PATCH | `/api/v1/crm/accounts/{accountId}` | `CRM.ACCOUNT.WRITE` | `IMPLEMENTED_AND_CONNECTED` |
| PATCH | `/api/v1/crm/accounts/{accountId}/archive` | `CRM.ACCOUNT.ARCHIVE` | `IMPLEMENTED_AND_CONNECTED` |
| PATCH | `/api/v1/crm/accounts/{accountId}/restore` | `CRM.ACCOUNT.ARCHIVE` | `IMPLEMENTED_AND_CONNECTED` |
| POST | `/api/v1/crm/contacts` | `CRM.CONTACT.WRITE` | `IMPLEMENTED_AND_CONNECTED` |
| GET | `/api/v1/crm/contacts` | `CRM.CONTACT.READ` | `IMPLEMENTED_AND_CONNECTED` |
| GET | `/api/v1/crm/contacts/{contactId}` | `CRM.CONTACT.READ` | `IMPLEMENTED_AND_CONNECTED` |
| PATCH | `/api/v1/crm/contacts/{contactId}` | `CRM.CONTACT.WRITE` | `IMPLEMENTED_AND_CONNECTED` |
| PATCH | `/api/v1/crm/contacts/{contactId}/archive` | `CRM.CONTACT.ARCHIVE` | `IMPLEMENTED_AND_CONNECTED` |
| PATCH | `/api/v1/crm/contacts/{contactId}/restore` | `CRM.CONTACT.ARCHIVE` | `IMPLEMENTED_AND_CONNECTED` |
| POST | `/api/v1/crm/leads` | `CRM.LEAD.WRITE` | `IMPLEMENTED_AND_CONNECTED` |
| GET | `/api/v1/crm/leads` | `CRM.LEAD.READ` | `IMPLEMENTED_AND_CONNECTED` |
| GET | `/api/v1/crm/leads/{leadId}` | `CRM.LEAD.READ` | `IMPLEMENTED_AND_CONNECTED` |
| PATCH | `/api/v1/crm/leads/{leadId}/status` | `CRM.LEAD.WRITE` | `IMPLEMENTED_AND_CONNECTED` |
| POST | `/api/v1/crm/leads/{leadId}/convert` | `CRM.LEAD.CONVERT` | `IMPLEMENTED_AND_CONNECTED` |
| POST | `/api/v1/crm/pipelines` | `CRM.ADMIN` | `IMPLEMENTED_AND_CONNECTED` |
| GET | `/api/v1/crm/pipelines` | `CRM.OPPORTUNITY.READ` | `IMPLEMENTED_AND_CONNECTED` |
| GET | `/api/v1/crm/pipelines/{pipelineId}/stages` | `CRM.OPPORTUNITY.READ` | `IMPLEMENTED_AND_CONNECTED` |
| POST | `/api/v1/crm/opportunities` | `CRM.OPPORTUNITY.WRITE` | `IMPLEMENTED_AND_CONNECTED` |
| GET | `/api/v1/crm/opportunities` | `CRM.OPPORTUNITY.READ` | `IMPLEMENTED_AND_CONNECTED` |
| GET | `/api/v1/crm/opportunities/{opportunityId}` | `CRM.OPPORTUNITY.READ` | `IMPLEMENTED_AND_CONNECTED` |
| PATCH | `/api/v1/crm/opportunities/{opportunityId}/stage` | `CRM.OPPORTUNITY.WRITE` | `IMPLEMENTED_AND_CONNECTED` |
| POST | `/api/v1/crm/activities` | `CRM.ACTIVITY.WRITE` | `IMPLEMENTED_AND_CONNECTED` |
| GET | `/api/v1/crm/activities` | `CRM.ACTIVITY.READ` | `IMPLEMENTED_AND_CONNECTED` |
| GET | `/api/v1/crm/activities/{activityId}` | `CRM.ACTIVITY.READ` | `IMPLEMENTED_AND_CONNECTED` |
| PATCH | `/api/v1/crm/activities/{activityId}/complete` | `CRM.ACTIVITY.WRITE` | `IMPLEMENTED_AND_CONNECTED` |
| GET | `/api/v1/crm/timeline/{subjectType}/{subjectId}` | `CRM.ACTIVITY.READ` | `IMPLEMENTED_AND_CONNECTED` |
| POST | `/api/v1/crm/imports/upload` (multipart) | `CRM.IMPORT.WRITE` | `IMPLEMENTED_AND_CONNECTED` |
| GET | `/api/v1/crm/imports` | `CRM.IMPORT.READ` | `IMPLEMENTED_AND_CONNECTED` |
| GET | `/api/v1/crm/imports/{jobId}` | `CRM.IMPORT.READ` | `IMPLEMENTED_AND_CONNECTED` |
| POST | `/api/v1/crm/imports/{jobId}/run` | `CRM.IMPORT.WRITE` | `IMPLEMENTED_AND_CONNECTED` |
| POST | `/api/v1/crm/imports/{jobId}/cancel` | `CRM.IMPORT.WRITE` | `IMPLEMENTED_AND_CONNECTED` |
| GET | `/api/v1/crm/imports/{jobId}/errors` | `CRM.IMPORT.READ` | `IMPLEMENTED_AND_CONNECTED` |
| GET | `/api/v1/crm/imports/{jobId}/errors.csv` | `CRM.IMPORT.READ` | `IMPLEMENTED_AND_CONNECTED` |
| POST | `/api/v1/crm/custom-fields` | `CRM.CUSTOM_FIELD.WRITE` | `IMPLEMENTED_AND_CONNECTED` |
| GET | `/api/v1/crm/custom-fields` | `CRM.CUSTOM_FIELD.READ` | `IMPLEMENTED_AND_CONNECTED` |
| PUT | `/api/v1/crm/custom-fields/values/{entityType}/{entityId}` | `CRM.CUSTOM_FIELD.WRITE` | `IMPLEMENTED_AND_CONNECTED` |
| GET | `/api/v1/crm/custom-fields/values/{entityType}/{entityId}` | `CRM.CUSTOM_FIELD.READ` | `IMPLEMENTED_AND_CONNECTED` |
| GET | `/api/v1/crm/custom-fields/values/{entityType}/{entityId}/sensitive` | `CRM.ADMIN` | `IMPLEMENTED_AND_CONNECTED` |
| GET | `/api/v1/crm/custom-fields/search` | `CRM.CUSTOM_FIELD.READ` | `IMPLEMENTED_AND_CONNECTED` |

### 3.4 Backend runtime evidence

- Deployment target: **self-hosted production server** (not Render).
- Health probe: `/actuator/health` exercised by `crm-real-smoke.yml` before
  running any tenant-authenticated assertions.
- Tenant isolation: proven in production smoke — Tenant B cannot read Tenant A's
  account by ID (HTTP `404`) and Tenant A's account is absent from Tenant B's
  list response.
- Custom-field encryption: AES-256-GCM with `CRM_CUSTOM_FIELD_ENCRYPTION_KEY`
  enforced by `scripts/crm/deployment-preflight.sh`.
- Import worker: lease-based, idempotent, with database-enforced
  `processed_rows = succeeded_rows + failed_rows`.

---

## 4. Database Baseline

### 4.1 Status

```text
DATABASE: IMPLEMENTED_AND_CONNECTED
FLYWAY_ENABLED (production): false — migrations applied manually
FLYWAY_ENABLED (preflight, staging, CI): true — enforced by scripts/crm/deployment-preflight.sh
```

### 4.2 Migration inventory

| Flyway version | File | Tables / changes | Status |
|---|---|---|---|
| `20260702.1` | `V20260702_1__create_unified_crm_core.sql` (309 lines) | `crm_accounts`, `crm_contacts`, `crm_pipelines`, `crm_pipeline_stages`, `crm_leads`, `crm_opportunities`, `crm_opportunity_stage_history`, `crm_activities`, `crm_timeline_events`, `crm_import_jobs`, `crm_custom_field_definitions`. Seeds 10 `CRM.*` capabilities. | `IMPLEMENTED_AND_CONNECTED` |
| `20260702.2` | `V20260702_2__reconcile_admin_role_and_capabilities.sql` (74 lines) | Forward-only reconciliation: creates an `ADMIN` role for every tenant when missing and assigns every active capability. | `IMPLEMENTED_AND_CONNECTED` |
| `20260702.3` | `V20260702_3__complete_crm_imports_custom_fields.sql` (133 lines) | Adds `crm_import_files`, `crm_import_errors`, `crm_custom_field_values`. Adds file-sha256, mapping_json, lease, and worker columns to `crm_import_jobs`. Seeds `CRM.CUSTOM_FIELD.READ/WRITE` and `CRM.IMPORT.READ/WRITE` capabilities. | `IMPLEMENTED_AND_CONNECTED` |
| `20260706.1` | `V20260706_1__create_tenant_quota.sql` | Tenant quota table (platform scaling, not CRM-only). | `IMPLEMENTED_AND_CONNECTED` |
| `20260711.1` | `V20260711_1__create_subscription_change_events.sql` | `subscription_change_events` (billing, not CRM-only). | `IMPLEMENTED_AND_CONNECTED` |
| `20260713.1` | `V20260713_1__create_crm_idempotency_records.sql` | `crm_idempotency_records` table for V2 idempotent POST endpoints. | `IMPLEMENTED_AND_CONNECTED` |
| `20260713.2` | `V20260713_2__add_pipeline_version_column.sql` | Adds `version` column to `crm_pipelines` for optimistic locking. | `IMPLEMENTED_AND_CONNECTED` |
| `20260716.1` | `V20260716_1__create_crm_tasks.sql` | `crm_tasks` table (first-class task management, separate from `crm_activities`). Seeds `CRM.TASK.READ/WRITE` capabilities. | `IMPLEMENTED_AND_CONNECTED` |
| `20260716.2` | `V20260716_2__create_crm_notes.sql` | `crm_notes` table (append-only notes attached to any CRM entity). Seeds `CRM.NOTE.READ/WRITE` capabilities. | `IMPLEMENTED_AND_CONNECTED` |

### 4.3 CRM RBAC capabilities

22 active `CRM.*` capabilities are seeded by `V20260702_1`, `V20260702_3`, `V20260716_1`, and `V20260716_2`:
| `20260716.1` | `V20260716_1__create_crm_tasks.sql` | `crm_tasks` table (first-class task management, separate from `crm_activities`). Seeds `CRM.TASK.READ/WRITE` capabilities and grants them to ADMIN role. | `IMPLEMENTED_AND_CONNECTED` |

### 4.3 CRM RBAC capabilities

20 active `CRM.*` capabilities are seeded by `V20260702_1`, `V20260702_3`, and `V20260716_1`:

```text
CRM.ACCOUNT.READ        CRM.ACCOUNT.WRITE       CRM.ACCOUNT.ARCHIVE
CRM.CONTACT.READ        CRM.CONTACT.WRITE       CRM.CONTACT.ARCHIVE
CRM.LEAD.READ           CRM.LEAD.WRITE          CRM.LEAD.CONVERT
CRM.OPPORTUNITY.READ    CRM.OPPORTUNITY.WRITE
CRM.ACTIVITY.READ       CRM.ACTIVITY.WRITE
CRM.TASK.READ           CRM.TASK.WRITE
CRM.NOTE.READ           CRM.NOTE.WRITE
CRM.IMPORT.READ         CRM.IMPORT.WRITE
CRM.CUSTOM_FIELD.READ   CRM.CUSTOM_FIELD.WRITE
CRM.ADMIN
```

The previous `CRM-DEPLOYMENT-READINESS.md` claim of "14 CRM capabilities" is
**stale**; the reconciled count after `V20260716_2` is **22** (was 18 before
Tasks + Notes migrations). The drift check flags any document or workflow that
hard-codes `14`, `15`, `18`, or `20`.
**stale**; the reconciled count after `V20260716_1` is **20** (was 18 before
the Tasks migration). The drift check flags any document or workflow that
hard-codes `14`, `15`, or `18`.

### 4.4 G1 extension tables (planned but not yet applied)

The CRM Execution Board and the older `crm-execution-data.ts` reference a G1
group that introduces `crm_tasks`, `crm_assignments`, `crm_transfers`,
`crm_notes`, `crm_audit_logs`, `crm_reports`, `crm_phone_numbers`, and
`crm_contact_lookup_index`. These tables are **not** present in
`apps/sanad-platform/src/main/resources/db/migration/` on the baseline SHA and
are **not** applied to production. They are tracked as `DOCUMENTED_ONLY` until
`EXEC-PROMPT-CRM-008` lands a forward-only Flyway migration that is exercised
by a Testcontainers migration test.

### 4.5 Row-Level Security

The unified CRM core schema is **not** protected by PostgreSQL Row-Level
Security policies. Tenant isolation is enforced at the **application layer** via
the `tenant_id` predicate on every query in `CrmService` and
`CrmExtendedService`, plus the `@RequireCapability` authorization aspect.
Adding RLS as defense-in-depth is tracked as
`EXEC-PROMPT-CRM-018` and must not be described as already implemented.

---

## 5. Frontend Baseline

### 5.1 Status

```text
FRONTEND: PARTIALLY_IMPLEMENTED
```

The CRM Command Center is deployed to Vercel and the API client is connected,
but most tabs render only an `EmptyState` placeholder. The marketing surface
must not be described as a complete product.

### 5.2 Module inventory

| Module | Path | Lines | Status |
|---|---|---:|---|
| CRM Command Center (default export, `/crm` route) | `apps/web/app/crm/crm-command-center.tsx` | 419 | `IMPLEMENTED_AND_CONNECTED` |
| `/crm` page (re-exports Command Center) | `apps/web/app/crm/page.tsx` | 5 | `IMPLEMENTED_AND_CONNECTED` |
| CRM i18n provider (Arabic / English, RTL / LTR) | `apps/web/app/crm/crm-i18n.tsx` | 139 | `IMPLEMENTED_AND_CONNECTED` |
| CRM Overview (KPI placeholders, real dashboard fetch) | `apps/web/app/crm/crm-overview.tsx` | 90 | `IMPLEMENTED_AND_CONNECTED` |
| CRM Execution Board (G0–G10 tracker) | `apps/web/app/crm/crm-execution-board.tsx` | 308 | `IMPLEMENTED_AND_CONNECTED` |
| Execution Board data registry (G0–G10 + tasks) | `apps/web/app/crm/crm-execution-data.ts` | 89 | `IMPLEMENTED_AND_CONNECTED` |
| CRM Empty State component | `apps/web/app/crm/crm-empty-state.tsx` | 36 | `IMPLEMENTED_AND_CONNECTED` |
| CRM Pipeline Board (Kanban placeholder) | `apps/web/app/crm/crm-pipeline-board.tsx` | 154 | `IMPLEMENTED_NOT_CONNECTED` |
| CRM Virtual Table | `apps/web/app/crm/crm-virtual-table.tsx` | 85 | `IMPLEMENTED_NOT_CONNECTED` |
| CRM View Utils | `apps/web/app/crm/crm-view-utils.ts` | 17 | `IMPLEMENTED_AND_CONNECTED` |
| CRM workspace v2 (superseded) | `apps/web/app/crm/crm-workspace-v2.tsx` | 350 | `SUPERSEDED` |
| CRM advanced view (superseded) | `apps/web/app/crm/crm-advanced-view.tsx` | 102 | `SUPERSEDED` |
| CRM API client | `apps/web/lib/api/crm.ts` | 140 | `IMPLEMENTED_AND_CONNECTED` |
| CRM module CSS | `apps/web/app/crm/crm.module.css` | — | `IMPLEMENTED_AND_CONNECTED` |
| CRM Command Center CSS | `apps/web/app/crm/crm-command-center.module.css` | — | `IMPLEMENTED_AND_CONNECTED` |

### 5.3 Command Center tab inventory (16 tabs)

| # | Tab id | Label key | Backed by | Status |
|---:|---|---|---|---|
| 1 | `overview` | `tab.overview` | `CrmOverview` — live `crmApi.dashboard()` fetch, KPI placeholders | `IMPLEMENTED_AND_CONNECTED` |
| 2 | `leads` | `tab.leads` | `CrmEmptyState` only | `PARTIALLY_IMPLEMENTED` |
| 3 | `customers` | `tab.customers` | `CrmEmptyState` only | `PARTIALLY_IMPLEMENTED` |
| 4 | `contacts` | `tab.contacts` | `CrmEmptyState` only | `PARTIALLY_IMPLEMENTED` |
| 5 | `opportunities` | `tab.opportunities` | `CrmEmptyState` only | `PARTIALLY_IMPLEMENTED` |
| 6 | `pipeline` | `tab.pipeline` | `CrmEmptyState` only (Kanban board exists but is not wired) | `PARTIALLY_IMPLEMENTED` |
| 7 | `tasks` | `tab.tasks` | `CrmEmptyState` only (G1 table not migrated) | `NOT_IMPLEMENTED` |
| 8 | `transfers` | `tab.transfers` | `CrmEmptyState` only (G1 table not migrated) | `NOT_IMPLEMENTED` |
| 9 | `employees` | `tab.employees` | `CrmEmptyState` only | `NOT_IMPLEMENTED` |
| 10 | `reports` | `tab.reports` | `CrmEmptyState` only | `NOT_IMPLEMENTED` |
| 11 | `mobileSync` | `tab.mobileSync` | `CrmEmptyState` only | `NOT_IMPLEMENTED` |
| 12 | `callerId` | `tab.callerId` | `CrmEmptyState` only (G1 table not migrated) | `NOT_IMPLEMENTED` |
| 13 | `aiCrm` | `tab.aiCrm` | `CrmEmptyState` only | `NOT_IMPLEMENTED` |
| 14 | `billing` | `tab.billing` | `CrmEmptyState` only | `NOT_IMPLEMENTED` |
| 15 | `settings` | `tab.settings` | `CrmEmptyState` only | `NOT_IMPLEMENTED` |
| 16 | `executionBoard` | `tab.executionBoard` | `CrmExecutionBoard` — live G0–G10 tracker | `IMPLEMENTED_AND_CONNECTED` |

A tab that renders `<CrmEmptyState subtitleKey={...}/>` is **not** a complete
feature. The drift check rejects any release note, README excerpt, or roadmap
entry that presents an empty-state-only tab as a delivered capability.

### 5.4 API client coverage

`apps/web/lib/api/crm.ts` exposes typed methods that map 1:1 to the backend
endpoints in §3.3, with the exception of imports and custom fields (those have
no UI surface yet). Methods implemented:

- `dashboard`, `accounts`, `createAccount`, `archiveAccount`, `restoreAccount`,
  `customer360`
- `contacts`, `createContact`, `archiveContact`, `restoreContact`
- `leads`, `createLead`, `changeLeadStatus`, `convertLead`
- `pipelines`, `createPipeline`, `stages`
- `opportunities`, `createOpportunity`, `moveOpportunity`
- `activities`, `createActivity`, `completeActivity`

Missing client methods: imports (`uploadImport`, `listImportJobs`,
`getImportJob`, `runImport`, `cancelImport`, `listImportErrors`,
`downloadImportErrors`) and custom fields (`createCustomField`,
`listCustomFields`, `upsertCustomFieldValues`, `readCustomFieldValues`,
`readSensitiveCustomFieldValues`, `searchCustomFieldValues`). These are tracked
under `EXEC-PROMPT-CRM-014` and `EXEC-PROMPT-CRM-016`.

---

## 6. Tests Baseline

### 6.1 Status

```text
BACKEND_TESTS: PARTIALLY_IMPLEMENTED
FRONTEND_TESTS: PARTIALLY_IMPLEMENTED
E2E_TESTS: NOT_IMPLEMENTED
MIGRATION_VALIDATION_TESTS: PARTIALLY_IMPLEMENTED
```

### 6.2 Backend test inventory

| Test class | Path | Test methods | Status |
|---|---|---:|---|
| `CrmApiIntegrationTest` | `apps/sanad-platform/src/test/java/com/sanad/platform/crm/web/CrmApiIntegrationTest.java` (226 lines) | 2 | `IMPLEMENTED_AND_CONNECTED` |
| `CrmImportAndCustomFieldIntegrationTest` | `apps/sanad-platform/src/test/java/com/sanad/platform/crm/web/CrmImportAndCustomFieldIntegrationTest.java` (215 lines) | 1 | `IMPLEMENTED_AND_CONNECTED` |
| `CrmPostgresMigrationTest` | `apps/sanad-platform/src/test/java/com/sanad/platform/crm/web/CrmPostgresMigrationTest.java` (166 lines) | 4 | `IMPLEMENTED_AND_CONNECTED` |
| `CrmXlsxImportIntegrationTest` | `apps/sanad-platform/src/test/java/com/sanad/platform/crm/web/CrmXlsxImportIntegrationTest.java` (135 lines) | 1 | `IMPLEMENTED_AND_CONNECTED` |

Total: 8 `@Test` methods across 4 classes. Coverage spans the full CRM
lifecycle (account → contact → pipeline → opportunity → activity → lead
conversion → customer-360 → dashboard), tenant isolation, PostgreSQL migration
clean-install and upgrade, custom-field encryption, and XLSX import.

### 6.3 Frontend test inventory

| Test file | Path | Status |
|---|---|---|
| `crm-interactions.test.tsx` | `apps/web/app/crm/crm-interactions.test.tsx` (111 lines) | `IMPLEMENTED_AND_CONNECTED` |

This is the **only** frontend CRM test on disk. It covers Command Center tab
switching and the empty-state contract. There are no component-level tests for
`CrmOverview`, `CrmExecutionBoard`, `CrmPipelineBoard`, or the API client.

### 6.4 End-to-end tests

There is **no** Playwright E2E test for the CRM surface. The
`apps/web/e2e/` directory contains visual-regression and bilingual-theme-matrix
specs only. A CRM E2E that proves the full login → `/crm` → dashboard → lead
creation → conversion → customer-360 flow against a live environment is tracked
as `EXEC-PROMPT-CRM-026`.

### 6.5 Migration validation tests

`CrmPostgresMigrationTest` covers clean-install and upgrade paths against
PostgreSQL via Testcontainers. **Missing**: a test that asserts the production
Flyway history table contains exactly the expected CRM versions
(`20260702.1`, `20260702.2`, `20260702.3`) — without this, manual application
of migrations on the production Supabase database can drift silently. Tracked
as `EXEC-PROMPT-CRM-020`.

---

## 7. CI / Workflow Baseline

### 7.1 Status

```text
CRM_SPECIFIC_CI: PARTIALLY_IMPLEMENTED
CRM_WORKFLOW_VERIFICATION: PARTIALLY_IMPLEMENTED
ISSUE_189_REFERENCED: NOT_IMPLEMENTED
```

### 7.2 CRM-specific workflow inventory

| Workflow | Path | Trigger | Status |
|---|---|---|---|
| `CRM Deployment Readiness` | `.github/workflows/crm-deployment-readiness.yml` (78 lines) | `pull_request` to `main`, `workflow_dispatch` | `PARTIALLY_IMPLEMENTED` — exists, not verified as a required status check |
| `CRM Real API Smoke` | `.github/workflows/crm-real-smoke.yml` (84 lines) | `workflow_dispatch` only | `PARTIALLY_IMPLEMENTED` — exists, not gated on every deployment |
| `CRM Web Lint Diagnostics` | `.github/workflows/crm-web-lint-diagnostics.yml` (47 lines) | `pull_request`, `workflow_dispatch` | `PARTIALLY_IMPLEMENTED` — captures lint output, but does not fail the build |
| `CI` (main pipeline) | `.github/workflows/ci.yml` (96 lines) | `push` to `main`, `pull_request` | `IMPLEMENTED_AND_CONNECTED` — runs Maven tests including CRM integration tests, but has no CRM-specific job |

### 7.3 Drift findings

- **No CRM-specific job in `ci.yml`.** The main CI pipeline runs the Maven
  suite, which includes CRM tests, but it does not surface a named "CRM" status
  check. Pull requests can pass CI without CRM tests being explicitly green if
  the surefire reporter rolls up to the Maven aggregate. Tracked as
  `EXEC-PROMPT-CRM-022`.
- **`crm-web-lint-diagnostics.yml` captures but does not block on lint.** The
  workflow always uploads `lint.log` as an artifact and only fails on a
  non-zero exit code in the final step; intermediate lint failures are
  preserved but not enforced as a hard gate. Tracked as
  `EXEC-PROMPT-CRM-024`.
- **`crm-real-smoke.yml` is `workflow_dispatch` only.** A production
  deployment does not automatically trigger a smoke run; an operator must
  invoke it with `base_url` and `expected_sha` inputs. Tracked as
  `EXEC-PROMPT-CRM-025`.
- **Issue #189 is not referenced in any workflow or doc.** The CRM inventory
  findings flag this. Tracked as `EXEC-PROMPT-CRM-023`.

---

## 8. Documentation Baseline

### 8.1 Status

```text
DOCUMENTATION: NEEDS_RECONCILIATION (in progress — this branch closes the gap)
```

### 8.2 Inventory and reconciliation

| Document | Path | Status |
|---|---|---|
| CRM README (top-level index) | `docs/crm/README.md` | Was `DOCUMENTED_ONLY` with false `CRM_PRODUCT_BUILD: NOT STARTED` claim. Reconciled on this branch to `IMPLEMENTED_AND_CONNECTED`. |
| CRM Current Baseline | `docs/crm/CRM-CURRENT-BASELINE.md` (this file) | `IMPLEMENTED_AND_CONNECTED` — new on this branch. |
| CRM Enterprise Execution Roadmap | `docs/crm/CRM-ENTERPRISE-EXECUTION-ROADMAP.md` | `IMPLEMENTED_AND_CONNECTED` — new on this branch. |
| CRM Gap Analysis | `docs/crm-gap-analysis.md` | Was accurate but stale. Updated on this branch to roll up against the new baseline. |
| CRM Readiness Assessment | `docs/crm-readiness-assessment.md` | Was accurate but stale. Updated on this branch to roll up against the new baseline. |
| CRM Deployment Readiness Contract | `docs/crm/CRM-DEPLOYMENT-READINESS.md` | `IMPLEMENTED_AND_CONNECTED`. Cites issues #173, #197, #29 as independent release gates. |
| CRM UI License Review | `docs/crm/CRM-UI-LICENSE-REVIEW.md` | `IMPLEMENTED_AND_CONNECTED`. |
| CRM Test and Quality Plan | `docs/crm/CRM-TEST-AND-QUALITY-PLAN.md` | `IMPLEMENTED_AND_CONNECTED`. |
| CRM Review Checklist | `docs/crm/CRM-REVIEW-CHECKLIST.md` | `IMPLEMENTED_AND_CONNECTED`. |
| CRM MVP Execution Backlog | `docs/crm/CRM-MVP-EXECUTION-BACKLOG.md` | `DOCUMENTED_ONLY` — pre-execution plan; still says "Implementation: NOT STARTED", which is now stale. Tracked for refresh under `EXEC-PROMPT-CRM-002`. |
| CRM Domain and Service Boundaries | `docs/crm/CRM-DOMAIN-AND-SERVICE-BOUNDARIES.md` | `DOCUMENTED_ONLY` — design reference. |
| CRM Data / API / Event Contract | `docs/crm/CRM-DATA-API-EVENT-CONTRACT.md` | `DOCUMENTED_ONLY` — design reference. |
| CRM Readiness Gate | `docs/crm/CRM-READINESS-GATE.md` | `DOCUMENTED_ONLY` — gate conditions. |
| CRM Integration Worklog 20260702 | `docs/crm/CRM-INTEGRATION-WORKLOG-20260702.md` | `IMPLEMENTED_AND_CONNECTED` — historical record. |
| CRM Build Decision | `docs/crm/CRM-BUILD-DECISION.md` | `DOCUMENTED_ONLY` — original decision record. |
| CRM Global Build Reference | `docs/crm/CRM-GLOBAL-BUILD-REFERENCE.md` | `DOCUMENTED_ONLY` — product scope reference. |

---

## 9. Production Status

### 9.1 Runtime topology

| Layer | Provider | Notes |
|---|---|---|
| Backend | Self-hosted server | Migrated off Render per `1ed75ee8`. |
| Frontend | Vercel | Next.js app. |
| Database | Supabase (PostgreSQL) | CRM core migrations applied manually. |
| Flyway | `FLYWAY_ENABLED=false` on production | Migrations applied manually per `CRM-DEPLOYMENT-READINESS.md` §"Database deployment". Preflight enforces `FLYWAY_ENABLED=true` for staging/CI. |

### 9.2 Live verification

The CRM real smoke workflow proves the following against the live backend:

- Authenticated dashboard is reachable.
- Account create + read works.
- Contact linked to account works.
- Pipeline create + stages list works.
- Opportunity create + transition to Won works.
- Activity create + complete works.
- Lead create + qualify + convert works.
- Customer 360 + timeline works.
- Dashboard counts reflect created records.
- Tenant isolation: second tenant receives HTTP `404` on the first tenant's
  account ID, and the first tenant's account is absent from the second
  tenant's account list.

### 9.3 Control-plane operations

Control-panel operations (tenant creation, organization creation, membership
creation) are verified working and are not part of the CRM product surface.
They are out of scope for this baseline.

---

## 10. Open Risks and Drift Items

The following drift items are tracked as roadmap execution prompts in
`docs/crm/CRM-ENTERPRISE-EXECUTION-ROADMAP.md`. Each must be closed before the
relevant gate.

| Drift item | Severity | Roadmap prompt |
|---|---|---|
| `docs/crm/README.md` historically claimed `CRM_PRODUCT_BUILD: NOT STARTED` while code was merged. | High | `EXEC-PROMPT-CRM-001` (closed by this branch). |
| No CRM-specific job in main `ci.yml`. | High | `EXEC-PROMPT-CRM-022`. |
| No E2E for the CRM surface. | High | `EXEC-PROMPT-CRM-026`. |
| No Flyway-history assertion test for production Supabase. | High | `EXEC-PROMPT-CRM-020`. |
| `crm-web-lint-diagnostics.yml` does not block on lint failure. | Medium | `EXEC-PROMPT-CRM-024`. |
| `crm-real-smoke.yml` is `workflow_dispatch` only; not gated on every deployment. | Medium | `EXEC-PROMPT-CRM-025`. |
| Issue #189 is not referenced in any workflow or CRM doc. | Medium | `EXEC-PROMPT-CRM-023`. |
| No row-level security on CRM tables; isolation is application-layer only. | Medium | `EXEC-PROMPT-CRM-018`. |
| G1 extension tables (`crm_tasks`, `crm_notes`, etc.) referenced by Execution Board but no migration on `main`. | Medium | `EXEC-PROMPT-CRM-008`. |
| 12 of 16 Command Center tabs are empty-state-only. | Medium | `EXEC-PROMPT-CRM-014` through `EXEC-PROMPT-CRM-017`. |
| Frontend API client missing imports + custom-fields methods. | Medium | `EXEC-PROMPT-CRM-014`, `EXEC-PROMPT-CRM-016`. |
| `CRM-MVP-EXECUTION-BACKLOG.md` still says "Implementation: NOT STARTED". | Low | `EXEC-PROMPT-CRM-002`. |
| No formal production GO decision record for commercial launch. | High | `EXEC-PROMPT-CRM-031`. |

---

## 11. Glossary

- **Baseline SHA** — the exact `main` commit at which this document was
  reconciled. Any change to CRM source after this SHA requires a new
  reconciliation commit that updates this file and the roadmap.
- **Drift** — any state where a CRM document, workflow, or runtime artifact
  contradicts the as-built code on `main`. Detected by
  `scripts/crm/governance-drift-check.sh`.
- **GO decision record** — a written, externally-approved authorization to
  commercialize. Distinct from a deployment being live.
- **Empty-state-only tab** — a Command Center tab whose default `renderContent`
  branch resolves to `<CrmEmptyState/>`. Such a tab is not a delivered feature.

---

## 12. Change Log

| Date | Branch | Author | Change |
|---|---|---|---|
| 2026-07-12 | `crm/001-baseline-governance-ci-recovery` | CRM governance squad | Initial baseline creation. Reconciled against `cee332e7`. Supersedes `CRM_PRODUCT_BUILD: NOT STARTED` claim in `docs/crm/README.md`. |
