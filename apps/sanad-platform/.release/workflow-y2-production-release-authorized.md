# Workflow Y2 Production Release Authorization Marker

This file is an inert release-control marker.

It does not change application runtime behavior. Its presence under `apps/sanad-platform/**` ensures the protected merge that introduces the production orchestrator also triggers `Publish Render Backend Image` for that exact merge SHA.

The production orchestrator additionally requires the immutable squash-merge commit message marker `PRODUCTION-RELEASE-AUTHORIZED`; this file alone never authorizes a production deployment.

## R0C-12 Production Authorization — 2026-09-09

- Source protected release: PR #989
- Certified source head: `38f6d9bff4bbae5b2dbb3acc60ef91e1db9e68d1`
- Certified source TREE_SHA: `9b4e55717fb83dd41d4cf2b7e767c210ca2df836`
- Protected release merge SHA: `0e20697394d58d75bc7084b491d93112db983309`
- R0C-12 protected release: `CLOSED`
- Owner production authorization: `GRANTED`
- Runtime application code change in this authorization PR: `NONE`
- Database migration change in this authorization PR: `NONE`
- Security/RBAC semantic change in this authorization PR: `NONE`
- Production deployment must occur only through the canonical `production-release.yml` path with rollback enabled.
- R0C-13 remains `FORBIDDEN` and is not authorized by this marker.

The protected squash merge that authorizes deployment MUST include the exact commit-message marker `PRODUCTION-RELEASE-AUTHORIZED`. Any other merge result remains unauthorized for production.

## Flyway incident-closure release candidate — 2026-09-09

- Remediation source PR: `#1006`
- Certified current-main source head: `86c25a809a0b3d2077ff525d65587216d0dff76b`
- Certified source TREE_SHA: `32a153b460da07b72e27077b94f47c1e4f41a7ea`
- Flyway ledger-head regression fix: `MERGED_AND_EXACT_HEAD_VERIFIED`
- Frontend dependency security remediation: `MERGED_AND_SECURITY_BASELINE_GREEN`
- Runtime application code change in this authorization PR: `NONE`
- Database migration change in this authorization PR: `NONE`
- Security/RBAC semantic change in this authorization PR: `NONE`
- This authorization PR is intentionally inert and exists to create a protected, reviewable release-control merge under `apps/sanad-platform/**`, which triggers exact-SHA backend image publication.
- Owner production authorization is NOT self-granted by this file. The protected squash merge must only be executed after an explicit owner authorization gate has been satisfied.
- The protected squash merge MUST include the exact commit-message marker `PRODUCTION-RELEASE-AUTHORIZED`; without that marker the production orchestrator fails closed.
- Production deployment must occur exclusively through `production-release.yml` with `rollback_on_failure=true`.
- No manual Render deployment, no Flyway history mutation, no `flyway repair`, and no out-of-order migration execution are authorized.
