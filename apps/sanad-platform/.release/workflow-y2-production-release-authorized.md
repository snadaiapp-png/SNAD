# Workflow Y2 Production Release Authorization Marker

This file is an inert release-control marker.

It does not change application runtime behavior. Its presence under `apps/sanad-platform/**` ensures the protected merge that introduces the production orchestrator also triggers `Publish Render Backend Image` for that exact merge SHA.

The production orchestrator additionally requires the immutable squash-merge commit message marker `PRODUCTION-RELEASE-AUTHORIZED`; this file alone never authorizes a production deployment.
