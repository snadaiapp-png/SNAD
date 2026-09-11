-- ============================================================
-- R1-F/G — Attachment platform boundary + External participants/actions
-- ============================================================
-- R1 GATE R1.14/R1.15: platform-shared file REFERENCE boundary (no Workflow
-- binary silos, no large blobs in workflow relational rows) + R1.16/R1.17/
-- R1.18 external participant model and ExternalActionRequest foundation.
-- Forward-only; follows the platform tenant-isolation conventions
-- (tenant_id NOT NULL, UNIQUE (tenant_id,id), ENABLE RLS + fail-closed
-- tenant_isolation policy on unset GUC — the workflow engine family convention,
-- consistent with V20260815_10/V20260902_*; FORCE RLS remains the CRM/HR
-- precedent where runtime GUC wiring is already enforced).

-- ------------------------------------------------------------
-- platform_files — shared platform file REFERENCE registry
-- (storage bytes remain outside the platform DB; the reference is opaque)
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS platform_files (
    id                  UUID            NOT NULL,
    tenant_id           UUID            NOT NULL,
    source_module       VARCHAR(50)     NOT NULL,
    source_entity_type  VARCHAR(100),
    source_entity_id    UUID,
    mime_type           VARCHAR(255),
    size_bytes          BIGINT,
    checksum_sha256     CHAR(64),
    classification      VARCHAR(30)     NOT NULL DEFAULT 'INTERNAL',
    storage_reference   TEXT            NOT NULL,
    uploaded_by         UUID,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_platform_files PRIMARY KEY (id),
    CONSTRAINT uk_platform_files_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT ck_platform_files_classification
        CHECK (classification IN ('PUBLIC','INTERNAL','CONFIDENTIAL','RESTRICTED'))
);
CREATE INDEX IF NOT EXISTS idx_platform_files_tenant ON platform_files(tenant_id, created_at);

ALTER TABLE platform_files ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS platform_files_tenant_isolation ON platform_files;
CREATE POLICY platform_files_tenant_isolation ON platform_files
    USING (tenant_id = current_setting('app.tenant_id', true)::UUID)
    WITH CHECK (tenant_id = current_setting('app.tenant_id', true)::UUID);

-- ------------------------------------------------------------
-- workflow_attachments — bounded Workflow attachment references
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS workflow_attachments (
    id                UUID            NOT NULL,
    tenant_id         UUID            NOT NULL,
    scope             VARCHAR(30)     NOT NULL,
    scope_id          UUID            NOT NULL,
    file_reference_id UUID,
    attachment_class  VARCHAR(30)     NOT NULL,
    required_flag     BOOLEAN         NOT NULL DEFAULT false,
    uploaded_by       UUID,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_workflow_attachments PRIMARY KEY (id),
    CONSTRAINT uk_workflow_attachments_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT ck_wf_attachment_scope
        CHECK (scope IN ('PROCESS','STEP','WORK_ITEM','APPROVAL','EXTERNAL_ACTION')),
    CONSTRAINT ck_wf_attachment_class
        CHECK (attachment_class IN ('IMAGE','PDF','DOCUMENT','SPREADSHEET','VIDEO','OTHER_FILE')),
    CONSTRAINT fk_wf_attachment_file
        FOREIGN KEY (tenant_id, file_reference_id)
        REFERENCES platform_files (tenant_id, id)
);
CREATE INDEX IF NOT EXISTS idx_wf_attachments_scope
    ON workflow_attachments(tenant_id, scope, scope_id);

ALTER TABLE workflow_attachments ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS workflow_attachments_tenant_isolation ON workflow_attachments;
CREATE POLICY workflow_attachments_tenant_isolation ON workflow_attachments
    USING (tenant_id = current_setting('app.tenant_id', true)::UUID)
    WITH CHECK (tenant_id = current_setting('app.tenant_id', true)::UUID);

-- ------------------------------------------------------------
-- workflow_external_participants — NO fake User/Employee records
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS workflow_external_participants (
    id                     UUID            NOT NULL,
    tenant_id              UUID            NOT NULL,
    participant_type       VARCHAR(30)     NOT NULL,
    source_module          VARCHAR(50)     NOT NULL,
    source_entity_type     VARCHAR(100)    NOT NULL,
    source_entity_id       UUID            NOT NULL,
    display_reference      VARCHAR(300),
    communication_reference VARCHAR(300),
    created_at             TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_workflow_external_participants PRIMARY KEY (id),
    CONSTRAINT uk_wf_ext_participant_tenant UNIQUE (tenant_id, id),
    CONSTRAINT uk_wf_ext_participant_source
        UNIQUE (tenant_id, participant_type, source_entity_type, source_entity_id),
    CONSTRAINT ck_wf_ext_participant_type
        CHECK (participant_type IN ('CUSTOMER','CUSTOMER_CONTACT','SUPPLIER_CONTACT',
                                    'PARTNER_CONTACT','EXTERNAL_PERSON'))
);
CREATE INDEX IF NOT EXISTS idx_wf_ext_participants_tenant
    ON workflow_external_participants(tenant_id, participant_type);

ALTER TABLE workflow_external_participants ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS workflow_external_participants_tenant_isolation ON workflow_external_participants;
CREATE POLICY workflow_external_participants_tenant_isolation ON workflow_external_participants
    USING (tenant_id = current_setting('app.tenant_id', true)::UUID)
    WITH CHECK (tenant_id = current_setting('app.tenant_id', true)::UUID);

-- ------------------------------------------------------------
-- workflow_external_actions — bounded request to a non-SNAD participant
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS workflow_external_actions (
    id                UUID            NOT NULL,
    tenant_id         UUID            NOT NULL,
    workflow_instance_id UUID         NOT NULL,
    workflow_step_instance_id UUID,
    participant_id    UUID            NOT NULL,
    action_type       VARCHAR(30)     NOT NULL,
    status            VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    token_hash        VARCHAR(128),
    token_expires_at  TIMESTAMP WITH TIME ZONE,
    allowed_actions   JSONB,
    response_payload  JSONB,
    idempotency_key   VARCHAR(200)    NOT NULL,
    responded_at      TIMESTAMP WITH TIME ZONE,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_workflow_external_actions PRIMARY KEY (id),
    CONSTRAINT uk_wf_ext_action_tenant UNIQUE (tenant_id, id),
    CONSTRAINT uk_wf_ext_action_idempotency UNIQUE (tenant_id, idempotency_key),
    CONSTRAINT ck_wf_ext_action_type
        CHECK (action_type IN ('APPROVE','REJECT','ACKNOWLEDGE','CONFIRM',
                               'UPLOAD','PROVIDE_INFORMATION','SIGN_REQUEST')),
    CONSTRAINT ck_wf_ext_action_status
        CHECK (status IN ('PENDING','VIEWED','RESPONDED','EXPIRED','REVOKED','CANCELLED')),
    CONSTRAINT fk_wf_ext_action_instance
        FOREIGN KEY (workflow_instance_id) REFERENCES workflow_instances (id),
    CONSTRAINT fk_wf_ext_action_participant
        FOREIGN KEY (tenant_id, participant_id)
        REFERENCES workflow_external_participants (tenant_id, id)
);
CREATE INDEX IF NOT EXISTS idx_wf_ext_actions_tenant_status
    ON workflow_external_actions(tenant_id, status, token_expires_at);

ALTER TABLE workflow_external_actions ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS workflow_external_actions_tenant_isolation ON workflow_external_actions;
CREATE POLICY workflow_external_actions_tenant_isolation ON workflow_external_actions
    USING (tenant_id = current_setting('app.tenant_id', true)::UUID)
    WITH CHECK (tenant_id = current_setting('app.tenant_id', true)::UUID);
