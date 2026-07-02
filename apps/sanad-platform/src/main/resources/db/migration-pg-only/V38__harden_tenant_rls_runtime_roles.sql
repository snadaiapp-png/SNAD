-- ============================================================
-- SANAD Platform - Flyway migration V18 (PostgreSQL only)
-- ------------------------------------------------------------
-- Stage 04A §9-10 — Runtime role hardening for RLS enforcement.
--
-- Creates the sanad_runtime_app role (if not exists) and grants it
-- the minimum CRUD privileges on all application tables. The runtime
-- role is NOT a superuser, does NOT have BYPASSRLS, and is NOT the
-- table owner — so RLS policies are enforced for all application queries.
--
-- The migration_owner role (used by Flyway) remains the table owner
-- and can bypass RLS during migrations. The application's datasource
-- must use sanad_runtime_app (not the migration owner) at runtime.
--
-- This migration is in db/migration-pg-only/ (PostgreSQL only).
-- ============================================================

-- Create the runtime role if it doesn't exist.
-- Use DO $$ to conditionally create (CREATE ROLE IF NOT EXISTS is not available in all PG versions).
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'sanad_runtime_app') THEN
        CREATE ROLE sanad_runtime_app
            WITH LOGIN
            NOSUPERUSER
            NOCREATEDB
            NOCREATEROLE
            NOREPLICATION
            NOBYPASSRLS;
        RAISE NOTICE 'Created role sanad_runtime_app (NOSUPERUSER, NOBYPASSRLS)';
    ELSE
        RAISE NOTICE 'Role sanad_runtime_app already exists';
    END IF;
END $$;

-- Grant schema usage
GRANT USAGE ON SCHEMA public TO sanad_runtime_app;

-- Grant DML privileges on all application tables to the runtime role.
-- The runtime role can SELECT, INSERT, UPDATE, DELETE but NOT CREATE/DROP/ALTER.
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO sanad_runtime_app;

-- Grant usage on sequences (for any SERIAL/IDENTITY columns)
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO sanad_runtime_app;

-- Ensure future tables created by the migration_owner also grant privileges
-- to the runtime role automatically.
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO sanad_runtime_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT USAGE, SELECT ON SEQUENCES TO sanad_runtime_app;

-- Verify: the runtime role should NOT be a superuser and should NOT have BYPASSRLS.
-- This is a documentation comment; the actual verification happens in CI via:
--   SELECT rolsuper, rolbypassrls FROM pg_roles WHERE rolname = 'sanad_runtime_app';
-- Expected: rolsuper = false, rolbypassrls = false.

-- Note: The password for sanad_runtime_app is set via the DATABASE_PASSWORD
-- environment variable in CI/production. This migration does NOT set the
-- password — it must be set out-of-band (e.g., via psql \password or
-- ALTER ROLE sanad_runtime_app WITH PASSWORD '...').
-- In CI, the bootstrap script creates the role with a password before
-- Flyway runs.
