# CRM-003 / CRM-003R — Final Independent Verification Protocol

## Corrected historical record

```text
ORIGINAL_PR: #502
ACTUAL_ORIGINAL_FINAL_HEAD: 5cba9afe92fbb6765119c16706fbaee49b06104b
ORIGINAL_MERGE_SHA: e441e18948a2ba9a9f0e3a018b1bbe4473e2d93f
FORMER_DOCUMENTED_HEAD: ad1ce3d50096d338bc26cfc6c49829def92e8105
CORRECTIVE_ISSUE: #771
CORRECTIVE_PR: #773
```

The former document was a pre-merge approval record for an intermediate SHA. It
was not a valid post-merge CRM-G2 closure record and is superseded by this
protocol plus the immutable PR #773 and Issue #771 timeline.

## Independent verification scope

The final corrective head must independently pass:

- CI;
- Web CI;
- Security Baseline;
- Security Scan (OWASP);
- CRM API Contract Validation;
- CRM Authenticated Acceptance;
- Playwright E2E & Visual Regression;
- CRM Deployment Readiness;
- Backup Restore Validation;
- CRM-003R PostgreSQL keyset and OpenAPI semantic parity;
- governance drift diagnostics;
- compile and architecture regression gates triggered by the repository.

## Non-negotiable proof conditions

```text
CHECKED_OUT_SHA_EQUALS_PR_HEAD: TRUE
POSTGRESQL_VERSION: 16
ZERO_TEST_RUN: PROHIBITED
FAILED_ACCEPTANCE_TESTS: 0
ERRORED_ACCEPTANCE_TESTS: 0
SKIPPED_ACCEPTANCE_TESTS: 0
FAILED_REQUIRED_WORKFLOWS: 0
PENDING_REQUIRED_WORKFLOWS: 0
OPEN_BLOCKING_REVIEW_THREADS: 0
MERGE_EXPECTED_HEAD_MATCH: TRUE
```

## Closure decision rule

CRM-G2 is closed only when all of the following become true:

1. PR #773 exact head is unchanged after every required workflow settles as
   `completed/success`.
2. The PostgreSQL acceptance artifact proves test execution and zero skipped
   critical cases.
3. Review-thread inspection returns no unresolved blockers.
4. PR #773 is merged using `expected_head_sha` equal to that verified head.
5. Issue #771 is reconciled with exact head, workflow run IDs, merge SHA and is
   closed with reason `completed`.

The exact immutable identities are intentionally recorded in the GitHub control
records after the checks settle rather than inserted into a self-modifying file.
