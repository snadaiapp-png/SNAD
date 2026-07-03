# Production Backup and Restore Runbook

**Service**: SANAD Platform — Production Database (Supabase PostgreSQL)
**Owner**: Abdulrhman Senen
**Last Updated**: 2026-06-24

---

## 1. Purpose

This runbook documents the production database backup verification and restore drill procedure for the SANAD platform. It covers read-only verification of the production database, backup creation confirmation, and restore into a disposable isolated database.

---

## 2. Scope

- Covers Supabase-hosted PostgreSQL backup and restore
- Restore is ALWAYS performed into a disposable database — NEVER into production
- Does NOT cover application-level backup (Render/Vercel state)

---

## 3. Prerequisites

- Access to Supabase project dashboard or API
- Production database credentials stored as GitHub repository secrets
- `pg_dump` and `psql` available in the CI runner
- A disposable Supabase project or database for restore verification

---

## 4. Backup Verification

### 4.1 Read-Only Production Database Verification

Connect to the production database using read-only credentials:

```bash
PGPASSWORD=$DATABASE_PASSWORD psql \
  -h $DATABASE_HOST \
  -p $DATABASE_PORT \
  -U $DATABASE_USERNAME \
  -d $DATABASE_NAME \
  -c "SELECT version();"
```

Verify:

1. **Connection succeeds**: Database is reachable
2. **Database identity verified**: Correct database name and host
3. **Flyway schema history accessible**:
   ```sql
   SELECT installed_rank, version, description, installed_on
   FROM flyway_schema_history
   ORDER BY installed_rank DESC
   LIMIT 5;
   ```
4. **Latest migration version**: Must include V15
5. **V15 ADMIN capabilities applied**:
   ```sql
   SELECT COUNT(*) FROM role_capabilities
   WHERE role_id = (SELECT id FROM roles WHERE code = 'ADMIN');
   ```
   Expected: 19 per tenant
6. **No write operations during verification**: Only SELECT queries used

### 4.2 Backup Creation Verification

Supabase provides automatic daily backups on paid plans. Verify:

1. **Supabase dashboard**: Navigate to Database → Backups
2. **Latest backup timestamp**: Must be within the last 24 hours
3. **Backup size > 0**: Confirmed via dashboard
4. **Encryption**: Supabase backups are encrypted at rest

For manual backup using `pg_dump`:

```bash
pg_dump \
  -h $DATABASE_HOST \
  -p $DATABASE_PORT \
  -U $DATABASE_USERNAME \
  -d $DATABASE_NAME \
  --no-password \
  --format=custom \
  --file=backup_$(date +%Y%m%d_%H%M%S).dump
```

**Important**: Never include the password in command output or logs.

---

## 5. Restore Drill

### 5.1 Create Disposable Database

Create a new temporary database on a separate Supabase project or instance:

```bash
createdb -h $RESTORE_HOST -U $RESTORE_USER sanad_restore_test
```

### 5.2 Restore Backup

```bash
pg_restore \
  -h $RESTORE_HOST \
  -U $RESTORE_USER \
  -d sanad_restore_test \
  --no-owner \
  --no-privileges \
  backup_file.dump
```

### 5.3 Validate Restored Database

1. **Schema exists**:
   ```sql
   SELECT count(*) FROM information_schema.tables
   WHERE table_schema = 'public';
   ```
2. **Flyway history exists**:
   ```sql
   SELECT count(*) FROM flyway_schema_history;
   ```
3. **Required tables exist**: tenants, organizations, users, auth_credentials, refresh_tokens, roles, access_capabilities, role_capabilities
4. **Representative row counts consistent**:
   ```sql
   SELECT 'tenants' as tbl, count(*) FROM tenants
   UNION ALL SELECT 'users', count(*) FROM users
   UNION ALL SELECT 'roles', count(*) FROM roles;
   ```
5. **V15 migration applied**:
   ```sql
   SELECT count(*) FROM flyway_schema_history WHERE version = '15';
   ```

### 5.4 Cleanup

```bash
dropdb -h $RESTORE_HOST -U $RESTORE_USER sanad_restore_test
```

**CRITICAL**: Always delete the disposable database after validation. Never leave restored production data in an unsecured location.

---

## 6. RPO/RTO Evidence

| Metric | Target | Evidence |
|--------|--------|----------|
| RPO | 24 hours | Supabase daily automatic backup |
| RTO | 4 hours | Restore drill duration measured |
| Backup frequency | Daily | Supabase paid plan |
| Backup retention | 30 days | Supabase configuration |

---

## 7. Decision Authority

| Action | Approver |
|--------|----------|
| Access production database (read-only) | Abdulrhman Senen |
| Trigger manual backup | Abdulrhman Senen |
| Restore into disposable database | Abdulrhman Senen |
| Restore into production (emergency only) | Abdulrhman Senen |

---

## 8. Required Repository Secrets

For the Backup Verify workflow to function, the following GitHub secrets must be configured in the Production environment:

- `PRODUCTION_DATABASE_URL` — Full JDBC or PostgreSQL connection URL
- `DATABASE_HOST` — Supabase database host
- `DATABASE_PORT` — Supabase database port (typically 5432)
- `DATABASE_NAME` — Database name
- `DATABASE_USERNAME` — Read-only database user
- `DATABASE_PASSWORD` — Database password
- `DATABASE_SSLMODE` — SSL mode (typically require)

Currently configured:
- `PRODUCTION_DATABASE_URL`: Present
- Others: **NOT CONFIGURED — OWNER ACTION REQUIRED**

---

## 9. Current Status

| Check | Status | Evidence |
|-------|--------|----------|
| Production connection | NOT VERIFIED | Missing DATABASE_* secrets |
| Flyway history | NOT VERIFIED | Missing DATABASE_* secrets |
| V15 applied | NOT VERIFIED | Missing DATABASE_* secrets |
| Backup creation | NOT VERIFIED | Requires Supabase paid plan + access |
| Restore drill | NOT EXECUTED | Requires disposable database |
| RPO evidence | NOT VERIFIED | Requires backup verification |
| RTO evidence | NOT MEASURED | Requires restore drill |

**EXTERNAL OWNER ACTION REQUIRED** — Database credentials and Supabase access must be configured before backup verification can proceed.
