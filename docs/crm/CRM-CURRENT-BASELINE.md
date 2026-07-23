# CRM Current Baseline

> **Authoritative branch:** `main`
> **Reconciled main SHA:** `b67fab8ce01543214bd1fd0047c19e27455a1f3c`
> **Last reconciled:** 2026-07-17
> **Document status:** AUTHORITATIVE

This document is the single source of truth for the as-built CRM state in the
SNAD repository. It supersedes older, duplicated, or contradictory baseline
statements. A feature branch is not classified as merged, deployed, accepted,
or closed until its exact head is verified and merged. Production authorization
and database evidence remain separate controlled decisions.

## 1. Baseline status

```text
CRM_PRODUCT_BUILD: IMPLEMENTED_AND_CONNECTED
CRM_BUILD_READINESS: CLOSED
PLATFORM_CORE_DEPENDENCY: SATISFIED
PRODUCTION_AUTHORIZATION: PARTIAL
EXEC-PROMPT-CRM-006: MERGED / PRODUCTION_VERIFICATION_GOVERNED_SEPARATELY
CRM-G1: IMPLEMENTED_AND_MERGED / PRODUCTION_EVIDENCE_PENDING
EXEC-PROMPT-CRM-007: IN_PROGRESS / PR #546
```

The existing CRM runtime, operational routes, account master, contacts,
pipelines, opportunities, activities, imports, tasks, notes, tags, audit,
timeline, Customer 360, and relationship model are implemented and merged.
Commercial launch authorization remains governed separately and is not implied
by repository implementation or CI success.

`EXEC-PROMPT-CRM-006` was merged through pull request `#520` with merge SHA
`d0fe9bdd2aec9f080450b509e9d1a53c9d0ec275`. Production runtime verification is
governed separately from this repository baseline.

CRM-G1 was implemented through pull request `#552`. Its exact candidate head
`b8ff660650d6ee836271957c08591b5f3bc0be8c` passed all required CI, PostgreSQL
16 schema verification, Testcontainers, authenticated Playwright, security,
architecture, backup, performance, and readiness gates. It was squash-merged to
`main` as `f1eee10480cf3416edcf3824dab66a5450310e8a` using
`expected_head_sha` protection. Formal G1 closure still requires controlled
production PostgreSQL/Supabase migration evidence and a post-deployment
two-tenant isolation smoke result.

CRM-007 remains an unmerged candidate in pull request `#546`. It introduces
canonical owner-scoped addresses and communication methods. Its repository,
production, and gate status must remain `IN_PROGRESS` until exact-head CI,
protected merge, controlled database migration, Vercel Production deployment,
and post-deployment smoke evidence are complete. The `/api/system/release`
regression remains independently tracked in issue `#545`.

## 2. Architectural baseline

CRM remains inside the approved modular platform architecture:

- Domain: `apps/sanad-platform/src/main/java/com/sanad/platform/crm`
- Operational UI: `apps/web/app/crm`
- API boundary: authenticated `/api/v1/crm/*` and `/api/v2/crm/*`
- Tenant source: authentication context only
- Authorization: capability-based, deny-by-default
- Concurrency: entity versions plus ETag/If-Match or expected-version contracts
- Persistence: PostgreSQL with forward-only Flyway migrations
- Audit: central platform audit adapter plus CRM audit evidence where required
- Timeline: central CRM timeline adapter
- Events: existing platform mechanisms; no parallel CRM event runtime

The legacy `crm_contacts.account_id` field is transitional. It remains available
for compatibility while the canonical multi-account association uses the
contact-account relationship model. Removal requires a later deprecation gate
after all callers and generated clients are migrated.

CRM-007 preserves `crm_account_addresses`, account/contact primary email and
phone fields, and other compatibility projections while canonical writes use
tenant-scoped owner records.

## 3. Current API baseline

The merged baseline includes stable typed operations for:

- accounts and enterprise customer master
- contacts, person profiles, and lifecycle
- multi-account contact relationships, roles, history, and ownership
- leads and conversion
- pipelines and opportunities
- activities and timeline
- tasks, notes, and tags
- import jobs and row errors
- custom fields
- Customer 360

CRM-006 added typed operations under `/api/v2/crm` for person profiles,
contact-account relationships, tenant-defined roles, primary relationships,
relationship lifecycle, relationship history, ownership history, accounts by
contact, contacts by account, and row-oriented relationship imports.

CRM-007 adds candidate `/api/v2/crm` operations for addresses, communication
methods, lifecycle/history, verification, privacy masking, governed search,
import, export, and tenant communication policies. These operations are not
classified as merged or deployed while pull request `#546` remains open.

The CRM-G1 merge changes persistence and validation artifacts. It does not claim
that Tasks, Transfers, Reports, Caller ID, or other empty-state Command Center
surfaces are fully delivered as user-facing features.

## 4. Database migration inventory

The following migrations are authoritative and ordered. Platform migrations are
listed where the CRM runtime depends on them.

| Flyway version | File | Purpose | Repository status |
|---|---|---|---|
| `20260702.1` | `V20260702_1__create_unified_crm_core.sql` | Unified CRM tables and initial capabilities | `MERGED` |
| `20260702.2` | `V20260702_2__reconcile_admin_role_and_capabilities.sql` | Reconcile ADMIN roles and active capabilities | `MERGED` |
| `20260702.3` | `V20260702_3__complete_crm_imports_custom_fields.sql` | Import and custom-field completion | `MERGED` |
| `20260706.1` | `V20260706_1__create_tenant_quota.sql` | Tenant quota dependency | `MERGED` |
| `20260711.1` | `V20260711_1__create_subscription_change_events.sql` | Subscription event dependency | `MERGED` |
| `20260713.1` | `V20260713_1__create_crm_idempotency_records.sql` | CRM request idempotency | `MERGED` |
| `20260713.2` | `V20260713_2__add_pipeline_version_column.sql` | Pipeline optimistic concurrency | `MERGED` |
| `20260716.1` | `V20260716_1__create_crm_tasks.sql` | First-class CRM tasks | `MERGED` |
| `20260716.2` | `V20260716_2__create_crm_notes.sql` | Append-only CRM notes | `MERGED` |
| `20260716.3` | `V20260716_3__create_crm_tags.sql` | Tenant CRM tags and assignments | `MERGED` |
| `20260716.4` | `V20260716_4__crm_enterprise_account_customer_master.sql` | Enterprise account golden record | `MERGED` |
| `20260717.1` | `V20260717_1__crm_contact_relationship_model.sql` | Person-profile extensions, multi-account relationships, relationship and ownership history, deterministic legacy backfill | `MERGED` (`#520`) |
| `20260717.2` | `V20260717_2__crm_contact_relationship_capabilities.sql` | Relationship, sensitive-field, and import capabilities | `MERGED` (`#520`) |
| `20260717.3` | `V20260717_3__crm_timeline_tenant_lifecycle.sql` | Align CRM timeline retention and cleanup with controlled tenant lifecycle | `MERGED` (`#520`) |
| `20260717.6` | `V20260717_6__create_crm_g1_extension_tables.sql` | Complete the six missing G1 extension tables and the eight-table/26-index isolation contract | `MERGED_AND_CI_VERIFIED` (`#552`) |
| `20260717.100` | `V20260717_100__crm_addresses_communication_methods.sql` | Canonical tenant-scoped addresses, communication methods, history, policies, and compatibility backfill | `IN_PROGRESS` (`#546`) |
| `20260717.101` | `V20260717_101__crm_addresses_communication_capabilities.sql` | Address, communication, sensitive-read, administration, and export capabilities | `IN_PROGRESS` (`#546`) |
| `20260722.1` | `V20260722_1__create_crm_sales_teams.sql` | CRM-008B: Sales teams and team memberships (PostgreSQL-native: JSONB metadata, partial unique indexes, fail-closed guards) | `IN_PROGRESS` (`#691`) |
| `20260722.2` | `V20260722_2__create_crm_queues.sql` | CRM-008B: Queues and queue memberships (PostgreSQL-native: partial unique index for single-active) | `IN_PROGRESS` (`#691`) |
| `20260722.3` | `V20260722_3__create_crm_territories.sql` | CRM-008B: Territories, closure table, and territory assignments (PostgreSQL-native: JSONB rule_definition, partial unique index for single-primary) | `IN_PROGRESS` (`#691`) |
| `20260722.4` | `V20260722_4__create_crm_assignment_rules.sql` | CRM-008B: Assignment rules and rule versions (PostgreSQL-native: JSONB match_conditions, partial unique index for single-active-version) | `IN_PROGRESS` (`#691`) |
| `20260722.5` | `V20260722_5__upgrade_crm_assignments_and_create_ownership_history.sql` | CRM-008B: Extend crm_assignments + create ownership history (PostgreSQL-native: JSONB workflow_result, G1 backfill subject_type→record_type/subject_id→record_id/assigned_user_id→owner_user_id, fail-closed on unmappable rows) | `IN_PROGRESS` (`#691`) |
| `20260722.6` | `V20260722_6__create_crm_transfer_requests.sql` | CRM-008B: Transfer requests and transfer steps (PostgreSQL-native: JSONB record_ids, SoD checks) | `IN_PROGRESS` (`#691`) |
| `20260722.7` | `V20260722_7__add_owner_team_queue_columns.sql` | CRM-008B: Add owner_team_id, owner_queue_id to 6 CRM tables (PostgreSQL-native: fail-closed on partial column state) | `IN_PROGRESS` (`#691`) |
| `20260722.8` | `V20260722_8__seed_crm_ownership_capabilities.sql` | CRM-008B: Seed 17 capabilities + define SALES_MANAGER and SALES_REPRESENTATIVE roles + assign per-role capability subsets (PostgreSQL-native: fail-closed on conflicting capability name/description, no ON CONFLICT DO NOTHING) | `IN_PROGRESS` (`#691`) |
| `20260722.9` | `V20260722_9__create_crm_assignment_rule_counters.sql` | CRM-008B: Round-robin counter table (PostgreSQL-native: BIGINT counter, unique per tenant+rule) | `IN_PROGRESS` (`#691`) |

The CRM-006 migration set passed clean PostgreSQL installation and supported
upgrade verification while retaining contacts and legacy account associations,
preventing duplicate relationships, preserving the transitional legacy field,
and enforcing same-tenant integrity with composite keys.

The CRM-G1 migration passed clean install and supported upgrade paths. Automated
checks proved all eight G1 extension tables have tenant ownership foreign keys,
proved the explicit index count is exactly 26 with `tenant_id` leading every
index, and proved concrete contact links use same-tenant composite foreign keys.
Production application of Flyway version `20260717.6` is not inferred from CI.

CRM-007 migrations `20260717.100` and `20260717.101` remain candidates. Their
presence in this inventory records governance traceability only; it does not
claim merge or production application.

## 5. Authorization baseline

The merged capability model includes account, contact, relationship, lead,
opportunity, activity, import, custom-field, task, note, tag, sensitive-contact,
and CRM administration capabilities. No CRM endpoint may infer a tenant from
request parameters or payloads. Authenticated users without the required
capability receive `403`; unauthenticated requests receive `401`; cross-tenant
entity access is concealed or rejected.

CRM-007 candidate capabilities govern address read/write/admin/export and
communication read/write/admin/sensitive-read/export separately. Existing active
ADMIN roles receive the candidate capabilities through the migration; other
roles are not broadened implicitly.

## 6. User-interface baseline

Operational CRM routes are mounted below `/crm/(operational)`. The merged
baseline includes list and detail routes for accounts, contacts, leads,
opportunities, tasks, notes, tags, imports, and settings. The relationship model
extends contact and account detail routes with bilingual relationship
workspaces.

CRM-007 adds a bilingual candidate workspace to account and contact detail
layouts. This candidate UI is not classified as Production until the controlled
deployment and smoke gates complete.

The CRM-G1 merge is a database and verification foundation. It does not change
the delivery status of empty-state-only Command Center tabs.

## 7. Verification record and remaining controls

The exact CRM-G1 head SHA passed:

- Maven tests and PostgreSQL Testcontainers
- Flyway clean-install and upgrade tests
- the dedicated PostgreSQL 16 G1 schema-isolation gate
- tenant-isolation and RBAC negative tests
- CRM API contract and generated client checks
- Web TypeScript, ESLint, and Next.js production build
- authenticated acceptance and Playwright regression
- security, architecture, service-decomposition, performance, backup/restore,
  business-process E2E, provenance, and production-readiness gates
- empty review-thread verification
- protected merge using `expected_head_sha`

The remaining G1 controls are external to repository CI:

- apply Flyway version `20260717.6` to the controlled production database;
- run `scripts/crm/verify-g1-tenant-isolation.sql` against that target;
- record backup, migration, operator, timestamp, target, and rollback evidence;
- run and record authenticated two-tenant post-deployment isolation smoke tests.

CRM-007 must not be closed until one unchanged head passes all required gates,
then merges through `expected_head_sha`, and production migration/deployment,
smoke, and runtime-error evidence are recorded.

```text
CRM-G1: IMPLEMENTED_AND_MERGED / PRODUCTION_EVIDENCE_PENDING
EXEC-PROMPT-CRM-007: IN_PROGRESS
```
