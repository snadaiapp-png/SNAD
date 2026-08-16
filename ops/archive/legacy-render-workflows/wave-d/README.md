# Legacy Render / Production Diagnostics — Wave D

Wave D removes four incident-only diagnostic workflows from `.github/workflows/` after R6 certified the canonical release control plane.

Archived files:

- `_check-fail.yml` — read-only Render deploy/event failure inspection.
- `_list-deploys.yml` — read-only Render deployment history inspection.
- `_render-events.yml` — read-only Render service/events/log inspection that could surface registry/service metadata in Actions logs.
- `_repro-crash.yml` — legacy startup reproduction against Production DB. This workflow is dangerous under Clean-Room governance because it explicitly starts the application with `FLYWAY_ENABLED=true`, `FLYWAY_BASELINE_ON_MIGRATE=true`, and `FLYWAY_VALIDATE_ON_MIGRATE=false` against production credentials.

None is a required `main` branch-protection check. None is part of the canonical image, migration, deploy, rollback, smoke, security, or CI authority.

The exact original blobs are preserved under this archive path for provenance; they are non-executable here.
