-- ============================================================
-- SANAD Platform - Flyway migration V4
-- ------------------------------------------------------------
-- Creates the `users` table.
--
-- A User is scoped to a Tenant. This is the persistence foundation
-- only — no authentication, password, RBAC, or login logic exists
-- at this stage. Future stages will link Users to Organization
-- Memberships and add role/permission models.
--
-- Conventions (consistent with V1, V2, V3):
--   - UUID primary keys
--   - snake_case column names
--   - explicit constraint names with platform prefix
--   - created_at and updated_at as TIMESTAMP WITH TIME ZONE (UTC)
--   - status stored as STRING (Hibernate EnumType.STRING)
--   - FK references named fk_<child>_<parent>
--
-- Compatible with PostgreSQL 14+ (production target).
-- Compatible with H2 (MODE=PostgreSQL) for the local profile.
-- ============================================================

CREATE TABLE users (
    id           UUID            NOT NULL,
    tenant_id    UUID            NOT NULL,
    email        VARCHAR(255)    NOT NULL,
    display_name VARCHAR(200),
    status       VARCHAR(20)     NOT NULL,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT fk_users_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT uk_users_tenant_email
        UNIQUE (tenant_id, email),
    CONSTRAINT ck_users_status
        CHECK (status IN ('ACTIVE', 'INACTIVE', 'INVITED', 'SUSPENDED', 'ARCHIVED'))
);

-- Indexes to support common query patterns:
CREATE INDEX idx_users_tenant       ON users (tenant_id);
CREATE INDEX idx_users_email        ON users (email);
CREATE INDEX idx_users_tenant_email ON users (tenant_id, email);
