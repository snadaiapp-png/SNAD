# Users Module Production Closure Authorization — 2026-09-30

This file is an inert release-control marker only. It does not change application runtime behavior, database migrations, tenant isolation, authentication, authorization, RBAC semantics, or credentials.

- Certified engineering main SHA before release-control PR: `b49fe6384d9364d844251a9f079db776aacad9b5`
- Users Module implementation PR: `#1174`
- Users Module implementation merge SHA: `74d27fb1d02bd94daa36fc74ab12791c1b7fc954`
- UAC Wave 1 integration PR affecting Users/RBAC: `#1198`
- UAC Wave 1 exact merged main SHA: `b49fe6384d9364d844251a9f079db776aacad9b5`
- Post-Merge Main Verification run: `36697172632` — PASS
- Users Module Closure run: `36697172844` — PASS
- CI run on the same main revision: `36697172531` — PASS
- PostgreSQL Direct governance: REQUIRED; Docker/Testcontainers remain prohibited for certification
- Owner production deployment authorization: `GRANTED_EXPLICITLY_2026-09-30` by the instruction to continue the Users Module through comprehensive closure
- Production purpose: complete the Users Module live authenticated same-revision closure gate only
- Required release path: canonical `production-release.yml` only
- Rollback on failure: `REQUIRED=true`
- Runtime application behavior change in this authorization PR: `NONE`
- Database/Flyway migration change in this authorization PR: `NONE`
- Security/RBAC/Identity semantic change in this authorization PR: `NONE`
- Credential change in this authorization PR: `NONE`
- Independent human review with repository write access: `REQUIRED`
- Exact-head required checks: `REQUIRED_PASS`
- Pre-merge current-main drift guard: `REQUIRED_PASS`

This authorization is strictly limited to aligning production with the already-certified Users/RBAC engineering state represented by `b49fe6384d9364d844251a9f079db776aacad9b5` plus this inert release-control marker, so the governing Users Module specification can execute its remaining live authenticated production verification on the resulting exact main revision.

The protected merge MUST contain the exact immutable commit-message marker `PRODUCTION-RELEASE-AUTHORIZED`. Without that marker, `Workflow Y2 Production Release Orchestrator` must remain fail-closed and must not dispatch production.

Only the canonical chain is authorized:

`required exact-head CI` → `independent approval` → protected merge containing `PRODUCTION-RELEASE-AUTHORIZED` → `Publish Render Backend Image` → exact immutable SHA image → `Workflow Y2 Production Release Orchestrator` → `production-release.yml` with `rollback_on_failure=true` → Render exact-image verification → readiness/Flyway/security checks → authenticated Control Plane production smoke → live release-SHA proof → final Users Module closure reconciliation.

No manual Render deployment, direct production DDL/DML, Flyway history mutation, `flyway repair`, out-of-order migration execution, force push, branch-protection bypass, credential substitution, tenant-boundary bypass, or authorization weakening is authorized.

If the release-control PR head, current `main`, required CI, independent approval, immutable-image evidence, or production target changes before merge, this authorization fails closed and must be re-evaluated.