# CRM-G2 Stage Report — CRM-003R Corrective Closure

## Current decision

```text
REPORT_DATE: 2026-07-26
CONTROL_ISSUE: #771
CORRECTIVE_BRANCH: fix/crm-003r-real-keyset-closure
EXACT_CORRECTIVE_SHA: PENDING_FINAL_UNCHANGED_HEAD
CRM_G2_FINAL_CLOSURE: WITHHELD_PENDING_EXACT_HEAD_ACCEPTANCE
```

This report is intentionally fail-closed. It records the corrective implementation,
but it does not declare CRM-G2 closed until one unchanged pull-request head passes
all required repository and CRM-003R acceptance gates.

## Corrective implementation

- Replaced validation-only cursor handling for the affected CRM v2 collection
  operations with tenant-scoped PostgreSQL keyset queries.
- Applied the decoded `(sortValue, tieBreakerId)` boundary to each subsequent query.
- Applied endpoint-specific allowlisted sort expressions and ascending/descending
  direction at the database layer.
- Preserved endpoint filters in a cursor-bound scope so filter drift fails closed.
- Added deterministic `(sortExpression, id)` ordering and `limit + 1` lookahead.
- Implemented custom-field cursor traversal rather than returning a permanently
  empty page descriptor.
- Added PostgreSQL acceptance tests for page progression, zero overlap, stable
  traversal, tied values, tenant isolation, filter preservation, tamper rejection,
  and ascending/descending parity.
- Added runtime OpenAPI correction and semantic tests for required
  `Idempotency-Key` headers.
- Added a dedicated non-skippable CRM-003R GitHub Actions workflow.

## Closure matrix

| Requirement | Current state | Evidence |
|---|---|---|
| REAL_KEYSET_PAGINATION | implemented / awaiting exact-head CI | `CrmCoreCursorPaginationAspect` |
| PAGE_1_PAGE_2_OVERLAP | awaiting PostgreSQL acceptance | `CrmCoreKeysetPaginationPostgresTest` |
| CURSOR_PROGRESS_FAILURES | awaiting PostgreSQL acceptance | multi-page traversal tests |
| STABLE_DATASET_GAPS | awaiting PostgreSQL acceptance | full stable-dataset traversal |
| TENANT_ISOLATION | awaiting PostgreSQL acceptance | cross-tenant cursor rejection |
| ASC_DESC_TRAVERSAL | awaiting PostgreSQL acceptance | inverse traversal assertion |
| FILTER_PRESERVATION | awaiting PostgreSQL acceptance | search-bound cursor tests |
| OPENAPI_PARAMETER_DRIFT | awaiting semantic gate | runtime customizer and test |
| POSTGRESQL_ACCEPTANCE | awaiting GitHub Actions | `CRM-003R Corrective Acceptance` |
| FAILED_REQUIRED_WORKFLOWS | unknown until final head settles | must equal 0 |
| PENDING_REQUIRED_WORKFLOWS | unknown until final head settles | must equal 0 |
| SKIPPED_CRITICAL_TESTS | unknown until final head settles | must equal 0 |

## Finalization rule

This file may be updated to `CLOSED_COMPLETED` only after the exact corrective SHA,
workflow run IDs, zero-failure matrix, merge SHA, and post-merge verification are
recorded. Until then:

```text
CRM_003R: OPEN
CRM_G2: OPEN_BLOCKED
FALSE_CLOSURE: PROHIBITED
```
