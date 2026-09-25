# G2 Production Release Control

This file is an inert release-control marker only. It contains no runtime, UI, API, database, workflow-engine, security, or deployment implementation changes.

- Certified main SHA: `4dfac9eb649ed4079d544bcc65f70757fe4d2a4c`
- Corrective PR: `#1150`
- Corrective PR exact head: `ef26ef1a5871e8bc49daba46ef3bd3858bfdbbfe`
- G2 production visual contract: required in canonical `production-release.yml`
- PostgreSQL Direct governance: required
- RLS / RBAC / tenant isolation: must remain enabled
- Flyway history mutation / repair: forbidden
- Docker/Testcontainers governing path: forbidden
- Manual production deployment: forbidden

Authorization semantics:
- This PR does **not** itself deploy production.
- Merge is permitted only after exact-head required CI is terminal green and an independent approval exists on the exact release-control head.
- Any drift of `main` from `4dfac9eb649ed4079d544bcc65f70757fe4d2a4c` before authorization merge invalidates this release-control PR.
- The squash merge commit message for the release-control PR must contain the literal token `PRODUCTION-RELEASE-AUTHORIZED`.
- Production must proceed only through the canonical `production-release.yml` chain after authorization.
- Canonical production release must fail closed unless the authenticated G2 Employee/Manager/HR AR/EN Desktop/Mobile visual matrix passes with exact-SHA evidence.

`MERGE_AUTHORIZATION=NO`
`FINAL_GATE=OPEN`
