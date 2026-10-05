-- SANAD Users Phase 5: Dynamic Application IAM Registry.
-- Registry is discovery/metadata only; canonical RBAC/capability/scope state
-- remains the sole authorization source of truth.

CREATE TABLE IF NOT EXISTS application_iam_contracts (
    application_code       VARCHAR(50)  NOT NULL,
    contract_version       VARCHAR(20)  NOT NULL,
    status                 VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    capability_namespaces  JSONB        NOT NULL DEFAULT '[]'::jsonb,
    supported_scopes       JSONB        NOT NULL DEFAULT '[]'::jsonb,
    declared_capabilities  JSONB        NOT NULL DEFAULT '[]'::jsonb,
    role_templates         JSONB        NOT NULL DEFAULT '[]'::jsonb,
    compatibility_metadata JSONB        NOT NULL DEFAULT '{}'::jsonb,
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_application_iam_contracts PRIMARY KEY (application_code),
    CONSTRAINT fk_application_iam_contracts_application
        FOREIGN KEY (application_code) REFERENCES applications(code) ON DELETE CASCADE,
    CONSTRAINT ck_application_iam_contracts_status
        CHECK (status IN ('ACTIVE', 'INACTIVE', 'STALE', 'INCOMPATIBLE')),
    CONSTRAINT ck_application_iam_contracts_namespaces_array
        CHECK (jsonb_typeof(capability_namespaces) = 'array'),
    CONSTRAINT ck_application_iam_contracts_scopes_array
        CHECK (jsonb_typeof(supported_scopes) = 'array'),
    CONSTRAINT ck_application_iam_contracts_capabilities_array
        CHECK (jsonb_typeof(declared_capabilities) = 'array'),
    CONSTRAINT ck_application_iam_contracts_role_templates_array
        CHECK (jsonb_typeof(role_templates) = 'array'),
    CONSTRAINT ck_application_iam_contracts_compatibility_object
        CHECK (jsonb_typeof(compatibility_metadata) = 'object')
);

CREATE INDEX IF NOT EXISTS idx_application_iam_contracts_status
    ON application_iam_contracts(status);
