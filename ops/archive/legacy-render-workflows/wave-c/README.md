# Legacy Diagnostic Workflows — Wave C

Wave C removes historical production diagnostics from `.github/workflows/` after R6 certified the canonical release control plane.

These files are preserved here as non-executable historical evidence only:

- `_db-enc-check.yml` — manual read-only production database inspection that depends on a Dockerized `psql` client and legacy datasource secret wiring. It is outside the governing PostgreSQL Direct release/test path and is not a branch-protection required check.
- `forensic-diagnostic.yml` — manual read-only RBAC/user forensic query that can emit production user, membership, role, tenant and capability details into GitHub Actions logs. It is an incident diagnostic, not CI or release authority, and is not a branch-protection required check.

Neither workflow is part of the canonical image, migration, deploy, rollback, smoke, security, or protected CI authorities.

R6 evidence before this archive:

```text
CONTRACT_TESTS=49/49 PASS
UNEXPECTED_PRODUCTION_WRITERS=0
SECRET_CANDIDATE_FILES=0
RENDER_ENV_WRITERS=0
```
