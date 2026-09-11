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


## R0C-12 G16 Human Production Authority Candidate — 2026-09-11

- Certified source baseline SHA: `e658320cb4655717222345fbf8f9fb89ce1b4006`
- G01–G13 certification: `PASS`
- G14 immutable image publication run: `34507878015`
- G14 candidate image digest: `sha256:27aeba95ad278656e95cbaafe4ead8a2131bbc9a679136a1c05615e9698a2eeb`
- G15 production preflight run: `34544777756`
- G15 operational readiness: `PASS`
- Current production deployment remains on the pre-existing live image; this authorization candidate performs no production deployment.
- Runtime application code change in this authorization PR: `NONE`
- Database migration change in this authorization PR: `NONE`
- Security/RBAC semantic change in this authorization PR: `NONE`
- Owner production authorization: `GRANTED_EXPLICITLY_2026-09-11`
- Independent human review: `REQUIRED`
- R0C-13: `FORBIDDEN`

This authorization PR is intentionally inert and exists only to create a protected, reviewable G16 release-control merge under `apps/sanad-platform/**`.

Owner production authorization has been explicitly granted for R0C12 only, bound to certified source baseline `e658320cb4655717222345fbf8f9fb89ce1b4006` and PR #1016, conditional on all required checks passing, independent human approval, protected squash merge with the exact `PRODUCTION-RELEASE-AUTHORIZED` marker, and rollback enabled. R0C13 is explicitly not authorized. The merge remains fail-closed if the source baseline, PR head, required checks, or independent approval changes.

The protected squash merge that grants G16 MUST include the exact commit-message marker `PRODUCTION-RELEASE-AUTHORIZED`. Without that marker the Workflow Y2 Production Release Orchestrator must fail closed.

After an authorized protected merge, the resulting new exact-main SHA becomes the release SHA. Only the canonical chain is permitted:

`Publish Render Backend Image` → immutable exact-SHA image → `Workflow Y2 Production Release Orchestrator` → `production-release.yml` with `rollback_on_failure=true` → post-deploy verification.

No manual Render deployment, no direct production DDL, no Flyway history mutation, no `flyway repair`, no out-of-order migration execution, no branch-protection bypass, no force push, and no R0C-13 action are authorized by this candidate.



## R0C-12 Final Production Alignment Authority — 2026-09-11

- Certified engineering main SHA: `5b02335a4f3936da3f535c07b0017e128f58265e`
- Certified engineering TREE_SHA: `50a3596e36b55fb67758096ccd1fd5da56238c3d`
- Canonical Gate G run: `34589848973` — `PASS`
- Canonical Gate G acceptance: `31/31`, `0F/0E/0S`
- Engineering PR: `#1021` — `MERGED`
- Post-Merge Main Verification run: `34593816585` — `PASS`
- Main CI run: `34593816490` — `PASS`
- Engineering exact-SHA image publication run: `34593816498` — `PASS`
- Current production remains on the previously authorized live image until this authorization PR is protected-merged and the canonical production release succeeds.
- Runtime application code change in this authorization PR: `NONE`
- Database migration change in this authorization PR: `NONE`
- Security/RBAC semantic change in this authorization PR: `NONE`
- Owner final production/commercial alignment authorization: `GRANTED_EXPLICITLY_2026-09-11`
- Independent human review: `REQUIRED`
- R0C-13 implementation/release authorization: `FORBIDDEN`

This authorization PR is intentionally inert and changes only this release-control marker. It exists to create the protected release-control main commit required by the canonical production orchestrator.

Owner authority is explicitly granted for final R0C12 production alignment and commercial closure, bound to certified engineering main `5b02335a4f3936da3f535c07b0017e128f58265e`, conditional on exact-head required checks passing, independent write-access approval, protected squash merge, rollback enabled, and final production verification.

The protected squash merge MUST contain the exact immutable commit-message marker `PRODUCTION-RELEASE-AUTHORIZED`. Without that marker, production release remains fail-closed.

Only the canonical release chain is authorized:

`Publish Render Backend Image` → immutable exact-main image → `Workflow Y2 Production Release Orchestrator` → `production-release.yml` with `rollback_on_failure=true` → Render exact-image verification → readiness → Flyway invariants → security boundary → SCP production smoke → Vercel Control Plane/BFF → sanitized release evidence.

No manual Render deployment, no direct production DDL, no Flyway history mutation, no `flyway repair`, no out-of-order migration execution, no force push, no branch-protection bypass, and no R0C-13 production action are authorized.
