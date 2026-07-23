-- H2 compatibility stub for CRM-008B Foundation V20260722.8.
-- The real PostgreSQL-native migration (with JSONB, partial unique
-- indexes, and fail-closed DO $$ guards) is loaded from
-- db/vendor/postgresql. H2 does not execute PostgreSQL PL/pgSQL,
-- so this stub preserves Flyway version parity only. The actual
-- CRM-008B schema is verified by PostgreSQL Testcontainers tests
-- (CrmPostgresMigrationTest, Crm008bFoundationUpgradeTest, etc.).
SELECT 1;
