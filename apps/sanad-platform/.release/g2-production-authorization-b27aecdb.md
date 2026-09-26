# G2 Production Release Control

Inert release-control marker only.

- Certified main SHA: `b27aecdb0770ecdb85874b1bfa0a065e8731cce3`
- Corrective PR: `#1153`
- Corrective PR exact head: `50be99cfbea8ba93638ab9223d41e408f1e5ea80`
- Production identity provisioning: required before production release
- G2 visual verification: required before final closure
- PostgreSQL Direct governance: required
- RLS, RBAC, and tenant isolation must remain enabled
- Manual production deployment is forbidden

Authorization semantics:
- This PR does not itself provision identities or deploy production.
- Merge requires terminal green exact-head CI and independent approval on the exact release-control head.
- Any drift of `main` from `b27aecdb0770ecdb85874b1bfa0a065e8731cce3` invalidates this release-control PR.
- The squash merge commit message must contain `PRODUCTION-RELEASE-AUTHORIZED`.
- After authorization, production identity provisioning must pass before the canonical production release is started.
- Final closure requires authenticated G2 visual evidence on the exact authorized SHA.

`MERGE_AUTHORIZATION=NO`
`FINAL_GATE=OPEN`
