# G2 Production Authorization — b5c8cad3

Certified main: b5c8cad39c4f113d6801e050bfeaf86ca9cbda40
Corrective PR: #1155
Corrective exact head: 9065cddfbda1a706f536e4debefcf5529d6c2ead
Provisioning failure source: run 36209874302

Authorization conditions:
- exact-head required CI must be terminal green
- independent approval must target the exact release-control head
- main drift invalidates this authorization
- squash merge commit must contain literal PRODUCTION-RELEASE-AUTHORIZED
- G2 Production Identity Provisioning must pass before canonical production release
- Tenant B only; Control Plane tenant must not be used for G2 identities
- G2 production secrets remain read-only
- PostgreSQL Direct governance remains in force
- RLS/RBAC/tenant isolation remain enabled
- no BYPASSRLS, no Flyway history mutation, no Docker governing path
- production release remains fail-closed
- authenticated production G2 visual smoke 60/60 is required for final closure

MERGE_AUTHORIZATION=NO
FINAL_GATE=OPEN
