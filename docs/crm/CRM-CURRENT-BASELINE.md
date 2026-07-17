# CRM Current Baseline

> **Authoritative branch:** `main`
> **CRM-007 starting main SHA:** `1c76e879d91d95e29f505250b445463f5ea7a6d4`
> **Last reconciled:** 2026-07-17
> **Document status:** AUTHORITATIVE

This document is the repository source of truth for the as-built CRM state. A
feature branch may be documented as `IN_PROGRESS`, but it is not classified as
merged, deployed, accepted, or complete until its exact head is verified,
merged to `main`, and the production deployment is proven.

## 1. Baseline status

```text
CRM_PRODUCT_BUILD: IMPLEMENTED_AND_CONNECTED
CRM_BUILD_READINESS: CLOSED
PLATFORM_CORE_DEPENDENCY: SATISFIED
PRODUCTION_AUTHORIZATION: PARTIAL
EXEC-PROMPT-CRM-006: DONE
EXEC-PROMPT-CRM-007: IN_PROGRESS
CRM-007_PR: #546
```

CRM-006 delivered the person profile and multi-account contact relationship
model through merge commit `d0fe9bdd2aec9f080450b509e9d1a53c9d0ec275`.
Its exact candidate passed the required CI, PostgreSQL, contract, authenticated,
security, Web and Playwright workflows and was included in trusted `main`
Production deployments.

CRM-007 is being implemented on branch
`crm/007-addresses-communication-methods`. It adds canonical addresses and
communication methods for Accounts and People/Contacts. It remains
`IN_PROGRESS` until exact-head verification, expected-head merge, backend
acceptance, and Vercel Production verification are complete.

The later `/api/system/release` env-file runtime regression is tracked in issue
`#545` as a parallel operational remediation and is not part of the CRM-007
domain scope.

## 2. Architectural baseline

- Domain: `apps/sanad-platform/src/main/java/com/sanad/platform/crm`
- Operational UI: `apps/web/app/crm`
- API boundary: authenticated `/api/v1/crm/*` and `/api/v2/crm/*`
- Tenant source: authenticated context only; tenant IDs are not accepted from payloads
- Authorization: capability-based and deny-by-default
- Concurrency: entity versions plus ETag/If-Match or expected-version contracts
- Persistence: PostgreSQL with forward-only Flyway migrations
- Audit: central platform Audit adapter
- Timeline: central CRM Timeline adapter
- Events: existing platform mechanisms; no parallel CRM event runtime

Compatibility fields and tables remain during the transition:

- `crm_contacts.account_id`
- `crm_accounts.primary_email` and `crm_accounts.primary_phone`
- `crm_contacts.primary_email` and `crm_contacts.primary_phone`
- `crm_account_addresses`

CRM-007 treats the canonical owner-scoped tables as the source for new write
operations and maintains compatibility projections. Removal of legacy fields or
tables requires a later deprecation gate after all callers and generated clients
are migrated.

## 3. Current API baseline

The merged baseline includes stable typed operations for accounts, contacts,
leads, pipelines, opportunities, activities, tasks, notes, tags, imports,
custom fields, Customer 360, person profiles, contact-account relationships,
relationship lifecycle/history, and ownership history.

CRM-007 adds typed `/api/v2/crm` operations for:

- Account and Person address list/create/read/update
- primary address selection, archive/reactivate, and address history
- Account and Person communication-method list/create/read/update
- preferred method selection, verification lifecycle, archive/reactivate, and history
- cursor pagination and bounded filters
- field-level masking for confidential and restricted communication values

These operations remain `IN_PROGRESS` until PR `#546` is merged and deployed.

## 4. Database migration inventory

| Flyway version | File | Purpose | Baseline status |
|---|---|---|---|
| `20260702.1` | `V20260702_1__create_unified_crm_core.sql` | Unified CRM tables and initial capabilities | `IMPLEMENTED_AND_CONNECTED` |
| `20260702.2` | `V20260702_2__reconcile_admin_role_and_capabilities.sql` | ADMIN and active-capability reconciliation | `IMPLEMENTED_AND_CONNECTED` |
| `20260702.3` | `V20260702_3__complete_crm_imports_custom_fields.sql` | Import and custom-field completion | `IMPLEMENTED_AND_CONNECTED` |
| `20260706.1` | `V20260706_1__create_tenant_quota.sql` | Tenant quota dependency | `IMPLEMENTED_AND_CONNECTED` |
| `20260711.1` | `V20260711_1__create_subscription_change_events.sql` | Subscription event dependency | `IMPLEMENTED_AND_CONNECTED` |
| `20260713.1` | `V20260713_1__create_crm_idempotency_records.sql` | CRM request idempotency | `IMPLEMENTED_AND_CONNECTED` |
| `20260713.2` | `V20260713_2__add_pipeline_version_column.sql` | Pipeline concurrency | `IMPLEMENTED_AND_CONNECTED` |
| `20260716.1` | `V20260716_1__create_crm_tasks.sql` | CRM tasks | `IMPLEMENTED_AND_CONNECTED` |
| `20260716.2` | `V20260716_2__create_crm_notes.sql` | CRM notes | `IMPLEMENTED_AND_CONNECTED` |
| `20260716.3` | `V20260716_3__create_crm_tags.sql` | CRM tags and assignments | `IMPLEMENTED_AND_CONNECTED` |
| `20260716.4` | `V20260716_4__crm_enterprise_account_customer_master.sql` | Enterprise account golden record and legacy account addresses | `IMPLEMENTED_AND_CONNECTED` |
| `20260717.1` | `V20260717_1__crm_contact_relationship_model.sql` | Person profiles and multi-account relationships | `IMPLEMENTED_AND_CONNECTED` |
| `20260717.2` | `V20260717_2__crm_contact_relationship_capabilities.sql` | Relationship and sensitive-field capabilities | `IMPLEMENTED_AND_CONNECTED` |
| `20260717.3` | `V20260717_3__crm_timeline_tenant_lifecycle.sql` | Timeline tenant lifecycle alignment | `IMPLEMENTED_AND_CONNECTED` |
| `20260717.4` | `V20260717_4__crm_addresses_communication_methods.sql` | Canonical owner-scoped addresses, communication methods, history, policies and legacy backfill | `IN_PROGRESS` |
| `20260717.5` | `V20260717_5__crm_addresses_communication_capabilities.sql` | Address, communication, sensitive-read and export capabilities | `IN_PROGRESS` |

CRM-007 migrations must pass clean PostgreSQL installation and upgrade from
`20260717.3`. The upgrade must preserve every legacy address row and every
primary email/phone value, preserve Arabic text exactly, create no duplicate
canonical record on rerun, retain legacy projections, and enforce cross-tenant
integrity with composite foreign keys.

## 5. Authorization and privacy baseline

CRM-007 introduces:

```text
CRM.ADDRESS.READ
CRM.ADDRESS.WRITE
CRM.ADDRESS.ADMIN
CRM.COMMUNICATION.READ
CRM.COMMUNICATION.WRITE
CRM.COMMUNICATION.ADMIN
CRM.COMMUNICATION.SENSITIVE.READ
CRM.COMMUNICATION.EXPORT
```

Only existing active `ADMIN` roles receive these capabilities automatically.
Other roles are not broadened implicitly. Communication values classified as
`CONFIDENTIAL` or `RESTRICTED` are masked unless the authenticated principal has
`CRM.COMMUNICATION.SENSITIVE.READ`; export remains independently governed.

## 6. Verification requirements

CRM-007 cannot transition from `IN_PROGRESS` until one unchanged head SHA proves:

- Maven compile and all tests pass
- PostgreSQL clean-install and CRM-006-to-CRM-007 upgrade pass
- legacy row-count and Arabic-value preservation pass
- tenant-isolation and composite-key negative tests pass
- RBAC, masking, Audit, Timeline, history, rollback and optimistic concurrency pass
- E.164 normalization and no-silent-country-fabrication tests pass
- API contract and generated TypeScript drift are zero
- Web TypeScript, ESLint, Next.js production build, RTL/LTR, responsive and accessibility tests pass
- authenticated acceptance and Playwright regression pass
- required workflow failures, pending runs, cancellations and critical skips are zero
- Surefire XML actual testcase elements are inspected
- all review threads are resolved
- branch is synchronized with `main` and merge uses `expected_head_sha`
- backend health, BFF connectivity and CRM Production routes are healthy
- Vercel Production is `READY` on the CRM-007 merge SHA with no fatal runtime errors

Until those conditions are met, this document must continue to report:

```text
EXEC-PROMPT-CRM-007: IN_PROGRESS
```
