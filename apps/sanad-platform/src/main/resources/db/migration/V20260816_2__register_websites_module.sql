-- ============================================================
-- V20260816_2: Websites Platform — Module Registration
--
-- Registers the WEBSITES module in the global module catalog.
-- This is the first migration of the Website Platform implementation.
--
-- H2 compatibility: pure idempotent INSERT, runs on PG and H2.
-- ============================================================

INSERT INTO modules (id, code, name, description, status, display_order, version, enabled, created_at, updated_at)
SELECT gen_random_uuid(), v.code, v.name, v.description, v.status, v.display_order, v.version, v.enabled, NOW(), NOW()
FROM (VALUES
    ('WEBSITES', 'Websites', 'Website Platform — multi-tenant websites, pages, domains, publishing, SEO', 'ACTIVE', 95, '1.0', true)
) AS v(code, name, description, status, display_order, version, enabled)
WHERE NOT EXISTS (SELECT 1 FROM modules m WHERE m.code = v.code);

-- Seed WEBSITE.* capabilities
INSERT INTO access_capabilities (id, code, name, description, status, created_at, updated_at)
SELECT gen_random_uuid(), code, name, desc_text, 'ACTIVE', NOW(), NOW()
FROM (VALUES
    ('WEBSITE.VIEW',    'Website View',    'View websites, pages, domains, and published content'),
    ('WEBSITE.WRITE',   'Website Write',   'Create and update websites, pages, navigation, and theme'),
    ('WEBSITE.PUBLISH', 'Website Publish', 'Publish and unpublish website pages'),
    ('WEBSITE.ADMIN',   'Website Admin',   'Full administrative access including activate/suspend/archive and domain management')
) AS v(code, name, desc_text)
WHERE NOT EXISTS (SELECT 1 FROM access_capabilities ac WHERE ac.code = v.code);

-- Bind to ADMIN for all tenants (idempotent)
INSERT INTO role_capabilities (id, tenant_id, role_id, capability_id, created_at)
SELECT gen_random_uuid(), t.id, r.id, ac.id, NOW()
FROM tenants t
JOIN roles r ON r.tenant_id = t.id AND r.code = 'ADMIN'
JOIN access_capabilities ac ON ac.status = 'ACTIVE'
   AND ac.code IN ('WEBSITE.VIEW', 'WEBSITE.WRITE', 'WEBSITE.PUBLISH', 'WEBSITE.ADMIN')
WHERE NOT EXISTS (
    SELECT 1 FROM role_capabilities rc
    WHERE rc.tenant_id = t.id AND rc.role_id = r.id AND rc.capability_id = ac.id
);
