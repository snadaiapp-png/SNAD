# CRM-008B — Final Closure Evidence

> **Status:** `PENDING_CRM_008R_CORRECTIVE_ACCEPTANCE`  
> **Evidence authority:** Issue #725 and PR #726  
> **Created:** 2026-07-26

This record intentionally remains `PENDING`. It must not be changed to PASS,
CLOSED or PRODUCTION_VERIFIED until the corrective merge SHA exists and the
post-merge production gates have completed on that exact SHA.

## 1. Historical design and implementation

```text
CRM_008A_DESIGN_DELIVERY_PR: #591
CRM_008A_FINAL_CORRECTION_PR: #593
CRM_008A_SECURITY_REMEDIATION_PR: #594
CRM_008A_DESIGN_CLOSURE: COMPLETED

CRM_008B_AUTHORIZATION_ISSUE: #597
CRM_008B_IMPLEMENTATION_PR: #691
CRM_008B_IMPLEMENTATION_HEAD_SHA: cf20094dc5998b6b42d20dcbcb16743b07058852
CRM_008B_IMPLEMENTATION_MERGE_SHA: 74c6618a60ecd983086553cf75f71b5a6c8d2c9a
CRM_008B_FEATURE_HEAD_WORKFLOWS: 23/23 SUCCESS
```

## 2. As-built schema statement

```text
OWNERSHIP_TABLES_CREATED: 13
EXISTING_CRM_ASSIGNMENTS_TABLE_UPGRADED: 1
AUTHORIZED_MIGRATIONS: V20260722_1 THROUGH V20260722_9
MIGRATION_POLICY: FORWARD_ONLY
FLYWAY_REPAIR_USED: NO
SCHEMA_HISTORY_EDIT_USED: NO
MANUAL_PRODUCTION_SQL_USED: NO
```

The design-stage phrase "14 new tables" included `crm_assignments`, which
already existed in the CRM-G1 baseline. The authoritative as-built wording is
`13 newly created + 1 existing table upgraded`.

## 3. Delivered CRM-008B scope

- Sales teams and memberships.
- Queues, queue memberships, claims and releases.
- Territories, hierarchy, closure and assignments.
- Versioned assignment rules and deterministic distribution.
- Manual, rule-driven and bulk ownership assignment.
- Immutable ownership history.
- Formal ownership transfer requests, approval steps and separation of duties.
- 17 ownership capabilities and the governed sales roles.
- 38 governed ownership API operations.
- PostgreSQL concurrency, tenant-isolation and migration acceptance.

## 4. Deferred boundaries

```text
MULTI_STEP_APPROVAL_WITH_STUB_WORKFLOW: FAIL_CLOSED
HRM_ABSENCE_REASSIGNMENT_WITH_STUB_HRM: DISABLED
SHARED_CONTRIBUTOR_OWNERSHIP: DEFERRED
COMMERCIAL_GO_LIVE: NOT_AUTHORIZED_BY_THIS_RECORD
```

## 5. CRM-008R corrective stage

```text
CRM_008R_ISSUE: #725
CRM_008R_PR: #726
CRM_008R_AUTHORIZED_BASE_SHA: d8c0e8dc330a054cc071c0c9ca2c8b59cf52dae4
CRM_008R_CORRECTIVE_HEAD_SHA: PENDING
CRM_008R_CORRECTIVE_MERGE_SHA: PENDING
ATOMIC_IF_MATCH_EVIDENCE: PENDING
CURSOR_PAGINATION_EVIDENCE: PENDING
EXACT_HEAD_WORKFLOWS: PENDING
POST_MERGE_PRODUCTION_PROOF: PENDING
```

CRM-008R must prove that every governed If-Match mutation keeps a PostgreSQL row
lock or equivalent compare-and-set authority through the final mutation, and
that the same ETag cannot produce two successful writes.

Teams, queues, transfers and assignment-rule lists must use bounded PostgreSQL
keyset queries with opaque tenant/filter-bound cursors. No target endpoint may
materialize an unbounded tenant result merely to truncate it in Java.

## 6. Required exact-head workflow evidence

| Workflow | Run ID | Result |
|---|---:|---|
| CI / Maven | PENDING | PENDING |
| PostgreSQL Acceptance | PENDING | PENDING |
| CRM Authenticated Acceptance | PENDING | PENDING |
| CRM API Contract Validation | PENDING | PENDING |
| CRM G1 Schema Isolation | PENDING | PENDING |
| Security Baseline | PENDING | PENDING |
| Security Scan (OWASP) | PENDING | PENDING |
| Development Security Acceptance | PENDING | PENDING |
| Auth Session Reliability | PENDING | PENDING |
| Web CI | PENDING | PENDING |
| Playwright E2E & Visual Regression | PENDING | PENDING |
| Performance Baseline | PENDING | PENDING |
| Backup Restore Validation | PENDING | PENDING |
| CRM Modular Architecture Validation | PENDING | PENDING |
| Production Readiness Gate | PENDING | PENDING |

## 7. Required production evidence

```text
CORRECTIVE_MERGE_SHA: PENDING
VERCEL_DEPLOYMENT_ID: PENDING
VERCEL_GITHUB_COMMIT_SHA_MATCH: PENDING
RENDER_DEPLOYMENT_ID: PENDING
RENDER_IMAGE: PENDING
RENDER_IMAGE_DIGEST: PENDING
FLYWAY_READ_ONLY_STATE: PENDING
TWO_TENANT_ISOLATION: PENDING
SAME_ETAG_RACE_ONE_WINNER: PENDING
STALE_ETAG_HTTP_412: PENDING
CURSOR_FIRST_MIDDLE_FINAL: PENDING
TAMPERED_CURSOR_REJECTION: PENDING
CROSS_TENANT_CURSOR_REJECTION: PENDING
UNEXPECTED_CRM_HTTP_5XX: PENDING
```

## 8. Final approvals

```text
PRODUCT_APPROVAL: PENDING
QA_APPROVAL: PENDING
SECURITY_APPROVAL: PENDING
OPEN_REVIEW_THREADS: PENDING
EXPECTED_HEAD_MERGE_PROTECTION: PENDING
ISSUE_597_BODY_RECONCILED: PENDING
PR_691_BODY_RECONCILED: PENDING
CRM_008_FINAL_CLOSURE: NO
```

The final evidence update must replace every PENDING field with an immutable run,
artifact, deployment, digest or approval reference. Unsupported PASS claims are
prohibited.
