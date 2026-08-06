# CRM-006 Final Production Release Marker

This marker requests the governed final CRM-006 Production closure release. The eventual protected `main` merge commit must produce the immutable backend image, deploy it to Render, retain forward-only Flyway semantics, and pass the production closure workflow on the same release.

```text
STAGE: CRM-006
RELEASE_ATTEMPT: 20260727-FINAL-CLOSURE
ACTION: EXACT_SHA_PRODUCTION_DEPLOY_AND_VERIFY
TARGET: GHCR_AND_RENDER_PRODUCTION
SOURCE_AUTHORITY: protected main merge commit
FLYWAY_MODE: forward-only
REQUIRED_PROOF: image identity, health, Flyway, Contact acceptance, tenant isolation
CLOSURE_ASSERTED_BY_THIS_FILE: NO
```

Final closure is authorized only after the immutable image, Render deployment identity, health/liveness/readiness, Flyway postconditions, CRM acceptance, tenant isolation, and production error gates pass.
