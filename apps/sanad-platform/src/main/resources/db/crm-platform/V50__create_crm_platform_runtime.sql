CREATE SCHEMA IF NOT EXISTS crm_platform;

CREATE TABLE IF NOT EXISTS crm_platform.event_outbox (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(160) NOT NULL,
    event_version INTEGER NOT NULL DEFAULT 1 CHECK (event_version > 0),
    routing_key VARCHAR(200) NOT NULL,
    payload JSONB NOT NULL,
    headers JSONB NOT NULL DEFAULT '{}'::jsonb,
    correlation_id UUID,
    causation_id UUID,
    status VARCHAR(24) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING','PROCESSING','PUBLISHED','FAILED','DEAD')),
    attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    available_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at TIMESTAMPTZ,
    last_error TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_crm_event_outbox_dispatch
    ON crm_platform.event_outbox (status, available_at, created_at);
CREATE INDEX IF NOT EXISTS idx_crm_event_outbox_tenant_aggregate
    ON crm_platform.event_outbox (tenant_id, aggregate_type, aggregate_id, created_at DESC);

CREATE TABLE IF NOT EXISTS crm_platform.event_dead_letter (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    source_event_id UUID,
    routing_key VARCHAR(200) NOT NULL,
    payload JSONB NOT NULL,
    headers JSONB NOT NULL DEFAULT '{}'::jsonb,
    failure_code VARCHAR(100) NOT NULL,
    failure_message TEXT,
    failed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    replayed_at TIMESTAMPTZ,
    replayed_by UUID
);
CREATE INDEX IF NOT EXISTS idx_crm_dead_letter_tenant_failed
    ON crm_platform.event_dead_letter (tenant_id, failed_at DESC);

CREATE TABLE IF NOT EXISTS crm_platform.workflow_definition (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    workflow_key VARCHAR(160) NOT NULL,
    version INTEGER NOT NULL CHECK (version > 0),
    name VARCHAR(255) NOT NULL,
    definition JSONB NOT NULL,
    active BOOLEAN NOT NULL DEFAULT true,
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, workflow_key, version)
);

CREATE TABLE IF NOT EXISTS crm_platform.workflow_instance (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    definition_id UUID NOT NULL REFERENCES crm_platform.workflow_definition(id),
    business_key VARCHAR(255) NOT NULL,
    subject_type VARCHAR(100) NOT NULL,
    subject_id UUID NOT NULL,
    state VARCHAR(80) NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'RUNNING'
        CHECK (status IN ('RUNNING','WAITING','COMPLETED','FAILED','CANCELLED')),
    variables JSONB NOT NULL DEFAULT '{}'::jsonb,
    correlation_id UUID,
    started_by UUID NOT NULL,
    started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, business_key)
);
CREATE INDEX IF NOT EXISTS idx_crm_workflow_instance_subject
    ON crm_platform.workflow_instance (tenant_id, subject_type, subject_id, status);

CREATE TABLE IF NOT EXISTS crm_platform.workflow_task (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    workflow_instance_id UUID NOT NULL REFERENCES crm_platform.workflow_instance(id) ON DELETE CASCADE,
    task_key VARCHAR(160) NOT NULL,
    assignee_user_id UUID,
    assignee_queue_id UUID,
    status VARCHAR(24) NOT NULL DEFAULT 'OPEN'
        CHECK (status IN ('OPEN','CLAIMED','COMPLETED','CANCELLED','EXPIRED')),
    priority INTEGER NOT NULL DEFAULT 50 CHECK (priority BETWEEN 0 AND 100),
    due_at TIMESTAMPTZ,
    claimed_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    result JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_crm_workflow_task_queue
    ON crm_platform.workflow_task (tenant_id, status, due_at, priority DESC);

CREATE TABLE IF NOT EXISTS crm_platform.workflow_timer (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    workflow_instance_id UUID NOT NULL REFERENCES crm_platform.workflow_instance(id) ON DELETE CASCADE,
    timer_key VARCHAR(160) NOT NULL,
    fire_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED'
        CHECK (status IN ('SCHEDULED','CLAIMED','FIRED','CANCELLED','FAILED')),
    attempts INTEGER NOT NULL DEFAULT 0,
    payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (workflow_instance_id, timer_key)
);
CREATE INDEX IF NOT EXISTS idx_crm_workflow_timer_due
    ON crm_platform.workflow_timer (status, fire_at);

CREATE TABLE IF NOT EXISTS crm_platform.notification_template (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    template_key VARCHAR(160) NOT NULL,
    channel VARCHAR(20) NOT NULL CHECK (channel IN ('EMAIL','SMS','PUSH','WHATSAPP','IN_APP')),
    locale VARCHAR(20) NOT NULL,
    subject_template TEXT,
    body_template TEXT NOT NULL,
    active BOOLEAN NOT NULL DEFAULT true,
    version INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, template_key, channel, locale, version)
);

CREATE TABLE IF NOT EXISTS crm_platform.notification_message (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    template_key VARCHAR(160),
    channel VARCHAR(20) NOT NULL CHECK (channel IN ('EMAIL','SMS','PUSH','WHATSAPP','IN_APP')),
    recipient VARCHAR(512) NOT NULL,
    locale VARCHAR(20) NOT NULL DEFAULT 'ar-SA',
    variables JSONB NOT NULL DEFAULT '{}'::jsonb,
    subject TEXT,
    body TEXT,
    status VARCHAR(24) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING','PROCESSING','SENT','DELIVERED','FAILED','SUPPRESSED','DEAD')),
    idempotency_key VARCHAR(255) NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    available_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    provider_message_id VARCHAR(255),
    last_error TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    sent_at TIMESTAMPTZ,
    UNIQUE (tenant_id, idempotency_key)
);
CREATE INDEX IF NOT EXISTS idx_crm_notification_dispatch
    ON crm_platform.notification_message (status, available_at, created_at);

CREATE TABLE IF NOT EXISTS crm_platform.import_job (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    entity_type VARCHAR(80) NOT NULL,
    object_key VARCHAR(1024) NOT NULL,
    mapping JSONB NOT NULL DEFAULT '{}'::jsonb,
    status VARCHAR(24) NOT NULL DEFAULT 'UPLOADED'
        CHECK (status IN ('UPLOADED','SCANNING','VALIDATING','READY','RUNNING','COMPLETED','FAILED','CANCELLED')),
    total_rows BIGINT NOT NULL DEFAULT 0,
    processed_rows BIGINT NOT NULL DEFAULT 0,
    succeeded_rows BIGINT NOT NULL DEFAULT 0,
    failed_rows BIGINT NOT NULL DEFAULT 0,
    checkpoint JSONB NOT NULL DEFAULT '{}'::jsonb,
    requested_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    last_error TEXT
);
CREATE INDEX IF NOT EXISTS idx_crm_import_job_tenant_status
    ON crm_platform.import_job (tenant_id, status, created_at DESC);

CREATE TABLE IF NOT EXISTS crm_platform.export_job (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    entity_type VARCHAR(80) NOT NULL,
    filter JSONB NOT NULL DEFAULT '{}'::jsonb,
    fields JSONB NOT NULL DEFAULT '[]'::jsonb,
    status VARCHAR(24) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING','APPROVAL_REQUIRED','APPROVED','RUNNING','COMPLETED','FAILED','EXPIRED','CANCELLED')),
    object_key VARCHAR(1024),
    expires_at TIMESTAMPTZ,
    requested_by UUID NOT NULL,
    approved_by UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ,
    last_error TEXT
);
CREATE INDEX IF NOT EXISTS idx_crm_export_job_tenant_status
    ON crm_platform.export_job (tenant_id, status, created_at DESC);

CREATE TABLE IF NOT EXISTS crm_platform.webhook_subscription (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    endpoint_url VARCHAR(2048) NOT NULL,
    event_types JSONB NOT NULL,
    secret_reference VARCHAR(512) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT true,
    timeout_seconds INTEGER NOT NULL DEFAULT 10 CHECK (timeout_seconds BETWEEN 1 AND 60),
    max_attempts INTEGER NOT NULL DEFAULT 8 CHECK (max_attempts BETWEEN 1 AND 20),
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_crm_webhook_subscription_tenant_active
    ON crm_platform.webhook_subscription (tenant_id, active);

CREATE TABLE IF NOT EXISTS crm_platform.webhook_delivery (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    subscription_id UUID NOT NULL REFERENCES crm_platform.webhook_subscription(id),
    event_id UUID NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING','PROCESSING','DELIVERED','FAILED','DEAD')),
    attempts INTEGER NOT NULL DEFAULT 0,
    available_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    response_status INTEGER,
    response_body_hash VARCHAR(128),
    delivered_at TIMESTAMPTZ,
    last_error TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (subscription_id, event_id)
);
CREATE INDEX IF NOT EXISTS idx_crm_webhook_delivery_dispatch
    ON crm_platform.webhook_delivery (status, available_at, created_at);

CREATE TABLE IF NOT EXISTS crm_platform.consent_record (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    subject_type VARCHAR(40) NOT NULL,
    subject_id UUID NOT NULL,
    purpose VARCHAR(160) NOT NULL,
    channel VARCHAR(40),
    lawful_basis VARCHAR(80) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('GRANTED','DENIED','WITHDRAWN','EXPIRED')),
    evidence JSONB NOT NULL DEFAULT '{}'::jsonb,
    effective_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ,
    recorded_by UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_crm_consent_subject
    ON crm_platform.consent_record (tenant_id, subject_type, subject_id, purpose, effective_at DESC);

CREATE TABLE IF NOT EXISTS crm_platform.retention_policy (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    data_class VARCHAR(120) NOT NULL,
    jurisdiction VARCHAR(40) NOT NULL,
    retention_days INTEGER NOT NULL CHECK (retention_days > 0),
    action VARCHAR(24) NOT NULL CHECK (action IN ('ARCHIVE','ANONYMIZE','DELETE','REVIEW')),
    active BOOLEAN NOT NULL DEFAULT true,
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, data_class, jurisdiction)
);

CREATE TABLE IF NOT EXISTS crm_platform.privacy_request (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    request_type VARCHAR(24) NOT NULL CHECK (request_type IN ('ACCESS','CORRECT','RESTRICT','DELETE','EXPORT')),
    subject_type VARCHAR(40) NOT NULL,
    subject_id UUID NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'RECEIVED'
        CHECK (status IN ('RECEIVED','IDENTITY_PENDING','APPROVAL_PENDING','RUNNING','COMPLETED','REJECTED','FAILED')),
    due_at TIMESTAMPTZ NOT NULL,
    requested_by UUID,
    assigned_to UUID,
    result_object_key VARCHAR(1024),
    audit_data JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ,
    last_error TEXT
);
CREATE INDEX IF NOT EXISTS idx_crm_privacy_request_due
    ON crm_platform.privacy_request (tenant_id, status, due_at);

CREATE TABLE IF NOT EXISTS crm_platform.pipeline (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    currency_code CHAR(3),
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, name)
);

CREATE TABLE IF NOT EXISTS crm_platform.pipeline_stage (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    pipeline_id UUID NOT NULL REFERENCES crm_platform.pipeline(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    sequence INTEGER NOT NULL CHECK (sequence >= 0),
    probability NUMERIC(5,2) NOT NULL DEFAULT 0 CHECK (probability BETWEEN 0 AND 100),
    terminal_state VARCHAR(20) CHECK (terminal_state IN ('WON','LOST')),
    active BOOLEAN NOT NULL DEFAULT true,
    UNIQUE (pipeline_id, sequence),
    UNIQUE (pipeline_id, name)
);

CREATE TABLE IF NOT EXISTS crm_platform.lead (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    display_name VARCHAR(512) NOT NULL,
    normalized_name VARCHAR(512) NOT NULL,
    company_name VARCHAR(512),
    email VARCHAR(512),
    phone VARCHAR(100),
    source VARCHAR(160),
    status VARCHAR(32) NOT NULL DEFAULT 'NEW'
        CHECK (status IN ('NEW','ASSIGNED','CONTACTED','QUALIFIED','DISQUALIFIED','CONVERTED','ARCHIVED')),
    owner_user_id UUID,
    queue_id UUID,
    score NUMERIC(8,3),
    custom_data JSONB NOT NULL DEFAULT '{}'::jsonb,
    converted_account_id UUID,
    converted_contact_id UUID,
    converted_opportunity_id UUID,
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_crm_lead_tenant_status_owner
    ON crm_platform.lead (tenant_id, status, owner_user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_crm_lead_normalized_name
    ON crm_platform.lead (tenant_id, normalized_name);

CREATE TABLE IF NOT EXISTS crm_platform.opportunity (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    account_id UUID,
    pipeline_id UUID NOT NULL REFERENCES crm_platform.pipeline(id),
    stage_id UUID NOT NULL REFERENCES crm_platform.pipeline_stage(id),
    name VARCHAR(512) NOT NULL,
    amount NUMERIC(24,6),
    currency_code CHAR(3) NOT NULL,
    probability NUMERIC(5,2) NOT NULL DEFAULT 0 CHECK (probability BETWEEN 0 AND 100),
    forecast_category VARCHAR(40),
    expected_close_date DATE,
    owner_user_id UUID,
    status VARCHAR(24) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN','WON','LOST','CANCELLED','ARCHIVED')),
    win_loss_reason VARCHAR(500),
    custom_data JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_crm_opportunity_pipeline
    ON crm_platform.opportunity (tenant_id, pipeline_id, stage_id, status, expected_close_date);
CREATE INDEX IF NOT EXISTS idx_crm_opportunity_owner
    ON crm_platform.opportunity (tenant_id, owner_user_id, status, updated_at DESC);

CREATE TABLE IF NOT EXISTS crm_platform.activity (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    activity_type VARCHAR(32) NOT NULL CHECK (activity_type IN ('TASK','CALL','MEETING','EMAIL','NOTE','MESSAGE','OTHER')),
    subject VARCHAR(512) NOT NULL,
    body TEXT,
    related_type VARCHAR(80),
    related_id UUID,
    owner_user_id UUID,
    status VARCHAR(24) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN','IN_PROGRESS','COMPLETED','CANCELLED','ARCHIVED')),
    priority INTEGER NOT NULL DEFAULT 50 CHECK (priority BETWEEN 0 AND 100),
    start_at TIMESTAMPTZ,
    due_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_crm_activity_timeline
    ON crm_platform.activity (tenant_id, related_type, related_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_crm_activity_owner_due
    ON crm_platform.activity (tenant_id, owner_user_id, status, due_at);

CREATE TABLE IF NOT EXISTS crm_platform.custom_field_definition (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    entity_type VARCHAR(80) NOT NULL,
    field_key VARCHAR(120) NOT NULL,
    label JSONB NOT NULL,
    data_type VARCHAR(32) NOT NULL,
    validation JSONB NOT NULL DEFAULT '{}'::jsonb,
    sensitive BOOLEAN NOT NULL DEFAULT false,
    searchable BOOLEAN NOT NULL DEFAULT false,
    required BOOLEAN NOT NULL DEFAULT false,
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, entity_type, field_key)
);

CREATE TABLE IF NOT EXISTS crm_platform.integration_endpoint (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    integration_type VARCHAR(80) NOT NULL,
    endpoint_url VARCHAR(2048),
    credential_reference VARCHAR(512),
    configuration JSONB NOT NULL DEFAULT '{}'::jsonb,
    status VARCHAR(20) NOT NULL DEFAULT 'DISABLED' CHECK (status IN ('DISABLED','ACTIVE','DEGRADED','FAILED')),
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_crm_integration_tenant_type
    ON crm_platform.integration_endpoint (tenant_id, integration_type, status);
