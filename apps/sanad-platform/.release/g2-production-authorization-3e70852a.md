# G2 Production Release Control

This file is an inert release-control marker only. It contains no runtime, UI, API, database, workflow-engine, security, or deployment implementation changes.

- Corrected certified main SHA: `3e70852a9040a8306b1263a92b36ca79bacbeb9f`
- Corrective PR: `#1147`
- Corrective PR exact head: `faa6bab691bb000dca5aa52addf7bef84ee880a7`
- PostgreSQL Direct governance: required
- RLS / RBAC / tenant isolation: must remain enabled
- Flyway history mutation / repair: forbidden
- Docker/Testcontainers governing path: forbidden
- Manual production deployment: forbidden

Authorization semantics:
- This PR does **not** itself deploy production.
- Merge is permitted only after exact-head required CI is terminal green and an independent approval exists on the exact release-control head.
- Any drift of `main` from `3e70852a9040a8306b1263a92b36ca79bacbeb9f` before authorization merge invalidates this release-control PR.
- The squash merge commit message for the release-control PR must contain the literal token `PRODUCTION-RELEASE-AUTHORIZED`.
- Production must proceed only through the canonical `production-release.yml` chain after authorization.

`MERGE_AUTHORIZATION=NO`
`FINAL_GATE=OPEN`
