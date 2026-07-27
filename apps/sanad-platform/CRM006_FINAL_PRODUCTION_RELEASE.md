# CRM-006 Final Production Release Marker

This file intentionally changes the backend project root to request a fresh immutable backend image build and Render Production deployment from the eventual protected `main` merge commit.

```text
STAGE: CRM-006
ACTION: FINAL_PRODUCTION_REDEPLOY
TARGET: GHCR_AND_RENDER_PRODUCTION
SOURCE_AUTHORITY: protected main merge commit
FLYWAY_MODE: forward-only
CLOSURE_ASSERTED_BY_THIS_FILE: NO
```

Final closure is authorized only after the immutable image, Render deployment identity, health/liveness/readiness, Flyway postconditions, CRM acceptance, tenant isolation, and production error gates pass.
