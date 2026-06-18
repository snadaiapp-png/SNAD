-- ============================================================
-- SANAD Platform - Flyway migration V1
-- ------------------------------------------------------------
-- Creates the foundational `tenants` table.
--
-- This is the root table of the multi-tenant data model. Every
-- other domain table in the platform will carry a `tenant_id`
-- foreign key back to this table.
--
-- Conventions established here (to be reused by future migrations):
--   - UUID primary keys
--   - snake_case column names
--   - explicit constraint names with platform prefix
--   - `created_at` and `updated_at` as INSTANT (UTC) timestamps
--   - status stored as STRING (Hibernate EnumType.STRING)
--
-- Compatible with PostgreSQL 14+ (production target).
-- Compatible with H2 (MODE=PostgreSQL) for the `local` profile.
-- ============================================================

CREATE TABLE tenants (
    id          UUID            NOT NULL,
    name        VARCHAR(200)    NOT NULL,
    subdomain   VARCHAR(63)     NOT NULL,
    status      VARCHAR(20)     NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_tenants PRIMARY KEY (id),
    CONSTRAINT uk_tenants_subdomain UNIQUE (subdomain)
);

-- Index to support fast subdomain lookups (the UNIQUE constraint
-- already creates an implicit index in PostgreSQL, but H2 in
-- MODE=PostgreSQL does not always do so; this explicit index
-- guarantees consistent performance across both databases).
CREATE INDEX idx_tenants_subdomain ON tenants (subdomain);

-- Optional: add a CHECK constraint to enforce status values at the
-- database level. Hibernate's EnumType.STRING already guarantees
-- valid values, but a defence-in-depth check at the DB layer is
-- cheap insurance against direct SQL inserts that bypass Hibernate.
ALTER TABLE tenants
    ADD CONSTRAINT ck_tenants_status
    CHECK (status IN ('ACTIVE', 'SUSPENDED', 'PENDING', 'ARCHIVED'));
