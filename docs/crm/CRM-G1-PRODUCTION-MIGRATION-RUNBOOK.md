# CRM-G1 Production Migration Runbook

## 1. Purpose

This runbook governs the production application and verification of Flyway migration
`V20260717_6__complete_crm_g1_extension_tables.sql`.

It does not authorize a production change by itself. The database owner or release
manager must approve the change window, verify a restorable backup, execute the
migration with the production secret-management process, and attach immutable
runtime evidence.

## 2. Required approvals and inputs

Before execution, record all of the following:

- exact approved Git commit SHA;
- approved change ticket or release reference;
- verified backup or restore-point reference;
- database owner approval;
- application owner approval;
- maintenance window start and end;
- named rollback decision owner.

No credential, JDBC URL, access token, password, or private connection detail may be
committed to the repository or copied into the stage report.

## 3. Preconditions

1. The exact candidate SHA has passed `CRM G1 Schema Acceptance`.
2. `CrmPostgresMigrationTest` passes on PostgreSQL 16.
3. The CI artifact contains the eight expected tables and exactly 26 explicit
   `idx_crm_%` indexes.
4. Flyway validation succeeds against production history.
5. The target database backup is complete and restoration has been verified.
6. No conflicting schema migration or CRM deployment is running.
7. The application release containing migration `20260717.6` is available.

## 4. Migration procedure

Use the organization-approved deployment runner and secret store. The operator must
perform the equivalent of the following without exposing secrets:

```bash
cd apps/sanad-platform

mvn --batch-mode --no-transfer-progress flyway:validate \
  -Dflyway.url="$SPRING_DATASOURCE_URL" \
  -Dflyway.user="$SPRING_DATASOURCE_USERNAME" \
  -Dflyway.password="$SPRING_DATASOURCE_PASSWORD" \
  -Dflyway.locations=classpath:db/migration \
  -Dflyway.outOfOrder=true

mvn --batch-mode --no-transfer-progress flyway:migrate \
  -Dflyway.url="$SPRING_DATASOURCE_URL" \
  -Dflyway.user="$SPRING_DATASOURCE_USERNAME" \
  -Dflyway.password="$SPRING_DATASOURCE_PASSWORD" \
  -Dflyway.locations=classpath:db/migration \
  -Dflyway.validateOnMigrate=true \
  -Dflyway.outOfOrder=true \
  -Dflyway.target=20260717.6
```

The operator must stop immediately if validation fails, the production history is
unexpected, or the target migration is not the only intended pending schema change.

## 5. Post-migration verification

Run the repository verification script against the same production database:

```bash
PSQL_URL="${SPRING_DATASOURCE_URL#jdbc:}"
PGPASSWORD="$SPRING_DATASOURCE_PASSWORD" psql "$PSQL_URL" \
  -U "$SPRING_DATASOURCE_USERNAME" \
  -v ON_ERROR_STOP=1 \
  -f scripts/crm/verify-g1-schema.sql
```

The script must terminate successfully and prove:

- all eight CRM-G1 extension tables exist;
- exactly 26 explicit tenant-scoped `idx_crm_%` indexes exist on those tables;
- every extension table has a tenant foreign key;
- contact lookup references are constrained to the same tenant;
- Flyway migration `20260717.6` exists exactly once and is successful.

## 6. Runtime isolation smoke

After schema verification, execute the authenticated CRM acceptance workflow with two
real test tenants. Evidence must show:

1. Tenant A can create and read its own CRM records.
2. Tenant B cannot read Tenant A records through API identifiers.
3. Tenant B cannot load Tenant A CRM detail pages.
4. Tenant B lists, overview, search, and contact lookup contain no Tenant A data.
5. Unauthorized access returns a controlled `4xx`, never cross-tenant content.

The existing Playwright contract is:

`apps/web/e2e/crm-tenant-isolation.spec.ts`

## 7. Evidence package

Attach the following to the change ticket and reference it from
`docs/crm/evidence/CRM-G1-PRODUCTION-MIGRATION-EVIDENCE.md`:

- exact deployed commit SHA;
- Flyway validation output;
- Flyway history row for `20260717.6`;
- table inventory;
- 26-index inventory;
- tenant-constraint inventory;
- isolation smoke report;
- start and completion timestamps;
- backup reference;
- operator and approver identities;
- incident or rollback reference, when applicable.

Redact credentials and private connection details before retaining evidence.

## 8. Rollback

The migration is forward-only and additive. Do not attempt a destructive down
migration in production.

If rollback is required:

1. stop the affected CRM deployment and background workers;
2. preserve Flyway and application logs;
3. roll back the application to the previously approved artifact;
4. restore the verified pre-migration database backup when schema restoration is
   required;
5. rerun authentication, CRM read-path, tenant-isolation, and Flyway-history checks;
6. record the incident, rollback decision, restored backup reference, and final state.

## 9. Closure rule

CRM-G1 may be marked `CLOSED` only after all of the following are linked to one exact
approved SHA:

- successful source CI;
- successful PostgreSQL Testcontainers evidence;
- successful production Flyway evidence;
- successful post-migration schema verification;
- successful two-tenant runtime isolation evidence;
- completed production evidence record;
- owner approval.
