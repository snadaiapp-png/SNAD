# CRM-G2 Stage Report — CRM-003R Corrective Closure

## Governance identity

```text
REPORT_DATE: 2026-07-26
ORIGINAL_IMPLEMENTATION_PR: #502
ORIGINAL_MERGE_SHA: e441e18948a2ba9a9f0e3a018b1bbe4473e2d93f
CORRECTIVE_CONTROL_ISSUE: #771
CORRECTIVE_PULL_REQUEST: #773
CORRECTIVE_BRANCH: fix/crm-003r-real-keyset-closure
CLOSURE_AUTHORITY: immutable PR #773 and Issue #771 records
```

## Root cause corrected

The original CRM v2 list contract decoded and validated opaque cursors but did
not apply the cursor position to the query or result set. A client could receive
the first page again after submitting the server-issued next cursor. The original
codec tests did not exercise page-one/page-two endpoint traversal and therefore
could not detect the defect.

CRM-003R replaces that behavior with tenant-scoped PostgreSQL keyset pagination.
The decoded sort value and UUID tie-breaker now participate in the database
predicate; direction and endpoint-specific sort expressions are applied in SQL;
filters are bound into cursor scope; and every page uses deterministic ordering
with one-row lookahead.

## Corrective implementation

- Real keyset pagination for the nine affected CRM v2 collection operations.
- Tenant, endpoint, sort, direction and filter-bound opaque cursors.
- Deterministic `(sortExpression, id)` ordering.
- `limit + 1` lookahead and non-repeating next cursor generation.
- Cross-tenant, filter-drift, malformed and tampered cursor rejection.
- Custom-field list traversal implemented.
- Unsupported custom-field boolean status sorting fails closed as a governed
  validation error after capability authorization.
- Runtime OpenAPI marks declared CRM POST `Idempotency-Key` parameters required,
  matching fail-closed runtime behavior.
- PostgreSQL 16 acceptance proves stable traversal, zero overlap, no gaps,
  tied-value ordering, ASC/DESC parity, tenant isolation and filter preservation.
- The dedicated workflow rejects missing reports, zero tests, failures, errors
  and skipped critical tests.
- Playwright is now triggered by governed CRM backend-contract and evidence
  changes and verifies the exact PR head.

## Closure matrix

| Requirement | Required result | Executable evidence |
|---|---|---|
| REAL_KEYSET_PAGINATION | PASS | SQL keyset aspect and PostgreSQL traversal |
| PAGE_1_PAGE_2_OVERLAP | 0 | page-ID intersection assertion |
| CURSOR_PROGRESS_FAILURES | 0 | cursor-change and bounded traversal assertions |
| STABLE_DATASET_GAPS | 0 | complete stable-dataset traversal |
| TENANT_ISOLATION | PASS | row isolation and cross-tenant cursor rejection |
| ASC_DESC_TRAVERSAL | PASS | descending result equals reverse ascending result |
| FILTER_PRESERVATION | PASS | filtered traversal and changed-filter rejection |
| OPENAPI_PARAMETER_DRIFT | 0 | runtime semantic parity test and contract workflow |
| POSTGRESQL_ACCEPTANCE | PASS | PostgreSQL 16 Testcontainers workflow |
| FAILED_REQUIRED_WORKFLOWS | 0 | final exact-head workflow inventory |
| PENDING_REQUIRED_WORKFLOWS | 0 | final exact-head workflow inventory |
| SKIPPED_CRITICAL_TESTS | 0 | Surefire XML assertion |

## Closure rule

```text
CRM_003R = CLOSED_COMPLETED
CRM_G2 = CLOSED_COMPLETED
```

only when PR #773 is merged using `expected_head_sha` equal to the fully verified
unchanged head and Issue #771 is reconciled and closed as `completed`. Before
those two external immutable events, the status remains `OPEN_BLOCKED`.

This conditional rule prevents a report commit from falsely certifying its own
SHA. Exact final SHA, workflow run IDs, artifact digest and merge SHA are written
to the PR and issue timeline after the checks settle.

## Governance boundary

This closes the CRM-G2 repository-delivery gate only. Commercial go-live remains
subject to later production and release governance.
