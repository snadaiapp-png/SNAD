-- ============================================================
-- SNAD Platform — Create CRM email logs
-- ============================================================
-- Creates the crm_email_logs table for email audit trail and
-- open/click tracking. Seeds CRM.EMAIL.READ and CRM.EMAIL.WRITE
-- capabilities and grants them to ADMIN roles.
-- ============================================================

-- 1. Email logs table
CREATE TABLE IF NOT EXISTS crm_email_logs (
    id                   UUID        NOT NULL,
    tenant_id            UUID        NOT NULL,
    user_id              UUID,
    from_address         VARCHAR(255) NOT NULL,
    to_address           VARCHAR(255) NOT NULL,
    subject              VARCHAR(500) NOT NULL,
    status               VARCHAR(50)  NOT NULL DEFAULT 'PENDING',
    provider             VARCHAR(50),
    provider_message_id  VARCHAR(255),
    related_entity_type  VARCHAR(100),
    related_entity_id    UUID,
    template_name        VARCHAR(100),
    sent_at              TIMESTAMP WITH TIME ZONE,
    opened_at            TIMESTAMP WITH TIME ZONE,
    clicked_at           TIMESTAMP WITH TIME ZONE,
    click_url            TEXT,
    error_message        TEXT,
    created_at           TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_crm_email_logs PRIMARY KEY (id),
    CONSTRAINT fk_crm_email_logs_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT uk_crm_email_logs_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT ck_crm_email_logs_status CHECK (status IN ('PENDING', 'SENT', 'FAILED', 'QUEUED', 'BOUNCED', 'COMPLAINED'))
);

-- 2. Indexes for email log queries
CREATE INDEX IF NOT EXISTS idx_crm_email_logs_tenant_created
    ON crm_email_logs (tenant_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_crm_email_logs_tenant_status
    ON crm_email_logs (tenant_id, status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_crm_email_logs_tenant_related
    ON crm_email_logs (tenant_id, related_entity_type, related_entity_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_crm_email_logs_tenant_to
    ON crm_email_logs (tenant_id, to_address);

CREATE INDEX IF NOT EXISTS idx_crm_email_logs_provider_message
    ON crm_email_logs (provider_message_id) WHERE provider_message_id IS NOT NULL;

-- 3. Seed CRM.EMAIL.READ and CRM.EMAIL.WRITE capabilities
INSERT INTO access_capabilities (id, code, name, description, status, created_at, updated_at)
SELECT gen_random_uuid(), capability.code, capability.name, capability.description, 'ACTIVE', NOW(), NOW()
FROM (VALUES
    ('CRM.EMAIL.READ',  'Read CRM Emails',  'View tenant CRM email logs and tracking data'),
    ('CRM.EMAIL.WRITE', 'Write CRM Emails', 'Send CRM emails and manage email templates')
) AS capability(code, name, description)
WHERE NOT EXISTS (
    SELECT 1 FROM access_capabilities ac WHERE ac.code = capability.code
);

-- 4. Grant CRM.EMAIL.* capabilities to ADMIN roles in every tenant
INSERT INTO role_capabilities (id, tenant_id, role_id, capability_id, granted_at, granted_by, status)
SELECT
    gen_random_uuid(),
    role.tenant_id,
    role.id,
    capability.id,
    NOW(),
    NULL,
    'ACTIVE'
FROM roles role
JOIN access_capabilities capability ON (
    capability.code IN ('CRM.EMAIL.READ', 'CRM.EMAIL.WRITE')
    AND capability.status = 'ACTIVE'
)
WHERE role.code = 'ADMIN'
  AND role.status = 'ACTIVE'
  AND NOT EXISTS (
    SELECT 1 FROM role_capabilities rc
    WHERE rc.tenant_id = role.tenant_id
      AND rc.role_id = role.id
      AND rc.capability_id = capability.id
      AND rc.status = 'ACTIVE'
);
