# G2 Production Release Authorization — 2026-09-25

This is an inert release-control marker for the post-merge production alignment of HRM G2 (Time & Attendance, Timesheets, and Leave).

- Source engineering PR: `#1133`
- Certified source PR head: `10ebf65827c3cd3606b1ef4fa12be9060f740c66`
- Engineering merge/main baseline: `eb19a2dd9b476bc3a5af0c9b0755489fc1865c0f`
- Source PR exact-head CI: `PASS`
- Source PR independent review: `APPROVED`
- Source PR merge: `COMPLETE`
- Post-merge production orchestrator on `eb19a2dd...`: `FAIL_CLOSED`
- Fail-closed reason: merge commit message did not contain the immutable production marker `PRODUCTION-RELEASE-AUTHORIZED`
- Owner final production authorization: `GRANTED_EXPLICITLY_2026-09-25`
- Runtime application code change in this authorization PR: `NONE`
- Database migration change in this authorization PR: `NONE`
- Security/RBAC/RLS semantic change in this authorization PR: `NONE`
- PostgreSQL Direct governance: `UNCHANGED`
- Rollback on production failure: `REQUIRED=true`
- Independent human review with repository write access: `REQUIRED`
- Exact-head required CI: `REQUIRED_PASS`
- Pre-merge current-main drift guard: `REQUIRED_PASS`

This release-control change exists only to create a protected, reviewable exact-main authorization commit after the engineering merge. It must not alter runtime code, migrations, tenant isolation, RLS, RBAC, Workflow Y2 ownership, or database history.

The protected squash merge for this authorization PR MUST contain the exact immutable commit-message marker `PRODUCTION-RELEASE-AUTHORIZED`. Without that marker the Workflow Y2 Production Release Orchestrator must fail closed.

The only authorized release path is:

`Publish Render Backend Image` → immutable exact-main image → `Workflow Y2 Production Release Orchestrator` → `production-release.yml` with `rollback_on_failure=true` → exact-image verification → readiness → Flyway/runtime invariants → security/tenant-isolation boundary → G2/SCP production smoke → Vercel Control Plane/BFF verification → sanitized release evidence.

No manual Render deployment, direct production DDL, Flyway history mutation, `flyway repair`, out-of-order migration execution, force push, branch-protection bypass, RLS weakening, tenant-isolation weakening, or synthetic success evidence is authorized.

If `main`, this release-control PR head, required CI, independent approval, immutable-image evidence, or the production target changes before merge, authorization fails closed and must be re-evaluated.
