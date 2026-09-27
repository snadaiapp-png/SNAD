# G2 Final Recertification Trigger

This governance-only marker exists to force the canonical G2 release gates to execute on a fresh exact SHA after the final product-surface merge and closure of superseded delivery PRs.

- Canonical implementation: PR #1161
- Superseded delivery paths closed: PR #1159, PR #1160
- Governing database path: PostgreSQL Direct
- Required recertification gates:
  - G2 Authenticated Acceptance
  - HRM Human Preview
- No runtime behavior, authorization, RBAC/RLS, tenant isolation, workflow ownership, database schema, API contract, or production data is changed by this marker.

The merge SHA created by this PR is the only SHA eligible for the final `G2_FULLY_CLOSED` declaration after both gates and repository required checks complete successfully.
