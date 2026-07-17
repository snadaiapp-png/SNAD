# CRM-G1 Stage Report — Database and Multi-Tenant Foundation

> **Report ID:** `G1-STAGE-REPORT-V1`  
> **Implementation PR:** `#552`  
> **Verified implementation head:** `b8ff660650d6ee836271957c08591b5f3bc0be8c`  
> **Merged commit:** `f1eee10480cf3416edcf3824dab66a5450310e8a`  
> **Technical implementation:** `MERGED`  
> **Implementation exact-SHA CI:** `PASS`  
> **Evidence-hardening exact-SHA CI:** `EXTERNAL_PR_CHECK_REQUIRED`  
> **Production migration evidence:** `PENDING`  
> **Gate status:** `OPEN / NOT_READY_FOR_CLOSURE`

## 1. Executive decision

CRM-G1 is no longer rejected for missing source implementation. The merged
repository now contains the eight-table extension schema, exactly 26 explicit
tenant-scoped indexes, migration and catalog verification, a formal report, and
a successful authenticated two-tenant acceptance run.

Formal closure is still prohibited because the controlled target database has
not been proven from repository evidence. Production Flyway application,
post-migration schema evidence, runtime post-deployment isolation, and owner
approval remain mandatory.

## 2. Verified source identity

PR `#552` was merged from exact head:

`b8ff660650d6ee836271957c08591b5f3bc0be8c`

The following pull-request workflows completed successfully on that head:

- `CRM G1 Schema Isolation` — run `29600125168`;
- `CRM Authenticated Acceptance` — run `29600125144`;
- platform `CI`;
- `Web CI`;
- `CRM Deployment Readiness`;
- `Production Readiness Gate`;
- `CRM API Contract Validation`;
- `CRM Modular Architecture Validation`;
- security, backup, provenance, performance, and backlog guards.

The dedicated G1 workflow applied all Flyway migrations to PostgreSQL 16 and
executed the read-only isolation verifier. The authenticated acceptance workflow
built Spring Boot and Next.js, seeded controlled identities, ran Flyway, and
completed the CRM Playwright acceptance suite successfully.

## 3. Unified CRM core — 11 tables

The established unified core contains:

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

## 4. CRM-G1 extension set — exactly 8 tables

| Table | Migration | Purpose | Explicit indexes |
|---|---|---|---:|
| `crm_tasks` | `V20260716_1__create_crm_tasks.sql` | CRM work items | 3 |
| `crm_assignments` | `V20260717_6__create_crm_g1_extension_tables.sql` | Tenant-scoped entity assignments | 3 |
| `crm_transfers` | `V20260717_6__create_crm_g1_extension_tables.sql` | Ownership and assignment transfers | 3 |
| `crm_notes` | `V20260716_2__create_crm_notes.sql` | Append-oriented CRM notes | 3 |
| `crm_audit_logs` | `V20260717_6__create_crm_g1_extension_tables.sql` | CRM audit evidence | 4 |
| `crm_reports` | `V20260717_6__create_crm_g1_extension_tables.sql` | Report definitions and scheduling metadata | 3 |
| `crm_phone_numbers` | `V20260717_6__create_crm_g1_extension_tables.sql` | Normalized contact phone records | 4 |
| `crm_contact_lookup_index` | `V20260717_6__create_crm_g1_extension_tables.sql` | Contact and caller lookup projection | 3 |
| **Total** |  |  | **26** |

`crm_tasks` and `crm_notes` existed before migration `20260717.6`; that migration
adds the remaining six tables and 20 indexes. The cumulative G1 contract is
therefore exactly eight extension tables and 26 explicit indexes.

## 5. Tenant-isolation controls

### 5.1 Database controls

- all eight extension tables contain mandatory `tenant_id`;
- all eight extension tables reference `tenants(id)`;
- all 26 explicit indexes begin with `tenant_id`;
- phone-to-contact and lookup-to-contact relations use composite same-tenant
  foreign keys;
- tenant-scoped unique identities are present;
- assignment and transfer subject types are constrained to approved CRM entity
  categories;
- application services remain responsible for authenticated tenant predicates
  on polymorphic subject references.

PostgreSQL row-level security is not claimed as delivered by G1 and remains a
separate defense-in-depth milestone.

### 5.2 Behavioral PostgreSQL evidence

The evidence-hardening test
`CrmG1TenantIsolationPostgresTest` performs a real negative write test:

1. create Tenant A and Tenant B;
2. create an account and contact under Tenant A;
3. attempt to create a Tenant B lookup row referencing Tenant A's contact;
4. require PostgreSQL to reject the write;
5. create the same lookup under Tenant A and require success;
6. prove no Tenant B row was persisted.

This complements the read-only catalog script. Catalog presence alone is not
treated as proof that a cross-tenant write is rejected.

### 5.3 Runtime API and UI evidence

`apps/web/e2e/crm-tenant-isolation.spec.ts` verifies that Tenant B cannot expose
Tenant A records through direct API identifiers, detail routes, lists, search,
or overview surfaces. The merged implementation head passed `CRM Authenticated
Acceptance`.

## 6. Verification artifacts

| Artifact | Responsibility | Status |
|---|---|---|
| `CrmPostgresMigrationTest` | Clean install, ordered upgrades, tables, constraints, 8 tenant FKs, 26 tenant-leading indexes | Implemented and passed on implementation head |
| `CrmG1TenantIsolationPostgresTest` | Negative cross-tenant write and positive same-tenant write | Implemented; exact-head hardening CI required |
| `scripts/crm/verify-g1-tenant-isolation.sql` | Read-only catalog verification of 8 tables, 8 tenant FKs, 26 indexes, tenant-leading keys, and concrete same-tenant FKs | Implemented and passed on implementation head |
| `CRM G1 Schema Isolation` | Exact-SHA PostgreSQL execution, behavioral tests, and immutable evidence artifact | Hardened; current PR check required |
| `CRM Authenticated Acceptance` | Authenticated Spring Boot, Next.js, and Playwright tenant-isolation acceptance | Passed on implementation head |
| `CRM-G1-PRODUCTION-MIGRATION-RUNBOOK.md` | Controlled target-database execution and rollback procedure | Present |
| `CRM-G1-PRODUCTION-MIGRATION-EVIDENCE.md` | Formal production evidence record | Present; pending execution |

## 7. Capability baseline

The original G1 baseline retains these 18 capabilities:

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

Later migrations add task, note, tag, relationship, and sensitive-data
capabilities. Those additions do not invalidate the original G1 subset.

## 8. Production evidence contract

Production execution must follow:

`docs/crm/CRM-G1-PRODUCTION-MIGRATION-RUNBOOK.md`

Evidence must be recorded in:

`docs/crm/evidence/CRM-G1-PRODUCTION-MIGRATION-EVIDENCE.md`

The target-database operator must prove a recoverable backup, validate Flyway,
apply through `20260717.6`, run the read-only schema verifier, capture the Flyway
row and inventories, and run authenticated two-tenant post-deployment smoke
checks. No production result is fabricated by this report.

## 9. Acceptance matrix

| Requirement | Result |
|---|---|
| 11 unified CRM core tables documented | `PASS` |
| Exactly 8 G1 extension tables implemented | `PASS` |
| Exactly 26 explicit tenant-scoped indexes implemented | `PASS` |
| Tenant ownership FK on all 8 extension tables | `PASS` |
| `tenant_id` leads all 26 explicit indexes | `PASS` |
| Concrete contact relations use same-tenant composite FKs | `PASS` |
| Implementation exact-SHA CI | `PASS` |
| Authenticated two-tenant source acceptance | `PASS` |
| Behavioral PostgreSQL cross-tenant write test | `IMPLEMENTED / CURRENT EXACT-HEAD CI REQUIRED` |
| Immutable G1 evidence artifact | `IMPLEMENTED / CURRENT EXACT-HEAD CI REQUIRED` |
| Production Flyway application | `PENDING` |
| Production schema verification | `PENDING` |
| Post-deployment authenticated two-tenant smoke | `PENDING` |
| Database and application owner approval | `PENDING` |

## 10. Gate decision

```text
G1-STAGE-REPORT-V1: SOURCE_IMPLEMENTED / EVIDENCE_HARDENED
CRM-G1: OPEN / NOT_READY_FOR_CLOSURE
8_EXTENSION_TABLES: VERIFIED_IN_SOURCE_AND_CI
26_EXPLICIT_INDEXES: VERIFIED_IN_SOURCE_AND_CI
IMPLEMENTATION_EXACT_SHA_CI: PASS
AUTHENTICATED_SOURCE_ISOLATION: PASS
BEHAVIORAL_POSTGRESQL_TEST: IMPLEMENTED / CURRENT_CI_REQUIRED
PRODUCTION_MIGRATION_EVIDENCE: REQUIRED
POST_DEPLOYMENT_RUNTIME_ISOLATION: REQUIRED
FORMAL_OWNER_APPROVAL: REQUIRED
```

CRM-G1 must not be marked `CLOSED`, `DONE`, or `APPROVED` until production
execution and owner approvals are recorded against one traceable release
identity.
