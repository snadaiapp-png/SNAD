# Workflow Y2 Production Orchestrator Guardrails

The orchestrator must never call Render directly, must never read database credentials, and must only dispatch the canonical `production-release.yml` after exact-main immutable image publication succeeds and the authorized squash-merge marker is present.
