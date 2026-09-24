-- ============================================================
-- V20260814_2: Module Capabilities + Plan Module Entitlements
--
-- Creates:
--   1. module_capabilities — catalog of capabilities per module
--      (BOOLEAN_CAPABILITY, FEATURE_ENABLED, NUMERIC_LIMIT, QUOTA)
--   2. plan_module_entitlements — links saas_plans to modules
--      with specific capability/limit/quota values
--
-- This EXTENDS the existing saas_plan_entitlements table (V19) without
-- modifying it. The existing featureCode/enabled/limitValue model stays
-- for backward compatibility; this new table adds module-scoped entitlements.
--
-- Idempotent + backward compatible.
-- ============================================================

-- ============================================================
-- STEP 1: module_capabilities — catalog of capabilities per module
-- ============================================================
CREATE TABLE IF NOT EXISTS module_capabilities (
    id              UUID            NOT NULL,
    module_id       UUID            NOT NULL,
    code            VARCHAR(150)   NOT NULL,        -- e.g., 'CRM.MAX_CONTACTS', 'AI.MONTHLY_OPS'
    name            VARCHAR(200)   NOT NULL,
    description     VARCHAR(1000),
    capability_type VARCHAR(30)    NOT NULL,        -- MODULE_ENABLED | FEATURE_ENABLED | NUMERIC_LIMIT | QUOTA | BOOLEAN_CAPABILITY
    default_value   VARCHAR(500),                   -- default value when not overridden by plan
    status          VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_module_capabilities PRIMARY KEY (id),
    CONSTRAINT fk_module_capabilities_module FOREIGN KEY (module_id) REFERENCES modules(id),
    CONSTRAINT uk_module_capabilities_code UNIQUE (code),
    CONSTRAINT ck_module_cap_type CHECK (capability_type IN (
        'MODULE_ENABLED', 'FEATURE_ENABLED', 'NUMERIC_LIMIT', 'QUOTA', 'BOOLEAN_CAPABILITY'
    )),
    CONSTRAINT ck_module_cap_status CHECK (status IN ('ACTIVE','INACTIVE'))
);

CREATE INDEX IF NOT EXISTS idx_module_caps_module ON module_capabilities(module_id);
CREATE INDEX IF NOT EXISTS idx_module_caps_status ON module_capabilities(status);

-- ============================================================
-- STEP 2: plan_module_entitlements — per-plan module configuration
-- Links saas_plans → modules with specific capability/limit/quota values
-- ============================================================
CREATE TABLE IF NOT EXISTS plan_module_entitlements (
    id                      UUID            NOT NULL,
    plan_id                 UUID            NOT NULL,
    module_id               UUID            NOT NULL,
    module_enabled          BOOLEAN         NOT NULL DEFAULT true,
    capability_code         VARCHAR(150),   -- nullable: if set, overrides module_capabilities.default_value
    capability_value        VARCHAR(500),   -- value as string (parsed by EntitlementResolver based on type)
    limit_value             BIGINT,         -- for NUMERIC_LIMIT type
    quota_value             BIGINT,         -- for QUOTA type (with quota_period)
    quota_period            VARCHAR(20),    -- DAILY | MONTHLY | YEARLY | TOTAL
    effective_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at              TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_plan_module_entitlements PRIMARY KEY (id),
    CONSTRAINT fk_pme_plan FOREIGN KEY (plan_id) REFERENCES saas_plans(id),
    CONSTRAINT fk_pme_module FOREIGN KEY (module_id) REFERENCES modules(id),
    CONSTRAINT uk_pme_plan_module_cap UNIQUE (plan_id, module_id, capability_code),
    CONSTRAINT ck_pme_quota_period CHECK (quota_period IS NULL OR quota_period IN ('DAILY','MONTHLY','YEARLY','TOTAL'))
);

CREATE INDEX IF NOT EXISTS idx_pme_plan ON plan_module_entitlements(plan_id);
CREATE INDEX IF NOT EXISTS idx_pme_module ON plan_module_entitlements(module_id);
CREATE INDEX IF NOT EXISTS idx_pme_plan_module ON plan_module_entitlements(plan_id, module_id);

-- ============================================================
-- STEP 3: tenant_entitlement_cache — cached effective entitlements per tenant
-- Populated by EntitlementResolver when subscription changes
-- ============================================================
CREATE TABLE IF NOT EXISTS tenant_entitlement_cache (
    id                      UUID            NOT NULL,
    tenant_id               UUID            NOT NULL,
    subscription_id         UUID,
    plan_id                 UUID            NOT NULL,
    module_id               UUID            NOT NULL,
    module_enabled          BOOLEAN         NOT NULL,
    capability_code         VARCHAR(150)    NOT NULL,
    capability_type         VARCHAR(30)     NOT NULL,
    effective_value         VARCHAR(500),
    effective_limit         BIGINT,
    effective_quota         BIGINT,
    quota_period            VARCHAR(20),
    effective_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    expires_at              TIMESTAMP WITH TIME ZONE,    -- nullable: NULL = no expiry
    source                  VARCHAR(20)     NOT NULL DEFAULT 'SUBSCRIPTION',  -- SUBSCRIPTION | MANUAL_OVERRIDE | TRIAL
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at              TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_tenant_entitlement_cache PRIMARY KEY (id),
    CONSTRAINT fk_tec_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_tec_module FOREIGN KEY (module_id) REFERENCES modules(id),
    CONSTRAINT uk_tec_tenant_module_cap UNIQUE (tenant_id, module_id, capability_code),
    CONSTRAINT ck_tec_source CHECK (source IN ('SUBSCRIPTION','MANUAL_OVERRIDE','TRIAL'))
);

CREATE INDEX IF NOT EXISTS idx_tec_tenant ON tenant_entitlement_cache(tenant_id);
CREATE INDEX IF NOT EXISTS idx_tec_tenant_module ON tenant_entitlement_cache(tenant_id, module_id);
CREATE INDEX IF NOT EXISTS idx_tec_effective_at ON tenant_entitlement_cache(effective_at);

-- ============================================================
-- STEP 4: Seed default module_capabilities (idempotent)
-- Each module gets a MODULE_ENABLED capability + key limits/quotas
-- ============================================================
INSERT INTO module_capabilities (id, module_id, code, name, description, capability_type, default_value, status, created_at, updated_at)
SELECT gen_random_uuid(), m.id, seed.code, seed.name, seed.description, seed.capability_type, seed.default_value, 'ACTIVE', NOW(), NOW()
FROM modules m
JOIN (VALUES
    -- CRM module
    ('CRM', 'CRM.ENABLED',               'CRM Module Enabled',         'Whether CRM module is enabled',                  'MODULE_ENABLED',    'true'),
    ('CRM', 'CRM.MAX_CONTACTS',          'Max Contacts',               'Maximum number of contacts',                     'NUMERIC_LIMIT',     '10000'),
    ('CRM', 'CRM.MAX_ACCOUNTS',          'Max Accounts',                'Maximum number of accounts',                     'NUMERIC_LIMIT',     '1000'),
    ('CRM', 'CRM.ADVANCED_PIPELINE',     'Advanced Pipeline',          'Access to advanced pipeline features',           'BOOLEAN_CAPABILITY','false'),
    ('CRM', 'CRM.MONTHLY_API_CALLS',     'Monthly API Calls',          'API call quota per month',                       'QUOTA',             '50000'),

    -- AI module
    ('AI', 'AI.ENABLED',                 'AI Module Enabled',          'Whether AI module is enabled',                   'MODULE_ENABLED',    'true'),
    ('AI', 'AI.MAX_AGENTS',              'Max AI Agents',              'Maximum number of AI agents',                    'NUMERIC_LIMIT',     '5'),
    ('AI', 'AI.MONTHLY_OPERATIONS',       'Monthly AI Operations',      'AI inference operations per month',              'QUOTA',             '10000'),

    -- WORKFLOW module
    ('WORKFLOW', 'WORKFLOW.ENABLED',              'Workflow Module Enabled',    'Whether Workflow module is enabled',     'MODULE_ENABLED',    'true'),
    ('WORKFLOW', 'WORKFLOW.MAX_WORKFLOWS',        'Max Workflows',               'Maximum number of workflows',            'NUMERIC_LIMIT',     '10'),
    ('WORKFLOW', 'WORKFLOW.MONTHLY_EXECUTIONS',   'Monthly Executions',          'Workflow executions per month',         'QUOTA',             '1000'),

    -- ERP module
    ('ERP', 'ERP.ENABLED',               'ERP Module Enabled',         'Whether ERP module is enabled',                 'MODULE_ENABLED',    'false'),
    ('ERP', 'ERP.MAX_SKUS',              'Max SKUs',                   'Maximum number of stock-keeping units',         'NUMERIC_LIMIT',     '1000'),

    -- FINANCE module
    ('FINANCE', 'FINANCE.ENABLED',       'Finance Module Enabled',     'Whether Finance module is enabled',             'MODULE_ENABLED',    'true'),
    ('FINANCE', 'FINANCE.MONTHLY_INVOICES','Monthly Invoices',          'Invoice generation quota per month',            'QUOTA',             '100'),

    -- ANALYTICS module
    ('ANALYTICS', 'ANALYTICS.ENABLED',           'Analytics Module Enabled', 'Whether Analytics module is enabled',   'MODULE_ENABLED',    'true'),
    ('ANALYTICS', 'ANALYTICS.ADVANCED_REPORTS',  'Advanced Reports',          'Access to advanced reporting features', 'BOOLEAN_CAPABILITY','false'),

    -- HRM module
    ('HRM', 'HRM.ENABLED',               'HRM Module Enabled',         'Whether HRM module is enabled',                 'MODULE_ENABLED',    'false'),
    ('HRM', 'HRM.MAX_EMPLOYEES',         'Max Employees',              'Maximum number of employees',                   'NUMERIC_LIMIT',     '50'),

    -- POS module
    ('POS', 'POS.ENABLED',               'POS Module Enabled',         'Whether POS module is enabled',                 'MODULE_ENABLED',    'false'),
    ('POS', 'POS.MAX_TERMINALS',         'Max Terminals',              'Maximum number of POS terminals',                'NUMERIC_LIMIT',     '1'),

    -- ECOMMERCE_CX module
    ('ECOMMERCE_CX', 'ECOMMERCE_CX.ENABLED',      'Ecommerce/CX Enabled',  'Whether Ecommerce/CX module is enabled','MODULE_ENABLED',    'false'),

    -- INDUSTRY_SOLUTIONS module
    ('INDUSTRY_SOLUTIONS', 'INDUSTRY_SOLUTIONS.ENABLED', 'Industry Solutions Enabled', 'Whether Industry Solutions module is enabled', 'MODULE_ENABLED', 'false')
) AS seed(module_code, code, name, description, capability_type, default_value)
ON seed.module_code = m.code
WHERE NOT EXISTS (SELECT 1 FROM module_capabilities mc WHERE mc.code = seed.code);

-- ============================================================
-- STEP 5: Seed default plan_module_entitlements (idempotent)
-- Maps STARTER/GROWTH/ENTERPRISE plans to modules
-- ============================================================
DO $$
DECLARE
    starter_plan_id    UUID;
    growth_plan_id     UUID;
    enterprise_plan_id UUID;
    crm_module_id      UUID;
    ai_module_id       UUID;
    workflow_module_id UUID;
    analytics_module_id UUID;
    finance_module_id  UUID;
BEGIN
    -- Get plan IDs (they were seeded in V19 or V20260813_1)
    SELECT id INTO starter_plan_id    FROM saas_plans WHERE code = 'STARTER'    LIMIT 1;
    SELECT id INTO growth_plan_id     FROM saas_plans WHERE code = 'GROWTH'     LIMIT 1;
    SELECT id INTO enterprise_plan_id FROM saas_plans WHERE code = 'ENTERPRISE' LIMIT 1;

    -- Get module IDs
    SELECT id INTO crm_module_id      FROM modules WHERE code = 'CRM'        LIMIT 1;
    SELECT id INTO ai_module_id       FROM modules WHERE code = 'AI'         LIMIT 1;
    SELECT id INTO workflow_module_id FROM modules WHERE code = 'WORKFLOW'   LIMIT 1;
    SELECT id INTO analytics_module_id FROM modules WHERE code = 'ANALYTICS' LIMIT 1;
    SELECT id INTO finance_module_id  FROM modules WHERE code = 'FINANCE'    LIMIT 1;

    -- STARTER: CRM + AI + Workflow (limited)
    IF starter_plan_id IS NOT NULL AND crm_module_id IS NOT NULL THEN
        INSERT INTO plan_module_entitlements (id, plan_id, module_id, module_enabled, capability_code, capability_value, limit_value, quota_value, quota_period, effective_at, created_at, updated_at)
        SELECT gen_random_uuid(), starter_plan_id, crm_module_id, true, 'CRM.MAX_CONTACTS', '1000', 1000, NULL, NULL, NOW(), NOW(), NOW()
        WHERE NOT EXISTS (SELECT 1 FROM plan_module_entitlements WHERE plan_id = starter_plan_id AND module_id = crm_module_id AND capability_code = 'CRM.MAX_CONTACTS');
        INSERT INTO plan_module_entitlements (id, plan_id, module_id, module_enabled, capability_code, capability_value, limit_value, quota_value, quota_period, effective_at, created_at, updated_at)
        SELECT gen_random_uuid(), starter_plan_id, crm_module_id, true, 'CRM.MONTHLY_API_CALLS', '5000', NULL, 5000, 'MONTHLY', NOW(), NOW(), NOW()
        WHERE NOT EXISTS (SELECT 1 FROM plan_module_entitlements WHERE plan_id = starter_plan_id AND module_id = crm_module_id AND capability_code = 'CRM.MONTHLY_API_CALLS');
    END IF;

    IF starter_plan_id IS NOT NULL AND ai_module_id IS NOT NULL THEN
        INSERT INTO plan_module_entitlements (id, plan_id, module_id, module_enabled, capability_code, capability_value, limit_value, quota_value, quota_period, effective_at, created_at, updated_at)
        SELECT gen_random_uuid(), starter_plan_id, ai_module_id, true, 'AI.MAX_AGENTS', '1', 1, NULL, NULL, NOW(), NOW(), NOW()
        WHERE NOT EXISTS (SELECT 1 FROM plan_module_entitlements WHERE plan_id = starter_plan_id AND module_id = ai_module_id AND capability_code = 'AI.MAX_AGENTS');
    END IF;

    IF starter_plan_id IS NOT NULL AND workflow_module_id IS NOT NULL THEN
        INSERT INTO plan_module_entitlements (id, plan_id, module_id, module_enabled, capability_code, capability_value, limit_value, quota_value, quota_period, effective_at, created_at, updated_at)
        SELECT gen_random_uuid(), starter_plan_id, workflow_module_id, true, 'WORKFLOW.MAX_WORKFLOWS', '3', 3, NULL, NULL, NOW(), NOW(), NOW()
        WHERE NOT EXISTS (SELECT 1 FROM plan_module_entitlements WHERE plan_id = starter_plan_id AND module_id = workflow_module_id AND capability_code = 'WORKFLOW.MAX_WORKFLOWS');
    END IF;

    -- GROWTH: CRM + AI + Workflow + Analytics (expanded)
    IF growth_plan_id IS NOT NULL AND crm_module_id IS NOT NULL THEN
        INSERT INTO plan_module_entitlements (id, plan_id, module_id, module_enabled, capability_code, capability_value, limit_value, quota_value, quota_period, effective_at, created_at, updated_at)
        SELECT gen_random_uuid(), growth_plan_id, crm_module_id, true, 'CRM.MAX_CONTACTS', '25000', 25000, NULL, NULL, NOW(), NOW(), NOW()
        WHERE NOT EXISTS (SELECT 1 FROM plan_module_entitlements WHERE plan_id = growth_plan_id AND module_id = crm_module_id AND capability_code = 'CRM.MAX_CONTACTS');
        INSERT INTO plan_module_entitlements (id, plan_id, module_id, module_enabled, capability_code, capability_value, limit_value, quota_value, quota_period, effective_at, created_at, updated_at)
        SELECT gen_random_uuid(), growth_plan_id, crm_module_id, true, 'CRM.MONTHLY_API_CALLS', '50000', NULL, 50000, 'MONTHLY', NOW(), NOW(), NOW()
        WHERE NOT EXISTS (SELECT 1 FROM plan_module_entitlements WHERE plan_id = growth_plan_id AND module_id = crm_module_id AND capability_code = 'CRM.MONTHLY_API_CALLS');
        INSERT INTO plan_module_entitlements (id, plan_id, module_id, module_enabled, capability_code, capability_value, limit_value, quota_value, quota_period, effective_at, created_at, updated_at)
        SELECT gen_random_uuid(), growth_plan_id, crm_module_id, true, 'CRM.ADVANCED_PIPELINE', 'true', NULL, NULL, NULL, NOW(), NOW(), NOW()
        WHERE NOT EXISTS (SELECT 1 FROM plan_module_entitlements WHERE plan_id = growth_plan_id AND module_id = crm_module_id AND capability_code = 'CRM.ADVANCED_PIPELINE');
    END IF;

    IF growth_plan_id IS NOT NULL AND ai_module_id IS NOT NULL THEN
        INSERT INTO plan_module_entitlements (id, plan_id, module_id, module_enabled, capability_code, capability_value, limit_value, quota_value, quota_period, effective_at, created_at, updated_at)
        SELECT gen_random_uuid(), growth_plan_id, ai_module_id, true, 'AI.MAX_AGENTS', '10', 10, NULL, NULL, NOW(), NOW(), NOW()
        WHERE NOT EXISTS (SELECT 1 FROM plan_module_entitlements WHERE plan_id = growth_plan_id AND module_id = ai_module_id AND capability_code = 'AI.MAX_AGENTS');
        INSERT INTO plan_module_entitlements (id, plan_id, module_id, module_enabled, capability_code, capability_value, limit_value, quota_value, quota_period, effective_at, created_at, updated_at)
        SELECT gen_random_uuid(), growth_plan_id, ai_module_id, true, 'AI.MONTHLY_OPERATIONS', '50000', NULL, 50000, 'MONTHLY', NOW(), NOW(), NOW()
        WHERE NOT EXISTS (SELECT 1 FROM plan_module_entitlements WHERE plan_id = growth_plan_id AND module_id = ai_module_id AND capability_code = 'AI.MONTHLY_OPERATIONS');
    END IF;

    IF growth_plan_id IS NOT NULL AND analytics_module_id IS NOT NULL THEN
        INSERT INTO plan_module_entitlements (id, plan_id, module_id, module_enabled, capability_code, capability_value, limit_value, quota_value, quota_period, effective_at, created_at, updated_at)
        SELECT gen_random_uuid(), growth_plan_id, analytics_module_id, true, 'ANALYTICS.ADVANCED_REPORTS', 'true', NULL, NULL, NULL, NOW(), NOW(), NOW()
        WHERE NOT EXISTS (SELECT 1 FROM plan_module_entitlements WHERE plan_id = growth_plan_id AND module_id = analytics_module_id AND capability_code = 'ANALYTICS.ADVANCED_REPORTS');
    END IF;

    -- ENTERPRISE: All modules + unlimited-ish limits
    IF enterprise_plan_id IS NOT NULL AND crm_module_id IS NOT NULL THEN
        INSERT INTO plan_module_entitlements (id, plan_id, module_id, module_enabled, capability_code, capability_value, limit_value, quota_value, quota_period, effective_at, created_at, updated_at)
        SELECT gen_random_uuid(), enterprise_plan_id, crm_module_id, true, 'CRM.MAX_CONTACTS', '1000000', 1000000, NULL, NULL, NOW(), NOW(), NOW()
        WHERE NOT EXISTS (SELECT 1 FROM plan_module_entitlements WHERE plan_id = enterprise_plan_id AND module_id = crm_module_id AND capability_code = 'CRM.MAX_CONTACTS');
        INSERT INTO plan_module_entitlements (id, plan_id, module_id, module_enabled, capability_code, capability_value, limit_value, quota_value, quota_period, effective_at, created_at, updated_at)
        SELECT gen_random_uuid(), enterprise_plan_id, crm_module_id, true, 'CRM.MONTHLY_API_CALLS', '1000000', NULL, 1000000, 'MONTHLY', NOW(), NOW(), NOW()
        WHERE NOT EXISTS (SELECT 1 FROM plan_module_entitlements WHERE plan_id = enterprise_plan_id AND module_id = crm_module_id AND capability_code = 'CRM.MONTHLY_API_CALLS');
        INSERT INTO plan_module_entitlements (id, plan_id, module_id, module_enabled, capability_code, capability_value, limit_value, quota_value, quota_period, effective_at, created_at, updated_at)
        SELECT gen_random_uuid(), enterprise_plan_id, crm_module_id, true, 'CRM.ADVANCED_PIPELINE', 'true', NULL, NULL, NULL, NOW(), NOW(), NOW()
        WHERE NOT EXISTS (SELECT 1 FROM plan_module_entitlements WHERE plan_id = enterprise_plan_id AND module_id = crm_module_id AND capability_code = 'CRM.ADVANCED_PIPELINE');
    END IF;

    IF enterprise_plan_id IS NOT NULL AND ai_module_id IS NOT NULL THEN
        INSERT INTO plan_module_entitlements (id, plan_id, module_id, module_enabled, capability_code, capability_value, limit_value, quota_value, quota_period, effective_at, created_at, updated_at)
        SELECT gen_random_uuid(), enterprise_plan_id, ai_module_id, true, 'AI.MAX_AGENTS', '100', 100, NULL, NULL, NOW(), NOW(), NOW()
        WHERE NOT EXISTS (SELECT 1 FROM plan_module_entitlements WHERE plan_id = enterprise_plan_id AND module_id = ai_module_id AND capability_code = 'AI.MAX_AGENTS');
        INSERT INTO plan_module_entitlements (id, plan_id, module_id, module_enabled, capability_code, capability_value, limit_value, quota_value, quota_period, effective_at, created_at, updated_at)
        SELECT gen_random_uuid(), enterprise_plan_id, ai_module_id, true, 'AI.MONTHLY_OPERATIONS', '500000', NULL, 500000, 'MONTHLY', NOW(), NOW(), NOW()
        WHERE NOT EXISTS (SELECT 1 FROM plan_module_entitlements WHERE plan_id = enterprise_plan_id AND module_id = ai_module_id AND capability_code = 'AI.MONTHLY_OPERATIONS');
    END IF;
END $$;

-- ============================================================
-- STEP 6: (Removed — Flyway automatically records the migration
--          in flyway_schema_history on successful commit.)
--
-- Same fix as V20260814_1: removed the DO block that manually
-- INSERTed into flyway_schema_history, which caused CI to hang.
-- See V20260814_1 for the full explanation.
-- ============================================================
