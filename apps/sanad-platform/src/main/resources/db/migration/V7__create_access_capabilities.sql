CREATE TABLE access_capabilities (
    id UUID NOT NULL,
    code VARCHAR(150) NOT NULL,
    name VARCHAR(200) NOT NULL,
    description VARCHAR(1000),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_access_capabilities PRIMARY KEY (id),
    CONSTRAINT uk_access_capabilities_code UNIQUE (code),
    CONSTRAINT ck_access_capabilities_status CHECK (status IN ('ACTIVE','INACTIVE'))
);

CREATE INDEX idx_access_capabilities_status ON access_capabilities(status);
