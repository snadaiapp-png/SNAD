# CRM Deployment Readiness Contract

## Scope

This document defines the configuration and verification required to deploy the completed CRM source to a production-like environment. It does **not** grant production or commercial release approval.

## Required runtime configuration

The deployment secret manager must provide these values. They must not be committed to Git or copied into workflow logs.

| Variable | Requirement |
|---|---|
| `SPRING_DATASOURCE_URL` | PostgreSQL connection URL for the target environment |
| `SPRING_DATASOURCE_USERNAME` | Least-privilege application database principal |
| `SPRING_DATASOURCE_PASSWORD` | Database credential from secret management |
| `JWT_SECRET` | Environment-specific authentication signing secret |
| `CRM_CUSTOM_FIELD_ENCRYPTION_KEY` | Base64 AES-256 key used for sensitive custom-field values |
| `SPRING_PROFILES_ACTIVE` | Non-local deployment profile |

Recommended operational settings:

| Variable | Default | Production-like recommendation |
|---|---:|---:|
| `SANAD_CRM_IMPORT_WORKER_ENABLED` | `true` | `true` on exactly the intended worker replicas |
| `SANAD_CRM_IMPORT_WORKER_INITIAL_DELAY_MS` | `5000` | `5000` or higher |
| `SANAD_CRM_IMPORT_WORKER_DELAY_MS` | `5000` | Set from measured queue demand |

Spring property aliases may be supplied using the canonical names:

```text
sanad.crm.import-worker-enabled
sanad.crm.import-worker-initial-delay-ms
sanad.crm.import-worker-delay-ms
sanad.crm.custom-field-encryption-key
```

## Database deployment

1. Take and verify a PostgreSQL backup before migration.
2. Confirm Flyway history is valid through `20260702.1` or an earlier approved version.
3. Deploy the application artifact containing `V20260702_2__complete_crm_imports_custom_fields.sql`.
4. Allow Flyway to apply the additive migration once.
5. Verify Flyway version `20260702.2` is successful and unique.
6. Verify the following tables are present:
   - `crm_import_files`
   - `crm_import_errors`
   - `crm_custom_field_values`
7. Verify all 18 active `CRM.%` capabilities exist and ADMIN role assignments were populated.

The migration is additive. Rollback must use application rollback plus a database restore when schema rollback is required; destructive down-migrations are intentionally not automated.

## Import worker controls

- Jobs use database leases and a unique worker identifier.
- A worker processes each row in an independent transaction.
- `processed_rows = succeeded_rows + failed_rows` is enforced by the database.
- Expired `RUNNING` jobs are reclaimable and resume after the recorded processed row count.
- Completed and cancelled jobs cannot be re-queued.
- Uploaded files are limited to 10 MB, 10,000 rows, and 100 columns.
- XLSX expansion is limited to 50 MB and XML external entities are disabled.

## Sensitive custom fields

- Sensitive definitions cannot be searchable.
- Sensitive values are stored as AES-GCM ciphertext.
- Ordinary reads return `[REDACTED]`.
- Decrypted reads require `CRM.ADMIN`.
- Loss of `CRM_CUSTOM_FIELD_ENCRYPTION_KEY` makes existing sensitive values unrecoverable. The key therefore requires backup, rotation, and access-control procedures in the environment secret manager.

## Pre-deployment gates

The exact candidate SHA must pass:

- Maven compile and test suite.
- H2 application integration tests.
- PostgreSQL 16 clean-install migration test.
- PostgreSQL upgrade from the pre-CRM schema.
- PostgreSQL upgrade from CRM core `20260702.1`.
- Web lint, interaction tests, and Next.js build.
- Security, identity, backup/restore, performance, and OWASP repository checks.

## Post-deployment verification

Run the authenticated CRM smoke workflow with two real tenant tokens. Evidence must prove:

- Account create/read.
- Linked Contact.
- Pipeline and Stages.
- Opportunity and transition to Won.
- Activity create/complete.
- Lead conversion.
- Customer 360 and Dashboard.
- Tenant A records are inaccessible to Tenant B.

## Rollback

1. Stop or disable CRM import workers.
2. Preserve import-job and error evidence.
3. Roll back the application to the previously approved artifact.
4. If the migration itself must be removed, restore the verified pre-migration database backup.
5. Verify authentication, tenant isolation, CRM read paths, and Flyway history.
6. Record the incident and the rollback SHA.

## Governance status

```text
CRM SOURCE DEPLOYMENT PACKAGE: SUBJECT TO EXACT-SHA CI
PRODUCTION DEPLOYMENT: NOT EXECUTED BY THIS BRANCH
PRODUCTION APPROVAL: NOT GRANTED BY THIS DOCUMENT
COMMERCIAL GO-LIVE: NOT AUTHORIZED
```

Issues #173, #197, and #29 remain independent release gates and must be closed with external evidence before production approval.
