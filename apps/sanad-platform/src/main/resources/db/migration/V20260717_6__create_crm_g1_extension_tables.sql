-- ============================================================
-- SNAD Platform — Complete CRM G1 extension tables
-- ------------------------------------------------------------
-- Gate: CRM-G1 — Database and multi-tenant foundation
--
-- Completes the eight-table G1 extension set. crm_tasks and
-- crm_notes already exist in V20260716_1 and V20260716_2; this
-- migration adds the remaining six tenant-owned tables and 20
-- explicit tenant-scoped indexes. Together the G1 set contains
-- exactly 8 tables and 26 explicit performance indexes.
--
-- Tenant isolation is enforced through tenant_id on every table,
-- a tenant foreign key on every table, composite same-tenant
-- contact foreign keys where the relation is concrete, and the
-- mandatory application-layer tenant predicate for polymorphic
-- CRM subjects. PostgreSQL RLS remains a later defense-in-depth gate.
-- ============================================================

CREATE TABLE IF NOT EXISTS crm_assignments (
    id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,

    subject_type VARCHAR(40) NOT NULL,
    subject_id UUID NOT NULL,
    assigned_user_id UUID NOT NULL,
    assignment_role VARCHAR(80) NOT NULL DEFAULT 'OWNER',
    status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
    starts_at TIMESTAMP WITH TIME ZONE NOT NULL,
    ends_at TIMESTAMP WITH TIME ZONE,
    reason TEXT,

    created_by UUID NOT NULL,
    updated_by UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT pk_crm_assignments PRIMARY KEY (id),
    CONSTRAINT uk_crm_assignments_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_crm_assignments_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT ck_crm_assignments_subject_type CHECK (
        subject_type IN ('ACCOUNT','CONTACT','LEAD','OPPORTUNITY','ACTIVITY','TASK')
    ),
    CONSTRAINT ck_crm_assignments_status CHECK (status IN ('ACTIVE','ENDED','CANCELLED')),
    CONSTRAINT ck_crm_assignments_dates CHECK (ends_at IS NULL OR ends_at >= starts_at)
);

CREATE INDEX IF NOT EXISTS idx_crm_assignments_subject_active
    ON crm_assignments (tenant_id, subject_type, subject_id, status, starts_at DESC);
CREATE INDEX IF NOT EXISTS idx_crm_assignments_user_active
    ON crm_assignments (tenant_id, assigned_user_id, status, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_crm_assignments_role_status
    ON crm_assignments (tenant_id, assignment_role, status, updated_at DESC);

CREATE TABLE IF NOT EXISTS crm_transfers (
    id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,

    subject_type VARCHAR(40) NOT NULL,
    subject_id UUID NOT NULL,
    from_user_id UUID,
    to_user_id UUID NOT NULL,
    transfer_type VARCHAR(24) NOT NULL DEFAULT 'OWNERSHIP',
    status VARCHAR(24) NOT NULL DEFAULT 'REQUESTED',
    requested_by UUID NOT NULL,
    approved_by UUID,
    requested_at TIMESTAMP WITH TIME ZONE NOT NULL,
    decided_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    reason TEXT,
    decision_notes TEXT,

    created_by UUID NOT NULL,
    updated_by UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT pk_crm_transfers PRIMARY KEY (id),
    CONSTRAINT uk_crm_transfers_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_crm_transfers_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT ck_crm_transfers_subject_type CHECK (
        subject_type IN ('ACCOUNT','CONTACT','LEAD','OPPORTUNITY','ACTIVITY','TASK')
    ),
    CONSTRAINT ck_crm_transfers_type CHECK (transfer_type IN ('OWNERSHIP','ASSIGNMENT','QUEUE')),
    CONSTRAINT ck_crm_transfers_status CHECK (
        status IN ('REQUESTED','APPROVED','REJECTED','COMPLETED','CANCELLED')
    ),
    CONSTRAINT ck_crm_transfers_distinct_users CHECK (
        from_user_id IS NULL OR from_user_id <> to_user_id
    ),
    CONSTRAINT ck_crm_transfers_decision_time CHECK (
        decided_at IS NULL OR decided_at >= requested_at
    ),
    CONSTRAINT ck_crm_transfers_completion_time CHECK (
        completed_at IS NULL OR completed_at >= requested_at
    )
);

CREATE INDEX IF NOT EXISTS idx_crm_transfers_subject_status
    ON crm_transfers (tenant_id, subject_type, subject_id, status, requested_at DESC);
CREATE INDEX IF NOT EXISTS idx_crm_transfers_recipient_status
    ON crm_transfers (tenant_id, to_user_id, status, requested_at DESC);
CREATE INDEX IF NOT EXISTS idx_crm_transfers_requested_at
    ON crm_transfers (tenant_id, requested_at DESC, status);

CREATE TABLE IF NOT EXISTS crm_audit_logs (
    id UUID NOT NULL,
    tenant_id UUID NOT NULL,

    entity_type VARCHAR(40) NOT NULL,
    entity_id UUID,
    action_code VARCHAR(80) NOT NULL,
    actor_user_id UUID,
    correlation_id VARCHAR(120),
    source_ip VARCHAR(64),
    user_agent VARCHAR(500),
    before_json TEXT,
    after_json TEXT,
    metadata_json TEXT,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT pk_crm_audit_logs PRIMARY KEY (id),
    CONSTRAINT uk_crm_audit_logs_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_crm_audit_logs_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT ck_crm_audit_logs_entity_type CHECK (
        entity_type IN ('ACCOUNT','CONTACT','LEAD','OPPORTUNITY','PIPELINE','ACTIVITY','TASK',
                        'ASSIGNMENT','TRANSFER','NOTE','REPORT','PHONE_NUMBER','IMPORT','CUSTOM_FIELD')
    ),
    CONSTRAINT ck_crm_audit_logs_action_not_empty CHECK (LENGTH(TRIM(action_code)) > 0)
);

CREATE INDEX IF NOT EXISTS idx_crm_audit_logs_entity_time
    ON crm_audit_logs (tenant_id, entity_type, entity_id, occurred_at DESC);
CREATE INDEX IF NOT EXISTS idx_crm_audit_logs_actor_time
    ON crm_audit_logs (tenant_id, actor_user_id, occurred_at DESC);
CREATE INDEX IF NOT EXISTS idx_crm_audit_logs_correlation
    ON crm_audit_logs (tenant_id, correlation_id, occurred_at DESC);
CREATE INDEX IF NOT EXISTS idx_crm_audit_logs_action_time
    ON crm_audit_logs (tenant_id, action_code, occurred_at DESC);

CREATE TABLE IF NOT EXISTS crm_reports (
    id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,

    name VARCHAR(240) NOT NULL,
    description TEXT,
    report_type VARCHAR(60) NOT NULL,
    definition_json TEXT NOT NULL,
    parameters_json TEXT,
    visibility VARCHAR(24) NOT NULL DEFAULT 'PRIVATE',
    owner_user_id UUID NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
    schedule_expression VARCHAR(160),
    last_run_at TIMESTAMP WITH TIME ZONE,

    created_by UUID NOT NULL,
    updated_by UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT pk_crm_reports PRIMARY KEY (id),
    CONSTRAINT uk_crm_reports_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_crm_reports_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT ck_crm_reports_visibility CHECK (visibility IN ('PRIVATE','TEAM','TENANT')),
    CONSTRAINT ck_crm_reports_status CHECK (status IN ('DRAFT','ACTIVE','ARCHIVED')),
    CONSTRAINT ck_crm_reports_name_not_empty CHECK (LENGTH(TRIM(name)) > 0),
    CONSTRAINT ck_crm_reports_definition_not_empty CHECK (LENGTH(TRIM(definition_json)) > 0)
);

CREATE INDEX IF NOT EXISTS idx_crm_reports_owner_status
    ON crm_reports (tenant_id, owner_user_id, status, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_crm_reports_type_status
    ON crm_reports (tenant_id, report_type, status, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_crm_reports_last_run
    ON crm_reports (tenant_id, last_run_at DESC, status);

CREATE TABLE IF NOT EXISTS crm_phone_numbers (
    id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    contact_id UUID NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,

    phone_type VARCHAR(24) NOT NULL DEFAULT 'MOBILE',
    country_code VARCHAR(8),
    national_number VARCHAR(40),
    e164 VARCHAR(24) NOT NULL,
    extension VARCHAR(12),
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    verified_at TIMESTAMP WITH TIME ZONE,
    archived BOOLEAN NOT NULL DEFAULT FALSE,

    created_by UUID NOT NULL,
    updated_by UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT pk_crm_phone_numbers PRIMARY KEY (id),
    CONSTRAINT uk_crm_phone_numbers_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_crm_phone_numbers_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_crm_phone_numbers_contact_same_tenant
        FOREIGN KEY (tenant_id, contact_id) REFERENCES crm_contacts (tenant_id, id),
    CONSTRAINT ck_crm_phone_numbers_type CHECK (phone_type IN ('MOBILE','WORK','HOME','FAX','OTHER')),
    CONSTRAINT ck_crm_phone_numbers_e164 CHECK (LENGTH(TRIM(e164)) >= 4 AND e164 LIKE '+%')
);

CREATE INDEX IF NOT EXISTS idx_crm_phone_numbers_contact
    ON crm_phone_numbers (tenant_id, contact_id, archived, is_primary DESC);
CREATE INDEX IF NOT EXISTS idx_crm_phone_numbers_e164
    ON crm_phone_numbers (tenant_id, e164, archived);
CREATE INDEX IF NOT EXISTS idx_crm_phone_numbers_primary
    ON crm_phone_numbers (tenant_id, is_primary, archived, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_crm_phone_numbers_verified
    ON crm_phone_numbers (tenant_id, verified_at DESC, archived);

CREATE TABLE IF NOT EXISTS crm_contact_lookup_index (
    id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    contact_id UUID NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,

    normalized_phone VARCHAR(40),
    normalized_email VARCHAR(320),
    normalized_name VARCHAR(320) NOT NULL,
    searchable_text TEXT,
    source_updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,

    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT pk_crm_contact_lookup_index PRIMARY KEY (id),
    CONSTRAINT uk_crm_contact_lookup_tenant_contact UNIQUE (tenant_id, contact_id),
    CONSTRAINT fk_crm_contact_lookup_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_crm_contact_lookup_contact_same_tenant
        FOREIGN KEY (tenant_id, contact_id) REFERENCES crm_contacts (tenant_id, id),
    CONSTRAINT ck_crm_contact_lookup_identifier CHECK (
        normalized_phone IS NOT NULL OR normalized_email IS NOT NULL OR LENGTH(TRIM(normalized_name)) > 0
    )
);

CREATE INDEX IF NOT EXISTS idx_crm_contact_lookup_phone
    ON crm_contact_lookup_index (tenant_id, normalized_phone, active);
CREATE INDEX IF NOT EXISTS idx_crm_contact_lookup_email
    ON crm_contact_lookup_index (tenant_id, normalized_email, active);
CREATE INDEX IF NOT EXISTS idx_crm_contact_lookup_name
    ON crm_contact_lookup_index (tenant_id, normalized_name, active);
