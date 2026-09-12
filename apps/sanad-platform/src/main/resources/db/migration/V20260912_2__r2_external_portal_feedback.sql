-- ============================================================
-- R2-D/H — Secure External Action Portal + Customer Feedback (GATES R2.11/R2.12/R2.18)
-- ============================================================
-- ADDITIVE FORWARD-ONLY. Builds on R1 workflow_external_participants /
-- workflow_external_actions (V20260911_2). No User/Employee rows are ever
-- created for external participants (R1 invariant).

-- Portal OTP policy flag per external action (additive ALTER, R2.11:
-- "optional OTP when policy requires").
ALTER TABLE workflow_external_actions
    ADD COLUMN IF NOT EXISTS otp_required BOOLEAN NOT NULL DEFAULT FALSE;

-- ------------------------------------------------------------
-- 1. workflow_portal_access_log — rate limiting + audit trail (R2.11)
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS workflow_portal_access_log (
    id                  BIGSERIAL       PRIMARY KEY,
    tenant_id           UUID            NOT NULL,
    external_action_id  UUID            NOT NULL,
    access_type         VARCHAR(20)     NOT NULL,
    outcome             VARCHAR(20)     NOT NULL,
    remote_fingerprint  VARCHAR(64),
    detail              VARCHAR(300),
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_wf_portal_access_type CHECK (access_type IN
        ('VIEW', 'RESPOND', 'OTP_ISSUE', 'OTP_VERIFY')),
    CONSTRAINT ck_wf_portal_access_outcome CHECK (outcome IN
        ('ALLOWED', 'DENIED', 'RATE_LIMITED', 'EXPIRED', 'REVOKED'))
);
CREATE INDEX IF NOT EXISTS idx_wf_portal_access_action
    ON workflow_portal_access_log(external_action_id, access_type, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_wf_portal_access_fingerprint
    ON workflow_portal_access_log(remote_fingerprint, created_at DESC);

ALTER TABLE workflow_portal_access_log ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS workflow_portal_access_log_tenant_isolation
    ON workflow_portal_access_log;
CREATE POLICY workflow_portal_access_log_tenant_isolation
    ON workflow_portal_access_log
    USING (tenant_id = current_setting('app.tenant_id', true)::UUID)
    WITH CHECK (tenant_id = current_setting('app.tenant_id', true)::UUID);

-- ------------------------------------------------------------
-- 2. workflow_portal_otp_challenges — optional OTP (R2.11)
--    short-lived, attempt-limited, bound to action+participant,
--    plaintext OTP never stored or logged.
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS workflow_portal_otp_challenges (
    id                  UUID            NOT NULL,
    tenant_id           UUID            NOT NULL,
    external_action_id  UUID            NOT NULL,
    participant_id      UUID            NOT NULL,
    otp_hash            CHAR(64)        NOT NULL,
    expires_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    attempt_count       INTEGER         NOT NULL DEFAULT 0,
    max_attempts        INTEGER         NOT NULL DEFAULT 5,
    consumed_at         TIMESTAMP WITH TIME ZONE,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_workflow_portal_otp_challenges PRIMARY KEY (id),
    CONSTRAINT uk_wf_portal_otp_tenant UNIQUE (tenant_id, id),
    CONSTRAINT ck_wf_portal_otp_attempts CHECK (attempt_count <= max_attempts)
);
CREATE INDEX IF NOT EXISTS idx_wf_portal_otp_action
    ON workflow_portal_otp_challenges(external_action_id, expires_at DESC);

ALTER TABLE workflow_portal_otp_challenges ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS workflow_portal_otp_tenant_isolation
    ON workflow_portal_otp_challenges;
CREATE POLICY workflow_portal_otp_tenant_isolation
    ON workflow_portal_otp_challenges
    USING (tenant_id = current_setting('app.tenant_id', true)::UUID)
    WITH CHECK (tenant_id = current_setting('app.tenant_id', true)::UUID);

-- ------------------------------------------------------------
-- 3. workflow_customer_feedback — evidence-input foundation (R2.18)
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS workflow_customer_feedback (
    id                  UUID            NOT NULL,
    tenant_id           UUID            NOT NULL REFERENCES tenants(id),
    workflow_instance_id UUID,
    workflow_step_instance_id UUID,
    external_action_id  UUID,
    source_module       VARCHAR(50),
    source_entity_type  VARCHAR(100),
    source_entity_id    UUID,
    participant_id      UUID,
    rating              INTEGER         NOT NULL,
    comment             VARCHAR(2000),
    correlation_id      UUID,
    submitted_by_type   VARCHAR(20)     NOT NULL DEFAULT 'EXTERNAL',
    submitted_by_user_id UUID,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_workflow_customer_feedback PRIMARY KEY (id),
    CONSTRAINT uk_wf_feedback_tenant UNIQUE (tenant_id, id),
    CONSTRAINT ck_wf_feedback_rating CHECK (rating BETWEEN 1 AND 5),
    CONSTRAINT ck_wf_feedback_source CHECK (submitted_by_type IN
        ('EXTERNAL', 'USER'))
);

CREATE INDEX IF NOT EXISTS idx_wf_feedback_instance
    ON workflow_customer_feedback(tenant_id, workflow_instance_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_wf_feedback_source
    ON workflow_customer_feedback(tenant_id, source_entity_type, source_entity_id);

ALTER TABLE workflow_customer_feedback ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS workflow_customer_feedback_tenant_isolation
    ON workflow_customer_feedback;
CREATE POLICY workflow_customer_feedback_tenant_isolation
    ON workflow_customer_feedback
    USING (tenant_id = current_setting('app.tenant_id', true)::UUID)
    WITH CHECK (tenant_id = current_setting('app.tenant_id', true)::UUID);
