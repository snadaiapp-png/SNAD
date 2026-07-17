-- ============================================================
-- SNAD Platform — Complete CRM G1 extension tables
-- ------------------------------------------------------------
-- Gate: CRM-G1 / EXEC-PROMPT-CRM-008
--
-- Existing forward-only migrations already create:
--   - crm_tasks  (V20260716.1)
--   - crm_notes  (V20260716.2)
--
-- This migration creates the remaining six G1 extension tables so the
-- cumulative G1 contract is exactly eight tables with 26 explicit
-- tenant-scoped business indexes.
--
-- Portable SQL: PostgreSQL 16 and H2 in PostgreSQL mode.
-- ============================================================

CREATE TABLE IF NOT EXISTS crm_assignments (
    id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,

    account_id UUID,
    contact_id UUID,
    lead_id UUID,
    opportunity_id UUID,
    task_id UUID,

    assignee_user_id UUID NOT NULL,
    assigned_by_user_id UUID NOT NULL,
    assignment_role VARCHAR(40) NOT NULL DEFAULT 'OWNER',
    status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
    starts_at TIMESTAMP WITH TIME ZONE,
    ends_at TIMESTAMP WITH TIME ZONE,

    created_by UUID NOT NULL,
    updated_by UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT pk_crm_assignments PRIMARY KEY (id),
    CONSTRAINT uk_crm_assignments_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_crm_assignments_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_crm_assignments_account_same_tenant
        FOREIGN KEY (tenant_id, account_id) REFERENCES crm_accounts (tenant_id, id),
    CONSTRAINT fk_crm_assignments_contact_same_tenant
        FOREIGN KEY (tenant_id, contact_id) REFERENCES crm_contacts (tenant_id, id),
    CONSTRAINT fk_crm_assignments_lead_same_tenant
        FOREIGN KEY (tenant_id, lead_id) REFERENCES crm_leads (tenant_id, id),
    CONSTRAINT fk_crm_assignments_opportunity_same_tenant
        FOREIGN KEY (tenant_id, opportunity_id) REFERENCES crm_opportunities (tenant_id, id),
    CONSTRAINT fk_crm_assignments_task_same_tenant
        FOREIGN KEY (tenant_id, task_id) REFERENCES crm_tasks (tenant_id, id),
    CONSTRAINT fk_crm_assignments_assignee_same_tenant
        FOREIGN KEY (tenant_id, assignee_user_id) REFERENCES users (tenant_id, id),
    CONSTRAINT fk_crm_assignments_assigner_same_tenant
        FOREIGN KEY (tenant_id, assigned_by_user_id) REFERENCES users (tenant_id, id),
    CONSTRAINT ck_crm_assignments_one_subject CHECK (
        (CASE WHEN account_id IS NULL THEN 0 ELSE 1 END) +
        (CASE WHEN contact_id IS NULL THEN 0 ELSE 1 END) +
        (CASE WHEN lead_id IS NULL THEN 0 ELSE 1 END) +
        (CASE WHEN opportunity_id IS NULL THEN 0 ELSE 1 END) +
        (CASE WHEN task_id IS NULL THEN 0 ELSE 1 END) = 1
    ),
    CONSTRAINT ck_crm_assignments_role CHECK (
        assignment_role IN ('OWNER','COLLABORATOR','REVIEWER','FOLLOWER')
    ),
    CONSTRAINT ck_crm_assignments_status CHECK (
        status IN ('ACTIVE','SUSPENDED','COMPLETED','CANCELLED')
    ),
    CONSTRAINT ck_crm_assignments_dates CHECK (
        ends_at IS NULL OR starts_at IS NULL OR ends_at >= starts_at
    )
);

CREATE INDEX IF NOT EXISTS idx_crm_assignments_subject
    ON crm_assignments (
        tenant_id, account_id, contact_id, lead_id, opportunity_id, task_id, status
    );
CREATE INDEX IF NOT EXISTS idx_crm_assignments_assignee
    ON crm_assignments (tenant_id, assignee_user_id, status, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_crm_assignments_active_window
    ON crm_assignments (tenant_id, status, starts_at, ends_at);

CREATE TABLE IF NOT EXISTS crm_transfers (
    id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,

    account_id UUID,
    contact_id UUID,
    lead_id UUID,
    opportunity_id UUID,
    task_id UUID,

    from_user_id UUID,
    to_user_id UUID NOT NULL,
    requested_by_user_id UUID NOT NULL,
    decided_by_user_id UUID,
    reason VARCHAR(1000),
    decision_note VARCHAR(1000),
    status VARCHAR(24) NOT NULL DEFAULT 'REQUESTED',
    requested_at TIMESTAMP WITH TIME ZONE NOT NULL,
    decided_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,

    created_by UUID NOT NULL,
    updated_by UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT pk_crm_transfers PRIMARY KEY (id),
    CONSTRAINT uk_crm_transfers_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_crm_transfers_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_crm_transfers_account_same_tenant
        FOREIGN KEY (tenant_id, account_id) REFERENCES crm_accounts (tenant_id, id),
    CONSTRAINT fk_crm_transfers_contact_same_tenant
        FOREIGN KEY (tenant_id, contact_id) REFERENCES crm_contacts (tenant_id, id),
    CONSTRAINT fk_crm_transfers_lead_same_tenant
        FOREIGN KEY (tenant_id, lead_id) REFERENCES crm_leads (tenant_id, id),
    CONSTRAINT fk_crm_transfers_opportunity_same_tenant
        FOREIGN KEY (tenant_id, opportunity_id) REFERENCES crm_opportunities (tenant_id, id),
    CONSTRAINT fk_crm_transfers_task_same_tenant
        FOREIGN KEY (tenant_id, task_id) REFERENCES crm_tasks (tenant_id, id),
    CONSTRAINT fk_crm_transfers_from_user_same_tenant
        FOREIGN KEY (tenant_id, from_user_id) REFERENCES users (tenant_id, id),
    CONSTRAINT fk_crm_transfers_to_user_same_tenant
        FOREIGN KEY (tenant_id, to_user_id) REFERENCES users (tenant_id, id),
    CONSTRAINT fk_crm_transfers_requester_same_tenant
        FOREIGN KEY (tenant_id, requested_by_user_id) REFERENCES users (tenant_id, id),
    CONSTRAINT fk_crm_transfers_decider_same_tenant
        FOREIGN KEY (tenant_id, decided_by_user_id) REFERENCES users (tenant_id, id),
    CONSTRAINT ck_crm_transfers_one_subject CHECK (
        (CASE WHEN account_id IS NULL THEN 0 ELSE 1 END) +
        (CASE WHEN contact_id IS NULL THEN 0 ELSE 1 END) +
        (CASE WHEN lead_id IS NULL THEN 0 ELSE 1 END) +
        (CASE WHEN opportunity_id IS NULL THEN 0 ELSE 1 END) +
        (CASE WHEN task_id IS NULL THEN 0 ELSE 1 END) = 1
    ),
    CONSTRAINT ck_crm_transfers_users_distinct CHECK (
        from_user_id IS NULL OR from_user_id <> to_user_id
    ),
    CONSTRAINT ck_crm_transfers_status CHECK (
        status IN ('REQUESTED','APPROVED','REJECTED','CANCELLED','COMPLETED')
    ),
    CONSTRAINT ck_crm_transfers_decision_time CHECK (
        decided_at IS NULL OR decided_at >= requested_at
    ),
    CONSTRAINT ck_crm_transfers_completion_time CHECK (
        completed_at IS NULL OR completed_at >= requested_at
    )
);

CREATE INDEX IF NOT EXISTS idx_crm_transfers_subject
    ON crm_transfers (
        tenant_id, account_id, contact_id, lead_id, opportunity_id, task_id, status
    );
CREATE INDEX IF NOT EXISTS idx_crm_transfers_recipient_status
    ON crm_transfers (tenant_id, to_user_id, status, requested_at DESC);
CREATE INDEX IF NOT EXISTS idx_crm_transfers_requested_at
    ON crm_transfers (tenant_id, requested_at DESC, status);

CREATE TABLE IF NOT EXISTS crm_audit_logs (
    id UUID NOT NULL,
    tenant_id UUID NOT NULL,

    aggregate_type VARCHAR(80) NOT NULL,
    aggregate_id UUID NOT NULL,
    action VARCHAR(80) NOT NULL,
    actor_user_id UUID,
    request_id VARCHAR(120),
    correlation_id VARCHAR(120),
    source VARCHAR(80) NOT NULL DEFAULT 'CRM',
    before_json TEXT,
    after_json TEXT,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT pk_crm_audit_logs PRIMARY KEY (id),
    CONSTRAINT uk_crm_audit_logs_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_crm_audit_logs_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_crm_audit_logs_actor_same_tenant
        FOREIGN KEY (tenant_id, actor_user_id) REFERENCES users (tenant_id, id),
    CONSTRAINT ck_crm_audit_logs_action_not_empty CHECK (LENGTH(TRIM(action)) > 0),
    CONSTRAINT ck_crm_audit_logs_aggregate_type_not_empty CHECK (
        LENGTH(TRIM(aggregate_type)) > 0
    )
);

CREATE INDEX IF NOT EXISTS idx_crm_audit_logs_aggregate
    ON crm_audit_logs (tenant_id, aggregate_type, aggregate_id, occurred_at DESC);
CREATE INDEX IF NOT EXISTS idx_crm_audit_logs_actor
    ON crm_audit_logs (tenant_id, actor_user_id, occurred_at DESC);
CREATE INDEX IF NOT EXISTS idx_crm_audit_logs_correlation
    ON crm_audit_logs (tenant_id, correlation_id, occurred_at DESC);
CREATE INDEX IF NOT EXISTS idx_crm_audit_logs_occurred
    ON crm_audit_logs (tenant_id, occurred_at DESC, action);

CREATE TABLE IF NOT EXISTS crm_reports (
    id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,

    code VARCHAR(120) NOT NULL,
    name VARCHAR(240) NOT NULL,
    description VARCHAR(1000),
    report_type VARCHAR(40) NOT NULL,
    definition_json TEXT NOT NULL,
    visibility VARCHAR(24) NOT NULL DEFAULT 'PRIVATE',
    owner_user_id UUID NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
    schedule_cron VARCHAR(160),
    last_run_at TIMESTAMP WITH TIME ZONE,

    created_by UUID NOT NULL,
    updated_by UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT pk_crm_reports PRIMARY KEY (id),
    CONSTRAINT uk_crm_reports_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uk_crm_reports_tenant_code UNIQUE (tenant_id, code),
    CONSTRAINT fk_crm_reports_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_crm_reports_owner_same_tenant
        FOREIGN KEY (tenant_id, owner_user_id) REFERENCES users (tenant_id, id),
    CONSTRAINT ck_crm_reports_type CHECK (
        report_type IN ('TABULAR','SUMMARY','MATRIX','DASHBOARD','EXPORT')
    ),
    CONSTRAINT ck_crm_reports_visibility CHECK (
        visibility IN ('PRIVATE','TEAM','TENANT')
    ),
    CONSTRAINT ck_crm_reports_status CHECK (
        status IN ('DRAFT','ACTIVE','PAUSED','ARCHIVED')
    ),
    CONSTRAINT ck_crm_reports_definition_not_empty CHECK (
        LENGTH(TRIM(definition_json)) > 0
    )
);

CREATE INDEX IF NOT EXISTS idx_crm_reports_status_type
    ON crm_reports (tenant_id, status, report_type, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_crm_reports_owner
    ON crm_reports (tenant_id, owner_user_id, status, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_crm_reports_last_run
    ON crm_reports (tenant_id, last_run_at DESC, status);

CREATE TABLE IF NOT EXISTS crm_phone_numbers (
    id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,

    account_id UUID,
    contact_id UUID,
    lead_id UUID,

    raw_number VARCHAR(80) NOT NULL,
    normalized_e164 VARCHAR(32) NOT NULL,
    country_code VARCHAR(2),
    extension VARCHAR(16),
    label VARCHAR(80),
    primary_number BOOLEAN NOT NULL DEFAULT FALSE,
    verified_at TIMESTAMP WITH TIME ZONE,
    archived BOOLEAN NOT NULL DEFAULT FALSE,

    created_by UUID NOT NULL,
    updated_by UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT pk_crm_phone_numbers PRIMARY KEY (id),
    CONSTRAINT uk_crm_phone_numbers_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_crm_phone_numbers_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_crm_phone_numbers_account_same_tenant
        FOREIGN KEY (tenant_id, account_id) REFERENCES crm_accounts (tenant_id, id),
    CONSTRAINT fk_crm_phone_numbers_contact_same_tenant
        FOREIGN KEY (tenant_id, contact_id) REFERENCES crm_contacts (tenant_id, id),
    CONSTRAINT fk_crm_phone_numbers_lead_same_tenant
        FOREIGN KEY (tenant_id, lead_id) REFERENCES crm_leads (tenant_id, id),
    CONSTRAINT ck_crm_phone_numbers_one_subject CHECK (
        (CASE WHEN account_id IS NULL THEN 0 ELSE 1 END) +
        (CASE WHEN contact_id IS NULL THEN 0 ELSE 1 END) +
        (CASE WHEN lead_id IS NULL THEN 0 ELSE 1 END) = 1
    ),
    CONSTRAINT ck_crm_phone_numbers_e164_not_empty CHECK (
        LENGTH(TRIM(normalized_e164)) > 0
    ),
    CONSTRAINT ck_crm_phone_numbers_country CHECK (
        country_code IS NULL OR CHAR_LENGTH(country_code) = 2
    )
);

CREATE INDEX IF NOT EXISTS idx_crm_phone_numbers_account
    ON crm_phone_numbers (tenant_id, account_id, archived, primary_number);
CREATE INDEX IF NOT EXISTS idx_crm_phone_numbers_contact
    ON crm_phone_numbers (tenant_id, contact_id, archived, primary_number);
CREATE INDEX IF NOT EXISTS idx_crm_phone_numbers_lead
    ON crm_phone_numbers (tenant_id, lead_id, archived, primary_number);
CREATE INDEX IF NOT EXISTS idx_crm_phone_numbers_normalized
    ON crm_phone_numbers (tenant_id, normalized_e164, archived);

CREATE TABLE IF NOT EXISTS crm_contact_lookup_index (
    id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    contact_id UUID NOT NULL,
    account_id UUID,

    normalized_name VARCHAR(240) NOT NULL,
    normalized_email VARCHAR(255),
    normalized_phone VARCHAR(32),
    search_text TEXT NOT NULL,
    source_version BIGINT NOT NULL DEFAULT 0,
    last_indexed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT pk_crm_contact_lookup_index PRIMARY KEY (id),
    CONSTRAINT uk_crm_contact_lookup_index_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uk_crm_contact_lookup_index_contact UNIQUE (tenant_id, contact_id),
    CONSTRAINT fk_crm_contact_lookup_index_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_crm_contact_lookup_contact_same_tenant
        FOREIGN KEY (tenant_id, contact_id) REFERENCES crm_contacts (tenant_id, id)
        ON DELETE CASCADE,
    CONSTRAINT fk_crm_contact_lookup_account_same_tenant
        FOREIGN KEY (tenant_id, account_id) REFERENCES crm_accounts (tenant_id, id),
    CONSTRAINT ck_crm_contact_lookup_name_not_empty CHECK (
        LENGTH(TRIM(normalized_name)) > 0
    ),
    CONSTRAINT ck_crm_contact_lookup_search_not_empty CHECK (
        LENGTH(TRIM(search_text)) > 0
    )
);

CREATE INDEX IF NOT EXISTS idx_crm_contact_lookup_name
    ON crm_contact_lookup_index (tenant_id, normalized_name, contact_id);
CREATE INDEX IF NOT EXISTS idx_crm_contact_lookup_email
    ON crm_contact_lookup_index (tenant_id, normalized_email, contact_id);
CREATE INDEX IF NOT EXISTS idx_crm_contact_lookup_phone
    ON crm_contact_lookup_index (tenant_id, normalized_phone, contact_id);
