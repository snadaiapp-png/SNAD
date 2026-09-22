-- ============================================================================
-- V20260922_2 — Subscription closure: website/store monetization limits
--
-- Adds canonical subscription-backed numeric limits for multi-website and
-- multi-store resources. EntitlementResolver remains the single read authority.
-- Existing plans are populated idempotently; no catalog rows are replaced.
-- ============================================================================

INSERT INTO module_capabilities
    (id, module_id, code, name, description, capability_type, default_value,
     status, created_at, updated_at)
SELECT gen_random_uuid(), m.id, v.code, v.name, v.description,
       'NUMERIC_LIMIT', v.default_value, 'ACTIVE', NOW(), NOW()
FROM modules m
JOIN (VALUES
    ('WEBSITES',     'WEBSITES.MAX_WEBSITES',    'Maximum Websites',
     'Maximum non-archived websites allowed by the active subscription', '1'),
    ('ECOMMERCE_CX', 'ECOMMERCE_CX.MAX_STORES', 'Maximum Stores',
     'Maximum non-archived ecommerce stores allowed by the active subscription', '1')
) AS v(module_code, code, name, description, default_value)
  ON v.module_code = m.code
WHERE NOT EXISTS (
    SELECT 1 FROM module_capabilities mc WHERE mc.code = v.code
);

DO $$
DECLARE
    plan_row RECORD;
    websites_module UUID;
    ecommerce_module UUID;
    website_limit BIGINT;
    store_limit BIGINT;
BEGIN
    SELECT id INTO websites_module FROM modules WHERE code = 'WEBSITES' LIMIT 1;
    SELECT id INTO ecommerce_module FROM modules WHERE code = 'ECOMMERCE_CX' LIMIT 1;

    FOR plan_row IN
        SELECT id, code FROM saas_plans WHERE status = 'ACTIVE'
    LOOP
        website_limit := CASE plan_row.code
            WHEN 'STARTER' THEN 1
            WHEN 'GROWTH' THEN 5
            WHEN 'ENTERPRISE' THEN 100
            ELSE 1
        END;
        store_limit := CASE plan_row.code
            WHEN 'STARTER' THEN 1
            WHEN 'GROWTH' THEN 5
            WHEN 'ENTERPRISE' THEN 100
            ELSE 1
        END;

        IF websites_module IS NOT NULL THEN
            INSERT INTO plan_module_entitlements
                (id, plan_id, module_id, module_enabled, capability_code,
                 capability_value, limit_value, quota_value, quota_period,
                 effective_at, created_at, updated_at)
            SELECT gen_random_uuid(), plan_row.id, websites_module, TRUE,
                   'WEBSITES.MAX_WEBSITES', website_limit::text, website_limit,
                   NULL, NULL, NOW(), NOW(), NOW()
            WHERE NOT EXISTS (
                SELECT 1 FROM plan_module_entitlements
                WHERE plan_id = plan_row.id
                  AND module_id = websites_module
                  AND capability_code = 'WEBSITES.MAX_WEBSITES'
            );
        END IF;

        IF ecommerce_module IS NOT NULL THEN
            INSERT INTO plan_module_entitlements
                (id, plan_id, module_id, module_enabled, capability_code,
                 capability_value, limit_value, quota_value, quota_period,
                 effective_at, created_at, updated_at)
            SELECT gen_random_uuid(), plan_row.id, ecommerce_module, TRUE,
                   'ECOMMERCE_CX.MAX_STORES', store_limit::text, store_limit,
                   NULL, NULL, NOW(), NOW(), NOW()
            WHERE NOT EXISTS (
                SELECT 1 FROM plan_module_entitlements
                WHERE plan_id = plan_row.id
                  AND module_id = ecommerce_module
                  AND capability_code = 'ECOMMERCE_CX.MAX_STORES'
            );
        END IF;
    END LOOP;
END $$;
