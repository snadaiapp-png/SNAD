-- ============================================================
-- V20260926_2 — Platform IAM role metadata
-- ============================================================
-- Classifies canonical tenant-scoped roles for Platform IAM protection.
-- This table is metadata only; authorization continues to use the existing
-- roles / role_capabilities / user_role_assignments capability engine.
-- ============================================================

CREATE TABLE platform_role_metadata (
    control_tenant_id   UUID                     NOT NULL,
    role_id             UUID                     NOT NULL,
    role_type           VARCHAR(20)              NOT NULL,
    protected           BOOLEAN                  NOT NULL DEFAULT FALSE,
    owner_role          BOOLEAN                  NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_platform_role_metadata_tenant_role
        UNIQUE (control_tenant_id, role_id),
    CONSTRAINT fk_platform_role_metadata_tenant_role
        FOREIGN KEY (control_tenant_id, role_id)
        REFERENCES roles(tenant_id, id),
    CONSTRAINT ck_platform_role_metadata_type
        CHECK (role_type IN ('SYSTEM', 'CUSTOM'))
);

CREATE INDEX idx_platform_role_metadata_tenant_type
    ON platform_role_metadata (control_tenant_id, role_type);

ALTER TABLE platform_role_metadata ENABLE ROW LEVEL SECURITY;
ALTER TABLE platform_role_metadata FORCE ROW LEVEL SECURITY;

CREATE POLICY platform_role_metadata_tenant_isolation ON platform_role_metadata
    USING (control_tenant_id = current_setting('app.tenant_id', true)::UUID)
    WITH CHECK (control_tenant_id = current_setting('app.tenant_id', true)::UUID);
