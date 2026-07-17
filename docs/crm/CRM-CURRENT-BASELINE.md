# CRM Current Baseline

> **Authoritative branch:** `main`
> **Reconciled main SHA:** `e02594e8cef13d65fb32b996d41e9a3875ce94e2`
> **Last reconciled:** 2026-07-17
> **Document status:** AUTHORITATIVE

This document is the single source of truth for the as-built CRM state in the
SNAD repository. It supersedes older, duplicated, or contradictory baseline
statements. A feature branch may be documented here as `IN_PROGRESS`, but it is
not classified as merged, deployed, accepted, or closed until its exact head is
verified, merged to `main`, and the production deployment is proven.

## 1. Baseline status

```text
CRM_PRODUCT_BUILD: IMPLEMENTED_AND_CONNECTED
CRM_BUILD_READINESS: CLOSED
PLATFORM_CORE_DEPENDENCY: SATISFIED
PRODUCTION_AUTHORIZATION: PARTIAL
EXEC-PROMPT-CRM-006: IN PROGRESS
CRM-G1-COMPLETION-CANDIDATE: IN PROGRESS
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

The CRM-G1 completion candidate is being implemented in pull request `#552` on
branch `crm/g1-complete-foundation`. It completes the eight-table G1 extension
set and the exact 26-index contract. It remains `IN_PROGRESS` until exact-SHA CI,
merge, controlled PostgreSQL/Supabase migration evidence, and authenticated
two-tenant isolation evidence are complete.

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

The CRM-G1 completion candidate is database-only. It does not claim that the
Tasks, Transfers, Reports, Caller ID, or other empty-state UI surfaces are
functionally delivered.

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
| `20260717.6` | `V20260717_6__create_crm_g1_extension_tables.sql` | Complete the six missing G1 extension tables and the eight-table/26-index isolation contract | `IN_PROGRESS` (`#552`) |

CRM-006 migrations must pass both clean PostgreSQL installation and upgrade from
`20260716.4`. The upgrade must retain every contact and legacy account
association, create no duplicate relationship, preserve the legacy field during
the transition, and enforce cross-tenant integrity with composite keys.

The CRM-G1 migration candidate must pass clean install and all supported upgrade
paths, prove all eight G1 tables have tenant ownership foreign keys, prove the
explicit index count is exactly 26 with `tenant_id` leading every index, and
prove the concrete contact links reject cross-tenant references.

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

## 6. User-interface baseline

Operational CRM routes are mounted below `/crm/(operational)`. The merged
baseline includes list and detail routes for accounts, contacts, leads,
opportunities, tasks, notes, tags, imports, and settings. CRM-006 extends contact
and account detail routes with bilingual relationship workspaces. These UI
changes remain `IN_PROGRESS` until Web CI, lint, production build, Playwright,
Vercel preview, and authenticated acceptance pass on the exact final head.

The CRM-G1 candidate changes persistence and validation artifacts only. It does
not change the delivery status of empty-state-only Command Center tabs.

## 7. Verification requirements

CRM-006 and the CRM-G1 completion candidate cannot transition from `IN_PROGRESS`
until all applicable controls are proven on one unchanged head SHA:

- Maven tests and PostgreSQL Testcontainers pass
- Flyway clean-install and upgrade tests pass
- tenant-isolation and RBAC negative tests pass
- audit, timeline, rollback, and duplicate-event tests pass where applicable
- API contract and generated TypeScript drift are zero where applicable
- Web TypeScript, ESLint, and Next.js production build pass
- authenticated acceptance and Playwright regression pass
- required workflow failures, pending runs, cancellations, and critical skips are zero
- Surefire XML is inspected and actual testcase elements are counted
- all review threads are resolved
- merge uses `expected_head_sha`
- backend acceptance and BFF connectivity are healthy
- Vercel production is `READY` on the relevant merge SHA where applicable
- production routes and runtime logs are verified where applicable
- CRM-G1 Flyway version `20260717.6` is applied to the controlled target database
- `scripts/crm/verify-g1-tenant-isolation.sql` passes against that target
- authenticated two-tenant post-deployment isolation evidence is recorded

Until those conditions are met, this document must continue to report:

```text
EXEC-PROMPT-CRM-006: IN PROGRESS
CRM-G1-COMPLETION-CANDIDATE: IN PROGRESS
```
