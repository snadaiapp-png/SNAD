-- ============================================================
-- SNAD CRM — Enterprise Account and Customer Master
-- EXEC-PROMPT-CRM-005
-- Forward-only, tenant-scoped, PostgreSQL/H2 PostgreSQL-mode compatible.
-- ============================================================

CREATE TABLE crm_account_taxonomies (
    id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    taxonomy_type VARCHAR(24) NOT NULL,
    code VARCHAR(80) NOT NULL,
    name_ar VARCHAR(240) NOT NULL,
    name_en VARCHAR(240) NOT NULL,
    parent_id UUID,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by UUID NOT NULL,
    updated_by UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_crm_account_taxonomies PRIMARY KEY (id),
    CONSTRAINT uk_crm_account_taxonomies_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uk_crm_account_taxonomies_code UNIQUE (tenant_id, taxonomy_type, code),
    CONSTRAINT fk_crm_account_taxonomies_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_crm_account_taxonomies_parent FOREIGN KEY (tenant_id, parent_id)
        REFERENCES crm_account_taxonomies (tenant_id, id),
    CONSTRAINT ck_crm_account_taxonomies_type CHECK (taxonomy_type IN ('CLASSIFICATION','SEGMENT')),
    CONSTRAINT ck_crm_account_taxonomies_parent_not_self CHECK (parent_id IS NULL OR parent_id <> id)
);
CREATE INDEX idx_crm_account_taxonomies_active
    ON crm_account_taxonomies (tenant_id, taxonomy_type, active, code);

CREATE TABLE crm_account_profiles (
    account_id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    legal_name VARCHAR(320) NOT NULL,
    trade_name VARCHAR(320),
    registration_number VARCHAR(160),
    tax_registration_number VARCHAR(160),
    industry VARCHAR(160),
    organization_size VARCHAR(32),
    website_url VARCHAR(500),
    customer_tier VARCHAR(40),
    risk_level VARCHAR(24) NOT NULL DEFAULT 'UNKNOWN',
    risk_flags VARCHAR(1000),
    classification_id UUID,
    segment_id UUID,
    merge_candidate BOOLEAN NOT NULL DEFAULT FALSE,
    created_by UUID NOT NULL,
    updated_by UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_crm_account_profiles PRIMARY KEY (account_id),
    CONSTRAINT uk_crm_account_profiles_tenant_id UNIQUE (tenant_id, account_id),
    CONSTRAINT fk_crm_account_profiles_account FOREIGN KEY (tenant_id, account_id)
        REFERENCES crm_accounts (tenant_id, id),
    CONSTRAINT fk_crm_account_profiles_classification FOREIGN KEY (tenant_id, classification_id)
        REFERENCES crm_account_taxonomies (tenant_id, id),
    CONSTRAINT fk_crm_account_profiles_segment FOREIGN KEY (tenant_id, segment_id)
        REFERENCES crm_account_taxonomies (tenant_id, id),
    CONSTRAINT ck_crm_account_profiles_size CHECK (
        organization_size IS NULL OR organization_size IN ('MICRO','SMALL','MEDIUM','LARGE','ENTERPRISE')
    ),
    CONSTRAINT ck_crm_account_profiles_risk CHECK (
        risk_level IN ('UNKNOWN','LOW','MEDIUM','HIGH','CRITICAL')
    )
);
CREATE INDEX idx_crm_account_profiles_registration
    ON crm_account_profiles (tenant_id, registration_number);
CREATE INDEX idx_crm_account_profiles_tax_registration
    ON crm_account_profiles (tenant_id, tax_registration_number);
CREATE INDEX idx_crm_account_profiles_segment
    ON crm_account_profiles (tenant_id, segment_id, customer_tier, risk_level);

CREATE TABLE crm_account_relationships (
    id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    source_account_id UUID NOT NULL,
    target_account_id UUID NOT NULL,
    relationship_type VARCHAR(32) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    effective_from DATE,
    effective_to DATE,
    description VARCHAR(500),
    created_by UUID NOT NULL,
    updated_by UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_crm_account_relationships PRIMARY KEY (id),
    CONSTRAINT uk_crm_account_relationships_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uk_crm_account_relationships_active UNIQUE
        (tenant_id, source_account_id, target_account_id, relationship_type),
    CONSTRAINT fk_crm_account_relationships_source FOREIGN KEY (tenant_id, source_account_id)
        REFERENCES crm_accounts (tenant_id, id),
    CONSTRAINT fk_crm_account_relationships_target FOREIGN KEY (tenant_id, target_account_id)
        REFERENCES crm_accounts (tenant_id, id),
    CONSTRAINT ck_crm_account_relationships_type CHECK (
        relationship_type IN ('PARENT','SUBSIDIARY','BRANCH','PARTNER')
    ),
    CONSTRAINT ck_crm_account_relationships_status CHECK (status IN ('ACTIVE','ENDED')),
    CONSTRAINT ck_crm_account_relationships_not_self CHECK (source_account_id <> target_account_id),
    CONSTRAINT ck_crm_account_relationships_dates CHECK (
        effective_to IS NULL OR effective_from IS NULL OR effective_to >= effective_from
    )
);
CREATE INDEX idx_crm_account_relationships_source
    ON crm_account_relationships (tenant_id, source_account_id, status, relationship_type);
CREATE INDEX idx_crm_account_relationships_target
    ON crm_account_relationships (tenant_id, target_account_id, status, relationship_type);

CREATE TABLE crm_account_external_identifiers (
    id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    account_id UUID NOT NULL,
    provider VARCHAR(120) NOT NULL,
    system_scope VARCHAR(120) NOT NULL,
    external_id VARCHAR(240) NOT NULL,
    label VARCHAR(240),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by UUID NOT NULL,
    updated_by UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_crm_account_external_identifiers PRIMARY KEY (id),
    CONSTRAINT uk_crm_account_external_identifiers_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uk_crm_account_external_identifier UNIQUE
        (tenant_id, provider, system_scope, external_id),
    CONSTRAINT fk_crm_account_external_identifiers_account FOREIGN KEY (tenant_id, account_id)
        REFERENCES crm_accounts (tenant_id, id)
);
CREATE INDEX idx_crm_account_external_identifiers_account
    ON crm_account_external_identifiers (tenant_id, account_id, active);

CREATE TABLE crm_account_status_history (
    id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    account_id UUID NOT NULL,
    from_status VARCHAR(32),
    to_status VARCHAR(32) NOT NULL,
    reason VARCHAR(500),
    changed_by UUID NOT NULL,
    changed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_crm_account_status_history PRIMARY KEY (id),
    CONSTRAINT fk_crm_account_status_history_account FOREIGN KEY (tenant_id, account_id)
        REFERENCES crm_accounts (tenant_id, id)
);
CREATE INDEX idx_crm_account_status_history_account
    ON crm_account_status_history (tenant_id, account_id, changed_at DESC);

CREATE TABLE crm_account_ownership_history (
    id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    account_id UUID NOT NULL,
    from_owner_user_id UUID,
    to_owner_user_id UUID,
    reason VARCHAR(500),
    changed_by UUID NOT NULL,
    changed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_crm_account_ownership_history PRIMARY KEY (id),
    CONSTRAINT fk_crm_account_ownership_history_account FOREIGN KEY (tenant_id, account_id)
        REFERENCES crm_accounts (tenant_id, id)
);
CREATE INDEX idx_crm_account_ownership_history_account
    ON crm_account_ownership_history (tenant_id, account_id, changed_at DESC);

CREATE TABLE crm_account_projection_snapshots (
    id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    account_id UUID NOT NULL,
    projection_type VARCHAR(40) NOT NULL,
    source_system VARCHAR(120) NOT NULL,
    connection_status VARCHAR(24) NOT NULL,
    payload_json TEXT,
    source_updated_at TIMESTAMP WITH TIME ZONE,
    synced_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_crm_account_projection_snapshots PRIMARY KEY (id),
    CONSTRAINT uk_crm_account_projection_snapshot UNIQUE
        (tenant_id, account_id, projection_type, source_system),
    CONSTRAINT fk_crm_account_projection_snapshots_account FOREIGN KEY (tenant_id, account_id)
        REFERENCES crm_accounts (tenant_id, id),
    CONSTRAINT ck_crm_account_projection_type CHECK (
        projection_type IN ('FINANCIAL_SUMMARY','ORDERS','SERVICE')
    ),
    CONSTRAINT ck_crm_account_projection_status CHECK (
        connection_status IN ('READY','STALE','NOT_CONNECTED','ERROR')
    )
);
CREATE INDEX idx_crm_account_projection_snapshots_account
    ON crm_account_projection_snapshots (tenant_id, account_id, projection_type, synced_at DESC);

-- Backfill an enterprise profile and initial history for existing accounts.
INSERT INTO crm_account_profiles (
    account_id, tenant_id, version, legal_name, trade_name, risk_level,
    merge_candidate, created_by, updated_by, created_at, updated_at
)
SELECT account.id, account.tenant_id, 0, account.display_name, account.display_name, 'UNKNOWN',
       FALSE, account.created_by, account.updated_by, account.created_at, account.updated_at
FROM crm_accounts account
WHERE NOT EXISTS (
    SELECT 1 FROM crm_account_profiles profile
    WHERE profile.tenant_id = account.tenant_id AND profile.account_id = account.id
);

INSERT INTO crm_account_status_history (
    id, tenant_id, account_id, from_status, to_status, reason, changed_by, changed_at
)
SELECT gen_random_uuid(), account.tenant_id, account.id, NULL, account.lifecycle_status,
       'CRM-005 profile backfill', account.created_by, account.created_at
FROM crm_accounts account
WHERE NOT EXISTS (
    SELECT 1 FROM crm_account_status_history history
    WHERE history.tenant_id = account.tenant_id AND history.account_id = account.id
);

INSERT INTO crm_account_ownership_history (
    id, tenant_id, account_id, from_owner_user_id, to_owner_user_id, reason, changed_by, changed_at
)
SELECT gen_random_uuid(), account.tenant_id, account.id, NULL, account.owner_user_id,
       'CRM-005 profile backfill', account.created_by, account.created_at
FROM crm_accounts account
WHERE account.owner_user_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM crm_account_ownership_history history
    WHERE history.tenant_id = account.tenant_id AND history.account_id = account.id
);

-- Granular capabilities for the enterprise Account Master.
INSERT INTO access_capabilities (id, code, name, description, status, created_at, updated_at)
SELECT gen_random_uuid(), capability.code, capability.name, capability.description,
       'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM (VALUES
    ('CRM.ACCOUNT.MASTER.READ', 'Read Account Master', 'View enterprise account master profile and projections'),
    ('CRM.ACCOUNT.MASTER.WRITE', 'Write Account Master', 'Maintain enterprise account master profile and taxonomies'),
    ('CRM.ACCOUNT.RELATIONSHIP.READ', 'Read Account Relationships', 'View enterprise account relationships'),
    ('CRM.ACCOUNT.RELATIONSHIP.WRITE', 'Write Account Relationships', 'Maintain enterprise account relationships'),
    ('CRM.ACCOUNT.IDENTIFIER.READ', 'Read Account Identifiers', 'View external account identifiers'),
    ('CRM.ACCOUNT.IDENTIFIER.WRITE', 'Write Account Identifiers', 'Maintain external account identifiers'),
    ('CRM.ACCOUNT.HISTORY.READ', 'Read Account History', 'View account status and ownership histories'),
    ('CRM.ACCOUNT.RISK.READ', 'Read Account Risk', 'View account tier and risk classifications'),
    ('CRM.ACCOUNT.RISK.WRITE', 'Write Account Risk', 'Maintain account tier and risk classifications')
) AS capability(code, name, description)
WHERE NOT EXISTS (
    SELECT 1 FROM access_capabilities existing WHERE existing.code = capability.code
);

INSERT INTO role_capabilities (id, tenant_id, role_id, capability_id, created_at)
SELECT gen_random_uuid(), role.tenant_id, role.id, capability.id, CURRENT_TIMESTAMP
FROM roles role
JOIN access_capabilities capability ON capability.code IN (
    'CRM.ACCOUNT.MASTER.READ','CRM.ACCOUNT.MASTER.WRITE',
    'CRM.ACCOUNT.RELATIONSHIP.READ','CRM.ACCOUNT.RELATIONSHIP.WRITE',
    'CRM.ACCOUNT.IDENTIFIER.READ','CRM.ACCOUNT.IDENTIFIER.WRITE',
    'CRM.ACCOUNT.HISTORY.READ','CRM.ACCOUNT.RISK.READ','CRM.ACCOUNT.RISK.WRITE'
) AND capability.status = 'ACTIVE'
WHERE role.code = 'ADMIN' AND role.status = 'ACTIVE'
  AND NOT EXISTS (
      SELECT 1 FROM role_capabilities existing
      WHERE existing.tenant_id = role.tenant_id
        AND existing.role_id = role.id
        AND existing.capability_id = capability.id
  );
