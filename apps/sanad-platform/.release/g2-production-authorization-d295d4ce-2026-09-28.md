# G2 Production Alignment Authorization — 2026-09-28

This file is an inert release-control marker only. It does not change application runtime behavior, database migrations, or security/RBAC semantics.

- Certified engineering main SHA: `d295d4cefbef9b332cff24e5c0561de99f9c4387`
- Source engineering PR: `#1175`
- Source engineering exact-head CI: `PASS`
- Source engineering independent review: `APPROVED`
- Owner production deployment authorization: `GRANTED_EXPLICITLY_2026-09-28`
- Production target: active Render service `sanad-backend`
- Current production image before alignment: `ghcr.io/snadaiapp-png/snad-backend:7289a9df98be912e502faea209f793dcd95e68ea`
- Required release path: canonical `production-release.yml` only
- Rollback on failure: `REQUIRED=true`
- Runtime application behavior change in this authorization PR: `NONE`
- Database migration change in this authorization PR: `NONE`
- Security/RBAC semantic change in this authorization PR: `NONE`
- Independent human review with repository write access: `REQUIRED`
- Exact-head required checks: `REQUIRED_PASS`
- Pre-merge current-main drift guard: `REQUIRED_PASS`

This authorization is strictly limited to aligning production with the certified engineering state represented by `d295d4cefbef9b332cff24e5c0561de99f9c4387`, including the G2 identity-provisioning contract repair from PR #1175.

The protected squash merge MUST contain the exact immutable commit-message marker `PRODUCTION-RELEASE-AUTHORIZED`. Without that marker, the Workflow Y2 Production Release Orchestrator must fail closed.

Only the canonical chain is authorized:

`Publish Render Backend Image` → exact immutable SHA image → `Workflow Y2 Production Release Orchestrator` → `production-release.yml` with `rollback_on_failure=true` → Render exact-image verification → readiness → Flyway/runtime invariants → security boundary → production smoke → sanitized release evidence.

No manual Render deployment, direct production DDL, Flyway history mutation, `flyway repair`, out-of-order migration execution, force push, branch-protection bypass, or substitution of Control Plane credentials for Tenant B is authorized.

If the release-control PR head, current `main`, required CI, independent approval, immutable-image evidence, or production target changes before merge, this authorization fails closed and must be re-evaluated.
