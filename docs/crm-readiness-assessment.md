# CRM Readiness Assessment

> **Branch:** `crm/001-baseline-governance-ci-recovery`
> **Baseline SHA:** `cee332e7f86a6ea64fbb5f72120ae77c441f6eac`
> **Companion documents:**
> - `docs/crm/CRM-CURRENT-BASELINE.md` — authoritative as-built baseline.
> - `docs/crm/CRM-ENTERPRISE-EXECUTION-ROADMAP.md` — forward execution plan.
> - `docs/crm-gap-analysis.md` — environment and feature gap matrix.
> **Status codes:** inherited from `CRM-CURRENT-BASELINE.md` §1.

This readiness assessment rolls up the per-component status of every CRM
surface against the enterprise readiness bar. Each row points to the
execution prompt in `CRM-ENTERPRISE-EXECUTION-ROADMAP.md` that closes the
remaining gap.

---

## 1. CRM Components Status

| Component | Backend | Frontend | Database | Overall | Closing prompt |
|---|---|---|---|---|---|
| Accounts (Companies) | `IMPLEMENTED_AND_CONNECTED` | `PARTIALLY_IMPLEMENTED` | `IMPLEMENTED_AND_CONNECTED` | `PARTIALLY_IMPLEMENTED` | `EXEC-PROMPT-CRM-015` |
| Contacts | `IMPLEMENTED_AND_CONNECTED` | `PARTIALLY_IMPLEMENTED` | `IMPLEMENTED_AND_CONNECTED` | `PARTIALLY_IMPLEMENTED` | `EXEC-PROMPT-CRM-016` |
| Leads | `IMPLEMENTED_AND_CONNECTED` | `PARTIALLY_IMPLEMENTED` | `IMPLEMENTED_AND_CONNECTED` | `PARTIALLY_IMPLEMENTED` | `EXEC-PROMPT-CRM-014` |
| Sales Pipeline | `IMPLEMENTED_AND_CONNECTED` | `PARTIALLY_IMPLEMENTED` | `IMPLEMENTED_AND_CONNECTED` | `PARTIALLY_IMPLEMENTED` | `EXEC-PROMPT-CRM-020` |
| Opportunities | `IMPLEMENTED_AND_CONNECTED` | `PARTIALLY_IMPLEMENTED` | `IMPLEMENTED_AND_CONNECTED` | `PARTIALLY_IMPLEMENTED` | `EXEC-PROMPT-CRM-019` |
| Activities | `IMPLEMENTED_AND_CONNECTED` | `PARTIALLY_IMPLEMENTED` | `IMPLEMENTED_AND_CONNECTED` | `PARTIALLY_IMPLEMENTED` | `EXEC-PROMPT-CRM-017` |
| Customer Timeline | `IMPLEMENTED_AND_CONNECTED` | `PARTIALLY_IMPLEMENTED` | `IMPLEMENTED_AND_CONNECTED` | `PARTIALLY_IMPLEMENTED` | `EXEC-PROMPT-CRM-017` |
| Import/Export | `IMPLEMENTED_AND_CONNECTED` (import only) | `NOT_IMPLEMENTED` | `IMPLEMENTED_AND_CONNECTED` | `PARTIALLY_IMPLEMENTED` | `EXEC-PROMPT-CRM-016` |
| Custom Fields | `IMPLEMENTED_AND_CONNECTED` | `NOT_IMPLEMENTED` | `IMPLEMENTED_AND_CONNECTED` | `PARTIALLY_IMPLEMENTED` | `EXEC-PROMPT-CRM-016` |
| Dashboard | `IMPLEMENTED_AND_CONNECTED` | `IMPLEMENTED_AND_CONNECTED` | — | `IMPLEMENTED_AND_CONNECTED` | — |
| Tasks | `NOT_IMPLEMENTED` | `NOT_IMPLEMENTED` | `NOT_IMPLEMENTED` (G1 table not migrated) | `NOT_IMPLEMENTED` | `EXEC-PROMPT-CRM-008`, `EXEC-PROMPT-CRM-021` |
| Notes | `NOT_IMPLEMENTED` | `NOT_IMPLEMENTED` | `NOT_IMPLEMENTED` (G1 table not migrated) | `NOT_IMPLEMENTED` | `EXEC-PROMPT-CRM-008` |
| Transfers | `NOT_IMPLEMENTED` | `NOT_IMPLEMENTED` | `NOT_IMPLEMENTED` (G1 table not migrated) | `NOT_IMPLEMENTED` | `EXEC-PROMPT-CRM-008`, `EXEC-PROMPT-CRM-023` |
| Employees | `NOT_IMPLEMENTED` | `NOT_IMPLEMENTED` | `NOT_IMPLEMENTED` | `NOT_IMPLEMENTED` | `EXEC-PROMPT-CRM-023` |
| Communications | `NOT_IMPLEMENTED` | `NOT_IMPLEMENTED` | `NOT_IMPLEMENTED` (G1 phone_numbers not migrated) | `NOT_IMPLEMENTED` | `EXEC-PROMPT-CRM-008` |
| Products/Services | `NOT_IMPLEMENTED` | `NOT_IMPLEMENTED` | `NOT_IMPLEMENTED` | `NOT_IMPLEMENTED` | — |
| Quotations | `NOT_IMPLEMENTED` | `NOT_IMPLEMENTED` | `NOT_IMPLEMENTED` | `NOT_IMPLEMENTED` | — |
| Sales Orders | `NOT_IMPLEMENTED` | `NOT_IMPLEMENTED` | `NOT_IMPLEMENTED` | `NOT_IMPLEMENTED` | — |
| Reports | `NOT_IMPLEMENTED` | `NOT_IMPLEMENTED` | `NOT_IMPLEMENTED` (G1 reports table not migrated) | `NOT_IMPLEMENTED` | `EXEC-PROMPT-CRM-008`, `EXEC-PROMPT-CRM-025` |
| Mobile Sync | `NOT_IMPLEMENTED` | `NOT_IMPLEMENTED` | `NOT_IMPLEMENTED` | `NOT_IMPLEMENTED` | — |
| Caller ID | `NOT_IMPLEMENTED` | `NOT_IMPLEMENTED` | `NOT_IMPLEMENTED` (G1 contact_lookup_index not migrated) | `NOT_IMPLEMENTED` | `EXEC-PROMPT-CRM-008` |
| AI Integration | `NOT_IMPLEMENTED` | `NOT_IMPLEMENTED` | `NOT_IMPLEMENTED` | `NOT_IMPLEMENTED` | — |
| Workflow Integration | `NOT_IMPLEMENTED` | `NOT_IMPLEMENTED` | `NOT_IMPLEMENTED` | `NOT_IMPLEMENTED` | — |
| Settings | `NOT_IMPLEMENTED` | `NOT_IMPLEMENTED` | — | `NOT_IMPLEMENTED` | — |
| Row-Level Security | — | — | `NOT_IMPLEMENTED` | `NOT_IMPLEMENTED` | `EXEC-PROMPT-CRM-018` |

---

## 2. CRM API Endpoints

### 2.1 Fully implemented and connected (43 endpoints)

```
GET    /api/v1/crm/dashboard
POST   /api/v1/crm/accounts
GET    /api/v1/crm/accounts
GET    /api/v1/crm/accounts/{accountId}
GET    /api/v1/crm/accounts/{accountId}/customer-360
PATCH  /api/v1/crm/accounts/{accountId}
PATCH  /api/v1/crm/accounts/{accountId}/archive
PATCH  /api/v1/crm/accounts/{accountId}/restore
POST   /api/v1/crm/contacts
GET    /api/v1/crm/contacts
GET    /api/v1/crm/contacts/{contactId}
PATCH  /api/v1/crm/contacts/{contactId}
PATCH  /api/v1/crm/contacts/{contactId}/archive
PATCH  /api/v1/crm/contacts/{contactId}/restore
POST   /api/v1/crm/leads
GET    /api/v1/crm/leads
GET    /api/v1/crm/leads/{leadId}
PATCH  /api/v1/crm/leads/{leadId}/status
POST   /api/v1/crm/leads/{leadId}/convert
POST   /api/v1/crm/pipelines
GET    /api/v1/crm/pipelines
GET    /api/v1/crm/pipelines/{pipelineId}/stages
POST   /api/v1/crm/opportunities
GET    /api/v1/crm/opportunities
GET    /api/v1/crm/opportunities/{opportunityId}
PATCH  /api/v1/crm/opportunities/{opportunityId}/stage
POST   /api/v1/crm/activities
GET    /api/v1/crm/activities
GET    /api/v1/crm/activities/{activityId}
PATCH  /api/v1/crm/activities/{activityId}/complete
GET    /api/v1/crm/timeline/{subjectType}/{subjectId}
POST   /api/v1/crm/imports/upload            (multipart)
GET    /api/v1/crm/imports
GET    /api/v1/crm/imports/{jobId}
POST   /api/v1/crm/imports/{jobId}/run
POST   /api/v1/crm/imports/{jobId}/cancel
GET    /api/v1/crm/imports/{jobId}/errors
GET    /api/v1/crm/imports/{jobId}/errors.csv
POST   /api/v1/crm/custom-fields
GET    /api/v1/crm/custom-fields
PUT    /api/v1/crm/custom-fields/values/{entityType}/{entityId}
GET    /api/v1/crm/custom-fields/values/{entityType}/{entityId}
GET    /api/v1/crm/custom-fields/values/{entityType}/{entityId}/sensitive
GET    /api/v1/crm/custom-fields/search
```

The previously documented count of "25 endpoints" is stale; the reconciled
count is **43**. The drift-check script does not enforce this count today but
any future audit should use 43 as the baseline.

---

## 3. CRM Database Tables

| Table | Source migration | Tenant-Owned | Has `tenant_id` | RLS Policy | Status |
|---|---|---|---|---|---|
| `crm_accounts` | `V20260702_1` | YES | YES | `NOT_IMPLEMENTED` | `IMPLEMENTED_AND_CONNECTED` |
| `crm_contacts` | `V20260702_1` | YES | YES | `NOT_IMPLEMENTED` | `IMPLEMENTED_AND_CONNECTED` |
| `crm_pipelines` | `V20260702_1` | YES | YES | `NOT_IMPLEMENTED` | `IMPLEMENTED_AND_CONNECTED` |
| `crm_pipeline_stages` | `V20260702_1` | YES | YES | `NOT_IMPLEMENTED` | `IMPLEMENTED_AND_CONNECTED` |
| `crm_leads` | `V20260702_1` | YES | YES | `NOT_IMPLEMENTED` | `IMPLEMENTED_AND_CONNECTED` |
| `crm_opportunities` | `V20260702_1` | YES | YES | `NOT_IMPLEMENTED` | `IMPLEMENTED_AND_CONNECTED` |
| `crm_opportunity_stage_history` | `V20260702_1` | YES | YES | `NOT_IMPLEMENTED` | `IMPLEMENTED_AND_CONNECTED` |
| `crm_activities` | `V20260702_1` | YES | YES | `NOT_IMPLEMENTED` | `IMPLEMENTED_AND_CONNECTED` |
| `crm_timeline_events` | `V20260702_1` | YES | YES | `NOT_IMPLEMENTED` | `IMPLEMENTED_AND_CONNECTED` |
| `crm_import_jobs` | `V20260702_1` (extended by `V20260702_3`) | YES | YES | `NOT_IMPLEMENTED` | `IMPLEMENTED_AND_CONNECTED` |
| `crm_custom_field_definitions` | `V20260702_1` (extended by `V20260702_3`) | YES | YES | `NOT_IMPLEMENTED` | `IMPLEMENTED_AND_CONNECTED` |
| `crm_import_files` | `V20260702_3` | YES | YES | `NOT_IMPLEMENTED` | `IMPLEMENTED_AND_CONNECTED` |
| `crm_import_errors` | `V20260702_3` | YES | YES | `NOT_IMPLEMENTED` | `IMPLEMENTED_AND_CONNECTED` |
| `crm_custom_field_values` | `V20260702_3` | YES | YES | `NOT_IMPLEMENTED` | `IMPLEMENTED_AND_CONNECTED` |
| `crm_tasks` | (planned, `EXEC-PROMPT-CRM-008`) | — | — | — | `NOT_IMPLEMENTED` |
| `crm_assignments` | (planned, `EXEC-PROMPT-CRM-008`) | — | — | — | `NOT_IMPLEMENTED` |
| `crm_transfers` | (planned, `EXEC-PROMPT-CRM-008`) | — | — | — | `NOT_IMPLEMENTED` |
| `crm_notes` | (planned, `EXEC-PROMPT-CRM-008`) | — | — | — | `NOT_IMPLEMENTED` |
| `crm_audit_logs` | (planned, `EXEC-PROMPT-CRM-008`) | — | — | — | `NOT_IMPLEMENTED` |
| `crm_reports` | (planned, `EXEC-PROMPT-CRM-008`) | — | — | — | `NOT_IMPLEMENTED` |
| `crm_phone_numbers` | (planned, `EXEC-PROMPT-CRM-008`) | — | — | — | `NOT_IMPLEMENTED` |
| `crm_contact_lookup_index` | (planned, `EXEC-PROMPT-CRM-008`) | — | — | — | `NOT_IMPLEMENTED` |

Tenant isolation today is enforced at the **application layer** via the
`tenant_id` predicate on every query in `CrmService` and `CrmExtendedService`,
plus the `@RequireCapability` authorization aspect. Adding RLS as
defense-in-depth is tracked as `EXEC-PROMPT-CRM-018` and must not be
described as already implemented.

---

## 4. CRM RBAC Capabilities

18 active `CRM.*` capabilities are seeded by `V20260702_1` and
`V20260702_3` and assigned to every tenant's `ADMIN` role by
`V20260702_2`:

```
CRM.ACCOUNT.READ        CRM.ACCOUNT.WRITE       CRM.ACCOUNT.ARCHIVE
CRM.CONTACT.READ        CRM.CONTACT.WRITE       CRM.CONTACT.ARCHIVE
CRM.LEAD.READ           CRM.LEAD.WRITE          CRM.LEAD.CONVERT
CRM.OPPORTUNITY.READ    CRM.OPPORTUNITY.WRITE
CRM.ACTIVITY.READ       CRM.ACTIVITY.WRITE
CRM.IMPORT.READ         CRM.IMPORT.WRITE
CRM.CUSTOM_FIELD.READ   CRM.CUSTOM_FIELD.WRITE
CRM.ADMIN
```

The reconciled count is **18**. Any document that hard-codes 14 or 15 fails
the drift check.

---

## 5. Frontend Command Center Readiness

| # | Tab | Status | Closing prompt |
|---:|---|---|---|
| 1 | Overview | `IMPLEMENTED_AND_CONNECTED` | — |
| 2 | Leads | `PARTIALLY_IMPLEMENTED` (empty-state only) | `EXEC-PROMPT-CRM-014` |
| 3 | Customers | `PARTIALLY_IMPLEMENTED` (empty-state only) | `EXEC-PROMPT-CRM-015` |
| 4 | Contacts | `PARTIALLY_IMPLEMENTED` (empty-state only) | `EXEC-PROMPT-CRM-016` |
| 5 | Opportunities | `PARTIALLY_IMPLEMENTED` (empty-state only) | `EXEC-PROMPT-CRM-019` |
| 6 | Pipeline | `PARTIALLY_IMPLEMENTED` (empty-state only) | `EXEC-PROMPT-CRM-020` |
| 7 | Tasks | `NOT_IMPLEMENTED` (empty-state only; G1 table not migrated) | `EXEC-PROMPT-CRM-008`, `EXEC-PROMPT-CRM-021` |
| 8 | Transfers | `NOT_IMPLEMENTED` (empty-state only; G1 table not migrated) | `EXEC-PROMPT-CRM-008`, `EXEC-PROMPT-CRM-023` |
| 9 | Employees | `NOT_IMPLEMENTED` (empty-state only) | `EXEC-PROMPT-CRM-023` |
| 10 | Reports | `NOT_IMPLEMENTED` (empty-state only; G1 table not migrated) | `EXEC-PROMPT-CRM-008`, `EXEC-PROMPT-CRM-025` |
| 11 | Mobile Sync | `NOT_IMPLEMENTED` (empty-state only) | — |
| 12 | Caller ID | `NOT_IMPLEMENTED` (empty-state only; G1 table not migrated) | `EXEC-PROMPT-CRM-008` |
| 13 | AI CRM | `NOT_IMPLEMENTED` (empty-state only) | — |
| 14 | Billing | `NOT_IMPLEMENTED` (empty-state only) | — |
| 15 | Settings | `NOT_IMPLEMENTED` (empty-state only) | — |
| 16 | Execution Board | `IMPLEMENTED_AND_CONNECTED` | — |

A tab that renders `<CrmEmptyState/>` is **not** a delivered feature. The
drift check rejects any release note, README excerpt, or roadmap entry that
presents an empty-state-only tab as a delivered capability.

---

## 6. Test Readiness

| Test surface | Count | Status | Closing prompt |
|---|---:|---|---|
| Backend CRM lifecycle (`CrmApiIntegrationTest`) | 2 `@Test` | `IMPLEMENTED_AND_CONNECTED` | — |
| Backend CRM imports + custom fields (`CrmImportAndCustomFieldIntegrationTest`) | 1 `@Test` | `IMPLEMENTED_AND_CONNECTED` | — |
| Backend PostgreSQL migration (`CrmPostgresMigrationTest`) | 4 `@Test` | `IMPLEMENTED_AND_CONNECTED` | — |
| Backend XLSX import (`CrmXlsxImportIntegrationTest`) | 1 `@Test` | `IMPLEMENTED_AND_CONNECTED` | — |
| Frontend component (`crm-interactions.test.tsx`) | 1 file | `PARTIALLY_IMPLEMENTED` | — |
| End-to-end (Playwright) | 0 | `NOT_IMPLEMENTED` | `EXEC-PROMPT-CRM-026` |
| Flyway history assertion | 0 | `NOT_IMPLEMENTED` | `EXEC-PROMPT-CRM-028` |
| Performance baseline | 0 | `NOT_IMPLEMENTED` | `EXEC-PROMPT-CRM-033` |
| Accessibility audit (axe-core) | 0 | `NOT_IMPLEMENTED` | `EXEC-PROMPT-CRM-034` |
| Penetration test (CRM-specific) | 0 | `NOT_IMPLEMENTED` | `EXEC-PROMPT-CRM-032` |

---

## 7. CI/CD Readiness

| Workflow | Status | Closing prompt |
|---|---|---|
| `ci.yml` (main pipeline) | `IMPLEMENTED_AND_CONNECTED` — but no CRM-specific job. | `EXEC-PROMPT-CRM-022` |
| `crm-deployment-readiness.yml` | `PARTIALLY_IMPLEMENTED` — exists, not verified as required check. | `EXEC-PROMPT-CRM-030` |
| `crm-real-smoke.yml` | `PARTIALLY_IMPLEMENTED` — `workflow_dispatch` only; not gated on every deploy. | `EXEC-PROMPT-CRM-027` |
| `crm-web-lint-diagnostics.yml` | `PARTIALLY_IMPLEMENTED` — captures lint but does not block. | `EXEC-PROMPT-CRM-024` |
| Issue #189 reference | `NOT_IMPLEMENTED` — not referenced anywhere. | `EXEC-PROMPT-CRM-029` |

---

## 8. Production Readiness

| Aspect | Status | Notes |
|---|---|---|
| Backend runtime | `IMPLEMENTED_AND_CONNECTED` | Self-hosted production server. |
| Frontend runtime | `IMPLEMENTED_AND_CONNECTED` | Vercel. |
| Database runtime | `IMPLEMENTED_AND_CONNECTED` | Supabase PostgreSQL. Migrations applied manually (`FLYWAY_ENABLED=false` on production). |
| Authenticated smoke | `IMPLEMENTED_AND_CONNECTED` | Two-tenant CRM real smoke proves tenant isolation. |
| Tenant isolation | `IMPLEMENTED_AND_CONNECTED` | Application-layer; RLS is `NOT_IMPLEMENTED` (`EXEC-PROMPT-CRM-018`). |
| Custom-field encryption | `IMPLEMENTED_AND_CONNECTED` | AES-256-GCM, enforced by `scripts/crm/deployment-preflight.sh`. |
| Import worker | `IMPLEMENTED_AND_CONNECTED` | Lease-based, idempotent, database-enforced progress. |
| Formal GO decision record | `NOT_IMPLEMENTED` | `docs/release/CRM-PRODUCTION-GO.md` does not exist. | `EXEC-PROMPT-CRM-031` |
| Penetration test | `NOT_IMPLEMENTED` | `EXEC-PROMPT-CRM-032` |
| Performance baseline | `NOT_IMPLEMENTED` | `EXEC-PROMPT-CRM-033` |
| Accessibility audit | `NOT_IMPLEMENTED` | `EXEC-PROMPT-CRM-034` |

---

## 9. CRM Completion Estimate

```
Overall CRM Completion: ~45% of enterprise target

Implemented and connected:
  - Core entities (accounts, contacts, leads, opportunities, activities)
  - Pipeline management
  - Customer 360 timeline
  - Dashboard
  - Import framework (upload, validate, run, cancel, error CSV)
  - Custom-field framework (definitions, values, AES-GCM sensitive fields)
  - API layer (43 endpoints)
  - CRM Command Center shell + overview + execution board
  - Authenticated two-tenant production smoke
  - 8 backend CRM @Test methods across 4 classes

Partially implemented:
  - 12 of 16 Command Center tabs are empty-state placeholders
  - Frontend API client missing imports + custom-fields methods
  - 3 CRM-specific workflows exist but are not required status checks
  - crm-real-smoke.yml is workflow_dispatch only

Not implemented:
  - G1 extension tables (tasks, notes, transfers, audit logs, reports,
    phone_numbers, contact_lookup_index)
  - Row-Level Security
  - E2E tests
  - Flyway history assertion test
  - Performance baseline
  - Accessibility audit
  - CRM-specific penetration test
  - Issue #189 reference in workflows
  - Formal production GO decision record
  - Products/services catalog, quotations, sales orders
  - Workflow automation, event bus
  - Mobile sync, caller ID, AI CRM, billing, settings tabs
```

The complete execution plan to close these gaps is in
`docs/crm/CRM-ENTERPRISE-EXECUTION-ROADMAP.md`.
