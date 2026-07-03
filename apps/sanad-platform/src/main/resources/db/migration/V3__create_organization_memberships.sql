-- ============================================================
-- SANAD Platform - Flyway migration V3
-- ------------------------------------------------------------
-- Creates the `organization_memberships` table.
--
-- A membership links an email address to an Organization within
-- a Tenant. It is the persistence foundation for "who belongs to
-- which organization". No User entity reference yet; the email
-- column will eventually be joinable to a future users table.
--
-- Conventions (consistent with V1 and V2):
--   - UUID primary keys
--   - snake_case column names
--   - explicit constraint names with platform prefix
--   - created_at and updated_at as TIMESTAMP WITH TIME ZONE (UTC)
--   - status stored as STRING (Hibernate EnumType.STRING)
--   - FK references named fk_<child>_<parent>
--
-- Tenant isolation is enforced at the query layer (every repository
-- method takes a tenantId parameter). The tenant_id column is a plain
-- UUID (NOT a FK to tenants) to keep membership queries lightweight;
-- the FK is still declared for referential integrity.
--
-- Compatible with PostgreSQL 14+ (production target).
-- Compatible with H2 (MODE=PostgreSQL) for the local profile.
-- ============================================================

CREATE TABLE organization_memberships (
    id              UUID            NOT NULL,
    tenant_id       UUID            NOT NULL,
    organization_id UUID            NOT NULL,
    email           VARCHAR(255)    NOT NULL,
    display_name    VARCHAR(200),
    status          VARCHAR(20)     NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_org_memberships PRIMARY KEY (id),
    CONSTRAINT fk_org_memberships_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_org_memberships_organization
        FOREIGN KEY (organization_id) REFERENCES organizations (id),
    CONSTRAINT uk_org_memberships_tenant_org_email
        UNIQUE (tenant_id, organization_id, email),
    CONSTRAINT ck_org_memberships_status
        CHECK (status IN ('ACTIVE', 'INACTIVE', 'INVITED', 'REMOVED'))
);

-- Indexes to support common query patterns:
--   1. idx_org_memberships_tenant: speeds up findByTenantId()
--   2. idx_org_memberships_organization: speeds up cross-org queries
--   3. idx_org_memberships_tenant_org: speeds up findByTenantIdAndOrganizationId()
--   4. idx_org_memberships_email: speeds up email lookups + existence checks
CREATE INDEX idx_org_memberships_tenant      ON organization_memberships (tenant_id);
CREATE INDEX idx_org_memberships_organization ON organization_memberships (organization_id);
CREATE INDEX idx_org_memberships_tenant_org  ON organization_memberships (tenant_id, organization_id);
CREATE INDEX idx_org_memberships_email       ON organization_memberships (email);
