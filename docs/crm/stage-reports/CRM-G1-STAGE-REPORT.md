# CRM-G1 Stage Report V1

## Decision

```text
REPORT_ID: G1-STAGE-REPORT-V1
SOURCE_IMPLEMENTATION: COMPLETE_ON_CANDIDATE_BRANCH
8_EXTENSION_TABLES: IMPLEMENTED_AND_ASSERTED
26_EXPLICIT_INDEXES: IMPLEMENTED_AND_ASSERTED
POSTGRESQL_ISOLATION_TEST: IMPLEMENTED
PLAYWRIGHT_ISOLATION_SCRIPT: PRESENT
EXACT_HEAD_CI: EXTERNAL_PR_CHECK_REQUIRED
PRODUCTION_MIGRATION_EVIDENCE: PENDING
CRM-G1: OPEN / NOT_READY_FOR_CLOSURE
```

This report replaces the previous unsupported summary with an auditable inventory.
The source contract is implemented on `crm/g1-extension-closure`. Closure remains
blocked unless the current pull-request head has a successful external CI check and
the production evidence record is completed and approved.

The exact-head result is intentionally not hard-coded in this file because editing the
report changes the head SHA. The decision authority must read the current
`CRM G1 Schema Acceptance` check and its artifact from the pull request or release
record at the time of approval.

## Delivery files

- `V20260702_1__create_unified_crm_core.sql`
- `V20260716_1__create_crm_tasks.sql`
- `V20260716_2__create_crm_notes.sql`
- `V20260717_6__complete_crm_g1_extension_tables.sql`
- `CrmPostgresMigrationTest.java`
- `scripts/crm/verify-g1-schema.sql`
- `.github/workflows/crm-g1-schema-acceptance.yml`
- `apps/web/e2e/crm-tenant-isolation.spec.ts`
- `docs/crm/CRM-G1-PRODUCTION-MIGRATION-RUNBOOK.md`
- `docs/crm/evidence/CRM-G1-PRODUCTION-MIGRATION-EVIDENCE.md`

## Core inventory — 11 tables

1. `crm_accounts`
2. `crm_contacts`
3. `crm_pipelines`
4. `crm_pipeline_stages`
5. `crm_leads`
6. `crm_opportunities`
7. `crm_opportunity_stage_history`
8. `crm_activities`
9. `crm_timeline_events`
10. `crm_import_jobs`
11. `crm_custom_field_definitions`

## G1 extension inventory — exactly 8 tables

1. `crm_tasks`
2. `crm_assignments`
3. `crm_transfers`
4. `crm_notes`
5. `crm_audit_logs`
6. `crm_reports`
7. `crm_phone_numbers`
8. `crm_contact_lookup_index`

`crm_tasks` and `crm_notes` already existed in forward-only migrations. Migration
`20260717.6` creates the remaining six without duplicating existing tables.

## Explicit index inventory — exactly 26

The acceptance count includes only explicit business indexes named `idx_crm_%`. It
excludes indexes created implicitly for primary keys and unique constraints.

| Table | Indexes |
|---|---|
| `crm_tasks` | `idx_crm_tasks_assignee_status`, `idx_crm_tasks_related`, `idx_crm_tasks_status_due` |
| `crm_assignments` | `idx_crm_assignments_subject`, `idx_crm_assignments_assignee`, `idx_crm_assignments_active_window` |
| `crm_transfers` | `idx_crm_transfers_subject`, `idx_crm_transfers_recipient_status`, `idx_crm_transfers_requested_at` |
| `crm_notes` | `idx_crm_notes_subject`, `idx_crm_notes_author`, `idx_crm_notes_active` |
| `crm_audit_logs` | `idx_crm_audit_logs_aggregate`, `idx_crm_audit_logs_actor`, `idx_crm_audit_logs_correlation`, `idx_crm_audit_logs_occurred` |
| `crm_reports` | `idx_crm_reports_status_type`, `idx_crm_reports_owner`, `idx_crm_reports_last_run` |
| `crm_phone_numbers` | `idx_crm_phone_numbers_account`, `idx_crm_phone_numbers_contact`, `idx_crm_phone_numbers_lead`, `idx_crm_phone_numbers_normalized` |
| `crm_contact_lookup_index` | `idx_crm_contact_lookup_name`, `idx_crm_contact_lookup_email`, `idx_crm_contact_lookup_phone` |

`CrmPostgresMigrationTest` and `verify-g1-schema.sql` fail unless PostgreSQL reports
exactly 26 matching indexes across these eight tables.

## Tenant-isolation controls

- Tenant context remains authentication-derived; it is not trusted from client input.
- All eight extension tables have non-null `tenant_id` and a tenant root foreign key.
- Assignments and transfers use composite same-tenant references for CRM subjects and
  users.
- Phone records use same-tenant references for account, contact, or lead.
- Contact lookup uses same-tenant references for both contact and account.
- Database checks require exactly one subject where a record may target multiple CRM
  entity types.
- `rejectsCrossTenantContactLookupReferences` proves that a Tenant B row cannot
  reference a Tenant A contact or account, while the same-tenant write succeeds.
- The existing Playwright suite verifies API, detail-page, list, search, and overview
  isolation between two tenants.

Application query predicates remain mandatory. PostgreSQL row-level security is not
claimed as delivered by this report.

## Capability baseline

The original 18-capability G1 baseline is:

`CRM.ACCOUNT.READ`, `CRM.ACCOUNT.WRITE`, `CRM.ACCOUNT.ARCHIVE`,
`CRM.CONTACT.READ`, `CRM.CONTACT.WRITE`, `CRM.CONTACT.ARCHIVE`,
`CRM.LEAD.READ`, `CRM.LEAD.WRITE`, `CRM.LEAD.CONVERT`,
`CRM.OPPORTUNITY.READ`, `CRM.OPPORTUNITY.WRITE`, `CRM.ACTIVITY.READ`,
`CRM.ACTIVITY.WRITE`, `CRM.ADMIN`, `CRM.CUSTOM_FIELD.READ`,
`CRM.CUSTOM_FIELD.WRITE`, `CRM.IMPORT.READ`, and `CRM.IMPORT.WRITE`.

Later CRM migrations add task, note, tag, relationship, and sensitive-data
capabilities. The current complete migration test therefore asserts 29 active CRM
capabilities. The 18-capability set remains a required subset; the evolved schema is
not reduced to an obsolete exact count.

## Automated acceptance

The PostgreSQL suite covers clean installation, upgrade from the pre-CRM schema,
upgrade from CRM core, unique Flyway history, final version `20260717.6`, complete
table inventory, exact index count, tenant constraints, and negative cross-tenant
reference behavior.

The CI workflow applies the complete Flyway chain on PostgreSQL 16, runs the catalog
verifier and Testcontainers suite, and uploads schema, index, Flyway, and test evidence
bound to the exact candidate SHA. A workflow definition is not evidence of success;
the external PR or release check must be successful on the current immutable head.

## Acceptance matrix

| Requirement | Result |
|---|---|
| 11 core tables identified | PASS |
| Exactly 8 extension tables implemented | PASS in source |
| Exactly 26 explicit indexes implemented | PASS in source |
| Tenant root FK coverage | PASS in source |
| Composite same-tenant constraints | PASS in source |
| PostgreSQL negative isolation test | IMPLEMENTED |
| Playwright isolation contract | PRESENT |
| Formal report | PRESENT |
| Exact-head CI run | EXTERNAL PR CHECK — MUST BE PASS AT DECISION TIME |
| Production Flyway application | PENDING |
| Production schema evidence | PENDING |
| Runtime production isolation evidence | PENDING |
| Owner approval | PENDING |

## Final gate

```text
G1-STAGE-REPORT-V1: IMPLEMENTED / AWAITING_RUNTIME_EVIDENCE
CRM-G1: OPEN / NOT_READY_FOR_CLOSURE
8_EXTENSION_TABLES: CONCLUSIVELY_DEFINED_IN_SOURCE
26_INDEXES: CONCLUSIVELY_DEFINED_IN_SOURCE
TENANT_ISOLATION_TESTS: IMPLEMENTED
EXACT_HEAD_CI: EXTERNAL PR CHECK REQUIRED
PRODUCTION_MIGRATION_EVIDENCE: REQUIRED
FORMAL_OWNER_APPROVAL: REQUIRED
```

No `CLOSED`, `DONE`, or `APPROVED` status is authorized until all remaining evidence is
linked to one immutable release identity.
