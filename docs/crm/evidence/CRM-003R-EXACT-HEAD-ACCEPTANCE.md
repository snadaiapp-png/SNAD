# CRM-003R — Exact-Head Acceptance Record

## Control identity

```text
CONTROL_ISSUE: #771
CORRECTIVE_PULL_REQUEST: #773
BASELINE_REVIEW_PR: #772
ORIGINAL_IMPLEMENTATION_PR: #502
FINAL_IDENTITY_LOCATION: PR #773 and Issue #771 immutable timelines
```

A source file cannot truthfully embed the SHA created by the commit that changes
that same source file. Therefore the exact final head, run IDs, artifact digest
and merge SHA are recorded in the GitHub control records after all checks settle.
This document defines the acceptance contract and the evidence sources that must
be reconciled there.

## Required exact-head workflows

- CI
- Web CI
- Security Baseline
- Security Scan (OWASP)
- CRM API Contract Validation
- CRM Authenticated Acceptance
- Playwright E2E & Visual Regression
- CRM Deployment Readiness
- Backup Restore Validation
- CRM-003R Corrective Acceptance

The repository-triggered compile, architecture, provenance, process and other
regression workflows must also contain no failure on the verified head.

## Required settled state

```text
CHECKED_OUT_SHA_EQUALS_PR_HEAD: TRUE
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
OPEN_REVIEW_THREADS: 0
EXPECTED_HEAD_MERGE: PASS
```

## Evidence sources

- The `CRM-003R Corrective Acceptance` artifact contains Maven output and
  Surefire XML from PostgreSQL 16 and OpenAPI semantic tests.
- The Playwright workflow verifies the same exact PR head and publishes its HTML
  report and failure artifacts.
- `fetch_commit_workflow_runs` inventory on the final head proves failed and
  pending workflow counts.
- PR review-thread inventory proves unresolved review count.
- The expected-head merge result proves the verified head was the merged head.
- Issue #771 contains the final reconciled matrix and merge identity.

Any mismatch keeps CRM-003R and CRM-G2 open.
