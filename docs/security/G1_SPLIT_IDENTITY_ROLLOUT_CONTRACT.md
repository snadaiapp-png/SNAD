# G1 production split-identity rollout contract

> **Status update — 2026-11-03 (read-only verification, Issue #1462).**
> The mandatory provisioning below remains the gate. Current verified facts:
>
> * The split-identity chain **is merged on `main`**: `V20261103_1__sanad_migrator_role_contract.sql`
>   and `V20261103_2__migrator_ownership_and_runtime_acl.sql` (PR #1471, merge `c043cf6`,
>   CI run 41552016355 success; certified chain head `20261103.2`).
> * The **production database has not applied it yet**: live `flyway_schema_history` head is
>   `20261021.1` (verified read-only 2026-11-03 02:33 UTC via the Supabase management API;
>   `sanad_migrator` does not exist on production, `sanad.deployment_topology` is unset).
> * The protected path `.github/workflows/database-migrate-production-migrator.yml` is hardened
>   (fail closed): exact-role pre-flight, env-only secrets, `approved_sha` binding, enforced
>   `expected_head`, and pre/post catalog assertions (role contract, membership isolation,
>   separated topology, migrator ownership, append-only ledger, FORCE RLS).
> * Operator blockers before the production run: provision `sanad_migrator` and the
>   `PROD_FLYWAY_MIGRATOR_USER` / `PROD_FLYWAY_MIGRATOR_PASSWORD` Production-environment
>   secrets (not present as of 2026-11-03), set `ALTER DATABASE postgres SET
>   sanad.deployment_topology = 'separated'`, run the RUNBOOK §2(b) ownership transfer,
>   verify a restorable backup (G6), and obtain G8 release authorization.

This PR is intentionally DRAFT and must not be merged until the deployment is provisioned and the full application test matrix passes. It changes Spring Boot's production Flyway connection identity; it does **not** create a database role, migrate table ownership, or grant runtime DML.

## Mandatory provisioning

1. Create a distinct `sanad_migrator` LOGIN role through an approved privileged bootstrap procedure, not by the runtime application.
2. Set `FLYWAY_USER` and `FLYWAY_PASSWORD` through the deployment secret manager. Do not reuse `SPRING_DATASOURCE_USERNAME` / `SPRING_DATASOURCE_PASSWORD`.
3. Ensure the migrator can manage the migration schema and Flyway history, and the runtime role cannot modify either.
4. Review and execute forward-only ownership transfer and *evidence-derived* runtime grants on an isolated production-equivalent database. Do not grant blanket DML to 312 tables without call-site review.
5. Confirm `sanad` has NOSUPERUSER, NOBYPASSRLS, no table ownership, no schema CREATE, no TRUNCATE, and required RLS policies remain ENABLE/FORCE.
6. Run full Flyway fresh and upgrade chains, Maven, PostgreSQL Direct, tenant A/B and missing-GUC tests, user registration/login/session refresh, subscriptions and workers.
7. Do not merge this PR or deploy while any mandatory gate fails or credentials are absent. If Flyway is intentionally executed in a separate migration job, set `FLYWAY_ENABLED=false` on the runtime service.

## Risk

Existing deployments with `FLYWAY_ENABLED=true` but no `FLYWAY_USER` / `FLYWAY_PASSWORD` may fail to start. This is an intentional fail-closed draft change, **not yet deployable**.

Evidence from third-party report claiming V20261103_1/2 migrations and 312 missing grants was not available as actual source files in this chat; those claims are UNVERIFIED and the migrations are NOT included in this PR.
