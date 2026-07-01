# Stage 04A.1 — Runtime Role Verification

## 1. Role Separation

Stage 04A.1 §7-8 implements database role separation in CI:

### Administrative account (container-level)
- `POSTGRES_USER=postgres`
- `POSTGRES_PASSWORD=ci-postgres-admin-only`
- Used only for role provisioning and privilege grants

### Migration account
- `sanad_migration_owner` (password: `ci-migration-only`)
- CREATEDB (for test database creation)
- Owns all tables (created by Flyway)
- Used by Flyway to run migrations
- NOT used by the application at runtime

### Runtime account
- `sanad_runtime_app` (password: `ci-runtime-only`)
- `NOSUPERUSER`
- `NOCREATEDB`
- `NOCREATEROLE`
- `NOREPLICATION`
- `NOBYPASSRLS`
- NOT table owner
- Has CRUD privileges (SELECT, INSERT, UPDATE, DELETE) on all application tables
- Subject to RLS policies

## 2. CI Provisioning

The `tenant-isolation` CI job provisions roles before running tests:

```sql
CREATE ROLE sanad_migration_owner WITH LOGIN PASSWORD 'ci-migration-only' CREATEDB;
CREATE ROLE sanad_runtime_app WITH LOGIN PASSWORD 'ci-runtime-only'
  NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS;
```

Flyway migrations run as `sanad_migration_owner`.
Application tests run as `sanad_runtime_app`.

## 3. Verification

CI verifies the runtime role flags:

```sql
SELECT rolname, rolsuper, rolbypassrls, rolcreatedb, rolcreaterole, rolreplication
FROM pg_roles WHERE rolname = 'sanad_runtime_app';
```

Expected: `sanad_runtime_app|f|f|f|f|f` (all flags false except login).

## 4. RLS Enforcement

Because `sanad_runtime_app` is:
- NOT a superuser
- NOT the table owner
- Does NOT have BYPASSRLS

...all queries run by the application are subject to RLS policies. Even if the application layer fails to scope a query, the database refuses to return cross-tenant rows.

## 5. V18 Migration

The V18 migration (in `db/migration-pg-only/`) creates the `sanad_runtime_app` role and grants CRUD privileges. However, CI does NOT rely on V18 alone — it provisions the role explicitly before Flyway runs, ensuring the role exists even if V18 hasn't been applied yet.

## 6. Test Coverage

- CI step "Verify runtime role is non-superuser and non-BYPASSRLS" checks the role flags
- CI step "Run tenant isolation tests as runtime role" runs all tests with `DATABASE_USERNAME=sanad_runtime_app`
- Any test that queries a tenant-owned table is automatically verified under RLS
