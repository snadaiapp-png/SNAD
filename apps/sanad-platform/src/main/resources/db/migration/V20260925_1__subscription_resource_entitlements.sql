-- ============================================================================
-- V20260925_1 — Subscription closure: website/store monetization limits
--
-- Adds canonical subscription-backed numeric limits for multi-website and
-- multi-store resources. EntitlementResolver remains the single read authority.
-- Existing module enablement is preserved exactly; no plan is broadened or rewritten.
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

-- Do not synthesize plan entitlements here. The module capability default is
-- consumed only after the tenant's existing plan has already enabled the
-- corresponding module. Writing module_enabled=TRUE rows for every ACTIVE plan
-- would silently broaden commercial entitlements. Operators can override these
-- numeric defaults through the governed plan-module entitlement APIs.
