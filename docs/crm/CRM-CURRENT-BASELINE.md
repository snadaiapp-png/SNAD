# CRM Current Baseline

> **Authoritative branch:** `main`
> **Reconciled main SHA:** `f1eee10480cf3416edcf3824dab66a5450310e8a`
> **Last reconciled:** 2026-07-17
> **Document status:** AUTHORITATIVE

This document is the single source of truth for the as-built CRM state in the
SNAD repository. It supersedes older, duplicated, or contradictory baseline
statements. Source implementation, production deployment, and formal milestone
closure are separate states and must not be conflated.

## 1. Baseline status

```text
CRM_PRODUCT_BUILD: IMPLEMENTED_AND_CONNECTED
CRM_BUILD_READINESS: CLOSED
PLATFORM_CORE_DEPENDENCY: SATISFIED
PRODUCTION_AUTHORIZATION: PARTIAL
EXEC-PROMPT-CRM-006: IN PROGRESS
CRM-G1_EXTENSION_SCHEMA: SOURCE_IMPLEMENTED / PRODUCTION_PENDING
```

The existing CRM runtime, operational routes, account master, contacts,
pipelines, opportunities, activities, imports, tasks, notes, tags, audit,
timeline, and Customer 360 are implemented and connected. Commercial launch
authorization remains governed separately and is not implied by a technical
runtime deployment.

`EXEC-PROMPT-CRM-006` is being implemented in pull request `#520` on branch
`crm/006-contact-relationship-model`. Until that pull request satisfies all
required workflows, independent exact-head verification, merge, backend
acceptance, and Vercel production verification, its status remains
`IN_PROGRESS`.

CRM-G1 source implementation was merged through pull request `#552`. Its exact
implementation head `b8ff660650d6ee836271957c08591b5f3bc0be8c` passed the
dedicated PostgreSQL schema-isolation workflow and authenticated CRM acceptance.
The merged source contains the eight-table extension schema and exact 26-index
contract. CRM-G1 remains production-pending until controlled target-database
Flyway evidence, post-deployment isolation evidence, and owner approval are
complete.

## 2. Architectural baseline

CRM remains inside the approved modular platform architecture:

- Domain: `apps/sanad-platform/src/main/java/com/sanad/platform/crm`
- Operational UI: `apps/web/app/crm`
- API boundary: authenticated `/api/v1/crm/*` and `/api/v2/crm/*`
- Tenant source: authentication context only
- Authorization: capability-based, deny-by-default
- Concurrency: entity versions plus ETag/If-Match or expected-version contracts
- Persistence: PostgreSQL with forward-only Flyway migrations
- Audit: central platform audit adapter
- Timeline: central CRM timeline adapter
- Events: existing platform mechanisms; no parallel CRM event runtime

The legacy `crm_contacts.account_id` field is transitional. It remains available
for compatibility while the canonical multi-account association moves to the
contact-account relationship model. Removal requires a later deprecation gate
after all callers and generated clients are migrated.

## 3. Current API baseline

The merged baseline includes stable typed operations for:

- accounts and enterprise customer master
- contacts and lifecycle
- leads and conversion
- pipelines and opportunities
- activities and timeline
- tasks, notes, and tags
- import jobs and row errors
- custom fields
- Customer 360

CRM-006 adds typed operations under `/api/v2/crm` for person profiles,
contact-account relationships, tenant-defined roles, primary relationships,
relationship lifecycle, relationship history, ownership history, accounts by
contact, contacts by account, and row-oriented relationship imports. These
operations are `IN_PROGRESS` until PR `#520` is merged and deployed.

The CRM-G1 extension schema is database foundation work. It does not claim that
Transfers, Reports, Caller ID, or other empty-state UI surfaces are functionally
delivered merely because their persistence foundations exist.

## 4. Database migration inventory

The following migrations are authoritative and ordered. Platform migrations are
listed where the CRM runtime depends on them.

| Flyway version | File | Purpose | Baseline status |
|---|---|---|---|
| `20260702.1` | `V20260702_1__create_unified_crm_core.sql` | Unified CRM tables and initial capabilities | `IMPLEMENTED_AND_CONNECTED` |
| `20260702.2` | `V20260702_2__reconcile_admin_role_and_capabilities.sql` | Reconcile ADMIN roles and active capabilities | `IMPLEMENTED_AND_CONNECTED` |
| `20260702.3` | `V20260702_3__complete_crm_imports_custom_fields.sql` | Import and custom-field completion | `IMPLEMENTED_AND_CONNECTED` |
| `20260706.1` | `V20260706_1__create_tenant_quota.sql` | Tenant quota dependency | `IMPLEMENTED_AND_CONNECTED` |
| `20260711.1` | `V20260711_1__create_subscription_change_events.sql` | Subscription event dependency | `IMPLEMENTED_AND_CONNECTED` |
| `20260713.1` | `V20260713_1__create_crm_idempotency_records.sql` | CRM request idempotency | `IMPLEMENTED_AND_CONNECTED` |
| `20260713.2` | `V20260713_2__add_pipeline_version_column.sql` | Pipeline optimistic concurrency | `IMPLEMENTED_AND_CONNECTED` |
| `20260716.1` | `V20260716_1__create_crm_tasks.sql` | First-class CRM tasks | `IMPLEMENTED_AND_CONNECTED` |
| `20260716.2` | `V20260716_2__create_crm_notes.sql` | Append-only CRM notes | `IMPLEMENTED_AND_CONNECTED` |
| `20260716.3` | `V20260716_3__create_crm_tags.sql` | Tenant CRM tags and assignments | `IMPLEMENTED_AND_CONNECTED` |
| `20260716.4` | `V20260716_4__crm_enterprise_account_customer_master.sql` | Enterprise account golden record | `IMPLEMENTED_AND_CONNECTED` |
| `20260717.1` | `V20260717_1__crm_contact_relationship_model.sql` | Person-profile extensions, multi-account relationships, relationship and ownership history, deterministic legacy backfill | `IN_PROGRESS` |
| `20260717.2` | `V20260717_2__crm_contact_relationship_capabilities.sql` | Relationship, sensitive-field, and import capabilities | `IN_PROGRESS` |
| `20260717.3` | `V20260717_3__crm_timeline_tenant_lifecycle.sql` | Align CRM timeline retention and cleanup with controlled tenant lifecycle | `IN_PROGRESS` |
| `20260717.6` | `V20260717_6__create_crm_g1_extension_tables.sql` | Complete the six missing G1 extension tables and the eight-table/26-index isolation contract | `SOURCE_IMPLEMENTED / PRODUCTION_PENDING` |

CRM-006 migrations must pass both clean PostgreSQL installation and upgrade from
`20260716.4`. The upgrade must retain every contact and legacy account
association, create no duplicate relationship, preserve the legacy field during
the transition, and enforce cross-tenant integrity with composite keys.

The CRM-G1 migration passes clean installation and supported upgrade paths in
source CI. The acceptance contract proves all eight G1 tables have tenant
ownership foreign keys, the explicit index count is exactly 26 with `tenant_id`
leading every index, and concrete contact links reject cross-tenant references.
Target production application remains independently gated.

## 5. Authorization baseline

The merged capability model includes account, contact, lead, opportunity,
activity, import, custom-field, task, note, tag, and CRM administration
capabilities. CRM-006 introduces these additional capabilities while remaining
`IN_PROGRESS`:

```text
CRM.RELATIONSHIP.READ
CRM.RELATIONSHIP.WRITE
CRM.RELATIONSHIP.ADMIN
CRM.CONTACT.SENSITIVE.READ
CRM.CONTACT.IMPORT
```

No CRM-006 endpoint may infer a tenant from request parameters or payloads.
Authenticated users without the required capability receive `403`; unauthenticated
requests receive `401`; cross-tenant entity access is concealed or rejected.

The CRM-G1 schema migration does not create authorization claims for application
features that are not yet connected to services and user interfaces.

## 6. User-interface baseline

Operational CRM routes are mounted below `/crm/(operational)`. The merged
baseline includes list and detail routes for accounts, contacts, leads,
opportunities, tasks, notes, tags, imports, and settings. CRM-006 extends contact
and account detail routes with bilingual relationship workspaces. These UI
changes remain `IN_PROGRESS` until Web CI, lint, production build, Playwright,
Vercel preview, and authenticated acceptance pass on the exact final head.

CRM-G1 changes persistence and validation artifacts only. It does not change the
delivery status of empty-state-only Command Center tabs.

## 7. Verification and closure requirements

CRM-006 cannot transition from `IN_PROGRESS` until all applicable controls are
proven on one unchanged head SHA:

- Maven tests and PostgreSQL Testcontainers pass;
- Flyway clean-install and upgrade tests pass;
- tenant-isolation and RBAC negative tests pass;
- audit, timeline, rollback, and duplicate-event tests pass where applicable;
- API contract and generated TypeScript drift are zero where applicable;
- Web TypeScript, ESLint, and Next.js production build pass;
- authenticated acceptance and Playwright regression pass;
- required workflow failures, pending runs, cancellations, and critical skips are zero;
- Surefire XML is inspected and actual testcase elements are counted;
- all review threads are resolved;
- merge uses `expected_head_sha`;
- backend acceptance and BFF connectivity are healthy;
- Vercel production is `READY` on the relevant merge SHA where applicable;
- production routes and runtime logs are verified where applicable.

CRM-G1 may transition from `SOURCE_IMPLEMENTED / PRODUCTION_PENDING` to
`PRODUCTION_VERIFIED` and formal closure only when:

- the current G1 evidence-hardening source passes exact-SHA CI;
- PostgreSQL migration and behavioral cross-tenant tests pass;
- Flyway version `20260717.6` is applied to the controlled target database;
- `scripts/crm/verify-g1-tenant-isolation.sql` passes against that target;
- the Flyway row, eight-table inventory, 26-index inventory, and tenant
  constraints are retained as immutable evidence;
- authenticated two-tenant post-deployment isolation evidence is recorded;
- database and application owners approve the evidence record.

Until those production conditions are met, this document must continue to report:

```text
EXEC-PROMPT-CRM-006: IN PROGRESS
CRM-G1_EXTENSION_SCHEMA: SOURCE_IMPLEMENTED / PRODUCTION_PENDING
```
