# Legacy Production Runtime Reproduction — Wave E

This archive contains the historical `render-repro-prod-env.yml` workflow after it was removed from `.github/workflows/` during Clean-Room R7.

Reason for removal:

- it used Production environment credentials;
- it fetched live Render environment values;
- it launched the backend against the production database;
- it explicitly enabled runtime Flyway;
- it disabled Flyway validation and enabled baseline-on-migrate;
- schema migration authority belongs only to `.github/workflows/database-migrate.yml`.

The archived workflow is retained as non-executable evidence only. It is not a release, CI, smoke, or diagnostic authority.
