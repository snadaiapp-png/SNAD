# CRM Gap Analysis

> **Branch:** `crm/001-baseline-governance-ci-recovery`
> **Baseline SHA:** `cee332e7f86a6ea64fbb5f72120ae77c441f6eac`
> **Companion documents:**
> - `docs/crm/CRM-CURRENT-BASELINE.md` — authoritative as-built baseline.
> - `docs/crm/CRM-ENTERPRISE-EXECUTION-ROADMAP.md` — forward execution plan.
> **Status codes:** inherited from `CRM-CURRENT-BASELINE.md` §1.

This gap analysis rolls the as-built CRM state (per
`CRM-CURRENT-BASELINE.md`) against the target enterprise CRM product. Every
row references the execution prompt in `CRM-ENTERPRISE-EXECUTION-ROADMAP.md`
that closes the gap.

---

## 1. Environment Readiness Assessment

| Requirement | Status | Notes |
|---|---|---|
| Unified Tenant Context | `IMPLEMENTED_AND_CONNECTED` | `TenantContextProvider` + `TenantContextFilter` enforced on every CRM endpoint. |
| Authentication & Authorization | `IMPLEMENTED_AND_CONNECTED` | JWT + RBAC + `@RequireCapability`. 18 active `CRM.*` capabilities seeded by `V20260702_1` and `V20260702_3`. |
| Entity-level RBAC | `IMPLEMENTED_AND_CONNECTED` | Capability-based with tenant scoping; every CRM query filters on `tenant_id`. |
| API Standards | `IMPLEMENTED_AND_CONNECTED` | RESTful, unified error handling, `problem+json`. |
| Validation Framework | `IMPLEMENTED_AND_CONNECTED` | Jakarta Bean Validation via `CrmModels` records. |
| Error Handling | `IMPLEMENTED_AND_CONNECTED` | `GlobalExceptionHandler` with safe messages. |
| Audit Logging | `PARTIALLY_IMPLEMENTED` | Platform `platform_audit_logs` (V17) exists; CRM-specific `crm_audit_logs` is part of the G1 extension tables (`EXEC-PROMPT-CRM-008`) and not yet migrated. |
| Notifications | `NOT_IMPLEMENTED` | No notification framework for CRM events. Tracked as future work outside the current roadmap. |
| File Attachments | `NOT_IMPLEMENTED` | No file upload/storage for CRM entities. |
| Search and Filtering | `PARTIALLY_IMPLEMENTED` | Basic filtering on every list endpoint; full-text search is `NOT_IMPLEMENTED`. Custom-field search via `crm_custom_field_values.searchable_value` is `IMPLEMENTED_AND_CONNECTED`. |
| Pagination and Sorting | `IMPLEMENTED_AND_CONNECTED` | Cursor-based pagination, sort support on every list endpoint. |
| Custom Fields | `IMPLEMENTED_AND_CONNECTED` | Definitions, values, AES-GCM encryption for sensitive fields, searchable values, sensitive-value read gate (`CRM.ADMIN`). API + DB + tests are connected. Frontend client methods are `NOT_STARTED` (`EXEC-PROMPT-CRM-016`). |
| Tags | `NOT_IMPLEMENTED` | No tag/label system. |
| Activity Timeline | `IMPLEMENTED_AND_CONNECTED` | `crm_timeline_events` + `customer-360` endpoint + `timeline` endpoint. |
| Workflow Hooks | `NOT_IMPLEMENTED` | No workflow/automation engine. |
| Event Bus | `NOT_IMPLEMENTED` | No event publishing/subscribing. |
| Background Jobs | `IMPLEMENTED_AND_CONNECTED` | Lease-based import worker with `SANAD_CRM_IMPORT_WORKER_*` controls. |
| Reporting | `NOT_IMPLEMENTED` | No report generation framework. Tracked as `EXEC-PROMPT-CRM-025`. |
| Import/Export | `PARTIALLY_IMPLEMENTED` | Import is `IMPLEMENTED_AND_CONNECTED` (XLSX/CSV upload, validation, run, cancel, error CSV). Export is `NOT_IMPLEMENTED`. |
| Localization (Arabic) | `PARTIALLY_IMPLEMENTED` | Frontend `crm-i18n.tsx` provides Arabic/English dictionaries and RTL/LTR. Backend messages are mixed. |
| Timezone Support | `IMPLEMENTED_AND_CONNECTED` | UTC storage, timezone-aware display via `time_zone` columns on `crm_accounts` and `crm_contacts`. |
| Reference Data | `IMPLEMENTED_AND_CONNECTED` | `access_capabilities`, `saas_plans` seeded. |
| Scalability | `IMPLEMENTED_AND_CONNECTED` | Stateless backend, HikariCP pooling, Testcontainers-verified migrations. |
| Performance | `PARTIALLY_IMPLEMENTED` | Indexes on critical paths; no load-test baseline. Tracked as `EXEC-PROMPT-CRM-033`. |

---

## 2. CRM Feature Gap Matrix

Status codes: `IMPLEMENTED_AND_CONNECTED`, `IMPLEMENTED_NOT_CONNECTED`,
`PARTIALLY_IMPLEMENTED`, `DOCUMENTED_ONLY`, `NOT_IMPLEMENTED`, `BLOCKED`,
`DEPRECATED`, `SUPERSEDED`.

| Feature | Backend | Frontend | Database | Overall status | Closing prompt |
|---|---|---|---|---|---|
| Accounts (Companies) | `IMPLEMENTED_AND_CONNECTED` | `PARTIALLY_IMPLEMENTED` (empty-state tab) | `IMPLEMENTED_AND_CONNECTED` | `PARTIALLY_IMPLEMENTED` | `EXEC-PROMPT-CRM-015` |
| Contacts | `IMPLEMENTED_AND_CONNECTED` | `PARTIALLY_IMPLEMENTED` (empty-state tab) | `IMPLEMENTED_AND_CONNECTED` | `PARTIALLY_IMPLEMENTED` | `EXEC-PROMPT-CRM-016` |
| Leads | `IMPLEMENTED_AND_CONNECTED` | `PARTIALLY_IMPLEMENTED` (empty-state tab) | `IMPLEMENTED_AND_CONNECTED` | `PARTIALLY_IMPLEMENTED` | `EXEC-PROMPT-CRM-014` |
| Sales Pipeline | `IMPLEMENTED_AND_CONNECTED` | `PARTIALLY_IMPLEMENTED` (empty-state tab) | `IMPLEMENTED_AND_CONNECTED` | `PARTIALLY_IMPLEMENTED` | `EXEC-PROMPT-CRM-020` |
| Opportunities | `IMPLEMENTED_AND_CONNECTED` | `PARTIALLY_IMPLEMENTED` (empty-state tab) | `IMPLEMENTED_AND_CONNECTED` | `PARTIALLY_IMPLEMENTED` | `EXEC-PROMPT-CRM-019` |
| Activities | `IMPLEMENTED_AND_CONNECTED` | `PARTIALLY_IMPLEMENTED` (no dedicated tab) | `IMPLEMENTED_AND_CONNECTED` | `PARTIALLY_IMPLEMENTED` | `EXEC-PROMPT-CRM-017` |
| Customer Timeline | `IMPLEMENTED_AND_CONNECTED` | `PARTIALLY_IMPLEMENTED` (no UI surface) | `IMPLEMENTED_AND_CONNECTED` | `PARTIALLY_IMPLEMENTED` | `EXEC-PROMPT-CRM-017` |
| Import/Export | `IMPLEMENTED_AND_CONNECTED` (import) | `NOT_IMPLEMENTED` | `IMPLEMENTED_AND_CONNECTED` | `PARTIALLY_IMPLEMENTED` | `EXEC-PROMPT-CRM-016` |
| Custom Fields | `IMPLEMENTED_AND_CONNECTED` | `NOT_IMPLEMENTED` (no client methods) | `IMPLEMENTED_AND_CONNECTED` | `PARTIALLY_IMPLEMENTED` | `EXEC-PROMPT-CRM-016` |
| Dashboard | `IMPLEMENTED_AND_CONNECTED` | `IMPLEMENTED_AND_CONNECTED` | — | `IMPLEMENTED_AND_CONNECTED` | — |
| Tasks | `NOT_IMPLEMENTED` | `NOT_IMPLEMENTED` (empty-state tab) | `NOT_IMPLEMENTED` (G1 table not migrated) | `NOT_IMPLEMENTED` | `EXEC-PROMPT-CRM-008`, `EXEC-PROMPT-CRM-021` |
| Notes | `NOT_IMPLEMENTED` | `NOT_IMPLEMENTED` | `NOT_IMPLEMENTED` (G1 table not migrated) | `NOT_IMPLEMENTED` | `EXEC-PROMPT-CRM-008` |
| Transfers | `NOT_IMPLEMENTED` | `NOT_IMPLEMENTED` (empty-state tab) | `NOT_IMPLEMENTED` (G1 table not migrated) | `NOT_IMPLEMENTED` | `EXEC-PROMPT-CRM-008`, `EXEC-PROMPT-CRM-023` |
| Employees | `NOT_IMPLEMENTED` | `NOT_IMPLEMENTED` (empty-state tab) | `NOT_IMPLEMENTED` | `NOT_IMPLEMENTED` | `EXEC-PROMPT-CRM-023` |
| Communications | `NOT_IMPLEMENTED` | `NOT_IMPLEMENTED` | `NOT_IMPLEMENTED` (G1 phone_numbers table not migrated) | `NOT_IMPLEMENTED` | `EXEC-PROMPT-CRM-008` |
| Products/Services | `NOT_IMPLEMENTED` | `NOT_IMPLEMENTED` | `NOT_IMPLEMENTED` | `NOT_IMPLEMENTED` | — |
| Quotations | `NOT_IMPLEMENTED` | `NOT_IMPLEMENTED` | `NOT_IMPLEMENTED` | `NOT_IMPLEMENTED` | — |
| Sales Orders | `NOT_IMPLEMENTED` | `NOT_IMPLEMENTED` | `NOT_IMPLEMENTED` | `NOT_IMPLEMENTED` | — |
| Reports | `NOT_IMPLEMENTED` | `NOT_IMPLEMENTED` (empty-state tab) | `NOT_IMPLEMENTED` (G1 reports table not migrated) | `NOT_IMPLEMENTED` | `EXEC-PROMPT-CRM-008`, `EXEC-PROMPT-CRM-025` |
| Mobile Sync | `NOT_IMPLEMENTED` | `NOT_IMPLEMENTED` (empty-state tab) | `NOT_IMPLEMENTED` | `NOT_IMPLEMENTED` | — |
| Caller ID | `NOT_IMPLEMENTED` | `NOT_IMPLEMENTED` (empty-state tab) | `NOT_IMPLEMENTED` (G1 contact_lookup_index not migrated) | `NOT_IMPLEMENTED` | `EXEC-PROMPT-CRM-008` |
| AI CRM | `NOT_IMPLEMENTED` | `NOT_IMPLEMENTED` (empty-state tab) | `NOT_IMPLEMENTED` | `NOT_IMPLEMENTED` | — |
| Billing | `NOT_IMPLEMENTED` | `NOT_IMPLEMENTED` (empty-state tab) | `PARTIALLY_IMPLEMENTED` (`subscription_change_events` exists) | `PARTIALLY_IMPLEMENTED` | — |
| Settings | `NOT_IMPLEMENTED` | `NOT_IMPLEMENTED` (empty-state tab) | — | `NOT_IMPLEMENTED` | — |
| Workflow Integration | `NOT_IMPLEMENTED` | `NOT_IMPLEMENTED` | `NOT_IMPLEMENTED` | `NOT_IMPLEMENTED` | — |
| Row-Level Security | `NOT_IMPLEMENTED` | — | `NOT_IMPLEMENTED` | `NOT_IMPLEMENTED` | `EXEC-PROMPT-CRM-018` |

---

## 3. CI/CD Gap Matrix

| Workflow | Status | Gap | Closing prompt |
|---|---|---|---|
| `ci.yml` (main pipeline) | `IMPLEMENTED_AND_CONNECTED` | No CRM-specific job; CRM tests roll up into the Maven aggregate. | `EXEC-PROMPT-CRM-022` |
| `crm-deployment-readiness.yml` | `PARTIALLY_IMPLEMENTED` | Exists; not verified as a required status check. | `EXEC-PROMPT-CRM-030` |
| `crm-real-smoke.yml` | `PARTIALLY_IMPLEMENTED` | `workflow_dispatch` only; not gated on every production deploy. | `EXEC-PROMPT-CRM-027` |
| `crm-web-lint-diagnostics.yml` | `PARTIALLY_IMPLEMENTED` | Captures lint output but does not block on lint failure. | `EXEC-PROMPT-CRM-024` |
| Issue #189 reference | `NOT_IMPLEMENTED` | Issue #189 is not referenced in any workflow or CRM doc. | `EXEC-PROMPT-CRM-029` |

---

## 4. Test Gap Matrix

| Test type | Status | Gap | Closing prompt |
|---|---|---|---|
| Backend integration (CRM lifecycle) | `IMPLEMENTED_AND_CONNECTED` | 2 tests in `CrmApiIntegrationTest`. | — |
| Backend integration (imports + custom fields) | `IMPLEMENTED_AND_CONNECTED` | 1 test in `CrmImportAndCustomFieldIntegrationTest`. | — |
| Backend integration (XLSX import) | `IMPLEMENTED_AND_CONNECTED` | 1 test in `CrmXlsxImportIntegrationTest`. | — |
| Backend migration (PostgreSQL clean + upgrade) | `IMPLEMENTED_AND_CONNECTED` | 4 tests in `CrmPostgresMigrationTest`. | — |
| Frontend component | `PARTIALLY_IMPLEMENTED` | 1 test file (`crm-interactions.test.tsx`). No tests for `CrmOverview`, `CrmExecutionBoard`, `CrmPipelineBoard`, or API client. | — |
| End-to-end | `NOT_IMPLEMENTED` | No Playwright E2E for CRM. | `EXEC-PROMPT-CRM-026` |
| Flyway history assertion | `NOT_IMPLEMENTED` | No test asserting the production Supabase Flyway history matches the expected CRM versions. | `EXEC-PROMPT-CRM-028` |
| Performance | `NOT_IMPLEMENTED` | No load-test baseline. | `EXEC-PROMPT-CRM-033` |
| Accessibility | `NOT_IMPLEMENTED` | No axe-core audit. | `EXEC-PROMPT-CRM-034` |
| Penetration test | `NOT_IMPLEMENTED` | No CRM-specific pentest report. | `EXEC-PROMPT-CRM-032` |

---

## 5. Documentation Gap Matrix

| Document | Status | Gap | Closing prompt |
|---|---|---|---|
| `docs/crm/CRM-CURRENT-BASELINE.md` | `IMPLEMENTED_AND_CONNECTED` | New on this branch. | — |
| `docs/crm/CRM-ENTERPRISE-EXECUTION-ROADMAP.md` | `IMPLEMENTED_AND_CONNECTED` | New on this branch. | — |
| `docs/crm/README.md` | `IMPLEMENTED_AND_CONNECTED` | Reconciled on this branch; no longer claims `NOT STARTED`. | — |
| `docs/crm-gap-analysis.md` (this file) | `IMPLEMENTED_AND_CONNECTED` | Reconciled on this branch. | — |
| `docs/crm-readiness-assessment.md` | `IMPLEMENTED_AND_CONNECTED` | Reconciled on this branch. | — |
| `docs/crm/CRM-DEPLOYMENT-READINESS.md` | `IMPLEMENTED_AND_CONNECTED` | Cites 14 CRM capabilities — should be updated to 18. | `EXEC-PROMPT-CRM-002` |
| `docs/crm/CRM-MVP-EXECUTION-BACKLOG.md` | `DOCUMENTED_ONLY` | Still says "Implementation: NOT STARTED". Stale. | `EXEC-PROMPT-CRM-002` |
| `docs/crm/CRM-GLOBAL-BUILD-REFERENCE.md` | `DOCUMENTED_ONLY` | Design reference; not stale but not reconciled against the baseline. | — |
| `docs/crm/CRM-DOMAIN-AND-SERVICE-BOUNDARIES.md` | `DOCUMENTED_ONLY` | Design reference. | — |
| `docs/crm/CRM-DATA-API-EVENT-CONTRACT.md` | `DOCUMENTED_ONLY` | Design reference. | — |
| `docs/crm/CRM-READINESS-GATE.md` | `DOCUMENTED_ONLY` | Gate conditions; closed by this branch. | — |
| `docs/crm/CRM-INTEGRATION-WORKLOG-20260702.md` | `IMPLEMENTED_AND_CONNECTED` | Historical record. | — |
| `docs/crm/CRM-BUILD-DECISION.md` | `DOCUMENTED_ONLY` | Original decision record. | — |
| `docs/crm/CRM-TEST-AND-QUALITY-PLAN.md` | `IMPLEMENTED_AND_CONNECTED` | Test strategy. | — |
| `docs/crm/CRM-REVIEW-CHECKLIST.md` | `IMPLEMENTED_AND_CONNECTED` | Code-review checklist. | — |
| `docs/crm/CRM-UI-LICENSE-REVIEW.md` | `IMPLEMENTED_AND_CONNECTED` | License review. | — |

---

## 6. Final Assessment

```
CRM Environment Readiness: IMPLEMENTED_AND_CONNECTED (core)
CRM Product Completeness:  PARTIALLY_IMPLEMENTED (12 of 16 Command Center tabs are empty-state-only)

The platform provides:
  - Solid multi-tenant foundation with application-layer tenant isolation.
  - JWT authentication with session versioning.
  - RBAC with 18 fine-grained CRM capabilities.
  - SaaS subscription/plan management.
  - CRM core entities (accounts, contacts, leads, opportunities, activities).
  - API-first architecture with 30+ CRM endpoints under /api/v1/crm/*.
  - CRM Command Center deployed to Vercel with 16 tabs (2 with real content).
  - Lease-based import worker with XLSX/CSV support and error reporting.
  - Custom-field framework with AES-GCM encryption for sensitive values.
  - Customer 360 timeline.
  - Authenticated two-tenant production smoke workflow.

Missing for full enterprise CRM:
  - G1 extension tables (tasks, notes, transfers, audit logs, reports,
    phone_numbers, contact_lookup_index) — migration not yet on main.
  - Row-Level Security as defense-in-depth.
  - 14 of 16 Command Center tabs are empty-state placeholders.
  - Frontend API client methods for imports and custom fields.
  - E2E tests, Flyway history assertion test, performance baseline,
    accessibility audit, and CRM-specific penetration test.
  - CRM-specific job in ci.yml; CRM workflows not verified as required
    status checks; crm-real-smoke.yml not gated on every deploy.
  - Issue #189 not referenced anywhere.
  - Formal production GO decision record.
```

The complete list of execution prompts that close these gaps is in
`docs/crm/CRM-ENTERPRISE-EXECUTION-ROADMAP.md`.
