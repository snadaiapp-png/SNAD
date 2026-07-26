# CRM-003 — API Contract and Concurrency Evidence

## Authoritative identity

```text
ORIGINAL_IMPLEMENTATION_PR: #502 / MERGED
ORIGINAL_FINAL_HEAD_SHA: 5cba9afe92fbb6765119c16706fbaee49b06104b
ORIGINAL_MERGE_SHA: e441e18948a2ba9a9f0e3a018b1bbe4473e2d93f
CORRECTIVE_CONTROL_ISSUE: #771
CORRECTIVE_PULL_REQUEST: #773
CORRECTIVE_BRANCH: fix/crm-003r-real-keyset-closure
CRM_G2_CLOSURE_RULE: PR #773 must merge with expected-head protection and Issue #771 must close as completed
```

This document supersedes the former pre-push placeholders and the former claim
that cursor behavior was only a performance optimization. The post-merge review
proved that the decoded cursor position was not consumed, which was a contract
correctness defect. CRM-003R corrects that defect and adds executable acceptance.

## Delivered contract foundation

CRM-003 preserves the following valid capabilities from PR #502:

- typed `/api/v2/crm` request and response DTOs;
- external `camelCase` JSON contracts;
- sanitized CRM error envelope and governed error catalog;
- strong entity/version ETags and fail-closed `If-Match` validation;
- version-scoped atomic SQL mutations;
- database-backed idempotency scoped by tenant, principal, endpoint and key;
- exact replay of status, body, headers and content type;
- committed OpenAPI and generated TypeScript contracts;
- tenant and capability boundaries;
- dedicated contract, security and regression workflows.

## CRM-003R corrective implementation

PR #773 adds:

1. PostgreSQL keyset pagination for accounts, contacts, leads, opportunities,
   activities, timeline, imports, import errors and custom fields.
2. Database predicates that consume the decoded `(sortValue, tieBreakerId)`
   cursor boundary.
3. Tenant-scoped queries with endpoint filters preserved across pages.
4. Endpoint-specific allowlisted sort expressions and direction.
5. Deterministic `(sortExpression, id)` ordering and `limit + 1` lookahead.
6. Cursor scopes bound to tenant, endpoint, sort, direction and filters.
7. Cross-tenant, filter-drift and tamper rejection.
8. Runtime OpenAPI requiredness alignment for `Idempotency-Key`.
9. A fail-closed custom-field sort boundary that prevents boolean/string SQL
   coercion from becoming an ungoverned server error.
10. A dedicated exact-head PostgreSQL 16 acceptance workflow with zero-test and
    skipped-test guards.
11. Playwright triggering for governed CRM backend-contract and evidence changes,
    so UI regression evidence cannot be reused from an earlier SHA.

## Executable acceptance requirements

One unchanged exact corrective head must prove:

```text
REAL_KEYSET_PAGINATION: PASS
PAGE_1_PAGE_2_OVERLAP: 0
CURSOR_PROGRESS_FAILURES: 0
STABLE_DATASET_GAPS: 0
TENANT_ISOLATION: PASS
ASC_DESC_TRAVERSAL: PASS
FILTER_PRESERVATION: PASS
OPENAPI_PARAMETER_DRIFT: 0
POSTGRESQL_ACCEPTANCE: PASS
FAILED_REQUIRED_WORKFLOWS: 0
PENDING_REQUIRED_WORKFLOWS: 0
SKIPPED_CRITICAL_TESTS: 0
```

The exact final head, immutable workflow run IDs, artifact digest, merge SHA and
post-merge decision are recorded in PR #773 and Issue #771. This avoids embedding
a self-referential SHA that would change merely by writing it into this file.

## Governance boundary

CRM-G2 repository closure does not independently authorize commercial go-live.
Later CRM stages remain governed by their own acceptance records.
