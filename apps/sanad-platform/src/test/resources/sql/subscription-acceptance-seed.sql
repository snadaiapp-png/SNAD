-- SNAD Subscription Control Plane authenticated acceptance seed
-- Extends crm-acceptance-seed.sql in a disposable PostgreSQL Direct database.
-- Tenant A is also the configured control-plane tenant for this isolated job.

SELECT set_config('app.tenant_id', '11111111-1111-4111-8111-111111111111', false);

INSERT INTO saas_plans (
    id, code, name, status, currency_code,
    monthly_price_minor, annual_price_minor, trial_days,
    max_users, max_organizations, storage_mb,
    created_at, updated_at
) VALUES (
    '33333333-3333-4333-8333-333333333333',
    'SUBSCRIPTION-ACCEPTANCE',
    'Subscription Acceptance Plan',
    'ACTIVE',
    'SAR',
    30000,
    300000,
    0,
    10,
    5,
    1024,
    NOW(),
    NOW()
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO plan_versions (
    id, plan_id, version_number, status,
    effective_from, currency_code, monthly_price_minor,
    annual_price_minor, trial_days, max_users,
    max_organizations, storage_mb, created_at, updated_at
) VALUES (
    '33333333-3333-4333-8333-333333333334',
    '33333333-3333-4333-8333-333333333333',
    1,
    'ACTIVE',
    NOW() - INTERVAL '1 day',
    'SAR',
    30000,
    300000,
    0,
    10,
    5,
    1024,
    NOW(),
    NOW()
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO tenant_subscriptions (
    id, tenant_id, plan_id, plan_version_id, status,
    billing_cycle, seat_quantity, credit_balance_minor,
    started_at, current_period_start, current_period_end,
    cancel_at_period_end, created_at, updated_at
) VALUES (
    '44444444-4444-4444-8444-444444444444',
    '11111111-1111-4111-8111-111111111111',
    '33333333-3333-4333-8333-333333333333',
    '33333333-3333-4333-8333-333333333334',
    'ACTIVE',
    'MONTHLY',
    1,
    0,
    NOW() - INTERVAL '1 day',
    NOW() - INTERVAL '1 day',
    NOW() + INTERVAL '29 days',
    FALSE,
    NOW(),
    NOW()
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO subscription_items (
    id, tenant_id, subscription_id, item_type,
    plan_id, plan_version_id, name_snapshot, quantity,
    unit_amount_minor, currency_code, status,
    created_at, updated_at
) VALUES (
    '55555555-5555-4555-8555-555555555555',
    '11111111-1111-4111-8111-111111111111',
    '44444444-4444-4444-8444-444444444444',
    'PLAN',
    '33333333-3333-4333-8333-333333333333',
    '33333333-3333-4333-8333-333333333334',
    'Subscription Acceptance Plan',
    1,
    30000,
    'SAR',
    'ACTIVE',
    NOW(),
    NOW()
)
ON CONFLICT (id) DO NOTHING;

SELECT 'subscription-acceptance-seed: status=' || status
FROM tenant_subscriptions
WHERE id = '44444444-4444-4444-8444-444444444444';
