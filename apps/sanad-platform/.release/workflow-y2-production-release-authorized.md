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
