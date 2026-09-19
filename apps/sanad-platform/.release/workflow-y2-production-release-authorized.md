# Workflow Y2 Production Release Authorization Marker

This file is an inert release-control marker.

It does not change application runtime behavior. Its presence under `apps/sanad-platform/**` ensures the protected merge that introduces the production orchestrator also triggers `Publish Render Backend Image` for that exact merge SHA.

The production orchestrator additionally requires the immutable squash-merge commit message marker `PRODUCTION-RELEASE-AUTHORIZED`; this file alone never authorizes a production deployment.

## Exact-main authorization — 2026-09-19

- Certified source main SHA: `89d7226e31a10a13bac8fb1d055a63ecf83eb731`
- Source main CI run: `35430349131` — `SUCCESS`
- Source Post-Merge Main Verification run: `35430349186` — `SUCCESS`
- Source exact-SHA Publish Render Backend Image run: `35430349122` — `SUCCESS`
- Prior Workflow Y2 Production Release Orchestrator run: `35430411766` — `FAIL_CLOSED`; it MUST NOT be rerun.
- Runtime application behavior change in this authorization PR: `NONE`
- Database migration change in this authorization PR: `NONE`
- Security/RBAC semantic change in this authorization PR: `NONE`
- Independent human review: `REQUIRED`
- Exact-head required checks: `REQUIRED_PASS`
- Pre-merge current-main drift guard: `REQUIRED_PASS`
- Rollback on failure: `REQUIRED=true`

This authorization change is intentionally inert and changes only this release-control marker. It exists solely to create a protected, reviewable release-control merge bound to the exact certified source main above.

The protected squash merge MUST contain the exact immutable commit-message marker `PRODUCTION-RELEASE-AUTHORIZED`. Without that marker, the production orchestrator must fail closed.

After protected merge, the resulting exact main SHA becomes the release-control SHA. Only the canonical path is authorized:

`Publish Render Backend Image` → exact immutable SHA image → canonical SANAD Production Release with rollback enabled → exact-image Render verification → Flyway/runtime invariants → security/SCP/Vercel release evidence → NEW Workflow Y2 Vercel Production Certification.

No manual Render deployment, direct production DDL, Flyway history mutation, `flyway repair`, out-of-order migration execution, force push, branch-protection bypass, catalog/subscription mutation, or Workflow Final Gate baseline update is authorized by this marker.

If source main, this PR head, required CI, independent approval, immutable-image evidence, or production target changes before merge, this authorization fails closed and must be re-evaluated.
