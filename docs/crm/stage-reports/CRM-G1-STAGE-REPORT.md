# CRM-G1 Stage Report — Database and Multi-Tenant Foundation

> **Report ID:** `G1-STAGE-REPORT-V1`  
> **Candidate branch:** `crm/g1-complete-foundation`  
> **Report date:** 2026-07-17  
> **Technical implementation:** `COMPLETE`  
> **Exact-SHA CI evidence:** `PENDING`  
> **Production migration evidence:** `PENDING`  
> **Gate status:** `NEEDS_REVIEW / NOT_CLOSED`

## 1. Scope delivered

CRM-G1 delivers the database and multi-tenant foundation required by the CRM
execution roadmap. The candidate contains:

- the existing 11-table unified CRM core;
- the complete eight-table G1 extension set;
- exactly 26 explicit tenant-scoped performance indexes across the eight G1
  extension tables;
- tenant ownership constraints on every G1 extension table;
- same-tenant composite foreign keys where the relationship is concrete;
- PostgreSQL Testcontainers assertions for migration ordering, table presence,
  constraints, tenant ownership, and index count;
- a read-only PostgreSQL verification script;
- the existing API/UI cross-tenant Playwright denial suite.

## 2. Unified CRM core — 11 tables

The core schema is provided by
`V20260702_1__create_unified_crm_core.sql`:

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

## 3. G1 extension set — 8 tables

| Table | Migration | Purpose | Explicit indexes |
|---|---|---|---:|
| `crm_tasks` | `V20260716_1__create_crm_tasks.sql` | First-class CRM work items | 3 |
| `crm_assignments` | `V20260717_6__create_crm_g1_extension_tables.sql` | Tenant-scoped entity assignment history | 3 |
| `crm_transfers` | `V20260717_6__create_crm_g1_extension_tables.sql` | Ownership/assignment transfer workflow data | 3 |
| `crm_notes` | `V20260716_2__create_crm_notes.sql` | Append-only CRM notes | 3 |
| `crm_audit_logs` | `V20260717_6__create_crm_g1_extension_tables.sql` | CRM-specific immutable audit evidence | 4 |
| `crm_reports` | `V20260717_6__create_crm_g1_extension_tables.sql` | Report definitions and scheduling metadata | 3 |
| `crm_phone_numbers` | `V20260717_6__create_crm_g1_extension_tables.sql` | Normalized contact phone records | 4 |
| `crm_contact_lookup_index` | `V20260717_6__create_crm_g1_extension_tables.sql` | Caller/contact lookup projection | 3 |
| **Total** |  |  | **26** |

## 4. Explicit index inventory — 26

### `crm_tasks` — 3

- `idx_crm_tasks_assignee_status`
- `idx_crm_tasks_related`
- `idx_crm_tasks_status_due`

### `crm_assignments` — 3

- `idx_crm_assignments_subject_active`
- `idx_crm_assignments_user_active`
- `idx_crm_assignments_role_status`

### `crm_transfers` — 3

- `idx_crm_transfers_subject_status`
- `idx_crm_transfers_recipient_status`
- `idx_crm_transfers_requested_at`

### `crm_notes` — 3

- `idx_crm_notes_subject`
- `idx_crm_notes_author`
- `idx_crm_notes_active`

### `crm_audit_logs` — 4

- `idx_crm_audit_logs_entity_time`
- `idx_crm_audit_logs_actor_time`
- `idx_crm_audit_logs_correlation`
- `idx_crm_audit_logs_action_time`

### `crm_reports` — 3

- `idx_crm_reports_owner_status`
- `idx_crm_reports_type_status`
- `idx_crm_reports_last_run`

### `crm_phone_numbers` — 4

- `idx_crm_phone_numbers_contact`
- `idx_crm_phone_numbers_e164`
- `idx_crm_phone_numbers_primary`
- `idx_crm_phone_numbers_verified`

### `crm_contact_lookup_index` — 3

- `idx_crm_contact_lookup_phone`
- `idx_crm_contact_lookup_email`
- `idx_crm_contact_lookup_name`

Every explicit index starts with `tenant_id` as the leading key.

## 5. Tenant-isolation strategy

### Database enforcement delivered in G1

- Every G1 extension table contains a mandatory `tenant_id` column.
- Every G1 extension table has a foreign key from `tenant_id` to `tenants(id)`.
- Every G1 extension table has a tenant-scoped identity/uniqueness contract.
- `crm_phone_numbers` uses
  `fk_crm_phone_numbers_contact_same_tenant` to prevent a phone row from
  referencing a contact in another tenant.
- `crm_contact_lookup_index` uses
  `fk_crm_contact_lookup_contact_same_tenant` to prevent a lookup row from
  referencing a contact in another tenant.
- Polymorphic subjects use `subject_type` + `subject_id`; their tenant boundary
  is enforced by the mandatory application-layer tenant predicate because a
  portable relational foreign key cannot target multiple CRM tables.

### Runtime enforcement

- Tenant identity is obtained from the authenticated context, not request
  payloads or query parameters.
- CRM queries must include the authenticated tenant predicate.
- Capability checks remain deny-by-default.
- `apps/web/e2e/crm-tenant-isolation.spec.ts` verifies that Tenant B cannot read
  Tenant A accounts, contacts, leads, or opportunities through APIs, detail
  pages, list pages, or the CRM overview.

### Defense in depth not claimed by G1

PostgreSQL row-level security is not represented as complete. It remains planned
under `EXEC-PROMPT-CRM-018` as an additional database defense layer.

## 6. Verification artifacts

| Artifact | Verification responsibility | Candidate status |
|---|---|---|
| `CrmPostgresMigrationTest` | Clean install, upgrade order, all CRM tables, G1 tenant FKs, two same-tenant contact FKs, exactly 26 G1 indexes, tenant-first index keys | Implemented; CI pending |
| `scripts/crm/verify-g1-tenant-isolation.sql` | Read-only production-like schema verification for 8 tables, 8 tenant FKs, 26 indexes, tenant-first index keys, and same-tenant contact FKs | Implemented; target execution pending |
| `apps/web/e2e/crm-tenant-isolation.spec.ts` | Authenticated API/UI negative tests across two tenants | Present; exact-SHA CI pending |

## 7. Baseline CRM capabilities — 18

The G1 acceptance baseline retains the 18 capabilities established by the
unified core and completion migrations:

```text
CRM.ACCOUNT.READ
CRM.ACCOUNT.WRITE
CRM.ACCOUNT.ARCHIVE
CRM.CONTACT.READ
CRM.CONTACT.WRITE
CRM.CONTACT.ARCHIVE
CRM.LEAD.READ
CRM.LEAD.WRITE
CRM.LEAD.CONVERT
CRM.OPPORTUNITY.READ
CRM.OPPORTUNITY.WRITE
CRM.ACTIVITY.READ
CRM.ACTIVITY.WRITE
CRM.IMPORT.READ
CRM.IMPORT.WRITE
CRM.CUSTOM_FIELD.READ
CRM.CUSTOM_FIELD.WRITE
CRM.ADMIN
```

Later migrations add task, note, tag, relationship, and other capabilities.
Those later additions do not alter the 18-capability G1 baseline enumerated by
the roadmap.

## 8. Production migration evidence contract

G1 cannot be formally closed from repository evidence alone. The authorized
database operator must execute the following against the target PostgreSQL
instance without exposing credentials:

1. verify a recoverable backup;
2. validate Flyway history and checksums;
3. apply through Flyway version `20260717.6`;
4. run `scripts/crm/verify-g1-tenant-isolation.sql`;
5. record the target environment, application/merge SHA, Flyway installed rank,
   successful migration timestamp, script result, operator, and rollback
   reference in the controlled deployment evidence store;
6. run the authenticated two-tenant CRM smoke/isolation workflow.

No production execution result is fabricated by this report.

## 9. Acceptance matrix

| Requirement | Result |
|---|---|
| 11 unified CRM core tables documented | `PASS` |
| Eight G1 extension tables implemented | `PASS` |
| Exactly 26 explicit tenant-scoped indexes implemented | `PASS` |
| Tenant ownership FK on all eight extension tables | `PASS` |
| Concrete contact relationships protected by same-tenant composite FKs | `PASS` |
| Testcontainers migration assertions implemented | `PASS` |
| Read-only PostgreSQL isolation verification script implemented | `PASS` |
| API/UI cross-tenant negative test script present | `PASS` |
| Exact candidate SHA passes required CI | `PENDING` |
| Migration applied to production PostgreSQL/Supabase with recorded evidence | `PENDING` |
| Authenticated two-tenant post-deployment smoke test | `PENDING` |

## 10. Gate decision

```text
G1-STAGE-REPORT-V1: IMPLEMENTED / NEEDS_REVIEW
CRM-G1: OPEN / NOT_READY_FOR_CLOSURE
8_EXTENSION_TABLES: IMPLEMENTED
26_EXPLICIT_INDEXES: IMPLEMENTED
TENANT_ISOLATION_TESTS: IMPLEMENTED
EXACT_SHA_CI: PENDING
PRODUCTION_MIGRATION_EVIDENCE: PENDING
COMMERCIAL_OR_PRODUCTION_APPROVAL: NOT_GRANTED
```

The technical implementation is complete on the candidate branch. Formal gate
closure requires successful exact-SHA CI plus controlled target-database and
authenticated two-tenant evidence.
