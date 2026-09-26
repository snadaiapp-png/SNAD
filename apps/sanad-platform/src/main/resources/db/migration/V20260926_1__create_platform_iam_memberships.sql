-- ============================================================
-- V20260926_1 — Platform IAM memberships
-- ============================================================
-- Formal Platform Membership over an existing Control Plane user identity.
-- The composite foreign key is the hard database boundary preventing a
-- membership from referencing a user row from another tenant.
-- ============================================================

CREATE TABLE platform_memberships (
    id                  UUID                     NOT NULL DEFAULT gen_random_uuid(),
    control_tenant_id   UUID                     NOT NULL,
    user_id             UUID                     NOT NULL,
    status              VARCHAR(20)              NOT NULL,
    invited_at          TIMESTAMP WITH TIME ZONE,
    activated_at        TIMESTAMP WITH TIME ZONE,
    suspended_at        TIMESTAMP WITH TIME ZONE,
    locked_at           TIMESTAMP WITH TIME ZONE,
    disabled_at         TIMESTAMP WITH TIME ZONE,
    created_by          UUID,
    updated_by          UUID,
    status_reason       TEXT,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_platform_memberships PRIMARY KEY (id),
    CONSTRAINT uk_platform_memberships_tenant_user
        UNIQUE (control_tenant_id, user_id),
    CONSTRAINT fk_platform_memberships_tenant_user
        FOREIGN KEY (control_tenant_id, user_id)
        REFERENCES users(tenant_id, id),
    CONSTRAINT ck_platform_memberships_status
        CHECK (status IN ('INVITED', 'ACTIVE', 'SUSPENDED', 'LOCKED', 'DISABLED'))
);

CREATE INDEX idx_platform_memberships_tenant_status
    ON platform_memberships (control_tenant_id, status);

CREATE INDEX idx_platform_memberships_tenant_user
    ON platform_memberships (control_tenant_id, user_id);

ALTER TABLE platform_memberships ENABLE ROW LEVEL SECURITY;
ALTER TABLE platform_memberships FORCE ROW LEVEL SECURITY;

CREATE POLICY platform_memberships_tenant_isolation ON platform_memberships
    USING (control_tenant_id = current_setting('app.tenant_id', true)::UUID)
    WITH CHECK (control_tenant_id = current_setting('app.tenant_id', true)::UUID);
