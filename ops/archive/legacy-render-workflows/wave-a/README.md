# Legacy Production Workflows — Wave A

These workflows were removed from `.github/workflows/` during Backend Clean-Room R2 because source review proved that they can mutate the legacy Render service and/or production PostgreSQL outside the future canonical release control plane.

They are preserved here as historical evidence only and are **not executable GitHub Actions** from this path.

Wave A contains only files whose mutation behavior was manually verified during the forensic review:

- `apply-migration-manually.yml` — applies production SQL manually and writes a Flyway history record by hand.
- `database-migrate-production.yml` — production Flyway runner with normal-release-unsafe baseline/validation settings and Docker dependency.
- `database-migrate-pooler.yml` — applies migration SQL directly through a pooler, outside the governed PostgreSQL Direct/Flyway path.
- `suspend-and-migrate.yml` — suspends Render and runs production Flyway migrations with validation disabled.
- `force-deploy-suspend.yml` — suspends the production backend then triggers a Render deployment.
- `switch-db-pool-mode.yml` — rewrites production `DATABASE_URL`/pool settings and triggers deployment.
- `emergency-deploy.yml` — rewrites production DB/Flyway/JPA/JWT/JVM/runtime settings and deploys.
- `enable-flyway.yml` — rewrites Render Flyway/JPA/pool settings and deploys.
- `create-postgres-database.yml` — can create a new Render PostgreSQL service, replace the backend `DATABASE_URL`, and deploy.

The plaintext-secret-bearing `direct-db-insert.yml` is deliberately **not archived** here. It was removed from the current Clean-Room tree and is covered by `ops/security/CREDENTIAL-EXPOSURE-2026-08-16.md`; credential rotation is mandatory.

None of the Wave A files back a required `main` branch protection status context.
