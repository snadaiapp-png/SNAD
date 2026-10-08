-- Current SANAD module IAM contracts for module-local user provisioning.
-- Registry data is discovery/metadata only. Authorization remains canonical RBAC.
-- Route roots are metadata so Users UI contains no hardcoded module list.

WITH governed(application_code, capability_namespaces, route_roots) AS (
    VALUES
      ('CRM',                '["CRM"]'::jsonb,                    '["crm"]'::jsonb),
      ('AI',                 '["AI"]'::jsonb,                     '["ai"]'::jsonb),
      ('WORKFLOW',           '["WORKFLOW"]'::jsonb,               '["workflow"]'::jsonb),
      ('ERP',                '["ERP"]'::jsonb,                    '["erp","inventory"]'::jsonb),
      ('FINANCE',            '["FINANCE"]'::jsonb,                '["finance","accounting"]'::jsonb),
      ('ANALYTICS',          '["ANALYTICS"]'::jsonb,              '["analytics"]'::jsonb),
      ('HRM',                '["HRM","HR"]'::jsonb,               '["hr"]'::jsonb),
      ('POS',                '["POS"]'::jsonb,                    '["pos"]'::jsonb),
      ('ECOMMERCE_CX',       '["ECOMMERCE","CX"]'::jsonb,         '["ecommerce","cx"]'::jsonb),
      ('INDUSTRY_SOLUTIONS', '["INDUSTRY"]'::jsonb,               '["industry","industry-solutions"]'::jsonb),
      ('WEBSITES',           '["WEBSITE","WEBSITES"]'::jsonb,     '["websites"]'::jsonb)
),
contracts AS (
    SELECT
        g.application_code,
        g.capability_namespaces,
        g.route_roots,
        COALESCE((
            SELECT jsonb_agg(ac.code ORDER BY ac.code)
              FROM access_capabilities ac
             WHERE ac.status = 'ACTIVE'
               AND EXISTS (
                    SELECT 1
                      FROM jsonb_array_elements_text(g.capability_namespaces) ns(value)
                     WHERE ac.code = ns.value OR ac.code LIKE ns.value || '.%'
               )
        ), '[]'::jsonb) AS declared_capabilities
      FROM governed g
      JOIN applications a ON a.code = g.application_code AND a.status = 'ACTIVE'
)
INSERT INTO application_iam_contracts (
    application_code, contract_version, status,
    capability_namespaces, supported_scopes, declared_capabilities,
    role_templates, compatibility_metadata, created_at, updated_at
)
SELECT
    c.application_code,
    '1',
    'ACTIVE',
    c.capability_namespaces,
    '["TENANT","ORGANIZATION"]'::jsonb,
    c.declared_capabilities,
    '[]'::jsonb,
    jsonb_build_object('routeRoots', c.route_roots),
    NOW(),
    NOW()
FROM contracts c
ON CONFLICT (application_code) DO UPDATE
SET contract_version = EXCLUDED.contract_version,
    status = 'ACTIVE',
    capability_namespaces = EXCLUDED.capability_namespaces,
    supported_scopes = EXCLUDED.supported_scopes,
    declared_capabilities = EXCLUDED.declared_capabilities,
    compatibility_metadata =
      COALESCE(application_iam_contracts.compatibility_metadata, '{}'::jsonb)
      || EXCLUDED.compatibility_metadata,
    updated_at = NOW();
