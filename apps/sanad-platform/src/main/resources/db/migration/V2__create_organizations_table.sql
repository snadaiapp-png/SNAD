-- ============================================================
-- SANAD Platform - Flyway migration V2
-- ------------------------------------------------------------
-- Creates the `organizations` table.
--
-- An Organization is the first operational aggregate built on
-- top of Tenant. Each Organization belongs to exactly one
-- Tenant and has a unique name within that Tenant.
--
-- Conventions (consistent with V1):
--   - UUID primary keys
--   - snake_case column names
--   - explicit constraint names with platform prefix
--   - created_at and updated_at as TIMESTAMP WITH TIME ZONE (UTC)
--   - status stored as STRING (Hibernate EnumType.STRING)
--   - FK references to parent tables named fk_<child>_<parent>
--
-- Compatible with PostgreSQL 14+ (production target).
-- Compatible with H2 (MODE=PostgreSQL) for the local profile.
-- ============================================================

CREATE TABLE organizations (
    id           UUID            NOT NULL,
    tenant_id    UUID            NOT NULL,
    name         VARCHAR(200)    NOT NULL,
    description  VARCHAR(1000),
    status       VARCHAR(20)     NOT NULL,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_organizations PRIMARY KEY (id),
    CONSTRAINT fk_organizations_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT uk_organizations_tenant_name
        UNIQUE (tenant_id, name),
    CONSTRAINT ck_organizations_status
        CHECK (status IN ('ACTIVE', 'INACTIVE', 'ARCHIVED'))
);

-- Indexes to support common query patterns:
--   1. idx_organizations_tenant: speeds up findByTenantId() lookups
--      (most common query in the application layer)
--   2. idx_organizations_name: speeds up existsByTenantIdAndName()
--      uniqueness checks and name searches
CREATE INDEX idx_organizations_tenant ON organizations (tenant_id);
CREATE INDEX idx_organizations_name  ON organizations (name);
