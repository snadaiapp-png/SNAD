CREATE TABLE roles (
    id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    code VARCHAR(100) NOT NULL,
    name VARCHAR(200) NOT NULL,
    description VARCHAR(1000),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_roles PRIMARY KEY (id),
    CONSTRAINT fk_roles_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT uk_roles_tenant_code UNIQUE (tenant_id, code),
    CONSTRAINT uk_roles_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT ck_roles_status CHECK (status IN ('ACTIVE','INACTIVE','ARCHIVED'))
);

CREATE INDEX idx_roles_tenant ON roles(tenant_id);
CREATE INDEX idx_roles_tenant_status ON roles(tenant_id, status);
