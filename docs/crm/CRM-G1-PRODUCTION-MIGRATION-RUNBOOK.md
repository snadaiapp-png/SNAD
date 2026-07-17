# CRM-G1 Production Migration Runbook

## 1. Purpose

This runbook governs the controlled production application and verification of
Flyway migration:

`V20260717_6__create_crm_g1_extension_tables.sql`

It does not authorize a production change by itself. The database owner and
release owner must approve the change window, verify a recoverable backup,
execute through the approved secret-management and deployment process, and
retain immutable evidence.

## 2. Mandatory inputs

Record before execution:

- exact approved application or release SHA;
- approved change ticket or release reference;
- target environment identifier;
- verified backup or restore-point reference;
- database owner approval;
- application owner approval;
- planned change window;
- named rollback decision owner.

Credentials, JDBC URLs, tokens, passwords, and private network details must not
be committed or copied into the evidence report.

## 3. Preconditions

1. The exact release SHA passed `CRM G1 Schema Isolation`.
2. The immutable CI artifact proves:
   - eight G1 extension tables;
   - exactly 26 explicit `idx_crm_%` indexes;
   - `tenant_id` as the leading key on all 26 indexes;
   - eight tenant-root foreign keys;
   - successful migration and behavioral PostgreSQL tests.
3. `CRM Authenticated Acceptance` passed on the same source identity or an
   explicitly traceable release identity.
4. Flyway history and checksums validate against the target database.
5. A recoverable backup or restore point has been verified.
6. No conflicting database migration or CRM deployment is active.

## 4. Pre-migration evidence

Capture and retain, with secrets redacted:

- current database identity and PostgreSQL version;
- current successful Flyway head;
- pending Flyway migrations;
- exact release SHA;
- backup reference and verification timestamp;
- operator and approver identities;
- UTC start time.

Stop if Flyway reports checksum drift, failed history, an unexpected pending
migration, or a target different from the approved environment.

## 5. Controlled migration

Use the organization-approved deployment runner and secret store. The operator
must perform the equivalent of:

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

Do not manually mark Flyway history as successful and do not execute a
hand-edited copy of the migration.

## 6. Post-migration schema verification

Run the repository read-only verifier against the same target database:

```bash
PSQL_URL="${SPRING_DATASOURCE_URL#jdbc:}"
PGPASSWORD="$SPRING_DATASOURCE_PASSWORD" psql "$PSQL_URL" \
  -U "$SPRING_DATASOURCE_USERNAME" \
  -v ON_ERROR_STOP=1 \
  -f scripts/crm/verify-g1-tenant-isolation.sql
```

The script must report all of the following as `PASS`:

- all eight G1 extension tables exist;
- `tenant_id` exists on all eight tables;
- all eight tables reference `tenants(id)`;
- exactly 26 explicit CRM-G1 indexes exist;
- every explicit index begins with `tenant_id`;
- both concrete contact relationships use same-tenant composite foreign keys.

Also capture the successful Flyway row for version `20260717.6` and the complete
table, index, and constraint inventories.

## 7. Runtime tenant-isolation smoke

Run the authenticated acceptance workflow with two controlled test tenants after
the production deployment. Evidence must prove:

1. Tenant A can create and read its own CRM records.
2. Tenant B cannot retrieve Tenant A account, contact, lead, or opportunity IDs.
3. Tenant B detail pages do not expose Tenant A content.
4. Tenant B lists, overview, search, and contact lookup do not expose Tenant A
   data.
5. Unauthorized access produces a controlled `4xx`, never cross-tenant content.
6. Authentication, CRM read paths, and application health remain healthy.

The repository contract is:

`apps/web/e2e/crm-tenant-isolation.spec.ts`

## 8. Evidence package

Complete:

`docs/crm/evidence/CRM-G1-PRODUCTION-MIGRATION-EVIDENCE.md`

Attach or reference:

- exact deployed SHA;
- change ticket;
- backup reference;
- Flyway validation and migration output;
- Flyway `20260717.6` history row;
- eight-table inventory;
- 26-index inventory;
- tenant-constraint inventory;
- authenticated two-tenant acceptance result;
- application health and regression result;
- UTC timestamps;
- operator and approver identities;
- rollback or incident reference, when applicable.

## 9. Rollback

Migration `20260717.6` is additive and forward-only. Do not create or run an
unreviewed destructive down migration.

When rollback is required:

1. stop the affected CRM deployment and related workers;
2. preserve application, database, and Flyway logs;
3. roll back the application to the previous approved artifact;
4. restore the verified pre-migration backup when schema restoration is needed;
5. rerun Flyway history, authentication, CRM read-path, and tenant-isolation
   checks;
6. record the rollback decision, restored backup, incident reference, and final
   state.

## 10. Closure rule

CRM-G1 may be marked `CLOSED` only when all evidence is bound to one traceable
release identity and the following are complete:

- exact-SHA source CI;
- PostgreSQL migration and behavioral isolation tests;
- successful production Flyway application;
- successful production schema verification;
- successful authenticated two-tenant runtime isolation;
- completed production evidence record;
- database and application owner approval.
