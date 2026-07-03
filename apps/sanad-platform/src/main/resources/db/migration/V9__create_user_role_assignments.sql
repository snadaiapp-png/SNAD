ALTER TABLE organizations
    ADD CONSTRAINT uk_organizations_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE user_role_assignments (
    id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    user_id UUID NOT NULL,
    role_id UUID NOT NULL,
    organization_id UUID,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_user_role_assignments PRIMARY KEY (id),
    CONSTRAINT fk_user_role_user FOREIGN KEY (tenant_id, user_id)
        REFERENCES users(tenant_id, id),
    CONSTRAINT fk_user_role_role FOREIGN KEY (tenant_id, role_id)
        REFERENCES roles(tenant_id, id),
    CONSTRAINT fk_user_role_organization FOREIGN KEY (tenant_id, organization_id)
        REFERENCES organizations(tenant_id, id),
    CONSTRAINT uk_user_role_scope UNIQUE (tenant_id, user_id, role_id, organization_id),
    CONSTRAINT ck_user_role_status CHECK (status IN ('ACTIVE','REVOKED'))
);

CREATE INDEX idx_user_role_user ON user_role_assignments(tenant_id, user_id);
CREATE INDEX idx_user_role_role ON user_role_assignments(tenant_id, role_id);
CREATE INDEX idx_user_role_org ON user_role_assignments(tenant_id, organization_id);
