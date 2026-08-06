# CRM-003R Corrective Test Matrix

The following assertions are executable acceptance requirements and must pass on
one unchanged exact pull-request head.

| Gate | Executable assertion |
|---|---|
| REAL_KEYSET_PAGINATION | decoded cursor boundary participates in PostgreSQL `WHERE` predicate |
| PAGE_1_PAGE_2_OVERLAP | intersection of page-one and page-two IDs is empty |
| CURSOR_PROGRESS_FAILURES | consecutive non-terminal cursors differ and traversal terminates |
| STABLE_DATASET_GAPS | complete traversal contains every stable tenant record exactly once |
| TENANT_ISOLATION | tenant B cannot consume tenant A cursor or observe tenant A rows |
| ASC_DESC_TRAVERSAL | descending traversal is the exact reverse of ascending traversal |
| FILTER_PRESERVATION | filtered pages remain filtered and cursor reuse with changed filter fails |
| OPENAPI_PARAMETER_DRIFT | CRM POST `Idempotency-Key` runtime parameter is required where declared |
| POSTGRESQL_ACCEPTANCE | tests run against PostgreSQL 16, not H2 or mocks |
| FAILED_REQUIRED_WORKFLOWS | zero before merge |
| PENDING_REQUIRED_WORKFLOWS | zero before merge |
| SKIPPED_CRITICAL_TESTS | dedicated workflow asserts skipped count is zero |

The dedicated workflow also fails on missing Surefire reports or a zero-test run.
