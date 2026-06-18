-- ============================================================
-- SANAD Platform - Flyway migration V2
-- ------------------------------------------------------------
-- Creates the `organizations` table.
--
-- An Organization is the first operational aggregate linked to a
-- Tenant. Multiple Organizations may exist under a single Tenant,
-- each representing a distinct business unit (branch, subsidiary,
-- brand). All operational modules (ERP, CRM, HRM, Accounting,
-- Commerce) will reference an Organization as their immediate
-- parent in subsequent migrations.
--
-- Conventions (consistent with V1__create_tenants_table.sql):
--   - UUID primary keys
--   - snake_case column names
--   - explicit constraint names with platform prefix
--   - `created_at` and `updated_at` as INSTANT (UTC) timestamps
--   - status stored as STRING (Hibernate EnumType.STRING)
--   - foreign keys explicitly named fk_<table>_<referenced>
--
-- Compatible with PostgreSQL 14+ (production target).
-- Compatible with H2 (MODE=PostgreSQL) for the `local` profile.
-- ============================================================

CREATE TABLE organizations (
    id          UUID            NOT NULL,
    tenant_id   UUID            NOT NULL,
    name        VARCHAR(200)    NOT NULL,
    description VARCHAR(1000),
    status      VARCHAR(20)     NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_organizations            PRIMARY KEY (id),
    CONSTRAINT fk_organizations_tenant     FOREIGN KEY (tenant_id)
                                          REFERENCES tenants (id),
    CONSTRAINT uk_organizations_tenant_name UNIQUE (tenant_id, name),
    CONSTRAINT ck_organizations_status     CHECK (status IN ('ACTIVE', 'INACTIVE', 'ARCHIVED'))
);

-- Indexes to support the most common query patterns:
--   1. Listing all Organizations under a Tenant (idx_organizations_tenant)
--   2. Looking up an Organization by name within a Tenant
--      (the UNIQUE constraint already creates an implicit index in
--      PostgreSQL, but H2 in MODE=PostgreSQL does not always do so;
--      this explicit index guarantees consistent performance across
--      both databases).
CREATE INDEX idx_organizations_tenant ON organizations (tenant_id);
CREATE INDEX idx_organizations_name   ON organizations (name);
