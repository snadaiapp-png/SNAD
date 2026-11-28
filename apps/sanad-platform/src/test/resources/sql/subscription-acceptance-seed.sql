-- SNAD Subscription Control Plane authenticated acceptance seed
-- Extends crm-acceptance-seed.sql in a disposable PostgreSQL Direct database.
-- Tenant A is also the configured control-plane tenant for this isolated job.

-- Open ONE transaction: the signed bind below is transaction-local.
BEGIN;

-- G1 signed-context contract (V20261124_1/_2): bind the transaction's tenant
-- with the disposable fixture key (repository-published for fixture topologies
-- only; V20261124_2 provisions it automatically there). The raw session GUC is
-- no longer trusted by the enforced policies — they read the HMAC-validated
-- public.app_tenant_ctx_text().
DO $signed_fixture_11111111$
DECLARE
    v_tenant uuid := '11111111-1111-4111-8111-111111111111'::uuid;
    v_mac text;
BEGIN
    v_mac := encode(hmac(
        convert_to(v_tenant::text || ':' || txid_current()::text || ':' || pg_backend_pid()::text, 'UTF8'),
        decode(repeat('0123456789abcdef', 4), 'hex'), 'sha256'), 'hex');
    IF NOT public.app_bind_tenant_ctx(v_tenant, v_mac)
       OR public.app_tenant_ctx_text() IS DISTINCT FROM v_tenant::text THEN
        RAISE EXCEPTION 'SIGNED_FIXTURE_BIND_REFUSED';
    END IF;
END
$signed_fixture_11111111$;

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

COMMIT;
