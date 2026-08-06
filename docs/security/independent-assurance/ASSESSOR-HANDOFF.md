# Independent Assessor Handoff

## Current boundary

```text
REM-P0-006: OPEN / NOT_READY
INDEPENDENT_ASSESSOR: NOT_APPOINTED
ASSESSMENT_EXECUTION: NOT_STARTED
BROAD_COMMERCIAL_GO_LIVE: NOT_APPROVED
```

Repository approvals approve only the review-package change. They do not prove assessor independence, assessment execution, remediation retest or residual-risk acceptance.

## Before access is granted

Security Governance must verify and retain outside the repository:

1. Legal organization and named lead assessor.
2. Relevant competence and testing methodology.
3. Signed conflict-of-interest and independence declaration.
4. Signed rules of engagement and emergency contacts.
5. Approved target allowlist, test window and source IPs.
6. Data-processing, confidentiality, retention and evidence-destruction terms.

Only sanitized or immutable non-secret references belong in the evidence index. Never commit credentials, tokens, customer data or unrestricted exploit material.

## Minimum access pack

- Exact repository SHA and deployment/configuration identifier.
- API contracts and architecture/trust-boundary documentation.
- Two isolated tenants with multiple roles and privilege levels.
- A production-like target for destructive or high-impact cases.
- Read-only access to sanitized configuration and audit evidence.
- A named Security Governance contact authorized to stop the engagement.

## Required deliverables

- Executive and technical assessment reports.
- Completed coverage matrix and findings register.
- Evidence index with content digests or immutable restricted references.
- Independent retest statement for every remediated material finding.
- Assessor appointment and independence evidence.
- Separate assessor, Security Governance and Project Owner approvals.

The handoff is complete only when protected closure validation passes on `main` for the exact assessed release and a governed closure decision is published.
