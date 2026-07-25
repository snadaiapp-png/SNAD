# CRM Current Baseline

> **Authoritative branch:** `main`  
> **CRM-008R authorized base:** `d8c0e8dc330a054cc071c0c9ca2c8b59cf52dae4`  
> **Reconciled:** 2026-07-26  
> **Document status:** AUTHORITATIVE FOR CRM-007 / CRM-008R SCOPE

This document is the current source of truth for the as-built CRM state relevant
to CRM-007 and CRM-008. Historical design documents, issue bodies and pull
request descriptions remain evidence of their time and must not be interpreted
as current status when this baseline supersedes them.

Repository merge, production evidence and commercial authorization are separate
controlled decisions. A stage is not classified as production-closed solely
because its source code was merged or its CI passed.

## 1. Current stage status

```text
CRM_PRODUCT_BUILD: IMPLEMENTED_AND_CONNECTED
CRM_BUILD_READINESS: CLOSED
PLATFORM_CORE_DEPENDENCY: SATISFIED

CRM_G1: CLOSED_WITH_PRODUCTION_EVIDENCE
CRM_007: CLOSED_WITH_PRODUCTION_EVIDENCE
CRM_007_FINAL_RELEASE_SHA: 4cedf631a3e61f39039615d93cd03c3111213eb9
CRM_007_FINAL_EVIDENCE: docs/crm/evidence/CRM-007-FINAL-PRODUCTION-CLOSURE.md

CRM_008A_DESIGN: CLOSED_APPROVED
CRM_008B_IMPLEMENTATION: MERGED
CRM_008B_ORIGINAL_HEAD_SHA: cf20094dc5998b6b42d20dcbcb16743b07058852
CRM_008B_ORIGINAL_MERGE_SHA: 74c6618a60ecd983086553cf75f71b5a6c8d2c9a
CRM_008R_CORRECTIVE_REMEDIATION: IN_PROGRESS
CRM_008R_ISSUE: #725
CRM_008R_PR: #726 / DRAFT
CRM_008R_MERGE_AUTHORIZED: NO
CRM_008R_DEPLOYMENT_AUTHORIZED: NO

CRM_009_AND_LATER_STAGES: PRESERVED_AND_GOVERNED_SEPARATELY
COMMERCIAL_GO_LIVE: NOT_INFERRED_FROM_THIS_DOCUMENT
```

## 2. Architectural baseline

CRM remains part of the approved modular platform architecture:

- Backend domain: `apps/sanad-platform/src/main/java/com/sanad/platform/crm`.
- Operational UI: `apps/web/app/crm`.
- Authenticated API boundary: `/api/v1/crm/*` and `/api/v2/crm/*`.
- Tenant identity source: authenticated context only.
- Authorization: capability-based and deny-by-default.
- Persistence: PostgreSQL with forward-only Flyway migrations.
- Concurrency: ETag/If-Match plus an atomic database compare-and-set or row lock.
- Audit: central platform AuditPort and governed authorization-failure evidence.
- Timeline: central CRM TimelineEventPort.
- Workflow and AI: central platform integrations; no parallel CRM runtime.

Compatibility fields such as `crm_contacts.account_id`, account/contact primary
email and phone projections and legacy address projections remain transitional.
Removal requires a separate deprecation gate after every caller and generated
client has migrated.

## 3. CRM-007 baseline

CRM-007 delivers canonical owner-scoped addresses and communication methods for
ACCOUNT and PERSON records. The stage includes tenant-safe CRUD, lifecycle,
primary/preferred selection, verification, privacy masking, search, import,
export, Audit, Timeline, compatibility projections, bilingual operational UI,
OpenAPI generation and PostgreSQL upgrade evidence.

Final release evidence:

```text
FINAL_RELEASE_SHA: 4cedf631a3e61f39039615d93cd03c3111213eb9
VERCEL_DEPLOYMENT: dpl_FtG7Pj4MUBNjEFjahPopscqKn7b9
RENDER_DEPLOYMENT: dep-d9gartok1i2s7388lprg
RENDER_IMAGE_DIGEST: sha256:810e69e1c05668ebd9540b71554e13190c837d38004aa3a37dacbde7521cb2cd
CRM_G1_RUN: 29917230857 / SUCCESS
CRM_007_RUN: 29917314330 / SUCCESS
UNEXPECTED_CRM_HTTP_5XX: 0
```

The authoritative CRM-007 execution record is
`docs/crm/EXEC-PROMPT-CRM-007.md` and the immutable production evidence is
`docs/crm/evidence/CRM-007-FINAL-PRODUCTION-CLOSURE.md`.

## 4. CRM-008 baseline

CRM-008A established the approved design for:

- sales teams and memberships;
- work queues, memberships, claim and release;
- territories, hierarchy, closure and assignments;
- versioned deterministic assignment rules;
- manual, rule-driven and bulk assignment;
- immutable ownership history;
- formal ownership transfers and separation of duties;
- tenant isolation, RBAC, audit and OpenAPI governance.

CRM-008B implemented and merged that foundation through PR #691:

```text
IMPLEMENTATION_HEAD_SHA: cf20094dc5998b6b42d20dcbcb16743b07058852
IMPLEMENTATION_MERGE_SHA: 74c6618a60ecd983086553cf75f71b5a6c8d2c9a
FEATURE_HEAD_WORKFLOWS: 23/23 SUCCESS
```

The implementation introduced 13 new ownership tables and upgraded the existing
`crm_assignments` table. The original approved phrase "14 new tables" is
historical design wording; the as-built schema is `13 created + 1 upgraded`.

### Deferred CRM-008 boundaries

- Multi-step approvals remain fail-closed until the real Workflow Engine path is active.
- HRM absence-driven reassignment remains disabled until real HRM integration is authorized.
- Shared/contributor ownership remains deferred to CRM-008C or a separately authorized stage.
- Commercial go-live is not implied by the CRM-008 source merge.

## 5. CRM-008R corrective scope

Post-merge review identified two implementation-hardening requirements:

1. Some ownership controllers validated If-Match against a pre-read value but
   did not keep a database lock or compare-and-set predicate through the final
   mutation, leaving a read/write race window.
2. Teams, queues, transfers and assignment rules materialized unbounded tenant
   collections and truncated them in Java.

CRM-008R corrects these without rewriting historical evidence:

```text
ISSUE: #725
PR: #726 / DRAFT
AUTHORIZED_BASE_SHA: d8c0e8dc330a054cc071c0c9ca2c8b59cf52dae4
ATOMIC_IF_MATCH: IMPLEMENTATION_IN_PROGRESS
DATABASE_CURSOR_PAGINATION: IMPLEMENTATION_IN_PROGRESS
FINAL_CLOSURE_EVIDENCE: PENDING
MERGE_AUTHORIZED: NO
DEPLOYMENT_AUTHORIZED: NO
```

The corrective stage must pass one unchanged exact-head run set, protected merge,
exact-SHA deployment and production concurrency/pagination smoke before its
status changes to closed.

## 6. Migration inventory relevant to CRM-007 and CRM-008

| Flyway version | Purpose | Current repository classification |
|---|---|---|
| `20260717.6` | CRM-G1 extension tables and isolation foundation | MERGED / PRODUCTION RECONCILED |
| `20260717.100` | Canonical addresses and communication methods | MERGED / PRODUCTION VERIFIED |
| `20260717.101` | Address and communication capabilities | MERGED / PRODUCTION VERIFIED |
| `20260718.1` | Forward-only CRM-G1 baseline-gap reconciliation | MERGED / PRODUCTION VERIFIED |
| `20260722.1` | Sales teams and memberships | MERGED |
| `20260722.2` | Queues and memberships | MERGED |
| `20260722.3` | Territories, closure and assignments | MERGED |
| `20260722.4` | Assignment rules and versions | MERGED |
| `20260722.5` | Upgrade assignments and create ownership history | MERGED |
| `20260722.6` | Transfer requests and approval steps | MERGED |
| `20260722.7` | Owner team/queue compatibility columns | MERGED |
| `20260722.8` | Ownership capabilities and roles | MERGED |
| `20260722.9` | Per-tenant/per-rule round-robin counters | MERGED |

CRM-008R does not authorize a new migration by default. Any schema change must
be proven unavoidable and separately authorized. Flyway repair, manual history
editing, destructive rollback and ad-hoc production SQL remain prohibited.

## 7. API and security baseline

- CRM endpoints never accept tenant identity from body or query as authority.
- Unauthenticated requests return 401.
- Missing capabilities return 403.
- Cross-tenant access is concealed or rejected fail-closed.
- Writes requiring idempotency reject missing keys.
- Version-governed writes require If-Match and stale requests return 412.
- CRM-008R makes the 412 decision atomic by holding the target database row lock
  through the mutation transaction.
- Collection cursors are opaque, tenant-bound, filter-bound and use bounded
  PostgreSQL keyset queries.

## 8. Evidence and closure control

CRM-007 is closed and must not be reopened merely because shared infrastructure
is hardened later. CRM-008R remains open until:

- PostgreSQL race tests prove one winner for the same ETag;
- cursor first/middle/final, tampering and cross-tenant tests pass;
- CI, PostgreSQL, security, API contract, authenticated acceptance, Web CI,
  Playwright, performance, backup and readiness workflows all pass on one SHA;
- PR #726 is merged with expected-head protection;
- the exact merge SHA is deployed and production smoke has zero unexplained 5xx;
- `docs/crm/crm-008/evidence/CRM-008B-FINAL-CLOSURE.md` is finalized;
- the current-status blocks of Issue #597 and PR #691 are reconciled without
  deleting their historical execution records.
