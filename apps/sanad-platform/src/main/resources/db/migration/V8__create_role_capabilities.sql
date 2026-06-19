CREATE TABLE role_capabilities (
    id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    role_id UUID NOT NULL,
    capability_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_role_capabilities PRIMARY KEY (id),
    CONSTRAINT fk_role_capabilities_role FOREIGN KEY (tenant_id, role_id)
        REFERENCES roles(tenant_id, id),
    CONSTRAINT fk_role_capabilities_capability FOREIGN KEY (capability_id)
        REFERENCES access_capabilities(id),
    CONSTRAINT uk_role_capabilities UNIQUE (tenant_id, role_id, capability_id)
);

CREATE INDEX idx_role_capabilities_role ON role_capabilities(tenant_id, role_id);
CREATE INDEX idx_role_capabilities_capability ON role_capabilities(capability_id);
