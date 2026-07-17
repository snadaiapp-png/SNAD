-- EXEC-PROMPT-CRM-006: Contacts, People and Multi-Account Relationship Model
-- Transitional migration: crm_contacts.account_id remains available for legacy contracts.
-- The canonical multi-account association is crm_contact_account_relationships.

ALTER TABLE crm_contacts ADD COLUMN IF NOT EXISTS legal_name VARCHAR(240);
ALTER TABLE crm_contacts ADD COLUMN IF NOT EXISTS preferred_name VARCHAR(160);
ALTER TABLE crm_contacts ADD COLUMN IF NOT EXISTS middle_name VARCHAR(120);
ALTER TABLE crm_contacts ADD COLUMN IF NOT EXISTS pronouns VARCHAR(80);
ALTER TABLE crm_contacts ADD COLUMN IF NOT EXISTS source VARCHAR(120);

UPDATE crm_contacts
SET legal_name = COALESCE(legal_name, display_name),
    preferred_name = COALESCE(preferred_name, given_name)
WHERE legal_name IS NULL OR preferred_name IS NULL;

CREATE TABLE IF NOT EXISTS crm_contact_relationship_roles (
    id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    code VARCHAR(80) NOT NULL,
    name_ar VARCHAR(160) NOT NULL,
    name_en VARCHAR(160) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by UUID NOT NULL,
    updated_by UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_crm_contact_relationship_roles PRIMARY KEY (id),
    CONSTRAINT uk_crm_contact_relationship_roles_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uk_crm_contact_relationship_roles_code UNIQUE (tenant_id, code),
    CONSTRAINT fk_crm_contact_relationship_roles_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT ck_crm_contact_relationship_roles_code CHECK (CHAR_LENGTH(TRIM(code)) BETWEEN 2 AND 80)
);

CREATE TABLE IF NOT EXISTS crm_contact_account_relationships (
    id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    contact_id UUID NOT NULL,
    account_id UUID NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    role_code VARCHAR(40) NOT NULL,
    custom_role_id UUID,
    role_key VARCHAR(120) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    primary_relationship BOOLEAN NOT NULL DEFAULT FALSE,
    primary_scope_contact_id UUID,
    valid_from DATE,
    valid_to DATE,
    job_title VARCHAR(160),
    department VARCHAR(160),
    decision_authority VARCHAR(32) NOT NULL DEFAULT 'NONE',
    owner_user_id UUID,
    created_by UUID NOT NULL,
    updated_by UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    archived_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT pk_crm_contact_account_relationships PRIMARY KEY (id),
    CONSTRAINT uk_crm_contact_account_relationships_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uk_crm_contact_account_relationship_identity UNIQUE (tenant_id, contact_id, account_id, role_key),
    CONSTRAINT uk_crm_contact_account_relationship_primary UNIQUE (tenant_id, primary_scope_contact_id),
    CONSTRAINT fk_crm_contact_relationship_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_crm_contact_relationship_contact_same_tenant FOREIGN KEY (tenant_id, contact_id) REFERENCES crm_contacts (tenant_id, id),
    CONSTRAINT fk_crm_contact_relationship_account_same_tenant FOREIGN KEY (tenant_id, account_id) REFERENCES crm_accounts (tenant_id, id),
    CONSTRAINT fk_crm_contact_relationship_custom_role_same_tenant FOREIGN KEY (tenant_id, custom_role_id) REFERENCES crm_contact_relationship_roles (tenant_id, id),
    CONSTRAINT ck_crm_contact_relationship_role CHECK (role_code IN ('DECISION_MAKER','BILLING','TECHNICAL','INFLUENCER','EMPLOYEE','PARTNER','OTHER')),
    CONSTRAINT ck_crm_contact_relationship_custom_role CHECK ((role_code = 'OTHER' AND custom_role_id IS NOT NULL) OR (role_code <> 'OTHER' AND custom_role_id IS NULL) OR (role_code = 'OTHER' AND custom_role_id IS NULL)),
    CONSTRAINT ck_crm_contact_relationship_status CHECK (status IN ('ACTIVE','INACTIVE','ARCHIVED')),
    CONSTRAINT ck_crm_contact_relationship_dates CHECK (valid_to IS NULL OR valid_from IS NULL OR valid_to >= valid_from),
    CONSTRAINT ck_crm_contact_relationship_decision CHECK (decision_authority IN ('NONE','INFLUENCER','RECOMMENDER','DECIDER','FINAL_APPROVER')),
    CONSTRAINT ck_crm_contact_relationship_primary_scope CHECK (
        (primary_relationship = TRUE AND status = 'ACTIVE' AND primary_scope_contact_id = contact_id)
        OR (primary_scope_contact_id IS NULL AND (primary_relationship = FALSE OR status <> 'ACTIVE'))
    )
);

CREATE INDEX IF NOT EXISTS idx_crm_contact_relationships_contact
    ON crm_contact_account_relationships (tenant_id, contact_id, status, updated_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS idx_crm_contact_relationships_account
    ON crm_contact_account_relationships (tenant_id, account_id, status, updated_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS idx_crm_contact_relationships_search
    ON crm_contact_account_relationships (tenant_id, role_code, department, job_title, owner_user_id, status);

CREATE TABLE IF NOT EXISTS crm_contact_relationship_history (
    id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    relationship_id UUID NOT NULL,
    contact_id UUID NOT NULL,
    account_id UUID NOT NULL,
    event_type VARCHAR(40) NOT NULL,
    previous_version BIGINT,
    new_version BIGINT NOT NULL,
    snapshot TEXT NOT NULL,
    changed_by UUID NOT NULL,
    changed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_crm_contact_relationship_history PRIMARY KEY (id),
    CONSTRAINT fk_crm_contact_relationship_history_relationship_same_tenant FOREIGN KEY (tenant_id, relationship_id) REFERENCES crm_contact_account_relationships (tenant_id, id),
    CONSTRAINT fk_crm_contact_relationship_history_contact_same_tenant FOREIGN KEY (tenant_id, contact_id) REFERENCES crm_contacts (tenant_id, id),
    CONSTRAINT fk_crm_contact_relationship_history_account_same_tenant FOREIGN KEY (tenant_id, account_id) REFERENCES crm_accounts (tenant_id, id),
    CONSTRAINT ck_crm_contact_relationship_history_event CHECK (event_type IN ('CREATED','UPDATED','PRIMARY_CHANGED','ACTIVATED','DEACTIVATED','ARCHIVED','REACTIVATED','MIGRATED'))
);
CREATE INDEX IF NOT EXISTS idx_crm_contact_relationship_history_subject
    ON crm_contact_relationship_history (tenant_id, relationship_id, changed_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS idx_crm_contact_relationship_history_contact
    ON crm_contact_relationship_history (tenant_id, contact_id, changed_at DESC, id DESC);

CREATE TABLE IF NOT EXISTS crm_contact_ownership_history (
    id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    contact_id UUID NOT NULL,
    previous_owner_user_id UUID,
    new_owner_user_id UUID,
    changed_by UUID NOT NULL,
    changed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    reason VARCHAR(500),
    CONSTRAINT pk_crm_contact_ownership_history PRIMARY KEY (id),
    CONSTRAINT fk_crm_contact_ownership_history_contact_same_tenant FOREIGN KEY (tenant_id, contact_id) REFERENCES crm_contacts (tenant_id, id)
);
CREATE INDEX IF NOT EXISTS idx_crm_contact_ownership_history_contact
    ON crm_contact_ownership_history (tenant_id, contact_id, changed_at DESC, id DESC);

-- Deterministic and idempotent backfill. The legacy relationship ID is the contact ID.
INSERT INTO crm_contact_account_relationships (
    id, tenant_id, contact_id, account_id, version, role_code, custom_role_id, role_key,
    status, primary_relationship, primary_scope_contact_id, valid_from, valid_to,
    job_title, department, decision_authority, owner_user_id,
    created_by, updated_by, created_at, updated_at, archived_at
)
SELECT c.id, c.tenant_id, c.id, c.account_id, 0, 'OTHER', NULL, 'LEGACY_ACCOUNT',
       CASE WHEN c.lifecycle_status = 'ARCHIVED' THEN 'INACTIVE' ELSE 'ACTIVE' END,
       CASE WHEN c.lifecycle_status = 'ARCHIVED' THEN FALSE ELSE TRUE END,
       CASE WHEN c.lifecycle_status = 'ARCHIVED' THEN NULL ELSE c.id END,
       CAST(c.created_at AS DATE), NULL, NULL, NULL, 'NONE', c.owner_user_id,
       c.created_by, c.updated_by, c.created_at, c.updated_at, NULL
FROM crm_contacts c
WHERE c.account_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM crm_contact_account_relationships r
      WHERE r.tenant_id = c.tenant_id AND r.contact_id = c.id AND r.account_id = c.account_id
  );

INSERT INTO crm_contact_relationship_history (
    id, tenant_id, relationship_id, contact_id, account_id, event_type,
    previous_version, new_version, snapshot, changed_by, changed_at
)
SELECT c.id, c.tenant_id, c.id, c.id, c.account_id, 'MIGRATED', NULL, 0,
       '{"source":"crm_contacts.account_id","roleCode":"OTHER","roleKey":"LEGACY_ACCOUNT"}',
       c.updated_by, c.updated_at
FROM crm_contacts c
WHERE c.account_id IS NOT NULL
  AND EXISTS (
      SELECT 1 FROM crm_contact_account_relationships r
      WHERE r.tenant_id = c.tenant_id AND r.id = c.id
  )
  AND NOT EXISTS (
      SELECT 1 FROM crm_contact_relationship_history h
      WHERE h.tenant_id = c.tenant_id AND h.id = c.id
  );

-- Legacy compatibility remains read/write during CRM-006. Removal is deferred to a later gate
-- after all callers use crm_contact_account_relationships and generated clients are migrated.
