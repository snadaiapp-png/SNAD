-- Unified CRM core migration.
-- Uses a version newer than the latest migration on main so normal ordered upgrades apply it.
-- Installs the real application tables used by /api/v1/crm/**.
-- Compatible with PostgreSQL and H2 PostgreSQL mode.

CREATE TABLE crm_accounts (
    id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    display_name VARCHAR(240) NOT NULL,
    normalized_name VARCHAR(240) NOT NULL,
    account_type VARCHAR(40) NOT NULL,
    lifecycle_status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    parent_account_id UUID,
    owner_user_id UUID,
    primary_currency_code VARCHAR(3),
    preferred_locale VARCHAR(35),
    time_zone VARCHAR(64),
    source VARCHAR(80),
    created_by UUID NOT NULL,
    updated_by UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    archived_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT pk_crm_accounts PRIMARY KEY (id),
    CONSTRAINT uk_crm_accounts_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_crm_accounts_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_crm_accounts_parent_same_tenant FOREIGN KEY (tenant_id, parent_account_id) REFERENCES crm_accounts (tenant_id, id),
    CONSTRAINT ck_crm_accounts_type CHECK (account_type IN ('BUSINESS','PERSON','PARTNER','PROSPECT','OTHER')),
    CONSTRAINT ck_crm_accounts_status CHECK (lifecycle_status IN ('ACTIVE','INACTIVE','ARCHIVED')),
    CONSTRAINT ck_crm_accounts_currency CHECK (primary_currency_code IS NULL OR CHAR_LENGTH(primary_currency_code) = 3),
    CONSTRAINT ck_crm_accounts_parent_not_self CHECK (parent_account_id IS NULL OR parent_account_id <> id)
);
CREATE INDEX idx_crm_accounts_tenant_status ON crm_accounts (tenant_id, lifecycle_status, updated_at DESC);
CREATE INDEX idx_crm_accounts_tenant_name ON crm_accounts (tenant_id, normalized_name, id);
CREATE INDEX idx_crm_accounts_tenant_owner ON crm_accounts (tenant_id, owner_user_id, lifecycle_status);

CREATE TABLE crm_contacts (
    id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    account_id UUID,
    given_name VARCHAR(120) NOT NULL,
    family_name VARCHAR(120),
    display_name VARCHAR(240) NOT NULL,
    normalized_name VARCHAR(240) NOT NULL,
    primary_email VARCHAR(255),
    normalized_email VARCHAR(255),
    primary_phone VARCHAR(64),
    preferred_locale VARCHAR(35),
    time_zone VARCHAR(64),
    lifecycle_status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    owner_user_id UUID,
    consent_summary VARCHAR(40) NOT NULL DEFAULT 'UNKNOWN',
    created_by UUID NOT NULL,
    updated_by UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    archived_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT pk_crm_contacts PRIMARY KEY (id),
    CONSTRAINT uk_crm_contacts_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_crm_contacts_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_crm_contacts_account_same_tenant FOREIGN KEY (tenant_id, account_id) REFERENCES crm_accounts (tenant_id, id),
    CONSTRAINT ck_crm_contacts_status CHECK (lifecycle_status IN ('ACTIVE','INACTIVE','ARCHIVED')),
    CONSTRAINT ck_crm_contacts_consent CHECK (consent_summary IN ('UNKNOWN','GRANTED','DENIED','WITHDRAWN'))
);
CREATE INDEX idx_crm_contacts_tenant_account ON crm_contacts (tenant_id, account_id, updated_at DESC);
CREATE INDEX idx_crm_contacts_name ON crm_contacts (tenant_id, normalized_name, id);
CREATE INDEX idx_crm_contacts_email ON crm_contacts (tenant_id, normalized_email);
CREATE INDEX idx_crm_contacts_owner ON crm_contacts (tenant_id, owner_user_id, lifecycle_status);

CREATE TABLE crm_pipelines (
    id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    name VARCHAR(160) NOT NULL,
    currency_code VARCHAR(3),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_crm_pipelines PRIMARY KEY (id),
    CONSTRAINT uk_crm_pipelines_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_crm_pipelines_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT uk_crm_pipelines_tenant_name UNIQUE (tenant_id, name),
    CONSTRAINT ck_crm_pipelines_currency CHECK (currency_code IS NULL OR CHAR_LENGTH(currency_code) = 3)
);

CREATE TABLE crm_pipeline_stages (
    id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    pipeline_id UUID NOT NULL,
    name VARCHAR(160) NOT NULL,
    sequence INTEGER NOT NULL,
    probability NUMERIC(5,2) NOT NULL DEFAULT 0,
    terminal_state VARCHAR(20),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT pk_crm_pipeline_stages PRIMARY KEY (id),
    CONSTRAINT uk_crm_pipeline_stages_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_crm_pipeline_stages_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_crm_pipeline_stages_pipeline_same_tenant FOREIGN KEY (tenant_id, pipeline_id) REFERENCES crm_pipelines (tenant_id, id),
    CONSTRAINT uk_crm_pipeline_stages_sequence UNIQUE (tenant_id, pipeline_id, sequence),
    CONSTRAINT uk_crm_pipeline_stages_name UNIQUE (tenant_id, pipeline_id, name),
    CONSTRAINT ck_crm_pipeline_stage_sequence CHECK (sequence >= 0),
    CONSTRAINT ck_crm_pipeline_stage_probability CHECK (probability BETWEEN 0 AND 100),
    CONSTRAINT ck_crm_pipeline_stage_terminal CHECK (terminal_state IS NULL OR terminal_state IN ('WON','LOST'))
);

CREATE TABLE crm_leads (
    id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    display_name VARCHAR(240) NOT NULL,
    normalized_name VARCHAR(240) NOT NULL,
    company_name VARCHAR(240),
    email VARCHAR(255),
    normalized_email VARCHAR(255),
    phone VARCHAR(64),
    source VARCHAR(120),
    status VARCHAR(32) NOT NULL DEFAULT 'NEW',
    owner_user_id UUID,
    queue_id UUID,
    score NUMERIC(8,3),
    converted_account_id UUID,
    converted_contact_id UUID,
    converted_opportunity_id UUID,
    created_by UUID NOT NULL,
    updated_by UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_crm_leads PRIMARY KEY (id),
    CONSTRAINT uk_crm_leads_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_crm_leads_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_crm_leads_converted_account_same_tenant FOREIGN KEY (tenant_id, converted_account_id) REFERENCES crm_accounts (tenant_id, id),
    CONSTRAINT fk_crm_leads_converted_contact_same_tenant FOREIGN KEY (tenant_id, converted_contact_id) REFERENCES crm_contacts (tenant_id, id),
    CONSTRAINT ck_crm_leads_status CHECK (status IN ('NEW','ASSIGNED','CONTACTED','QUALIFIED','DISQUALIFIED','CONVERTED','ARCHIVED'))
);
CREATE INDEX idx_crm_leads_tenant_status_owner ON crm_leads (tenant_id, status, owner_user_id, updated_at DESC);
CREATE INDEX idx_crm_leads_email ON crm_leads (tenant_id, normalized_email);

CREATE TABLE crm_opportunities (
    id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    account_id UUID,
    contact_id UUID,
    pipeline_id UUID NOT NULL,
    stage_id UUID NOT NULL,
    name VARCHAR(240) NOT NULL,
    amount NUMERIC(24,6),
    currency_code VARCHAR(3) NOT NULL,
    probability NUMERIC(5,2) NOT NULL DEFAULT 0,
    forecast_category VARCHAR(40),
    expected_close_date DATE,
    owner_user_id UUID,
    status VARCHAR(24) NOT NULL DEFAULT 'OPEN',
    win_loss_reason VARCHAR(500),
    created_by UUID NOT NULL,
    updated_by UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_crm_opportunities PRIMARY KEY (id),
    CONSTRAINT uk_crm_opportunities_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_crm_opportunities_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_crm_opportunities_account_same_tenant FOREIGN KEY (tenant_id, account_id) REFERENCES crm_accounts (tenant_id, id),
    CONSTRAINT fk_crm_opportunities_contact_same_tenant FOREIGN KEY (tenant_id, contact_id) REFERENCES crm_contacts (tenant_id, id),
    CONSTRAINT fk_crm_opportunities_pipeline_same_tenant FOREIGN KEY (tenant_id, pipeline_id) REFERENCES crm_pipelines (tenant_id, id),
    CONSTRAINT fk_crm_opportunities_stage_same_tenant FOREIGN KEY (tenant_id, stage_id) REFERENCES crm_pipeline_stages (tenant_id, id),
    CONSTRAINT ck_crm_opportunities_currency CHECK (CHAR_LENGTH(currency_code) = 3),
    CONSTRAINT ck_crm_opportunities_probability CHECK (probability BETWEEN 0 AND 100),
    CONSTRAINT ck_crm_opportunities_status CHECK (status IN ('OPEN','WON','LOST','CANCELLED','ARCHIVED'))
);
CREATE INDEX idx_crm_opportunities_pipeline ON crm_opportunities (tenant_id, pipeline_id, stage_id, status, updated_at DESC);
CREATE INDEX idx_crm_opportunities_owner ON crm_opportunities (tenant_id, owner_user_id, status, expected_close_date);

ALTER TABLE crm_leads ADD CONSTRAINT fk_crm_leads_converted_opportunity_same_tenant
    FOREIGN KEY (tenant_id, converted_opportunity_id) REFERENCES crm_opportunities (tenant_id, id);

CREATE TABLE crm_opportunity_stage_history (
    id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    opportunity_id UUID NOT NULL,
    from_stage_id UUID,
    to_stage_id UUID NOT NULL,
    changed_by UUID NOT NULL,
    changed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    reason VARCHAR(500),
    CONSTRAINT pk_crm_opportunity_stage_history PRIMARY KEY (id),
    CONSTRAINT fk_crm_stage_history_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_crm_stage_history_opportunity_same_tenant FOREIGN KEY (tenant_id, opportunity_id) REFERENCES crm_opportunities (tenant_id, id),
    CONSTRAINT fk_crm_stage_history_from_stage_same_tenant FOREIGN KEY (tenant_id, from_stage_id) REFERENCES crm_pipeline_stages (tenant_id, id),
    CONSTRAINT fk_crm_stage_history_to_stage_same_tenant FOREIGN KEY (tenant_id, to_stage_id) REFERENCES crm_pipeline_stages (tenant_id, id)
);
CREATE INDEX idx_crm_stage_history_timeline ON crm_opportunity_stage_history (tenant_id, opportunity_id, changed_at DESC);

CREATE TABLE crm_activities (
    id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    activity_type VARCHAR(32) NOT NULL,
    subject VARCHAR(240) NOT NULL,
    body TEXT,
    related_type VARCHAR(40),
    related_id UUID,
    owner_user_id UUID,
    status VARCHAR(24) NOT NULL DEFAULT 'OPEN',
    priority INTEGER NOT NULL DEFAULT 50,
    start_at TIMESTAMP WITH TIME ZONE,
    due_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    created_by UUID NOT NULL,
    updated_by UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_crm_activities PRIMARY KEY (id),
    CONSTRAINT uk_crm_activities_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_crm_activities_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT ck_crm_activities_type CHECK (activity_type IN ('TASK','CALL','MEETING','EMAIL','NOTE','MESSAGE','OTHER')),
    CONSTRAINT ck_crm_activities_status CHECK (status IN ('OPEN','IN_PROGRESS','COMPLETED','CANCELLED','ARCHIVED')),
    CONSTRAINT ck_crm_activities_priority CHECK (priority BETWEEN 0 AND 100)
);
CREATE INDEX idx_crm_activities_timeline ON crm_activities (tenant_id, related_type, related_id, created_at DESC);
CREATE INDEX idx_crm_activities_owner_due ON crm_activities (tenant_id, owner_user_id, status, due_at);

CREATE TABLE crm_timeline_events (
    id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    subject_type VARCHAR(40) NOT NULL,
    subject_id UUID NOT NULL,
    event_type VARCHAR(120) NOT NULL,
    summary VARCHAR(500) NOT NULL,
    source_type VARCHAR(80) NOT NULL,
    source_id UUID NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by UUID NOT NULL,
    CONSTRAINT pk_crm_timeline_events PRIMARY KEY (id),
    CONSTRAINT fk_crm_timeline_events_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id)
);
CREATE INDEX idx_crm_timeline_subject ON crm_timeline_events (tenant_id, subject_type, subject_id, occurred_at DESC);

CREATE TABLE crm_import_jobs (
    id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    entity_type VARCHAR(80) NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'UPLOADED',
    total_rows BIGINT NOT NULL DEFAULT 0,
    processed_rows BIGINT NOT NULL DEFAULT 0,
    succeeded_rows BIGINT NOT NULL DEFAULT 0,
    failed_rows BIGINT NOT NULL DEFAULT 0,
    requested_by UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_error TEXT,
    CONSTRAINT pk_crm_import_jobs PRIMARY KEY (id),
    CONSTRAINT fk_crm_import_jobs_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT ck_crm_import_jobs_status CHECK (status IN ('UPLOADED','VALIDATING','READY','RUNNING','COMPLETED','FAILED','CANCELLED')),
    CONSTRAINT ck_crm_import_jobs_counts CHECK (total_rows >= 0 AND processed_rows >= 0 AND succeeded_rows >= 0 AND failed_rows >= 0)
);

CREATE TABLE crm_custom_field_definitions (
    id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    entity_type VARCHAR(80) NOT NULL,
    field_key VARCHAR(120) NOT NULL,
    label_ar VARCHAR(240) NOT NULL,
    label_en VARCHAR(240) NOT NULL,
    data_type VARCHAR(32) NOT NULL,
    sensitive BOOLEAN NOT NULL DEFAULT FALSE,
    searchable BOOLEAN NOT NULL DEFAULT FALSE,
    required BOOLEAN NOT NULL DEFAULT FALSE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_crm_custom_field_definitions PRIMARY KEY (id),
    CONSTRAINT fk_crm_custom_fields_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT uk_crm_custom_fields_tenant_entity_key UNIQUE (tenant_id, entity_type, field_key)
);

INSERT INTO access_capabilities (id, code, name, description, status, created_at, updated_at)
SELECT gen_random_uuid(), capability.code, capability.name, capability.description, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM (
    VALUES
        ('CRM.ACCOUNT.READ', 'Read CRM Accounts', 'View tenant CRM accounts'),
        ('CRM.ACCOUNT.WRITE', 'Write CRM Accounts', 'Create and update tenant CRM accounts'),
        ('CRM.ACCOUNT.ARCHIVE', 'Archive CRM Accounts', 'Archive tenant CRM accounts'),
        ('CRM.CONTACT.READ', 'Read CRM Contacts', 'View tenant CRM contacts'),
        ('CRM.CONTACT.WRITE', 'Write CRM Contacts', 'Create and update tenant CRM contacts'),
        ('CRM.CONTACT.ARCHIVE', 'Archive CRM Contacts', 'Archive tenant CRM contacts'),
        ('CRM.LEAD.READ', 'Read CRM Leads', 'View tenant CRM leads'),
        ('CRM.LEAD.WRITE', 'Write CRM Leads', 'Create and update tenant CRM leads'),
        ('CRM.LEAD.CONVERT', 'Convert CRM Leads', 'Convert tenant CRM leads'),
        ('CRM.OPPORTUNITY.READ', 'Read CRM Opportunities', 'View tenant CRM opportunities'),
        ('CRM.OPPORTUNITY.WRITE', 'Write CRM Opportunities', 'Create and update tenant CRM opportunities'),
        ('CRM.ACTIVITY.READ', 'Read CRM Activities', 'View tenant CRM activities'),
        ('CRM.ACTIVITY.WRITE', 'Write CRM Activities', 'Create and update tenant CRM activities'),
        ('CRM.ADMIN', 'Administer CRM', 'Administer tenant CRM configuration')
) AS capability(code, name, description)
WHERE NOT EXISTS (SELECT 1 FROM access_capabilities existing WHERE existing.code = capability.code);

INSERT INTO role_capabilities (id, tenant_id, role_id, capability_id, created_at)
SELECT gen_random_uuid(), role.tenant_id, role.id, capability.id, CURRENT_TIMESTAMP
FROM roles role
JOIN access_capabilities capability ON capability.code LIKE 'CRM.%' AND capability.status = 'ACTIVE'
WHERE role.code = 'ADMIN'
  AND role.status = 'ACTIVE'
  AND NOT EXISTS (
      SELECT 1 FROM role_capabilities existing
      WHERE existing.tenant_id = role.tenant_id
        AND existing.role_id = role.id
        AND existing.capability_id = capability.id
  );
