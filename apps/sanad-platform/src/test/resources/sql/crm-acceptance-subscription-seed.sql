-- SNAD CRM authenticated acceptance — login-eligible subscription fixture
-- This file is intentionally separate from crm-acceptance-seed.sql because the
-- subscription-control-plane E2E composes that identity seed with its own exact
-- subscription fixture. CRM workflows load this file explicitly.
--
-- tenant_subscriptions is fail-closed FORCE RLS (V20261029_1): each
-- subscription insert re-binds the session GUC to its owning tenant; the GUC
-- is reset at the end of the file (repo seed convention).
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

-- G1 signed-context contract (V20261124_1/_2): bind the transaction's tenant
-- with the disposable fixture key (repository-published for fixture topologies
-- only; V20261124_2 provisions it automatically there). The raw session GUC is
-- no longer trusted by the enforced policies — they read the HMAC-validated
-- public.app_tenant_ctx_text().
DO $signed_fixture_22222222$
DECLARE
    v_tenant uuid := '22222222-2222-4222-8222-222222222222'::uuid;
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
$signed_fixture_22222222$;

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

-- Session hygiene: clear the tenant context for any later statement.
COMMIT;
