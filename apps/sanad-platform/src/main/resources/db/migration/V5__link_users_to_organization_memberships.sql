-- ============================================================
-- SANAD Platform - Flyway migration V5
-- ------------------------------------------------------------
-- Links Organization Memberships to tenant-scoped Users while
-- preserving invitation-first records where no User exists yet.
--
-- user_id remains nullable so an email invitation can be created
-- before the corresponding User account is provisioned.
--
-- The composite foreign key (tenant_id, user_id) prevents a
-- membership from referencing a User in another Tenant.
-- ============================================================

ALTER TABLE users
    ADD CONSTRAINT uk_users_tenant_id UNIQUE (tenant_id, id);

ALTER TABLE organization_memberships
    ADD COLUMN user_id UUID;

ALTER TABLE organization_memberships
    ADD CONSTRAINT fk_org_memberships_tenant_user
        FOREIGN KEY (tenant_id, user_id)
        REFERENCES users (tenant_id, id);

ALTER TABLE organization_memberships
    ADD CONSTRAINT uk_org_memberships_tenant_org_user
        UNIQUE (tenant_id, organization_id, user_id);

CREATE INDEX idx_org_memberships_tenant_user
    ON organization_memberships (tenant_id, user_id);
