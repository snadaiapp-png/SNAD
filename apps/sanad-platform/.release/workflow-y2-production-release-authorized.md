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



## R0C-12 Current-Main Production Alignment Authority — 2026-09-11

- Current certified main SHA: `a244d02aacd1789214e0e0de3cc6ffc936b5db93`
- Current certified main TREE_SHA: `3d9bfde9889709fcf9bb4db511eed92518873cd8`
- Parent production-aligned SHA: `da7404367463b3c3c037ae7a13c41a90466dc52a`
- Corrective source PR: `#1020`
- Main CI run: `34602967128` — `PASS`
- Post-Merge Main Verification run: `34602967213` — `PASS`
- Playwright E2E/Visual run: `34602966943` — `PASS`
- Web CI run: `34602966979` — `PASS`
- CRM Deployment Readiness run: `34602967015` — `PASS`
- Stage 07 Provenance run: `34602967095` — `PASS`
- Exact-SHA image publication run: `34602967013` — `PASS`
- Existing production remains on `da7404367463b3c3c037ae7a13c41a90466dc52a` until this authorization PR is protected-merged and the canonical release verifies the new exact-main image live.
- Runtime application code change in this authorization PR: `NONE`
- Database migration change in this authorization PR: `NONE`
- Security/RBAC semantic change in this authorization PR: `NONE`
- Owner production-alignment authorization for the current correct main: `GRANTED_EXPLICITLY_2026-09-11`
- Independent human review with write access: `REQUIRED`
- R0C-13 release authorization: `FORBIDDEN`

This authorization PR is intentionally inert and changes only this release-control marker. It exists solely to align production with the current certified main after PR #1020.

The protected squash merge MUST contain the exact immutable marker `PRODUCTION-RELEASE-AUTHORIZED`. Without it, the production orchestrator must fail closed.

Only the canonical path is authorized:

`Publish Render Backend Image` → exact immutable image → `Workflow Y2 Production Release Orchestrator` → `production-release.yml` with `rollback_on_failure=true` → Render exact-image verification → readiness → Flyway invariants → security boundary → SCP production contract smoke → Vercel Control Plane/BFF → sanitized release evidence.

No manual Render deployment, direct production DDL, Flyway history mutation, `flyway repair`, out-of-order migration execution, force push, branch-protection bypass, or R0C-13 production action is authorized.


## Workflow FIC Final Production Alignment Authority — 2026-09-13

- Historical Workflow FIC certified SHA: `b2da8ebed5b0f4e1734bc4ebc9c983aabca428f0`
- Current production-alignment source base: `a7fd5c0428ea6c774ff52e564cd791f9d1faf3a3`
- Corrected FIC evidence: `docs/certification/WORKFLOW-FIC-FINAL-EVIDENCE-CORRECTED.md`
- Owner final production deployment authorization: `GRANTED_EXPLICITLY_2026-09-13`
- Independent human review with repository write access: `REQUIRED`
- Release-control PR exact-head required checks: `REQUIRED_PASS`
- Pre-merge current-main drift guard: `REQUIRED_PASS`
- Runtime application behavior change in this authorization change: `NONE`
- Database migration change in this authorization change: `NONE`
- Security/RBAC semantic change in this authorization change: `NONE`
- Rollback on failure: `REQUIRED=true`
- `LIVE_AUTONOMOUS_AI=OFF`

This authorization is intentionally limited to the protected canonical production deployment path. It does not rewrite the historical FIC SHA and does not fabricate unresolved external/commercial evidence.

The protected squash merge MUST contain the exact immutable commit-message marker `PRODUCTION-RELEASE-AUTHORIZED`. Without that marker, the production orchestrator must fail closed.

Only the canonical release chain is authorized:

`Publish Render Backend Image` → exact immutable SHA image → `Workflow Y2 Production Release Orchestrator` → `production-release.yml` with `rollback_on_failure=true` → exact-image Render verification → readiness → Flyway runtime invariants → security boundary → SCP production smoke → Vercel Control Plane/BFF → sanitized release evidence.

No manual Render deployment, direct production DDL, Flyway history mutation, `flyway repair`, out-of-order migration execution, force push, branch-protection bypass, or autonomous AI activation is authorized.

If the release-control PR head, current `main`, required CI, independent approval, or exact-image evidence changes before merge, this authorization fails closed and must be re-evaluated.


## Current-Main Subscription Correctness Production Authority — 2026-09-13

- Current protected main SHA: `cc9cdb0254aca0919e2818f71ebb8173ab922934`
- Runtime backend source change SHA: `381ca82efd692411bd9cac6936996731524d1e04`
- Backend-changing PR: `#1047` — comprehensive subscription correctness repair
- Backend source PR independent review: `APPROVED`
- Backend source PR required CI: `PASS`
- Immutable backend image publication run for `381ca82...`: `34771630651` — `PASS`
- Production release orchestrator run `34771697684`: `FAIL_CLOSED` before dispatch because the exact main commit did not contain `PRODUCTION-RELEASE-AUTHORIZED`
- Workflow production-certification correction PR: `#1052`
- Workflow certification correction independent review: `APPROVED`
- Workflow certification correction required CI: `PASS`
- Current Render production backend remains on the previously authorized immutable image until this release-control PR is protected-merged and the canonical release succeeds.
- Runtime application code change in this authorization PR: `NONE`
- Database migration change in this authorization PR: `NONE`
- Security/RBAC semantic change in this authorization PR: `NONE`
- Owner production authorization: `GRANTED_EXPLICITLY_2026-09-13`
- Independent human review with repository write access: `REQUIRED`
- Exact-head required checks: `REQUIRED_PASS`
- Rollback on failure: `REQUIRED=true`

This authorization PR is intentionally inert and changes only this release-control marker. It exists to create the protected, reviewable exact-main release commit required by the canonical production orchestrator after the backend-changing subscription repair was merged without a production authorization marker.

The protected squash merge MUST contain the exact immutable commit-message marker `PRODUCTION-RELEASE-AUTHORIZED`. Without that marker, the production orchestrator must remain fail-closed.

Only the canonical release chain is authorized:

`Publish Render Backend Image` → exact immutable SHA image → `Workflow Y2 Production Release Orchestrator` → `production-release.yml` with `rollback_on_failure=true` → exact-image Render verification → readiness → Flyway/runtime invariants → security boundary → subscription/SCP production smoke → Vercel Control Plane/BFF → sanitized release evidence.

No manual Render deployment, direct production DDL, Flyway history mutation, `flyway repair`, out-of-order migration execution, force push, branch-protection bypass, credential mutation, or subscription/entitlement state fabrication is authorized.

If the release-control PR head, current `main`, required CI, independent approval, immutable-image evidence, or production target changes before merge, this authorization fails closed and must be re-evaluated.


## Workflow Designer Production Alignment Authority — 2026-09-15

- Certified engineering main SHA: `f62644009c1edb68314e5cbadd1c6ebddc4a0ddb`
- Source PR: `#1065` — Workflow Designer Browser Acceptance Gate
- Source PR exact head: `d9ee9262a366a19478112d216f468cca2c0ae769`
- Source PR independent human review: `APPROVED`
- Source PR required CI: `PASS`
- Designer Browser Acceptance: `PASS`
- Vercel frontend deployment for engineering main: `PASS`
- Observed live Render backend image before alignment: `ghcr.io/snadaiapp-png/snad-backend:e30d47be1716dcd32a4101f01428f62a66d0ac18`
- Backend source parity before alignment: `FAIL` because current main contains later `apps/sanad-platform/**` changes not present in the live image.
- Runtime application behavior change in this release-control PR: `NONE`
- Database migration change in this release-control PR: `NONE`
- Security/RBAC semantic change in this release-control PR: `NONE`
- Owner production deployment authorization: `GRANTED_EXPLICITLY_2026-09-15`
- Independent human review with repository write access: `REQUIRED`
- Exact-head required checks: `REQUIRED_PASS`
- Pre-merge current-main drift guard: `REQUIRED_PASS`
- Rollback on failure: `REQUIRED=true`

This authorization is strictly limited to aligning production with the certified engineering state represented by `f62644009c1edb68314e5cbadd1c6ebddc4a0ddb` through the canonical protected release path.

The protected squash merge MUST contain the exact immutable commit-message marker `PRODUCTION-RELEASE-AUTHORIZED`. Without that marker, the production orchestrator must fail closed.

Only the canonical path is authorized:

`Publish Render Backend Image` → exact immutable SHA image → `Workflow Y2 Production Release Orchestrator` → `production-release.yml` with `rollback_on_failure=true` → exact-image Render verification → readiness → Flyway/runtime invariants → security boundary → subscription/SCP production smoke → Vercel Control Plane/BFF → sanitized release evidence → Workflow Y2 Vercel Production Certification.

No manual Render deployment, direct production DDL, Flyway history mutation, `flyway repair`, out-of-order migration execution, force push, branch-protection bypass, credential mutation, subscription/entitlement fabrication, or autonomous AI activation is authorized.

If the release-control PR head, current `main`, required CI, independent approval, immutable-image evidence, or production target changes before merge, this authorization fails closed and must be re-evaluated.


## Subscription / SCP PRE-G2 Production Alignment Authority — 2026-09-16

- Certified engineering main SHA: `8d58f86fbd18ef310a2972896198a38d5dcac199`
- Source engineering PR: `#1062` — Subscription / SCP PRE-G2 G1 review and remediation
- Source PR certified head: `8d165de42713adcb3773d22c6d20a9240c490afc`
- Source PR independent review: `APPROVED`
- PRE-G2 review: `PASS`
- Post-Merge Main Verification run: `35059631411` — `PASS`
- Main CI run: `35059631445` — `PASS`
- Web CI run: `35059631394` — `PASS`
- Playwright E2E / Visual run: `35059631460` — `PASS`
- Exact-main backend image publication run: `35059631425` — `PASS`
- Production release orchestrator run: `35059713824` — `FAIL_CLOSED` before dispatch because exact main lacked the immutable production authorization marker
- Canonical SANAD Production Release for this exact main: `NOT_STARTED`
- Runtime application behavior change in this release-control PR: `NONE`
- Database migration change in this release-control PR: `NONE`
- Security/RBAC semantic change in this release-control PR: `NONE`
- AI activation/change in this release-control PR: `NONE`
- Independent human review with repository write access: `REQUIRED`
- Exact-head required checks: `REQUIRED_PASS`
- Pre-merge current-main drift guard: `REQUIRED_PASS`
- Rollback on failure: `REQUIRED=true`
- G2 implementation/release authorization: `NOT_GRANTED`

This authorization change is intentionally inert and changes only this release-control marker. It exists to create the protected, reviewable release-control main commit required by the canonical production orchestrator after the certified Subscription / SCP PRE-G2 engineering merge landed without the production authorization marker.

The protected squash merge MUST contain the exact immutable commit-message marker `PRODUCTION-RELEASE-AUTHORIZED`. Without that marker, the production orchestrator must remain fail-closed.

Only the canonical production path is authorized after all gates above remain satisfied:

`Publish Render Backend Image` → exact immutable main image → `Workflow Y2 Production Release Orchestrator` → `production-release.yml` with `rollback_on_failure=true` → exact-image Render verification → readiness → Flyway/runtime invariants → security boundary → Subscription/SCP production smoke → Vercel Control Plane/BFF → sanitized release evidence → Vercel production certification where applicable.

No manual Render deployment, direct production DDL, Flyway history mutation, `flyway repair`, out-of-order migration execution, force push, branch-protection bypass, credential mutation, subscription/entitlement fabrication, or G2 activation is authorized.

If the release-control PR head, current `main`, required CI, independent approval, immutable-image evidence, or production target changes before merge, this authorization fails closed and must be re-evaluated.


## Workflow Audit WF-AUD-01..08 Production Alignment Authority — 2026-09-17

- Certified engineering main SHA: `487b8e884624f5b6832a48a7ee3d01b994684b56`
- Source engineering PR: `#1074` — Workflow audit remediation WF-AUD-01 through WF-AUD-08
- Source PR certified head: `afab9b1cc45070e64eb63383fe4ea32984b52e9a`
- Source PR independent review: `APPROVED`
- Source PR Backend CI / Maven / PostgreSQL Direct / CRM Integration: `PASS`
- Source PR Workflow Y2 G4 Release Gate: `PASS`
- Source PR Web CI / Security / Compile / Playwright / Performance / Provenance / Smoke: `PASS`
- Post-Merge Main Verification run: `35151547415` — `PASS`
- Vercel Production Certification run: `35151547448` — `FAIL_CLOSED` because live Render backend source did not yet match current main
- Production Release Orchestrator run: `35151720135` — `FAIL_CLOSED` before dispatch because exact main lacked `PRODUCTION-RELEASE-AUTHORIZED`
- Current production remains on the previously authorized live backend image until this release-control PR is protected-merged and the canonical release succeeds.
- Runtime application behavior change in this authorization PR: `NONE`
- Database migration change in this authorization PR: `NONE`
- Security/RBAC semantic change in this authorization PR: `NONE`
- AI activation/change in this authorization PR: `NONE`
- Owner production deployment authorization: `GRANTED_EXPLICITLY_2026-09-17`
- Independent human review with repository write access: `REQUIRED`
- Exact-head required checks: `REQUIRED_PASS`
- Pre-merge current-main drift guard: `REQUIRED_PASS`
- Rollback on failure: `REQUIRED=true`

This authorization change is intentionally inert and changes only this release-control marker. It exists to create the protected, reviewable release-control main commit required by the canonical production orchestrator after PR #1074 was merged and Post-Merge Main Verification passed while production remained on an older Render backend image.

The protected squash merge MUST contain the exact immutable commit-message marker `PRODUCTION-RELEASE-AUTHORIZED`. Without that marker, the production orchestrator must remain fail-closed.

Only the canonical production path is authorized after all gates above remain satisfied:

`Publish Render Backend Image` → exact immutable main image → `Workflow Y2 Production Release Orchestrator` → `production-release.yml` with `rollback_on_failure=true` → exact-image Render verification → readiness → Flyway/runtime invariants → security boundary → Subscription/SCP production smoke → Vercel Control Plane/BFF → sanitized release evidence → Workflow Y2 Vercel Production Certification.

No manual Render deployment, direct production DDL, Flyway history mutation, `flyway repair`, out-of-order migration execution, force push, branch-protection bypass, credential mutation, subscription/entitlement fabrication, or autonomous AI activation is authorized.

If the release-control PR head, current `main`, required CI, independent approval, immutable-image evidence, or production target changes before merge, this authorization fails closed and must be re-evaluated.


## Flyway Forward-Lineage Final Production Authority — 2026-09-18

- Certified engineering main SHA: `00be1025d9d1e7940ac1872737c6f089e135d7b2`
- Source engineering PR: `#1086` — forward-only production Flyway lineage remediation
- Source PR exact head: `6eadeca94175c560af0fbe3a703ae2d40a66cf5a`
- Source PR independent review: `APPROVED`
- Source PR exact-head CI: `PASS`
- Main CI run `35371799114` / #3869: `PASS`
- Post-Merge Main Verification run `35371798997` / #1073: `PASS`
- Exact-main backend image publication run `35371799421` / #360: `PASS`
- Prior failed Render deployment: `dep-damkccou01pc73ajhuf0`
- Proven immutable production Flyway ledger floor before remediation: `20260914.1`
- Forward-only pending sequence authorized for canonical deployment:
  - `20260914.2`
  - `20260918.2`
  - `20260918.3`
  - `20260918.4`
  - `20260918.5`
  - `20260918.6`
  - `20260918.7`
- Retired retroactive versions MUST remain absent from production history:
  - `20260908.2`
  - `20260908.3`
  - `20260908.4`
  - `20260913.1`
  - `20260914.3`
  - `20260918.1`
- Runtime application behavior change in this authorization PR: `NONE`
- Database migration content change in this authorization PR: `NONE`
- Security/RBAC semantic change in this authorization PR: `NONE`
- Persistent Flyway out-of-order execution: `FORBIDDEN`
- Flyway repair / manual schema-history mutation: `FORBIDDEN`
- Independent human review with repository write access: `REQUIRED`
- Exact-head required checks: `REQUIRED_PASS`
- Pre-merge current-main drift guard: `REQUIRED_PASS`
- Rollback on failure: `REQUIRED=true`

This authorization change is intentionally inert and changes only this release-control marker. It authorizes the canonical protected production alignment for the forward-only Flyway remediation merged as PR #1086.

The protected squash merge MUST contain the exact immutable commit-message marker `PRODUCTION-RELEASE-AUTHORIZED`. Without that marker, the production orchestrator must fail closed.

Only the canonical production path is authorized:

`Publish Render Backend Image` → exact immutable main image → `Workflow Y2 Production Release Orchestrator` → `production-release.yml` with `rollback_on_failure=true` → exact-image Render verification → readiness → Flyway/runtime invariants → security boundary → production smoke/certification.

Final closure additionally requires read-only verification that production `flyway_schema_history` has zero failed rows, contains the authorized forward sequence, excludes the retired retroactive versions, and that normal Flyway operation remains `outOfOrder=false`.

No manual Render deployment, direct production DDL, Flyway history mutation, `flyway repair`, out-of-order migration execution, force push, branch-protection bypass, credential mutation, or entitlement fabrication is authorized.
