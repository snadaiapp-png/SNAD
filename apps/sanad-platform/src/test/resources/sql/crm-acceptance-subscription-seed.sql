-- SNAD CRM authenticated acceptance — login-eligible subscription fixture
-- This file is intentionally separate from crm-acceptance-seed.sql because the
-- subscription-control-plane E2E composes that identity seed with its own exact
-- subscription fixture. CRM workflows load this file explicitly.

INSERT INTO saas_plans (
    id, code, name, status, currency_code,
    monthly_price_minor, annual_price_minor, trial_days,
    max_users, max_organizations, storage_mb,
    created_at, updated_at
) VALUES (
    'c1c1c1c1-c1c1-4c1c-8c1c-c1c1c1c1c1c1',
    'CRM-ACCEPTANCE',
    'CRM Acceptance Plan',
    'ACTIVE',
    'SAR',
    0,
    0,
    0,
    20,
    5,
    1024,
    NOW(),
    NOW()
)
ON CONFLICT (id) DO NOTHING;

-- Explicit CRM module entitlement keeps the fixture aligned with the real
-- subscription -> plan -> module authority chain.
INSERT INTO plan_module_entitlements (
    id, plan_id, module_id, module_enabled, capability_code, created_at, updated_at
)
SELECT
    'c2c2c2c2-c2c2-4c2c-8c2c-c2c2c2c2c2c2',
    'c1c1c1c1-c1c1-4c1c-8c1c-c1c1c1c1c1c1',
    module.id,
    TRUE,
    NULL,
    NOW(),
    NOW()
FROM modules module
WHERE module.code = 'CRM'
ON CONFLICT DO NOTHING;

INSERT INTO tenant_subscriptions (
    id, tenant_id, plan_id, status, billing_cycle,
    seat_quantity, credit_balance_minor, started_at,
    current_period_start, current_period_end,
    cancel_at_period_end, created_at, updated_at
)
SELECT
    'c3c3c3c3-c3c3-4c3c-8c3c-c3c3c3c3c3c3',
    '11111111-1111-4111-8111-111111111111',
    'c1c1c1c1-c1c1-4c1c-8c1c-c1c1c1c1c1c1',
    'ACTIVE',
    'MONTHLY',
    10,
    0,
    NOW(),
    NOW(),
    NOW() + INTERVAL '30 days',
    FALSE,
    NOW(),
    NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM tenant_subscriptions
    WHERE tenant_id = '11111111-1111-4111-8111-111111111111'
      AND status NOT IN ('CANCELLED', 'EXPIRED', 'TERMINATED')
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO tenant_subscriptions (
    id, tenant_id, plan_id, status, billing_cycle,
    seat_quantity, credit_balance_minor, started_at,
    current_period_start, current_period_end,
    cancel_at_period_end, created_at, updated_at
)
SELECT
    'c4c4c4c4-c4c4-4c4c-8c4c-c4c4c4c4c4c4',
    '22222222-2222-4222-8222-222222222222',
    'c1c1c1c1-c1c1-4c1c-8c1c-c1c1c1c1c1c1',
    'ACTIVE',
    'MONTHLY',
    10,
    0,
    NOW(),
    NOW(),
    NOW() + INTERVAL '30 days',
    FALSE,
    NOW(),
    NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM tenant_subscriptions
    WHERE tenant_id = '22222222-2222-4222-8222-222222222222'
      AND status NOT IN ('CANCELLED', 'EXPIRED', 'TERMINATED')
)
ON CONFLICT (id) DO NOTHING;
