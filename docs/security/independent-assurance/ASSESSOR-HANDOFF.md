# Independent Assessor Handoff

## Current status

```text
REM-P0-006: OPEN / NOT_READY
INDEPENDENT_ASSESSOR: NOT_APPOINTED
ASSESSMENT EXECUTION: NOT_STARTED
BROAD COMMERCIAL GO-LIVE: NOT_APPROVED
```

The two GitHub approvals already recorded on PR #551 approve the review-package change only. They do not prove assessor independence, test execution, remediation retest or residual-risk acceptance.

## Before access is granted

Security Governance must record outside this repository:

1. Legal organization and named lead assessor.
2. Relevant competence and testing methodology.
3. Signed conflict-of-interest and independence declaration.
4. Signed rules of engagement and emergency contacts.
5. Approved target allowlist, test window and source IPs.
6. Data-processing, confidentiality, retention and evidence-destruction terms.

Only non-secret references to these records belong in `assessment-manifest.json`. Never commit credentials, tokens, customer data or unrestricted exploit details.

## Minimum access pack

- Exact repository SHA and deployment/configuration identifier.
- API/OpenAPI contracts and architecture/trust-boundary documentation.
- Two isolated test tenants with distinct identities and privilege levels.
- Dedicated non-production or production-like target for destructive cases.
- Read-only access to sanitized security configuration and relevant audit evidence.
- Named Security Governance contact authorized to stop the engagement.

## Required deliverables

1. Executive report with scope, dates, target versions and overall opinion.
2. Technical report with every finding mapped to the coverage matrix.
3. Completed `TEST-COVERAGE-MATRIX.json` with evidence references.
4. Completed `findings-register.json` with severity and ownership.
5. Completed `evidence-index.json` containing sanitized evidence digests.
6. Independent retest statement for every critical/high and remediated finding.
7. Updated assessment manifest and explicit assessor approval or rejection.

## Completion test

The handoff is complete only when closure-mode validation passes on the exact candidate SHA and Security Governance plus Project Owner independently record approval. A GitHub PR approval, successful CI run or healthy deployment alone is insufficient.
